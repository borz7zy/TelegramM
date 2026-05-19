package com.github.borz7zy.rlottie;

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

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

/**
 * Drawable backed by Samsung rlottie. Holds two ARGB_8888 bitmaps (front /
 * back) so rendering can happen on a background thread while the UI thread
 * draws the previously-rendered frame.
 *
 * Two consumption modes:
 *   - {@link #start()} / {@link #stop()} for an Animatable view-driven loop;
 *     a single shared {@link Handler}-based ticker invalidates the drawable
 *     at the source frame rate.
 *   - {@link #setProgress(float)} for callers that drive progress manually
 *     (e.g. spans that derive progress from monotonic time inside draw()).
 */
public class RLottieDrawable extends Drawable implements Animatable {

    /** Loaders + per-frame renders share one background executor. */
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(
            new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger();
                @Override public Thread newThread(@NonNull Runnable r) {
                    Thread t = new Thread(r, "rlottie-render-" + n.incrementAndGet());
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
            });

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Animation loops forever. */
    public static final int INFINITE = -1;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    /** Generation counter — bumped on destroy/replace so stale async tasks bail. */
    private final AtomicInteger generation = new AtomicInteger();

    private long handle;             // native Animation*
    private int totalFrame;
    private double frameRate;
    private int nativeWidth;
    private int nativeHeight;
    private long durationMs;

    private final int renderWidth;
    private final int renderHeight;

    private Bitmap front;            // drawn by Canvas
    private Bitmap back;             // being rendered into
    private int frontFrameNo = -1;
    private int backFrameNo = -1;
    private boolean renderInFlight;
    private int requestedFrameNo;

    private float progress;          // [0..1] — last value driven into the renderer
    private int repeatCount = INFINITE;
    private int loopsCompleted;

    // Auto-driven playback state.
    private boolean isRunning;
    private long animStartMs;
    private final Runnable tick = this::onTick;

    /**
     * Construct a drawable that renders {@code jsonData} at the given size.
     * If {@code renderW/H} are <=0 the native size is used.
     */
    public RLottieDrawable(@NonNull String jsonData, @NonNull String cacheKey,
                           int renderW, int renderH) {
        long h = RLottieNative.nativeCreate(jsonData, cacheKey);
        if (h == 0L) {
            // Construction failed — the drawable is inert and draws nothing.
            this.handle = 0L;
            this.totalFrame = 0;
            this.frameRate = 0;
            this.nativeWidth = 0;
            this.nativeHeight = 0;
            this.durationMs = 0;
            this.renderWidth = Math.max(1, renderW);
            this.renderHeight = Math.max(1, renderH);
            return;
        }

        this.handle = h;
        this.totalFrame = RLottieNative.nativeTotalFrame(h);
        this.frameRate = RLottieNative.nativeFrameRate(h);
        int[] size = new int[2];
        RLottieNative.nativeGetSize(h, size);
        this.nativeWidth = size[0] > 0 ? size[0] : 256;
        this.nativeHeight = size[1] > 0 ? size[1] : 256;
        double dur = RLottieNative.nativeDuration(h);
        this.durationMs = (long) Math.max(1, dur * 1000.0);

        int w = renderW > 0 ? renderW : nativeWidth;
        int h2 = renderH > 0 ? renderH : nativeHeight;
        this.renderWidth = Math.max(1, w);
        this.renderHeight = Math.max(1, h2);

        this.front = Bitmap.createBitmap(this.renderWidth, this.renderHeight,
                                         Bitmap.Config.ARGB_8888);
        this.back  = Bitmap.createBitmap(this.renderWidth, this.renderHeight,
                                         Bitmap.Config.ARGB_8888);
        // Prime frame 0 synchronously so the first draw shows something rather
        // than a blank rect while the background thread spins up.
        renderFrameOnto(this.front, 0);
        frontFrameNo = 0;
    }

