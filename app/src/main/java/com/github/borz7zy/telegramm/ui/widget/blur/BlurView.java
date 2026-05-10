package com.github.borz7zy.telegramm.ui.widget.blur;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class BlurView extends FrameLayout {

    @Nullable private View source;
    private float blurRadius = 24f;
    private final RenderNode renderNode = new RenderNode("BlurView");
    @Nullable private RenderEffect cachedEffect;
    private float cachedEffectRadius = -1f;

    private boolean capturing;

    private final ViewTreeObserver.OnPreDrawListener preDrawListener = () -> {
        if (source != null && getVisibility() == VISIBLE) invalidate();
        return true;
    };

    public BlurView(@NonNull Context context) {
        super(context);
        init();
    }

    public BlurView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BlurView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
    }

    public BlurView setupWith(@Nullable View source) {
        this.source = source;
        return this;
    }

    public BlurView setFrameClearDrawable(@Nullable Drawable ignored) {
        return this;
    }

    public BlurView setBlurRadius(float radius) {
        this.blurRadius = Math.max(0.1f, radius);
        invalidate();
        return this;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(preDrawListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
        renderNode.discardDisplayList();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (capturing) {
            return;
        }
        if (source != null && getWidth() > 0 && getHeight() > 0) {
            captureSource();
            canvas.drawRenderNode(renderNode);
        }
        super.draw(canvas);
    }

    private void captureSource() {
        renderNode.setPosition(0, 0, getWidth(), getHeight());
        Canvas rc = renderNode.beginRecording();
        capturing = true;
        try {
            int[] selfXY = new int[2];
            int[] sourceXY = new int[2];
            getLocationInWindow(selfXY);
            source.getLocationInWindow(sourceXY);
            rc.translate(sourceXY[0] - selfXY[0], sourceXY[1] - selfXY[1]);
            source.draw(rc);
        } finally {
            capturing = false;
            renderNode.endRecording();
        }
        if (cachedEffect == null || cachedEffectRadius != blurRadius) {
            cachedEffect = RenderEffect.createBlurEffect(
                    blurRadius, blurRadius, Shader.TileMode.CLAMP);
            cachedEffectRadius = blurRadius;
        }
        renderNode.setRenderEffect(cachedEffect);
    }
}