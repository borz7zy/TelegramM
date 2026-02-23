package com.github.borz7zy.telegramm.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.borz7zy.telegramm.core.accounts.AccountManager
import com.github.borz7zy.telegramm.core.accounts.AccountSession
import com.github.borz7zy.telegramm.core.accounts.AccountStorage
import com.github.borz7zy.telegramm.core.accounts.requireCurrentAccount
import com.github.borz7zy.telegramm.core.accounts.sendAwait
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class AuthViewModel : ViewModel() {

    sealed class UiState {
        object Phone : UiState()
        object Code : UiState()
        data class Password(val hint: String?) : UiState()
        object Loading : UiState()
    }

    sealed class Event {
        object NavigateToMain : Event()
        data class ShowError(val message: String) : Event()
        data class ShowToast(val message: String) : Event()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var lastStableState: UiState = UiState.Phone
    private lateinit var session: AccountSession

    init {
        viewModelScope.launch {
            try{
                session = resolveSession()
                observeAuthState()
            }catch (e: Exception){
                _events.emit(Event.ShowError("Failed to init session: ${e.message}"))
            }
        }
    }

    private suspend fun resolveSession(): AccountSession =
        AccountManager.getInstance()
            .getOrCreateSession(
                AccountStorage.getInstance()
                    .requireCurrentAccount()
            )

    private suspend fun observeAuthState() {
        session.authStateFlow
            .collect { state ->
                when (state) {
                    is TdApi.AuthorizationStateWaitPhoneNumber ->
                        updateState(UiState.Phone)

                    is TdApi.AuthorizationStateWaitCode ->
                        updateState(UiState.Code)

                    is TdApi.AuthorizationStateWaitPassword ->
                        updateState(
                            UiState.Password(state.passwordHint)
                        )

                    is TdApi.AuthorizationStateReady ->
                        _events.emit(Event.NavigateToMain)

                    else -> {}
                }
            }
    }

    fun onMainAction(phone: String, code: String, password: String) {
        when (_uiState.value) {
            is UiState.Phone -> sendPhone(phone)
            is UiState.Code -> sendCode(code)
            is UiState.Password -> sendPassword(password)
            else -> Unit
        }
    }

    fun onSecondaryAction() {
        when (_uiState.value) {
            is UiState.Code -> updateState(UiState.Phone)
            is UiState.Password -> recoverPassword()
            else -> Unit
        }
    }

    private fun sendPhone(phone: String) {
        if (phone.isBlank()) {
            emitError("Enter phone number")
            return
        }

        launchTdRequest(
            TdApi.SetAuthenticationPhoneNumber(
                phone,
                null)
        )
    }

    private fun sendCode(code: String) {
        if (code.isBlank()) {
            emitError("Enter verification code")
            return
        }

        launchTdRequest(
            TdApi.CheckAuthenticationCode(code)
        )
    }

    private fun sendPassword(password: String) {
        if (password.isBlank()) {
            emitError("Enter password")
            return
        }

        launchTdRequest(
            TdApi.CheckAuthenticationPassword(password)
        )
    }

    private fun recoverPassword() {
        viewModelScope.launch {
            val result = session.sendAwait(
                TdApi.RequestAuthenticationPasswordRecovery()
            )

            if (result is TdApi.Error)
                emitError(result.message)
            else
                _events.emit(Event.ShowToast("Recovery email sent"))
        }
    }

    private fun launchTdRequest(function: TdApi.Function<*>) {
        lastStableState = _uiState.value
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            val result = session.sendAwait(function)

            if (result is TdApi.Error) {
                emitError(result.message)
                _uiState.value = lastStableState
            }
        }
    }

    private fun updateState(state: UiState) {
        lastStableState = state
        _uiState.value = state
    }

    private fun emitError(message: String) {
        viewModelScope.launch {
            _events.emit(Event.ShowError(message))
        }
    }
}