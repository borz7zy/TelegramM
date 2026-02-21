package com.github.borz7zy.telegramm.ui.dialogs;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.ui.ThemeEngine;
import com.github.borz7zy.telegramm.ui.model.DialogItem;
import com.github.borz7zy.telegramm.utils.Logger;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DialogsAdapter extends RecyclerView.Adapter<DialogsAdapter.VH> {

    private ThemeEngine.Theme theme;

    public interface OnDialogClickListener {
        void onDialogClick(DialogItem item);
    }

    public interface OnStartDragListener{
        void onStartDrag(RecyclerView.ViewHolder vh);
    }

    public interface OnTopicToggleListener {
        void onTopicToggle(DialogItem item);
        void onTopicClick(long chatId, TdApi.ForumTopic topic);
    }

    private OnDialogClickListener clickListener;
    private OnStartDragListener dragListener;
    private OnTopicToggleListener topicToggleListener;

    public void setOnDialogClickListener(OnDialogClickListener l) {
        this.clickListener = l;
    }
    public void setOnTopicToggleListener(OnTopicToggleListener l) { this.topicToggleListener = l; }

    public void setOnDragListener(OnStartDragListener l){
        dragListener = l;
    }

    public void setTheme(ThemeEngine.Theme theme){
        if (theme == null) throw new IllegalArgumentException("Theme cannot be null!");
        this.theme = theme;
        notifyDataSetChanged();
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
                        && TextUtils.equals(a.avatarPath, b.avatarPath)
                        && a.isForum == b.isForum
                        && a.isExpanded == b.isExpanded
                        && (Objects.equals(a.topics, b.topics) || (a.topics != null && a.topics.equals(b.topics)));
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

    @SuppressLint("ResourceAsColor")
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

        int nameColor = theme.onSurfaceColor;
        int messageColor = theme.onSecondaryContainerColor;
        int timeColor = theme.onSecondaryContainerColor;
        int badgeColor = theme.primaryColor;
        int textBadgeColor = theme.onPrimaryColor;

        h.name.setText(item.name);
        h.name.setTextColor(nameColor);
        h.time.setText(item.time);
        h.time.setTextColor(timeColor);

        if (item.isTyping) {
            h.message.setText("Печатает..."); // TODO
        } else {
            h.message.setText(item.text);
        }
        h.message.setTextColor(messageColor);

        if (item.unread > 0) {
            h.unread.setVisibility(View.VISIBLE);
            h.unread.setText(String.valueOf(item.unread));
            h.unread.setTextColor(textBadgeColor);

            Drawable bg = DrawableCompat.wrap(h.unread.getBackground().mutate());
            DrawableCompat.setTint(bg, badgeColor);
            h.unread.setBackground(bg);
        } else {
            h.unread.setVisibility(View.GONE);
        }

        bindAvatar(h.avatar, item.chatId, item.avatarFileId, item.avatarPath, badgeColor);

        if (item.isForum) {
            h.showTopicsBtn.setVisibility(View.VISIBLE);
            h.showTopicsBtn.setOnClickListener(v -> {
                if (topicToggleListener != null) topicToggleListener.onTopicToggle(item);
            });

            // Поворот стрелки
            h.showTopicsBtn.setRotation(item.isExpanded ? 180f : 0f);

            // Контейнер топиков
            if (item.isExpanded) {
                h.topicsContainer.setVisibility(View.VISIBLE);
                populateTopics(h.topicsContainer, item, nameColor, messageColor);
            } else {
                h.topicsContainer.setVisibility(View.GONE);
            }
        } else {
            h.showTopicsBtn.setVisibility(View.GONE);
            h.topicsContainer.setVisibility(View.GONE);
        }
    }

    private void populateTopics(LinearLayout container, DialogItem item, int titleColor, int msgColor) {
        container.removeAllViews();

        if (item.topics == null || item.topics.isEmpty()) {
            TextView loading = new TextView(container.getContext());
            loading.setText("");
            loading.setPadding(40, 10, 10, 10);
            loading.setTextColor(msgColor);
            loading.setTextSize(12);
            container.addView(loading);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(container.getContext());

        for (TdApi.ForumTopic topic : item.topics) {
            LinearLayout topicLayout = new LinearLayout(container.getContext());
            topicLayout.setOrientation(LinearLayout.VERTICAL);
            topicLayout.setPadding(50, 16, 16, 16);
            topicLayout.setBackgroundResource(R.drawable.bg_badge);
            topicLayout.setBackgroundTintList(ContextCompat.getColorStateList(container.getContext(), android.R.color.transparent));

            TextView title = new TextView(container.getContext());
            String topicName = topic.info.name;
            if (TextUtils.isEmpty(topicName)) topicName = "General"; // TODO

            title.setText("# " + topicName);
            title.setTextColor(titleColor);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            title.setTypeface(null, Typeface.BOLD);

            TextView lastMsg = new TextView(container.getContext());
            String msgText = "";
            if (topic.lastMessage != null && topic.lastMessage.content instanceof TdApi.MessageText) {
                msgText = ((TdApi.MessageText) topic.lastMessage.content).text.text;
            } else {
                msgText = "Media"; // TODO
            }
            lastMsg.setText(msgText);
            lastMsg.setTextColor(msgColor);
            lastMsg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            lastMsg.setMaxLines(1);
            lastMsg.setEllipsize(TextUtils.TruncateAt.END);

            topicLayout.addView(title);
            topicLayout.addView(lastMsg);

            topicLayout.setOnClickListener(v -> {
                if (topicToggleListener != null) {
                    topicToggleListener.onTopicClick(item.chatId, topic);
                }
            });

            container.addView(topicLayout);
        }
    }

    private void bindAvatar(ImageView iv, long chatId, int fileId, String pathFromModel, int badgeColor) {
        if (iv == null) return;

        Glide.with(iv).clear(iv);

        ShapeDrawable placeholder = new ShapeDrawable(new OvalShape());
        placeholder.getPaint().setColor(badgeColor);

        if (fileId == 0) {
            iv.setImageDrawable(placeholder);
            return;
        }

        final String tag = chatId + ":" + fileId;
        iv.setTag(tag);

        String path = !TextUtils.isEmpty(pathFromModel)
                ? pathFromModel
                : TdMediaRepository.get().getCachedPath(fileId);

        if (!TextUtils.isEmpty(path)) {
            Glide.with(iv)
                    .load(path)
                    .apply(RequestOptions.circleCropTransform()
                            .placeholder(placeholder)
                            .error(placeholder))
                    .into(iv);
            return;
        }

        TdMediaRepository.get().getPathOrRequest(fileId, p -> {
            Object cur = iv.getTag();
            if (!(cur instanceof String) || !tag.equals(cur)) return;
            if (TextUtils.isEmpty(p)) return;

            Glide.with(iv)
                    .load(p)
                    .apply(RequestOptions.circleCropTransform()
                            .placeholder(placeholder)
                            .error(placeholder))
                    .into(iv);
        });
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, time, message, unread;
        final ImageView avatar;
        final ImageButton showTopicsBtn;
        final LinearLayout topicsContainer;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.dialog_name);
            time = itemView.findViewById(R.id.tv_time);
            message = itemView.findViewById(R.id.dialog_message);
            unread = itemView.findViewById(R.id.badge_unread);

            avatar = findImageView(itemView,
                    "dialog_avatar", "avatar", "iv_avatar", "img_avatar", "image_avatar");

            showTopicsBtn = itemView.findViewById(R.id.btn_show_topics);
            topicsContainer = itemView.findViewById(R.id.topics_container);
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
