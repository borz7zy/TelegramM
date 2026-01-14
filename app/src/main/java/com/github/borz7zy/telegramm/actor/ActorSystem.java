package com.github.borz7zy.telegramm.actor;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class ActorSystem implements AutoCloseable {
    private final String name;

    private final ConcurrentHashMap<String, ExecutorService> dispatchers = new ConcurrentHashMap<>();
    private final String defaultDispatcherId;

    private final ScheduledExecutorService scheduler;
    private final Logger logger;
    private final SupervisorStrategy defaultStrategy;
    private final DeadLetters deadLetters;

    private final ConcurrentHashMap<String, ActorCell> topLevel = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ActorRef> byPath = new ConcurrentHashMap<>();

    private volatile boolean terminated = false;

    private ActorSystem(Builder b) {
        this.name = b.name;
        this.logger = b.logger != null ? b.logger : Logger.stdout();
        this.defaultStrategy = b.defaultStrategy != null ? b.defaultStrategy : SupervisorStrategy.oneForOneDefault();
        this.defaultDispatcherId = b.defaultDispatcherId != null ? b.defaultDispatcherId : "cpu";

        // dispatchers
        if (b.dispatchers.isEmpty()) {
            dispatchers.put("cpu", Executors.newFixedThreadPool(
                    Math.max(2, Runtime.getRuntime().availableProcessors()),
                    namedFactory("actors-" + name + "-cpu", logger)
            ));
            dispatchers.put("io", Executors.newCachedThreadPool(
                    namedFactory("actors-" + name + "-io", logger)
            ));
        } else {
            dispatchers.putAll(b.dispatchers);
        }

        this.scheduler = b.scheduler != null ? b.scheduler : Executors.newSingleThreadScheduledExecutor(
                namedFactory("actors-" + name + "-sched", logger)
        );

        this.deadLetters = new DeadLetters(this);

        // built-in ask hub
        actorOf("askHub", Props.of(() -> new AskHubActor()).mailboxCapacity(4096).overflowStrategy(OverflowStrategy.DROP_OLD));
    }

    public static Builder builder(String name) { return new Builder(name); }

    public String name() { return name; }
    public Logger logger() { return logger; }
    public Scheduler scheduler() { return new Scheduler(this); }
    ScheduledExecutorService schedExec() { return scheduler; }

    public SupervisorStrategy defaultStrategy() { return defaultStrategy; }
    public ActorRef deadLetters() { return deadLetters.ref(); }

    public ExecutorService dispatcher() { return dispatcher(defaultDispatcherId); }

    public ExecutorService dispatcher(String id) {
        ExecutorService ex = dispatchers.get(id);
        if (ex != null) return ex;
        // fallback
        return dispatchers.get(defaultDispatcherId);
    }

    public ActorRef actorOf(String name, Props props) {
        requireRunning();
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(props, "props");
        if (name.isEmpty() || name.contains("/")) throw new IllegalArgumentException("Invalid actor name: " + name);

        ActorCell existing = topLevel.get(name);
        if (existing != null) return existing.ref();

        ActorPath path = ActorPath.root(this.name, name);
        ActorCell cell = ActorCell.createTopLevel(this, path, props);

        ActorCell prev = topLevel.putIfAbsent(name, cell);
        if (prev != null) {
            cell.stopNow("duplicate-top-level-name");
            return prev.ref();
        }
        cell.start();
        return cell.ref();
    }

    // resolve by "/a/b" inside this system, or by full "sys:/a/b"
    public ActorRef resolve(String path) {
        if (path == null || path.isEmpty()) return deadLetters();
        String key = path.startsWith("/") ? (name + ":" + path) : path;
        ActorRef ref = byPath.get(key);
        return ref != null ? ref : deadLetters();
    }

    void register(ActorRef ref) {
        if (ref == null) return;
        byPath.put(ref.path().toString(), ref);
    }

    void unregister(ActorRef ref) {
        if (ref == null) return;
        byPath.remove(ref.path().toString(), ref);
    }

    void unregisterTopLevel(ActorCell cell) {
        if (cell == null) return;
        topLevel.remove(cell.path().name(), cell);
    }

    void sendToDeadLetters(Envelope env, String reason) {
        deadLetters.accept(env, reason);
    }

    private void requireRunning() {
        if (terminated) throw new IllegalStateException("ActorSystem terminated");
    }

    @Override public void close() {
        terminated = true;

        for (Map.Entry<String, ActorCell> e : topLevel.entrySet()) {
            try { e.getValue().stopGracefully("system-close"); } catch (Throwable ignore) {}
        }
        topLevel.clear();

        for (ExecutorService ex : dispatchers.values()) {
            try { ex.shutdownNow(); } catch (Throwable ignore) {}
        }
        dispatchers.clear();

        scheduler.shutdownNow();
        byPath.clear();
    }

    static ThreadFactory namedFactory(String prefix, Logger logger) {
        AtomicInteger seq = new AtomicInteger(1);
        return r -> {
            Thread t = new Thread(r, prefix + "-" + seq.getAndIncrement());
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((th, ex) -> logger.error("Uncaught in " + th.getName(), ex));
            return t;
        };
    }

    public static final class Builder {
        private final String name;
        private final ConcurrentHashMap<String, ExecutorService> dispatchers = new ConcurrentHashMap<>();
        private String defaultDispatcherId = "cpu";
        private ScheduledExecutorService scheduler;
        private Logger logger;
        private SupervisorStrategy defaultStrategy;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public Builder addDispatcher(String id, ExecutorService ex) {
            dispatchers.put(Objects.requireNonNull(id), Objects.requireNonNull(ex));
            return this;
        }

        public Builder defaultDispatcher(String id) {
            this.defaultDispatcherId = Objects.requireNonNull(id);
            return this;
        }

        public Builder scheduler(ScheduledExecutorService scheduler) { this.scheduler = scheduler; return this; }
        public Builder logger(Logger logger) { this.logger = logger; return this; }
        public Builder defaultStrategy(SupervisorStrategy strategy) { this.defaultStrategy = strategy; return this; }

        public ActorSystem build() { return new ActorSystem(this); }
    }
}