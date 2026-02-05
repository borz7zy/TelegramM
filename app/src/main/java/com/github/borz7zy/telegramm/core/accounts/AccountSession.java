package com.github.borz7zy.telegramm.core.accounts;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.github.borz7zy.telegramm.R;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.io.File;
import java.util.Locale;

public class AccountSession {
    private final AccountEntity account;
    private final Context context;
    private Client client;

    private boolean tdlibParametersSent = false;
    private TdApi.AuthorizationState lastAuthState;

    public AccountSession(Context context, AccountEntity account){
        this.context = context;
        this.account = account;
        createClient();
    }

    private void createClient(){
        client = Client.create(this::onUpdate, null, null);

        client.send(new TdApi.GetAuthorizationState(), this::onUpdate);
    }

    private void onUpdate(TdApi.Object object){
        if (object instanceof TdApi.UpdateAuthorizationState) {
            processAuthState(((TdApi.UpdateAuthorizationState) object).authorizationState);
        } else if (object instanceof TdApi.AuthorizationState) {
            processAuthState((TdApi.AuthorizationState) object);
        } else if (object instanceof TdApi.Error) {
            Log.e("AccountSession", "TDLib Error [" + account.getAccountName() + "]: " + ((TdApi.Error) object).message);
        }
    }

    private void processAuthState(TdApi.AuthorizationState state) {
        lastAuthState = state;
        int constructor = state.getConstructor();

        if (constructor == TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR) {
            if (!tdlibParametersSent) {
                sendTdlibParameters();
            }
        } else if (constructor == TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR) {
            Log.d("AccountSession", "Account " + account.getAccountName() + " waiting for a phone number");
        } else if (constructor == TdApi.AuthorizationStateReady.CONSTRUCTOR) {
            Log.d("AccountSession", "Account " + account.getAccountName() + " ready to work!");
        }
    }

    // --------------------
    // Getters/Setters
    // --------------------

    private void sendTdlibParameters() {
        String dbPath = account.getAccountDbFolder();
        if (dbPath == null || dbPath.isEmpty()) {
            File rootDir = new File(context.getFilesDir(), "user_" + account.getAccountId());
            if (!rootDir.exists()) rootDir.mkdirs();
            dbPath = rootDir.getAbsolutePath();
        }

        TdApi.SetTdlibParameters request = new TdApi.SetTdlibParameters();

        request.databaseDirectory = dbPath;
        request.filesDirectory = dbPath + "/files";

        request.useMessageDatabase = true;
        request.useSecretChats = true;
        request.useFileDatabase = true;
        request.useChatInfoDatabase = true;

        try {
            request.apiId = Integer.parseInt(context.getString(R.string.api_id));
            request.apiHash = context.getString(R.string.api_hash);
        } catch (NumberFormatException e) {
            Log.e("AccountSession", "Error parsing api_id & api_hash. Check local.properties.");
            return;
        }

        request.systemLanguageCode = Locale.getDefault().getLanguage();
        request.deviceModel = Build.MODEL;
        request.systemVersion = Build.VERSION.RELEASE;
        request.applicationVersion = "1.0";

        request.useTestDc = true; // TODO

        client.send(request, result -> {
            if (result instanceof TdApi.Ok) {
                tdlibParametersSent = true;
            } else if (result instanceof TdApi.Error) {
                Log.e("AccountSession", "Error installing parameters: " + ((TdApi.Error) result).message);
            }
        });
    }

    public Client getClient() {
        return client;
    }
}
