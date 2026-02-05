package com.github.borz7zy.telegramm.core.settings;

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
}
