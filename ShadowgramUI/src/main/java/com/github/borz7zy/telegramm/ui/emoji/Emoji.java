/*
 * Adapted from Telegram for Android (DrKLO/Telegram), GPL v2+.
 * Original: org.telegram.messenger.Emoji.java
 * Stripped: animated/restricted/compound/recent-history paths, telegram-specific deps.
 */
package com.github.borz7zy.telegramm.ui.emoji;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.SparseIntArray;

import com.github.borz7zy.telegramm.AppManager;
import com.github.borz7zy.telegramm.utils.Logger;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class Emoji {

    private static final String TAG = "Emoji";

    private static final java.util.HashMap<CharSequence, DrawableInfo> rects = new java.util.HashMap<>();
    public static int drawImgSize;
    public static int bigImgSize;
    public static Paint placeholderPaint;
    private static final int[] emojiCounts = new int[]{
            EmojiData.data[0].length, EmojiData.data[1].length, EmojiData.data[2].length, EmojiData.data[3].length,
            EmojiData.data[4].length, EmojiData.data[5].length, EmojiData.data[6].length, EmojiData.data[7].length
    };
    private static final Bitmap[][] emojiBmp = new Bitmap[8][];
    private static final boolean[][] loadingEmoji = new boolean[8][];

    public static float emojiDrawingYOffset;
    public static boolean emojiDrawingUseAlpha = true;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Executor LOAD_EXEC = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "emoji-loader");
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    });

    private static final CopyOnWriteArrayList<WeakReference<Runnable>> invalidateCallbacks = new CopyOnWriteArrayList<>();

    private static final Runnable invalidateAllRunnable = () -> {
        Iterator<WeakReference<Runnable>> it = invalidateCallbacks.iterator();
        while (it.hasNext()) {
            Runnable r = it.next().get();
            if (r != null) r.run();
        }
    };

    public static void registerInvalidator(Runnable r) {
        invalidateCallbacks.add(new WeakReference<>(r));
    }

    public static void unregisterInvalidator(Runnable r) {
        for (WeakReference<Runnable> ref : invalidateCallbacks) {
            Runnable existing = ref.get();
            if (existing == null || existing == r) {
                invalidateCallbacks.remove(ref);
            }
        }
    }

    private static int dp(float v) {
        Context ctx = AppManager.getInstance().getContext();
        Resources res = ctx != null ? ctx.getResources() : Resources.getSystem();
        return (int) Math.ceil(v * res.getDisplayMetrics().density);
    }

    static {
        drawImgSize = dp(20);
        bigImgSize = dp(34);
        for (int a = 0; a < emojiBmp.length; a++) {
            emojiBmp[a] = new Bitmap[emojiCounts[a]];
            loadingEmoji[a] = new boolean[emojiCounts[a]];
        }
        for (int j = 0; j < EmojiData.data.length; j++) {
            for (int i = 0; i < EmojiData.data[j].length; i++) {
                rects.put(EmojiData.data[j][i], new DrawableInfo((byte) j, (short) i, i));
            }
        }
        placeholderPaint = new Paint();
        placeholderPaint.setColor(0x00000000);
    }

    public static void preloadEmoji(CharSequence code) {
        DrawableInfo info = getDrawableInfo(code);
        if (info != null) loadEmoji(info.page, info.page2);
    }

    private static SparseIntArray emojiAlphaMasks;
    private static volatile boolean alphaMasksLoadFailed;

    private static SparseIntArray loadEmojiAlphaMasks() {
        if (alphaMasksLoadFailed) return null;
        Context ctx = AppManager.getInstance().getContext();
        if (ctx == null) return null;
        try (InputStream is = ctx.getAssets().open("emoji/metadata.bin")) {
            ArrayList<byte[]> chunks = new ArrayList<>();
            int total = 0;
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) {
                byte[] copy = new byte[read];
                System.arraycopy(buf, 0, copy, 0, read);
                chunks.add(copy);
                total += read;
            }
            byte[] all = new byte[total];
            int pos = 0;
            for (byte[] c : chunks) {
                System.arraycopy(c, 0, all, pos, c.length);
                pos += c.length;
            }
            ByteBuffer bb = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
            int pairs = total / 4;
            SparseIntArray map = new SparseIntArray(pairs);
            for (int i = 0; i < pairs; i++) {
                int emojiIndex = bb.getShort() & 0xFFFF;
                int maskId = bb.getShort() & 0xFFFF;
                map.put(emojiIndex, maskId);
            }
            return map;
        } catch (Exception e) {
            Logger.LOGE(TAG, "loadEmojiAlphaMasks failed", e);
            alphaMasksLoadFailed = true;
            return null;
        }
    }

    private static void loadEmoji(final byte page, final short page2) {
        if (emojiBmp[page][page2] != null) return;
        synchronized (loadingEmoji) {
            if (loadingEmoji[page][page2]) return;
            loadingEmoji[page][page2] = true;
        }
        LOAD_EXEC.execute(() -> {
            Bitmap bitmap = loadBitmap("emoji/" + String.format(Locale.US, "%d_%d.png", page, page2));
            try {
                if (emojiAlphaMasks == null && !alphaMasksLoadFailed) {
                    emojiAlphaMasks = loadEmojiAlphaMasks();
                }
                int maskIndex = -1;
                if (emojiAlphaMasks != null) {
                    maskIndex = emojiAlphaMasks.get(page * 4096 + page2, -1);
                }
                if (bitmap != null && maskIndex != -1) {
                    Bitmap alphaBitmap = loadBitmap("emoji/masks/" + String.format(Locale.US, "%d.png", maskIndex));
                    if (alphaBitmap != null) {
                        int w = bitmap.getWidth();
                        int h = bitmap.getHeight();
                        int[] rgbPixels = new int[w * h];
                        int[] alphaPixels = new int[w * h];
                        bitmap.getPixels(rgbPixels, 0, w, 0, 0, w, h);
                        alphaBitmap.getPixels(alphaPixels, 0, w, 0, 0, w, h);
                        alphaBitmap.recycle();
                        for (int i = 0; i < rgbPixels.length; i++) {
                            int c = rgbPixels[i];
                            c = (c & 0x00FFFFFF) | ((alphaPixels[i] & 0xFF) << 24);
                            rgbPixels[i] = c;
                        }
                        bitmap.recycle();
                        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                        bitmap.setPixels(rgbPixels, 0, w, 0, 0, w, h);
                    }
                }
            } catch (Throwable e) {
                Logger.LOGE(TAG, "load mask failed", e);
            }
            if (bitmap != null) {
                emojiBmp[page][page2] = bitmap;
                MAIN.removeCallbacks(invalidateAllRunnable);
                MAIN.post(invalidateAllRunnable);
            }
            loadingEmoji[page][page2] = false;
        });
    }

    public static Bitmap loadBitmap(String path) {
        try {
            Context ctx = AppManager.getInstance().getContext();
            if (ctx == null) return null;
            int imageResize = (ctx.getResources().getDisplayMetrics().density <= 1.0f) ? 2 : 1;
            try (InputStream is = ctx.getAssets().open(path)) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = imageResize;
                return BitmapFactory.decodeStream(is, null, opts);
            }
        } catch (Throwable x) {
            Logger.LOGE(TAG, "Error loading emoji " + path, x);
            return null;
        }
    }

    public static String fixEmoji(String emoji) {
        char ch;
        int length = emoji.length();
        for (int a = 0; a < length; a++) {
            ch = emoji.charAt(a);
            if (ch >= 0xD83C && ch <= 0xD83E) {
                if (ch == 0xD83C && a < length - 1) {
                    ch = emoji.charAt(a + 1);
                    if (ch == 0xDE2F || ch == 0xDC04 || ch == 0xDE1A || ch == 0xDD7F) {
                        emoji = emoji.substring(0, a + 2) + "️" + emoji.substring(a + 2);
                        length++;
                        a += 2;
                    } else {
                        a++;
                    }
                } else {
                    a++;
                }
            } else if (ch == 0x20E3) {
                return emoji;
            } else if (ch >= 0x203C && ch <= 0x3299) {
                if (EmojiData.emojiToFE0FMap.containsKey(ch)) {
                    emoji = emoji.substring(0, a + 1) + "️" + emoji.substring(a + 1);
                    length++;
                    a++;
                }
            }
        }
        return emoji;
    }

    public static EmojiDrawable getEmojiDrawable(CharSequence code) {
        DrawableInfo info = getDrawableInfo(code);
        if (info == null) return null;
        EmojiDrawable ed = new SimpleEmojiDrawable(info, endsWithRightArrow(code));
        ed.setBounds(0, 0, drawImgSize, drawImgSize);
        return ed;
    }

    public static boolean endsWithRightArrow(CharSequence code) {
        return code != null && code.length() > 2
                && code.charAt(code.length() - 2) == '‍'
                && code.charAt(code.length() - 1) == '➡';
    }

    private static DrawableInfo getDrawableInfo(CharSequence code) {
        if (endsWithRightArrow(code)) code = code.subSequence(0, code.length() - 2);
        DrawableInfo info = rects.get(code);
        if (info == null) {
            CharSequence newCode = EmojiData.emojiAliasMap.get(code);
            if (newCode != null) info = rects.get(newCode);
        }
        return info;
    }

    public static boolean isValidEmoji(CharSequence code) {
        if (TextUtils.isEmpty(code)) return false;
        DrawableInfo info = rects.get(code);
        if (info == null) {
            CharSequence newCode = EmojiData.emojiAliasMap.get(code);
            if (newCode != null) info = rects.get(newCode);
        }
        return info != null;
    }

    public static Drawable getEmojiBigDrawable(String code) {
        EmojiDrawable ed = getEmojiDrawable(code);
        if (ed == null) {
            CharSequence newCode = EmojiData.emojiAliasMap.get(code);
            if (newCode != null) ed = getEmojiDrawable(newCode);
        }
        if (ed == null) return null;
        ed.setBounds(0, 0, bigImgSize, bigImgSize);
        ed.fullSize = true;
        return ed;
    }

    public static abstract class EmojiDrawable extends Drawable {
        public boolean fullSize = false;
        int placeholderColor = 0x10000000;

        public boolean isLoaded() { return false; }
        public void preload() {}
    }

    public static class SimpleEmojiDrawable extends EmojiDrawable {
        private final DrawableInfo info;
        private static final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private static final Rect rect = new Rect();
        private final boolean invert;

        public SimpleEmojiDrawable(DrawableInfo i, boolean invert) {
            this.info = i;
            this.invert = invert;
        }

        public Rect getDrawRect() {
            Rect original = getBounds();
            int cX = original.centerX(), cY = original.centerY();
            int s = (fullSize ? bigImgSize : drawImgSize);
            rect.left = cX - s / 2;
            rect.right = cX + s / 2;
            rect.top = cY - s / 2;
            rect.bottom = cY + s / 2;
            return rect;
        }

        @Override
        public void draw(Canvas canvas) {
            if (!isLoaded()) {
                loadEmoji(info.page, info.page2);
                placeholderPaint.setColor(placeholderColor);
                Rect bounds = getBounds();
                canvas.drawCircle(bounds.centerX(), bounds.centerY(), bounds.width() * .4f, placeholderPaint);
                return;
            }
            Rect b = fullSize ? getDrawRect() : getBounds();
            if (!canvas.quickReject(b.left, b.top, b.right, b.bottom, Canvas.EdgeType.AA)) {
                if (invert) {
                    canvas.save();
                    canvas.scale(-1, 1, b.centerX(), b.centerY());
                }
                canvas.drawBitmap(emojiBmp[info.page][info.page2], null, b, paint);
                if (invert) canvas.restore();
            }
        }

        @Override public int getOpacity() { return PixelFormat.TRANSPARENT; }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) {}

        @Override
        public boolean isLoaded() { return emojiBmp[info.page][info.page2] != null; }

        @Override
        public void preload() { if (!isLoaded()) loadEmoji(info.page, info.page2); }
    }

    private static class DrawableInfo {
        public byte page;
        public short page2;
        public int emojiIndex;

        DrawableInfo(byte p, short p2, int idx) { page = p; page2 = p2; emojiIndex = idx; }
    }

    public static class EmojiSpanRange {
        public int start;
        public int end;
        public CharSequence code;

        public EmojiSpanRange(int start, int end, CharSequence code) {
            this.start = start;
            this.end = end;
            this.code = code;
        }
    }

    public static boolean fullyConsistsOfEmojis(CharSequence cs) {
        int[] only = new int[1];
        parseEmojis(cs, only);
        return only[0] > 0;
    }

    public static int countEmojisOnly(CharSequence cs) {
        int[] only = new int[1];
        parseEmojis(cs, only);
        return only[0];
    }

    public static ArrayList<EmojiSpanRange> parseEmojis(CharSequence cs) {
        return parseEmojis(cs, null);
    }

    public static ArrayList<EmojiSpanRange> parseEmojis(CharSequence cs, int[] emojiOnly) {
        ArrayList<EmojiSpanRange> emojis = new ArrayList<>();
        if (cs == null || cs.length() <= 0) return emojis;
        long buf = 0;
        char c;
        int startIndex = -1;
        int startLength = 0;
        int previousGoodIndex = 0;
        StringBuilder emojiCode = new StringBuilder(16);
        final int length = cs.length();
        boolean doneEmoji = false;
        boolean notOnlyEmoji;
        boolean resetStartIndex = false;
        try {
            for (int i = 0; i < length; i++) {
                c = cs.charAt(i);
                notOnlyEmoji = false;
                if (c >= 0xD83C && c <= 0xD83E || (buf != 0 && (buf & 0xFFFFFFFF00000000L) == 0 && (buf & 0xFFFF) == 0xD83C && (c >= 0xDDE6 && c <= 0xDDFF))) {
                    if (startIndex == -1) startIndex = i;
                    else if (resetStartIndex) {
                        startIndex = i;
                        startLength = 0;
                        resetStartIndex = false;
                    }
                    emojiCode.append(c);
                    startLength++;
                    buf <<= 16;
                    buf |= c;
                } else if (emojiCode.length() > 0 && (c == 0x2640 || c == 0x2642 || c == 0x2695) || buf > 0 && (c & 0xF000) == 0xD000) {
                    emojiCode.append(c);
                    startLength++;
                    buf = 0;
                    doneEmoji = true;
                } else if (c == 0x20E3) {
                    if (i > 0) {
                        char c2 = cs.charAt(previousGoodIndex);
                        if ((c2 >= '0' && c2 <= '9') || c2 == '#' || c2 == '*') {
                            startIndex = previousGoodIndex;
                            startLength = i - previousGoodIndex + 1;
                            emojiCode.append(c2);
                            emojiCode.append(c);
                            doneEmoji = true;
                            resetStartIndex = false;
                        }
                    }
                } else if ((c == 0x00A9 || c == 0x00AE || c >= 0x203C && c <= 0x3299) && EmojiData.dataCharsMap.containsKey(c)) {
                    if (startIndex == -1) startIndex = i;
                    else if (resetStartIndex) {
                        startIndex = i;
                        startLength = 0;
                        resetStartIndex = false;
                    }
                    startLength++;
                    emojiCode.append(c);
                    doneEmoji = true;
                } else if (startIndex != -1) {
                    emojiCode.setLength(0);
                    startIndex = -1;
                    startLength = 0;
                    doneEmoji = false;
                    resetStartIndex = false;
                } else if (c != 0xfe0f && c != '\n' && c != ' ' && c != '\t') {
                    notOnlyEmoji = true;
                }
                if (doneEmoji && i + 2 < length) {
                    char next = cs.charAt(i + 1);
                    if (next == 0xD83C) {
                        next = cs.charAt(i + 2);
                        if (next >= 0xDFFB && next <= 0xDFFF) {
                            emojiCode.append(cs.subSequence(i + 1, i + 3));
                            startLength += 2;
                            i += 2;
                        }
                    } else if (emojiCode.length() >= 2 && emojiCode.charAt(0) == 0xD83C && emojiCode.charAt(1) == 0xDFF4 && next == 0xDB40) {
                        i++;
                        while (true) {
                            if (i < cs.length()) emojiCode.append(cs.charAt(i));
                            if (i + 1 < cs.length()) emojiCode.append(cs.charAt(i + 1));
                            startLength += 2;
                            i += 2;
                            if (i >= cs.length() || cs.charAt(i) != 0xDB40) {
                                i--;
                                break;
                            }
                        }
                    }
                }
                previousGoodIndex = i;
                char prevCh = c;
                for (int a = 0; a < 3; a++) {
                    if (i + 1 < length) {
                        c = cs.charAt(i + 1);
                        if (a == 1) {
                            if (c == 0x200D && emojiCode.length() > 0) {
                                notOnlyEmoji = false;
                                emojiCode.append(c);
                                i++;
                                startLength++;
                                doneEmoji = false;
                            }
                        } else if (prevCh == '*' || prevCh == '#' || prevCh >= '0' && prevCh <= '9') {
                            if (c >= 0xFE00 && c <= 0xFE0F) {
                                startIndex = previousGoodIndex;
                                resetStartIndex = true;
                                i++;
                                startLength++;
                                if (!doneEmoji) doneEmoji = i + 1 >= length;
                            }
                        } else if (startIndex != -1) {
                            if (c >= 0xFE00 && c <= 0xFE0F) {
                                i++;
                                startLength++;
                                if (!doneEmoji) doneEmoji = i + 1 >= length;
                            }
                        }
                    }
                }
                if (notOnlyEmoji && emojiOnly != null) {
                    emojiOnly[0] = 0;
                    emojiOnly = null;
                }
                if (doneEmoji && i + 2 < length && cs.charAt(i + 1) == 0xD83C) {
                    char next = cs.charAt(i + 2);
                    if (next >= 0xDFFB && next <= 0xDFFF) {
                        emojiCode.append(cs.subSequence(i + 1, i + 3));
                        startLength += 2;
                        i += 2;
                    }
                }
                if (doneEmoji) {
                    if (emojiOnly != null) emojiOnly[0]++;
                    if (startIndex >= 0 && startIndex + startLength <= length) {
                        emojis.add(new EmojiSpanRange(startIndex, startIndex + startLength, emojiCode.subSequence(0, emojiCode.length())));
                    }
                    startLength = 0;
                    startIndex = -1;
                    emojiCode.setLength(0);
                    doneEmoji = false;
                    resetStartIndex = false;
                }
            }
        } catch (Exception e) {
            Logger.LOGE(TAG, "parseEmojis", e);
        }
        if (emojiOnly != null && emojiCode.length() != 0) emojiOnly[0] = 0;
        return emojis;
    }

    public static CharSequence replaceEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, boolean createNew) {
        return replaceEmoji(cs, fontMetrics, createNew, null);
    }

    public static CharSequence replaceEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, boolean createNew, int[] emojiOnly) {
        return replaceEmoji(cs, fontMetrics, createNew, emojiOnly, DynamicDrawableSpan.ALIGN_BOTTOM, 1.0f);
    }

    public static CharSequence replaceEmoji(CharSequence cs, Paint.FontMetricsInt fontMetrics, boolean createNew, int[] emojiOnly, int alignment, float scale) {
        if (cs == null || cs.length() == 0) return cs;
        Spannable s;
        if (!createNew && cs instanceof Spannable) s = (Spannable) cs;
        else s = Spannable.Factory.getInstance().newSpannable(cs.toString());
        ArrayList<EmojiSpanRange> emojis = parseEmojis(s, emojiOnly);
        if (emojis.isEmpty()) return cs;
        EmojiSpan span;
        Drawable drawable;
        int limitCount = 4096;
        for (int i = 0; i < emojis.size(); ++i) {
            try {
                EmojiSpanRange emojiRange = emojis.get(i);
                drawable = getEmojiDrawable(emojiRange.code);
                if (drawable != null) {
                    span = new EmojiSpan(drawable, alignment, fontMetrics);
                    span.emoji = emojiRange.code == null ? null : emojiRange.code.toString();
                    span.scale = scale;
                    s.setSpan(span, emojiRange.start, emojiRange.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } catch (Exception e) {
                Logger.LOGE(TAG, "replaceEmoji setSpan", e);
            }
            if ((i + 1) >= limitCount) break;
        }
        return s;
    }

    public static class EmojiSpan extends ImageSpan {
        public Paint.FontMetricsInt fontMetrics;
        public float scale = 1f;
        public int size;
        public String emoji;

        public EmojiSpan(Drawable d, int verticalAlignment, Paint.FontMetricsInt original) {
            super(d, verticalAlignment);
            fontMetrics = original;
            size = computeSize(original);
        }

        private static int computeSize(Paint.FontMetricsInt fm) {
            int s = 0;
            if (fm != null) s = Math.abs(fm.descent) + Math.abs(fm.ascent);
            if (s == 0) {
                Context ctx = AppManager.getInstance().getContext();
                Resources res = ctx != null ? ctx.getResources() : Resources.getSystem();
                s = (int) Math.ceil(20 * res.getDisplayMetrics().density);
            }
            return s;
        }

        public void replaceFontMetrics(Paint.FontMetricsInt newMetrics, int newSize) {
            fontMetrics = newMetrics;
            size = newSize;
        }

        public void replaceFontMetrics(Paint.FontMetricsInt newMetrics) {
            fontMetrics = newMetrics;
            size = computeSize(newMetrics);
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (fm == null) fm = new Paint.FontMetricsInt();
            int scaledSize = (int) (scale * size);
            if (fontMetrics == null) {
                int sz = super.getSize(paint, text, start, end, fm);
                Context ctx = AppManager.getInstance().getContext();
                Resources res = ctx != null ? ctx.getResources() : Resources.getSystem();
                int offset = (int) Math.ceil(8 * res.getDisplayMetrics().density);
                int w = (int) Math.ceil(10 * res.getDisplayMetrics().density);
                fm.top = -w - offset;
                fm.bottom = w - offset;
                fm.ascent = -w - offset;
                fm.leading = 0;
                fm.descent = w - offset;
                return sz;
            } else {
                fm.ascent = fontMetrics.ascent;
                fm.descent = fontMetrics.descent;
                fm.top = fontMetrics.top;
                fm.bottom = fontMetrics.bottom;
                if (getDrawable() != null) getDrawable().setBounds(0, 0, scaledSize, scaledSize);
                return scaledSize;
            }
        }

        public boolean drawn;
        public float lastDrawX, lastDrawY;

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            lastDrawX = x + scale * size / 2f;
            lastDrawY = top + (bottom - top) / 2f;
            drawn = true;
            boolean restoreAlpha = false;
            if (paint.getAlpha() != 255 && emojiDrawingUseAlpha) {
                restoreAlpha = true;
                getDrawable().setAlpha(paint.getAlpha());
            }
            boolean needRestore = false;
            float ty = emojiDrawingYOffset - (size - scale * size) / 2;
            if (ty != 0) {
                needRestore = true;
                canvas.save();
                canvas.translate(0, ty);
            }
            super.draw(canvas, text, start, end, x, top, y, bottom, paint);
            if (needRestore) canvas.restore();
            if (restoreAlpha) getDrawable().setAlpha(255);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            if (getDrawable() instanceof EmojiDrawable) {
                ((EmojiDrawable) getDrawable()).placeholderColor = 0x10ffffff & ds.getColor();
            }
            super.updateDrawState(ds);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EmojiSpan that = (EmojiSpan) o;
            return Float.compare(scale, that.scale) == 0 && size == that.size && Objects.equals(emoji, that.emoji);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scale, size, emoji);
        }
    }
}