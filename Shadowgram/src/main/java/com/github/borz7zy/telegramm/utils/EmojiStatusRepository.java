package com.github.borz7zy.telegramm.utils;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.core.accounts.AccountSession;

import org.drinkless.tdlib.TdApi;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class EmojiStatusRepository {

    private static final EmojiStatusRepository INSTANCE = new EmojiStatusRepository();
    public static EmojiStatusRepository get() { return INSTANCE; }

    private final ConcurrentHashMap<Long, TdApi.Sticker> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<Consumer<TdApi.Sticker>>> pending = new ConcurrentHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile int currentAccountId = -1;

    private EmojiStatusRepository() {}

    public void setCurrentAccountId(int accountId) {
        this.currentAccountId = accountId;
    }

    @Nullable
    public TdApi.Sticker getCached(long customEmojiId) {
        if (customEmojiId == 0L) return null;
        return cache.get(customEmojiId);
    }

    public void resolve(long customEmojiId, Consumer<TdApi.Sticker> onReady) {
        if (customEmojiId == 0L) {
            onReady.accept(null);
            return;
        }

        TdApi.Sticker cached = cache.get(customEmojiId);
        if (cached != null) {
            onReady.accept(cached);
            return;
        }

        boolean[] needFetch = {false};
        pending.compute(customEmojiId, (id, list) -> {
            if (list == null) {
                list = new CopyOnWriteArrayList<>();
                needFetch[0] = true;
            }
            list.add(onReady);
            return list;
        });

        if (needFetch[0]) {
            startResolve(customEmojiId);
        }
    }

    private void startResolve(long customEmojiId) {
        AccountSession session = AccountManager.getInstance().getSession(currentAccountId);
        if (session == null) {
            mainHandler.post(() -> finish(customEmojiId, null));
            return;
        }

        session.send(new TdApi.GetCustomEmojiStickers(new long[]{customEmojiId}), result -> {
            TdApi.Sticker found = null;
            if (result instanceof TdApi.Stickers) {
                TdApi.Stickers stickers = (TdApi.Stickers) result;
                if (stickers.stickers != null && stickers.stickers.length > 0) {
                    for (TdApi.Sticker s : stickers.stickers) {
                        if (s != null) { found = s; break; }
                    }
                }
            }
            final TdApi.Sticker out = found;
            mainHandler.post(() -> finish(customEmojiId, out));
        });
    }

    private void finish(long customEmojiId, @Nullable TdApi.Sticker sticker) {
        if (sticker != null) {
            cache.put(customEmojiId, sticker);
        }
        List<Consumer<TdApi.Sticker>> waiters = pending.remove(customEmojiId);
        if (waiters != null) {
            for (Consumer<TdApi.Sticker> w : waiters) {
                w.accept(sticker);
            }
        }
    }
}
