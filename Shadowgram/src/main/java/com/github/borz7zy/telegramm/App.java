package com.github.borz7zy.telegramm;

import android.app.Application;

import com.github.borz7zy.telegramm.core.TgConfig;
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

        AppManager.init(this, buildConfig());

        try {
            Client.execute(new TdApi.SetLogVerbosityLevel(1));
        } catch (Exception e) {
            Logger.LOGE("TdLib", "Failed to set verbosity", e);
        }
    }

    /**
     * Reads the credentials injected as {@code resValue} strings by this module's
     * build script. This is the only place they are read: everything downstream
     * receives them through {@link TgConfig}, so no library module needs the
     * application's generated {@code R} class.
     */
    private TgConfig buildConfig() {
        int apiId = 0;
        try {
            apiId = Integer.parseInt(getString(R.string.api_id));
        } catch (NumberFormatException e) {
            // Empty when local.properties has no api_id; TDLib will reject the
            // parameters later with a clearer error than a crash here would give.
            Logger.LOGE("App", "Invalid api_id — check local.properties", e);
        }
        return new TgConfig(
                apiId,
                getString(R.string.api_hash),
                getResources().getBoolean(R.bool.test_dc),
                getString(R.string.version_name),
                getString(R.string.version_code));
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
