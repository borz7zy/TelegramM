package com.github.borz7zy.telegramm.actor;

final class DeadLetters {
    private final ActorSystem system;
    private final ActorRef ref;

    DeadLetters(ActorSystem system) {
        this.system = system;
        this.ref = new ActorRef(null, ActorPath.dead("deadLetters"));
    }

    ActorRef ref() { return ref; }

    void accept(Envelope env, String reason) {
        system.logger().warn("DeadLetters (" + reason + "): msg=" + safe(env.message)
                + ", sender=" + env.sender + ", to=deadLetters");
    }

    private String safe(Object o) {
        try { return String.valueOf(o); } catch (Throwable t) { return "<toString failed>"; }
    }
}