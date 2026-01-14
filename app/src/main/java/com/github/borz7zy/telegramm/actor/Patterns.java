package com.github.borz7zy.telegramm.actor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class Patterns {
    private Patterns() {}

    private static final AtomicLong REQ = new AtomicLong(1);

    public static Promise<Object> ask(ActorSystem system, ActorRef to, Object payload, long timeout, TimeUnit unit) {
        long id = REQ.getAndIncrement();
        Promise<Object> p = new Promise<>();

        ActorRef askHub = system.resolve("/askHub");
        askHub.tell(new Ask.Register(id, p, unit.toMillis(timeout)));

        to.tell(new Ask.Request(id, payload), askHub);
        return p;
    }
}