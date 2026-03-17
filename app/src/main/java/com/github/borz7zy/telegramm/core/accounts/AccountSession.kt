package com.github.borz7zy.telegramm.core.accounts

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.github.borz7zy.telegramm.App
import com.github.borz7zy.telegramm.AppManager
import com.github.borz7zy.telegramm.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

class AccountSession(private val context: Context, private val account: AccountEntity) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var client: Client? = null
    @OptIn(ExperimentalAtomicApi::class)
    private val isInitializing = AtomicBoolean(false)
    private val pendingActions = ConcurrentLinkedQueue<() -> Unit>()

    private var tdlibParametersSent = false
    private var meRequested = false

    private var lastAuthState: AuthorizationState? = null

    private val _authStateFlow = MutableStateFlow<AuthorizationState?>(null)
    val authStateFlow: StateFlow<AuthorizationState?>
        get() {
            ensureClient()
            return _authStateFlow
        }

    val authStateLiveData = MutableLiveData<AuthorizationState>()

    private val updateHandlers = CopyOnWriteArrayList<ResultHandler>()

    @OptIn(ExperimentalAtomicApi::class)
    private fun ensureClient() {
        if (client != null) return

        if (isInitializing.get()) return

        if (isInitializing.compareAndSet(false, true)) {
            scope.launch {
                try {
                    val newClient = Client.create(
                        { update -> onUpdate(update) },
                        null,
                        null
                    )

                    newClient.send(GetAuthorizationState()) { update -> onUpdate(update) }

                    client = newClient

                    while (!pendingActions.isEmpty()) {
                        pendingActions.poll()?.invoke()
                    }
                } catch (e: Exception) {
                    Log.e("AccountSession", "Error init TdLib: ${e.message}")
                    isInitializing.set(false)
                }
            }
        }
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
        val currentClient = client
        if (currentClient != null) {
            currentClient.send(function) { result ->
                if (result is TdApi.Error) {
                    Log.e("AccountSession", "Error: ${result.message}")
                }
            }
        } else {
            pendingActions.add {
                client?.send(function) { result ->
                    if (result is TdApi.Error) {
                        Log.e("AccountSession", "Error: ${result.message}")
                    }
                }
            }
            ensureClient()
        }
    }

    fun send(
        function: TdApi.Function<*>,
        handler: ResultHandler?
    ) {
        val currentClient = client
        if (currentClient != null) {
            currentClient.send(function, handler)
        } else {
            pendingActions.add {
                client?.send(function, handler)
            }
            ensureClient()
        }
    }


    fun sendAwait(
        function: TdApi.Function<*>?,
        handler: ResultHandler?
    ) {
        val currentClient = client
        if (currentClient != null) {
            currentClient.send(function, handler)
        } else {
            pendingActions.add {
                client?.send(function, handler)
            }
            ensureClient()
        }
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

        request.systemLanguageCode = Resources.getSystem().configuration.getLocales().get(0).language

        request.deviceModel =
            if (Build.MODEL != null) Build.MANUFACTURER + " " + Build.MODEL else "Virtual Machine on Android"

        val system =
            if (Build.VERSION.BASE_OS.isEmpty()) "Android " else Build.VERSION.BASE_OS + " "
        request.systemVersion = system + Build.VERSION.RELEASE + " (SDK: " +Build.VERSION.SDK_INT + ")"

        request.applicationVersion =
            App.getApplication().getString(R.string.version_name) +
                    " (" +
                    App.getApplication().getString(R.string.version_code) +
                    ")"

        request.useTestDc = context.resources.getBoolean(R.bool.test_dc)

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

    fun getClient(): Client? {
        ensureClient()
        return client
    }
}