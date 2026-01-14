package com.github.borz7zy.telegramm.actor;

import java.util.Objects;

public final class ActorRef {

    private final ActorCell cell;
    private final ActorPath path;

    ActorRef(ActorCell cell, ActorPath path) {
        this.cell = cell;
        this.path = path;
    }

    public ActorCell cell() { return cell; }

    public ActorPath path() { return path; }
    public String name() { return path.name(); }

    public void tell(Object message) { tell(message, ActorRef.noSender()); }

    public void tell(Object message, ActorRef sender) {
        Objects.requireNonNull(message, "message");
        if (cell == null) return; // NoSender
        cell.enqueue(new Envelope(message, sender != null ? sender : ActorRef.noSender()));
    }

    public static ActorRef noSender() { return NoSenderHolder.NO_SENDER; }

    static final class NoSenderHolder {
        static final ActorRef NO_SENDER = new ActorRef(null, ActorPath.dead("noSender"));
    }

    @Override public String toString() { return "ActorRef(" + path + ")"; }

}
