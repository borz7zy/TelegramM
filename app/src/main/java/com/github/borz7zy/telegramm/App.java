package com.github.borz7zy.telegramm;

import android.app.Application;

import com.github.borz7zy.telegramm.utils.Logger;

import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

public class App extends Application {
    private static App INSTANCE = null;

    @Override
    public void onCreate() {
        super.onCreate();

        Logger.init(this);

        setInstance(this);

        AppManager.init(this);

        try {
            Client.execute(new TdApi.SetLogVerbosityLevel(1));
        } catch (Exception e) {
            Logger.LOGE("TdLib", "Failed to set verbosity", e);
        }
    }

    private static void setInstance(App instance) {
        if (INSTANCE == null)
            INSTANCE = instance;
    }

    public static App getApplication(){
        return INSTANCE;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

    @Override
    public void onTrimMemory(int level){
        super.onTrimMemory(level);

        if(level >= TRIM_MEMORY_RUNNING_LOW){
        }
    }

    @Override
    public void onLowMemory(){
        super.onLowMemory();
    }
}
