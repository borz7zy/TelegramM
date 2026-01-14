package com.github.borz7zy.telegramm.actors;

import android.content.Context;

import com.github.borz7zy.telegramm.actor.AbstractActor;
import com.github.borz7zy.telegramm.actor.ActorRef;
import com.github.borz7zy.telegramm.actor.Ask;
import com.github.borz7zy.telegramm.actor.Props;
import com.github.borz7zy.telegramm.core.TdMessages;

import java.util.HashMap;
import java.util.Map;

public class AccountManagerActor extends AbstractActor {
    private final Context context;
    private final Map<Integer, ActorRef> accounts = new HashMap<>();

    public AccountManagerActor(Context context) {
        this.context = context;
    }

    @Override
    public void onReceive(Object message) {
        if (message instanceof Ask.Request) {
            Ask.Request request = (Ask.Request) message;

            Object payload = request.payload;

            if (payload instanceof TdMessages.GetAccount) {
                int accId = ((TdMessages.GetAccount) payload).accountId;

                ActorRef child = accounts.get(accId);
                if (child == null) {
                    Props props = Props.of(() -> new TdClientActor(context, accId))
                            .dispatcher("io");
                    child = context().actorOf("user" + accId, props);
                    accounts.put(accId, child);
                }

                sender().tell(Ask.Response.success(request.id, child));
            }
        }
        else if (message instanceof TdMessages.GetAccount) {
            int accId = ((TdMessages.GetAccount) message).accountId;

            ActorRef child = accounts.get(accId);
            if (child == null) {
                Props props = Props.of(() -> new TdClientActor(context, accId))
                        .dispatcher("io");

                child = context().actorOf("user" + accId, props);
                accounts.put(accId, child);
            }

            sender().tell(child);
        }
    }
}
