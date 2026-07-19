package com.github.borz7zy.telegramm.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.animation.Interpolator;

public class TypingDrawable extends Drawable {

    private static final float DIVIDER = 0.38f;
    private static final float DURATION = 1500.0f;

    private static final Interpolator INTERPOLATOR = new Interpolator() {
        @Override
        public float getInterpolation(float input) {
            return Math.min(1.5f * input, 1.0f);
        }
    };

    private final Bitmap bitmap;
    private final float bitmapRatio;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect drawBounds = new Rect();

    private boolean animating = false;
    private long startTime = 0L;

    public TypingDrawable(Context context, int typingImageResId) {
        BitmapDrawable drawable =
                (BitmapDrawable) context.getResources().getDrawable(typingImageResId);
        this.bitmap = drawable.getBitmap();
        this.bitmapRatio = (float) bitmap.getWidth() / (float) bitmap.getHeight();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);

        float rectRatio = (float) bounds.width() / (float) bounds.height();
        float newWidth;
        float newHeight;

        if (bitmapRatio > rectRatio) {
            float scale = (float) bounds.width() / (float) bitmap.getWidth();
            newWidth = Math.min(bitmap.getWidth() * scale, bitmap.getWidth());
            newHeight = newWidth / bitmapRatio;
        } else {
            float scale = (float) bounds.height() / (float) bitmap.getHeight();
            newHeight = Math.min(bitmap.getHeight() * scale, bitmap.getHeight());
            newWidth = newHeight * bitmapRatio;
        }

        int dx = (int) ((bounds.width() - newWidth) * 0.5f);
        int dy = (int) ((bounds.height() - newHeight) * 0.5f);

        drawBounds.set(
                bounds.left + dx,
                bounds.top + dy,
                bounds.right - dx,
                bounds.bottom - dy
        );

        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        if (!animating) {
            return;
        }

        long now = System.currentTimeMillis();
        float elapsed = (now - startTime) % DURATION;
        float progress = elapsed / DURATION;
        float interpolated = INTERPOLATOR.getInterpolation(progress);

        float dividerX = drawBounds.width() * DIVIDER;
        float dx = dividerX * interpolated;

        int save1 = canvas.save();
        canvas.clipRect(
                drawBounds.left,
                drawBounds.top,
                drawBounds.left + dx,
                drawBounds.bottom
        );
        canvas.drawBitmap(bitmap, null, drawBounds, paint);
        canvas.restoreToCount(save1);

        int save2 = canvas.save();
        canvas.clipRect(
                drawBounds.left + dx,
                drawBounds.top,
                drawBounds.right,
                drawBounds.bottom
        );
        canvas.translate(dx - dividerX, 0f);
        canvas.drawBitmap(bitmap, null, drawBounds, paint);
        canvas.restoreToCount(save2);

        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        // intentionally ignored
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // intentionally ignored
    }

    @Override
    public int getOpacity() {
        return 0;
    }

    public void start() {
        if (!animating) {
            animating = true;
            startTime = System.currentTimeMillis();
            invalidateSelf();
        }
    }

    public void stop() {
        if (animating) {
            animating = false;
            startTime = 0L;
        }
    }
}
