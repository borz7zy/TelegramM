package com.github.borz7zy.telegramm.actor;

public final class Ask {
    private Ask() {}

    public static final class Request {
        public final long id;
        public final Object payload;
        public Request(long id, Object payload) {
            this.id = id;
            this.payload = payload;
        }
    }

    public static final class Response {
        public final long id;
        public final Object payload;
        public final Throwable error;

        private Response(long id, Object payload, Throwable error) {
            this.id = id;
            this.payload = payload;
            this.error = error;
        }

        public static Response success(long id, Object payload) { return new Response(id, payload, null); }
        public static Response failure(long id, Throwable error) { return new Response(id, null, error); }
    }

    static final class Timeout {
        final long id;
        Timeout(long id) { this.id = id; }
    }

    static final class Register {
        final long id;
        final Promise<Object> promise;
        final long timeoutMillis;
        Register(long id, Promise<Object> promise, long timeoutMillis) {
            this.id = id;
            this.promise = promise;
            this.timeoutMillis = timeoutMillis;
        }
    }
}