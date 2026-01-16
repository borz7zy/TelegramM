package com.github.borz7zy.telegramm.ui.widget;

import android.os.SystemClock;

import androidx.recyclerview.widget.RecyclerView;

public final class Sonic {
    private static final long BOOST_WINDOW_MS = 400;
    private static final int MIN_VELOCITY = 500;
    private static final float BOOST_STEP = 1.52f;
    private static final float MAX_BOOST = 5.2f;

    private long lastFlingTime = 0;
    private int lastDirection = 0;
    private float boostFactor = 1f;
    private int maxFlingVelocity = 0;
    private boolean wasSettling = false;

    public void setMaxFlingVelocity(int maxVelocity) {
        this.maxFlingVelocity = maxVelocity;
    }

    public void setWasSettling(boolean settling) {
        this.wasSettling = settling;
    }

    public int applyBoost(RecyclerView rv, int velocityY) {
        long now = SystemClock.uptimeMillis();
        int direction = Integer.signum(velocityY);

        if (Math.abs(velocityY) < MIN_VELOCITY || direction == 0) {
            reset();
            return velocityY;
        }

        if (!rv.canScrollVertically(direction)) {
            reset();
            return velocityY;
        }

        boolean timeValid = (now - lastFlingTime < BOOST_WINDOW_MS);
        boolean isBoosting = (direction == lastDirection) && (timeValid || wasSettling);
        if (isBoosting) {
            if (maxFlingVelocity > 0) {
                int halfSpeed = maxFlingVelocity / 2;
                if (Math.abs(velocityY) < halfSpeed) {
                    velocityY = direction * halfSpeed;
                    boostFactor = 1f;
                } else {
                    boostFactor = Math.min(boostFactor * BOOST_STEP, MAX_BOOST);
                    float curvedBoost = 1f + (float) Math.log1p(boostFactor - 1f);
                    velocityY = (int) (velocityY * curvedBoost);
                }
            } else {
                boostFactor = Math.min(boostFactor * BOOST_STEP, MAX_BOOST);
                float curvedBoost = 1f + (float) Math.log1p(boostFactor - 1f);
                velocityY = (int) (velocityY * curvedBoost);
            }
        } else {
            boostFactor = 1f;
        }

        lastFlingTime = now;
        lastDirection = direction;
        wasSettling = false;

        return velocityY;
    }

    public void reset() {
        boostFactor = 1f;
        lastDirection = 0;
        lastFlingTime = 0;
        wasSettling = false;
    }
}
