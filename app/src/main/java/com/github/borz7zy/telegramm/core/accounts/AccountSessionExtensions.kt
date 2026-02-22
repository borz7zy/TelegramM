package com.github.borz7zy.telegramm.core.accounts

import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume

suspend fun AccountSession.sendAwait(
    function: TdApi.Function<*>
): TdApi.Object =
    suspendCancellableCoroutine { cont ->
        send(function) { result ->
            if (cont.isActive) {
                cont.resume(result)
            }
        }
    }
