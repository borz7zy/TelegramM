package com.github.borz7zy.telegramm.actor;

final class Envelope {
    final Object message;
    final ActorRef sender;
    Envelope(Object message, ActorRef sender) {
        this.message = message;
        this.sender = sender;
    }
}
