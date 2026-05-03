package com.github.borz7zy.telegramm.ui.widget;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.github.borz7zy.telegramm.R;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.LoopingMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;
import com.bumptech.glide.Glide;
import com.github.borz7zy.telegramm.utils.EmojiStatusRepository;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import org.drinkless.tdlib.TdApi;

import java.io.File;
import java.io.FileInputStream;
import java.util.zip.GZIPInputStream;

@UnstableApi
public class EmojiStatusView extends FrameLayout {

    private final ImageView imageView;
    private final LottieAnimationView lottieView;
    private final PlayerView playerView;
    @Nullable private ExoPlayer player;
    @Nullable private String currentVideoPath;

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

        lottieView = new LottieAnimationView(context);
        lottieView.setVisibility(GONE);
        lottieView.setRepeatCount(LottieDrawable.INFINITE);
        addView(lottieView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        playerView = (PlayerView) LayoutInflater.from(context)
                .inflate(R.layout.widget_emoji_status_player, this, false);
        playerView.setUseController(false);
        playerView.setKeepContentOnPlayerReset(true);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setClickable(false);
        playerView.setFocusable(false);
        playerView.setVisibility(GONE);
        addView(playerView);

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
        try {
            LottieCompositionFactory.fromJsonInputStream(
                    new GZIPInputStream(new FileInputStream(path)), path
            ).addListener(composition -> {
                if (g != generation || composition == null) return;
                imageView.setVisibility(GONE);
                playerView.setVisibility(GONE);
                lottieView.setVisibility(VISIBLE);
                lottieView.setComposition(composition);
                lottieView.setRepeatCount(LottieDrawable.INFINITE);
                lottieView.playAnimation();
            }).addFailureListener(t -> {
                if (g != generation) return;
                setVisibility(GONE);
            });
        } catch (Exception e) {
            setVisibility(GONE);
        }
    }

    private void renderStatic(String path, long g) {
        if (g != generation) return;
        lottieView.setVisibility(GONE);
        playerView.setVisibility(GONE);
        imageView.setVisibility(VISIBLE);
        Glide.with(imageView).load(new File(path)).into(imageView);
    }

    private void renderWebm(String path, long g) {
        if (g != generation) return;

        if (player != null && TextUtils.equals(path, currentVideoPath)) {
            if (!player.getPlayWhenReady()) player.setPlayWhenReady(true);
            return;
        }

        releasePlayer();
        currentVideoPath = path;

        Context ctx = getContext();
        ExoPlayer p = new ExoPlayer.Builder(ctx).build();
        p.setTrackSelectionParameters(
                p.getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .build()
        );
        p.setVolume(0f);

        MediaSource src = new ProgressiveMediaSource.Factory(new DefaultDataSource.Factory(ctx))
                .createMediaSource(MediaItem.fromUri(Uri.fromFile(new File(path))));
        p.setMediaSource(new LoopingMediaSource(src));
        p.setRepeatMode(Player.REPEAT_MODE_OFF);
        p.prepare();
        p.setPlayWhenReady(true);

        player = p;
        imageView.setVisibility(GONE);
        lottieView.setVisibility(GONE);
        playerView.setVisibility(VISIBLE);
        playerView.setPlayer(p);
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        playerView.setPlayer(null);
        currentVideoPath = null;
    }

    private void clearViews() {
        try { Glide.with(imageView).clear(imageView); } catch (Exception ignored) {}
        imageView.setImageDrawable(null);
        imageView.setVisibility(GONE);
        lottieView.cancelAnimation();
        lottieView.setImageDrawable(null);
        lottieView.setVisibility(GONE);
        releasePlayer();
        playerView.setVisibility(GONE);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (lottieView.getVisibility() == VISIBLE) {
            lottieView.pauseAnimation();
        }
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (lottieView.getVisibility() == VISIBLE && lottieView.getComposition() != null) {
            lottieView.playAnimation();
        }
        if (player != null && playerView.getVisibility() == VISIBLE) {
            player.setPlayWhenReady(true);
        }
    }
}
