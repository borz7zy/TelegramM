package com.github.borz7zy.telegramm.ui.dialogs;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.borz7zy.telegramm.AppManager;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.core.accounts.AccountSession;
import com.github.borz7zy.telegramm.core.accounts.AccountStorage;
import com.github.borz7zy.telegramm.ui.MainViewModel;
import com.github.borz7zy.telegramm.ui.ThemeEngine;
import com.github.borz7zy.telegramm.ui.base.BaseTelegramFragment;
import com.github.borz7zy.telegramm.ui.chat.ChatFragment;
import com.github.borz7zy.telegramm.ui.model.DialogItem;
import com.github.borz7zy.telegramm.ui.model.FolderItem;
import com.github.borz7zy.telegramm.ui.widget.SpringRecyclerView;
import com.github.borz7zy.telegramm.utils.Logger;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DialogsFragment extends BaseTelegramFragment implements Client.ResultHandler {

    private DialogsAdapter adapter;
    private SpringRecyclerView recyclerView;
    private RecyclerView foldersRecyclerView;
    private FoldersAdapter foldersAdapter;
    private View progressBar;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AccountSession currentSession;

    private ItemTouchHelper itemTouchHelper;
    private boolean isReordering;
    private boolean pendingSubmit;
    private ArrayList<Long> pinnedOrderOverride;

    private int currentTop = 0;
    private int currentBottom = 0;

    private boolean isLoadingMore = false;
    private boolean endReached = false;
    private Runnable rebuildRunnable;

    private int currentFolderId = FolderItem.ID_ALL;

    private int mainChatListPosition = 0;

    private final List<TdApi.ChatFolderInfo> rawFolders = new ArrayList<>();

    private MainViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dialogs, container, false);
    }

    @SuppressLint("ResourceAsColor")
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        recyclerView = view.findViewById(R.id.recycler_dialogs);
        foldersRecyclerView = requireActivity().findViewById(R.id.rv_folders);
        progressBar = view.findViewById(R.id.progress_dialogs);

        ThemeEngine themeEngine = AppManager.getInstance().getThemeEngine();
        ThemeEngine.Theme currentTheme = themeEngine.getCurrentTheme().getValue();

        setupRecyclerView();
        setupFoldersBar();

        if (currentTheme != null) {
            applyTheme(view, currentTheme);
        }

        setupInsets();

        viewModel.getDialogList().observe(getViewLifecycleOwner(), list -> {
            Logger.LOGD("DialogsFragment", "dialogList updated, size=" + (list == null ? "null" : list.size()));
            adapter.submitList(list);
        });

        viewModel.getDialogsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(isLoading ? View.INVISIBLE : View.VISIBLE);
        });

        if (!viewModel.getDialogs().isEmpty()) {
            refreshList();
        }

        themeEngine.getCurrentTheme().observe(getViewLifecycleOwner(), theme -> {
            applyTheme(view, theme);
        });

        if (currentSession == null) {
            initializeSession();
        }
    }

    private void applyTheme(View root, ThemeEngine.Theme theme) {
        adapter.setTheme(theme);
        foldersAdapter.setTheme(theme);
        root.setBackgroundColor(theme.surfaceColor);
    }

    private void setupFoldersBar() {
        foldersAdapter = new FoldersAdapter();

        LinearLayoutManager lm = new LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false);
        foldersRecyclerView.setLayoutManager(lm);
        foldersRecyclerView.setAdapter(foldersAdapter);

        foldersAdapter.setOnFolderSelectedListener(folder -> {
            if (folder.id == currentFolderId) return;
            selectFolder(folder.id);
        });
    }

    private void rebuildFolderTabs() {
        List<FolderItem> tabs = new ArrayList<>();

        for (TdApi.ChatFolderInfo info : rawFolders) {
            String title = (info.name != null && info.name.text != null)
                    ? info.name.text.text
                    : "Folder " + info.id;
            tabs.add(new FolderItem(info.id, title, currentFolderId == info.id));
        }

        FolderItem allTab = new FolderItem(FolderItem.ID_ALL, "All", currentFolderId == FolderItem.ID_ALL);
        int insertAt = Math.max(0, Math.min(mainChatListPosition, tabs.size()));
        tabs.add(insertAt, allTab);

        boolean hasFolders = rawFolders.size() > 0;
        mainHandler.post(() -> {
            foldersAdapter.submitList(tabs);
            viewModel.setFoldersAvailable(hasFolders);
        });
    }

    private void selectFolder(int folderId) {
        currentFolderId = folderId;

        List<FolderItem> current = foldersAdapter.getCurrentList();
        List<FolderItem> updated = new ArrayList<>(current.size());
        for (FolderItem f : current) {
            updated.add(f.withSelected(f.id == folderId));
        }
        foldersAdapter.submitList(updated);

        isLoadingMore = false;
        endReached = false;
        pinnedOrderOverride = null;

        rebuildDialogsFromCache();
        adapter.resetFirstDiff();

        if (viewModel.getDialogs().isEmpty()) {
            adapter.submitList(Collections.emptyList());
            mainHandler.post(() -> viewModel.setDialogsLoading(true));
            loadMoreChats();
        } else {
            mainHandler.post(() -> viewModel.setDialogsLoading(false));
            refreshList();
        }
    }

    private void rebuildDialogsFromCache() {
        viewModel.getDialogs().clear();
        for (TdApi.Chat chat : viewModel.getChatCache().values()) {
            long order = getOrderForActiveList(chat);
            if (order != 0) {
                viewModel.getDialogs().put(chat.id, new DialogItem(chat, order));
            }
        }
    }

    private void cacheChat(TdApi.Chat chat) {
        if (chat == null) return;
        viewModel.getChatCache().put(chat.id, chat);
    }

    private void mergePositionIntoCache(long chatId, TdApi.ChatPosition position) {
        TdApi.Chat cached = viewModel.getChatCache().get(chatId);
        if (cached == null) return;

        synchronized (cached) {
            ArrayList<TdApi.ChatPosition> next = new ArrayList<>();
            boolean replaced = false;
            if (cached.positions != null) {
                for (TdApi.ChatPosition p : cached.positions) {
                    if (sameChatList(p.list, position.list)) {
                        if (position.order != 0) next.add(position);
                        replaced = true;
                    } else {
                        next.add(p);
                    }
                }
            }
            if (!replaced && position.order != 0) {
                next.add(position);
            }
            cached.positions = next.toArray(new TdApi.ChatPosition[0]);
        }
    }

    private static boolean sameChatList(TdApi.ChatList a, TdApi.ChatList b) {
        if (a == null || b == null) return false;
        if (a.getClass() != b.getClass()) return false;
        if (a instanceof TdApi.ChatListFolder) {
            return ((TdApi.ChatListFolder) a).chatFolderId
                    == ((TdApi.ChatListFolder) b).chatFolderId;
        }
        return true;
    }

    private void setupRecyclerView() {
        adapter = new DialogsAdapter();
        adapter.setOnFirstDiffDoneListener(() ->
                mainHandler.post(() -> viewModel.setDialogsLoading(false)));

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int total = lm.getItemCount();
                int lastVisible = lm.findLastVisibleItemPosition();
                if (!isLoadingMore && !endReached && total > 0 && lastVisible >= total - 4) {
                    loadMoreChats();
                }
            }
        });

        adapter.setOnDialogClickListener(item ->
            ChatFragment.newInstance(item.chatId, item.name).show(getParentFragmentManager(), "chat_sheet"));

        adapter.setOnTopicToggleListener(new DialogsAdapter.OnTopicToggleListener() {
            @Override
            public void onTopicToggle(DialogItem item) {
                toggleForumTopics(item);
            }

            @Override
            public void onTopicClick(long chatId, TdApi.ForumTopic topic) {
                Logger.LOGD("DialogsFragment", "Open topic: " + topic.info.name);
            }
        });

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                int pos = vh.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return 0;
                if (!adapter.getItem(pos).isPinned) return 0;
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder fromVH,
                                  @NonNull RecyclerView.ViewHolder toVH) {
                int from = fromVH.getBindingAdapterPosition();
                int to = toVH.getBindingAdapterPosition();
                if (!adapter.getItem(to).isPinned) return false;
                return adapter.movePinned(from, to);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) isReordering = true;
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                pinnedOrderOverride = adapter.getPinnedIdsInUiOrder();
                updatePinnedOrderOnServer(pinnedOrderOverride);
                isReordering = false;
                if (pendingSubmit) {
                    pendingSubmit = false;
                    refreshList();
                }
            }
        });

        itemTouchHelper.attachToRecyclerView(recyclerView);
        adapter.setOnDragListener(vh -> itemTouchHelper.startDrag(vh));
    }

    private void setupInsets() {
        viewModel.getTopInset().observe(getViewLifecycleOwner(), height -> {
            currentTop = height;
            updateRecyclerPadding();
        });

        viewModel.getBottomInset().observe(getViewLifecycleOwner(), height -> {
            currentBottom = height;
            updateRecyclerPadding();
        });
    }

    private void updateRecyclerPadding() {
        recyclerView.setPadding(0, currentTop, 0, currentBottom);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (currentSession != null) {
            currentSession.removeUpdateHandler(this);
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onAuthStateChanged(TdApi.AuthorizationState state) {
        if (state instanceof TdApi.AuthorizationStateReady) {
            initializeSession();
        }
    }

    private void initializeSession() {
        if (currentSession != null) return;

        AccountStorage.getInstance().getCurrentActive(account -> {
            if (account == null) return;

            currentSession = AccountManager.getInstance().getSession(account.getAccountId());
            TdMediaRepository.get().setCurrentAccountId(account.getAccountId());
            com.github.borz7zy.telegramm.utils.EmojiStatusRepository.get()
                    .setCurrentAccountId(account.getAccountId());

            if (currentSession != null) {
                currentSession.addUpdateHandler(this);

                if (viewModel.getDialogs().isEmpty()) {
                    mainHandler.post(() -> viewModel.setDialogsLoading(true));
                }

                loadMoreChats();
            }
        });
    }

    private void loadMoreChats() {
        if (currentSession == null || isLoadingMore || endReached) return;
        isLoadingMore = true;

        TdApi.ChatList chatList = activeChatList();

        currentSession.send(
                new TdApi.LoadChats(chatList, 20),
                object -> mainHandler.post(() -> {
                    isLoadingMore = false;
                    if (object instanceof TdApi.Error) {
                        TdApi.Error err = (TdApi.Error) object;
                        if (err.code == 404) {
                            endReached = true;
                            if (viewModel.getDialogs().isEmpty()) {
                                viewModel.setDialogsLoading(false);
                            }
                        }
                    }
                })
        );
    }

    @Override
    public void onResult(TdApi.Object object) {
        if (object instanceof TdApi.Chat) {
            TdApi.Chat chat = (TdApi.Chat) object;
            cacheChat(chat);
            updateChat(chat);
        }
        else if (object instanceof TdApi.UpdateNewChat) {
            TdApi.Chat chat = ((TdApi.UpdateNewChat) object).chat;
            cacheChat(chat);
            updateChat(chat);
        }
        else if (object instanceof TdApi.UpdateChatPosition) {
            TdApi.UpdateChatPosition u = (TdApi.UpdateChatPosition) object;
            mergePositionIntoCache(u.chatId, u.position);
            if (matchesActiveList(u.position.list)) {
                handleChatPosition(u.chatId, u.position);
            }
        }
        else if (object instanceof TdApi.UpdateChatLastMessage) {
            currentSession.send(
                    new TdApi.GetChat(((TdApi.UpdateChatLastMessage) object).chatId), this);
        }
        else if (object instanceof TdApi.UpdateChatReadInbox) {
            currentSession.send(
                    new TdApi.GetChat(((TdApi.UpdateChatReadInbox) object).chatId), this);
        }
        else if (object instanceof TdApi.UpdateChatAction) {
            handleTyping(((TdApi.UpdateChatAction) object).chatId);
        }
        else if (object instanceof TdApi.UpdateChatFolders) {
            TdApi.UpdateChatFolders u = (TdApi.UpdateChatFolders) object;
            rawFolders.clear();
            if (u.chatFolders != null) {
                Collections.addAll(rawFolders, u.chatFolders);
            }
            mainChatListPosition = u.mainChatListPosition;

            boolean currentStillExists = (currentFolderId == FolderItem.ID_ALL);
            for (TdApi.ChatFolderInfo info : rawFolders) {
                if (info.id == currentFolderId) {
                    currentStillExists = true;
                    break;
                }
            }
            if (!currentStillExists) {
                currentFolderId = FolderItem.ID_ALL;
                isLoadingMore = false;
                endReached = false;
                rebuildDialogsFromCache();
                if (viewModel.getDialogs().isEmpty()) {
                    loadMoreChats();
                } else {
                    refreshList();
                }
            }

            rebuildFolderTabs();
        }
    }

    private void updateChat(TdApi.Chat chat) {
        long order = getOrderForActiveList(chat);

        if (order == 0) {
            if (viewModel.getDialogs().remove(chat.id) != null) refreshList();
            return;
        }

        DialogItem oldItem = viewModel.getDialogs().get(chat.id);
        DialogItem newItem = new DialogItem(chat, order);

        if (oldItem != null) {
            newItem.isTyping = oldItem.isTyping;
            newItem.isExpanded = oldItem.isExpanded;
            newItem.topics = oldItem.topics;
        }

        viewModel.getDialogs().put(chat.id, newItem);
        refreshList();
    }

    private void handleChatPosition(long chatId, TdApi.ChatPosition position) {
        if (position.order == 0) {
            if (viewModel.getDialogs().remove(chatId) != null) refreshList();
            return;
        }

        DialogItem old = viewModel.getDialogs().get(chatId);
        if (old != null) {
            viewModel.getDialogs().put(chatId,
                    old.copyWithOrderPinned(position.order, position.isPinned));
            refreshList();
            return;
        }

        TdApi.Chat cached = viewModel.getChatCache().get(chatId);
        if (cached != null) {
            updateChat(cached);
        } else {
            currentSession.send(new TdApi.GetChat(chatId), this);
        }
    }

    private void handleTyping(long chatId) {
        DialogItem old = viewModel.getDialogs().get(chatId);
        if (old == null) return;

        viewModel.getDialogs().put(chatId, old.copyWithTyping(true));
        refreshList();

        mainHandler.postDelayed(() -> {
            DialogItem cur = viewModel.getDialogs().get(chatId);
            if (cur != null) {
                viewModel.getDialogs().put(chatId, cur.copyWithTyping(false));
                refreshList();
            }
        }, 3000);
    }

    private void toggleForumTopics(DialogItem item) {
        boolean newState = !item.isExpanded;
        viewModel.getDialogs().put(item.chatId, item.copyWithExpanded(newState));
        refreshList();

        if (newState && (item.topics == null || item.topics.isEmpty())) {
            loadTopics(item.chatId);
        }
    }

    private void loadTopics(long chatId) {
        currentSession.send(new TdApi.GetForumTopics(chatId, "", 0, 0, 0, 100), object -> {
            if (!(object instanceof TdApi.ForumTopics)) return;
            TdApi.ForumTopics result = (TdApi.ForumTopics) object;

            mainHandler.post(() -> {
                DialogItem current = viewModel.getDialogs().get(chatId);
                if (current != null && current.isExpanded) {
                    ArrayList<TdApi.ForumTopic> topicList = new ArrayList<>();
                    if (result.topics != null) Collections.addAll(topicList, result.topics);
                    viewModel.getDialogs().put(chatId, current.copyWithTopics(topicList));
                    refreshList();
                }
            });
        });
    }

    private void refreshList() {
        if (isReordering) {
            pendingSubmit = true;
            return;
        }

        if (rebuildRunnable != null) mainHandler.removeCallbacks(rebuildRunnable);

        rebuildRunnable = () -> {
            ArrayList<DialogItem> list = new ArrayList<>(viewModel.getDialogs().values());

            final Map<Long, Integer> pinnedIndex = new HashMap<>();
            if (pinnedOrderOverride != null) {
                for (int i = 0; i < pinnedOrderOverride.size(); ++i) {
                    pinnedIndex.put(pinnedOrderOverride.get(i), i);
                }
            }

            Collections.sort(list, (a, b) -> {
                if (a.isPinned != b.isPinned) return a.isPinned ? -1 : 1;
                if (a.isPinned) {
                    Integer ia = pinnedIndex.get(a.chatId);
                    Integer ib = pinnedIndex.get(b.chatId);
                    if (ia != null || ib != null) {
                        if (ia == null) return 1;
                        if (ib == null) return -1;
                        return Integer.compare(ia, ib);
                    }
                }
                return Long.compare(b.order, a.order);
            });

            viewModel.postDialogList(list);
        };

        mainHandler.postDelayed(rebuildRunnable, 100);
    }

    private void updatePinnedOrderOnServer(ArrayList<Long> pinnedIds) {
        if (currentSession == null || pinnedIds == null) return;

        long[] ids = new long[pinnedIds.size()];
        for (int i = 0; i < pinnedIds.size(); ++i) ids[i] = pinnedIds.get(i);

        currentSession.send(new TdApi.SetPinnedChats(activeChatList(), ids));
    }

    private TdApi.ChatList activeChatList() {
        if (currentFolderId == FolderItem.ID_ALL) {
            return new TdApi.ChatListMain();
        }
        return new TdApi.ChatListFolder(currentFolderId);
    }

    private boolean matchesActiveList(TdApi.ChatList list) {
        if (currentFolderId == FolderItem.ID_ALL) {
            return list instanceof TdApi.ChatListMain;
        }
        if (list instanceof TdApi.ChatListFolder) {
            return ((TdApi.ChatListFolder) list).chatFolderId == currentFolderId;
        }
        return false;
    }

    private long getOrderForActiveList(TdApi.Chat chat) {
        if (chat == null || chat.positions == null) return 0;
        for (TdApi.ChatPosition pos : chat.positions) {
            if (matchesActiveList(pos.list)) return pos.order;
        }
        return 0;
    }

    private List<FolderItem> foldersAdapterCurrentList() {
        return foldersAdapter.getCurrentList();
    }
}