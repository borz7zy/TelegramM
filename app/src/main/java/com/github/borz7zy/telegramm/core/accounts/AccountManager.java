package com.github.borz7zy.telegramm.core.accounts;

import com.github.borz7zy.telegramm.AppManager;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/*
 * The account manager must be a singleton,
 * initialize all created accounts in tdlib,
 * and also have public getters
 */
public class AccountManager {
    private static AccountManager INSTANCE;

    private final ConcurrentHashMap<Integer, AccountSession> sessions = new ConcurrentHashMap<>();

    public static synchronized AccountManager getInstance(){
        if(INSTANCE == null)
            INSTANCE = new AccountManager();

        return INSTANCE;
    }

    private AccountManager(){}

    public void switchAccount(int accountId){
        AccountStorage.getInstance().setCurrentActive(accountId);
    }

    // --------------------
    // Getters/Setters
    // --------------------

    public AccountSession getSession(int accountId) {
        return sessions.get(accountId);
    }

    public AccountSession getOrCreateSession(AccountEntity account){
        return sessions.computeIfAbsent(
                account.getAccountId(),
                id -> new AccountSession(
                        AppManager.getInstance().getContext(),
                        account
                )
        );
    }
}
