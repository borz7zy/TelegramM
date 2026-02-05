package com.github.borz7zy.telegramm;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.github.borz7zy.telegramm.core.accounts.AccountDao;
import com.github.borz7zy.telegramm.core.accounts.AccountEntity;
import com.github.borz7zy.telegramm.core.settings.SettingsDao;
import com.github.borz7zy.telegramm.core.settings.SettingsEntity;

@Database(entities = {AccountEntity.class, SettingsEntity.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AccountDao accountDao();
    public abstract SettingsDao settingsDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context){
        if(INSTANCE == null){
            synchronized (AppDatabase.class){
                if(INSTANCE == null){
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "tgm_db")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
