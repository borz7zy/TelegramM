package com.github.borz7zy.telegramm.utils;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

public class RoundedOutlineProvider extends ViewOutlineProvider {
    private final int radius;

    public RoundedOutlineProvider(int r){
        radius = r;
    }

    @Override
    public void getOutline(View view, Outline outline) {
        outline.setRoundRect(
                0,
                0,
                view.getWidth(),
                view.getHeight(),
                radius);
    }
}
