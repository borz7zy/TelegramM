package com.github.borz7zy.telegramm.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.github.borz7zy.telegramm.core.accounts.AccountEntity;
import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.core.accounts.AccountSession;
import com.github.borz7zy.telegramm.core.accounts.AccountStorage;
import com.github.borz7zy.telegramm.utils.SingleLiveEvent;

import org.drinkless.tdlib.TdApi;
import org.drinkless.tdlib.TdApi.AuthorizationState;

public class AuthViewModel extends ViewModel {

    // --------------------
    // UI state
    // --------------------
    public static abstract class UiState {
        public static final class Phone extends UiState {
        }

        public static final class Code extends UiState {
        }

        public static final class Password extends UiState {
            public final String hint;

            public Password(String hint) {
                this.hint = hint;
            }
        }

        public static final class Loading extends UiState {
        }
    }

    // --------------------
    // One-shot events
    // --------------------
    public static abstract class Event {
        public static final class NavigateToMain extends Event {
        }

        public static final class ShowError extends Event {
            public final String message;

            public ShowError(String message) {
                this.message = message;
            }
        }

        public static final class ShowToast extends Event {
            public final String message;

            public ShowToast(String message) {
                this.message = message;
            }
        }
    }

    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(new UiState.Loading());
    private final SingleLiveEvent<Event> events = new SingleLiveEvent<>();

    private UiState lastStableState = new UiState.Phone();
    private AccountSession session;

    private LiveData<AccountEntity> activeAccountLiveData;
    private Observer<AccountEntity> activeAccountObserver;

    private LiveData<AuthorizationState> authStateLiveData;
    private Observer<AuthorizationState> authStateObserver;

    public AuthViewModel() {
        resolveSession();
    }

    public LiveData<UiState> getUiState() {
        return uiState;
    }

    public LiveData<Event> getEvents() {
        return events;
    }

    private void resolveSession() {
        AccountStorage storage = AccountStorage.getInstance();
        storage.ensureFirstAccountExists();

        activeAccountLiveData = storage.observeActiveAccount();
        activeAccountObserver = account -> {
            if (account == null) {
                return;
            }
            // Take the first non-null active account, then stop observing.
            activeAccountLiveData.removeObserver(activeAccountObserver);
            activeAccountObserver = null;

            session = AccountManager.getInstance().getOrCreateSession(account);
            observeAuthState();
        };
        activeAccountLiveData.observeForever(activeAccountObserver);
    }

    private void observeAuthState() {
        // getAuthStateLiveData() triggers TDLib client initialization.
        authStateLiveData = session.getAuthStateLiveData();
        authStateObserver = state -> {
            if (state instanceof TdApi.AuthorizationStateWaitPhoneNumber) {
                updateState(new UiState.Phone());
            } else if (state instanceof TdApi.AuthorizationStateWaitCode) {
                updateState(new UiState.Code());
            } else if (state instanceof TdApi.AuthorizationStateWaitPassword) {
                updateState(new UiState.Password(
                        ((TdApi.AuthorizationStateWaitPassword) state).passwordHint));
            } else if (state instanceof TdApi.AuthorizationStateReady) {
                events.postValue(new Event.NavigateToMain());
            }
        };
        authStateLiveData.observeForever(authStateObserver);
    }

    public void onMainAction(String phone, String code, String password) {
        UiState state = uiState.getValue();
        if (state instanceof UiState.Phone) {
            sendPhone(phone);
        } else if (state instanceof UiState.Code) {
            sendCode(code);
        } else if (state instanceof UiState.Password) {
            sendPassword(password);
        }
    }

    public void onSecondaryAction() {
        UiState state = uiState.getValue();
        if (state instanceof UiState.Code) {
            updateState(new UiState.Phone());
        } else if (state instanceof UiState.Password) {
            recoverPassword();
        }
    }

    private void sendPhone(String phone) {
        if (isBlank(phone)) {
            emitError("Enter phone number");
            return;
        }

        launchTdRequest(new TdApi.SetAuthenticationPhoneNumber(phone, null));
    }

    private void sendCode(String code) {
        if (isBlank(code)) {
            emitError("Enter verification code");
            return;
        }

        launchTdRequest(new TdApi.CheckAuthenticationCode(code));
    }

    private void sendPassword(String password) {
        if (isBlank(password)) {
            emitError("Enter password");
            return;
        }

        launchTdRequest(new TdApi.CheckAuthenticationPassword(password));
    }

    private void recoverPassword() {
        if (session == null) {
            emitError("Session not ready");
            return;
        }

        session.send(new TdApi.RequestAuthenticationPasswordRecovery(), result -> {
            if (result instanceof TdApi.Error) {
                emitError(((TdApi.Error) result).message);
            } else {
                events.postValue(new Event.ShowToast("Recovery email sent"));
            }
        });
    }

    private void launchTdRequest(TdApi.Function<?> function) {
        if (session == null) {
            emitError("Session not ready");
            return;
        }

        lastStableState = uiState.getValue();
        uiState.setValue(new UiState.Loading());

        session.send(function, result -> {
            if (result instanceof TdApi.Error) {
                emitError(((TdApi.Error) result).message);
                uiState.postValue(lastStableState);
            }
        });
    }

    private void updateState(UiState state) {
        lastStableState = state;
        uiState.setValue(state);
    }

    private void emitError(String message) {
        events.postValue(new Event.ShowError(message));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (activeAccountLiveData != null && activeAccountObserver != null) {
            activeAccountLiveData.removeObserver(activeAccountObserver);
        }
        if (authStateLiveData != null && authStateObserver != null) {
            authStateLiveData.removeObserver(authStateObserver);
        }
    }
}
