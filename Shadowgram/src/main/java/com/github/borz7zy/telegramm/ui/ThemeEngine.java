package com.github.borz7zy.telegramm.ui;

import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.ux.material.libmonet.dynamiccolor.MaterialDynamicColors;
import com.google.ux.material.libmonet.hct.Hct;
import com.google.ux.material.libmonet.quantize.QuantizerCelebi;
import com.google.ux.material.libmonet.scheme.SchemeTonalSpot;
import com.google.ux.material.libmonet.score.Score;

import java.util.List;
import java.util.Map;

/**
 * Holds the global Material You-derived color palette. Per-chat themes are
 * derived through the same {@link Theme#forSeed} / {@link Theme#forBitmap}
 * factories without going through this singleton.
 */
public class ThemeEngine {

    private static final int FALLBACK_SEED = 0xFF4285F4;

    private final MutableLiveData<Theme> currentTheme = new MutableLiveData<>();

    public LiveData<Theme> getCurrentTheme() {
        return currentTheme;
    }

    public void initTheme(@ColorInt int seedColor, boolean isNightMode) {
        updateTheme(seedColor, isNightMode);
    }

    public void updateTheme(@ColorInt int seedColor, boolean isNightMode) {
        // Thread-agnostic: callers may originate from the DB executor /
        // background work, so we always post.
        currentTheme.postValue(Theme.forSeed(seedColor, isNightMode));
    }

    public static class Theme {
        // Material You roles (a curated subset that the UI actually uses).
        public final int primaryColor;
        public final int onPrimaryColor;
        public final int primaryContainerColor;
        public final int onPrimaryContainerColor;

        public final int secondaryColor;
        public final int onSecondaryColor;
        public final int secondaryContainerColor;
        public final int onSecondaryContainerColor;

        public final int tertiaryColor;
        public final int onTertiaryColor;

        public final int surfaceColor;
        public final int onSurfaceColor;
        public final int surfaceVariantColor;
        public final int onSurfaceVariantColor;
        public final int surfaceContainerColor;
        public final int surfaceContainerHighColor;

        public final int outlineColor;
        public final int outlineVariantColor;
        public final int inversePrimaryColor;

        public final int backgroundColor;
        public final int onBackgroundColor;

        // Source data so callers can re-derive variants.
        public final int seedColor;
        public final boolean isDark;

        private Theme(int seedColor, boolean isDark, MaterialDynamicColors c, SchemeTonalSpot s) {
            this.seedColor = seedColor;
            this.isDark = isDark;

            this.primaryColor = c.primary().getArgb(s);
            this.onPrimaryColor = c.onPrimary().getArgb(s);
            this.primaryContainerColor = c.primaryContainer().getArgb(s);
            this.onPrimaryContainerColor = c.onPrimaryContainer().getArgb(s);

            this.secondaryColor = c.secondary().getArgb(s);
            this.onSecondaryColor = c.onSecondary().getArgb(s);
            this.secondaryContainerColor = c.secondaryContainer().getArgb(s);
            this.onSecondaryContainerColor = c.onSecondaryContainer().getArgb(s);

            this.tertiaryColor = c.tertiary().getArgb(s);
            this.onTertiaryColor = c.onTertiary().getArgb(s);

            this.surfaceColor = c.surface().getArgb(s);
            this.onSurfaceColor = c.onSurface().getArgb(s);
            this.surfaceVariantColor = c.surfaceVariant().getArgb(s);
            this.onSurfaceVariantColor = c.onSurfaceVariant().getArgb(s);
            this.surfaceContainerColor = c.surfaceContainer().getArgb(s);
            this.surfaceContainerHighColor = c.surfaceContainerHigh().getArgb(s);

            this.outlineColor = c.outline().getArgb(s);
            this.outlineVariantColor = c.outlineVariant().getArgb(s);
            this.inversePrimaryColor = c.inversePrimary().getArgb(s);

            this.backgroundColor = c.background().getArgb(s);
            this.onBackgroundColor = c.onBackground().getArgb(s);
        }

        @NonNull
        public static Theme forSeed(@ColorInt int seedColor, boolean isDark) {
            Hct sourceHct = Hct.fromInt(seedColor);
            SchemeTonalSpot scheme = new SchemeTonalSpot(sourceHct, isDark, 0.0);
            return new Theme(seedColor, isDark, new MaterialDynamicColors(), scheme);
        }

        /**
         * Build a theme where the seed is the dominant, UI-suitable color of
         * {@code bitmap}. Falls back to {@code fallbackSeed} when the bitmap
         * yields nothing usable (uniform / monochrome pictures).
         */
        @NonNull
        public static Theme forBitmap(@Nullable Bitmap bitmap,
                                      boolean isDark,
                                      @ColorInt int fallbackSeed) {
            int seed = (bitmap == null) ? fallbackSeed : extractSeed(bitmap, fallbackSeed);
            return forSeed(seed, isDark);
        }

        @NonNull
        public static Theme forBitmap(@Nullable Bitmap bitmap, boolean isDark) {
            return forBitmap(bitmap, isDark, FALLBACK_SEED);
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

    /**
     * Extract a Material-You-suitable seed color from a bitmap. Down-samples
     * to keep quantization fast (we only need the dominant cluster, not a
     * faithful palette).
     */
    @ColorInt
    public static int extractSeed(@NonNull Bitmap source, @ColorInt int fallbackSeed) {
        // 112x112 keeps QuantizerCelebi well under ~30ms on a mid-tier device,
        // while still producing the same dominant cluster as the full image.
        final int target = 112;
        Bitmap scaled = source;
        boolean recycleScaled = false;
        try {
            int w = source.getWidth();
            int h = source.getHeight();
            if (w <= 0 || h <= 0) return fallbackSeed;
            if (w > target || h > target) {
                float ratio = Math.min((float) target / w, (float) target / h);
                int nw = Math.max(1, Math.round(w * ratio));
                int nh = Math.max(1, Math.round(h * ratio));
                scaled = Bitmap.createScaledBitmap(source, nw, nh, true);
                recycleScaled = (scaled != source);
            }

            int sw = scaled.getWidth();
            int sh = scaled.getHeight();
            int[] pixels = new int[sw * sh];
            scaled.getPixels(pixels, 0, sw, 0, 0, sw, sh);

            Map<Integer, Integer> hist = QuantizerCelebi.quantize(pixels, 128);
            List<Integer> ranked = Score.score(hist, 1, fallbackSeed);
            if (ranked.isEmpty()) return fallbackSeed;
            return ranked.get(0);
        } catch (Throwable t) {
            return fallbackSeed;
        } finally {
            if (recycleScaled && !scaled.isRecycled()) {
                scaled.recycle();
            }
        }
    }
}