package com.github.borz7zy.telegramm.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.LruCache;

import androidx.annotation.Nullable;

import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.core.accounts.AccountSession;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class TdMediaRepository {

    private static final TdMediaRepository INSTANCE = new TdMediaRepository();
    public static TdMediaRepository get() { return INSTANCE; }

    private final LruCache<Integer, String> pathCache;

    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<Consumer<String>>> callbacks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Boolean> inFlight = new ConcurrentHashMap<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile int currentAccountId = -1;

    private TdMediaRepository() {
        this.pathCache = new LruCache<>(2048);
    }

    public void setCurrentAccountId(int accountId) {
        this.currentAccountId = accountId;
    }

    @Nullable
    public String getCachedPath(int fileId) {
        if (fileId == 0) return null;

        String cached = pathCache.get(fileId);

        if(!TextUtils.isEmpty(cached)){
            return cached;
        }
        return null;
    }

    public void getPathOrRequest(int fileId, Consumer<String> onReady) {
        if (fileId == 0) {
            onReady.accept(null);
            return;
        }

        String cached = getCachedPath(fileId);
        if (!TextUtils.isEmpty(cached)) {
            onReady.accept(cached);
            return;
        }

        callbacks.compute(fileId, (id, list)->{
            if(list == null){
                list = new CopyOnWriteArrayList<>();
            }
            list.add(onReady);
            return list;
        });

        startDownloadIfNeeded(fileId, 1);
    }

    private void startDownloadIfNeeded(int fileId, int priority) {
        if (inFlight.putIfAbsent(fileId, true) != null) return;

        AccountSession session = AccountManager.getInstance().getSession(currentAccountId);

        if (session == null) {
            Logger.LOGD("TdMediaRepository", "No active session for accountId: " + currentAccountId);
            finish(fileId, null);
            return;
        }

        TdApi.DownloadFile request = new TdApi.DownloadFile(fileId, priority, 0, 0, true);

        session.send(request, new Client.ResultHandler() {
            @Override
            public void onResult(TdApi.Object object) {
                String path = null;

                if (object instanceof TdApi.File) {
                    TdApi.File file = (TdApi.File) object;
                    if (file.local != null && file.local.isDownloadingCompleted) {
                        path = file.local.path;
                    }
                } else if (object instanceof TdApi.Error) {
                    Logger.LOGD("TdMediaRepository", "Download error: " + ((TdApi.Error) object).message);
                }

                final String finalPath = path;
                mainHandler.post(() -> finish(fileId, finalPath));
            }
        });
    }

    private void finish(int fileId, @Nullable String path) {
        if (!TextUtils.isEmpty(path)) {
            pathCache.put(fileId, path);
        }

        inFlight.remove(fileId);

        List<Consumer<String>> waitingCallbacks = callbacks.remove(fileId);
        if (waitingCallbacks != null) {
            for (Consumer<String> callback : waitingCallbacks) {
                callback.accept(path);
            }
        }
    }
}