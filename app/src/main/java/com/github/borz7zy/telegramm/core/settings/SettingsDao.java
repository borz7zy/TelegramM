package com.github.borz7zy.telegramm.core.settings;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface SettingsDao {

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    SettingsEntity getSettings();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SettingsEntity settings);

    @Update()
    void update(SettingsEntity settings);
}