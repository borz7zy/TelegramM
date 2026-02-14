package com.github.borz7zy.telegramm.ui.chat;

import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class TopLoadingAdapter extends RecyclerView.Adapter<TopLoadingAdapter.VH> {
    private boolean visible;

    public TopLoadingAdapter(){}

    @Override
    public long getItemId(int position) {
        return -999L;
    }

    public void setVisible(boolean v) {
        if (visible == v) return;
        visible = v;
        if (visible) notifyItemInserted(0);
        else notifyItemRemoved(0);
    }

    public boolean isVisible() {
        return visible;
    }

    @Override
    public int getItemCount() {
        return visible ? 1 : 0;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout root = new FrameLayout(parent.getContext());
        root.setLayoutParams(new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        ProgressBar pb = new ProgressBar(parent.getContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.CENTER;
        int pad = (int) (parent.getResources().getDisplayMetrics().density * 12);
        root.setPadding(0, pad, 0, pad);
        root.addView(pb, lp);
        return new VH(root);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) { }

    static class VH extends RecyclerView.ViewHolder {
        VH(@NonNull FrameLayout itemView) { super(itemView); }
    }
}
