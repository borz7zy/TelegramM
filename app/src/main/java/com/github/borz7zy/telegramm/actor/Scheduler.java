package com.github.borz7zy.telegramm.actor;

import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class Scheduler {
    private final ActorSystem system;

    Scheduler(ActorSystem system) { this.system = system; }

    public ScheduledFuture<?> scheduleOnce(long delay, TimeUnit unit, ActorRef to, Object msg) {
        return scheduleOnce(delay, unit, to, msg, ActorRef.noSender());
    }

    public ScheduledFuture<?> scheduleOnce(long delay, TimeUnit unit, ActorRef to, Object msg, ActorRef sender) {
        Objects.requireNonNull(unit);
        Objects.requireNonNull(to);
        Objects.requireNonNull(msg);
        return system.schedExec().schedule(() -> to.tell(msg, sender), delay, unit);
    }

    public ScheduledFuture<?> scheduleOnce(long delay, TimeUnit unit, Runnable task) {
        Objects.requireNonNull(unit);
        Objects.requireNonNull(task);
        return system.schedExec().schedule(task, delay, unit);
    }
}