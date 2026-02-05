package com.github.borz7zy.telegramm.core.accounts;

import androidx.lifecycle.LiveData;

import com.github.borz7zy.telegramm.AppManager;
import com.github.borz7zy.telegramm.core.settings.SettingsDao;
import com.github.borz7zy.telegramm.core.settings.SettingsEntity;

import java.util.List;

/*
 * The account storage must have:
 * - public account name getters
 * - public directory getters
 * - account space management logic (cache, etc.)
 * - be a singleton
 */
public class AccountStorage{
    private static AccountStorage INSTANCE = null;

    public static synchronized AccountStorage getInstance(){
        if(INSTANCE == null)
            INSTANCE = new AccountStorage();
        return INSTANCE;
    }

    private AccountStorage(){}

    public LiveData<AccountEntity> observeActiveAccount(){
        return AppManager.getInstance()
                .getAppDatabase()
                .accountDao()
                .observeActiveAccount();
    }

    // --------------------
    // Getters/Setters
    // --------------------
    public void getAllAccounts(AccountCallback callback){
        AppManager.getInstance()
                .getExecutorDb()
                .execute(new Runnable() {
            @Override
            public void run() {
                List<AccountEntity> accounts = AppManager.getInstance()
                        .getAppDatabase()
                        .accountDao()
                        .getAllAccounts();

                if(callback != null){
                    callback.onAccountsLoaded(accounts);
                }
            }
        });
    }

    public void getCurrentActive(AccountSingleCallback callback){
        AppManager.getInstance()
                .getExecutorDb()
                .execute(() -> {

                    SettingsEntity settings =
                            AppManager.getInstance()
                                    .getAppDatabase()
                                    .settingsDao()
                                    .getSettings();

                    if(settings == null || settings.currentActiveId == null){
                        callback.onAccountLoaded(null);
                        return;
                    }

                    AccountEntity account =
                            AppManager.getInstance()
                                    .getAppDatabase()
                                    .accountDao()
                                    .getAccountById(settings.currentActiveId);

                    callback.onAccountLoaded(account);
                });
    }

    public void setCurrentActive(int accountId){
        AppManager.getInstance()
                .getExecutorDb()
                .execute(() -> {

                    SettingsDao dao =
                            AppManager.getInstance()
                                    .getAppDatabase()
                                    .settingsDao();

                    SettingsEntity settings = dao.getSettings();

                    if(settings == null){
                        settings = new SettingsEntity();
                        settings.currentActiveId = accountId;
                        dao.insert(settings);
                    } else {
                        dao.setCurrentActiveId(accountId);
                    }
                });
    }
}
