package com.github.borz7zy.telegramm.actor;

public abstract class AbstractActor implements Actor {
    private ActorContext context;

    final void attachContext(ActorContext ctx) { this.context = ctx; }

    protected final ActorContext context() { return context; }
    protected final ActorRef self() { return context.self(); }
    protected final ActorRef sender() { return context.sender(); }

    protected final void stopSelf() { context.stop(self()); }
}
