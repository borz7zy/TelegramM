package com.github.borz7zy.telegramm;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.core.content.ContextCompat;

import com.github.borz7zy.telegramm.ui.ThemeEngine;

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

    private ThemeEngine themeEngine;

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
    @SuppressLint("ResourceAsColor")
    private AppManager(Context context){
        this.context = context.getApplicationContext();

        this.executorDb = Executors.newSingleThreadExecutor();

        this.db = AppDatabase.getDatabase(context);

        this.themeEngine = new ThemeEngine();
        boolean isNight = true; // TODO
        final int seedColor = ContextCompat.getColor(context, R.color.primaryColor);
        this.themeEngine.initTheme(seedColor, isNight);
    }

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

    public ThemeEngine getThemeEngine(){
        return themeEngine;
    }
}
