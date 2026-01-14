package com.github.borz7zy.telegramm.actor;

import android.util.Log;

public interface Logger {
    void debug(String msg);
    void info(String msg);
    void warn(String msg);
    void error(String msg, Throwable t);

    static Logger stdout() {
        return new Logger() {
            private static final String TAG = "ActorSystem";
            @Override public void debug(String msg) { Log.d(TAG, msg); }
            @Override public void info(String msg)  { Log.i(TAG, msg); }
            @Override public void warn(String msg)  { Log.w(TAG, msg); }
            @Override public void error(String msg, Throwable t) { Log.e(TAG, msg, t); }
        };
    }
}
