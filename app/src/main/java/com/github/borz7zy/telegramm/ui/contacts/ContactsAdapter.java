package com.github.borz7zy.telegramm.ui.contacts;

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

        h.contactName.setText(item.name);
        h.contactLastOnlineTime.setText(item.lastOnline != null ? item.lastOnline : "");

        if (theme != null) {
            h.contactName.setTextColor(theme.onSurfaceColor);
            h.contactLastOnlineTime.setTextColor(theme.secondaryContainerColor);
        }

        Glide.with(h.avatar.getContext())
                .load(!TextUtils.isEmpty(item.avatarPath)
                        ? item.avatarPath
                        : TdMediaRepository.get().getCachedPath(item.avatarFileId))
                .apply(RequestOptions.circleCropTransform()
                        .placeholder(R.drawable.bg_badge)
                        .error(R.drawable.bg_badge))
                .into(h.avatar);
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
