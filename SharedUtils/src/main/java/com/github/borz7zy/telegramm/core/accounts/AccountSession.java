package com.github.borz7zy.telegramm.core.accounts;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.github.borz7zy.telegramm.AppManager;
import com.github.borz7zy.telegramm.background.AsyncTask;
import com.github.borz7zy.telegramm.core.TgConfig;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.Client.ResultHandler;
import org.drinkless.tdlib.TdApi;
import org.drinkless.tdlib.TdApi.AuthorizationState;
import org.drinkless.tdlib.TdApi.GetAuthorizationState;
import org.drinkless.tdlib.TdApi.GetMe;
import org.drinkless.tdlib.TdApi.Ok;
import org.drinkless.tdlib.TdApi.SetTdlibParameters;
import org.drinkless.tdlib.TdApi.UpdateAuthorizationState;

import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class AccountSession {

    private static final String TAG = "AccountSession";

    private final Context context;
    private final AccountEntity account;

    private volatile Client client = null;

    private final AtomicBoolean isInitializing = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<Runnable> pendingActions = new ConcurrentLinkedQueue<>();

    private boolean tdlibParametersSent = false;
    private boolean meRequested = false;

    private AuthorizationState lastAuthState = null;

    private final MutableLiveData<AuthorizationState> authStateLiveData = new MutableLiveData<>();

    private final MutableLiveData<TdApi.User> currentUserLiveData = new MutableLiveData<>();

    private volatile long myUserId = 0;

    private final CopyOnWriteArrayList<ResultHandler> updateHandlers = new CopyOnWriteArrayList<>();

    private volatile TdApi.UpdateChatFolders lastChatFoldersUpdate = null;

    public AccountSession(Context context, AccountEntity account) {
        this.context = context;
        this.account = account;
    }

    public LiveData<AuthorizationState> getAuthStateLiveData() {
        ensureClient();
        return authStateLiveData;
    }

    public LiveData<TdApi.User> getCurrentUserLiveData() {
        return currentUserLiveData;
    }

    private void ensureClient() {
        if (client != null) return;

        if (isInitializing.get()) return;

        if (isInitializing.compareAndSet(false, true)) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        Client newClient = Client.create(
                                update -> onUpdate(update),
                                null,
                                null
                        );

                        newClient.send(new GetAuthorizationState(), update -> onUpdate(update));

                        client = newClient;

                        while (!pendingActions.isEmpty()) {
                            Runnable action = pendingActions.poll();
                            if (action != null) {
                                action.run();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error init TdLib: " + e.getMessage());
                        isInitializing.set(false);
                    }
                    return null;
                }
            }.execPool();
        }
    }

    private void emitAuthState(AuthorizationState state) {
        lastAuthState = state;

        if (state != null) {
            authStateLiveData.postValue(state);
        }
    }

    private void onUpdate(Object update) {
        if (update instanceof UpdateAuthorizationState) {
            AuthorizationState state = ((UpdateAuthorizationState) update).authorizationState;
            emitAuthState(state);

            switch (state.getConstructor()) {
                case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR:
                    if (!tdlibParametersSent) {
                        sendTdlibParameters();
                    }
                    break;

                case TdApi.AuthorizationStateReady.CONSTRUCTOR:
                    loadMeOnce();
                    break;
            }
        }

        if (update instanceof TdApi.UpdateChatFolders) {
            lastChatFoldersUpdate = (TdApi.UpdateChatFolders) update;
        }

        if (update instanceof TdApi.UpdateUser) {
            TdApi.User u = ((TdApi.UpdateUser) update).user;
            if (u != null && u.id == myUserId && myUserId != 0L) {
                currentUserLiveData.postValue(u);
            }
        }

        if (update instanceof TdApi.Object) {
            for (ResultHandler handler : updateHandlers) {
                handler.onResult((TdApi.Object) update);
            }
        }
    }

    public void send(TdApi.Function<?> function) {
        Client currentClient = client;
        if (currentClient != null) {
            currentClient.send(function, result -> {
                if (result instanceof TdApi.Error) {
                    Log.e(TAG, "Error: " + ((TdApi.Error) result).message);
                }
            });
        } else {
            pendingActions.add(() -> {
                Client c = client;
                if (c != null) {
                    c.send(function, result -> {
                        if (result instanceof TdApi.Error) {
                            Log.e(TAG, "Error: " + ((TdApi.Error) result).message);
                        }
                    });
                }
            });
            ensureClient();
        }
    }

    public void send(TdApi.Function<?> function, ResultHandler handler) {
        Client currentClient = client;
        if (currentClient != null) {
            currentClient.send(function, handler);
        } else {
            pendingActions.add(() -> {
                Client c = client;
                if (c != null) {
                    c.send(function, handler);
                }
            });
            ensureClient();
        }
    }

    public void sendAwait(TdApi.Function<?> function, ResultHandler handler) {
        Client currentClient = client;
        if (currentClient != null) {
            currentClient.send(function, handler);
        } else {
            pendingActions.add(() -> {
                Client c = client;
                if (c != null) {
                    c.send(function, handler);
                }
            });
            ensureClient();
        }
    }

    private void loadMeOnce() {
        if (meRequested) return;
        meRequested = true;

        client.send(new GetMe(), result -> {
            if (result instanceof TdApi.User) {
                TdApi.User user = (TdApi.User) result;
                myUserId = user.id;
                currentUserLiveData.postValue(user);

                AppManager.getInstance()
                        .getExecutorDb()
                        .execute(() -> {
                            account.setAccountTgId(user.id);
                            account.setAccountName(user.firstName + " " + user.lastName);
                            account.setAccountUsername(user.phoneNumber);
                            AppManager.getInstance()
                                    .getAppDatabase()
                                    .accountDao()
                                    .update(account);
                        });
            }
        });
    }

    private void sendTdlibParameters() {
        String dbPath = account.getAccountDbFolder();
        if (dbPath == null || dbPath.isEmpty()) {
            File rootDir = new File(
                    context.getFilesDir(),
                    "user_" + account.getAccountId()
            );
            if (!rootDir.exists()) rootDir.mkdirs();
            dbPath = rootDir.getAbsolutePath();
        }

        SetTdlibParameters request = new SetTdlibParameters();

        request.databaseDirectory = dbPath;
        request.filesDirectory = dbPath + "/files";

        request.useMessageDatabase = true;
        request.useSecretChats = true;
        request.useFileDatabase = true;
        request.useChatInfoDatabase = true;

        TgConfig config = AppManager.getInstance().getConfig();
        if (config == null) {
            Log.e(TAG, "TgConfig is missing; cannot send TDLib parameters");
            return;
        }
        request.apiId = config.getApiId();
        request.apiHash = config.getApiHash();

        request.systemLanguageCode =
                Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage();

        request.deviceModel =
                Build.MODEL != null ? Build.MANUFACTURER + " " + Build.MODEL : "Virtual Machine on Android";

        String system = Build.VERSION.BASE_OS.isEmpty() ? "Android " : Build.VERSION.BASE_OS + " ";
        request.systemVersion = system + Build.VERSION.RELEASE + " (SDK: " + Build.VERSION.SDK_INT + ")";

        request.applicationVersion = config.getApplicationVersion();

        request.useTestDc = config.isTestDc();

        client.send(request, result -> {
            if (result instanceof Ok) {
                tdlibParametersSent = true;
            } else if (result instanceof TdApi.Error) {
                Log.e(TAG, "TDLib params error: " + ((TdApi.Error) result).message);
            }
        });
    }

    public void addUpdateHandler(ResultHandler handler) {
        updateHandlers.add(handler);
        TdApi.UpdateChatFolders folders = lastChatFoldersUpdate;
        if (folders != null) {
            handler.onResult(folders);
        }
    }

    public void removeUpdateHandler(ResultHandler handler) {
        updateHandlers.remove(handler);
    }

    public Client getClient() {
        ensureClient();
        return client;
    }
}
