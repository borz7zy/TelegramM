package com.github.borz7zy.telegramm.ui.dialogs;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.borz7zy.telegramm.AppManager;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.core.accounts.AccountSession;
import com.github.borz7zy.telegramm.core.accounts.AccountStorage;
import com.github.borz7zy.telegramm.ui.LayoutViewModel;
import com.github.borz7zy.telegramm.ui.ThemeEngine;
import com.github.borz7zy.telegramm.ui.base.BaseTelegramFragment;
import com.github.borz7zy.telegramm.ui.chat.ChatFragment;
import com.github.borz7zy.telegramm.ui.model.DialogItem;
import com.github.borz7zy.telegramm.ui.widget.SpringRecyclerView;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DialogsFragment extends BaseTelegramFragment implements Client.ResultHandler {
    private DialogsAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Map<Long, DialogItem> dialogs = new ConcurrentHashMap<>();

    private SpringRecyclerView recyclerView;
    private AccountSession currentSession;

    private ItemTouchHelper itemTouchHelper;
    private boolean isReordering;
    private boolean pendingSubmit;
    private ArrayList<Long> pinnedOrderOverride;

    private int currentTop = 0;
    private int currentBottom = 0;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dialogs, container, false);
    }

    @SuppressLint("ResourceAsColor")
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_dialogs);
        setupRecyclerView();
        setupInsets();

        ThemeEngine themeEngine = AppManager.getInstance().getThemeEngine();
        ThemeEngine.Theme currentTheme = themeEngine.getCurrentTheme().getValue();
        if (currentTheme != null) {
            adapter.setTheme(currentTheme);
            view.setBackgroundColor(currentTheme.surfaceColor);
        }

        AppManager.getInstance().getThemeEngine().getCurrentTheme().observe(getViewLifecycleOwner(), theme -> {
            adapter.setTheme(theme);
            view.setBackgroundColor(theme.surfaceColor);
        });
    }

    private void setupRecyclerView() {
        adapter = new DialogsAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnDialogClickListener(item -> {
            ChatFragment.newInstance(item.chatId, item.name)
                    .show(getParentFragmentManager(), "chat_sheet");
        });

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                int pos = viewHolder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return 0;

                DialogItem item = adapter.getItem(pos);
                if (!item.isPinned) return 0;

                int drag = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                return makeMovementFlags(drag, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder fromVH,
                                  @NonNull RecyclerView.ViewHolder toVH) {
                int from = fromVH.getAdapterPosition();
                int to = toVH.getAdapterPosition();

                DialogItem target = adapter.getItem(to);
                if (!target.isPinned) return false;

                return adapter.movePinned(from, to);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    isReordering = true;
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);

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
        LayoutViewModel viewModel = new ViewModelProvider(requireActivity()).get(LayoutViewModel.class);

        viewModel.topInset.observe(getViewLifecycleOwner(), height -> {
            currentTop = height;
            updateRecyclerPadding();
        });

        viewModel.bottomInset.observe(getViewLifecycleOwner(), height -> {
            currentBottom = height;
            updateRecyclerPadding();
        });
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
    public void onResult(TdApi.Object object) {
        if (object instanceof TdApi.Chat) {
            updateChat((TdApi.Chat) object);
        }

        else if (object instanceof TdApi.UpdateNewChat) {
            updateChat(((TdApi.UpdateNewChat) object).chat);
        }

        else if (object instanceof TdApi.UpdateChatPosition) {
            TdApi.UpdateChatPosition u = (TdApi.UpdateChatPosition) object;
            if (u.position.list instanceof TdApi.ChatListMain) {
                handleChatPosition(u.chatId, u.position);
            }
        }

        else if (object instanceof TdApi.UpdateChatLastMessage) {
            TdApi.UpdateChatLastMessage u = (TdApi.UpdateChatLastMessage) object;
//            if (u.position.list instanceof TdApi.ChatListMain) {
                currentSession.send(new TdApi.GetChat(u.chatId), this);
//            }
        }

        else if (object instanceof TdApi.UpdateChatReadInbox) {
            currentSession.send(new TdApi.GetChat(((TdApi.UpdateChatReadInbox) object).chatId), this);
        }

        else if (object instanceof TdApi.UpdateChatAction) {
            handleTyping(((TdApi.UpdateChatAction) object).chatId);
        }
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

            if (currentSession != null) {
                currentSession.addUpdateHandler(this);
                loadChats();
            }
        });
    }

    private void loadChats() {
        currentSession.send(new TdApi.LoadChats(new TdApi.ChatListMain(), 100), object -> {
            fetchChats();
        });
    }

    private void fetchChats() {
        currentSession.send(new TdApi.GetChats(new TdApi.ChatListMain(), 100), object -> {
            if (object instanceof TdApi.Chats) {
                long[] ids = ((TdApi.Chats) object).chatIds;
                for (long id : ids) {
                    currentSession.send(new TdApi.GetChat(id), this);
                }
            }
        });
    }

    private void updateRecyclerPadding() {
        recyclerView.setPadding(0, currentTop, 0, currentBottom);
    }

    private void updateChat(TdApi.Chat chat) {
        long order = getOrder(chat);

        if (order == 0) {
            if (dialogs.remove(chat.id) != null) {
                refreshList();
            }
            return;
        }

        DialogItem newItem = new DialogItem(chat, order);

        DialogItem oldItem = dialogs.get(chat.id);
        if (oldItem != null) {
            newItem.isTyping = oldItem.isTyping;
        }

        dialogs.put(chat.id, newItem);
        refreshList();
    }

    private void handleChatPosition(long chatId, TdApi.ChatPosition position) {
        if (position.order == 0) {
            if (dialogs.remove(chatId) != null) {
                refreshList();
            }
            return;
        }

        DialogItem old = dialogs.get(chatId);
        if (old != null) {
            DialogItem fresh = old.copyWithOrderPinned(position.order, position.isPinned);
            dialogs.put(chatId, fresh);
            refreshList();
        } else {
            currentSession.send(new TdApi.GetChat(chatId), this);
        }
    }

    private void handleTyping(long chatId) {
        DialogItem old = dialogs.get(chatId);
        if (old != null) {
            dialogs.put(chatId, old.copyWithTyping(true));
            refreshList();

            mainHandler.postDelayed(() -> {
                DialogItem cur = dialogs.get(chatId);
                if (cur != null) {
                    dialogs.put(chatId, cur.copyWithTyping(false));
                    refreshList();
                }
            }, 3000);
        }
    }

    private void refreshList() {
        if (isReordering) {
            pendingSubmit = true;
            return;
        }

        mainHandler.post(() -> {
            ArrayList<DialogItem> list = new ArrayList<>(dialogs.values());

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

            adapter.submitList(list);
        });
    }

    private void updatePinnedOrderOnServer(ArrayList<Long> pinnedIds) {
        if (currentSession == null || pinnedIds == null) return;

        long[] ids = new long[pinnedIds.size()];
        for (int i = 0; i < pinnedIds.size(); ++i) ids[i] = pinnedIds.get(i);

        currentSession.send(new TdApi.SetPinnedChats(new TdApi.ChatListMain(), ids));
    }

    private long getOrder(TdApi.Chat chat) {
        if(chat == null || chat.positions == null) return 0;
        for(TdApi.ChatPosition pos : chat.positions){
            if(pos.list instanceof TdApi.ChatListMain){
                return pos.order;
            }
        }
        return 0;
    }
}
