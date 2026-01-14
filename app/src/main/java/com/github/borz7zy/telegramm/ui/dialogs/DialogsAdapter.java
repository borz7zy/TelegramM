package com.github.borz7zy.telegramm.ui.dialogs;

import android.graphics.Color;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.ui.model.DialogItem;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

public class DialogsAdapter extends ListAdapter<DialogItem, DialogsAdapter.VH> {

    public interface OnDialogClickListener {
        void onDialogClick(DialogItem item);
    }

    private OnDialogClickListener clickListener;

    public void setOnDialogClickListener(OnDialogClickListener l) {
        this.clickListener = l;
    }

    private static final DiffUtil.ItemCallback<DialogItem> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull DialogItem oldItem, @NonNull DialogItem newItem) {
            return oldItem.chatId == newItem.chatId;
        }

        @Override
        public boolean areContentsTheSame(@NonNull DialogItem oldItem, @NonNull DialogItem newItem) {
            return oldItem.order == newItem.order
                    && TextUtils.equals(oldItem.name, newItem.name)
                    && TextUtils.equals(oldItem.text, newItem.text)
                    && TextUtils.equals(oldItem.time, newItem.time)
                    && oldItem.unread == newItem.unread
                    && oldItem.isTyping == newItem.isTyping
                    && oldItem.avatarFileId == newItem.avatarFileId
                    && TextUtils.equals(oldItem.avatarPath, newItem.avatarPath);
        }
    };

    public DialogsAdapter() {
        super(DIFF);
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).chatId;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DialogItem item = getItem(position);

        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onDialogClick(item);
        });

        h.itemView.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return true;
        });

        h.name.setText(item.name);
        h.time.setText(item.time);

        if (item.isTyping) {
            h.message.setText("Печатает...");
            h.message.setTextColor(ContextCompat.getColor(h.itemView.getContext(), R.color.tg_primary));
        } else {
            h.message.setText(item.text);
            h.message.setTextColor(Color.GRAY);
        }

        if (item.unread > 0) {
            h.unread.setVisibility(View.VISIBLE);
            h.unread.setText(String.valueOf(item.unread));
        } else {
            h.unread.setVisibility(View.GONE);
        }

        bindAvatar(h.avatar, item.chatId, item.avatarFileId, item.avatarPath);
    }

    private void bindAvatar(ImageView iv, long chatId, int fileId, String pathFromModel) {
        if (iv == null) return;

        Glide.with(iv).clear(iv);
        iv.setImageResource(R.drawable.bg_badge);

        if (fileId == 0) return;

        final String tag = chatId + ":" + fileId;
        iv.setTag(tag);

        String path = !TextUtils.isEmpty(pathFromModel)
                ? pathFromModel
                : TdMediaRepository.get().getCachedPath(fileId);

        if (!TextUtils.isEmpty(path)) {
            Glide.with(iv)
                    .load(path)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.bg_badge)
                    .error(R.drawable.bg_badge)
                    .into(iv);
            return;
        }

        TdMediaRepository.get().getPathOrRequest(fileId, p -> {
            Object cur = iv.getTag();
            if (!(cur instanceof String) || !tag.equals(cur)) return;
            if (TextUtils.isEmpty(p)) return;

            Glide.with(iv)
                    .load(p)
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.bg_badge)
                    .error(R.drawable.bg_badge)
                    .into(iv);
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, time, message, unread;
        final ImageView avatar;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.dialog_name);
            time = itemView.findViewById(R.id.tv_time);
            message = itemView.findViewById(R.id.dialog_message);
            unread = itemView.findViewById(R.id.badge_unread);

            avatar = findImageView(itemView,
                    "dialog_avatar", "avatar", "iv_avatar", "img_avatar", "image_avatar");
        }
    }

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
}
