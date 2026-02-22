package com.github.borz7zy.telegramm.core.accounts

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun AccountStorage.requireCurrentAccount(): AccountEntity =
    suspendCancellableCoroutine { cont ->
        getCurrentActive { account ->
            if (account != null) {
                cont.resume(account)
            } else {
                cont.resumeWithException(
                    IllegalStateException("No active account")
                )
            }
        }
    }