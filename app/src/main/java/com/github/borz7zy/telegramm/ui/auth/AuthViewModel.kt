package com.github.borz7zy.telegramm.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.borz7zy.telegramm.AppManager
import com.github.borz7zy.telegramm.core.accounts.AccountEntity
import com.github.borz7zy.telegramm.core.accounts.AccountManager
import com.github.borz7zy.telegramm.core.accounts.AccountSession
import com.github.borz7zy.telegramm.core.accounts.AccountSingleCallback
import com.github.borz7zy.telegramm.core.accounts.AccountStorage
import com.github.borz7zy.telegramm.utils.Logger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

class AuthViewModel : ViewModel() {

    // ---------- UI STATE ----------

    sealed class UiState {
        object Phone : UiState()
        object Code : UiState()
        data class Password(val hint: String?) : UiState()
        object Loading : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Phone)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    sealed class Event {
        object NavigateToMain : Event()
        data class ShowError(val message: String) : Event()
        data class ShowToast(val message: String) : Event()
    }

    private var session: AccountSession? = null
    private var lastStableState: UiState = UiState.Phone

    private fun requireSession(): AccountSession =
        requireNotNull(session) { "AccountSession not initialized" }

    fun setSession(session: AccountSession?) {
        this.session = session
    }

    // ---------- AUTH STATE FROM TDLib ----------

    fun onAuthStateChanged(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                updateState(UiState.Phone)
            }

            is TdApi.AuthorizationStateWaitCode -> {
                updateState(UiState.Code)
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                updateState(UiState.Password(state.passwordHint))
            }

            is TdApi.AuthorizationStateReady -> {
                viewModelScope.launch {
                    _events.emit(Event.NavigateToMain)
                }
            }

            is TdApi.AuthorizationStateLoggingOut -> {
                _uiState.value = UiState.Loading
            }

            else -> Unit
        }
    }

    // ---------- ACTIONS ----------

    fun sendPhone(phone: String) {
        if (phone.isBlank()) {
            emitError("Enter phone number")
            return
        }

        setLoading()
        val req = TdApi.SetAuthenticationPhoneNumber().apply {
            phoneNumber = phone
        }

        sendSafely(req)
    }

    fun sendCode(code: String) {
        if (code.isBlank()) {
            emitError("Enter verification code")
            return
        }

        setLoading()
        sendSafely(TdApi.CheckAuthenticationCode(code))
    }

    fun sendPassword(password: String) {
        if (password.isBlank()) {
            emitError("Enter password")
            return
        }

        setLoading()
        sendSafely(TdApi.CheckAuthenticationPassword(password))
    }

    private fun sendSafely(function: TdApi.Function<*>) {
        val currentSession = session
        if (currentSession == null) {
            viewModelScope.launch {
                _events.emit(Event.ShowError("Session not ready yet"))
            }
            return
        }
        currentSession.send(function, errorHandler)
    }

    fun onWrongNumber() {
        updateState(UiState.Phone)
    }

    fun onForgotPassword() {
        requireSession().send(
            TdApi.RequestAuthenticationPasswordRecovery()
        ) { result ->
            if (result is TdApi.Error) {
                emitError("Recovery error: ${result.message}")
            } else {
                viewModelScope.launch {
                    _events.emit(Event.ShowToast("Recovery email sent"))
                }
            }
        }
    }

    private fun updateState(state: UiState) {
        lastStableState = state
        _uiState.value = state
    }

    private fun setLoading() {
        lastStableState = _uiState.value
        _uiState.value = UiState.Loading
    }

    private fun emitError(message: String) {
        viewModelScope.launch {
            _events.emit(Event.ShowError(message))
        }
    }

    private val errorHandler = Client.ResultHandler { result ->
        if (result is TdApi.Error) {
            viewModelScope.launch {
                _events.emit(Event.ShowError(result.message))
            }
            _uiState.value = lastStableState
        }
    }
}