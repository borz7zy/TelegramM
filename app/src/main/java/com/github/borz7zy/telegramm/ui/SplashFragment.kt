package com.github.borz7zy.telegramm.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.github.borz7zy.telegramm.AppManager;
import com.github.borz7zy.telegramm.background.AsyncTask;
import com.github.borz7zy.telegramm.core.accounts.AccountEntity;
import com.github.borz7zy.telegramm.core.accounts.AccountSession;
import com.github.borz7zy.telegramm.core.accounts.AccountStorage;
import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.ui.base.BaseTelegramFragment;
import com.github.borz7zy.telegramm.utils.Logger;

import org.drinkless.tdlib.TdApi;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class SplashFragment extends BaseTelegramFragment {
    private TextView splashText;
    private TdApi.AuthorizationState latestState;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        startAsyncWaitState();
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
                if (!isAdded()) return;

                if (c < fullText.length()) {
                    currentText.append(fullText.charAt(c));
                    splashText.setText(currentText.toString() + "|");

                    ++c;
                    mainHandler.postDelayed(this, charDelay);
                } else {
                    startBlinkingCursor(fullText);
                }
            }
        }.run();
    }

    private void startBlinkingCursor(String text) {
        mainHandler.postDelayed(new Runnable() {
            boolean showCursor = false;

            @Override
            public void run() {
                if (!isAdded()) return;

                splashText.setText(showCursor ? text + "|" : text);
                showCursor = !showCursor;

                mainHandler.postDelayed(this, 500);
            }
        }, 500);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onAuthStateChanged(TdApi.AuthorizationState state) {
        latestState = state;
        Logger.LOGD("SplashFragment", "onAuthStateChanged: " + state.getClass().getSimpleName());
    }

    private void startAsyncWaitState() {
        new AsyncTask<Void, Void, TdApi.AuthorizationState>() {

            @Override
            protected TdApi.AuthorizationState doInBackground(Void... params) throws Throwable {
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<TdApi.AuthorizationState> result = new AtomicReference<>();

                AppManager.getInstance().getExecutorDb().execute(() -> {
                    AccountStorage.getInstance().getCurrentActive(account -> {
                        if (account == null) {
                            AppManager.getInstance().getExecutorDb().execute(() -> {
                                AccountEntity newAccount = new AccountEntity(null, 0L, "New Account", "");
                                long newId = AppManager.getInstance().getAppDatabase().accountDao().insert(newAccount);
                                newAccount.setAccountId((int) newId);
                                AccountStorage.getInstance().setCurrentActive(newAccount.getAccountId());
                                Logger.LOGD("SplashFragment", "Created new account ID: " + newAccount.getAccountId());

                                AccountSession session = AccountManager.getInstance().getOrCreateSession(newAccount);

                                mainHandler.post(() -> subscribeAuthState(session, latch, result));
                            });
                        } else {
                            AccountSession session = AccountManager.getInstance().getOrCreateSession(account);
                            Logger.LOGD("SplashFragment", "Session created for account: " + account.getAccountId());
                            mainHandler.post(() -> subscribeAuthState(session, latch, result));
                        }
                    });
                });

                latch.await();
                return result.get();
            }

            private void subscribeAuthState(AccountSession session, CountDownLatch latch, AtomicReference<TdApi.AuthorizationState> result){
                AtomicBoolean counted = new AtomicBoolean(false);

                session.observeAuthState().observe(getViewLifecycleOwner(), state -> {
                    Logger.LOGD("SplashFragment", "observeAuthState update: " + state.getClass().getSimpleName());

                    if (state instanceof TdApi.AuthorizationStateWaitTdlibParameters) {
                        return;
                    }

                    if(state != null && counted.compareAndSet(false, true)){
                        result.set(state);
                        latch.countDown();
                    }
                });
            }

            @Override
            protected void onPostExecute(TdApi.AuthorizationState state) {
                if (!isAdded()) return;

                Logger.LOGD("SplashFragment", "AsyncTask finished with state: " + (state != null ? state.getClass().getSimpleName() : "null"));

                NavController nav = NavHostFragment.findNavController(SplashFragment.this);

                if (state instanceof TdApi.AuthorizationStateReady) {
                    Logger.LOGD("SplashFragment", "Navigate to Main");
                    nav.navigate(R.id.frag_splash_to_main);
                } else if (state instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
                    Logger.LOGD("SplashFragment", "Navigate to Phone");
                    nav.navigate(R.id.action_splashFragment_to_authPhoneFragment);
                } else if (state instanceof TdApi.AuthorizationStateWaitCode) {
                    Logger.LOGD("SplashFragment", "Navigate to Code");
                    nav.navigate(R.id.action_splashFragment_to_authCodeFragment);
                } else if (state instanceof TdApi.AuthorizationStateWaitPassword) {
                    Logger.LOGD("SplashFragment", "Navigate to Password");
                    nav.navigate(R.id.action_splashFragment_to_authPasswordFragment);
                } else {
                    Logger.LOGD("SplashFragment", "Unknown auth state: " + (state != null ? state.getClass().getSimpleName() : "null"));
                }
            }
        }.execPool();
    }
}
