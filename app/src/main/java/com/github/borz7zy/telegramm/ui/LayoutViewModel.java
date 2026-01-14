package com.github.borz7zy.telegramm.ui;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class LayoutViewModel extends ViewModel {
    public final MutableLiveData<Integer> topInset = new MutableLiveData<>(0);
    public final MutableLiveData<Integer> bottomInset = new MutableLiveData<>(0);
}