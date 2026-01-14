package com.github.borz7zy.telegramm.actor;

import java.util.concurrent.TimeUnit;

public interface SupervisorStrategy {
    Directive decide(Throwable cause);

    final class Directive {
        public enum Type { RESTART, STOP }
        public final Type type;
        public final long backoffMillis;

        private Directive(Type type, long backoffMillis) {
            this.type = type;
            this.backoffMillis = backoffMillis;
        }

        public static Directive restart(long backoffMillis) { return new Directive(Type.RESTART, Math.max(0, backoffMillis)); }
        public static Directive stop() { return new Directive(Type.STOP, 0); }
    }

    static SupervisorStrategy oneForOneDefault() {
        return cause -> Directive.restart(0);
    }

    static SupervisorStrategy stopOnAnyFailure() {
        return cause -> Directive.stop();
    }

    static SupervisorStrategy restartWithFixedBackoff(long time, TimeUnit unit) {
        long ms = unit.toMillis(time);
        return cause -> Directive.restart(ms);
    }
}
