package com.github.borz7zy.telegramm.ui.widget;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;

public class WavySeekBar extends AppCompatSeekBar {

    private Paint wavePaint;
    private Paint trackPaint;
    private Paint thumbPaint;

    private Path wavePath;

    private float waveAmplitude;
    private float waveFrequency;
    private float barWidth;
    private float barHeight;

    private float wavePhase = 0f;
    private ValueAnimator animator;
    private boolean isAnimating = false;

    public WavySeekBar(@NonNull Context context) {
        super(context);
        init();
    }

    public WavySeekBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WavySeekBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        waveAmplitude = 4f * density;
        waveFrequency = 0.05f;
        barWidth = 4f * density;
        barHeight = 16f * density;

        wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint.setColor(Color.WHITE);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(2f * density);

        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setColor(Color.parseColor("#80FFFFFF"));
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(2f * density);

        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint.setColor(Color.WHITE);
        thumbPaint.setStyle(Paint.Style.FILL);

        wavePath = new Path();

        setBackground(null);
        setThumb(null);

        startWaveAnimation();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        int trackWidth = width - paddingLeft - paddingRight;

        float progressRatio = (float) getProgress() / getMax();
        float thumbX = paddingLeft + (trackWidth * progressRatio);
        float centerY = height / 2f;

        if (thumbX < width - paddingRight) {
            canvas.drawLine(thumbX, centerY, width - paddingRight, centerY, trackPaint);
        }

        if (progressRatio > 0) {
            wavePath.reset();
            wavePath.moveTo(paddingLeft, centerY);

            for (float x = paddingLeft; x <= thumbX; x += 2) {
                float y = (float) (centerY + waveAmplitude * Math.sin((x - paddingLeft) * waveFrequency + wavePhase));
                wavePath.lineTo(x, y);
            }

            canvas.drawPath(wavePath, wavePaint);
        }

        @SuppressLint("DrawAllocation")
        RectF thumbRect = new RectF(
                thumbX - barWidth / 2,
                centerY - barHeight / 2,
                thumbX + barWidth / 2,
                centerY + barHeight / 2
        );
        canvas.drawRoundRect(thumbRect, barWidth / 2, barWidth / 2, thumbPaint);
    }

    public void startWaveAnimation() {
        if (isAnimating) return;
        isAnimating = true;

        animator = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            wavePhase = (float) animation.getAnimatedValue();
            wavePhase = -wavePhase;
            invalidate();
        });
        animator.start();
    }

    public void stopWaveAnimation() {
        if (animator != null) {
            animator.cancel();
        }
        isAnimating = false;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopWaveAnimation();
    }
}