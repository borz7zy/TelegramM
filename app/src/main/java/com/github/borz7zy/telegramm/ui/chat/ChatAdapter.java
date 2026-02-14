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

import androidx.annotation.NonNull;
import androidx.paging.PagingDataAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.ui.model.MessageItem;
import com.github.borz7zy.telegramm.ui.model.PhotoData;
import com.github.borz7zy.telegramm.ui.model.SystemMessages;
import com.github.borz7zy.telegramm.ui.widget.JustifiedLayout;
import com.github.borz7zy.telegramm.utils.RoundedOutlineProvider;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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

    private static final int MAX_PHOTO_POOL = 10;

    public interface OnBtnClickListener {
        void onBtnClick(MessageItem item, UiContent.UiButton btn);
    }

    private OnBtnClickListener btnListener;

    public void setBtnListener(OnBtnClickListener listener) {
        this.btnListener = listener;
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

    public int findPositionById(long id) {
        List<MessageItem> cur = snapshot().getItems();
        for (int i = 0; i < cur.size(); ++i) {
            if (cur.get(i).id == id) return i;
        }
        return -1;
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

    private void bindUserMessage(VH h, MessageItem m) {
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
        bindImages(h.imageBoardTop, m.photos);
        bindIncomingAvatar(h, m);
        bindButtons(h, m);
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
            bindImages(h.imageBoardTop, item.photos);
        }

        if ((mask & PAYLOAD_STATUS) != 0) {
            // TODO: check marks, statuses
        }

        if ((mask & PAYLOAD_BUTTONS) != 0) {
            bindButtons(h, item);
        }
    }


    public void setChatAvatar(int fileId) {
        if (chatAvatarFileId == fileId) return;
        chatAvatarFileId = fileId;
        notifyDataSetChanged();
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

    private void bindImages(JustifiedLayout layout, List<PhotoData> photos) {
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
                if (iv.getVisibility() != View.GONE) {
                    iv.setVisibility(View.GONE);
                    Glide.with(iv).clear(iv);
                }
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

                WeakReference<ImageView> weakImg = new WeakReference<>(iv);
                final int reqFid = photo.fileId;
                final String reqKey = contentKey;

                TdMediaRepository.get().getPathOrRequest(reqFid, p -> {
                    ImageView v = weakImg.get();
                    if (v == null) return;

                    Object tag = v.getTag();
                    if (!Objects.equals(tag, reqKey)) return;

                    if (TextUtils.isEmpty(p)) return;

                    v.post(() -> loadGlideImage(v, p));
                });
            }
        }
    }

    private int getTagId(View v) {
        Object tag = v.getTag();
        return (tag instanceof Integer) ? (Integer) tag : 0;
    }

    private void loadGlideImage(ImageView iv, String path) {

        Glide.with(iv)
                .load(path)
                .centerCrop()
                .dontAnimate()
                .override(iv.getWidth(), iv.getHeight())
                .placeholder(R.drawable.bg_msg_bubble)
                .error(R.drawable.bg_msg_bubble)
                .into(iv);
    }

    private int dp(View v, int dp) {
        return (int) (dp * v.getResources().getDisplayMetrics().density);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView text;
        final TextView time;
        final JustifiedLayout imageBoardTop;
        final JustifiedLayout imageBoardBottom;
        final ImageView avatar;
        final ViewGroup buttonsContainer;

        VH(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.tv_text);
            time = itemView.findViewById(R.id.tv_time);
            imageBoardTop = itemView.findViewById(R.id.image_board_top);
            imageBoardBottom = itemView.findViewById(R.id.image_board_bottom);

            avatar = findImageView(itemView, "msg_avatar", "message_avatar", "avatar", "iv_avatar");

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

            return mask == 0 ? null : mask;
        }

        private static boolean photosEqual(List<PhotoData> a, List<PhotoData> b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); ++i) {
                PhotoData pa = a.get(i);
                PhotoData pb = b.get(i);
                if (pa.fileId != pb.fileId) return false;
                if (pa.width != pb.width || pa.height != pb.height) return false;
                if (!TextUtils.equals(pa.localPath, pb.localPath)) return false;
            }
            return true;
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

    private static ImageView findImageView(View root, String... names) {
        String pkg = root.getContext().getPackageName();
        for (String n : names) {
            int id = root.getResources().getIdentifier(n, "id", pkg);
            if (id != 0) {
                View v = root.findViewById(id);
                if (v instanceof ImageView) return (ImageView) v;
            }
        }
        return null;
    }

    private void bindIncomingAvatar(VH h, MessageItem m) {
        if (h.avatar == null) return;

        if (m.outgoing) {
            h.avatar.setVisibility(View.GONE);
            return;
        }

        h.avatar.setVisibility(View.VISIBLE);

        int fid = chatAvatarFileId;
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

}
