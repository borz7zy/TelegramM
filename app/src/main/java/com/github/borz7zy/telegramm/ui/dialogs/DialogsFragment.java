package com.github.borz7zy.telegramm.ui.dialogs;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
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

    private void submitDialogs() {
        ArrayList<DialogItem> list = new ArrayList<>(dialogs.values());
        list.sort((a, b) -> Long.compare(b.order, a.order));
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

        adapter.setOnDialogClickListener(item ->{
            ChatFragment.newInstance(item.chatId, item.name)
                    .show(getParentFragmentManager(), "chat_sheet");
        });
    }

    @Override
    protected AbstractActor createActor() {
        return new DialogsActor();
    }

    // --------------------
    // UI ACTOR LOGIC
    // --------------------
    private class DialogsActor extends BaseUiActor {

        @Override
        protected void onReceiveMessage(Object message) {
            if (message instanceof TdMessages.ChatListUpdated) {
                List<TdApi.Chat> chats = ((TdMessages.ChatListUpdated) message).chats;
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

            else if (update instanceof TdApi.UpdateChatPosition) {
                TdApi.UpdateChatPosition u = (TdApi.UpdateChatPosition) update;
                if (u.position.list instanceof TdApi.ChatListMain) {
                    if (u.position.order == 0) {
                        if (dialogs.remove(u.chatId) != null) submitDialogs();
                        return;
                    }
                    DialogItem old = dialogs.get(u.chatId);
                    if (old != null) {
                        dialogs.put(u.chatId, old.copyWithOrder(u.position.order));
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