package com.github.borz7zy.telegramm.actor;

public final class SystemMessages {
    private SystemMessages() {}

    public static final class PoisonPill {
        public static final PoisonPill INSTANCE = new PoisonPill();
        private PoisonPill() {}
    }

    public static final class Terminated {
        public final ActorRef ref;
        public Terminated(ActorRef ref) { this.ref = ref; }
        @Override public String toString() { return "Terminated(" + ref + ")"; }
    }

    public static final class Failure {
        public final ActorRef child;
        public final Throwable cause;
        public Failure(ActorRef child, Throwable cause) {
            this.child = child;
            this.cause = cause;
        }
    }
}
