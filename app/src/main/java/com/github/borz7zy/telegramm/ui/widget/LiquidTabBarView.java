package com.github.borz7zy.telegramm.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

public class LiquidTabBarView extends LinearLayout {

    private static final int ACTIVE_COLOR = 0xFFFFFFFF;
    private static final int INACTIVE_COLOR = 0xFF9E9E9E;
    private static final int CAPSULE_COLOR = 0xFF2196F3;

    private static final float MOVE_STIFFNESS = 400f;
    private static final float MOVE_DAMPING = 0.85f;

    private static final float PRESS_STIFFNESS = 120f;
    private static final float PRESS_DAMPING = 0.7f;

    private static final float JELLY_SENSITIVITY = 0.0004f;
    private static final float MAX_JELLY_STRETCH = 0.30f;

    private final Paint capsulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF capsuleRect = new RectF();

    private SpringAnimation animX;
    private SpringAnimation animScale;
    private SpringAnimation animAlpha;

    private float cursorX = 0f;
    private float currentScale = 1f;
    private float currentAlpha = 1f;
    private float stretchFactorX = 0f;
    private float jellyLag = 0f;

    private int selectedIndex = 0;
    private float tabWidth = 0f;
    private int activePointerId = -1;
    private boolean isTouchingActiveTab = false;

    private OnTabSelectedListener listener;

    private final Path capsulePath = new Path();
    private float capsuleRadius = 0f;
    private int pendingIndex = -1;

    public interface OnTabSelectedListener {
        void onTabSelected(int index);
    }

    public LiquidTabBarView(Context context) {
        super(context);
        init();
    }

    public LiquidTabBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LiquidTabBarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setClipChildren(false);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        capsulePaint.setColor(CAPSULE_COLOR);

        tintPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        setupPhysics();
    }

    private void setupPhysics() {
        animX = new SpringAnimation(new FloatValueHolder(0f));
        SpringForce forceX = new SpringForce();
        forceX.setStiffness(MOVE_STIFFNESS);
        forceX.setDampingRatio(MOVE_DAMPING);
        animX.setSpring(forceX);
        animX.addUpdateListener((animation, value, velocity) -> {
            cursorX = value;
            float density = getResources().getDisplayMetrics().density;
            float raw = Math.abs(velocity / density) * JELLY_SENSITIVITY;
            raw = (float) (1f - Math.exp(-raw * 4f));
            stretchFactorX = Math.min(raw, MAX_JELLY_STRETCH);
            invalidate();
        });
        animX.addEndListener((animation, canceled, value, velocity) -> {
            if (!canceled) {
                animScale.animateToFinalPosition(1f);
                animAlpha.animateToFinalPosition(1f);
            }
        });

        animScale = new SpringAnimation(new FloatValueHolder(1f));
        SpringForce forceScale = new SpringForce();
        forceScale.setStiffness(PRESS_STIFFNESS);
        forceScale.setDampingRatio(PRESS_DAMPING);
        animScale.setSpring(forceScale);

        animAlpha = new SpringAnimation(new FloatValueHolder(1f));
        SpringForce forceAlpha = new SpringForce();
        forceAlpha.setStiffness(120f);
        forceAlpha.setDampingRatio(0.8f);
        animAlpha.setSpring(forceAlpha);
    }

    private void animateScaleForFlight() {
        animScale.cancel();
        animAlpha.cancel();
        animScale.animateToFinalPosition(1.15f);
        animAlpha.animateToFinalPosition(0.75f);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (getChildCount() == 0) return;

        tabWidth = (float) getWidth() / getChildCount();

        if (!animX.isRunning() && activePointerId == -1) {
            float targetX = selectedIndex * tabWidth;
            animX.cancel();
            animX.getSpring().setFinalPosition(targetX);
            cursorX = targetX;
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (tabWidth == 0 || getChildCount() == 0) {
            super.dispatchDraw(canvas);
            return;
        }

        int width = getWidth();
        int height = getHeight();

        int save = canvas.saveLayer(0, 0, width, height, null);

        drawChildrenWithColor(canvas, INACTIVE_COLOR);

        drawCapsule(canvas);

        canvas.save();
        canvas.clipPath(capsulePath);
        drawChildrenWithColor(canvas, ACTIVE_COLOR);
        canvas.restore();

        canvas.restoreToCount(save);
    }

    private void drawChildrenWithColor(Canvas canvas, int color) {
        tintPaint.setColor(color);
        tintPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            canvas.save();
            canvas.translate(child.getLeft(), child.getTop());

            if (child instanceof ViewGroup) {
                drawGroup((ViewGroup) child, canvas, color);
            } else {
                drawSingle(child, canvas, color);
            }

            canvas.restore();
        }
    }

    private void drawGroup(ViewGroup group, Canvas canvas, int color) {
        canvas.save();
        canvas.translate(-group.getScrollX(), -group.getScrollY());

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            canvas.save();
            canvas.translate(child.getLeft(), child.getTop());

            if (!child.getMatrix().isIdentity()) {
                canvas.concat(child.getMatrix());
            }

            if (child instanceof ViewGroup) {
                drawGroup((ViewGroup) child, canvas, color);
            } else {
                drawSingle(child, canvas, color);
            }

            canvas.restore();
        }

        canvas.restore();
    }

    private void drawSingle(View view, Canvas canvas, int color) {
        int save = canvas.saveLayer(0, 0, view.getWidth(), view.getHeight(), null);

        view.draw(canvas);

        tintPaint.setColor(color);
        tintPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawRect(0, 0, view.getWidth(), view.getHeight(), tintPaint);

        canvas.restoreToCount(save);
    }

    private void drawCapsule(Canvas canvas) {
        float centerX = cursorX + (tabWidth / 2f);
        float centerY = getHeight() / 2f;

        float baseW = tabWidth - dpToPx(64);
        float baseH = getHeight() - dpToPx(20);

        jellyLag += (stretchFactorX - jellyLag) * 0.25f;
        float finalScaleX = currentScale + jellyLag;
        float finalScaleY = currentScale - (jellyLag * 0.5f);

        float w = baseW * finalScaleX;
        float h = baseH * finalScaleY;

        float left = centerX - w / 2f;
        float top = centerY - h / 2f;
        float right = centerX + w / 2f;
        float bottom = centerY + h / 2f;

        capsuleRect.set(left, top, right, bottom);
        capsuleRadius = Math.min(w, h) / 2f;

        capsulePath.reset();
        capsulePath.addRoundRect(capsuleRect, capsuleRadius, capsuleRadius, Path.Direction.CW);

        capsulePaint.setAlpha((int) (255 * currentAlpha));
        canvas.drawRoundRect(capsuleRect, capsuleRadius, capsuleRadius, capsulePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        int action = event.getActionMasked();

        switch (action) {

            case MotionEvent.ACTION_DOWN: {
                activePointerId = event.getPointerId(0);

                int touchedIndex = (int) (x / tabWidth);
                touchedIndex = Math.max(0, Math.min(getChildCount() - 1, touchedIndex));

                pendingIndex = touchedIndex;
                isTouchingActiveTab = true;

                animScale.cancel();
                animAlpha.cancel();
                animScale.animateToFinalPosition(1.18f);
                animAlpha.animateToFinalPosition(0.6f);

                if (touchedIndex != selectedIndex) {
                    jellyLag = 0f;
                    stretchFactorX = 0f;
                    animX.animateToFinalPosition(touchedIndex * tabWidth);
                }

                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (activePointerId == -1 || !isTouchingActiveTab) return false;

                float targetX = x - (tabWidth / 2f);
                float maxScroll = getWidth() - tabWidth;
                targetX = Math.max(0f, Math.min(maxScroll, targetX));

                animX.animateToFinalPosition(targetX);

                int newPending = (int) (x / tabWidth);
                newPending = Math.max(0, Math.min(getChildCount() - 1, newPending));
                pendingIndex = newPending;

                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                activePointerId = -1;
                handleActionUp(x);
                return true;
            }
        }

        return super.onTouchEvent(event);
    }

    private void handleActionUp(float upX) {
        int targetIndex = (pendingIndex != -1) ? pendingIndex : (int) (upX / tabWidth);
        targetIndex = Math.max(0, Math.min(getChildCount() - 1, targetIndex));

        int prevSelected = selectedIndex;
        selectedIndex = targetIndex;
        pendingIndex = -1;
        isTouchingActiveTab = false;

        if (prevSelected != selectedIndex) {
            jellyLag = 0f;
            stretchFactorX = 0f;
            animateScaleForFlight();
        } else {
            animScale.animateToFinalPosition(1f);
            animAlpha.animateToFinalPosition(1f);
        }

        animX.animateToFinalPosition(selectedIndex * tabWidth);

        if (prevSelected != selectedIndex && listener != null) {
            listener.onTabSelected(selectedIndex);
        }
    }

    public void setSelectedIndex(int index) {
        setSelectedIndex(index, true);
    }

    public void setSelectedIndex(int index, boolean notify) {
        if (index < 0 || index >= getChildCount()) return;

        int prevIndex = selectedIndex;
        selectedIndex = index;

        float targetX = index * tabWidth;
        animX.animateToFinalPosition(targetX);

        if (notify && prevIndex != index && listener != null) {
            listener.onTabSelected(index);
        }
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}