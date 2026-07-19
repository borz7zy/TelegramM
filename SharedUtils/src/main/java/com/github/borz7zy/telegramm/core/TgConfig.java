package com.github.borz7zy.telegramm.core;

/**
 * Build-time TDLib credentials and version info.
 *
 * <p>These values originate from {@code local.properties}/{@code gradle.properties}
 * and are injected as {@code resValue} strings by the <em>application</em> module,
 * so they are only readable there. The app reads them once in {@code App.onCreate()}
 * and hands them down via {@link com.github.borz7zy.telegramm.AppManager#init}, which
 * keeps this module free of any dependency on the app's generated {@code R} class.
 */
public final class TgConfig {

    private final int apiId;
    private final String apiHash;
    private final boolean testDc;
    private final String versionName;
    private final String versionCode;

    public TgConfig(int apiId, String apiHash, boolean testDc,
                    String versionName, String versionCode) {
        this.apiId = apiId;
        this.apiHash = apiHash;
        this.testDc = testDc;
        this.versionName = versionName;
        this.versionCode = versionCode;
    }

    public int getApiId() {
        return apiId;
    }

    public String getApiHash() {
        return apiHash;
    }

    public boolean isTestDc() {
        return testDc;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getVersionCode() {
        return versionCode;
    }

    /** Formatted as TDLib's {@code applicationVersion}, e.g. {@code "0.2.0 (20)"}. */
    public String getApplicationVersion() {
        return versionName + " (" + versionCode + ")";
    }
}
