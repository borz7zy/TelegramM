package com.github.borz7zy.telegramm.actor;

public interface Actor {
    default void preStart() throws Exception {}
    default void postStop() throws Exception {}
    default void preRestart(Throwable reason) throws Exception {}
    default void postRestart(Throwable reason) throws Exception {}

    void onReceive(Object message) throws Exception;
}
