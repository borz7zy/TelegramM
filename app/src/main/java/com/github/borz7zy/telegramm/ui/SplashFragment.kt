package com.github.borz7zy.telegramm.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import com.github.borz7zy.telegramm.AppManager
import com.github.borz7zy.telegramm.R
import com.github.borz7zy.telegramm.core.accounts.AccountEntity
import com.github.borz7zy.telegramm.core.accounts.AccountManager
import com.github.borz7zy.telegramm.core.accounts.AccountStorage
import com.github.borz7zy.telegramm.ui.base.BaseTelegramFragment
import com.github.borz7zy.telegramm.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import org.drinkless.tdlib.TdApi.AuthorizationState
import org.drinkless.tdlib.TdApi.AuthorizationStateReady
import org.drinkless.tdlib.TdApi.AuthorizationStateWaitCode
import org.drinkless.tdlib.TdApi.AuthorizationStateWaitPassword
import org.drinkless.tdlib.TdApi.AuthorizationStateWaitPhoneNumber
import kotlin.coroutines.resume

class SplashFragment : BaseTelegramFragment() {

    private var splashText: TextView? = null
    private var typewriterJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val root = view.findViewById<LinearLayout>(R.id.root_splash)
        splashText = view.findViewById(R.id.splashText)

        applyTheme(root)
        observeTheme(root)

        startTypewriterEffect()

        viewLifecycleOwner.lifecycleScope.launch {
            val state = waitForAuthState()
            handleNavigation(state)
        }
    }

    private fun applyTheme(root: LinearLayout?) {
        val theme = AppManager.getInstance().themeEngine.currentTheme.value
        root?.setBackgroundColor(theme?.surfaceColor ?: R.color.surfaceColor)
        splashText?.setTextColor(theme?.onSurfaceColor ?: R.color.onSurfaceColor)
    }

    private fun observeTheme(root: LinearLayout?) {
        AppManager.getInstance()
            .themeEngine
            .currentTheme
            .observe(viewLifecycleOwner) { theme ->
                root?.setBackgroundColor(theme.surfaceColor)
                splashText?.setTextColor(theme.onSurfaceColor)
            }
    }

    private suspend fun waitForAuthState(): TdApi.AuthorizationState {
        val account = getOrCreateAccount()

        val session = AccountManager.getInstance()
            .getOrCreateSession(account)

        return session
            .authStateFlow
            .filterNotNull()
            .first { state ->
                state !is TdApi.AuthorizationStateWaitTdlibParameters
            }
    }

    private suspend fun getOrCreateAccount(): AccountEntity =
        withContext(Dispatchers.IO) {

            suspendCancellableCoroutine { continuation ->

                AccountStorage.getInstance()
                    .getCurrentActive { account ->

                        if (account != null) {
                            continuation.resume(account)
                            return@getCurrentActive
                        }

                        val newAccount = AccountEntity(
                            null,
                            0L,
                            "New Account",
                            ""
                        )

                        val newId = AppManager.getInstance()
                            .appDatabase
                            .accountDao()
                            .insert(newAccount)

                        newAccount.setAccountId(newId.toInt())

                        AccountStorage.getInstance()
                            .setCurrentActive(newAccount.getAccountId())

                        Logger.LOGD(
                            "SplashFragment",
                            "Created new account ID: ${newAccount.getAccountId()}"
                        )

                        continuation.resume(newAccount)
                    }
            }
        }

    private fun handleNavigation(state: AuthorizationState?) {
        if (!isAdded) return

        val nav = NavHostFragment.findNavController(this)

        when (state) {
            is AuthorizationStateReady ->
                nav.navigate(R.id.frag_splash_to_main)

            is AuthorizationStateWaitPhoneNumber,
            is AuthorizationStateWaitCode,
            is AuthorizationStateWaitPassword ->
                nav.navigate(R.id.frag_splash_to_auth)

            else ->
                Logger.LOGD("SplashFragment", "Unknown auth state: $state")
        }
    }

    private fun startTypewriterEffect() {
        typewriterJob?.cancel()

        typewriterJob = viewLifecycleOwner.lifecycleScope.launch {

            val fullText = getString(R.string.app_name)
            val totalDuration = 2000L
            val charDelay = totalDuration / fullText.length

            splashText?.text = "|"

            val builder = StringBuilder()

            for (c in fullText) {
                builder.append(c)
                splashText?.text = "$builder|"
                delay(charDelay)
            }

            while (isActive) {
                splashText?.text = "$builder|"
                delay(500)
                splashText?.text = builder.toString()
                delay(500)
            }
        }
    }

    override fun onDestroyView() {
        typewriterJob?.cancel()
        super.onDestroyView()
    }
}
