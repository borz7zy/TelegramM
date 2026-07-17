package com.github.borz7zy.telegramm.ui.emoji;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.borz7zy.ffmpeg.VideoStickerDrawable;
import com.github.borz7zy.rlottie.RLottieDrawable;
import com.github.borz7zy.telegramm.AppManager;
import com.github.borz7zy.telegramm.utils.EmojiStatusRepository;
import com.github.borz7zy.telegramm.utils.Logger;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import org.drinkless.tdlib.TdApi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Inline animated/static custom emoji. The host EmojiTextView drives invalidate
 * ticks while any of these are present in its text — the span itself is
 * stateless w.r.t. animation (progress is derived from monotonic time).
 */
public class CustomEmojiSpan extends ReplacementSpan {

    private static final String TAG = "CustomEmojiSpan";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService DECODE_EXEC =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "custom-emoji-decode");
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });

    /** Decompressed Lottie JSON keyed by document id — avoids re-reading + gunzipping the .tgs. */
    private static final Map<Long, String> JSON_CACHE = new ConcurrentHashMap<>();
    private static final Map<Long, Bitmap> BITMAP_CACHE = new ConcurrentHashMap<>();

    public final long documentId;
    private final int size;
    @Nullable private final Paint.FontMetricsInt fontMetrics;

    @Nullable private RLottieDrawable lottieDrawable;
    @Nullable private VideoStickerDrawable videoDrawable;
    @Nullable private Bitmap bitmap;

    private boolean loadStarted;
    private long startMs;

    public CustomEmojiSpan(long documentId, @Nullable Paint.FontMetricsInt fm) {
        this.documentId = documentId;
        this.fontMetrics = fm;
        if (fm != null) {
            int s = Math.abs(fm.ascent) + Math.abs(fm.descent);
            this.size = s > 0 ? s : dp(20);
        } else {
            this.size = dp(20);
        }
    }

    public boolean isAnimated() {
        return (lottieDrawable != null && lottieDrawable.isValid())
                || (videoDrawable != null && videoDrawable.isValid());
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                       @Nullable Paint.FontMetricsInt fm) {
        if (fm != null && fontMetrics != null) {
            fm.ascent = fontMetrics.ascent;
            fm.descent = fontMetrics.descent;
            fm.top = fontMetrics.top;
            fm.bottom = fontMetrics.bottom;
        }
        return size;
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, @NonNull Paint paint) {
        ensureLoaded();

        int s = size;
        int yc = (top + bottom) / 2;
        int left = (int) x;

        RLottieDrawable d = lottieDrawable;
        if (d != null && d.isValid()) {
            if (startMs == 0L) startMs = SystemClock.uptimeMillis();
            long dur = d.getDurationMs();
            if (dur > 0) {
                long elapsed = SystemClock.uptimeMillis() - startMs;
                float progress = (elapsed % dur) / (float) dur;
                d.setProgress(progress);
            }
            d.setBounds(left, yc - s / 2, left + s, yc + s / 2);
            d.draw(canvas);
            return;
        }

        VideoStickerDrawable vd = videoDrawable;
        if (vd != null && vd.isValid()) {
            vd.step();
            vd.setBounds(left, yc - s / 2, left + s, yc + s / 2);
            vd.draw(canvas);
            return;
        }

        if (bitmap != null) {
            Rect dst = new Rect(left, yc - s / 2, left + s, yc + s / 2);
            canvas.drawBitmap(bitmap, null, dst, paint);
            return;
        }

        // Placeholder: faint dot using the text color until the file resolves.
        Emoji.placeholderPaint.setColor((paint.getColor() & 0x00FFFFFF) | 0x10000000);
        canvas.drawCircle(left + s / 2f, yc, s * 0.4f, Emoji.placeholderPaint);
    }

    private void ensureLoaded() {
        if (loadStarted) return;
        loadStarted = true;

        String cachedJson = JSON_CACHE.get(documentId);
        if (cachedJson != null) {
            final String j = cachedJson;
            DECODE_EXEC.execute(() -> buildAndInstall(j));
            return;
        }
        Bitmap cachedBmp = BITMAP_CACHE.get(documentId);
        if (cachedBmp != null) {
            this.bitmap = cachedBmp;
            return;
        }

        EmojiStatusRepository.get().resolve(documentId, sticker -> {
            if (sticker == null || sticker.sticker == null) return;
            int fileId = sticker.sticker.id;
            if (fileId == 0) return;
            String cached = TdMediaRepository.get().getCachedPath(fileId);
            if (!TextUtils.isEmpty(cached)) {
                decodeFile(sticker, cached);
            } else {
                TdMediaRepository.get().getPathOrRequest(fileId, path -> {
                    if (!TextUtils.isEmpty(path)) decodeFile(sticker, path);
                });
            }
        });
    }

    private void decodeFile(TdApi.Sticker sticker, String path) {
        if (sticker.format instanceof TdApi.StickerFormatTgs) {
            DECODE_EXEC.execute(() -> decodeTgs(path));
        } else if (sticker.format instanceof TdApi.StickerFormatWebm) {
            DECODE_EXEC.execute(() -> decodeWebm(path));
        } else {
            DECODE_EXEC.execute(() -> decodeStatic(path));
        }
    }

    private void decodeTgs(String path) {
        try {
            String json = RLottieDrawable.readTgsAsJson(path);
            if (json == null) return;
            JSON_CACHE.put(documentId, json);
            buildAndInstall(json);
        } catch (Throwable e) {
            Logger.LOGE(TAG, "decodeTgs failed for " + documentId, e);
        }
    }

    /** Construct an RLottieDrawable on the current (background) thread and post the result. */
    private void buildAndInstall(String json) {
        try {
            RLottieDrawable d = new RLottieDrawable(json, "ce:" + documentId, size, size);
            if (!d.isValid()) {
                d.release();
                return;
            }
            MAIN.post(() -> installLottie(d));
        } catch (Throwable e) {
            Logger.LOGE(TAG, "buildRLottie failed for " + documentId, e);
        }
    }

    private void decodeStatic(String path) {
        try {
            Bitmap b = BitmapFactory.decodeFile(path);
            if (b != null) {
                BITMAP_CACHE.put(documentId, b);
                MAIN.post(() -> this.bitmap = b);
            }
        } catch (Throwable e) {
            Logger.LOGE(TAG, "decodeStatic failed for " + documentId, e);
        }
    }

    private void decodeWebm(String path) {
        try {
            VideoStickerDrawable d = new VideoStickerDrawable(path, "ce:" + documentId, size, size);
            if (!d.isValid()) {
                d.release();
                return;
            }
            d.setRepeatCount(VideoStickerDrawable.INFINITE);
            // No start(): frames advance via step() from draw(), so a stale span
            // doesn't keep a ticker (and its native decoder) alive.
            MAIN.post(() -> this.videoDrawable = d);
        } catch (Throwable e) {
            Logger.LOGE(TAG, "decodeWebm failed for " + documentId, e);
        }
    }

    private void installLottie(RLottieDrawable d) {
        // We drive progress manually from draw(); the drawable's own ticker
        // would invalidate the entire host TextView every frame instead of the
        // local span — leave start() unused.
        d.setRepeatCount(RLottieDrawable.INFINITE);
        this.lottieDrawable = d;
    }

    private static int dp(float v) {
        Context ctx = AppManager.getInstance().getContext();
        Resources res = ctx != null ? ctx.getResources() : Resources.getSystem();
        return (int) Math.ceil(v * res.getDisplayMetrics().density);
    }
}