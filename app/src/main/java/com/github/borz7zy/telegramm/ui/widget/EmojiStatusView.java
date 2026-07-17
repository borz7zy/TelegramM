package com.github.borz7zy.telegramm.ui.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.github.borz7zy.ffmpeg.VideoStickerDrawable;
import com.github.borz7zy.rlottie.RLottieAnimationView;
import com.github.borz7zy.telegramm.utils.EmojiStatusRepository;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import org.drinkless.tdlib.TdApi;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class EmojiStatusView extends FrameLayout {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final ExecutorService VIDEO_EXEC = Executors.newSingleThreadExecutor(
            new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger();
                @Override public Thread newThread(@NonNull Runnable r) {
                    Thread t = new Thread(r, "emoji-status-video-" + n.incrementAndGet());
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
            });

    private final ImageView imageView;
    private final RLottieAnimationView lottieView;
    @Nullable private VideoStickerDrawable videoDrawable;

    private long currentCustomEmojiId = 0L;
    private long generation = 0L;

    public EmojiStatusView(Context context) { this(context, null); }

    public EmojiStatusView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmojiStatusView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setVisibility(GONE);
        addView(imageView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        lottieView = new RLottieAnimationView(context);
        lottieView.setVisibility(GONE);
        lottieView.setRepeatCount(RLottieAnimationView.INFINITE);
        addView(lottieView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        setVisibility(GONE);
    }

    public void setEmojiStatus(long customEmojiId) {
        if (customEmojiId == currentCustomEmojiId) return;

        currentCustomEmojiId = customEmojiId;
        long g = ++generation;

        clearViews();

        if (customEmojiId == 0L) {
            setVisibility(GONE);
            return;
        }

        setVisibility(VISIBLE);

        EmojiStatusRepository.get().resolve(customEmojiId, sticker -> {
            if (g != generation) return;
            if (sticker == null || sticker.sticker == null) {
                setVisibility(GONE);
                return;
            }
            loadStickerFile(sticker, g);
        });
    }

    private void loadStickerFile(TdApi.Sticker sticker, long g) {
        int fileId = sticker.sticker.id;
        if (fileId == 0) {
            setVisibility(GONE);
            return;
        }

        String cached = TdMediaRepository.get().getCachedPath(fileId);
        if (!TextUtils.isEmpty(cached)) {
            renderSticker(sticker, cached, g);
            return;
        }

        TdMediaRepository.get().getPathOrRequest(fileId, path -> {
            if (g != generation) return;
            if (TextUtils.isEmpty(path)) return;
            renderSticker(sticker, path, g);
        });
    }

    private void renderSticker(TdApi.Sticker sticker, String path, long g) {
        if (sticker.format instanceof TdApi.StickerFormatTgs) {
            renderTgs(path, g);
        } else if (sticker.format instanceof TdApi.StickerFormatWebm) {
            renderWebm(path, g);
        } else {
            renderStatic(path, g);
        }
    }

    private void renderTgs(String path, long g) {
        if (g != generation) return;
        releaseVideoDrawable();
        imageView.setVisibility(GONE);
        lottieView.setVisibility(VISIBLE);
        lottieView.setRepeatCount(RLottieAnimationView.INFINITE);
        lottieView.setTgsFile(path, path);
    }

    private void renderStatic(String path, long g) {
        if (g != generation) return;
        releaseVideoDrawable();
        lottieView.setVisibility(GONE);
        imageView.setImageDrawable(null);
        imageView.setVisibility(VISIBLE);
        Glide.with(imageView).load(new File(path)).into(imageView);
    }

    private void renderWebm(String path, long g) {
        if (g != generation) return;

        final int w = getWidth() > 0 ? getWidth() : 128;
        final int h = getHeight() > 0 ? getHeight() : 128;

        VIDEO_EXEC.execute(() -> {
            VideoStickerDrawable d = new VideoStickerDrawable(path, "webm:" + path, w, h);
            if (!d.isValid()) {
                d.release();
                return;
            }
            MAIN.post(() -> {
                if (g != generation) {
                    d.release();
                    return;
                }
                releaseVideoDrawable();
                videoDrawable = d;
                lottieView.setVisibility(GONE);
                try { Glide.with(imageView).clear(imageView); } catch (Exception ignored) {}
                imageView.setVisibility(VISIBLE);
                imageView.setImageDrawable(d);
                d.setRepeatCount(VideoStickerDrawable.INFINITE);
                d.start();
            });
        });
    }

    private void releaseVideoDrawable() {
        if (videoDrawable != null) {
            if (imageView.getDrawable() == videoDrawable) {
                imageView.setImageDrawable(null);
            }
            videoDrawable.release();
            videoDrawable = null;
        }
    }

    private void clearViews() {
        try { Glide.with(imageView).clear(imageView); } catch (Exception ignored) {}
        releaseVideoDrawable();
        imageView.setImageDrawable(null);
        imageView.setVisibility(GONE);
        lottieView.cancelAnimation();
        lottieView.setImageDrawable(null);
        lottieView.setVisibility(GONE);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (lottieView.getVisibility() == VISIBLE) {
            lottieView.pauseAnimation();
        }
        if (videoDrawable != null) {
            videoDrawable.stop();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (lottieView.getVisibility() == VISIBLE && lottieView.getLottieDrawable() != null) {
            lottieView.playAnimation();
        }
        if (videoDrawable != null && videoDrawable.isValid()) {
            videoDrawable.start();
        }
    }
}
