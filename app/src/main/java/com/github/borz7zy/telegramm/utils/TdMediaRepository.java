package com.github.borz7zy.telegramm.utils;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.github.borz7zy.telegramm.App;
import com.github.borz7zy.telegramm.actor.AbstractActor;
import com.github.borz7zy.telegramm.actor.ActorRef;
import com.github.borz7zy.telegramm.actor.Props;
import com.github.borz7zy.telegramm.background.AsyncTask;
import com.github.borz7zy.telegramm.core.StopActor;
import com.github.borz7zy.telegramm.core.TdMessages;

import org.drinkless.tdlib.TdApi;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class TdMediaRepository {

    private static final TdMediaRepository INSTANCE = new TdMediaRepository();
    public static TdMediaRepository get() { return INSTANCE; }

    private final ConcurrentHashMap<Integer, String> pathCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<Consumer<String>>> callbacks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Boolean> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Boolean> waitingForClient = new ConcurrentHashMap<>();

    private final AtomicLong seq = new AtomicLong(1);

    private volatile ActorRef clientActorRef;

    private TdMediaRepository() {}

    public void bindClient(ActorRef clientActorRef) {
        this.clientActorRef = clientActorRef;

        for (Integer fileId : waitingForClient.keySet()) {
            waitingForClient.remove(fileId);
            startDownloadIfNeeded(fileId, 1);
        }
    }

    @Nullable
    public String getCachedPath(int fileId) {
        String p = pathCache.get(fileId);
        return TextUtils.isEmpty(p) ? null : p;
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

        callbacks.computeIfAbsent(fileId, k -> new CopyOnWriteArrayList<>()).add(onReady);

        ActorRef client = clientActorRef;
        if (client == null) {
            waitingForClient.put(fileId, true);
            return;
        }

        startDownloadIfNeeded(fileId, 1);
    }

    private void startDownloadIfNeeded(int fileId, int priority) {
        if (inFlight.putIfAbsent(fileId, true) != null) return;

        ActorRef client = clientActorRef;
        if (client == null) {
            waitingForClient.put(fileId, true);
            return;
        }

        new DownloadFileTask(client, fileId, priority).execPool();
    }

    private void finish(int fileId, @Nullable String path) {
        if (!TextUtils.isEmpty(path)) {
            pathCache.put(fileId, path);
        }

        inFlight.remove(fileId);

        List<Consumer<String>> list = callbacks.remove(fileId);
        if (list != null) {
            for (Consumer<String> cb : list) cb.accept(path);
        }
    }

    // --------------------
    // AsyncTask: download via tdlib (DownloadFile synchronous=true)
    // --------------------
    private final class DownloadFileTask extends AsyncTask<Void, Void, String> {
        private final ActorRef client;
        private final int fileId;
        private final int priority;

        DownloadFileTask(ActorRef client, int fileId, int priority) {
            this.client = client;
            this.fileId = fileId;
            this.priority = priority;
        }

        @Override
        protected String doInBackground(Void... params) throws Throwable {
            BlockingQueue<TdApi.Object> q = new ArrayBlockingQueue<>(1);

            ActorRef replyActor = App.getApplication().getActorSystem().actorOf(
                    "td-reply-" + fileId + "-" + UUID.randomUUID(),
                    Props.of(() -> new OneShotReplyActor(q)).dispatcher("ui")
            );

            long requestId = seq.getAndIncrement();

            TdApi.DownloadFile fn = new TdApi.DownloadFile(fileId, priority, 0, 0, true);
            client.tell(new TdMessages.SendWithId(requestId, fn, replyActor));

            TdApi.Object res = q.poll(60, TimeUnit.SECONDS);
            replyActor.tell(new StopActor());

            if (res instanceof TdApi.File f) {
                if (f.local != null && f.local.isDownloadingCompleted && !TextUtils.isEmpty(f.local.path)) {
                    return f.local.path;
                }
                return (f.local != null) ? f.local.path : null;
            }

            return null; // Error/timeout
        }

        @Override
        protected void onPostExecute(String path) {
            finish(fileId, path);
        }

        @Override
        protected void onError(Throwable t) {
            finish(fileId, null);
        }

        @Override
        protected void onCanceled(String result) {
            finish(fileId, null);
        }
    }

    private static final class OneShotReplyActor extends AbstractActor {
        private final BlockingQueue<TdApi.Object> queue;

        OneShotReplyActor(BlockingQueue<TdApi.Object> queue) {
            this.queue = queue;
        }

        @Override
        public void onReceive(Object message) {
            if (message instanceof StopActor) {
                context().stop(self());
                return;
            }
            if (message instanceof TdMessages.ResultWithId r) {
                queue.offer(r.result);
                context().stop(self());
            }
        }
    }

    // --------------------
    // helpers for avatars
    // --------------------
    public void getChatAvatarPathOrRequest(@Nullable TdApi.ChatPhotoInfo photo, boolean big, Consumer<String> onReady) {
        if (photo == null) { onReady.accept(null); return; }
        int fileId = big ? photo.big.id : photo.small.id;
        getPathOrRequest(fileId, onReady);
    }

    public void getUserAvatarPathOrRequest(@Nullable TdApi.ProfilePhoto photo, boolean big, Consumer<String> onReady) {
        if (photo == null) { onReady.accept(null); return; }
        int fileId = big ? photo.big.id : photo.small.id;
        getPathOrRequest(fileId, onReady);
    }
}