package com.github.borz7zy.rlottie;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * View backed by Samsung rlottie. Extends {@link AppCompatImageView} so that
 * callers can still load static images (PNG/WEBP) into the same view via
 * Glide/setImageDrawable — when a Lottie composition is installed the view
 * delegates draw to {@link RLottieDrawable}.
 *
 * Mirrors the subset of LottieAnimationView's API the project actually used:
 *   - {@link #setRepeatCount(int)}
 *   - {@link #playAnimation()}
 *   - {@link #pauseAnimation()}
 *   - {@link #cancelAnimation()}
 *   - {@link #setTgsFile(String, String)}
 *   - {@link #setJsonStream(InputStream, String)}
 */
public class RLottieAnimationView extends AppCompatImageView {

    /** Loop forever (mirrors LottieDrawable.INFINITE). */
    public static final int INFINITE = RLottieDrawable.INFINITE;

    private static final ExecutorService IO_EXEC = Executors.newSingleThreadExecutor(
            new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger();
                @Override public Thread newThread(@NonNull Runnable r) {
                    Thread t = new Thread(r, "rlottie-io-" + n.incrementAndGet());
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
            });

    @Nullable private RLottieDrawable lottieDrawable;
    private int repeatCount = INFINITE;
    /** Generation counter so async loads from a previous binding don't install onto a recycled view. */
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private boolean autoPlay = true;

    public RLottieAnimationView(@NonNull Context context) { super(context); }

    public RLottieAnimationView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public RLottieAnimationView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setRepeatCount(int count) {
        this.repeatCount = count;
        if (lottieDrawable != null) lottieDrawable.setRepeatCount(count);
    }

    /**
     * If false, calling setTgsFile / setJsonStream / setLottieDrawable will not
     * auto-start playback. The caller can drive progress manually via
     * {@link #getLottieDrawable()}.{@code setProgress(...)} or call
     * {@link #playAnimation()} later.
     */
    public void setAutoPlay(boolean autoPlay) { this.autoPlay = autoPlay; }

    public void playAnimation() {
        if (lottieDrawable != null) lottieDrawable.start();
    }

    public void pauseAnimation() {
        if (lottieDrawable != null) lottieDrawable.stop();
    }

    public void cancelAnimation() {
        // Bump generation so any in-flight async load is ignored.
        loadGeneration.incrementAndGet();
        if (lottieDrawable != null) {
            lottieDrawable.stop();
            // Detach but don't release; caller may setTgsFile again.
            setImageDrawable(null);
            lottieDrawable.release();
            lottieDrawable = null;
        }
    }

    @Nullable
    public RLottieDrawable getLottieDrawable() { return lottieDrawable; }

    /**
     * Install a drawable directly. Replaces any existing composition.
     */
    public void setLottieDrawable(@NonNull RLottieDrawable d) {
        if (lottieDrawable != null && lottieDrawable != d) {
            lottieDrawable.stop();
            lottieDrawable.release();
        }
        lottieDrawable = d;
        d.setRepeatCount(repeatCount);
        setImageDrawable(d);
        if (autoPlay) d.start();
    }

    /**
     * Load a .tgs file asynchronously and install it on success. Cancels any
     * previously-pending load via the generation counter.
     */
    public void setTgsFile(@NonNull String path, @NonNull String cacheKey) {
        final int gen = loadGeneration.incrementAndGet();
        final int w = getWidth();
        final int h = getHeight();
        IO_EXEC.execute(() -> {
            RLottieDrawable d = RLottieDrawable.fromTgsFile(path, cacheKey, w, h);
            if (d == null) return;
            post(() -> {
                if (gen != loadGeneration.get()) {
                    d.release();
                    return;
                }
                setLottieDrawable(d);
            });
        });
    }

    /**
     * Load a Lottie JSON stream asynchronously and install it on success.
     * Closes the stream when done.
     */
    public void setJsonStream(@NonNull InputStream is, @NonNull String cacheKey) {
        final int gen = loadGeneration.incrementAndGet();
        final int w = getWidth();
        final int h = getHeight();
        IO_EXEC.execute(() -> {
            RLottieDrawable d = RLottieDrawable.fromJsonStream(is, cacheKey, w, h);
            if (d == null) return;
            post(() -> {
                if (gen != loadGeneration.get()) {
                    d.release();
                    return;
                }
                setLottieDrawable(d);
            });
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (lottieDrawable != null) lottieDrawable.stop();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (lottieDrawable != null && autoPlay
                && getVisibility() == View.VISIBLE) {
            lottieDrawable.start();
        }
    }
}