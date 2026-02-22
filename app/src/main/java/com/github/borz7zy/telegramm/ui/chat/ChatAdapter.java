package com.github.borz7zy.telegramm.ui.chat;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.LoopingMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.ui.model.MessageItem;
import com.github.borz7zy.telegramm.ui.model.PhotoData;
import com.github.borz7zy.telegramm.ui.model.SystemMessages;
import com.github.borz7zy.telegramm.ui.widget.JustifiedLayout;
import com.github.borz7zy.telegramm.utils.RoundedOutlineProvider;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class ChatAdapter extends PagingDataAdapter<MessageItem, RecyclerView.ViewHolder> {

    private static final int VT_IN = 0;
    private static final int VT_OUT = 1;
    private static final int VT_SYSTEM = 2;
    private static final int VT_LOADING = 3;

    private int chatAvatarFileId = 0;

    public static final int PAYLOAD_TEXT = 1;
    public static final int PAYLOAD_MEDIA = 2;
    public static final int PAYLOAD_STATUS = 4;
    public static final int PAYLOAD_BUTTONS = 8;
    public static final int PAYLOAD_USER_INFO = 16;

    private static final int MAX_PHOTO_POOL = 10;

    private Runnable loadMoreListener;
    private static final int LOAD_MORE_THRESHOLD = 8;

    private static final AtomicLong REQUEST_ID_GEN = new AtomicLong();

    public interface OnBtnClickListener {
        void onBtnClick(MessageItem item, UiContent.UiButton btn);
    }

    private OnBtnClickListener btnListener;

    public void setBtnListener(OnBtnClickListener listener) {
        this.btnListener = listener;
    }

    public void setLoadMoreListener(Runnable listener) {
        this.loadMoreListener = listener;
    }

    public ChatAdapter() {
        super(DIFF);
    }

    @Override
    public int getItemViewType(int position) {
        MessageItem m = getItem(position);

        if (m == null) {
            return VT_LOADING;
        }

        if (m.ui != null && m.ui.kind() == UiContent.Kind.SYSTEM) return VT_SYSTEM;
        return m.outgoing ? VT_OUT : VT_IN;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());

        if (viewType == VT_LOADING) {
            View v = new View(parent.getContext());
            v.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100));
            return new RecyclerView.ViewHolder(v){};
        }

        if (viewType == VT_SYSTEM) {
            View v = inf.inflate(R.layout.item_message_system, parent, false);
            return new SystemVH(v);
        }

        if (viewType == VT_OUT) {
            View v = inf.inflate(R.layout.item_message_out, parent, false);
            return new VH(v);
        } else {
            View v = inf.inflate(R.layout.item_message_in, parent, false);
            return new VH(v);
        }
    }

    @UnstableApi
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position < LOAD_MORE_THRESHOLD && loadMoreListener != null) {
            loadMoreListener.run();
        }

        MessageItem m = getItem(position);

        if (m == null) {
            return;
        }

        if (holder instanceof SystemVH sh) {
            bindSystemMessage(sh, m);
            return;
        }

        if (holder instanceof VH h) {
            bindUserMessage(h, m);
        }
    }

    private void bindSystemMessage(SystemVH sh, MessageItem m) {
        UiContent.System sysUi = (UiContent.System) m.ui;
        sh.giftImage.setVisibility(View.GONE);
        sh.giftName.setVisibility(View.GONE);
        sh.comment.setVisibility(View.GONE);

        if (sysUi.messageType instanceof SystemMessages.PremiumGift pg) {
            sh.giftName.setText(sysUi.text);
            sh.giftName.setVisibility(View.VISIBLE);
            sh.system.setText(pg.complete_caption);
            sh.system.setVisibility(View.VISIBLE);
            sh.comment.setText(pg.comment);
            sh.comment.setVisibility(View.VISIBLE);
            sh.giftImage.setVisibility(View.VISIBLE);
            bindGiftSticker(sh.giftImage, pg);
        } else if (sysUi.messageType instanceof SystemMessages.Default) {
            sh.system.setText(sysUi.text);
            sh.system.setVisibility(View.VISIBLE);
        }
    }

    @UnstableApi
    private void bindUserMessage(VH h, MessageItem m) {
        resetMedia(h);

        String text = "";
        if (m.ui instanceof UiContent.Text t) text = t.text;
        else if (m.ui instanceof UiContent.Media md) text = md.caption;

        if (TextUtils.isEmpty(text)) {
            h.text.setVisibility(View.GONE);
        } else {
            h.text.setVisibility(View.VISIBLE);
            h.text.setText(text);
        }

        h.time.setText(m.time);

        if (h.userName != null) {
            if (!TextUtils.isEmpty(m.senderName) && !m.senderName.equals("null")) {
                h.userName.setText(m.senderName);
                h.userName.setVisibility(View.VISIBLE);
            } else {
                h.userName.setVisibility(View.GONE);
            }
        }

        if (h.groupChatUserTag != null) {
            if (!TextUtils.isEmpty(m.gcTag) && !m.gcTag.equals("null")) {
                h.groupChatUserTag.setText(m.gcTag);
                h.groupChatUserTag.setVisibility(View.VISIBLE);
            } else {
                h.groupChatUserTag.setVisibility(View.GONE);
            }
        }

        if (m.ui instanceof UiContent.Sticker sticker) {
            bindSticker(h, sticker);
            return;
        }

        bindImages(h, h.imageBoardTop, m.photos);
        bindIncomingAvatar(h, m);
        bindButtons(h, m);
    }

    private void resetMedia(VH h) {
        h.imageBoardTop.setVisibility(View.GONE);
        h.imageBoardBottom.setVisibility(View.GONE);

        h.stickerView.setVisibility(View.GONE);
        Glide.with(h.stickerView).clear(h.stickerView);

        h.stickerPlayerView.setVisibility(View.GONE);
        h.stickerPlayerView.setPlayer(null);

        h.mediaRequestId = REQUEST_ID_GEN.incrementAndGet();

        if (h.player != null) {
            h.player.release();
            h.player = null;
            h.currentVideoPath = null;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        MessageItem item = getItem(position);
        if (item == null) {
            super.onBindViewHolder(holder, position, payloads);
            return;
        }

        if (!payloads.isEmpty()) {
            int mask = 0;
            for (Object p : payloads) {
                if (p instanceof Integer) mask |= (Integer) p;
            }
            bindPartial(holder, getItem(position), mask);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof VH) {
            VH h = (VH) holder;
            if (h.player != null) {
                h.player.release();
                h.player = null;
                h.currentVideoPath = null;
            }
            if (h.stickerPlayerView != null) {
                h.stickerPlayerView.setPlayer(null);
            }
        }
    }

    @UnstableApi
    private void bindSticker(VH h, UiContent.Sticker sticker) {

        if(sticker.type != UiContent.StickerType.VIDEO_WEBM) {
            h.stickerView.setVisibility(View.GONE);
        }else{
            h.stickerPlayerView.setVisibility(View.GONE);
        }

        if (sticker.fileId == 0) return;

        String cached = TdMediaRepository.get().getCachedPath(sticker.fileId);

        if (!TextUtils.isEmpty(cached)) {
            if(sticker.type != UiContent.StickerType.VIDEO_WEBM) {
                renderSticker(h.stickerView, cached, sticker.type);
            }else{
                renderSticker(h.stickerPlayerView, cached, sticker.type);
            }
            return;
        }

        String contentKey = "sticker_" + sticker.fileId;
        Object currentKey = h.stickerView.getTag();
        Object currentPlayerKey = h.stickerPlayerView.getTag();

        if(Objects.equals(contentKey, currentKey) || Objects.equals(contentKey, currentPlayerKey)){
            return;
        }

        h.stickerView.setTag(contentKey);
        h.stickerPlayerView.setTag(contentKey);

        if(sticker.type != UiContent.StickerType.VIDEO_WEBM) {
            h.stickerView.setImageResource(R.drawable.bg_msg_bubble);
            h.stickerView.setVisibility(View.VISIBLE);
        }else{
            h.stickerPlayerView.setVisibility(View.VISIBLE);
        }
        long requestId = REQUEST_ID_GEN.incrementAndGet();
        h.mediaRequestId = requestId;

        WeakReference<ImageView> weak = new WeakReference<>(h.stickerView);
        WeakReference<PlayerView> weakPlayer = new WeakReference<>(h.stickerPlayerView);

        final long expectedId = requestId;
        final String reqKey = contentKey;
        final int reqFid = sticker.fileId;

        TdMediaRepository.get().getPathOrRequest(reqFid, path -> {
            if(h.mediaRequestId != expectedId){
                return;
            }

            if(sticker.type != UiContent.StickerType.VIDEO_WEBM){
                ImageView iv = weak.get();
                if (iv == null) return;
                Object tag = iv.getTag();
                if(!Objects.equals(tag, reqKey)) return;
                if (TextUtils.isEmpty(path)) return;
                iv.post(() -> {
                    ViewGroup.LayoutParams layoutParams = iv.getLayoutParams();
                    layoutParams.width = sticker.width;
                    layoutParams.height = sticker.height;
                    iv.setLayoutParams(layoutParams);
                    renderSticker(iv, path, sticker.type);
                });
            }else{
                PlayerView pv = weakPlayer.get();
                if(pv == null) return;
                Object tag = pv.getTag();
                if(!Objects.equals(tag, reqKey)) return;
                if (TextUtils.isEmpty(path)) return;
                pv.post(()-> {
                    ViewGroup.LayoutParams layoutParams = pv.getLayoutParams();
                    layoutParams.width = sticker.width;
                    layoutParams.height = sticker.height;
                    pv.setLayoutParams(layoutParams);
                    renderSticker(pv, path, sticker.type);
                });
            }
        });
    }

    @UnstableApi
    private void renderSticker(Object obj, String path, UiContent.Sticker.StickerType type) {

        if (type == UiContent.StickerType.STATIC) {
            ImageView iv = (ImageView)obj;
            iv.setVisibility(View.VISIBLE);

            Glide.with(iv)
                    .load(path)
                    .dontAnimate()
                    .into(iv);

            return;
        }

        if (type == UiContent.StickerType.ANIMATED_TGS) {
            ImageView iv = (ImageView)obj;
            iv.setVisibility(View.VISIBLE);
            renderLottieSticker(iv, path);
            return;
        }

        if (type == UiContent.StickerType.VIDEO_WEBM) {
            PlayerView iv = (PlayerView)obj;
            iv.setVisibility(View.VISIBLE);
            renderVideoSticker(iv, path);
        }
    }

    private void renderLottieSticker(ImageView iv, String path) {

        if (!(iv instanceof LottieAnimationView)) {
            return;
        }

        LottieAnimationView lav =
                (com.airbnb.lottie.LottieAnimationView) iv;

        lav.setAnimation(new File(path).getAbsolutePath());
        lav.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE);
        lav.playAnimation();
    }

    @UnstableApi
    private void renderVideoSticker(PlayerView view, String path) {
        VH h = (VH) view.getTag(R.id.tag_holder);
        if (h == null) return;

        if (h.player != null && TextUtils.equals(path, h.currentVideoPath)) {
            if (!h.player.getPlayWhenReady()) {
                h.player.setPlayWhenReady(true);
            }
            return;
        }

        if (h.player != null) {
            h.player.release();
            h.player = null;
        }
        h.currentVideoPath = path;

        view.setUseController(false);
        view.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT);
        view.setKeepContentOnPlayerReset(true);
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        view.setClickable(false);
        view.setFocusable(false);

        Context context = view.getContext();

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        10000,
                        50000,
                        1000,
                        1000
                ).build();

        ExoPlayer player = new ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build();
        h.player = player;

        player.setTrackSelectionParameters(
                player.getTrackSelectionParameters()
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .build()
        );
        player.setVolume(0f);

        MediaSource fileSource =
                new ProgressiveMediaSource.Factory(
                        new DefaultDataSource.Factory(context)
                ).createMediaSource(MediaItem.fromUri(Uri.fromFile(new File(path))));

        LoopingMediaSource loopingSource =
                new LoopingMediaSource(fileSource);

        player.setMediaSource(loopingSource);

        player.setRepeatMode(Player.REPEAT_MODE_OFF);

        player.prepare();
        player.setPlayWhenReady(true);

        view.setPlayer(player);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (holder instanceof VH h) {
            if (h.player != null) {
                h.player.pause();
            }
        }
    }

    private void bindButtons(VH h, MessageItem item) {
        if (h.buttonsContainer == null) return;

        h.buttonsContainer.removeAllViews();

        if (item.ui == null || item.ui.buttons.isEmpty()) {
            h.buttonsContainer.setVisibility(View.GONE);
            return;
        }

        h.buttonsContainer.setVisibility(View.VISIBLE);
        Context ctx = h.buttonsContainer.getContext();

        for (List<UiContent.UiButton> row : item.ui.buttons) {
            LinearLayout rowLayout = new LinearLayout(ctx);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            for (UiContent.UiButton btnData : row) {
                Button btnView = new Button(ctx);
                btnView.setText(btnData.text);
                btnView.setAllCaps(false);
                btnView.setTextSize(14f);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                lp.setMargins(4, 4, 4, 4);
                btnView.setLayoutParams(lp);

                btnView.setOnClickListener(v -> {
                    if (btnListener != null) btnListener.onBtnClick(item, btnData);
                });

                rowLayout.addView(btnView);
            }
            h.buttonsContainer.addView(rowLayout);
        }
    }

    private void bindPartial(RecyclerView.ViewHolder holder, MessageItem item, int mask) {
        if (holder instanceof SystemVH) {
            // TODO: optimize this plz =|
            return;
        }

        VH h = (VH) holder;

        if((mask & PAYLOAD_USER_INFO) != 0){
            if (h.userName != null) {
                if (!TextUtils.isEmpty(item.senderName) && !item.senderName.equals("null")) {
                    h.userName.setText(item.senderName);
                    h.userName.setVisibility(View.VISIBLE);
                } else {
                    h.userName.setVisibility(View.GONE);
                }
            }
            if (h.groupChatUserTag != null) {
                if (!TextUtils.isEmpty(item.gcTag) && !item.gcTag.equals("null")) {
                    h.groupChatUserTag.setText(item.gcTag);
                    h.groupChatUserTag.setVisibility(View.VISIBLE);
                } else {
                    h.groupChatUserTag.setVisibility(View.GONE);
                }
            }
            bindIncomingAvatar(h, item);
        }

        if ((mask & PAYLOAD_TEXT) != 0) {
            String text = "";
            if (item.ui instanceof UiContent.Text t) text = t.text;
            else if (item.ui instanceof UiContent.Media md) text = md.caption;

            if (TextUtils.isEmpty(text)) {
                h.text.setVisibility(View.GONE);
            } else {
                h.text.setVisibility(View.VISIBLE);
                h.text.setText(text);
            }
            h.time.setText(item.time);
        }

        if ((mask & PAYLOAD_MEDIA) != 0) {
            bindImages(h, h.imageBoardTop, item.photos);
        }

        if ((mask & PAYLOAD_STATUS) != 0) {
            // unused?
        }

        if ((mask & PAYLOAD_BUTTONS) != 0) {
            bindButtons(h, item);
        }
    }


    public void setChatAvatar(int fileId) {
        if (chatAvatarFileId == fileId) return;
        chatAvatarFileId = fileId;
        notifyDataSetChanged(); // TODO: optimize
    }

    private void bindGiftSticker(ImageView iv, SystemMessages.PremiumGift pg) {
        Glide.with(iv).clear(iv);
        iv.setImageResource(R.drawable.bg_badge);

        if (!TextUtils.isEmpty(pg.stickerPath)) {
            Glide.with(iv)
                    .load(pg.stickerPath)
                    .placeholder(R.drawable.bg_badge)
                    .error(R.drawable.bg_badge)
                    .into(iv);
            return;
        }

        int fid = pg.stickerFileId;
        if (fid == 0) return;

        iv.setTag(fid);

        WeakReference<ImageView> weakIv = new WeakReference<>(iv);

        TdMediaRepository.get().getPathOrRequest(fid, path -> {
            ImageView view = weakIv.get();
            if (view == null) return;

            Object tag = view.getTag();
            if (!(tag instanceof Integer) || ((Integer) tag) != fid) return;

            if (TextUtils.isEmpty(path)) return;

            view.post(() -> {
                Glide.with(view)
                        .load(path)
                        .placeholder(R.drawable.bg_badge)
                        .error(R.drawable.bg_badge)
                        .into(view);
            });
        });
    }

    private void bindImages(VH h, JustifiedLayout layout, List<PhotoData> photos) {
        if (photos == null || photos.isEmpty()) {
            layout.setVisibility(View.GONE);
            return;
        }

        layout.setVisibility(View.VISIBLE);

        final int screenWidth = layout.getResources().getDisplayMetrics().widthPixels;
        final int bubbleWidth = (int) (screenWidth * 0.80f);

        ViewGroup.LayoutParams params = layout.getLayoutParams();
        if (params.width != bubbleWidth) {
            params.width = bubbleWidth;
            layout.setLayoutParams(params);
        }

        final int photoCount = photos.size();

        int targetHeight = (photoCount == 1) ? bubbleWidth : dp(layout, 120);
        Object currentMode = layout.getTag(R.id.tag_layout_mode);

        boolean needLayoutUpdate = (currentMode == null || !currentMode.equals(targetHeight));

        if (needLayoutUpdate) {
            layout.setTag(R.id.tag_layout_mode, targetHeight);

            if (photoCount == 1) {
                layout.setTargetRowHeightPx(bubbleWidth);
                layout.setRowHeightBoundsPx(dp(layout, 100), dp(layout, 450));
                layout.setJustifyLastRow(false);
            } else {
                layout.setTargetRowHeightPx(dp(layout, 120));
                layout.setRowHeightBoundsPx(dp(layout, 80), dp(layout, 200));
                layout.setJustifyLastRow(true);
            }
            layout.setSpacingPx(dp(layout,2));
        }

        while (layout.getChildCount() < MAX_PHOTO_POOL) {
            ImageView iv = new ImageView(layout.getContext());
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setClipToOutline(true);
            iv.setOutlineProvider(new RoundedOutlineProvider(dp(layout, 10)));
            layout.addView(iv);
        }

        for (int i = 0; i < MAX_PHOTO_POOL; ++i) {
            ImageView iv = (ImageView) layout.getChildAt(i);

            if (i >= photoCount) {
                Glide.with(iv).clear(iv);
                iv.setImageDrawable(null);
                iv.setTag(null);
                iv.setVisibility(View.GONE);
                continue;
            }

            if (iv.getVisibility() != View.VISIBLE) {
                iv.setVisibility(View.VISIBLE);
            }

            PhotoData photo = photos.get(i);

            JustifiedLayout.LayoutParams lp = (JustifiedLayout.LayoutParams) iv.getLayoutParams();
            if (lp == null) {
                lp = new JustifiedLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.aspectRatio = photo.aspectRatio;
                iv.setLayoutParams(lp);
            } else if (Math.abs(lp.aspectRatio - photo.aspectRatio) > 0.001f) {
                lp.aspectRatio = photo.aspectRatio;
                iv.setLayoutParams(lp);
            }

            String contentKey = (photo.fileId != 0) ? "remote_" + photo.fileId : "local_" + photo.localPath;
            Object currentKey = iv.getTag();

            if (Objects.equals(contentKey, currentKey)) {
                continue;
            }

            iv.setTag(contentKey);

            String path = photo.localPath;
            if (TextUtils.isEmpty(path) && photo.fileId != 0) {
                path = TdMediaRepository.get().getCachedPath(photo.fileId);
            }

            if (!TextUtils.isEmpty(path)) {
                loadGlideImage(iv, path);
            } else if (photo.fileId != 0) {
                iv.setImageResource(R.drawable.bg_msg_bubble);

                long requestId = REQUEST_ID_GEN.incrementAndGet();
                h.mediaRequestId = requestId;

                WeakReference<ImageView> weakImg = new WeakReference<>(iv);

                final long expectedId = requestId;
                final String reqKey = contentKey;
                final int reqFid = photo.fileId;

                TdMediaRepository.get().getPathOrRequest(reqFid, pathh -> {

                    if (h.mediaRequestId != expectedId) {
                        return;
                    }

                    ImageView v = weakImg.get();
                    if (v == null) return;

                    Object tag = v.getTag();
                    if (!Objects.equals(tag, reqKey)) return;

                    if (TextUtils.isEmpty(pathh)) return;

                    v.post(() -> {
                        if (h.mediaRequestId != expectedId) return;
                        loadGlideImage(v, pathh);
                    });
                });
            }
        }
    }

    private void loadGlideImage(ImageView iv, String path) {

        Glide.with(iv)
                .load(path)
                .centerCrop()
                .dontAnimate()
                .placeholder(R.drawable.bg_msg_bubble)
                .error(R.drawable.bg_msg_bubble)
                .into(iv);
    }

    private int dp(View v, int dp) {
        return (int) (dp * v.getResources().getDisplayMetrics().density);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView userName;
        final TextView groupChatUserTag;
        final TextView text;
        final TextView time;
        final JustifiedLayout imageBoardTop;
        final JustifiedLayout imageBoardBottom;
        final ImageView stickerView;
        PlayerView stickerPlayerView;
        ExoPlayer player;
        String currentVideoPath;
        final ImageView avatar;
        final ViewGroup buttonsContainer;

        long mediaRequestId = 0L;

        VH(@NonNull View itemView) {
            super(itemView);

            itemView.setTag(R.id.tag_holder, this);

            userName = itemView.findViewById(R.id.username_tv);
            groupChatUserTag = itemView.findViewById(R.id.group_tag_tv);

            text = itemView.findViewById(R.id.tv_text);

            time = itemView.findViewById(R.id.tv_time);

            imageBoardTop = itemView.findViewById(R.id.image_board_top);
            imageBoardBottom = itemView.findViewById(R.id.image_board_bottom);

            stickerView = itemView.findViewById(R.id.sticker_view);
            stickerPlayerView = itemView.findViewById(R.id.sticker_player_view);
            stickerPlayerView.setTag(R.id.tag_holder, this);

            avatar = itemView.findViewById(R.id.msg_avatar);

            buttonsContainer = itemView.findViewById(R.id.buttons_container);
        }
    }

    static class SystemVH extends RecyclerView.ViewHolder {
        final ImageView giftImage;
        final TextView giftName;
        final TextView system;
        final TextView comment;

        SystemVH(@NonNull View itemView) {
            super(itemView);
            giftImage = itemView.findViewById(R.id.image_gift);
            giftName = itemView.findViewById(R.id.gift_name);
            system = itemView.findViewById(R.id.tv_system);
            comment = itemView.findViewById(R.id.gift_comment);
        }
    }

    private static final DiffUtil.ItemCallback<MessageItem> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull MessageItem oldItem, @NonNull MessageItem newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull MessageItem oldItem, @NonNull MessageItem newItem) {
            if (!TextUtils.equals(oldItem.time, newItem.time)) return false;
            if (!Objects.equals(oldItem.ui, newItem.ui)) return false;
            if (!Objects.equals(oldItem.photos, newItem.photos)) return false;
            if (!TextUtils.equals(oldItem.senderName, newItem.senderName)) return false;
            if (!TextUtils.equals(oldItem.gcTag, newItem.gcTag)) return false;
            if (oldItem.senderAvatarFileId != newItem.senderAvatarFileId) return false;
            if (!buttonsEqual(oldItem.ui, newItem.ui)) return false;
            return true;
        }

        @Override
        public Object getChangePayload(@NonNull MessageItem oldItem, @NonNull MessageItem newItem) {
            int mask = 0;

            if (!Objects.equals(oldItem.ui, newItem.ui)) {
                mask |= PAYLOAD_TEXT;
                mask |= PAYLOAD_BUTTONS;
            }

            if (!TextUtils.equals(oldItem.time, newItem.time)) {
                mask |= PAYLOAD_TEXT;
            }

            if (!Objects.equals(oldItem.photos, newItem.photos)) {
                mask |= PAYLOAD_MEDIA;
            }

            boolean nameDiff = !TextUtils.equals(oldItem.senderName, newItem.senderName);
            boolean tagDiff = !TextUtils.equals(oldItem.gcTag, newItem.gcTag);
            boolean avaDiff = oldItem.senderAvatarFileId != newItem.senderAvatarFileId;

            if (nameDiff || tagDiff || avaDiff) {
                mask |= PAYLOAD_USER_INFO;
            }

            return mask == 0 ? null : mask;
        }

        private static boolean buttonsEqual(UiContent a, UiContent b) {
            if (a == b) return true;
            if (a == null || b == null) return false;

            List<List<UiContent.UiButton>> aa = a.buttons;
            List<List<UiContent.UiButton>> bb = b.buttons;

            if (aa == bb) return true;
            if (aa.size() != bb.size()) return false;

            for (int i = 0; i < aa.size(); ++i) {
                List<UiContent.UiButton> ra = aa.get(i);
                List<UiContent.UiButton> rb = bb.get(i);
                if (ra.size() != rb.size()) return false;

                for (int j = 0; j < ra.size(); ++j) {
                    UiContent.UiButton ba = ra.get(j);
                    UiContent.UiButton bb2 = rb.get(j);

                    if (!TextUtils.equals(ba.text, bb2.text)) return false;
                    if (!TextUtils.equals(ba.url, bb2.url)) return false;
                    if (!Arrays.equals(ba.data, bb2.data)) return false;
                }
            }

            return true;
        }
    };

    private void bindIncomingAvatar(VH h, MessageItem m) {
        if (h.avatar == null) return;

        int fid = getAvatarFileIdForSender(m);

        if (fid == 0){
            h.avatar.setImageResource(R.drawable.bg_badge);
            return;
        }

        final String tag = "msg:" + m.chatId + ":" + fid;

        if (tag.equals(h.avatar.getTag())) return;

        h.avatar.setTag(tag);
        Glide.with(h.avatar).clear(h.avatar);
        h.avatar.setImageResource(R.drawable.bg_badge);

        String cached = TdMediaRepository.get().getCachedPath(fid);
        if (!TextUtils.isEmpty(cached)) {
            Glide.with(h.avatar)
                    .load(cached)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.bg_badge)
                    .error(R.drawable.bg_badge)
                    .into(h.avatar);
            return;
        }

        WeakReference<ImageView> weakAvatar = new WeakReference<>(h.avatar);

        TdMediaRepository.get().getPathOrRequest(fid, p -> {
            ImageView iv = weakAvatar.get();
            if (iv == null) return;

            Object cur = iv.getTag();
            if (!(cur instanceof String) || !tag.equals(cur)) return;
            if (TextUtils.isEmpty(p)) return;

            iv.post(() -> {
                Glide.with(iv)
                        .load(p)
                        .apply(RequestOptions.circleCropTransform())
                        .placeholder(R.drawable.bg_badge)
                        .error(R.drawable.bg_badge)
                        .into(iv);
            });
        });
    }

    private int getAvatarFileIdForSender(MessageItem m) {
        return m.senderAvatarFileId;
    }

}
