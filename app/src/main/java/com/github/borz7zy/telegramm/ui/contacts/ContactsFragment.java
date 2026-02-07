package com.github.borz7zy.telegramm.ui.contacts;

import android.os.Bundle;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.core.accounts.AccountSession;
import com.github.borz7zy.telegramm.core.accounts.AccountStorage;
import com.github.borz7zy.telegramm.ui.base.BaseTelegramFragment;
import com.github.borz7zy.telegramm.ui.model.ContactItem;
import com.github.borz7zy.telegramm.ui.widget.SpringRecyclerView;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ContactsFragment extends BaseTelegramFragment implements Client.ResultHandler {
    
    private ContactsAdapter adapter;
    private SpringRecyclerView recyclerView;

    private final Map<Long, ContactItem> contactsMap = new ConcurrentHashMap<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AccountSession currentSession;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recycler_contacts);
        setupRecyclerView();
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ContactsAdapter();
        recyclerView.setAdapter(adapter);

        adapter.setOnConctactClickListener(item -> {
            // TODO: open profile fragment
        });
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

                loadContacts();
            }
        });
    }

    private void loadContacts() {
        currentSession.send(new TdApi.GetContacts(), this);
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
        if (object instanceof TdApi.Users) {
            TdApi.Users users = (TdApi.Users) object;
            for (long userId : users.userIds) {
                currentSession.send(new TdApi.GetUser(userId), this);
            }
        }

        else if (object instanceof TdApi.User) {
            updateUser((TdApi.User) object);
        }

        else if (object instanceof TdApi.UpdateUser) {
            updateUser(((TdApi.UpdateUser) object).user);
        }

        else if (object instanceof TdApi.UpdateUserStatus) {
            TdApi.UpdateUserStatus update = (TdApi.UpdateUserStatus) object;
            if (contactsMap.containsKey(update.userId)) {
                currentSession.send(new TdApi.GetUser(update.userId), this);
            }
        }
    }

    private void updateUser(TdApi.User user) {
//        if (user.type instanceof TdApi.UserTypeDeleted) return; // SKIP 'Deleted Account'

        long id = user.id;
        String firstName = user.firstName != null ? user.firstName : "";
        String lastName = user.lastName != null ? user.lastName : "";
        String name = (firstName + " " + lastName).trim();

        String lastOnline = getUserStatusString(user.status);

        int avatarId = 0;
        String avatarPath = null;

        if (user.profilePhoto != null && user.profilePhoto.small != null) {
            avatarId = user.profilePhoto.small.id;
            if (user.profilePhoto.small.local != null) {
                avatarPath = user.profilePhoto.small.local.path;
            }
        }

        ContactItem item = new ContactItem(id, name, lastOnline, avatarId, avatarPath);

        contactsMap.put(id, item);
        refreshList();
    }

    private String getUserStatusString(TdApi.UserStatus status) {
        if (status instanceof TdApi.UserStatusOnline) {
            return "Online";
        } else if (status instanceof TdApi.UserStatusOffline) {
            return "Offline";
        } else if (status instanceof TdApi.UserStatusRecently) {
            return "Was recently";
        } else if(status instanceof TdApi.UserStatusEmpty) {
            return "Was too long time";
        }
        return "";
    }

    private void refreshList() {
        mainHandler.post(() -> {
            List<ContactItem> list = new ArrayList<>(contactsMap.values());

            Collections.sort(list, new Comparator<ContactItem>() {
                @Override
                public int compare(ContactItem o1, ContactItem o2) {
                    return o1.name.compareToIgnoreCase(o2.name);
                }
            });

            adapter.submitList(list);
        });
    }
}