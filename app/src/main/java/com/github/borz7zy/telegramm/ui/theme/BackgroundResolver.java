package com.github.borz7zy.telegramm.ui.theme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.borz7zy.telegramm.ui.ThemeEngine;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import org.drinkless.tdlib.TdApi;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Renders a TDLib {@link TdApi.Background} (or per-chat
 * {@link TdApi.ChatBackground}) as an Android {@link Drawable}.
 *
 * <p>Fill-only backgrounds resolve synchronously; wallpaper/pattern types
 * trigger a download via {@link TdMediaRepository} and the resolver delivers
 * the final drawable through {@link Callback#onResolved}. The first emission
 * is always synchronous (a placeholder fill / cached drawable) so callers can
 * paint immediately while the document arrives.
 *
 * <p>The {@link Result#seedBitmap} is the bitmap (if any) the caller should
 * feed to {@code ThemeEngine.extractSeed} when "color the chat from its
 * wallpaper" mode is desired.
 */
public final class BackgroundResolver {

    public interface Callback {
        void onResolved(@NonNull Result result);
    }

    public static final class Result {
        @NonNull public final Drawable drawable;
        @Nullable public final Bitmap seedBitmap;
        /**
         * Pre-computed Monet seed color. {@code null} on placeholders or when
         * no bitmap was sourced. Computed off the main thread so callers can
         * apply the theme synchronously when the result lands.
         */
        @Nullable public final Integer seedColor;
        /**
         * True when this Result is a synchronous placeholder we emitted while
         * a wallpaper/pattern document is downloading. Callers that want a
         * stable Monet seed can skip recomputing the theme on placeholders and
         * wait for the final emission.
         */
        public final boolean isPlaceholder;

        public Result(@NonNull Drawable drawable, @Nullable Bitmap seedBitmap) {
            this(drawable, seedBitmap, null, false);
        }

        public Result(@NonNull Drawable drawable, @Nullable Bitmap seedBitmap, boolean isPlaceholder) {
            this(drawable, seedBitmap, null, isPlaceholder);
        }

        public Result(@NonNull Drawable drawable,
                      @Nullable Bitmap seedBitmap,
                      @Nullable Integer seedColor,
                      boolean isPlaceholder) {
            this.drawable = drawable;
            this.seedBitmap = seedBitmap;
            this.seedColor = seedColor;
            this.isPlaceholder = isPlaceholder;
        }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Decode + Monet-seed extraction is CPU-bound; do it off the main thread
     * so opening a chat with a wallpaper doesn't ANR. A single-thread executor
     * is enough — at most a handful of resolves run concurrently.
     */
    private static final ExecutorService DECODE_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "BackgroundResolver-decode");
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });

    /**
     * Cached Monet seed per wallpaper file path. Reopening the same chat
     * skips the QuantizerCelebi pass entirely.
     */
    private static final ConcurrentHashMap<String, Integer> SEED_CACHE = new ConcurrentHashMap<>();

    /**
     * Resolve a background. The {@link Callback} may fire twice: once
     * synchronously with a fill-only placeholder, then once more with the
     * fully-rendered drawable when documents download. Callers that don't
     * need the placeholder can ignore the first emission.
     */
    @MainThread
    public void resolve(@NonNull Context ctx,
                        @Nullable TdApi.Background background,
                        int dimmingPercent,
                        boolean isDark,
                        int fallbackColor,
                        @NonNull Callback callback) {
        resolveInternal(ctx, background, dimmingPercent, isDark, fallbackColor, callback, 0);
    }

    private void resolveInternal(@NonNull Context ctx,
                                 @Nullable TdApi.Background background,
                                 int dimmingPercent,
                                 boolean isDark,
                                 int fallbackColor,
                                 @NonNull Callback callback,
                                 int depth) {
        if (background == null) {
            callback.onResolved(new Result(new ColorDrawable(fallbackColor), null));
            return;
        }

        TdApi.BackgroundType type = background.type;
        if (type instanceof TdApi.BackgroundTypeFill) {
            Drawable d = renderFill(((TdApi.BackgroundTypeFill) type).fill, fallbackColor);
            callback.onResolved(new Result(applyDimming(d, dimmingPercent, isDark), null));
            return;
        }

        if (type instanceof TdApi.BackgroundTypeWallpaper) {
            // Show fallback while waiting for the JPEG.
            callback.onResolved(new Result(new ColorDrawable(fallbackColor), null, true));
            downloadDocument(background.document, path -> DECODE_EXECUTOR.execute(() -> {
                Bitmap bmp = decodeBitmap(path);
                if (bmp == null) return;
                Integer seed = computeSeedCached(path, bmp, fallbackColor);
                Drawable bd = new BitmapDrawable(ctx.getResources(), bmp);
                final Drawable finalDrawable = applyDimming(bd, dimmingPercent, isDark);
                mainHandler.post(() ->
                        callback.onResolved(new Result(finalDrawable, bmp, seed, false)));
            }));
            return;
        }

        if (type instanceof TdApi.BackgroundTypePattern) {
            TdApi.BackgroundTypePattern p = (TdApi.BackgroundTypePattern) type;
            Drawable fill = renderFill(p.fill, fallbackColor);
            // Emit fill immediately so the chat isn't blank while the pattern downloads.
            callback.onResolved(new Result(applyDimming(fill, dimmingPercent, isDark), null, true));

            // PNG patterns: alpha mask tinted with the dominant fill color.
            // TGV (gzipped subset of SVG) requires a vector renderer we don't
            // have today, so we fall back to fill-only and log a TODO.
            if (background.document == null) return;
            String mime = background.document.mimeType;
            if (!"image/png".equalsIgnoreCase(mime)) {
                // TODO: implement TGV (application/x-tgwallpattern) rendering.
                return;
            }

            downloadDocument(background.document, path -> DECODE_EXECUTOR.execute(() -> {
                Bitmap mask = decodeBitmap(path);
                if (mask == null) return;
                int patternColor = pickPatternColor(p.fill, p.isInverted);
                Drawable patternDrawable = composePattern(ctx, fill, mask,
                        patternColor, p.intensity, p.isInverted);
                final Drawable withDimming = applyDimming(patternDrawable, dimmingPercent, isDark);
                // Patterns derive their seed from the fill, not the mask
                // (the mask is grayscale alpha, not a chromatic source).
                final Integer seed = patternColor;
                mainHandler.post(() ->
                        callback.onResolved(new Result(withDimming, null, seed, false)));
            }));
            return;
        }

        if (type instanceof TdApi.BackgroundTypeChatTheme) {
            // Resolve the named EmojiChatTheme via ThemeRepository and recurse
            // into ts.background. Bounded recursion guards against malformed
            // themes that would point at another ChatTheme.
            String name = ((TdApi.BackgroundTypeChatTheme) type).themeName;
            TdApi.EmojiChatTheme et = ThemeRepository.get().findThemeByName(name);
            TdApi.ThemeSettings ts = (et == null)
                    ? null
                    : (isDark ? et.darkSettings : et.lightSettings);
            TdApi.Background nested = (ts != null) ? ts.background : null;
            if (nested != null && depth < 2) {
                resolveInternal(ctx, nested, dimmingPercent, isDark, fallbackColor, callback, depth + 1);
                return;
            }
            // Theme list hasn't loaded yet, or recursion would loop — emit a
            // fallback fill so callers still get something to paint.
            callback.onResolved(new Result(new ColorDrawable(fallbackColor), null));
            return;
        }

        // Unknown type: graceful fallback.
        callback.onResolved(new Result(new ColorDrawable(fallbackColor), null));
    }

    // -----------------------------------------------------------------
    //  Fill rendering
    // -----------------------------------------------------------------

    private Drawable renderFill(@Nullable TdApi.BackgroundFill fill, int fallback) {
        if (fill == null) return new ColorDrawable(fallback);

        if (fill instanceof TdApi.BackgroundFillSolid) {
            return new ColorDrawable(opaque(((TdApi.BackgroundFillSolid) fill).color));
        }

        if (fill instanceof TdApi.BackgroundFillGradient) {
            TdApi.BackgroundFillGradient g = (TdApi.BackgroundFillGradient) fill;
            int top = opaque(g.topColor);
            int bottom = opaque(g.bottomColor);
            GradientDrawable gd = new GradientDrawable(
                    orientationFromAngle(g.rotationAngle),
                    new int[]{top, bottom});
            gd.setShape(GradientDrawable.RECTANGLE);
            return gd;
        }

        if (fill instanceof TdApi.BackgroundFillFreeformGradient) {
            return new FreeformGradientDrawable(
                    ((TdApi.BackgroundFillFreeformGradient) fill).colors);
        }

        return new ColorDrawable(fallback);
    }

    private static int pickPatternColor(@NonNull TdApi.BackgroundFill fill, boolean isInverted) {
        // Inverted (dark) patterns: pixels are black, with the fill applied
        // only inside the mask. We keep that semantic by tinting with the
        // fill's dominant color.
        if (fill instanceof TdApi.BackgroundFillSolid) {
            return ((TdApi.BackgroundFillSolid) fill).color;
        }
        if (fill instanceof TdApi.BackgroundFillGradient) {
            TdApi.BackgroundFillGradient g = (TdApi.BackgroundFillGradient) fill;
            // Average top + bottom for the tint.
            return blend(g.topColor, g.bottomColor, 0.5f);
        }
        if (fill instanceof TdApi.BackgroundFillFreeformGradient) {
            int[] cs = ((TdApi.BackgroundFillFreeformGradient) fill).colors;
            if (cs == null || cs.length == 0) return isInverted ? 0xFF000000 : 0xFFFFFFFF;
            long r = 0, g = 0, b = 0;
            for (int c : cs) {
                r += Color.red(c);
                g += Color.green(c);
                b += Color.blue(c);
            }
            int n = cs.length;
            return Color.argb(0xFF, (int) (r / n), (int) (g / n), (int) (b / n));
        }
        return isInverted ? 0xFF000000 : 0xFFFFFFFF;
    }

    /**
     * Compose a pattern over a fill: for normal patterns the mask's alpha
     * acts as the pattern's intensity, tinted by the fill's averaged color
     * (Telegram's reference implementation does the same in spirit).
     */
    private static Drawable composePattern(@NonNull Context ctx,
                                           @NonNull Drawable fill,
                                           @NonNull Bitmap mask,
                                           int tintColor,
                                           int intensity,
                                           boolean isInverted) {
        // Tint the mask. The mask is a grayscale alpha PNG; we want it to
        // appear in `tintColor` over the fill, scaled by `intensity`.
        int alpha = (int) (255 * Math.max(0, Math.min(100, intensity)) / 100f);
        BitmapDrawable patternDrawable = new BitmapDrawable(ctx.getResources(), mask) {
            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }
        };
        patternDrawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        patternDrawable.setColorFilter(new PorterDuffColorFilter(
                Color.argb(alpha,
                        Color.red(tintColor),
                        Color.green(tintColor),
                        Color.blue(tintColor)),
                isInverted ? PorterDuff.Mode.MULTIPLY : PorterDuff.Mode.SRC_IN));

        return new LayerDrawable(new Drawable[]{fill, patternDrawable});
    }

    // -----------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------

    private static GradientDrawable.Orientation orientationFromAngle(int angle) {
        // TDLib: clockwise rotation, 0° == top→bottom flow.
        int normalized = ((angle % 360) + 360) % 360;
        switch (normalized) {
            case 0:   return GradientDrawable.Orientation.TOP_BOTTOM;
            case 45:  return GradientDrawable.Orientation.TL_BR;
            case 90:  return GradientDrawable.Orientation.LEFT_RIGHT;
            case 135: return GradientDrawable.Orientation.BL_TR;
            case 180: return GradientDrawable.Orientation.BOTTOM_TOP;
            case 225: return GradientDrawable.Orientation.BR_TL;
            case 270: return GradientDrawable.Orientation.RIGHT_LEFT;
            case 315: return GradientDrawable.Orientation.TR_BL;
            default:  return GradientDrawable.Orientation.TOP_BOTTOM;
        }
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    private static int blend(int a, int b, float t) {
        int ar = Color.red(a),   ag = Color.green(a),   ab = Color.blue(a);
        int br = Color.red(b),   bg = Color.green(b),   bb = Color.blue(b);
        return Color.argb(0xFF,
                (int) (ar + (br - ar) * t),
                (int) (ag + (bg - ag) * t),
                (int) (ab + (bb - ab) * t));
    }

    private static Drawable applyDimming(@NonNull Drawable base, int dimmingPercent, boolean isDark) {
        if (!isDark || dimmingPercent <= 0) return base;
        int alpha = (int) (255 * Math.min(100, dimmingPercent) / 100f);
        Drawable scrim = new ColorDrawable(Color.argb(alpha, 0, 0, 0));
        return new LayerDrawable(new Drawable[]{base, scrim});
    }

    /**
     * Run {@link ThemeEngine#extractSeed} once per wallpaper and memoize by
     * file path. Telegram delivers the same path for the same wallpaper file,
     * so a cache hit lets us paint the chat without ever touching the
     * Quantizer for that wallpaper again.
     */
    private static Integer computeSeedCached(@Nullable String path,
                                             @NonNull Bitmap bmp,
                                             int fallback) {
        if (TextUtils.isEmpty(path)) return ThemeEngine.extractSeed(bmp, fallback);
        Integer cached = SEED_CACHE.get(path);
        if (cached != null) return cached;
        int seed = ThemeEngine.extractSeed(bmp, fallback);
        SEED_CACHE.put(path, seed);
        return seed;
    }

    @Nullable
    private static Bitmap decodeBitmap(@Nullable String path) {
        if (TextUtils.isEmpty(path)) return null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(path, opts);
        } catch (Throwable t) {
            return null;
        }
    }

    private void downloadDocument(@Nullable TdApi.Document doc,
                                  @NonNull java.util.function.Consumer<String> onPath) {
        if (doc == null || doc.document == null) return;
        int fileId = doc.document.id;
        if (fileId == 0) return;

        String cached = TdMediaRepository.get().getCachedPath(fileId);
        if (!TextUtils.isEmpty(cached)) {
            onPath.accept(cached);
            return;
        }
        TdMediaRepository.get().getPathOrRequest(fileId, p -> {
            if (!TextUtils.isEmpty(p)) onPath.accept(p);
        });
    }

    // -----------------------------------------------------------------
    //  Freeform 4-corner gradient
    // -----------------------------------------------------------------

    /**
     * Telegram's "freeform gradient" is a 4-color blend across the corners.
     * We approximate it with a 64x64 bitmap of bilinear-interpolated colors,
     * scaled up by the drawable. Higher resolutions add cost without making
     * the gradient noticeably crisper since the interpolation is smooth.
     */
    private static final class FreeformGradientDrawable extends Drawable {
        private static final int RES = 64;
        private final int[] corners;
        @NonNull private final Bitmap baked;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Rect bounds = new Rect();

        FreeformGradientDrawable(int[] colors) {
            this.corners = padTo4(colors);
            this.baked = bake(corners);
        }

        private static int[] padTo4(int[] src) {
            if (src == null || src.length == 0) {
                return new int[]{0xFF000000, 0xFF222222, 0xFF444444, 0xFF666666};
            }
            int[] out = new int[4];
            for (int i = 0; i < 4; i++) out[i] = opaque(src[i % src.length]);
            return out;
        }

        private static Bitmap bake(int[] c) {
            int tl = c[0], tr = c[1], br = c[2], bl = c[3];
            Bitmap bmp = Bitmap.createBitmap(RES, RES, Bitmap.Config.ARGB_8888);
            int[] px = new int[RES * RES];
            // Bilinear interpolation across the four corner colors.
            for (int y = 0; y < RES; y++) {
                float ty = y / (float) (RES - 1);
                int rowOffset = y * RES;
                for (int x = 0; x < RES; x++) {
                    float tx = x / (float) (RES - 1);
                    int topX = blend(tl, tr, tx);
                    int botX = blend(bl, br, tx);
                    px[rowOffset + x] = blend(topX, botX, ty);
                }
            }
            bmp.setPixels(px, 0, RES, 0, 0, RES, RES);
            return bmp;
        }

        @Override
        protected void onBoundsChange(Rect b) {
            super.onBoundsChange(b);
            bounds.set(b);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.drawBitmap(baked, null, bounds, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(@Nullable ColorFilter cf) { paint.setColorFilter(cf); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }
}