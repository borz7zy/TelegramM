package com.github.borz7zy.telegramm.ui;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.ux.material.libmonet.dynamiccolor.MaterialDynamicColors;
import com.google.ux.material.libmonet.hct.Hct;
import com.google.ux.material.libmonet.scheme.SchemeFidelity;

public class ThemeEngine {

    private final MutableLiveData<Theme> currentTheme = new MutableLiveData<>();

    public LiveData<Theme> getCurrentTheme() {
        return currentTheme;
    }

    public void initTheme(@ColorInt int seedColor, boolean isNightMode) {
        updateTheme(seedColor, isNightMode);
    }

    public void updateTheme(@ColorInt int seedColor, boolean isNightMode) {
        Theme newTheme = new Theme(seedColor, isNightMode);
        currentTheme.setValue(newTheme);
    }

    public static class Theme {
        public int primaryColor;
        public int onPrimaryColor;
        public int secondaryContainerColor;
        public int onSecondaryContainerColor;
        public int surfaceColor;
        public int onSurfaceColor;

        public Theme(@ColorInt int seedColor, boolean isNightMode) {
            generateColors(seedColor, isNightMode);
        }

        private void generateColors(@ColorInt int seedColor, boolean isNightMode) {
            Hct sourceHct = Hct.fromInt(seedColor);
            MaterialDynamicColors dynamicColors = new MaterialDynamicColors();

            SchemeFidelity scheme = new SchemeFidelity(sourceHct, isNightMode, 0.0);

            primaryColor = dynamicColors.primary().getArgb(scheme);
            onPrimaryColor = dynamicColors.onPrimary().getArgb(scheme);

            secondaryContainerColor = dynamicColors.secondaryContainer().getArgb(scheme);
            onSecondaryContainerColor = dynamicColors.onSecondaryContainer().getArgb(scheme);

            surfaceColor = dynamicColors.surface().getArgb(scheme);
            onSurfaceColor = dynamicColors.onSurface().getArgb(scheme);
        }

        public int darken(int color, float factor) {
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            hsv[2] *= (1 - factor);
            return Color.HSVToColor(hsv);
        }

        public int lighten(int color, float factor) {
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            hsv[2] += (1 - hsv[2]) * factor;
            return Color.HSVToColor(hsv);
        }
    }
}
