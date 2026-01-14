package com.github.borz7zy.telegramm.background;

import android.os.Looper;
import android.os.Message;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class AsyncTask<Params, Progress, Result> {
    private static final int KEEP_ALIVE = 1;
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = (CPU_COUNT * 2) + 1;
    private static final BlockingQueue<Runnable> sPoolWorkQueue = new LinkedBlockingQueue(128);

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "AsyncTask #" + this.mCount.getAndIncrement());
        }
    };

    public static final Executor sPool = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, 1, TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);

    private static final Handler sHandler = new Handler();

    private final Object mLock = new Object();
    private AtomicBoolean mCancelled = new AtomicBoolean(false);

    protected abstract Result doInBackground(Params... paramsArr) throws Throwable;

    protected void onPreExecute() {
    }

    protected void onProgressUpdate(Progress progress) {
    }

    protected void onPostExecute(Result result) {
    }

    protected void onError(Throwable t) {
    }

    protected void onCanceled(Result result) {
    }

    public void publishProgress(Progress progress) {
        if (!this.mCancelled.get()) {
            AsyncResult<Progress, Result> result = new AsyncResult<>(this);
            result.progress = progress;
            sHandler.dispatchProgressUpdate(result);
        }
    }

    public void cancel() {
        this.mCancelled.set(true);
    }

    public boolean isCanceled() {
        return this.mCancelled.get();
    }

    public void execPool(final Params... params) {
        sPool.execute(new Runnable() {
            @Override
            public void run() {
                AsyncTask.this.execInCurrThread(params);
            }
        });
    }

    public void execInCurrThread(Params... params) {
        synchronized (this.mLock) {
            AsyncResult<Progress, Result> result = new AsyncResult<>(this);
            if (!this.mCancelled.get()) {
                sHandler.dispatchPreExecute(result);

                if (!this.mCancelled.get()) {
                    try {
                        result.result = doInBackground(params);

                        if (!this.mCancelled.get()) {
                            sHandler.dispatchPostExecute(result);
                        } else {
                            sHandler.dispatchCancel(result);
                        }
                    } catch (Throwable throwable) {
                        if (!this.mCancelled.get()) {
                            result.throwable = throwable;
                            sHandler.dispatchError(result);
                        } else {
                            sHandler.dispatchCancel(result);
                        }
                    }
                } else {
                    sHandler.dispatchCancel(result);
                }
            } else {
                sHandler.dispatchCancel(result);
            }
        }
    }

    static class Handler extends android.os.Handler {
        static final int MESSAGE_CANCEL = 4;
        static final int MESSAGE_ERROR = 3;
        static final int MESSAGE_POST_EXECUTE = 5;
        static final int MESSAGE_PRE_EXECUTE = 1;
        static final int MESSAGE_PROGRESS = 2;

        Handler() {
            super(Looper.getMainLooper());
        }

        void dispatchPreExecute(AsyncResult result) {
            sendMessage(result, 1);
        }
        void dispatchProgressUpdate(AsyncResult result) {
            sendMessage(result, 2);
        }
        void dispatchError(AsyncResult result) {
            sendMessage(result, 3);
        }
        void dispatchCancel(AsyncResult result) {
            sendMessage(result, 4);
        }
        void dispatchPostExecute(AsyncResult result) {
            sendMessage(result, 5);
        }
        void sendMessage(AsyncResult result, int code) {
            Message m = obtainMessage(code);
            m.obj = result;
            m.sendToTarget();
        }

        @Override
        public void handleMessage(Message msg) {
            AsyncResult obj = (AsyncResult) msg.obj;
            if (obj != null) {
                switch (msg.what) {
                    case MESSAGE_PRE_EXECUTE:
                        obj.task.onPreExecute();
                        break;
                    case MESSAGE_PROGRESS:
                        obj.task.onProgressUpdate(obj.progress);
                        break;
                    case MESSAGE_ERROR:
                        obj.task.onError(obj.throwable);
                        break;
                    case MESSAGE_CANCEL:
                        obj.task.onCanceled(obj.result);
                        break;
                    case MESSAGE_POST_EXECUTE:
                        obj.task.onPostExecute(obj.result);
                        break;
                }
            }
        }
    }

    static class AsyncResult<Progress, Result> {
        Progress progress;
        Result result;
        Throwable throwable;
        final AsyncTask task;

        AsyncResult(AsyncTask task) {
            this.task = task;
        }
    }
}