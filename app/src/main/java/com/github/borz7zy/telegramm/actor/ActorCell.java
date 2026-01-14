package com.github.borz7zy.telegramm.actor;

import com.github.borz7zy.telegramm.actor.SystemMessages.*;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class ActorCell implements Runnable {
    private final ActorSystem system;
    private final ActorPath path;
    private final Props props;
    private final ActorRef ref;

    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    // Lock-free-ish bounded mailbox (MPSC)
    private final ConcurrentLinkedQueue<Envelope> q = new ConcurrentLinkedQueue<>();
    private final AtomicInteger qSize = new AtomicInteger(0);
    private final int capacity;
    private final AtomicBoolean overflowMarkerEnqueued = new AtomicBoolean(false);

    private final ActorContext context;
    private volatile Actor actor;

    private final ConcurrentHashMap<String, ActorCell> children = new ConcurrentHashMap<>();
    private final Set<ActorCell> watchers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final AtomicInteger restartCount = new AtomicInteger(0);

    private final ActorCell parent; // null for top-level

    // Marker to crash actor on overflow when strategy=FAIL
    private static final Object MAILBOX_OVERFLOW = new Object();

    static ActorCell createTopLevel(ActorSystem system, ActorPath path, Props props) {
        return new ActorCell(system, null, path, props);
    }

    private ActorCell(ActorSystem system, ActorCell parent, ActorPath path, Props props) {
        this.system = system;
        this.parent = parent;
        this.path = path;
        this.props = props;
        this.ref = new ActorRef(this, path);

        this.capacity = props.mailboxCapacity;
        this.context = new ActorContext(system, this);

        this.actor = newActorInstance();

        // global register for resolve()
        system.register(ref);
    }

    ActorPath path() { return path; }
    ActorRef ref() { return ref; }

    void start() {
        try {
            if (actor instanceof AbstractActor) ((AbstractActor) actor).attachContext(context);
            actor.preStart();
        } catch (Throwable t) {
            system.logger().error("preStart failed: " + path, t);
            stopNow("preStart-failed");
        }
    }

    ActorRef createChild(String name, Props props) {
        if (stopping.get()) return system.deadLetters();

        if (name.isEmpty() || name.contains("/")) throw new IllegalArgumentException("Invalid child name: " + name);
        ActorCell existing = children.get(name);
        if (existing != null) return existing.ref();

        ActorPath childPath = ActorPath.childOf(this.path, name);
        ActorCell child = new ActorCell(system, this, childPath, props);

        ActorCell prev = children.putIfAbsent(name, child);
        if (prev != null) {
            child.stopNow("duplicate-child-name");
            return prev.ref();
        }
        child.start();
        return child.ref();
    }

    void enqueue(Envelope env) {
        if (env == null) return;

        if (stopping.get()) {
            system.sendToDeadLetters(env, "actor-stopping");
            return;
        }

        // bounded offer
        int s = qSize.incrementAndGet();
        if (s <= capacity) {
            q.offer(env);
            scheduleIfNeeded();
            return;
        }

        // overflow
        switch (props.overflowStrategy) {
            case DROP_NEW: {
                qSize.decrementAndGet();
                system.sendToDeadLetters(env, "mailbox-overflow-drop-new");
                return;
            }
            case DROP_OLD: {
                Envelope old = q.poll();
                if (old != null) {
                    qSize.decrementAndGet();
                    system.sendToDeadLetters(old, "mailbox-overflow-drop-old");
                    q.offer(env);
                    scheduleIfNeeded();
                    return;
                } else {
                    qSize.decrementAndGet();
                    system.sendToDeadLetters(env, "mailbox-overflow-drop-new-degraded");
                    return;
                }
            }
            case FAIL: {
                // drop message, but crash actor (once) via marker
                qSize.decrementAndGet();
                system.sendToDeadLetters(env, "mailbox-overflow-fail-drop");
                if (overflowMarkerEnqueued.compareAndSet(false, true)) {
                    q.offer(new Envelope(MAILBOX_OVERFLOW, ActorRef.noSender()));
                    scheduleIfNeeded();
                }
                return;
            }
            default: {
                qSize.decrementAndGet();
                system.sendToDeadLetters(env, "mailbox-overflow-unknown");
            }
        }
    }

    private void scheduleIfNeeded() {
        if (scheduled.compareAndSet(false, true)) {
            system.dispatcher(props.dispatcherId).execute(this);
        }
    }

    @Override public void run() {
        try {
            int processed = 0;
            int max = props.throughput;

            while (processed < max) {
                Envelope env = q.poll();
                if (env == null) break;
                qSize.decrementAndGet();
                processed++;

                if (stopping.get()) {
                    system.sendToDeadLetters(env, "actor-stopping");
                    continue;
                }

                if (env.message == PoisonPill.INSTANCE) {
                    stopGracefully("poison-pill");
                    continue;
                }

                if (env.message == MAILBOX_OVERFLOW) {
                    overflowMarkerEnqueued.set(false);
                    handleFailure(new RuntimeException("Mailbox overflow (" + path + ")"));
                    continue;
                }

                try {
                    context.setSender(env.sender);
                    actor.onReceive(env.message);
                } catch (Throwable t) {
                    handleFailure(t);
                } finally {
                    context.setSender(ActorRef.noSender());
                }
            }
        } finally {
            scheduled.set(false);
            if (!stopping.get() && q.peek() != null) scheduleIfNeeded();
        }
    }

    private void handleFailure(Throwable t) {
        system.logger().error("Actor failed: " + path, t);

        SupervisorStrategy strat = (props.supervisorStrategy != null) ? props.supervisorStrategy : system.defaultStrategy();
        SupervisorStrategy.Directive d = strat.decide(t);

        if (d.type == SupervisorStrategy.Directive.Type.STOP) {
            stopGracefully("failure-stop");
            notifyParentFailure(t);
            return;
        }

        long backoff = d.backoffMillis;
        if (backoff <= 0) {
            int k = restartCount.getAndIncrement();
            long exp = (long) (100L * Math.pow(2, Math.min(6, k))); // 100..6400
            backoff = Math.min(5000L, exp);
        }

        notifyParentFailure(t);
        restartWithBackoff(t, backoff);
    }

    private void notifyParentFailure(Throwable t) {
        if (parent != null) {
            parent.ref().tell(new Failure(this.ref(), t), this.ref());
        }
    }

    private void restartWithBackoff(Throwable reason, long backoffMillis) {
        system.logger().warn("Restarting " + path + " in " + backoffMillis + "ms");
        system.schedExec().schedule(() -> {
            if (stopping.get()) return;
            try {
                try { actor.preRestart(reason); } catch (Throwable ignore) {}

                Actor newActor = newActorInstance();
                actor = newActor;
                if (actor instanceof AbstractActor) ((AbstractActor) actor).attachContext(context);

                try { actor.postRestart(reason); } catch (Throwable ignore) {}
                try { actor.preStart(); } catch (Throwable ignore) {}

            } catch (Throwable t) {
                system.logger().error("Restart failed, stopping: " + path, t);
                stopGracefully("restart-failed");
            }
        }, backoffMillis, TimeUnit.MILLISECONDS);
    }

    private Actor newActorInstance() {
        Actor a = props.producer.produce();
        if (a == null) throw new IllegalStateException("Props producer returned null for " + path);
        return a;
    }

    void stop(ActorRef target) {
        if (target == null) return;
        ActorCell c = target.cell();
        if (c != null) c.stopGracefully("stop-by-context");
    }

    void stopGracefully(String reason) {
        if (!stopping.compareAndSet(false, true)) return;

        // stop children
        for (ActorCell c : children.values()) {
            try { c.stopGracefully("parent-stopping"); } catch (Throwable ignore) {}
        }
        children.clear();

        // drain mailbox
        Envelope e;
        while ((e = q.poll()) != null) qSize.decrementAndGet();

        try { actor.postStop(); } catch (Throwable t) { system.logger().error("postStop failed: " + path, t); }

        // notify watchers
        for (ActorCell w : watchers) {
            try { w.ref().tell(new Terminated(this.ref()), this.ref()); } catch (Throwable ignore) {}
        }
        watchers.clear();

        // unregister
        system.unregister(ref);
        if (parent == null) system.unregisterTopLevel(this);
        else parent.children.remove(path.name(), this);
    }

    void stopNow(String reason) { stopGracefully(reason); }

    void watch(ActorRef target) {
        ActorCell t = (target != null) ? target.cell() : null;
        if (t != null) t.watchers.add(this);
    }

    void unwatch(ActorRef target) {
        ActorCell t = (target != null) ? target.cell() : null;
        if (t != null) t.watchers.remove(this);
    }
}