package com.github.borz7zy.telegramm;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.github.borz7zy.telegramm.core.accounts.AccountDao;
import com.github.borz7zy.telegramm.core.accounts.AccountEntity;
import com.github.borz7zy.telegramm.core.settings.SettingsDao;
import com.github.borz7zy.telegramm.core.settings.SettingsEntity;

@Database(entities = {AccountEntity.class, SettingsEntity.class}, version = 3)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AccountDao accountDao();
    public abstract SettingsDao settingsDao();

    private static volatile AppDatabase INSTANCE;

    /**
     * v1 → v2: add theme columns to app_settings.
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE app_settings ADD COLUMN theme_name TEXT");
            db.execSQL("ALTER TABLE app_settings ADD COLUMN is_dark INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE app_settings ADD COLUMN custom_seed_color INTEGER");
        }
    };

    /**
     * v2 → v3: add follow_system_dark column.
     */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE app_settings ADD COLUMN follow_system_dark INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static AppDatabase getDatabase(final Context context){
        if(INSTANCE == null){
            synchronized (AppDatabase.class){
                if(INSTANCE == null){
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "tgm_db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}