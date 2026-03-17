package com.github.borz7zy.telegramm.ui.dialogs;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.ui.ThemeEngine;
import com.github.borz7zy.telegramm.ui.model.FolderItem;

import java.util.List;

public class FoldersAdapter extends RecyclerView.Adapter<FoldersAdapter.VH> {

    public interface OnFolderSelectedListener {
        void onFolderSelected(FolderItem folder);
    }

    private OnFolderSelectedListener listener;
    private ThemeEngine.Theme theme;

    private final AsyncListDiffer<FolderItem> differ;

    public FoldersAdapter() {
        DiffUtil.ItemCallback<FolderItem> cb = new DiffUtil.ItemCallback<FolderItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull FolderItem a, @NonNull FolderItem b) {
                return a.id == b.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull FolderItem a, @NonNull FolderItem b) {
                return a.equals(b);
            }
        };
        differ = new AsyncListDiffer<>(this, cb);
        setHasStableIds(true);
    }

    public void submitList(List<FolderItem> list) {
        differ.submitList(list);
    }

    public List<FolderItem> getCurrentList() {
        return differ.getCurrentList();
    }

    public void setTheme(ThemeEngine.Theme theme) {
        this.theme = theme;
        notifyDataSetChanged();
    }

    public void setOnFolderSelectedListener(OnFolderSelectedListener l) {
        this.listener = l;
    }

    @Override
    public long getItemId(int position) {
        return differ.getCurrentList().get(position).id;
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_folder_tab, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FolderItem item = differ.getCurrentList().get(position);

        h.title.setText(item.title);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onFolderSelected(item);
        });

        if (theme == null) return;

        if (item.isSelected) {
            applyBackground(h.itemView, theme.primaryColor, 0xFF);
            h.title.setTextColor(theme.onPrimaryColor);
        } else {
            applyBackground(h.itemView, theme.secondaryContainerColor, 0xCC);
            h.title.setTextColor(theme.onSecondaryContainerColor);
        }
    }

    private static void applyBackground(View view, int color, int alpha) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(100f);

        int argb = (alpha << 24) | (color & 0x00FFFFFF);
        bg.setColor(argb);

        view.setBackground(bg);
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView title;

        VH(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.folder_title);
        }
    }
}