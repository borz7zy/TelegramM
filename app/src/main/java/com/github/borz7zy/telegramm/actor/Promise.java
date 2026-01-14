package com.github.borz7zy.telegramm.actor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public final class Promise<T> implements Future<T> {
    private final Object lock = new Object();
    private final AtomicBoolean done = new AtomicBoolean(false);
    private T value;
    private Throwable error;

    private final List<Listener<T>> listeners = new ArrayList<>();

    public boolean trySuccess(T v) {
        if (!done.compareAndSet(false, true)) return false;
        List<Listener<T>> toFire;
        synchronized (lock) {
            value = v;
            toFire = new ArrayList<>(listeners);
            listeners.clear();
            lock.notifyAll();
        }
        for (Listener<T> l : toFire) l.fire(v, null);
        return true;
    }

    public boolean tryFailure(Throwable t) {
        if (!done.compareAndSet(false, true)) return false;
        List<Listener<T>> toFire;
        synchronized (lock) {
            error = t;
            toFire = new ArrayList<>(listeners);
            listeners.clear();
            lock.notifyAll();
        }
        for (Listener<T> l : toFire) l.fire(null, t);
        return true;
    }

    public void onComplete(Executor executor, BiConsumer<? super T, ? super Throwable> handler) {
        Listener<T> l = new Listener<>(executor, handler);
        if (done.get()) {
            T v; Throwable e;
            synchronized (lock) { v = value; e = error; }
            l.fire(v, e);
            return;
        }
        synchronized (lock) {
            if (done.get()) {
                T v = value; Throwable e = error;
                l.fire(v, e);
            } else {
                listeners.add(l);
            }
        }
    }

    @Override public boolean cancel(boolean mayInterruptIfRunning) { return false; }
    @Override public boolean isCancelled() { return false; }
    @Override public boolean isDone() { return done.get(); }

    @Override public T get() throws InterruptedException, ExecutionException {
        synchronized (lock) {
            while (!done.get()) lock.wait();
            if (error != null) throw new ExecutionException(error);
            return value;
        }
    }

    @Override public T get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        long end = System.nanoTime() + unit.toNanos(timeout);
        synchronized (lock) {
            while (!done.get()) {
                long left = end - System.nanoTime();
                if (left <= 0) throw new TimeoutException();
                lock.wait(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(left)));
            }
            if (error != null) throw new ExecutionException(error);
            return value;
        }
    }

    private static final class Listener<T> {
        final Executor ex;
        final BiConsumer<? super T, ? super Throwable> h;
        Listener(Executor ex, BiConsumer<? super T, ? super Throwable> h) { this.ex = ex; this.h = h; }
        void fire(T v, Throwable t) {
            ex.execute(() -> h.accept(v, t));
        }
    }
}
