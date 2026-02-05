package com.github.borz7zy.telegramm.core.accounts;


import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(AccountEntity account);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AccountEntity> accounts);

    @Query("SELECT * FROM accounts")
    List<AccountEntity> getAllAccounts();

    @Query("SELECT * FROM accounts WHERE account_id = :id")
    AccountEntity getAccountById(int id);

    @Query("SELECT * FROM accounts WHERE account_tg_id = :tgId LIMIT 1")
    AccountEntity getAccountByTgId(long tgId);

    @Update
    void update(AccountEntity account);

    @Delete
    void delete(AccountEntity account);

    @Query("DELETE FROM accounts WHERE account_id = :id")
    void deleteById(int id);

}
