package com.github.borz7zy.telegramm.utils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.atomic.AtomicBoolean;

/*
 * A LiveData that delivers each value only once, to a single observer.
 * Used for one-shot UI events (navigation, toast) so that a value emitted
 * before an observer subscribes, or a re-subscription after a configuration
 * change, does not replay the event.
 *
 * postValue() works from a background thread because its internal runnable
 * calls the overridden setValue() on the main thread.
 */
public class SingleLiveEvent<T> extends MutableLiveData<T> {

    private final AtomicBoolean pending = new AtomicBoolean(false);

    @MainThread
    @Override
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        super.observe(owner, t -> {
            if (pending.compareAndSet(true, false)) {
                observer.onChanged(t);
            }
        });
    }

    @MainThread
    @Override
    public void setValue(@Nullable T t) {
        pending.set(true);
        super.setValue(t);
    }

    public void call() {
        setValue(null);
    }
}
