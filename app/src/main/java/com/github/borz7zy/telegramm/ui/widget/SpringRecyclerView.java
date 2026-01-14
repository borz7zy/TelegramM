package com.github.borz7zy.telegramm.ui.widget;

import static androidx.recyclerview.widget.RecyclerView.EdgeEffectFactory.DIRECTION_BOTTOM;
import static androidx.recyclerview.widget.RecyclerView.EdgeEffectFactory.DIRECTION_RIGHT;
import static androidx.recyclerview.widget.RecyclerView.EdgeEffectFactory.DIRECTION_TOP;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.EdgeEffect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.RecyclerView;

public class SpringRecyclerView extends RecyclerView {

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

    private void init() {
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
        if (springAnimY != null) springAnimY.cancel();
        if (springAnimX != null) springAnimX.cancel();
        setTranslationY(0f);
        setTranslationX(0f);
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
            active = true;

            SpringAnimation anim = vertical ? springAnimY : springAnimX;
            if (anim.isRunning()) anim.cancel();

            int size = vertical ? getHeight() : getWidth();
            if (size <= 0) return;

            float deltaPx = sign * deltaDistance * size * PULL_FACTOR;

            float max = size * MAX_OVERSCROLL_FRACTION;
            if (vertical) {
                float next = clamp(getTranslationY() + deltaPx, -max, max);
                setTranslationY(next);
            } else {
                float next = clamp(getTranslationX() + deltaPx, -max, max);
                setTranslationX(next);
            }
        }

        @Override
        public void onRelease() {
            active = false;
            startSpringBackIfNeeded();
        }

        @Override
        public void onAbsorb(int velocity) {
            active = true;

            SpringAnimation anim = vertical ? springAnimY : springAnimX;
            anim.cancel();

            float v = sign * velocity * ABSORB_VELOCITY_FACTOR;
            anim.setStartVelocity(v);

            if (vertical && Math.abs(getTranslationY()) <= EPSILON_PX) setTranslationY(sign * 1f);
            if (!vertical && Math.abs(getTranslationX()) <= EPSILON_PX) setTranslationX(sign * 1f);

            anim.start();
        }

        private void startSpringBackIfNeeded() {
            if (vertical) {
                if (Math.abs(getTranslationY()) > EPSILON_PX) {
                    springAnimY.start();
                } else {
                    setTranslationY(0f);
                }
            } else {
                if (Math.abs(getTranslationX()) > EPSILON_PX) {
                    springAnimX.start();
                } else {
                    setTranslationX(0f);
                }
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
            if (springAnimY != null) springAnimY.cancel();
            if (springAnimX != null) springAnimX.cancel();
            setTranslationY(0f);
            setTranslationX(0f);
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
