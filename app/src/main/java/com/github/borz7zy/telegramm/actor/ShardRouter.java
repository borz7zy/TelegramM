package com.github.borz7zy.telegramm.actor;

public final class ShardRouter extends AbstractActor {

    public interface Factory {
        Props propsFor(String accountId);
    }

    public static final class Route {
        public final String accountId;
        public final Object payload;
        public Route(String accountId, Object payload) {
            this.accountId = accountId;
            this.payload = payload;
        }
    }

    private final Factory factory;

    public ShardRouter(Factory factory) {
        this.factory = factory;
    }

    @Override public void onReceive(Object message) {
        if (!(message instanceof Route)) return;
        Route r = (Route) message;

        ActorRef acc = context().actorOf(r.accountId, factory.propsFor(r.accountId));

        acc.tell(r.payload, sender());
    }
}