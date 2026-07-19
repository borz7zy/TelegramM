package com.github.borz7zy.telegramm.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.borz7zy.shadowgram.shadowgramui.R;
import com.github.borz7zy.telegramm.ui.model.DialogItem;

import org.drinkless.tdlib.TdApi;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MainViewModel extends ViewModel {

    private final MutableLiveData<Integer> currentTab = new MutableLiveData<>(R.id.nav_chats);

    public LiveData<Integer> getCurrentTab() {
        return currentTab;
    }

    public void setCurrentTab(int id) {
        currentTab.setValue(id);
    }

    private final MutableLiveData<Integer> topInset = new MutableLiveData<>();

    public LiveData<Integer> getTopInset() {
        return topInset;
    }

    public void setTopInset(int value) {
        topInset.setValue(value);
    }

    private final MutableLiveData<Integer> bottomInset = new MutableLiveData<>();

    public LiveData<Integer> getBottomInset() {
        return bottomInset;
    }

    public void setBottomInset(int value) {
        bottomInset.setValue(value);
    }

    private final ConcurrentHashMap<Long, DialogItem> dialogs = new ConcurrentHashMap<>();

    public ConcurrentHashMap<Long, DialogItem> getDialogs() {
        return dialogs;
    }

    private final ConcurrentHashMap<Long, TdApi.Chat> chatCache = new ConcurrentHashMap<>();

    public ConcurrentHashMap<Long, TdApi.Chat> getChatCache() {
        return chatCache;
    }

    private final MutableLiveData<List<DialogItem>> dialogList = new MutableLiveData<>();

    public LiveData<List<DialogItem>> getDialogList() {
        return dialogList;
    }

    public void postDialogList(List<DialogItem> list) {
        dialogList.setValue(list);
    }

    private final MutableLiveData<Boolean> dialogsLoading = new MutableLiveData<>(true);

    public LiveData<Boolean> getDialogsLoading() {
        return dialogsLoading;
    }

    public void setDialogsLoading(boolean loading) {
        dialogsLoading.setValue(loading);
    }

    private final MutableLiveData<Boolean> foldersAvailable = new MutableLiveData<>(false);

    public LiveData<Boolean> getFoldersAvailable() {
        return foldersAvailable;
    }

    public void setFoldersAvailable(boolean value) {
        if (!Boolean.valueOf(value).equals(foldersAvailable.getValue())) {
            foldersAvailable.setValue(value);
        }
    }
}
