package com.github.borz7zy.telegramm.core.accounts;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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
    private final MutableLiveData<TdApi.AuthorizationState> authStateLiveData =
            new MutableLiveData<>();

    public AccountSession(Context context, AccountEntity account){
        this.context = context;
        this.account = account;
    }

    private synchronized void ensureClient(){
        if(client != null) return;

        client = Client.create(this::onUpdate, null, null);
        client.send(new TdApi.GetAuthorizationState(), this::onUpdate);
    }

    public LiveData<TdApi.AuthorizationState> observeAuthState(){

        ensureClient();

        if(lastAuthState != null){
            authStateLiveData.postValue(lastAuthState);
        }

        return authStateLiveData;
    }

    private void onUpdate(Object update){
        if(update instanceof TdApi.UpdateAuthorizationState){

            TdApi.AuthorizationState state =
                    ((TdApi.UpdateAuthorizationState)update).authorizationState;

            authStateLiveData.postValue(state);

            switch(state.getConstructor()){

                case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR:
                    sendTdlibParameters();
                    break;
            }
        }
    }

    private void processAuthState(TdApi.AuthorizationState state) {
        lastAuthState = state;
        authStateLiveData.postValue(state);

        if (state instanceof TdApi.AuthorizationStateWaitTdlibParameters) {
            if (!tdlibParametersSent) {
                sendTdlibParameters();
            }
        }
    }

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
                Log.e("AccountSession", "Error installing parameters: " + ((TdApi.Error) result).message + " : " + ((TdApi.Error) result).toString());
            }
        });
    }

    public void send(TdApi.Function<?> function) {

        ensureClient();

        client.send(function, result -> {

            if(result instanceof TdApi.Error){
                // TODO
            }

        });
    }

    public void send(TdApi.Function<?> function,
                     Client.ResultHandler handler) {

        ensureClient();

        client.send(function, handler);
    }


    // --------------------
    // Getters/Setters
    // --------------------

    public Client getClient(){
        ensureClient();
        return client;
    }
}
