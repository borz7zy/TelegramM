package com.github.borz7zy.telegramm.ui.contacts;

import android.os.Bundle;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.actor.AbstractActor;
import com.github.borz7zy.telegramm.actor.ActorRef;
import com.github.borz7zy.telegramm.core.TdMessages;
import com.github.borz7zy.telegramm.ui.base.BaseTdFragment;
import com.github.borz7zy.telegramm.ui.model.ContactItem;
import com.github.borz7zy.telegramm.ui.widget.SpringRecyclerView;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ContactsFragment extends BaseTdFragment {
    
    private ContactsAdapter adapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SpringRecyclerView rv = view.findViewById(R.id.recycler_contacts);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ContactsAdapter();
        rv.setAdapter(adapter);

        adapter.setOnConctactClickListener(item->{
            // TODO: open profile fragment
        });
    }

    @Override
    protected AbstractActor createActor() {
        return new ContactsActor();
    }

    // --------------------
    // UI ACTOR LOGIC
    // --------------------
    private class ContactsActor extends BaseUiActor {

        @Override
        protected void onReceiveMessage(Object message) {
            if(message instanceof TdMessages.TdUpdate){
                TdApi.Object update = ((TdMessages.TdUpdate) message).object;
                handleUpdate(update);
            }
        }

        private void handleUpdate(TdApi.Object update){
            if(update instanceof TdApi.UpdateUser){
                TdApi.User u = ((TdApi.UpdateUser) update).user;
                updateOrAddContact(u);
            }
        }

        private void updateOrAddContact(TdApi.User user){
            long id = user.id;
            String name = user.firstName + (user.lastName != null ? " " + user.lastName : "");
            String lastOnline = user.status instanceof TdApi.UserStatusOnline ? "Online" : "";
            int avatarId = user.profilePhoto != null ? user.profilePhoto.small.id : 0;
            String avatarPath = user.profilePhoto != null ? user.profilePhoto.small.local.path : null;

            ContactItem item = new ContactItem(id, name, lastOnline, avatarId, avatarPath);

            List<ContactItem> current = new ArrayList<>(adapter.items);
            boolean found = false;
            for(int i = 0; i < current.size(); ++i){
                if(current.get(i).userId == id){
                    current.set(i, item);
                    found = true;
                    break;
                }
            }
            if(!found) current.add(item);

            adapter.submitList(current);
        }

        @Override
        public void onReceive(Object message) {
            super.onReceive(message);

            Log.d("ContactsFragment", message.toString());

            if (message instanceof ActorRef) {
                if (clientActorRef != null) {
                    long rand = new Random().nextLong();
                    clientActorRef.tell(new TdMessages.SendWithId(
                            rand,
                            new TdApi.GetContacts(),
                            self()
                    ));
                    Log.d("ContactsFragment", "Loading contacts");
                }
            }
            else if (message instanceof TdMessages.ResultWithId) {
                TdApi.Object res = ((TdMessages.ResultWithId) message).result;

                Log.d("ContactsFragment 3", res.toString());

                if (res instanceof TdApi.Users) {
                    TdApi.Users users = (TdApi.Users) res;

                    List<ContactItem> contactList = new ArrayList<>();
                    for (long id : users.userIds) {
                        long rand = new Random().nextLong();
                        clientActorRef.tell(new TdMessages.SendWithId(rand, new TdApi.GetUser(id), self()));
                    }
                }
                else if (res instanceof TdApi.User) {
                    TdApi.User user = (TdApi.User) res;
                    updateOrAddContact(user);
                }
            }
        }

    }
}