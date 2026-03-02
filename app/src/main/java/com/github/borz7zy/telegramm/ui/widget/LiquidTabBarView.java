package com.github.borz7zy.telegramm.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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

    private static final float PRESS_STIFFNESS = 180f;
    private static final float PRESS_DAMPING = 0.55f;

    private static final float JELLY_SENSITIVITY = 0.0004f;
    private static final float MAX_JELLY_STRETCH = 0.30f;

    private Paint capsulePaint;
    private Paint layerPaint;
    private RectF capsuleRect;

    private SpringAnimation animX;
    private SpringAnimation animScale;
    private SpringAnimation animAlpha;

    private float cursorX = 0f;
    private float currentScale = 1f;
    private float currentAlpha = 1f;
    private float stretchFactorX = 0f;

    private int selectedIndex = 0;
    private float tabWidth = 0f;
    private int activePointerId = -1;
    private boolean isTouchingActiveTab = false;

    private float jellyLag = 0f;

    private OnTabSelectedListener listener;

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

        capsulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        capsulePaint.setColor(CAPSULE_COLOR);

        layerPaint = new Paint();
        layerPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));

        capsuleRect = new RectF();

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
            float raw = Math.abs(velocity) * JELLY_SENSITIVITY;
            raw = (float) (1f - Math.exp(-raw * 4f));
            stretchFactorX = Math.min(raw, MAX_JELLY_STRETCH);
            invalidate();
        });

        animScale = new SpringAnimation(new FloatValueHolder(1f));
        SpringForce forceScale = new SpringForce();
        forceScale.setStiffness(PRESS_STIFFNESS);
        forceScale.setDampingRatio(PRESS_DAMPING);
        animScale.setSpring(forceScale);

        animScale.addUpdateListener((anim, value, vel) -> {
            currentScale = value;
            invalidate();
        });

        animAlpha = new SpringAnimation(new FloatValueHolder(1f));
        SpringForce forceAlpha = new SpringForce();
        forceAlpha.setStiffness(150f);
        forceAlpha.setDampingRatio(0.9f);
        animAlpha.setSpring(forceAlpha);

        animAlpha.addUpdateListener((anim, value, vel) -> {
            currentAlpha = Math.max(0f, Math.min(1f, value));
            invalidate();
        });
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (getChildCount() > 0) {
            tabWidth = (float) getWidth() / getChildCount();

            if (!animX.isRunning() && activePointerId == -1) {
                cursorX = selectedIndex * tabWidth;
                animX.cancel();
                animX.getSpring().setFinalPosition(cursorX);
                cursorX = selectedIndex * tabWidth;
            }
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
        canvas.clipRect(capsuleRect);

        drawChildrenWithColor(canvas, ACTIVE_COLOR);

        canvas.restore();
        canvas.restoreToCount(save);
    }

    private void drawChildrenWithColor(Canvas canvas, int color) {
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

        int save = canvas.saveLayer(
                0,
                0,
                view.getWidth(),
                view.getHeight(),
                null
        );

        view.draw(canvas);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        canvas.drawRect(
                0,
                0,
                view.getWidth(),
                view.getHeight(),
                paint
        );

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

        int alphaInt = (int) (255 * currentAlpha);
        capsulePaint.setAlpha(alphaInt);

        float radius = Math.min(w, h) / 2f;
        canvas.drawRoundRect(capsuleRect, radius, radius, capsulePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                activePointerId = event.getPointerId(0);

                int touchedIndex = (int) (x / tabWidth);
                touchedIndex = Math.max(0, Math.min(getChildCount() - 1, touchedIndex));

                if (touchedIndex == selectedIndex) {
                    isTouchingActiveTab = true;
                    animScale.animateToFinalPosition(1.22f);
                    animAlpha.animateToFinalPosition(0.5f);
                    animScale.setStartVelocity(6f);
                    animAlpha.setStartVelocity(-2f);
                } else {
                    isTouchingActiveTab = false;
                    animScale.animateToFinalPosition(0.9f);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (activePointerId == -1) return false;

                if (isTouchingActiveTab) {
                    float targetX = x - (tabWidth / 2f);

                    float maxScroll = getWidth() - tabWidth;
                    targetX = Math.max(0f, Math.min(maxScroll, targetX));

                    animX.animateToFinalPosition(targetX);
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePointerId = -1;
                handleActionUp(event.getX());
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleActionUp(float upX) {
        animScale.animateToFinalPosition(1f);
        animAlpha.animateToFinalPosition(1f);

        int targetIndex;

        if (isTouchingActiveTab) {
            float currentCenter = cursorX + (tabWidth / 2f);
            targetIndex = (int) (currentCenter / tabWidth);
        } else {
            targetIndex = (int) (upX / tabWidth);
        }

        targetIndex = Math.max(0, Math.min(getChildCount() - 1, targetIndex));
        setSelectedIndex(targetIndex);

        isTouchingActiveTab = false;
    }

    public void setSelectedIndex(int index) {
        if (index < 0 || index >= getChildCount()) return;

        int prevIndex = selectedIndex;
        selectedIndex = index;

        float targetX = index * tabWidth;
        animX.animateToFinalPosition(targetX);

        if (prevIndex != index && listener != null) {
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