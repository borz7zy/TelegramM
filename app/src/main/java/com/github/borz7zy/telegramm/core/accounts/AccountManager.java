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
    private volatile AccountSession activeSession;

    public static synchronized AccountManager getInstance(){
        if(INSTANCE == null)
            INSTANCE = new AccountManager();

        return INSTANCE;
    }

    private AccountManager(){}

    public void initialize(){
        AccountStorage.getInstance().getAllAccounts(new AccountCallback() {
            @Override
            public void onAccountsLoaded(List<AccountEntity> accounts) {
                initAccountsInTdLib(accounts);
            }
        });
    }

    private void initAccountsInTdLib(List<AccountEntity> accounts){
        if(accounts.isEmpty() || accounts == null){
            return;
        }

        for(AccountEntity account : accounts){
            if (sessions.containsKey(account.getAccountId())) {
                continue;
            }

            AccountSession session =
                    new AccountSession(AppManager.getInstance()
                            .getContext().getApplicationContext(), account);
            sessions.put(account.getAccountId(), session);

        }
    }

    public void switchAccount(int accountId){

        AccountSession session = sessions.get(accountId);

        if(session == null) return;

        activeSession = session;

        AccountStorage.getInstance().setCurrentActive(accountId);
    }

    // --------------------
    // Getters/Setters
    // --------------------

    public AccountSession getSession(int accountId) {
        return sessions.get(accountId);
    }

    public AccountSession getActiveSession() {
        return activeSession;
    }
}
