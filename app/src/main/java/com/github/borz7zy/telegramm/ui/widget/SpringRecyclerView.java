package com.github.borz7zy.telegramm.ui.widget;

import static androidx.recyclerview.widget.RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM;
import static androidx.recyclerview.widget.RecyclerView.EdgeEffectFactory.DIRECTION_RIGHT;
import static androidx.recyclerview.widget.RecyclerView.EdgeEffectFactory.DIRECTION_TOP;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.EdgeEffect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.RecyclerView;

public class SpringRecyclerView extends RecyclerView {

    private final Sonic sonic = new Sonic();

    private static final float PULL_FACTOR = 0.35f;
    private static final float ABSORB_VELOCITY_FACTOR = 0.20f;
    private static final float MAX_OVERSCROLL_FRACTION = 1.f;
    private static final float EPSILON_PX = 0.5f;

    private SpringAnimation springAnimY;
    private SpringAnimation springAnimX;

    public SpringRecyclerView(@NonNull Context context) {
        super(context);
        init();
    }

    public SpringRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpringRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @Override
    public boolean fling(int velocityX, int velocityY) {
        velocityY = sonic.applyBoost(this, velocityY);
        return super.fling(velocityX, velocityY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                boolean isFlying = getScrollState() == SCROLL_STATE_SETTLING;
                sonic.setWasSettling(isFlying);

                stopScroll();
                // cancel() is a no-op when the animation isn't running, so we
                // skip the explicit isRunning() check.
                springAnimY.cancel();
                springAnimX.cancel();
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                // If a spring was interrupted by the user touching the screen
                // mid-bounce and they released without pulling, the platform
                // never calls onRelease() on the EdgeEffect, leaving the view
                // stranded with a non-zero translation. Restart the spring
                // here so the view always settles back to 0.
                if (Math.abs(getTranslationY()) > EPSILON_PX && !springAnimY.isRunning()) {
                    springAnimY.start();
                }
                if (Math.abs(getTranslationX()) > EPSILON_PX && !springAnimX.isRunning()) {
                    springAnimX.start();
                }
                break;
            }
            default:
                break;
        }
        return super.onTouchEvent(e);
    }

    private void init() {
        int maxVelocity = ViewConfiguration.get(getContext()).getScaledMaximumFlingVelocity();
        sonic.setMaxFlingVelocity(maxVelocity);

        springAnimY = new SpringAnimation(this, SpringAnimation.TRANSLATION_Y, 0f);
        springAnimY.setSpring(new SpringForce(0f)
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY));

        springAnimX = new SpringAnimation(this, SpringAnimation.TRANSLATION_X, 0f);
        springAnimX.setSpring(new SpringForce(0f)
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY));

        setEdgeEffectFactory(new EdgeEffectFactory() {
            @NonNull
            @Override
            protected EdgeEffect createEdgeEffect(@NonNull RecyclerView view, int direction) {
                return new SpringEdgeEffect(view.getContext(), direction);
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        springAnimY.cancel();
        springAnimX.cancel();
        setTranslationY(0f);
        setTranslationX(0f);
        // Boost streak shouldn't survive a detach/reattach cycle (e.g. the
        // RV being moved between containers).
        sonic.reset();
    }

    private final class SpringEdgeEffect extends EdgeEffect {

        private final boolean vertical;
        private final int sign;
        private boolean active = false;

        SpringEdgeEffect(Context context, int direction) {
            super(context);
            this.vertical = (direction == DIRECTION_TOP || direction == DIRECTION_BOTTOM);
            this.sign = (direction == DIRECTION_BOTTOM || direction == DIRECTION_RIGHT) ? -1 : 1;
        }

        @Override
        public void onPull(float deltaDistance) {
            handlePull(deltaDistance);
        }

        @Override
        public void onPull(float deltaDistance, float displacement) {
            handlePull(deltaDistance);
        }

        @Override
        public float onPullDistance(float deltaDistance, float displacement) {
            handlePull(deltaDistance);
            return deltaDistance;
        }

        private void handlePull(float deltaDistance) {
            int size = vertical ? getHeight() : getWidth();
            if (size <= 0) return;

            active = true;

            SpringAnimation anim = vertical ? springAnimY : springAnimX;
            anim.cancel();

            float deltaPx = sign * deltaDistance * size * PULL_FACTOR;
            float max = size * MAX_OVERSCROLL_FRACTION;
            if (vertical) {
                setTranslationY(clamp(getTranslationY() + deltaPx, -max, max));
            } else {
                setTranslationX(clamp(getTranslationX() + deltaPx, -max, max));
            }
        }

        @Override
        public void onRelease() {
            active = false;
            startSpringBackIfNeeded();
        }

        @Override
        public void onAbsorb(int velocity) {
            // NOTE: do NOT set active=true here. `active` tracks user pulls
            // (set in handlePull, cleared in onRelease). onAbsorb is a system
            // event triggered by a fling hitting the edge — leaving active
            // alone lets isFinished() correctly observe the spring lifecycle.
            SpringAnimation anim = vertical ? springAnimY : springAnimX;
            anim.cancel();

            float v = sign * velocity * ABSORB_VELOCITY_FACTOR;
            anim.setStartVelocity(v);

            // Nudge the view off the rest position so the spring has somewhere
            // to bounce from. 1px is sub-pixel on hidpi, invisible.
            if (vertical && Math.abs(getTranslationY()) <= EPSILON_PX) setTranslationY(sign * 1f);
            if (!vertical && Math.abs(getTranslationX()) <= EPSILON_PX) setTranslationX(sign * 1f);

            anim.start();
        }

        private void startSpringBackIfNeeded() {
            SpringAnimation anim = vertical ? springAnimY : springAnimX;
            float t = vertical ? getTranslationY() : getTranslationX();
            if (Math.abs(t) > EPSILON_PX) {
                anim.start();
            } else if (vertical) {
                setTranslationY(0f);
            } else {
                setTranslationX(0f);
            }
        }

        @Override
        public boolean draw(Canvas canvas) {
            return false;
        }

        @Override
        public boolean isFinished() {
            float t = vertical ? getTranslationY() : getTranslationX();
            SpringAnimation anim = vertical ? springAnimY : springAnimX;

            boolean atRest = Math.abs(t) <= EPSILON_PX;
            boolean springIdle = !anim.isRunning();

            return !active && atRest && springIdle;
        }

        @Override
        public void finish() {
            active = false;
            // Only touch our axis: the other axis has its own EdgeEffect
            // tracking an unrelated translation.
            if (vertical) {
                springAnimY.cancel();
                setTranslationY(0f);
            } else {
                springAnimX.cancel();
                setTranslationX(0f);
            }
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}