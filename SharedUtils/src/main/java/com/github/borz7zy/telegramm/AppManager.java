package com.github.borz7zy.telegramm;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.github.borz7zy.sharedutils.R;
import com.github.borz7zy.telegramm.core.TgConfig;
import com.github.borz7zy.telegramm.core.settings.SettingsEntity;
import com.github.borz7zy.telegramm.core.theme.ThemeEngine;
import com.github.borz7zy.telegramm.core.theme.ThemeRepository;

import org.drinkless.tdlib.TdApi;

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
    private final TgConfig config;

    private ThemeEngine themeEngine;

    public static void init(Context context, TgConfig config){
        if(INSTANCE == null){
            synchronized (AppManager.class){
                if(INSTANCE == null){
                    INSTANCE = new AppManager(context, config);
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
    private AppManager(Context context, TgConfig config){
        this.context = context.getApplicationContext();
        this.config = config;

        this.executorDb = Executors.newSingleThreadExecutor();

        this.db = AppDatabase.getDatabase(context);

        this.themeEngine = new ThemeEngine();
        // Bootstrap with the static fallback so the very first frame has *some*
        // theme. The DB read below upgrades the seed once persisted prefs (and
        // later the TDLib EmojiChatTheme) are available.
        final int defaultSeed = ContextCompat.getColor(context, R.color.primaryColor);
        this.themeEngine.initTheme(defaultSeed, /* isDark */ true);

        // observeForever() must be attached on the main thread; the watcher
        // itself just dispatches DB reads back onto the executor.
        new Handler(Looper.getMainLooper()).post(() -> wireThemeNameWatcher(defaultSeed));

        // Read persisted theme prefs off the DB executor; this gives us the
        // initial seed before TDLib's UpdateEmojiChatThemes arrives. The
        // watcher re-seeds again once the theme list is populated.
        executorDb.execute(() -> {
            SettingsEntity s = db.settingsDao().getSettings();
            if (s == null) return;
            applySeedFromSettings(s, defaultSeed);
        });
    }

    private void applySeedFromSettings(@NonNull SettingsEntity s, int defaultSeed) {
        boolean isDark = s.followSystemDark ? isSystemDark() : s.isDark;
        int seed = (s.customSeedColor != null)
                ? s.customSeedColor
                : seedFromThemeName(s.themeName, isDark, defaultSeed);
        themeEngine.updateTheme(seed, isDark);
    }

    private boolean isSystemDark() {
        int uiMode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /**
     * Re-derive {@code isDark} from the current Configuration when the user
     * has opted into "follow system" night mode. Called from
     * {@link android.app.Activity#onConfigurationChanged} so the palette flips
     * with the system. No-op for users with an explicit isDark preference.
     */
    public void refreshNightModeFromSystem() {
        int defaultSeed = ContextCompat.getColor(context, R.color.primaryColor);
        executorDb.execute(() -> {
            SettingsEntity s = db.settingsDao().getSettings();
            if (s == null || !s.followSystemDark) return;
            applySeedFromSettings(s, defaultSeed);
        });
    }

    /**
     * Resolve a theme name to its accent color through the theme repository.
     * Returns {@code defaultSeed} if the theme list hasn't loaded yet — in
     * which case {@link #wireThemeNameWatcher} re-seeds once the list arrives.
     */
    private int seedFromThemeName(String themeName, boolean isDark, int defaultSeed) {
        if (themeName == null || themeName.isEmpty()) return defaultSeed;
        TdApi.EmojiChatTheme t = ThemeRepository.get().findThemeByName(themeName);
        if (t == null) return defaultSeed;
        TdApi.ThemeSettings ts = isDark ? t.darkSettings : t.lightSettings;
        return (ts != null) ? ts.accentColor : defaultSeed;
    }

    private void wireThemeNameWatcher(int defaultSeed) {
        // ThemeRepository emits its first non-empty list on the main thread
        // when TDLib delivers UpdateEmojiChatThemes. Observe forever — the
        // application context outlives any one Activity.
        ThemeRepository.get().getEmojiThemes().observeForever(themes -> {
            if (themes == null || themes.isEmpty()) return;
            executorDb.execute(() -> {
                SettingsEntity latest = db.settingsDao().getSettings();
                if (latest == null) return;
                applySeedFromSettings(latest, defaultSeed);
            });
        });
    }

    /**
     * Persist a new global theme selection and propagate it to {@link ThemeEngine}
     * immediately. The DB write happens off-thread; the Monet update is synchronous.
     *
     * <p>Selecting a theme always disables follow-system, since the user is
     * making an explicit isDark choice. Use {@link #setFollowSystemDark} to
     * opt back in.
     */
    public void selectTheme(String themeName, boolean isDark, Integer customSeedColor) {
        executorDb.execute(() -> {
            SettingsEntity s = db.settingsDao().getSettings();
            if (s == null) {
                s = new SettingsEntity();
                s.themeName = themeName;
                s.isDark = isDark;
                s.customSeedColor = customSeedColor;
                s.followSystemDark = false;
                db.settingsDao().insert(s);
            } else {
                s.themeName = themeName;
                s.isDark = isDark;
                s.customSeedColor = customSeedColor;
                s.followSystemDark = false;
                db.settingsDao().update(s);
            }
        });

        int defaultSeed = ContextCompat.getColor(context, R.color.primaryColor);
        int seed = (customSeedColor != null)
                ? customSeedColor
                : seedFromThemeName(themeName, isDark, defaultSeed);
        themeEngine.updateTheme(seed, isDark);
    }

    /**
     * Toggle follow-system night mode. When enabled, the persisted
     * {@code isDark} is ignored and the engine re-seeds against the current
     * system Configuration. Useful from the theme picker.
     */
    public void setFollowSystemDark(boolean enabled) {
        int defaultSeed = ContextCompat.getColor(context, R.color.primaryColor);
        boolean effectiveDark = enabled ? isSystemDark()
                : (themeEngine.getCurrentTheme().getValue() != null
                        && themeEngine.getCurrentTheme().getValue().isDark);
        executorDb.execute(() -> {
            SettingsEntity s = db.settingsDao().getSettings();
            if (s == null) {
                s = new SettingsEntity();
                s.followSystemDark = enabled;
                s.isDark = effectiveDark;
                db.settingsDao().insert(s);
            } else {
                s.followSystemDark = enabled;
                if (enabled) s.isDark = effectiveDark; // mirror the system pick into the field
                db.settingsDao().update(s);
            }
            applySeedFromSettings(s, defaultSeed);
        });
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

    public TgConfig getConfig(){
        return config;
    }
}