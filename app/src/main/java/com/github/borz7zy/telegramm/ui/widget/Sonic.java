package com.github.borz7zy.telegramm.ui.widget;

import android.os.SystemClock;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Repeated-fling boost: when the user flings in the same direction within
 * {@link #BOOST_WINDOW_MS} of a previous fling (or while the previous fling
 * was still settling), each subsequent fling's velocity is multiplied by a
 * curve that grows with the streak length. The curve is logarithmic so the
 * boost feels increasingly subtle past the first few flings.
 */
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

        // Below the noise floor or against a non-scrollable edge: no boost.
        if (Math.abs(velocityY) < MIN_VELOCITY
                || direction == 0
                || !rv.canScrollVertically(direction)) {
            reset();
            return velocityY;
        }

        boolean timeValid = (now - lastFlingTime < BOOST_WINDOW_MS);
        boolean isBoosting = (direction == lastDirection) && (timeValid || wasSettling);

        if (isBoosting) {
            // First "real" fling of a streak that's slower than half the
            // platform max: bump it to the half-max baseline so the user
            // feels an immediate response, and start the streak fresh.
            int halfSpeed = maxFlingVelocity > 0 ? maxFlingVelocity / 2 : 0;
            if (halfSpeed > 0 && Math.abs(velocityY) < halfSpeed) {
                velocityY = direction * halfSpeed;
                boostFactor = 1f;
            } else {
                velocityY = applyCurvedBoost(velocityY);
            }
        } else {
            boostFactor = 1f;
        }

        lastFlingTime = now;
        lastDirection = direction;
        wasSettling = false;

        return velocityY;
    }

    /**
     * Multiply velocity by the next step of the boost curve, capping the
     * accumulated boost at {@link #MAX_BOOST}. Extracted out of {@link
     * #applyBoost} so the formula isn't duplicated across branches.
     */
    private int applyCurvedBoost(int velocityY) {
        boostFactor = Math.min(boostFactor * BOOST_STEP, MAX_BOOST);
        float curvedBoost = 1f + (float) Math.log1p(boostFactor - 1f);
        return (int) (velocityY * curvedBoost);
    }

    public void reset() {
        boostFactor = 1f;
        lastDirection = 0;
        lastFlingTime = 0;
        wasSettling = false;
    }
}