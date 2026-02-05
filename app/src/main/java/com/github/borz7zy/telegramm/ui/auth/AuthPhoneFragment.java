package com.github.borz7zy.telegramm.ui.auth;

import static androidx.navigation.Navigation.findNavController;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.github.borz7zy.telegramm.AppManager;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.core.accounts.AccountEntity;
import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.core.accounts.AccountSession;
import com.github.borz7zy.telegramm.core.accounts.AccountStorage;

import org.drinkless.tdlib.TdApi;

public class AuthPhoneFragment extends Fragment {
    private static final String TAG = "AuthPhoneFragment";

    private EditText phoneEdit;
    private Button nextBtn;
    private AccountSession session;
    private boolean creatingAccount = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_auth_phone, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState){
        super.onViewCreated(view, savedInstanceState);

        phoneEdit = view.findViewById(R.id.phoneEdit);
        nextBtn = view.findViewById(R.id.nextBtn);
        NavController navController = findNavController(view);

        observeActiveAccount(navController);

        nextBtn.setOnClickListener(v -> {
            String phone = phoneEdit.getText().toString();
            if(session == null){
                Toast.makeText(requireContext(),"Session not ready",Toast.LENGTH_SHORT).show();
                return;
            }
            if(phone.isEmpty()){
                return;
            }
            TdApi.SetAuthenticationPhoneNumber req =
                    new TdApi.SetAuthenticationPhoneNumber();
            req.phoneNumber = phone;
            session.send(req);
        });
    }

    // --------------------
    // Observe account
    // --------------------

    private void observeActiveAccount(NavController navController){

        AccountStorage.getInstance()
                .observeActiveAccount()
                .observe(getViewLifecycleOwner(), account -> {

                    if(account == null && !creatingAccount) {
                        creatingAccount = true;
                        createNewAccount(navController);
                        return;
                    }

                    session =
                            AccountManager.getInstance()
                                    .getOrCreateSession(account);
                    observeAuthState(session, navController);
                });
    }

    private void createNewAccount(NavController navController) {
        AppManager.getInstance().getExecutorDb().execute(() -> {
            AccountEntity newAccount = new AccountEntity(
                    null,
                    0L,
                    "New Account",
                    ""
            );

            long newId = AppManager.getInstance().getAppDatabase().accountDao().insert(newAccount);
            newAccount.setAccountId((int)newId);

            AccountStorage.getInstance().setCurrentActive(newAccount.getAccountId());

            requireActivity().runOnUiThread(() -> {
                session = AccountManager.getInstance().getOrCreateSession(newAccount);
                observeAuthState(session, navController);
                Log.d(TAG, "New account created with ID: " + newAccount.getAccountId());
            });
        });
    }

    private void observeAuthState(AccountSession session,
                                  NavController navController){
        session.observeAuthState()
                .observe(getViewLifecycleOwner(), state -> {
                    if(state instanceof TdApi.AuthorizationStateWaitCode){
                        if(navController.getCurrentDestination()!=null &&
                                navController.getCurrentDestination().getId()
                                        == R.id.authPhoneFragment){
                            navController.navigate(R.id.frag_phone_to_code);
                        }
                    } else if(state instanceof TdApi.AuthorizationStateWaitPassword){
                        // TODO
                    } else if(state instanceof TdApi.AuthorizationStateReady){
                        updateAccountWithMe(session);
//                         navController.navigate(R.id.action_authPhoneFragment_to_mainFragment);
                    }
                });
    }

    private void updateAccountWithMe(AccountSession session) {
        TdApi.GetMe getMe = new TdApi.GetMe();
        session.send(getMe);
    }
}
