package com.github.borz7zy.telegramm.ui.chat;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.borz7zy.ffmpeg.VideoStickerDrawable;
import com.github.borz7zy.rlottie.RLottieAnimationView;
import com.github.borz7zy.shadowgram.shadowgramui.R;
import com.github.borz7zy.telegramm.core.theme.ThemeEngine;
import com.github.borz7zy.telegramm.ui.model.MessageItem;
import com.github.borz7zy.telegramm.ui.model.PhotoData;
import com.github.borz7zy.telegramm.ui.model.SystemMessages;
import com.github.borz7zy.telegramm.ui.widget.JustifiedLayout;
import com.github.borz7zy.telegramm.ui.widget.EmojiStatusView;
import com.github.borz7zy.telegramm.ui.widget.RoundedOutlineProvider;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ChatAdapter extends PagingDataAdapter<MessageItem, RecyclerView.ViewHolder> {

    private static final int VT_IN = 0;
    private static final int VT_OUT = 1;
    private static final int VT_SYSTEM = 2;
    private static final int VT_LOADING = 3;

    public static final int PAYLOAD_TEXT = 1;
    public static final int PAYLOAD_MEDIA = 2;
    public static final int PAYLOAD_STATUS = 4;
    public static final int PAYLOAD_BUTTONS = 8;
    public static final int PAYLOAD_USER_INFO = 16;

    private static final int MAX_PHOTO_POOL = 10;

    private Runnable loadMoreListener;
    private static final int LOAD_MORE_THRESHOLD = 8;

    /**
     * Per-chat (or global, when no override) Monet palette. Pushed in by
     * ChatFragment whenever the resolved theme changes; null until the first
     * theme arrives, in which case bindUserMessage falls back to the bubble's
     * XML defaults so we never paint a blank state.
     */
    private ThemeEngine.Theme theme;

    public void setTheme(ThemeEngine.Theme theme) {
        if (theme == null) return;
        if (this.theme != null
                && this.theme.seedColor == theme.seedColor
                && this.theme.isDark == theme.isDark) {
            return;
        }
        this.theme = theme;
        notifyDataSetChanged();
    }

    private static final AtomicLong REQUEST_ID_GEN = new AtomicLong();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final ExecutorService VIDEO_EXEC = Executors.newSingleThreadExecutor(
            new ThreadFactory() {
                private final AtomicInteger n = new AtomicInteger();
                @Override public Thread newThread(@NonNull Runnable r) {
                    Thread t = new Thread(r, "chat-video-sticker-" + n.incrementAndGet());
                    t.setPriority(Thread.NORM_PRIORITY - 1);
                    return t;
                }
            });

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

        if (theme != null) {
            int sysColor = theme.onSurfaceVariantColor;
            if (sh.system != null) sh.system.setTextColor(sysColor);
            if (sh.giftName != null) sh.giftName.setTextColor(theme.onSurfaceColor);
            if (sh.comment != null) sh.comment.setTextColor(sysColor);
        }

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

    private void bindMessageText(VH h, MessageItem m) {
        String text = "";
        org.drinkless.tdlib.TdApi.TextEntity[] entities = null;
        if (m.ui instanceof UiContent.Text t) {
            text = t.text;
            entities = t.entities;
        } else if (m.ui instanceof UiContent.Media md) {
            text = md.caption;
            entities = md.entities;
        }

        boolean empty = TextUtils.isEmpty(text);
        h.text.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;

        if (h.text instanceof com.github.borz7zy.telegramm.ui.emoji.EmojiTextView etv) {
            etv.setFormattedText(text, entities);
        } else {
            h.text.setText(text);
        }
    }

    private void bindOptionalLabel(TextView view, String value) {
        if (view == null) return;
        boolean has = !TextUtils.isEmpty(value) && !"null".equals(value);
        view.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) view.setText(value);
    }

    private void bindUserMessage(VH h, MessageItem m) {
        resetMedia(h);

        applyThemeToHolder(h, m);

        bindMessageText(h, m);

        h.time.setText(m.time);

        bindOptionalLabel(h.userName, m.senderName);
        bindOptionalLabel(h.groupChatUserTag, m.gcTag);

        if (h.emojiStatus != null) {
            h.emojiStatus.setEmojiStatus(m.senderEmojiCustomEmojiId);
        }
        if (m.ui instanceof UiContent.Sticker sticker) {
            applyStickerBubble(h);
            bindSticker(h, sticker);
            bindIncomingAvatar(h, m);
            return;
        }

        applyDefaultBubble(h);
        bindImages(h, h.imageBoardTop, m.photos);
        bindIncomingAvatar(h, m);
        bindButtons(h, m);
    }

    private void applyStickerBubble(VH h) {
        if (h.bubble == null) return;
        h.bubble.setBackground(null);
        h.bubble.setPadding(0, 0, 0, 0);
    }

    private void applyDefaultBubble(VH h) {
        if (h.bubble == null) return;
        if (h.bubble.getBackground() != h.bubbleBg) {
            h.bubble.setBackground(h.bubbleBg);
        }
        h.bubble.setPadding(h.bubblePadL, h.bubblePadT, h.bubblePadR, h.bubblePadB);
    }

    /**
     * Tint per-message Views from the current Monet palette. We mutate the
     * shared bubble drawable's tint, so this runs before applyStickerBubble /
     * applyDefaultBubble (which only swap background reference, not tint).
     */
    private void applyThemeToHolder(VH h, MessageItem m) {
        if (theme == null) return;

        boolean outgoing = m.outgoing;
        // Outgoing → primaryContainer (Material You "you" surface).
        // Incoming → secondaryContainer instead of surfaceContainer: in light
        // theme without a wallpaper, surface and surfaceContainer collapse to
        // near-white and the bubble disappears. secondaryContainer is tinted
        // with the seed and stays distinct from the chat surface.
        int bubbleColor = outgoing ? theme.primaryContainerColor : theme.secondaryContainerColor;
        int textColor = outgoing ? theme.onPrimaryContainerColor : theme.onSecondaryContainerColor;
        int subTextColor = outgoing ? theme.onPrimaryContainerColor : theme.onSecondaryContainerColor;

        if (h.bubbleBg != null) {
            // mutate() so the tint doesn't bleed into other holders sharing the
            // same constant-state drawable.
            android.graphics.drawable.Drawable bg = h.bubbleBg.mutate();
            bg.setTint(bubbleColor);
        }

        if (h.text != null) h.text.setTextColor(textColor);
        if (h.time != null) h.time.setTextColor(subTextColor);
        if (h.userName != null) h.userName.setTextColor(theme.primaryColor);
        if (h.groupChatUserTag != null) h.groupChatUserTag.setTextColor(subTextColor);
    }

    private static int[] computeStickerSizePx(View view, int rawW, int rawH, boolean isAnimatedEmoji) {
        int dpMax = isAnimatedEmoji ? 112 : 180;
        int maxPx = (int) (dpMax * view.getResources().getDisplayMetrics().density);
        if (rawW <= 0 || rawH <= 0) return new int[]{maxPx, maxPx};
        float ratio = (float) rawW / (float) rawH;
        int w, h;
        if (ratio >= 1f) {
            w = maxPx;
            h = (int) (maxPx / ratio);
        } else {
            h = maxPx;
            w = (int) (maxPx * ratio);
        }
        return new int[]{w, h};
    }

    private void resetMedia(VH h) {
        h.imageBoardTop.setVisibility(View.GONE);
        h.imageBoardBottom.setVisibility(View.GONE);

        h.stickerView.setVisibility(View.GONE);
        // Stop any TGS animation left over from the previous binding.
        h.stickerView.cancelAnimation();
        Glide.with(h.stickerView).clear(h.stickerView);
        // Clear the contentKey tag so a stale callback can't accidentally match
        // after the holder has been rebound to a different message.
        h.stickerView.setTag(null);

        h.mediaRequestId = REQUEST_ID_GEN.incrementAndGet();

        if (h.videoDrawable != null) {
            h.videoDrawable.release();
            h.videoDrawable = null;
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
            if (h.videoDrawable != null) {
                h.videoDrawable.release();
                h.videoDrawable = null;
            }
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (holder instanceof VH h && h.videoDrawable != null) {
            h.videoDrawable.stop();
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder instanceof VH h && h.videoDrawable != null && h.videoDrawable.isValid()) {
            h.videoDrawable.start();
        }
    }

    private void bindSticker(VH h, UiContent.Sticker sticker) {
        if (sticker.fileId == 0) return;

        int[] size = computeStickerSizePx(h.itemView, sticker.width, sticker.height, sticker.isAnimatedEmoji);
        applyStickerSize(h, size[0], size[1]);

        final String contentKey = "sticker_" + sticker.fileId;
        final RLottieAnimationView target = h.stickerView;

        target.setTag(contentKey);
        target.setVisibility(View.VISIBLE);

        String cached = TdMediaRepository.get().getCachedPath(sticker.fileId);
        if (!TextUtils.isEmpty(cached)) {
            renderSticker(h, cached, sticker.type);
            return;
        }

        target.setImageDrawable(null);

        final long requestId = REQUEST_ID_GEN.incrementAndGet();
        h.mediaRequestId = requestId;

        TdMediaRepository.get().getPathOrRequest(sticker.fileId, path ->
            onStickerPathReady(h, contentKey, requestId, path, sticker.type));
    }

    private void onStickerPathReady(VH h, String contentKey,
                                    long requestId, String path, UiContent.StickerType type) {
        // Runs on the main thread (fired from finish() via mainHandler).
        if (h.mediaRequestId != requestId) return;
        if (TextUtils.isEmpty(path)) return;

        final RLottieAnimationView target = h.stickerView;
        if (!contentKey.equals(target.getTag())) return;

        // Re-verify inside post(): the holder may be rebound between the check
        // above and the runnable being dispatched.
        target.post(() -> {
            if (!contentKey.equals(target.getTag())) return;
            if (h.mediaRequestId != requestId) return;
            renderSticker(h, path, type);
        });
    }

    private void applyStickerSize(VH h, int w, int h2) {
        ViewGroup.LayoutParams lp = h.stickerView.getLayoutParams();
        lp.width = w;
        lp.height = h2;
        h.stickerView.setLayoutParams(lp);
    }

    private void renderSticker(VH h, String path, UiContent.StickerType type) {
        RLottieAnimationView lav = h.stickerView;

        if (type == UiContent.StickerType.VIDEO_WEBM) {
            renderVideoSticker(h, path);
            return;
        }

        if (type == UiContent.StickerType.STATIC) {
            releaseVideoDrawable(h);
            lav.cancelAnimation();
            lav.setVisibility(View.VISIBLE);
            Glide.with(lav).load(path).dontAnimate().into(lav);
        } else if (type == UiContent.StickerType.ANIMATED_TGS) {
            releaseVideoDrawable(h);
            renderTgsSticker(lav, path);
        }
    }

    private void renderTgsSticker(RLottieAnimationView lav, String path) {
        // Cancel any prior Glide load + rlottie animation. setTgsFile reads
        // and decompresses the .tgs on a background thread, so scrolling
        // stays smooth.
        Glide.with(lav).clear(lav);
        lav.cancelAnimation();
        lav.setVisibility(View.VISIBLE);
        lav.setRepeatCount(RLottieAnimationView.INFINITE);
        lav.setTgsFile(path, "tgs:" + path);
    }

    private void releaseVideoDrawable(VH h) {
        if (h.videoDrawable != null) {
            h.videoDrawable.release();
            h.videoDrawable = null;
        }
    }

    private void renderVideoSticker(VH h, String path) {
        final RLottieAnimationView view = h.stickerView;
        final Object key = view.getTag();
        final long requestId = h.mediaRequestId;
        final int w = Math.max(1, view.getLayoutParams().width);
        final int h2 = Math.max(1, view.getLayoutParams().height);

        VIDEO_EXEC.execute(() -> {
            VideoStickerDrawable d = new VideoStickerDrawable(path, "webm:" + path, w, h2);
            if (!d.isValid()) {
                d.release();
                return;
            }
            MAIN.post(() -> {
                if (h.mediaRequestId != requestId || !Objects.equals(key, view.getTag())) {
                    d.release();
                    return;
                }
                releaseVideoDrawable(h);
                h.videoDrawable = d;
                view.cancelAnimation();
                Glide.with(view).clear(view);
                view.setVisibility(View.VISIBLE);
                view.setImageDrawable(d);
                d.setRepeatCount(VideoStickerDrawable.INFINITE);
                d.start();
            });
        });
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
                if (theme != null) {
                    btnView.setTextColor(theme.onSecondaryContainerColor);
                    btnView.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            theme.secondaryContainerColor));
                }

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
            bindOptionalLabel(h.userName, item.senderName);
            bindOptionalLabel(h.groupChatUserTag, item.gcTag);
            if (h.emojiStatus != null) {
                h.emojiStatus.setEmojiStatus(item.senderEmojiCustomEmojiId);
            }
            bindIncomingAvatar(h, item);
        }

        if ((mask & PAYLOAD_TEXT) != 0) {
            bindMessageText(h, item);
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


    private int dp(View v, int dp) {
        return (int) (dp * v.getResources().getDisplayMetrics().density);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView userName;
        final TextView groupChatUserTag;
        final EmojiStatusView emojiStatus;
        final TextView text;
        final TextView time;
        final JustifiedLayout imageBoardTop;
        final JustifiedLayout imageBoardBottom;
        final RLottieAnimationView stickerView;
        VideoStickerDrawable videoDrawable;
        final ImageView avatar;
        final ViewGroup buttonsContainer;

        final View bubble;
        final android.graphics.drawable.Drawable bubbleBg;
        final int bubblePadL;
        final int bubblePadT;
        final int bubblePadR;
        final int bubblePadB;

        long mediaRequestId = 0L;

        VH(@NonNull View itemView) {
            super(itemView);

            itemView.setTag(R.id.tag_holder, this);

            userName = itemView.findViewById(R.id.username_tv);
            groupChatUserTag = itemView.findViewById(R.id.group_tag_tv);
            emojiStatus = itemView.findViewById(R.id.emoji_status_view);

            text = itemView.findViewById(R.id.tv_text);

            time = itemView.findViewById(R.id.tv_time);

            imageBoardTop = itemView.findViewById(R.id.image_board_top);
            imageBoardBottom = itemView.findViewById(R.id.image_board_bottom);

            stickerView = itemView.findViewById(R.id.sticker_view);

            avatar = itemView.findViewById(R.id.msg_avatar);

            buttonsContainer = itemView.findViewById(R.id.buttons_container);

            bubble = itemView.findViewById(R.id.bubble);
            if (bubble != null) {
                bubbleBg = bubble.getBackground();
                bubblePadL = bubble.getPaddingLeft();
                bubblePadT = bubble.getPaddingTop();
                bubblePadR = bubble.getPaddingRight();
                bubblePadB = bubble.getPaddingBottom();
            } else {
                bubbleBg = null;
                bubblePadL = bubblePadT = bubblePadR = bubblePadB = 0;
            }
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
            if (oldItem.senderEmojiCustomEmojiId != newItem.senderEmojiCustomEmojiId) return false;
            return buttonsEqual(oldItem.ui, newItem.ui);
        }

        @Override
        public Object getChangePayload(@NonNull MessageItem oldItem, @NonNull MessageItem newItem) {
            int mask = 0;
            if (!Objects.equals(oldItem.ui, newItem.ui)) mask |= PAYLOAD_TEXT | PAYLOAD_BUTTONS;
            if (!TextUtils.equals(oldItem.time, newItem.time)) mask |= PAYLOAD_TEXT;
            if (!Objects.equals(oldItem.photos, newItem.photos)) mask |= PAYLOAD_MEDIA;

            if (!TextUtils.equals(oldItem.senderName, newItem.senderName) ||
                    !TextUtils.equals(oldItem.gcTag, newItem.gcTag) ||
                    oldItem.senderAvatarFileId != newItem.senderAvatarFileId ||
                    oldItem.senderEmojiCustomEmojiId != newItem.senderEmojiCustomEmojiId) {
                mask |= PAYLOAD_USER_INFO;
            }
            return mask == 0 ? null : mask;
        }

        private static boolean buttonsEqual(UiContent a, UiContent b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            List<List<UiContent.UiButton>> aa = a.buttons;
            List<List<UiContent.UiButton>> bb = b.buttons;
            if (aa.size() != bb.size()) return false;

            for (int i = 0; i < aa.size(); ++i) {
                List<UiContent.UiButton> ra = aa.get(i);
                List<UiContent.UiButton> rb = bb.get(i);
                if (ra.size() != rb.size()) return false;
                for (int j = 0; j < ra.size(); ++j) {
                    UiContent.UiButton ba = ra.get(j);
                    UiContent.UiButton bb2 = rb.get(j);
                    if (!TextUtils.equals(ba.text, bb2.text) ||
                            !TextUtils.equals(ba.url, bb2.url) ||
                            !Arrays.equals(ba.data, bb2.data)) return false;
                }
            }
            return true;
        }
    };

    private void bindIncomingAvatar(VH h, MessageItem m) {
        if (h.avatar == null) return;

        int fid = m.senderAvatarFileId;
        if (fid == 0) {
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

        WeakReference<ImageView> weak = new WeakReference<>(h.avatar);
        TdMediaRepository.get().getPathOrRequest(fid, p -> {
            ImageView iv = weak.get();
            if (iv == null || !tag.equals(iv.getTag()) || TextUtils.isEmpty(p)) return;
            iv.post(() -> Glide.with(iv)
                    .load(p)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.bg_badge)
                    .error(R.drawable.bg_badge)
                    .into(iv));
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

        final int count = photos.size();
        configureImageLayoutMode(layout, count, bubbleWidth);
        ensureImagePool(layout);

        // Bind-time request id captured once: reject only callbacks for *previous* binds,
        // not other photos in the same album.
        final long bindReqId = h.mediaRequestId;

        for (int i = 0; i < MAX_PHOTO_POOL; ++i) {
            ImageView iv = (ImageView) layout.getChildAt(i);
            if (i >= count) {
                clearImageSlot(iv);
            } else {
                bindPhotoSlot(h, iv, photos.get(i), bindReqId);
            }
        }
    }

    private void configureImageLayoutMode(JustifiedLayout layout, int count, int bubbleWidth) {
        int targetHeight = (count == 1) ? bubbleWidth : dp(layout, 120);
        if (Objects.equals(layout.getTag(R.id.tag_layout_mode), targetHeight)) return;

        layout.setTag(R.id.tag_layout_mode, targetHeight);

        if (count == 1) {
            layout.setTargetRowHeightPx(bubbleWidth);
            layout.setRowHeightBoundsPx(dp(layout, 100), dp(layout, 450));
            layout.setJustifyLastRow(false);
        } else {
            layout.setTargetRowHeightPx(dp(layout, 120));
            layout.setRowHeightBoundsPx(dp(layout, 80), dp(layout, 200));
            layout.setJustifyLastRow(true);
        }
        layout.setSpacingPx(dp(layout, 2));
    }

    private void ensureImagePool(JustifiedLayout layout) {
        while (layout.getChildCount() < MAX_PHOTO_POOL) {
            ImageView iv = new ImageView(layout.getContext());
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setClipToOutline(true);
            iv.setOutlineProvider(new RoundedOutlineProvider(dp(layout, 10)));
            layout.addView(iv);
        }
    }

    private void clearImageSlot(ImageView iv) {
        Glide.with(iv).clear(iv);
        iv.setImageDrawable(null);
        iv.setTag(null);
        iv.setVisibility(View.GONE);
    }

    private void bindPhotoSlot(VH h, ImageView iv, PhotoData photo, long bindReqId) {
        iv.setVisibility(View.VISIBLE);

        JustifiedLayout.LayoutParams lp = (JustifiedLayout.LayoutParams) iv.getLayoutParams();
        if (lp == null || Math.abs(lp.aspectRatio - photo.aspectRatio) > 0.001f) {
            if (lp == null) lp = new JustifiedLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.aspectRatio = photo.aspectRatio;
            iv.setLayoutParams(lp);
        }

        String contentKey = (photo.fileId != 0) ? "remote_" + photo.fileId : "local_" + photo.localPath;
        if (Objects.equals(contentKey, iv.getTag())) return;

        iv.setTag(contentKey);

        String path = photo.localPath;
        if (TextUtils.isEmpty(path) && photo.fileId != 0) {
            path = TdMediaRepository.get().getCachedPath(photo.fileId);
        }

        if (!TextUtils.isEmpty(path)) {
            loadGlideImage(iv, path);
        } else if (photo.fileId != 0) {
            iv.setImageResource(R.drawable.bg_msg_bubble);
            requestPhoto(h, iv, photo.fileId, contentKey, bindReqId);
        }
    }

    private void requestPhoto(VH h, ImageView iv, int fileId, String contentKey, long bindReqId) {
        WeakReference<ImageView> weak = new WeakReference<>(iv);
        TdMediaRepository.get().getPathOrRequest(fileId, p -> {
            if (h.mediaRequestId != bindReqId) return;
            ImageView v = weak.get();
            if (v == null || !Objects.equals(v.getTag(), contentKey) || TextUtils.isEmpty(p)) return;
            v.post(() -> loadGlideImage(v, p));
        });
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

    private void bindGiftSticker(ImageView iv, SystemMessages.PremiumGift pg) {
        Glide.with(iv).clear(iv);
        iv.setImageResource(R.drawable.bg_badge);

        if (!TextUtils.isEmpty(pg.stickerPath)) {
            Glide.with(iv).load(pg.stickerPath).placeholder(R.drawable.bg_badge).error(R.drawable.bg_badge).into(iv);
            return;
        }

        int fid = pg.stickerFileId;
        if (fid == 0) return;

        iv.setTag(fid);
        WeakReference<ImageView> weak = new WeakReference<>(iv);

        TdMediaRepository.get().getPathOrRequest(fid, path -> {
            ImageView v = weak.get();
            if (v == null || !(v.getTag() instanceof Integer) || ((Integer) v.getTag()) != fid || TextUtils.isEmpty(path)) return;
            v.post(() -> Glide.with(v).load(path).placeholder(R.drawable.bg_badge).error(R.drawable.bg_badge).into(v));
        });
    }

    private int getAvatarFileIdForSender(MessageItem m) {
        return m.senderAvatarFileId;
    }

}
