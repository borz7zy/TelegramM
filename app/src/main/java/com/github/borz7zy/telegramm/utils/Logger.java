package com.github.borz7zy.telegramm.utils;

import android.content.Context;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Logger {
    private static final String TAG = "TelegramM";
    private static File logFile;

    private static final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    public static void init(Context context) {

        File dir = new File(context.getFilesDir(), "logs");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        logFile = new File(dir, "app.log");
    }

    public static void LOGD(String tag, String msg) {
        Log.d(tag, msg);
        write("D", tag, msg);
    }

    public static void LOGI(String tag, String msg, Throwable t) {
        Log.i(tag, msg, t);
        write("I", tag, msg + "\n" + Log.getStackTraceString(t));
    }

    public static void LOGW(String tag, String msg) {
        Log.w(tag, msg);
        write("W", tag, msg);
    }

    public static void LOGE(String tag, String msg, Throwable t) {
        Log.e(tag, msg, t);
        write("E", tag, msg + "\n" + Log.getStackTraceString(t));
    }

    public static void LOGE(String tag, String msg) {
        Log.e(tag, msg);
        write("E", tag, msg);
    }

    private static void write(String level, String tag, String msg) {

        if (logFile == null) return;

        executor.execute(() -> {

            try (FileWriter fw = new FileWriter(logFile, true);
                 BufferedWriter bw = new BufferedWriter(fw)) {

                String line = System.currentTimeMillis()
                        + " " + level
                        + "/" + tag
                        + ": " + msg + "\n";

                bw.write(line);

            } catch (Exception e) {
                Log.e(TAG, "Logger write failed", e);
            }
        });
    }
}
