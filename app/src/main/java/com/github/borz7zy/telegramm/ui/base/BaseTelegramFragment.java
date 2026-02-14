package com.github.borz7zy.telegramm.ui.base;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;

import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.core.accounts.AccountSession;
import com.github.borz7zy.telegramm.core.accounts.AccountStorage;

import org.drinkless.tdlib.TdApi;

public abstract class BaseTelegramFragment extends Fragment {
    private final Class<?> clazz = this.getClass();
    private final String TAG = clazz.getSimpleName();

    protected AccountSession session;
    private LiveData<TdApi.AuthorizationState> authLiveData;

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        observeActiveAccount();
    }

    // --------------------
    // Observe account
    // --------------------

    private void observeActiveAccount(){

        AccountStorage.getInstance()
                .observeActiveAccount()
                .observe(getViewLifecycleOwner(), account -> {

                    if(account == null){
                        AccountStorage.getInstance()
                                .ensureFirstAccountExists();
                        return;
                    }

                    session =
                            AccountManager.getInstance()
                                    .getOrCreateSession(account);

                    onSessionReady(session);

                    observeAuthState(session);
                });
    }

    private void observeAuthState(AccountSession session) {

        if (authLiveData != null) {
            authLiveData.removeObservers(getViewLifecycleOwner());
        }

        authLiveData = session.observeAuthState();

        authLiveData.observe(getViewLifecycleOwner(), state -> {

            onAuthStateChanged(state);

            if (state instanceof TdApi.AuthorizationStateReady) {
                onAuthorized();
            }
        });
    }

    protected void onSessionReady(AccountSession session){}

    protected void onAuthStateChanged(TdApi.AuthorizationState state){}

    protected void onAuthorized(){}
}
