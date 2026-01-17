package com.github.borz7zy.telegramm.ui.dialogs;

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

import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.actor.AbstractActor;
import com.github.borz7zy.telegramm.actor.ActorRef;
import com.github.borz7zy.telegramm.core.TdMessages;
import com.github.borz7zy.telegramm.ui.LayoutViewModel;
import com.github.borz7zy.telegramm.ui.chat.ChatFragment;
import com.github.borz7zy.telegramm.ui.model.DialogItem;
import com.github.borz7zy.telegramm.ui.base.BaseTdFragment;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DialogsFragment extends BaseTdFragment {
    private DialogsAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Long, DialogItem> dialogs = new HashMap<>();

    private int currentTop = 0;
    private int currentBottom = 0;
    private ItemTouchHelper itemTouchHelper;
    private boolean isReordering;
    private boolean pendingSubmit;
    private ArrayList<Long> pinnedOrderOverride;
    private DialogsActor uiActor;

    private void submitDialogs() {
        if (isReordering) {
            pendingSubmit = true;
            return;
        }

        ArrayList<DialogItem> list = new ArrayList<>(dialogs.values());

        final Map<Long, Integer> pinnedIndex = new HashMap<>();
        if (pinnedOrderOverride != null) {
            for (int i = 0; i < pinnedOrderOverride.size(); ++i) {
                pinnedIndex.put(pinnedOrderOverride.get(i), i);
            }
        }

        list.sort((a, b) -> {
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

        handler.post(() -> adapter.submitList(list));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dialogs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv = view.findViewById(R.id.recycler_dialogs);

        LayoutViewModel viewModel = new ViewModelProvider(requireActivity()).get(LayoutViewModel.class);

        viewModel.topInset.observe(getViewLifecycleOwner(), height -> {
            currentTop = height;
            updateRecyclerPadding(rv);
        });

        viewModel.bottomInset.observe(getViewLifecycleOwner(), height -> {
            currentBottom = height;
            updateRecyclerPadding(rv);
        });

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DialogsAdapter();
        rv.setAdapter(adapter);

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback(){
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
                return adapter.movePinned(from, to);
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

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

                if (uiActor != null && !pinnedOrderOverride.isEmpty()) {
                    uiActor.setPinnedOrder(pinnedOrderOverride);
                }

                isReordering = false;
                if (pendingSubmit) {
                    pendingSubmit = false;
                    submitDialogs();
                }
            }
        });

        itemTouchHelper.attachToRecyclerView(rv);

        adapter.setOnDragListener(vh -> itemTouchHelper.startDrag(vh));

        adapter.setOnDialogClickListener(item ->{
            ChatFragment.newInstance(item.chatId, item.name)
                    .show(getParentFragmentManager(), "chat_sheet");
        });
    }

    @Override
    protected AbstractActor createActor() {
        uiActor = new DialogsActor();
        return uiActor;
    }

    // --------------------
    // UI ACTOR LOGIC
    // --------------------
    private class DialogsActor extends BaseUiActor {

        @Override
        protected void onReceiveMessage(Object message) {
            if (message instanceof TdMessages.ChatListUpdated) {
                List<TdApi.Chat> chats = ((TdMessages.ChatListUpdated) message).chats;
                pinnedOrderOverride = null;
                dialogs.clear();
                for (TdApi.Chat chat : chats) {
                    long order = getOrder(chat);
                    if (order != 0) {
                        dialogs.put(chat.id, new DialogItem(chat, order));
                    }
                }
                submitDialogs();
            }

            else if (message instanceof TdMessages.TdUpdate) {
                TdApi.Object update = ((TdMessages.TdUpdate) message).object;
                handleUpdate(update);
            }
        }

        private void handleUpdate(TdApi.Object update) {

            if (update instanceof TdApi.UpdateChatLastMessage) {
                TdApi.UpdateChatLastMessage u = (TdApi.UpdateChatLastMessage) update;
                updateChatInAdapter(u.chatId);
            }

            else if (update instanceof TdApi.UpdateChatPosition u) {
                if (u.position.list instanceof TdApi.ChatListMain) {
                    if (u.position.order == 0) {
                        if (dialogs.remove(u.chatId) != null) submitDialogs();
                        return;
                    }
                    DialogItem old = dialogs.get(u.chatId);
                    if (old != null) {
                        dialogs.put(u.chatId, old.copyWithOrderPinned(u.position.order, u.position.isPinned));
                        submitDialogs();
                    } else {
                        updateChatInAdapter(u.chatId);
                    }
                }
            }

            else if (update instanceof TdApi.UpdateChatReadInbox) {
                updateChatInAdapter(((TdApi.UpdateChatReadInbox) update).chatId);
            }

            else if (update instanceof TdApi.UpdateChatAction) {
                TdApi.UpdateChatAction u = (TdApi.UpdateChatAction) update;
                handleTyping(u.chatId);
            }

            else if (update instanceof TdApi.UpdateNewChat) {
                TdApi.Chat chat = ((TdApi.UpdateNewChat) update).chat;
                long order = getOrder(chat);
                if (order != 0) {
                    dialogs.put(chat.id, new DialogItem(chat, order));
                    submitDialogs();
                }
            }
        }

        private void updateChatInAdapter(long chatId) {
            long rand = new Random().nextLong();
            clientActorRef.tell(new TdMessages.SendWithId(rand, new TdApi.GetChat(chatId), self()));
        }

        @Override
        public void onReceive(Object message) {
            super.onReceive(message);

            if(message instanceof ActorRef){
                if(clientActorRef != null)
                    clientActorRef.tell(new TdMessages.LoadChats());
            }
            else if (message instanceof TdMessages.ResultWithId) {
                TdApi.Object res = ((TdMessages.ResultWithId) message).result;
                if (res instanceof TdApi.Chat) {
                    TdApi.Chat chat = (TdApi.Chat) res;
                    long order = getOrder(chat);
                    if (order == 0) {
                        dialogs.remove(chat.id);
                    } else {
                        DialogItem old = dialogs.get(chat.id);
                        DialogItem fresh = new DialogItem(chat, order);
                        if (old != null) fresh.isTyping = old.isTyping;
                        dialogs.put(chat.id, fresh);
                    }
                    submitDialogs();
                }
            }
        }

        private void handleTyping(long chatId) {
            DialogItem old = dialogs.get(chatId);
            if (old != null) {
                dialogs.put(chatId, old.copyWithTyping(true));
                submitDialogs();
                handler.postDelayed(() -> {
                    DialogItem cur = dialogs.get(chatId);
                    if (cur != null) {
                        dialogs.put(chatId, cur.copyWithTyping(false));
                        submitDialogs();
                    }
                }, 3000);
            }
        }

        public void setPinnedOrder(ArrayList<Long> pinnedIds) {
            if (clientActorRef == null) return;

            long[] ids = new long[pinnedIds.size()];
            for (int i = 0; i < pinnedIds.size(); ++i) ids[i] = pinnedIds.get(i);

            long rand = new Random().nextLong();
            clientActorRef.tell(new TdMessages.SendWithId(
                    rand,
                    new TdApi.SetPinnedChats(new TdApi.ChatListMain(), ids),
                    self()
            ));
        }
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

    private void updateRecyclerPadding(RecyclerView recycler) {
        recycler.setPadding(0, currentTop, 0, currentBottom);
    }
}