package com.github.borz7zy.telegramm.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.github.borz7zy.telegramm.AppManager;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.core.accounts.AccountEntity;
import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.core.accounts.AccountSession;
import com.github.borz7zy.telegramm.core.accounts.AccountStorage;
import com.github.borz7zy.telegramm.core.settings.SettingsEntity;

import org.drinkless.tdlib.TdApi;

import java.util.List;

public class SplashFragment extends Fragment {

    private TextView splashText;

    private boolean isAnimationDone = false;
    private TdApi.AuthorizationState pendingState = null;

    private LiveData<TdApi.AuthorizationState> currentAuthLiveData;

    private boolean navigated = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable blinkingRunnable;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_splash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        splashText = view.findViewById(R.id.splashText);

        startTypewriterEffect();

        checkAndCreateFirstAccountIfNeeded();

        observeActiveAccount();
    }

    private void startTypewriterEffect() {
        String fullText = getString(R.string.app_name);
        splashText.setText("|");

        long totalDuration = 2000;
        long charDelay = totalDuration / fullText.length();

        new Runnable() {
            int c = 0;
            StringBuilder currentText = new StringBuilder();

            @Override
            public void run() {
                if (getContext() == null) return;

                if (c < fullText.length()) {
                    currentText.append(fullText.charAt(c));

                    splashText.setText(currentText.toString() + "|");

                    ++c;
                    mainHandler.postDelayed(this, charDelay);
                } else {
                    isAnimationDone = true;
                    startBlinkingCursor(fullText);

                    checkAndNavigate();
                }
            }
        }.run();
    }

    private void checkAndCreateFirstAccountIfNeeded() {
        AppManager.getInstance().getExecutorDb().execute(() -> {
            SettingsEntity settings = AppManager.getInstance()
                    .getAppDatabase()
                    .settingsDao()
                    .getSettings();

            if (settings != null && settings.currentActiveId != null) {
                // Уже есть активный аккаунт → ничего не делаем
                return;
            }

            // Проверяем, есть ли хоть какие-то аккаунты
            List<AccountEntity> accounts = AppManager.getInstance()
                    .getAppDatabase()
                    .accountDao()
                    .getAllAccounts();

            Integer activeId = null;

            if (!accounts.isEmpty()) {
                // Есть аккаунты, но не выбран активный → берём первый
                activeId = accounts.get(0).getAccountId();
            } else {
                // Совсем нет аккаунтов → создаём новый
                AccountEntity newAccount = new AccountEntity(null, 0L, "New User", "");
                AppManager.getInstance().getAppDatabase().accountDao().insert(newAccount);
                // После вставки accountId уже сгенерирован
                activeId = newAccount.getAccountId();
            }

            if (activeId != null) {
                AccountStorage.getInstance().setCurrentActive(activeId);
            }

            // После этого LiveData в AuthPhoneFragment и SplashFragment увидит аккаунт
        });
    }

    private void observeActiveAccount(){

        AccountStorage.getInstance()
                .observeActiveAccount()
                .observe(getViewLifecycleOwner(), account -> {

                    if(account == null){
                        pendingState = new TdApi.AuthorizationStateWaitPhoneNumber();
                        checkAndNavigate();
                        return;
                    }

                    AccountSession session =
                            AccountManager.getInstance()
                                    .getOrCreateSession(account);

                    observeSession(session);
                });
    }

    private void observeSession(AccountSession session){

        if(currentAuthLiveData != null){
            currentAuthLiveData.removeObservers(getViewLifecycleOwner());
        }

        currentAuthLiveData = session.observeAuthState();

        currentAuthLiveData.observe(getViewLifecycleOwner(), state -> {

            pendingState = state;
            checkAndNavigate();
        });
    }

    private void startBlinkingCursor(String text) {
        blinkingRunnable = new Runnable() {
            boolean showCursor = false;

            @Override
            public void run() {
                if (getContext() == null) return;

                if (showCursor) {
                    splashText.setText(text + "|");
                } else {
                    splashText.setText(text);
                }
                showCursor = !showCursor;

                mainHandler.postDelayed(this, 500);
            }
        };
        mainHandler.postDelayed(blinkingRunnable, 500);
    }

    private void checkAndNavigate(){
        if(navigated) return;
        if(isAnimationDone && pendingState != null){
            navigated = true;
            NavController nav =
                    Navigation.findNavController(requireView());
            if (pendingState instanceof TdApi.AuthorizationStateReady){
                nav.navigate(R.id.action_splashFragment_to_mainFragment);
            }else{
                nav.navigate(R.id.action_splashFragment_to_authPhoneFragment);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);
    }
}