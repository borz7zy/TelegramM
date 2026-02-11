package com.github.borz7zy.telegramm.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    private val _currentTab = MutableLiveData<Int>()
    val currentTab: LiveData<Int> get() = _currentTab

    fun setCurrentTab(id: Int) {
        _currentTab.value = id
    }

    val topInset = MutableLiveData<Int>()
    val bottomInset = MutableLiveData<Int>()
}