package com.github.borz7zy.telegramm;

import android.app.Application;
import android.util.Log;

import com.github.borz7zy.telegramm.actor.ActorRef;
import com.github.borz7zy.telegramm.actor.ActorSystem;
import com.github.borz7zy.telegramm.actor.AndroidMainExecutor;
import com.github.borz7zy.telegramm.actor.Props;
import com.github.borz7zy.telegramm.actors.AccountManagerActor;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {

    private ActorSystem system;
    private ActorRef accountManager;

    private static App INSTANCE;

    @Override
    public void onCreate() {
        super.onCreate();
        INSTANCE = this;

        try {
            Client.execute(new TdApi.SetLogVerbosityLevel(1));
        } catch (Exception e) {
            Log.e("TdLib", "Failed to set verbosity", e);
        }

        system = ActorSystem.builder("telegram-client")
                .addDispatcher("ui", new AndroidMainExecutor())
                .addDispatcher("cpu", Executors.newFixedThreadPool(
                        Math.max(2, Runtime.getRuntime().availableProcessors())
                ))
                .addDispatcher("io", Executors.newCachedThreadPool())
                .build();

        accountManager = system.actorOf("accounts",
                Props.of(() -> new AccountManagerActor(this)).dispatcher("cpu"));
    }

    public static App getApplication(){
        return INSTANCE;
    }

    public ActorSystem getActorSystem() {
        return system;
    }

    public ActorRef getAccountManager() {
        return accountManager;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        system.close();
    }
}
