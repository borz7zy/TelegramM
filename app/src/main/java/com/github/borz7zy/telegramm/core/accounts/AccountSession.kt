package com.github.borz7zy.telegramm.core.accounts

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.github.borz7zy.telegramm.AppManager
import com.github.borz7zy.telegramm.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.Client.ResultHandler
import org.drinkless.tdlib.TdApi
import org.drinkless.tdlib.TdApi.AuthorizationState
import org.drinkless.tdlib.TdApi.GetAuthorizationState
import org.drinkless.tdlib.TdApi.GetMe
import org.drinkless.tdlib.TdApi.Ok
import org.drinkless.tdlib.TdApi.SetTdlibParameters
import org.drinkless.tdlib.TdApi.UpdateAuthorizationState
import java.io.File
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class AccountSession(private val context: Context, private val account: AccountEntity) {
    private var client: Client? = null
    private var tdlibParametersSent = false
    private var meRequested = false

    private var lastAuthState: AuthorizationState? = null

    private val _authStateFlow = MutableStateFlow<AuthorizationState?>(null)
    val authStateFlow: StateFlow<AuthorizationState?>
        get() {
            ensureClient()
            return _authStateFlow
        }

    val authStateLiveData =
        MutableLiveData<AuthorizationState>()

    private val updateHandlers = CopyOnWriteArrayList<ResultHandler>()

    @Synchronized
    private fun ensureClient() {
        if (client != null) return

        client = Client.create(
            { update -> onUpdate(update) },
            null,
            null
        )

        client!!.send(
            GetAuthorizationState()
        ) { update -> onUpdate(update) }
    }

    private fun emitAuthState(state: AuthorizationState?) {
        lastAuthState = state
        _authStateFlow.value = state

        if (state != null) {
            authStateLiveData.postValue(state)
        }
    }

    private fun onUpdate(update: Any?) {
        if (update is UpdateAuthorizationState) {
            val state = update.authorizationState
            emitAuthState(state)

            when (state.constructor) {
                TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
                    if (!tdlibParametersSent) {
                        sendTdlibParameters()
                    }
                }

                TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                    loadMeOnce()
                }
            }
        }

        if (update is TdApi.Object) {
            for (handler in updateHandlers) {
                handler.onResult(update)
            }
        }
    }

    fun send(function: TdApi.Function<*>) {
        ensureClient()
        client!!.send(function) { result ->
            if (result is TdApi.Error) {
                Log.e("AccountSession", result.message)
            }
        }
    }

    fun send(
        function: TdApi.Function<*>,
        handler: ResultHandler
    ) {
        ensureClient()
        client!!.send(function, handler)
    }

    fun sendAwait(
        function: TdApi.Function<*>?,
        handler: ResultHandler?
    ) {
        ensureClient()
        client!!.send(function, handler)
    }

    private fun loadMeOnce() {
        if (meRequested) return
        meRequested = true

        client!!.send(GetMe(), ResultHandler { result: TdApi.Object? ->
            if (result is TdApi.User) {
                AppManager.getInstance()
                    .getExecutorDb()
                    .execute(Runnable {
                        account.setAccountTgId(result.id)
                        account.setAccountName(
                            result.firstName + " " + result.lastName
                        )
                        account.setAccountUsername(result.phoneNumber)
                        AppManager.getInstance()
                            .getAppDatabase()
                            .accountDao()
                            .update(account)
                    })
            }
        })
    }

    private fun sendTdlibParameters() {
        var dbPath = account.getAccountDbFolder()
        if (dbPath == null || dbPath.isEmpty()) {
            val rootDir = File(
                context.getFilesDir(),
                "user_" + account.getAccountId()
            )
            if (!rootDir.exists()) rootDir.mkdirs()
            dbPath = rootDir.getAbsolutePath()
        }

        val request =
            SetTdlibParameters()

        request.databaseDirectory = dbPath
        request.filesDirectory = dbPath + "/files"

        request.useMessageDatabase = true
        request.useSecretChats = true
        request.useFileDatabase = true
        request.useChatInfoDatabase = true

        try {
            request.apiId = context.getString(R.string.api_id).toInt()
            request.apiHash =
                context.getString(R.string.api_hash)
        } catch (e: NumberFormatException) {
            Log.e(
                "AccountSession",
                "Error parsing api_id/api_hash"
            )
            return
        }

        request.systemLanguageCode =
            Locale.getDefault().getLanguage()
        request.deviceModel = Build.MODEL
        request.systemVersion = Build.VERSION.RELEASE
        request.applicationVersion = "1.0"

        request.useTestDc = true

        client!!.send(request, ResultHandler { result: TdApi.Object? ->
            if (result is Ok) {
                tdlibParametersSent = true
            } else if (result is TdApi.Error) {
                Log.e(
                    "AccountSession",
                    "TDLib params error: " + result.message
                )
            }
        })
    }

    fun addUpdateHandler(handler: ResultHandler) {
        updateHandlers.add(handler)
    }

    fun removeUpdateHandler(handler: ResultHandler) {
        updateHandlers.remove(handler)
    }

    fun getClient(): Client {
        ensureClient()
        return client!!
    }
}