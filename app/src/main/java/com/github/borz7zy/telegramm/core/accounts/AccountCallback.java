package com.github.borz7zy.telegramm.core.accounts;

import java.util.List;

public interface AccountCallback {
    void onAccountsLoaded(List<AccountEntity> accounts);
}
