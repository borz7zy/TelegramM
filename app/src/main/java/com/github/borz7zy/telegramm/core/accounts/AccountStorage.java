package com.github.borz7zy.telegramm.core.accounts;

import androidx.lifecycle.LiveData;

import com.github.borz7zy.telegramm.AppManager;
import com.github.borz7zy.telegramm.core.settings.SettingsDao;
import com.github.borz7zy.telegramm.core.settings.SettingsEntity;
import com.github.borz7zy.telegramm.utils.Logger;

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


    public void ensureFirstAccountExists() {

        AppManager.getInstance().getExecutorDb().execute(() -> {

            AccountDao dao =
                    AppManager.getInstance()
                            .getAppDatabase()
                            .accountDao();

            int count = dao.getAccountsCount();

            if (count > 0) return;

            AccountEntity newAccount = new AccountEntity(
                    null,
                    0L,
                    "New Account",
                    ""
            );

            long newId = dao.insert(newAccount);

            setCurrentActive((int) newId);
        });
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

    public void setCurrentActive(Integer id) {
        AppManager.getInstance().getExecutorDb().execute(() -> {

            SettingsDao settingsDao = AppManager.getInstance()
                    .getAppDatabase()
                    .settingsDao();

            SettingsEntity settings = settingsDao.getSettings();

            if (settings == null) {
                settings = new SettingsEntity();
                settings.id = 1;
                settings.currentActiveId = id;
                settingsDao.insert(settings);
            } else {
                settings.currentActiveId = id;
                settingsDao.update(settings);
            }

            Logger.LOGD("AccountStorage", "Active account saved: " + id);
        });
    }
}
