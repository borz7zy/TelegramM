package com.github.borz7zy.telegramm.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.navigation.fragment.NavHostFragment
import com.github.borz7zy.telegramm.AppManager
import com.github.borz7zy.telegramm.R
import com.github.borz7zy.telegramm.background.AsyncTask
import com.github.borz7zy.telegramm.core.accounts.AccountEntity
import com.github.borz7zy.telegramm.core.accounts.AccountManager
import com.github.borz7zy.telegramm.core.accounts.AccountSession
import com.github.borz7zy.telegramm.core.accounts.AccountSingleCallback
import com.github.borz7zy.telegramm.core.accounts.AccountStorage
import com.github.borz7zy.telegramm.ui.base.BaseTelegramFragment
import com.github.borz7zy.telegramm.utils.Logger
import org.drinkless.tdlib.TdApi.AuthorizationState
import org.drinkless.tdlib.TdApi.AuthorizationStateReady
import org.drinkless.tdlib.TdApi.AuthorizationStateWaitCode
import org.drinkless.tdlib.TdApi.AuthorizationStateWaitPassword
import org.drinkless.tdlib.TdApi.AuthorizationStateWaitPhoneNumber
import org.drinkless.tdlib.TdApi.AuthorizationStateWaitTdlibParameters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class SplashFragment : BaseTelegramFragment() {
    private var splashText: TextView? = null
    private var latestState: AuthorizationState? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_splash, container,
            false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val root = view.findViewById<LinearLayout>(R.id.root_splash)

        root?.setBackgroundColor(AppManager.getInstance().themeEngine.currentTheme.value
            ?.surfaceColor
            ?: R.color.surfaceColor)

        splashText = view.findViewById<TextView>(R.id.splashText)

        splashText?.setTextColor(AppManager.getInstance().themeEngine.currentTheme.value
            ?.onSurfaceColor
            ?: R.color.onSurfaceColor)

        startTypewriterEffect()
        startAsyncWaitState()

        AppManager.getInstance().themeEngine.currentTheme.observe(viewLifecycleOwner, {Theme->
            root?.setBackgroundColor(Theme.surfaceColor)
            splashText?.setTextColor(Theme.onSurfaceColor)
        })
    }

    private fun startTypewriterEffect() {
        val fullText = getString(R.string.app_name)
        splashText!!.text = "|"

        val totalDuration: Long = 2000
        val charDelay = totalDuration / fullText.length

        object : Runnable {
            var c: Int = 0
            var currentText: StringBuilder = StringBuilder()

            override fun run() {
                if (!isAdded()) return

                if (c < fullText.length) {
                    currentText.append(fullText.get(c))
                    splashText!!.setText("$currentText|")

                    ++c
                    mainHandler.postDelayed(this, charDelay)
                } else {
                    startBlinkingCursor(fullText)
                }
            }
        }.run()
    }

    private fun startBlinkingCursor(text: String?) {
        mainHandler.postDelayed(object : Runnable {
            var showCursor: Boolean = false

            override fun run() {
                if (!isAdded) return

                splashText!!.setText(if (showCursor) "$text|" else text)
                showCursor = !showCursor

                mainHandler.postDelayed(this, 500)
            }
        }, 500)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onAuthStateChanged(state: AuthorizationState) {
        latestState = state
        Logger.LOGD("SplashFragment",
            "onAuthStateChanged: " + state.javaClass.getSimpleName())
    }

    private fun startAsyncWaitState() {
        object : AsyncTask<Void?, Void?, AuthorizationState?>() {
            @Throws(Throwable::class)
            override fun doInBackground(vararg params: Void?): AuthorizationState? {
                val latch = CountDownLatch(1)
                val result = AtomicReference<AuthorizationState?>()

                AppManager.getInstance().executorDb.execute(Runnable {
                    AccountStorage.getInstance()
                        .getCurrentActive(AccountSingleCallback
                        { account: AccountEntity? ->
                            if (account == null) {
                                AppManager.getInstance()
                                    .executorDb
                                    .execute(Runnable {
                                    val newAccount = AccountEntity(
                                        null,
                                        0L,
                                        "New Account",
                                        "")
                                    val newId =
                                        AppManager.getInstance().appDatabase.accountDao()
                                            .insert(newAccount)
                                    newAccount.setAccountId(newId.toInt())
                                    AccountStorage.getInstance()
                                        .setCurrentActive(newAccount.getAccountId())
                                    Logger.LOGD(
                                        "SplashFragment",
                                        "Created new account ID: " + newAccount.getAccountId()
                                    )

                                    val session =
                                        AccountManager.getInstance().getOrCreateSession(newAccount)
                                    mainHandler.post(Runnable {
                                        subscribeAuthState(
                                            session,
                                            latch,
                                            result
                                        )
                                    })
                                })
                            } else {
                                val session =
                                    AccountManager.getInstance().getOrCreateSession(/* account = */
                                        account)
                                Logger.LOGD(
                                    /* tag = */ "SplashFragment",
                                    /* msg = */ "Session created for account: "
                                            + account.getAccountId()
                                )
                                mainHandler.post(Runnable {
                                    subscribeAuthState(
                                        session,
                                        latch,
                                        result
                                    )
                                })
                            }
                        })
                })

                latch.await()
                return result.get()
            }

            fun subscribeAuthState(
                session: AccountSession,
                latch: CountDownLatch,
                result: AtomicReference<AuthorizationState?>
            ) {
                val counted = AtomicBoolean(false)

                session.observeAuthState()
                    .observe(getViewLifecycleOwner(), Observer
                    { state: AuthorizationState? ->
                        Logger.LOGD(
                            "SplashFragment",
                            "observeAuthState update: " + state!!.javaClass.getSimpleName()
                        )
                        if (state is AuthorizationStateWaitTdlibParameters) {
                            return@Observer
                        }
                        if (counted.compareAndSet(
                                false,
                                true)
                        ) {
                            result.set(state)
                            latch.countDown()
                        }
                    })
            }

            override fun onPostExecute(state: AuthorizationState?) {
                if (!isAdded) return

                Logger.LOGD(
                    "SplashFragment",
                    "AsyncTask finished with state: "
                            + (if (state != null) state.javaClass.getSimpleName() else "null")
                )

                val nav = NavHostFragment.findNavController(this@SplashFragment)

                when (state) {
                    is AuthorizationStateReady -> {
                        Logger.LOGD("SplashFragment", "Navigate to Main")
                        nav.navigate(R.id.frag_splash_to_main)
                    }

                    is AuthorizationStateWaitPhoneNumber -> {
                        Logger.LOGD("SplashFragment", "Navigate to Phone")
                        nav.navigate(R.id.action_splashFragment_to_authPhoneFragment)
                    }

                    is AuthorizationStateWaitCode -> {
                        Logger.LOGD("SplashFragment", "Navigate to Code")
                        nav.navigate(R.id.action_splashFragment_to_authCodeFragment)
                    }

                    is AuthorizationStateWaitPassword -> {
                        Logger.LOGD("SplashFragment", "Navigate to Password")
                        nav.navigate(R.id.action_splashFragment_to_authPasswordFragment)
                    }

                    else -> {
                        Logger.LOGD(
                            "SplashFragment",
                            "Unknown auth state: "
                                    + (if (state != null) {
                                state.javaClass.getSimpleName()
                            } else "null")
                        )
                    }
                }
            }
        }.execPool()
    }
}
