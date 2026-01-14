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
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.ui.model.DialogItem;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import java.util.ArrayList;
import java.util.List;

public class DialogsAdapter extends RecyclerView.Adapter<DialogsAdapter.VH> {

    public interface OnDialogClickListener {
        void onDialogClick(DialogItem item);
    }

    public interface OnStartDragListener{
        void onStartDrag(RecyclerView.ViewHolder vh);
    }

    private OnDialogClickListener clickListener;
    private OnStartDragListener dragListener;

    public void setOnDialogClickListener(OnDialogClickListener l) {
        this.clickListener = l;
    }

    public void setOnDragListener(OnStartDragListener l){
        dragListener = l;
    }

    private final ArrayList<DialogItem> items = new ArrayList<>();

    public DialogItem getItem(int position){
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).chatId;
    }

    @Override
    public int getItemCount(){
        return items.size();
    }

    public DialogsAdapter(){
        setHasStableIds(true);
    }

    // DiffUtil
    public void submitList(List<DialogItem> newList){
        final ArrayList<DialogItem> old = new ArrayList<>(items);

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback(){
            @Override
            public int getOldListSize(){
                return old.size();
            }

            @Override
            public int getNewListSize(){
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return old.get(oldItemPosition).chatId == newList.get(newItemPosition).chatId;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                DialogItem a = old.get(oldItemPosition), b = newList.get(newItemPosition);
                return a.order == b.order
                        && a.isPinned == b.isPinned
                        && TextUtils.equals(a.name, b.name)
                        && TextUtils.equals(a.text, b.text)
                        && TextUtils.equals(a.time, b.time)
                        && a.unread == b.unread
                        && a.isTyping == b.isTyping
                        && a.avatarFileId == b.avatarFileId
                        && TextUtils.equals(a.avatarPath, b.avatarPath);
            }
        });

        items.clear();
        items.addAll(newList);
        diff.dispatchUpdatesTo(this);
    }

    public boolean movePinned(int from, int to){
        if (from < 0 || to < 0 || from >= items.size() || to >= items.size()) return false;
        if (!items.get(from).isPinned || !items.get(to).isPinned) return false;

        DialogItem moved = items.remove(from);
        items.add(to, moved);
        notifyItemMoved(from, to);
        return true;
    }

    public ArrayList<Long> getPinnedIdsInUiOrder() {
        ArrayList<Long> res = new ArrayList<>();
        for (DialogItem it : items) {
            if (it.isPinned) res.add(it.chatId);
            else break;
        }
        return res;
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
            if (!item.isPinned) return false;
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (dragListener != null) dragListener.onStartDrag(h);
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
