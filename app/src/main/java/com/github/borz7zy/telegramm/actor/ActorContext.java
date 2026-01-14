package com.github.borz7zy.telegramm.actor;

import java.util.Objects;

public final class ActorContext {
    private final ActorSystem system;
    private final ActorCell cell;

    private volatile ActorRef sender = ActorRef.noSender();

    ActorContext(ActorSystem system, ActorCell cell) {
        this.system = system;
        this.cell = cell;
    }

    public ActorSystem system() { return system; }
    public ActorRef self() { return cell.ref(); }

    public ActorRef sender() { return sender; }
    void setSender(ActorRef s) { sender = (s != null ? s : ActorRef.noSender()); }

    public ActorRef actorOf(String name, Props props) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(props);
        return cell.createChild(name, props);
    }

    public void stop(ActorRef ref) {
        if (ref == null) return;
        cell.stop(ref);
    }

    public void watch(ActorRef ref) { cell.watch(ref); }
    public void unwatch(ActorRef ref) { cell.unwatch(ref); }
}
