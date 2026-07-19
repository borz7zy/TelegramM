package com.github.borz7zy.telegramm.ui.contacts;

import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.borz7zy.shadowgram.shadowgramui.R;
import com.github.borz7zy.telegramm.core.theme.ThemeEngine;
import com.github.borz7zy.telegramm.ui.model.ContactItem;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import java.util.Collections;
import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.VH> {

    private ThemeEngine.Theme theme;

    public interface OnContactClickListener {
        void onContactClick(ContactItem item);
    }

    private OnContactClickListener clickListener;

    private final AsyncListDiffer<ContactItem> differ;

    public ContactsAdapter() {
        DiffUtil.ItemCallback<ContactItem> cb = new DiffUtil.ItemCallback<ContactItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull ContactItem a, @NonNull ContactItem b) {
                return a.userId == b.userId;
            }

            @Override
            public boolean areContentsTheSame(@NonNull ContactItem a, @NonNull ContactItem b) {
                return a.equals(b);
            }
        };
        differ = new AsyncListDiffer<>(this, cb);
        setHasStableIds(true);
    }

    public void setTheme(ThemeEngine.Theme theme) {
        if (this.theme == theme) return;
        this.theme = theme;
        notifyItemRangeChanged(0, getItemCount());
    }

    public void setOnContactClickListener(OnContactClickListener l) {
        this.clickListener = l;
    }

    public void submitList(List<ContactItem> newList) {
        differ.submitList(newList == null ? Collections.emptyList() : newList);
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    @Override
    public long getItemId(int position) {
        return differ.getCurrentList().get(position).userId;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ContactItem item = differ.getCurrentList().get(position);

        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onContactClick(item);
        });

        h.contactName.setText(item.name);
        h.contactLastOnlineTime.setText(item.lastOnline != null ? item.lastOnline : "");

        // Theme is delivered asynchronously; setTheme() will trigger a rebind.
        int badgeColor = (theme != null) ? theme.primaryColor : 0xFF888888;
        if (theme != null) {
            h.contactName.setTextColor(theme.onSurfaceColor);
            h.contactLastOnlineTime.setTextColor(theme.onSecondaryContainerColor);
        }

        bindAvatar(h.avatar, item.userId, item.avatarFileId, item.avatarPath, badgeColor);
    }

    private void bindAvatar(ImageView iv, long userId, int fileId, String pathFromModel, int badgeColor) {
        if (iv == null) return;

        Glide.with(iv).clear(iv);

        ShapeDrawable placeholder = new ShapeDrawable(new OvalShape());
        placeholder.getPaint().setColor(badgeColor);

        if (fileId == 0) {
            iv.setTag(null);
            iv.setImageDrawable(placeholder);
            return;
        }

        final String tag = "contact:" + userId + ":" + fileId;
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
            // Reject the callback if this view was rebound to a different contact in the meantime.
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
        final TextView contactName, contactLastOnlineTime;
        final ImageView avatar;

        VH(@NonNull View itemView) {
            super(itemView);

            contactName = itemView.findViewById(R.id.contact_name);
            contactLastOnlineTime = itemView.findViewById(R.id.contact_last_online_time);
            avatar = itemView.findViewById(R.id.contact_avatar);
        }
    }
}