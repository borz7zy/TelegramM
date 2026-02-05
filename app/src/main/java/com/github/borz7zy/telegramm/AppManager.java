package com.github.borz7zy.telegramm;

import android.content.Context;

import com.github.borz7zy.telegramm.core.accounts.AccountManager;
import com.github.borz7zy.telegramm.core.accounts.AccountStorage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * The manager must initialize everything in itself,
 * be a singleton,
 * and provide public getters for everything it stores.
 */
public class AppManager {
    private final ExecutorService executorDb;
    private AppDatabase db;

    private static volatile AppManager INSTANCE;
    private final Context context;

    public static void init(Context context){
        if(INSTANCE == null){
            synchronized (AppManager.class){
                if(INSTANCE == null){
                    INSTANCE = new AppManager(context);
                }
            }
        }
    }

    public static AppManager getInstance(){
        if(INSTANCE == null)
            throw new IllegalStateException("AppManager is not initialized! Call AppManager.init() in the App class.");
        return INSTANCE;
    }
    private AppManager(Context context){
        this.context = context.getApplicationContext();

        this.executorDb = Executors.newSingleThreadExecutor();

        this.db = AppDatabase.getDatabase(App.getApplication().getApplicationContext());
    }

//    private void startAppLogic(){
//        AccountManager.getInstance().initialize();
//    }

    // --------------------
    // Getters/Setters
    // --------------------

    public ExecutorService getExecutorDb(){
        return executorDb;
    }

    public AppDatabase getAppDatabase(){
        return db;
    }

    public Context getContext(){
        return context;
    }
}
