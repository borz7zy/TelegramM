package com.github.borz7zy.ffmpeg;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class VideoStickerDrawable extends Drawable implements Animatable {
    public static final int INFINITE = -1;

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(
            new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger();
                @Override public Thread newThread(@NonNull Runnable r) {
                    Thread t = new Thread(r, "video-sticker-" + n.incrementAndGet());
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
            });

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    private final AtomicInteger generation = new AtomicInteger();

    private long handle;
    private final int nativeWidth;
    private final int nativeHeight;
    private final int renderWidth;
    private final int renderHeight;
    private final long frameIntervalMs;

    private Bitmap front;
    private Bitmap back;
    private boolean hasFrame;

    private int repeatCount = INFINITE;
    private int loopsCompleted;

    private boolean isRunning;
    private boolean decodeInFlight;
    private long lastStepMs;
    private final Runnable tick = this::onTick;

    public VideoStickerDrawable(@NonNull String path, @NonNull String cacheKey,
                                int renderW, int renderH) {
        long h = FFmpegVideoDecoder.nativeOpen(path);
        if (h == 0L) {
            this.handle = 0L;
            this.nativeWidth = 0;
            this.nativeHeight = 0;
            this.renderWidth = Math.max(1, renderW);
            this.renderHeight = Math.max(1, renderH);
            this.frameIntervalMs = 33;
            return;
        }

        this.handle = h;
        int[] info = new int[5];
        FFmpegVideoDecoder.nativeGetInfo(h, info);
        this.nativeWidth = info[0] > 0 ? info[0] : 256;
        this.nativeHeight = info[1] > 0 ? info[1] : 256;
        int fpsNum = info[3] > 0 ? info[3] : 30;
        int fpsDen = info[4] > 0 ? info[4] : 1;
        double fps = (double) fpsNum / (double) fpsDen;
        this.frameIntervalMs = fps > 0 ? Math.max(16, (long) (1000.0 / fps)) : 33;

        int w = renderW > 0 ? renderW : nativeWidth;
        int h2 = renderH > 0 ? renderH : nativeHeight;
        this.renderWidth = Math.max(1, w);
        this.renderHeight = Math.max(1, h2);

        this.front = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);
        this.back = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888);

        int r = FFmpegVideoDecoder.nativeRenderNextFrame(handle, front);
        if (r == 1) {
            FFmpegVideoDecoder.nativeSeekToStart(handle);
            r = FFmpegVideoDecoder.nativeRenderNextFrame(handle, front);
        }
        hasFrame = (r == 0);
    }

    public boolean isValid() {
        return handle != 0L && front != null;
    }

    public void setRepeatCount(int count) {
        this.repeatCount = count;
    }

    @Override
    public void start() {
        if (!isValid()) return;
        if (isRunning) return;
        isRunning = true;
        loopsCompleted = 0;
        MAIN.removeCallbacks(tick);
        MAIN.postDelayed(tick, frameIntervalMs);
    }

    @Override
    public void stop() {
        isRunning = false;
        MAIN.removeCallbacks(tick);
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    private void onTick() {
        if (!isRunning || !isValid()) return;
        scheduleDecode();
        MAIN.postDelayed(tick, frameIntervalMs);
    }

    public void step() {
        if (!isValid()) return;
        long now = SystemClock.uptimeMillis();
        if (lastStepMs != 0L && now - lastStepMs < frameIntervalMs) return;
        lastStepMs = now;
        scheduleDecode();
    }

    private void scheduleDecode() {
        if (handle == 0L || back == null || decodeInFlight) return;
        decodeInFlight = true;
        final int gen = generation.get();
        EXEC.execute(() -> {
            if (gen != generation.get()) return;
            Bitmap target = this.back;
            if (target == null || target.isRecycled()) return;

            int r = FFmpegVideoDecoder.nativeRenderNextFrame(handle, target);
            final boolean wrapped;
            if (r == 1) {
                FFmpegVideoDecoder.nativeSeekToStart(handle);
                r = FFmpegVideoDecoder.nativeRenderNextFrame(handle, target);
                wrapped = true;
            } else {
                wrapped = false;
            }
            final boolean ok = (r == 0);

            MAIN.post(() -> {
                decodeInFlight = false;
                if (gen != generation.get()) return;
                if (!ok) return;

                Bitmap tmp = this.front;
                this.front = target;
                this.back = tmp;
                this.hasFrame = true;

                if (wrapped) {
                    loopsCompleted++;
                    if (repeatCount != INFINITE && loopsCompleted > repeatCount) {
                        isRunning = false;
                        MAIN.removeCallbacks(tick);
                    }
                }
                invalidateSelf();
            });
        });
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Bitmap b = front;
        if (b == null || b.isRecycled() || !hasFrame) return;
        Rect bounds = getBounds();
        canvas.drawBitmap(b, null, bounds, paint);
    }

    @Override
    public void setAlpha(int alpha) { paint.setAlpha(alpha); }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    @Override
    public int getIntrinsicWidth() { return renderWidth; }

    @Override
    public int getIntrinsicHeight() { return renderHeight; }

    public void release() {
        generation.incrementAndGet();
        stop();
        long h = this.handle;
        this.handle = 0L;
        if (h != 0L) FFmpegVideoDecoder.nativeClose(h);
        Bitmap a = this.front; this.front = null;
        Bitmap b = this.back;  this.back = null;
        if (a != null && !a.isRecycled()) a.recycle();
        if (b != null && !b.isRecycled()) b.recycle();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void finalize() throws Throwable {
        try {
            if (handle != 0L) FFmpegVideoDecoder.nativeClose(handle);
        } finally {
            super.finalize();
        }
    }
}
