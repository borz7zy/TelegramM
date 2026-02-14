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
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.ui.ThemeEngine;
import com.github.borz7zy.telegramm.ui.model.ContactItem;
import com.github.borz7zy.telegramm.utils.TdMediaRepository;

import java.util.ArrayList;
import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.VH> {

    private ThemeEngine.Theme theme;

    public void setTheme(ThemeEngine.Theme theme){
        this.theme = theme;
    }

    public interface OnContactClickListener{
        void onContactClick(ContactItem item);
    }

    private OnContactClickListener clickListener;
    public void setOnContactClickListener(OnContactClickListener l){
        this.clickListener = l;
    }

    final ArrayList<ContactItem> items = new ArrayList<>();

    public void submitList(List<ContactItem> newList){
        items.clear();
        if (newList != null) items.addAll(newList);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).userId;
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
        ContactItem item = items.get(position);

        h.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onContactClick(item);
        });

        int nameColor = theme.onSurfaceColor;
        int statusColor = theme.onSecondaryContainerColor;
        int badgeColor = theme.primaryColor;

        h.contactName.setText(item.name);
        h.contactName.setTextColor(nameColor);

        h.contactLastOnlineTime.setText(item.lastOnline != null ? item.lastOnline : ""); // TODO
        h.contactLastOnlineTime.setTextColor(statusColor);

        bindAvatar(h.avatar, item.avatarFileId, item.avatarPath, badgeColor);
    }

    private void bindAvatar(ImageView iv, int fileId, String pathFromModel, int badgeColor) {
        if (iv == null) return;

        Glide.with(iv.getContext()).clear(iv);

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

        VH(@NonNull View itemView){
            super(itemView);

            contactName = itemView.findViewById(R.id.contact_name);
            contactLastOnlineTime = itemView.findViewById(R.id.contact_last_online_time);
            avatar = itemView.findViewById(R.id.contact_avatar);
        }
    }
}
