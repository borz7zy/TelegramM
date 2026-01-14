package com.github.borz7zy.telegramm.actor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class AskHubActor extends AbstractActor {
    private static final class Entry {
        final Promise<Object> p;
        Entry(Promise<Object> p) { this.p = p; }
    }

    private final Map<Long, Entry> inflight = new HashMap<>(1024);

    @Override public void onReceive(Object message) {
        if (message instanceof Ask.Register) {
            Ask.Register r = (Ask.Register) message;
            inflight.put(r.id, new Entry(r.promise));

            context().system().scheduler().scheduleOnce(r.timeoutMillis, TimeUnit.MILLISECONDS,
                    self(), new Ask.Timeout(r.id), self());
            return;
        }

        if (message instanceof Ask.Timeout) {
            Ask.Timeout t = (Ask.Timeout) message;
            Entry e = inflight.remove(t.id);
            if (e != null) e.p.tryFailure(new java.util.concurrent.TimeoutException("ask timeout id=" + t.id));
            return;
        }

        if (message instanceof Ask.Response) {
            Ask.Response resp = (Ask.Response) message;
            Entry e = inflight.remove(resp.id);
            if (e != null) {
                if (resp.error != null) e.p.tryFailure(resp.error);
                else e.p.trySuccess(resp.payload);
            }
        }
    }
}