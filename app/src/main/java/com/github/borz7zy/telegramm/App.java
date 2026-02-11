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

        if(INSTANCE == null)
            INSTANCE = this;

        AppManager.init(this);

        try {
            Client.execute(new TdApi.SetLogVerbosityLevel(1));
        } catch (Exception e) {
            Logger.LOGE("TdLib", "Failed to set verbosity", e);
        }
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
