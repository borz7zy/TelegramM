package com.github.borz7zy.telegramm.actor; // Проверь пакет!

import android.os.Handler;
import android.os.Looper;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

public class AndroidMainExecutor extends AbstractExecutorService {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void execute(Runnable command) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            command.run();
        } else {
            handler.post(command);
        }
    }

    @Override
    public void shutdown() {
        // ignoring
    }

    @Override
    public List<Runnable> shutdownNow() {
        // ignoring
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return false;
    }
}