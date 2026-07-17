package com.github.borz7zy.telegramm.ui.dialogs;

import android.annotation.SuppressLint;
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
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.ui.ThemeEngine;
import com.github.borz7zy.telegramm.ui.model.DialogItem;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import org.drinkless.tdlib.TdApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DialogsAdapter extends RecyclerView.Adapter<DialogsAdapter.VH> {

    // Payload bit flags returned from getChangePayload so onBindViewHolder
    // can repaint only the changed parts of an existing row instead of the
    // whole item view (which causes visible flashes during typing / unread
    // / order updates).
    private static final int P_NAME    = 1;
    private static final int P_MESSAGE = 1 << 1; // text + isTyping
    private static final int P_TIME    = 1 << 2;
    private static final int P_UNREAD  = 1 << 3;
    private static final int P_AVATAR  = 1 << 4;
    private static final int P_FORUM   = 1 << 5; // isForum, isExpanded, topics
    private static final int P_PINNED  = 1 << 6;

    private ThemeEngine.Theme theme;
    private final AsyncListDiffer<DialogItem> differ;

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
        notifyDataSetChanged(); // TODO: optimize
    }
    public DialogItem getItem(int position){
        return differ.getCurrentList().get(position);
    }

    @Override
    public long getItemId(int position) {
        return differ.getCurrentList().get(position).chatId;
    }

    @Override
    public int getItemCount(){
        return differ.getCurrentList().size();
    }

    public interface OnFirstDiffDoneListener {
        void onFirstDiffDone();
    }

    private OnFirstDiffDoneListener firstDiffListener;
    private boolean firstDiffDone = false;

    public void setOnFirstDiffDoneListener(OnFirstDiffDoneListener l) {
        this.firstDiffListener = l;
    }

    public void resetFirstDiff() {
        firstDiffDone = false;
    }

    public DialogsAdapter(){
        DiffUtil.ItemCallback<DialogItem> diffCallback =
                new DiffUtil.ItemCallback<DialogItem>() {

                    @Override
                    public boolean areItemsTheSame(@NonNull DialogItem oldItem,
                                                   @NonNull DialogItem newItem) {
                        return oldItem.chatId == newItem.chatId;
                    }

                    @Override
                    public boolean areContentsTheSame(@NonNull DialogItem oldItem,
                                                      @NonNull DialogItem newItem) {

                        return oldItem.order == newItem.order
                                && oldItem.isPinned == newItem.isPinned
                                && TextUtils.equals(oldItem.name, newItem.name)
                                && TextUtils.equals(oldItem.text, newItem.text)
                                && TextUtils.equals(oldItem.time, newItem.time)
                                && oldItem.unread == newItem.unread
                                && oldItem.isTyping == newItem.isTyping
                                && oldItem.avatarFileId == newItem.avatarFileId
                                && TextUtils.equals(oldItem.avatarPath, newItem.avatarPath)
                                && oldItem.isForum == newItem.isForum
                                && oldItem.isExpanded == newItem.isExpanded
                                && Objects.equals(oldItem.topics, newItem.topics);
                    }

                    @Override
                    public Object getChangePayload(@NonNull DialogItem oldItem,
                                                   @NonNull DialogItem newItem) {
                        int mask = 0;
                        if (!TextUtils.equals(oldItem.name, newItem.name)) mask |= P_NAME;
                        if (!TextUtils.equals(oldItem.text, newItem.text)
                                || oldItem.isTyping != newItem.isTyping) mask |= P_MESSAGE;
                        if (!TextUtils.equals(oldItem.time, newItem.time)) mask |= P_TIME;
                        if (oldItem.unread != newItem.unread) mask |= P_UNREAD;
                        if (oldItem.avatarFileId != newItem.avatarFileId
                                || !TextUtils.equals(oldItem.avatarPath, newItem.avatarPath)) {
                            mask |= P_AVATAR;
                        }
                        if (oldItem.isForum != newItem.isForum
                                || oldItem.isExpanded != newItem.isExpanded
                                || !Objects.equals(oldItem.topics, newItem.topics)) {
                            mask |= P_FORUM;
                        }
                        if (oldItem.isPinned != newItem.isPinned) mask |= P_PINNED;
                        // mask 0 means areContentsTheSame would have returned true;
                        // returning null falls through to a full rebind which we
                        // never want here.
                        return mask == 0 ? null : Integer.valueOf(mask);
                    }
                };

        differ = new AsyncListDiffer<>(this, diffCallback);
        differ.addListListener((previousList, currentList) -> {
            if (!firstDiffDone && !currentList.isEmpty()) {
                firstDiffDone = true;
                if (firstDiffListener != null) firstDiffListener.onFirstDiffDone();
            }
        });
        setHasStableIds(true);
    }

    public void submitList(List<DialogItem> newList) {
        differ.submitList(newList);
    }

    public boolean movePinned(int from, int to) {

        List<DialogItem> current = differ.getCurrentList();

        if (from < 0 || to < 0 || from >= current.size() || to >= current.size())
            return false;

        if (!current.get(from).isPinned || !current.get(to).isPinned)
            return false;

        ArrayList<DialogItem> newList = new ArrayList<>(current);

        DialogItem moved = newList.remove(from);
        newList.add(to, moved);

        differ.submitList(newList);

        return true;
    }

    public ArrayList<Long> getPinnedIdsInUiOrder() {

        ArrayList<Long> res = new ArrayList<>();

        List<DialogItem> current = differ.getCurrentList();

        for (DialogItem it : current) {
            if (it.isPinned)
                res.add(it.chatId);
            else
                break;
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
        bindFull(h, differ.getCurrentList().get(position));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            bindFull(h, differ.getCurrentList().get(position));
            return;
        }
        int mask = 0;
        for (Object p : payloads) {
            if (p instanceof Integer) mask |= (Integer) p;
        }
        DialogItem item = differ.getCurrentList().get(position);
        bindPartial(h, item, mask);
    }

    private void bindFull(@NonNull VH h, @NonNull DialogItem item) {
        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onDialogClick(item);
        });

        h.itemView.setOnLongClickListener(v -> {
            if (!item.isPinned) return false;
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (dragListener != null) dragListener.onStartDrag(h);
            return true;
        });

        // Theme arrives asynchronously; setTheme() will trigger a rebind once available.
        if (theme == null) {
            h.name.setText(item.name);
            h.time.setText(item.time);
            h.message.setText(item.isTyping ? "Печатает..." : item.text);
            h.unread.setVisibility(item.unread > 0 ? View.VISIBLE : View.GONE);
            if (item.unread > 0) h.unread.setText(String.valueOf(item.unread));
            h.showTopicsBtn.setVisibility(View.GONE);
            h.topicsContainer.setVisibility(View.GONE);
            return;
        }

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

        applyUnread(h, item, badgeColor, textBadgeColor);

        bindAvatar(h.avatar, item.chatId, item.avatarFileId, item.avatarPath, badgeColor);

        applyForum(h, item, nameColor, messageColor);
    }

    private void bindPartial(@NonNull VH h, @NonNull DialogItem item, int mask) {
        // Click listeners can change identity-by-item, refresh cheaply.
        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onDialogClick(item);
        });
        h.itemView.setOnLongClickListener(v -> {
            if (!item.isPinned) return false;
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (dragListener != null) dragListener.onStartDrag(h);
            return true;
        });

        if (theme == null) {
            // Without a theme we can't tint; full bind path covers the
            // text-only fallback already.
            bindFull(h, item);
            return;
        }
        int nameColor = theme.onSurfaceColor;
        int messageColor = theme.onSecondaryContainerColor;
        int badgeColor = theme.primaryColor;
        int textBadgeColor = theme.onPrimaryColor;

        if ((mask & P_NAME) != 0) h.name.setText(item.name);
        if ((mask & P_MESSAGE) != 0) {
            h.message.setText(item.isTyping ? "Печатает..." : item.text);
        }
        if ((mask & P_TIME) != 0) h.time.setText(item.time);
        if ((mask & P_UNREAD) != 0) applyUnread(h, item, badgeColor, textBadgeColor);
        if ((mask & P_AVATAR) != 0) {
            bindAvatar(h.avatar, item.chatId, item.avatarFileId, item.avatarPath, badgeColor);
        }
        if ((mask & P_FORUM) != 0) applyForum(h, item, nameColor, messageColor);
        // P_PINNED currently only flips the long-press behaviour, already
        // wired through the listener above.
    }

    private void applyUnread(@NonNull VH h, @NonNull DialogItem item,
                             int badgeColor, int textBadgeColor) {
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
    }

    private void applyForum(@NonNull VH h, @NonNull DialogItem item,
                            int nameColor, int messageColor) {
        if (item.isForum) {
            h.showTopicsBtn.setVisibility(View.VISIBLE);
            h.showTopicsBtn.setOnClickListener(v -> {
                if (topicToggleListener != null) topicToggleListener.onTopicToggle(item);
            });
            h.showTopicsBtn.setRotation(item.isExpanded ? 180f : 0f);
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

        // Skip when the holder is already showing this avatar — avoids a
        // visible flash to the placeholder while Glide re-fetches the same
        // file on every state change (typing, unread, time, etc.).
        final String tag = (fileId == 0) ? ("empty:" + chatId) : (chatId + ":" + fileId);
        Object existing = iv.getTag();
        if (tag.equals(existing) && iv.getDrawable() != null) return;

        Glide.with(iv).clear(iv);
        iv.setTag(tag);

        ShapeDrawable placeholder = new ShapeDrawable(new OvalShape());
        placeholder.getPaint().setColor(badgeColor);

        if (fileId == 0) {
            iv.setImageDrawable(placeholder);
            return;
        }

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
