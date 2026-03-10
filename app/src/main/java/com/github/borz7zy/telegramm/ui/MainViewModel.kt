package com.github.borz7zy.telegramm.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.github.borz7zy.telegramm.R
import com.github.borz7zy.telegramm.ui.model.DialogItem
import java.util.concurrent.ConcurrentHashMap

class MainViewModel : ViewModel() {
    private val _currentTab = MutableLiveData<Int>(R.id.nav_chats)
    val currentTab: LiveData<Int> get() = _currentTab

    fun setCurrentTab(id: Int) {
        _currentTab.value = id
    }

    private val _topInset = MutableLiveData<Int>()
    val topInset: LiveData<Int> get() = _topInset
    fun setTopInset(value: Int) { _topInset.value = value }

    private val _bottomInset = MutableLiveData<Int>()
    val bottomInset: LiveData<Int> get() = _bottomInset
    fun setBottomInset(value: Int) { _bottomInset.value = value}

    val dialogs = ConcurrentHashMap<Long, DialogItem>()

    private val _dialogList = MutableLiveData<List<DialogItem>>()
    val dialogList: LiveData<List<DialogItem>> get() = _dialogList

    fun postDialogList(list: List<DialogItem>) {
        _dialogList.value = list
    }
}