    /**
     * Convenience factory: open a .tgs file (gzipped Lottie JSON) and build
     * a drawable. Returns null on failure. Does I/O — call off the main thread.
     */
    @Nullable
    public static RLottieDrawable fromTgsFile(@NonNull String path,
                                              @NonNull String cacheKey,
                                              int renderW, int renderH) {
        try (InputStream is = new GZIPInputStream(new FileInputStream(path))) {
            String json = readAll(is);
            RLottieDrawable d = new RLottieDrawable(json, cacheKey, renderW, renderH);
            if (d.handle == 0L) return null;
            return d;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Convenience factory: open a Lottie JSON stream (already decompressed) and
     * build a drawable. Returns null on failure. Does I/O — call off the main thread.
     */
    @Nullable
    public static RLottieDrawable fromJsonStream(@NonNull InputStream is,
                                                 @NonNull String cacheKey,
                                                 int renderW, int renderH) {
        try {
            String json = readAll(is);
            RLottieDrawable d = new RLottieDrawable(json, cacheKey, renderW, renderH);
            if (d.handle == 0L) return null;
            return d;
        } catch (IOException e) {
            return null;
        } finally {
            try { is.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * Read a .tgs file (gzipped Lottie JSON) into a JSON string. Returns null
     * on I/O error. Must be called off the main thread.
     */
    @Nullable
    public static String readTgsAsJson(@NonNull String path) {
        try (InputStream is = new GZIPInputStream(new FileInputStream(path))) {
            return readAll(is);
        } catch (IOException e) {
            return null;
        }
    }

    private static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = is.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    public boolean isValid() {
        return handle != 0L && totalFrame > 0 && front != null;
    }

    public int getTotalFrame()    { return totalFrame; }
    public double getFrameRate()  { return frameRate; }
    public long getDurationMs()   { return durationMs; }

    /**
     * Drive the animation to the given normalized position. Safe to call from
     * the UI thread; schedules the actual render on a background thread.
     */
    public void setProgress(float p) {
        if (Float.isNaN(p)) return;
        if (p < 0f) p = 0f;
        if (p > 1f) p = 1f;
        if (Math.abs(p - this.progress) < 1e-4f && frontFrameNo >= 0) return;
        this.progress = p;
        int frame = (int) (p * Math.max(0, totalFrame - 1));
        scheduleRender(frame);
    }

    public void setRepeatCount(int count) {
        // Lottie semantics: 0 means "play once", INFINITE means forever.
        this.repeatCount = count;
    }

    @Override
    public void start() {
        if (!isValid()) return;
        if (isRunning) return;
        isRunning = true;
        animStartMs = SystemClock.uptimeMillis();
        loopsCompleted = 0;
        MAIN.removeCallbacks(tick);
        MAIN.post(tick);
    }

    @Override
    public void stop() {
        isRunning = false;
        MAIN.removeCallbacks(tick);
    }

    @Override
    public boolean isRunning() { return isRunning; }

    private void onTick() {
        if (!isRunning || !isValid() || durationMs <= 0) return;

        long now = SystemClock.uptimeMillis();
        long elapsed = now - animStartMs;
        long completed = elapsed / durationMs;
        if (repeatCount != INFINITE && completed > repeatCount) {
            // Settle on the last frame and stop.
            setProgress(1f);
            isRunning = false;
            return;
        }
        loopsCompleted = (int) completed;

        float p = (elapsed % durationMs) / (float) durationMs;
        setProgress(p);

        // Next tick at the source frame rate. 16ms floor so we never busy-loop.
        long intervalMs = frameRate > 0 ? Math.max(16, (long) (1000.0 / frameRate)) : 33;
        MAIN.postDelayed(tick, intervalMs);
    }

    private void scheduleRender(int frameNo) {
        if (handle == 0L || back == null) return;
        if (frameNo == frontFrameNo && !renderInFlight) {
            invalidateSelf();
            return;
        }
        if (renderInFlight) {
            // Coalesce: latest requested wins.
            requestedFrameNo = frameNo;
            return;
        }
        requestedFrameNo = frameNo;
        renderInFlight = true;
        final int gen = generation.get();
        final int targetFrame = frameNo;
        EXEC.execute(() -> {
            if (gen != generation.get()) return;
            Bitmap target = this.back;
            if (target == null || target.isRecycled()) return;
            renderFrameOnto(target, targetFrame);

            MAIN.post(() -> {
                if (gen != generation.get()) return;
                // Swap front/back so the just-rendered frame becomes drawable.
                Bitmap tmp = this.front;
                this.front = target;
                this.back = tmp;
                this.frontFrameNo = targetFrame;
                renderInFlight = false;
                invalidateSelf();

                // If a newer frame was requested while we rendered, chain it.
                if (requestedFrameNo != targetFrame) {
                    int next = requestedFrameNo;
                    scheduleRender(next);
                }
            });
        });
    }

    private void renderFrameOnto(Bitmap target, int frameNo) {
        if (handle == 0L || target == null) return;
        if (frameNo < 0) frameNo = 0;
        if (totalFrame > 0 && frameNo >= totalFrame) frameNo = totalFrame - 1;
        // Clear before rendering — rlottie expects a zero-filled surface for
        // transparent regions.
        target.eraseColor(0);
        RLottieNative.nativeRenderFrame(handle, target, frameNo);
        backFrameNo = frameNo;
    }

    // --- Drawable ---

    @Override
    public void draw(@NonNull Canvas canvas) {
        Bitmap b = front;
        if (b == null || b.isRecycled()) return;
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
    public int getIntrinsicWidth()  { return renderWidth; }

    @Override
    public int getIntrinsicHeight() { return renderHeight; }

    /**
     * Free the native handle and bitmaps. After this the drawable is inert.
     */
    public void release() {
        generation.incrementAndGet();
        stop();
        long h = this.handle;
        this.handle = 0L;
        if (h != 0L) RLottieNative.nativeDestroy(h);
        Bitmap a = this.front;  this.front = null;
        Bitmap b = this.back;   this.back = null;
        if (a != null && !a.isRecycled()) a.recycle();
        if (b != null && !b.isRecycled()) b.recycle();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void finalize() throws Throwable {
        try {
            if (handle != 0L) RLottieNative.nativeDestroy(handle);
        } finally {
            super.finalize();
        }
    }
}