package com.github.borz7zy.telegramm.actor;

import java.util.Objects;

public final class ActorPath {
    private final String system;
    private final String full;

    private ActorPath(String system, String full) {
        this.system = system;
        this.full = full;
    }

    public static ActorPath root(String systemName, String name) {
        Objects.requireNonNull(systemName);
        Objects.requireNonNull(name);
        return new ActorPath(systemName, "/" + name);
    }

    static ActorPath childOf(ActorPath parent, String child) {
        return new ActorPath(parent.system, parent.full + "/" + child);
    }

    static ActorPath dead(String tag) { return new ActorPath("dead", "/dead/" + tag); }

    public String system() { return system; }
    public String full() { return full; }

    public String name() {
        int idx = full.lastIndexOf('/');
        return idx >= 0 ? full.substring(idx + 1) : full;
    }

    @Override public String toString() { return system + ":" + full; }
}
