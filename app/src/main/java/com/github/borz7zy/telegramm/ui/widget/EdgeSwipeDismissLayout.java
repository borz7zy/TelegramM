package com.github.borz7zy.telegramm.ui.widget;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;

public class EdgeSwipeDismissLayout extends FrameLayout {

    public interface DismissListener { void onDismiss(); }

    private @Nullable View sheet;
    private @Nullable View scrim;
    private @Nullable View behind;
    private @Nullable DismissListener dismissListener;

    private int edgePx;
    private final int slop;

    private final float dismissByDistance = 0.33f;
    private final float dismissVelocity = 1500f;

    private float behindMinScale = 0.95f;
    private float behindMinAlpha = 0.80f;

    private float downX, downY;
    private boolean tracking;
    private @Nullable VelocityTracker vt;

    private int lastDir = 1;
    private static final float SCRIM_MAX = 1.f;


    public EdgeSwipeDismissLayout(@NonNull Context context) {
        this(context, null);
    }

    public EdgeSwipeDismissLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setEdgeWidthDp(24);
    }

    public void setTargets(@NonNull View sheet, @Nullable View scrim) {
        this.sheet = sheet;
        this.scrim = scrim;

        if (Build.VERSION.SDK_INT >= 29) {
            post(() -> setSystemGestureExclusionRects(
                    Collections.singletonList(new Rect(0, 0, edgePx, getHeight()))
            ));
        }
    }

    public void setBehindView(@Nullable View behind) {
        this.behind = behind;
    }

    public void setDismissListener(@Nullable DismissListener l) {
        this.dismissListener = l;
    }

    public void setEdgeWidthDp(int dp) {
        edgePx = Math.round(dp * getResources().getDisplayMetrics().density);
    }

    public void setBehindOpenedState() {
        if (behind == null) return;
        behind.setScaleX(behindMinScale);
        behind.setScaleY(behindMinScale);
        behind.setAlpha(behindMinAlpha);
    }

    public void animateBehindToNormal(long dur) {
        if (behind == null) return;
        behind.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(dur).start();
    }

    public void animateBehindToOpened(long dur) {
        if (behind == null) return;
        behind.animate().scaleX(behindMinScale).scaleY(behindMinScale).alpha(behindMinAlpha).setDuration(dur).start();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (sheet == null) return super.onInterceptTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = ev.getX();
                downY = ev.getY();

                tracking = downX <= edgePx;

                if (tracking) {
                    vt = VelocityTracker.obtain();
                    vt.addMovement(ev);
                }
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (!tracking) break;

                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;

                if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
                    cancelTracking();
                    break;
                }

                if (dx > slop && dx > Math.abs(dy) * 1.2f) {
                    return true;
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelTracking();
                break;
        }

        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (sheet == null) return super.onTouchEvent(ev);
        if (!tracking) return false;

        if (vt != null) vt.addMovement(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE: {
                float dx = Math.max(0f, ev.getX() - downX);
                applyProgress(dx);
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float dx = Math.max(0f, ev.getX() - downX);
                float vx = 0f;

                if (vt != null) {
                    vt.computeCurrentVelocity(1000);
                    vx = vt.getXVelocity();
                }

                boolean dismiss = dx > sheet.getWidth() * dismissByDistance || vx > dismissVelocity;
                if (dismiss) animateDismiss();
                else animateBack();

                cancelTracking();
                return true;
            }
        }
        return super.onTouchEvent(ev);
    }

    private void applyProgress(float tx) {
        if (sheet == null) return;

        sheet.setTranslationX(tx);

        float w = Math.max(1f, sheet.getWidth());
        float p = Math.min(1f, tx / w);

        if (scrim != null) scrim.setAlpha(1f - p);

        if (behind != null) {
            float scale = behindMinScale + (1f - behindMinScale) * p;
            float alpha = behindMinAlpha + (1f - behindMinAlpha) * p;
            behind.setScaleX(scale);
            behind.setScaleY(scale);
            behind.setAlpha(alpha);
        }
    }

    public void animateBack() {
        if (sheet == null) return;

        sheet.animate().translationX(0f).setDuration(200).start();
        if (scrim != null) scrim.animate().alpha(1f).setDuration(200).start();
        if (behind != null) animateBehindToOpened(200);
    }

    public void animateDismiss() {
        if (sheet == null) return;
        float end = lastDir * sheet.getWidth();

        sheet.animate()
                .translationX(end)
                .setDuration(200)
                .withEndAction(() -> {
                    if (dismissListener != null) dismissListener.onDismiss();
                })
                .start();

        if (scrim != null) {
            scrim.animate().alpha(0f).setDuration(200).start();
        }
    }

    private void cancelTracking() {
        tracking = false;
        if (vt != null) {
            vt.recycle();
            vt = null;
        }
    }

    public void onPredictiveBackStarted() {
        if (sheet != null) sheet.animate().cancel();
        if (scrim != null) scrim.animate().cancel();
    }

    public void setPredictiveBackProgress(float progress, boolean fromLeft) {
        if (sheet == null) return;

        lastDir = fromLeft ? 1 : -1;
        float w = Math.max(1f, sheet.getWidth());

        float tx = lastDir * (progress * w);
        sheet.setTranslationX(tx);

        if (scrim != null) {
            float k = 1f - Math.min(1f, Math.abs(tx) / w);
            scrim.setAlpha(SCRIM_MAX * k);
        }
    }

    public void onPredictiveBackCancelled() {
        animateBack();
    }
}
