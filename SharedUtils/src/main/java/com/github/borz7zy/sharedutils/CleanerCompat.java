package com.github.borz7zy.sharedutils;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CleanerCompat {

    public interface Cleanable {
        void clean();
    }

    private interface Impl {
        Cleanable register(Object obj, Runnable action);
    }

    private static final Impl IMPL = createImpl();

    public static Cleanable register(Object obj, Runnable action) {
        if (obj == null) throw new NullPointerException("obj");
        if (action == null) throw new NullPointerException("action");
        return IMPL.register(obj, action);
    }

    private static Impl createImpl() {
        try {
            Class<?> cleanerClass = Class.forName("java.lang.ref.Cleaner");
            Object cleaner = cleanerClass.getMethod("create").invoke(null);

            Method register = cleanerClass.getMethod("register", Object.class, Runnable.class);

            return (obj, action) -> {
                try {
                    Object cleanableObj = register.invoke(cleaner, obj, action);
                    Method cleanMethod = cleanableObj.getClass().getMethod("clean");
                    return () -> {
                        try { cleanMethod.invoke(cleanableObj); } catch (Throwable ignored) {}
                    };
                } catch (Throwable t) {
                    return PhantomHolder.INSTANCE.register(obj, action);
                }
            };
        } catch (Throwable ignored) {
            return PhantomHolder.INSTANCE;
        }
    }

    private static final class PhantomHolder {
        static final PhantomImpl INSTANCE = new PhantomImpl();
    }

    private static final class PhantomImpl implements Impl {
        private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
        private final Set<PhantomCleanable> live = ConcurrentHashMap.newKeySet();

        PhantomImpl() {
            Thread t = new Thread(this::loop, "CleanerCompat");
            t.setDaemon(true);
            t.start();
        }

        private void loop() {
            while (true) {
                try {
                    PhantomCleanable ref = (PhantomCleanable) queue.remove();
                    ref.clean();
                } catch (InterruptedException ignored) {
                    // ignore
                } catch (Throwable ignored) {
                }
            }
        }

        @Override public Cleanable register(Object obj, Runnable action) {
            PhantomCleanable c = new PhantomCleanable(obj, queue, action, live);
            live.add(c);
            return c;
        }

        private static final class PhantomCleanable extends PhantomReference<Object> implements Cleanable {
            private final AtomicBoolean cleaned = new AtomicBoolean(false);
            private final Set<PhantomCleanable> live;
            private volatile Runnable action;

            PhantomCleanable(Object referent,
                             ReferenceQueue<? super Object> q,
                             Runnable action,
                             Set<PhantomCleanable> live) {
                super(referent, q);
                this.action = action;
                this.live = live;
            }

            @Override public void clean() {
                if (!cleaned.compareAndSet(false, true)) return;

                live.remove(this);
                Runnable a = action;
                action = null;

                try {
                    if (a != null) a.run();
                } finally {
                    clear();
                }
            }
        }
    }

    private CleanerCompat() {}
}