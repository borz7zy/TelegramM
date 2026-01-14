package com.github.borz7zy.telegramm.actor;

import java.util.Objects;

public final class Props {
    public interface Producer { Actor produce(); }

    final Producer producer;
    final int mailboxCapacity;
    final OverflowStrategy overflowStrategy;
    final SupervisorStrategy supervisorStrategy;

    final String dispatcherId;
    final int throughput;

    private Props(Producer producer, int cap, OverflowStrategy overflow, SupervisorStrategy strategy, String dispatcherId, int throughput) {
        this.producer = producer;
        this.mailboxCapacity = cap;
        this.overflowStrategy = overflow;
        this.supervisorStrategy = strategy;
        this.dispatcherId = dispatcherId;
        this.throughput = throughput;
    }

    public static Props of(Producer producer) {
        return new Props(Objects.requireNonNull(producer), 1024, OverflowStrategy.DROP_NEW, null, "cpu", 200);
    }

    public Props mailboxCapacity(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        return new Props(producer, capacity, overflowStrategy, supervisorStrategy, dispatcherId, throughput);
    }

    public Props overflowStrategy(OverflowStrategy s) {
        return new Props(producer, mailboxCapacity, Objects.requireNonNull(s), supervisorStrategy, dispatcherId, throughput);
    }

    public Props supervisorStrategy(SupervisorStrategy s) {
        return new Props(producer, mailboxCapacity, overflowStrategy, Objects.requireNonNull(s), dispatcherId, throughput);
    }

    public Props dispatcher(String dispatcherId) {
        return new Props(producer, mailboxCapacity, overflowStrategy, supervisorStrategy,
                Objects.requireNonNull(dispatcherId), throughput);
    }

    public Props throughput(int throughput) {
        if (throughput <= 0) throw new IllegalArgumentException("throughput must be > 0");
        return new Props(producer, mailboxCapacity, overflowStrategy, supervisorStrategy, dispatcherId, throughput);
    }
}
