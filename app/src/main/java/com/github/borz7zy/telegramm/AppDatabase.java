package com.github.borz7zy.telegramm;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.github.borz7zy.telegramm.core.accounts.AccountDao;
import com.github.borz7zy.telegramm.core.accounts.AccountEntity;

@Database(entities = {AccountEntity.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract AccountDao accountDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context){
        if(INSTANCE == null){
            synchronized (AppDatabase.class){
                if(INSTANCE == null){
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "telegram_accounts_db")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
