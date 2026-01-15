package com.github.borz7zy.telegramm.ui.chat;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.ui.model.MessageItem;
import com.github.borz7zy.telegramm.ui.model.PhotoData;
import com.github.borz7zy.telegramm.ui.model.SystemMessages;
import com.github.borz7zy.telegramm.ui.widget.JustifiedLayout;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import java.util.List;

public class MessagesAdapter extends ListAdapter<MessageItem, RecyclerView.ViewHolder> {

    private static final int VT_IN = 0;
    private static final int VT_OUT = 1;
    private static final int VT_SYSTEM = 2;

    private int chatAvatarFileId = 0;

    public static final int PAYLOAD_TEXT = 1;
    public static final int PAYLOAD_MEDIA = 2;
    public static final int PAYLOAD_STATUS = 4;

    public MessagesAdapter() {
        super(DIFF);
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    @Override
    public int getItemViewType(int position) {
        MessageItem m = getItem(position);
        if (m.ui != null && m.ui.kind() == UiContent.Kind.SYSTEM) return VT_SYSTEM;
        return m.outgoing ? VT_OUT : VT_IN;
    }

    public int findPositionById(long id) {
        List<MessageItem> cur = getCurrentList();
        for (int i = 0; i < cur.size(); ++i) {
            if (cur.get(i).id == id) return i;
        }
        return -1;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());

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

        if (holder instanceof SystemVH sh) {
            UiContent.System sysUi = (UiContent.System) m.ui;

            sh.giftImage.setVisibility(View.GONE);
            sh.giftName.setVisibility(View.GONE);
            sh.comment.setVisibility(View.GONE);

            if (sysUi.messageType instanceof SystemMessages.PremiumGift pg) {
                String title = (m.ui instanceof UiContent.System s) ? s.text : "";
                sh.giftName.setText(title);
                sh.giftName.setVisibility(View.VISIBLE);

                sh.system.setText(pg.complete_caption);
                sh.system.setVisibility(View.VISIBLE);

                sh.comment.setText(pg.comment);
                sh.comment.setVisibility(View.VISIBLE);

                sh.giftImage.setVisibility(View.VISIBLE);
                bindGiftSticker(sh.giftImage, pg);

                return;
            }

            if (sysUi.messageType instanceof SystemMessages.Default) {
                String text = (m.ui instanceof UiContent.System s) ? s.text : "";
                sh.system.setText(text);
                sh.system.setVisibility(View.VISIBLE);
                return;
            }

            return;
        }

        VH h = (VH) holder;

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
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
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

        TdMediaRepository.get().getPathOrRequest(fid, path -> {
            Object tag = iv.getTag();
            if (!(tag instanceof Integer) || ((Integer) tag) != fid) return;
            if (TextUtils.isEmpty(path)) return;

            Glide.with(iv)
                    .load(path)
                    .placeholder(R.drawable.bg_badge)
                    .error(R.drawable.bg_badge)
                    .into(iv);
        });
    }

    private void bindImages(JustifiedLayout layout, List<PhotoData> photos) {
        layout.removeAllViews();

        if (photos == null || photos.isEmpty()) {
            layout.setVisibility(View.GONE);
            return;
        }

        layout.setVisibility(View.VISIBLE);

        int screenWidth = layout.getResources().getDisplayMetrics().widthPixels;
        int bubbleWidth = (int) (screenWidth * 0.80f);

        ViewGroup.LayoutParams params = layout.getLayoutParams();
        params.width = bubbleWidth;
        layout.setLayoutParams(params);

        int photoCount = photos.size();

        if (photoCount == 1) {
            layout.setTargetRowHeightPx(bubbleWidth);
            layout.setRowHeightBoundsPx(dp(layout, 100), dp(layout, 450));
            layout.setJustifyLastRow(false);
        } else {
            layout.setTargetRowHeightPx(dp(layout, 120));
            layout.setRowHeightBoundsPx(dp(layout, 80), dp(layout, 200));
            layout.setJustifyLastRow(true);
        }

        layout.setSpacingPx(dp(layout, 2));

        final int radius = dp(layout, 10);

        for (PhotoData photo : photos) {
            ImageView iv = new ImageView(layout.getContext());
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);

            JustifiedLayout.LayoutParams lp = new JustifiedLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.aspectRatio = photo.aspectRatio;
            iv.setLayoutParams(lp);
            layout.addView(iv);

            final int fid = photo.fileId;
            final int w = photo.width;
            final int h = photo.height;

            iv.setTag(fid);

            Glide.with(iv).clear(iv);

            String path = photo.localPath;
            if(TextUtils.isEmpty(path) && fid != 0){
                String cached2 = TdMediaRepository.get().getCachedPath(fid);
                if(!TextUtils.isEmpty(cached2)) path = cached2;
            }
            if (TextUtils.isEmpty(path)) {
                iv.setImageResource(R.drawable.bg_msg_bubble);

                if (fid != 0) {
                    TdMediaRepository.get().getPathOrRequest(fid, p -> {
                        Object tag = iv.getTag();
                        if (!(tag instanceof Integer) || ((Integer) tag) != fid) return;
                        if (TextUtils.isEmpty(p)) return;

                        Glide.with(iv)
                                .load(p)
                                .apply(new RequestOptions()
                                        .transform(new CenterCrop(), new RoundedCorners(radius)))
                                .placeholder(R.drawable.bg_msg_bubble)
                                .override(w, h)
                                .into(iv);
                    });
                }

                continue;
            }

            Glide.with(iv)
                    .load(path)
                    .apply(new RequestOptions()
                            .transform(new CenterCrop(), new RoundedCorners(radius)))
                    .placeholder(R.drawable.bg_msg_bubble)
                    .override(w, h)
                    .into(iv);
        }
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

        VH(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.tv_text);
            time = itemView.findViewById(R.id.tv_time);
            imageBoardTop = itemView.findViewById(R.id.image_board_top);
            imageBoardBottom = itemView.findViewById(R.id.image_board_bottom);

            avatar = findImageView(itemView, "msg_avatar", "message_avatar", "avatar", "iv_avatar");
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
            if (oldItem.outgoing != newItem.outgoing) return false;
            if (!TextUtils.equals(oldItem.time, newItem.time)) return false;
            if (oldItem.mediaAlbumId != newItem.mediaAlbumId) return false;

            if (oldItem.ui == null && newItem.ui != null) return false;
            if (oldItem.ui != null && !oldItem.ui.equals(newItem.ui)) return false;

            if (oldItem.photos.size() != newItem.photos.size()) return false;
            for (int i = 0; i < oldItem.photos.size(); i++) {
                PhotoData a = oldItem.photos.get(i);
                PhotoData b = newItem.photos.get(i);
                if (!TextUtils.equals(a.localPath, b.localPath)) return false;
                if (a.width != b.width || a.height != b.height) return false;
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

        Glide.with(h.avatar).clear(h.avatar);
        h.avatar.setImageResource(R.drawable.bg_badge);

        int fid = chatAvatarFileId;
        if (fid == 0) return;

        final String tag = "msg:" + m.chatId + ":" + fid + ":" + m.id;
        h.avatar.setTag(tag);

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

        TdMediaRepository.get().getPathOrRequest(fid, p -> {
            Object cur = h.avatar.getTag();
            if (!(cur instanceof String) || !tag.equals(cur)) return;
            if (TextUtils.isEmpty(p)) return;

            Glide.with(h.avatar)
                    .load(p)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.bg_badge)
                    .error(R.drawable.bg_badge)
                    .into(h.avatar);
        });
    }

}
