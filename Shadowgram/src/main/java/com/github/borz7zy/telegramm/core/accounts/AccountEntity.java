package com.github.borz7zy.telegramm.core.accounts;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "accounts")
public class AccountEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "account_id")
    public int accountId;

    @ColumnInfo(name = "account_db_folder")
    private String accountDbFolder;

    @ColumnInfo(name = "account_tg_id")
    private long accountTgId;

    @ColumnInfo(name = "account_name")
    private String accountName;

    @ColumnInfo(name = "account_username")
    private String accountUsername;

    // --------------------
    // CONSTRUCTORS
    // --------------------
    public AccountEntity() {
    }

    @Ignore
    public AccountEntity(String accountDbFolder, long accountTgId, String accountName, String accountUsername) {
        this.accountDbFolder = accountDbFolder;
        this.accountTgId = accountTgId;
        this.accountName = accountName;
        this.accountUsername = accountUsername;
    }

    // --------------------
    // Getters/Setters
    // --------------------

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public String getAccountDbFolder() {
        return accountDbFolder;
    }

    public void setAccountDbFolder(String accountDbFolder) {
        this.accountDbFolder = accountDbFolder;
    }

    public long getAccountTgId() {
        return accountTgId;
    }

    public void setAccountTgId(long accountTgId) {
        this.accountTgId = accountTgId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getAccountUsername() {
        return accountUsername;
    }

    public void setAccountUsername(String accountUsername) {
        this.accountUsername = accountUsername;
    }
}
