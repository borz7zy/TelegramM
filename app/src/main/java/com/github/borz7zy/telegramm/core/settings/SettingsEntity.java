package com.github.borz7zy.telegramm.core.settings;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "app_settings")
public class SettingsEntity {

    @PrimaryKey
    @ColumnInfo(name = "id")
    public int id = 1;

    @ColumnInfo(name = "current_active_id")
    public Integer currentActiveId;

    /**
     * Name of the selected {@code TdApi.EmojiChatTheme}, or {@code null} for
     * "no chat theme" (Monet derives from {@link #customSeedColor} or the
     * built-in default).
     */
    @Nullable
    @ColumnInfo(name = "theme_name")
    public String themeName;

    /**
     * Whether the global UI uses the dark variant of the selected theme.
     */
    @ColumnInfo(name = "is_dark", defaultValue = "1")
    public boolean isDark = true;

    /**
     * Optional ARGB seed override for Monet. Takes precedence over the
     * theme's own accent color when non-null. {@code null} = use theme accent.
     */
    @Nullable
    @ColumnInfo(name = "custom_seed_color")
    public Integer customSeedColor;

    /**
     * When true, {@link #isDark} is overridden by the system's current
     * UI mode (Configuration#UI_MODE_NIGHT_*). When false, the persisted
     * {@link #isDark} flag is honored as-is.
     */
    @ColumnInfo(name = "follow_system_dark", defaultValue = "0")
    public boolean followSystemDark = false;
}