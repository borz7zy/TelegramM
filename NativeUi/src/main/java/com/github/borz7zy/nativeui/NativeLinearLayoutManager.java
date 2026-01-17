package com.github.borz7zy.nativeui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.util.SparseIntArray;

public class NativeLinearLayoutManager extends RecyclerView.LayoutManager {

    private RecyclerView rv;

    private long handle = 0L;

    private static final int MAX_VISIBLE_ITEMS = 256;
    private final int[] snapshotBuf = new int[5 + MAX_VISIBLE_ITEMS * 3];

    private int lastItemCount = -1;
    private int lastAppliedGeneration = -1;

    private int lastVW = -1, lastVH = -1;
    private int lastContentH = 0;

    private int lastScroll = 0;
    private int lastMaxScroll = 0;

    private int lastPadTop = -1, lastPadBottom = -1;

    private boolean stackFromEnd = false;
    private boolean didInitialStackFromEnd = false;

    private final SparseIntArray heightByPos = new SparseIntArray();
    private int estimatedItemHeight = 200;

    private int pendingPos = RecyclerView.NO_POSITION;
    private int pendingOffsetRv = 0;
    private boolean pendingAlignBottom = false;

    private boolean relayoutPosted = false;

    public NativeLinearLayoutManager(Context context) {}

    public void setStackFromEnd(boolean v) {
        if (stackFromEnd == v) return;
        stackFromEnd = v;
        didInitialStackFromEnd = false;
        requestLayoutSafely();
    }

    public int findFirstVisibleItemPosition() {
        View v = findTopChild();
        if (v == null) return RecyclerView.NO_POSITION;
        return getPosition(v);
    }

    public int findLastVisibleItemPosition() {
        View v = findBottomChild();
        if (v == null) return RecyclerView.NO_POSITION;
        return getPosition(v);
    }

    @Override
    public View findViewByPosition(int position) {
        final int c = getChildCount();
        for (int i = 0; i < c; i++) {
            View child = getChildAt(i);
            if (child != null && getPosition(child) == position) return child;
        }
        return null;
    }

    public void scrollToPositionWithOffset(int position, int offset) {
        pendingPos = position;
        pendingOffsetRv = offset;
        pendingAlignBottom = false;

        int padTop = getPaddingTop();
        int approx = position * Math.max(1, estimatedItemHeight) - (offset - padTop);
        if (handle != 0L) NativeBridge.nativeSetScroll(handle, approx);

        requestLayoutSafely();
    }

    @Override
    public void scrollToPosition(int position) {
        if (stackFromEnd) {
            pendingPos = position;
            pendingAlignBottom = true;
            pendingOffsetRv = 0;
            if (handle != 0L) NativeBridge.nativeSetScroll(handle, Integer.MAX_VALUE / 2);
            requestLayoutSafely();
        } else {
            scrollToPositionWithOffset(position, getPaddingTop());
        }
    }

    public void destroy() {
        if (handle != 0L) {
            NativeBridge.nativeDestroy(handle);
            handle = 0L;
        }
    }

    @Override
    public void onAttachedToWindow(@NonNull RecyclerView view) {
        super.onAttachedToWindow(view);
        rv = view;
        rv.setClipToPadding(false);

        if (handle == 0L) {
            handle = NativeBridge.nativeCreate();
            lastAppliedGeneration = -1;
            lastItemCount = -1;
        }
    }

    @Override
    public void onDetachedFromWindow(@NonNull RecyclerView view, @NonNull RecyclerView.Recycler recycler) {
        super.onDetachedFromWindow(view, recycler);
        rv = null;
        relayoutPosted = false;

        destroy();
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    @Override public boolean canScrollVertically() { return true; }
    @Override public boolean supportsPredictiveItemAnimations() { return false; }

    private void requestLayoutSafely() {
        final RecyclerView local = rv;
        if (local == null) return;
        if (!local.isAttachedToWindow()) return;
        local.requestLayout();
    }

    private void postRelayout() {
        final RecyclerView localRv = rv;
        if (localRv == null || relayoutPosted) return;

        relayoutPosted = true;
        localRv.postOnAnimation(() -> {
            relayoutPosted = false;
            if (rv != localRv) return;
            if (!localRv.isAttachedToWindow()) return;
            localRv.requestLayout();
        });
    }

    private int indexOfChildCompat(View child) {
        final int c = getChildCount();
        for (int i = 0; i < c; i++) {
            if (getChildAt(i) == child) return i;
        }
        return -1;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (dy == 0 || handle == 0L) return 0;

        int consumed = dy;
        if (dy < 0 && lastScroll <= 0) consumed = 0;
        else if (dy > 0 && lastScroll >= lastMaxScroll) consumed = 0;
        if (consumed == 0) return 0;

        lastScroll = clamp(lastScroll + consumed, 0, Math.max(0, lastMaxScroll));

        NativeBridge.nativeSubmitScroll(handle, consumed);
        offsetChildrenVertical(-consumed);

        boolean applied = applySnapshot(recycler, state);
        if (!applied) postRelayout();

        return consumed;
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (handle == 0L) return;

        if (state.isPreLayout()) {
            return;
        }

        int adapterCount = state.getItemCount();
        if (adapterCount == 0) {
            removeAndRecycleAllViews(recycler);
            lastItemCount = 0;
            lastScroll = 0;
            lastMaxScroll = 0;
            lastAppliedGeneration = -1;
            didInitialStackFromEnd = false;
            return;
        }

        int w = getWidth(), h = getHeight();
        int padTop = getPaddingTop();
        int padBottom = getPaddingBottom();

        int contentW = Math.max(0, w - getPaddingLeft() - getPaddingRight());
        int contentH = Math.max(0, h - padTop - padBottom);
        lastContentH = contentH;

        if (w != lastVW || h != lastVH) {
            lastVW = w; lastVH = h;
            NativeBridge.nativeSetViewport(handle, contentW, contentH);
        }

        if (padTop != lastPadTop || padBottom != lastPadBottom) {
            lastPadTop = padTop;
            lastPadBottom = padBottom;
            NativeBridge.nativeSetInsets(handle, padTop, padBottom);
        }

        if (adapterCount != lastItemCount) {
            lastItemCount = adapterCount;
            NativeBridge.nativeSetItemCount(handle, adapterCount);
        }

        if (stackFromEnd && !didInitialStackFromEnd && adapterCount > 0) {
            didInitialStackFromEnd = true;
            pendingPos = adapterCount - 1;
            pendingAlignBottom = true;
            NativeBridge.nativeSetScroll(handle, Integer.MAX_VALUE / 2);
        }

        boolean applied = applySnapshot(recycler, state);
        if (!applied) {
            postRelayout();
            return;
        }

        applyPendingAdjust();
    }

    private boolean applySnapshot(RecyclerView.Recycler recycler, RecyclerView.State state) {
        int gen = NativeBridge.nativeCopyLayoutSnapshot(handle, snapshotBuf);
        if (gen == 0 || gen == lastAppliedGeneration) return false;
        lastAppliedGeneration = gen;

        lastScroll = snapshotBuf[1];
        lastMaxScroll = snapshotBuf[2];
        int itemCountFromNative = snapshotBuf[3];
        int visibleCount = snapshotBuf[4];

        int adapterCount = state.getItemCount();
        int safeCount = Math.min(adapterCount, itemCountFromNative);

        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child == null) continue;
            int pos = getPosition(child);
            if (!isRequiredPosition(pos, visibleCount)) {
                removeAndRecycleView(child, recycler);
            }
        }

        final int left = getPaddingLeft();
        final int right = getWidth() - getPaddingRight();
        final int padTop = getPaddingTop();

        int insertIndex = 0;

        for (int i = 0; i < visibleCount; i++) {
            int base = 5 + i * 3;
            int position = snapshotBuf[base];
            int top = snapshotBuf[base + 1];

            if (position < 0 || position >= safeCount) continue;

            View view = findViewByPosition(position);
            if (view == null) {
                view = recycler.getViewForPosition(position);
                addView(view, insertIndex);
            } else {
                int curIndex = indexOfChildCompat(view);
                if (curIndex != -1 && curIndex != insertIndex) {
                    detachViewAt(curIndex);
                    attachView(view, insertIndex);
                }
            }

            measureChildWithMargins(view, 0, 0);

            int hPx = getDecoratedMeasuredHeight(view);
            if (hPx > 0) {
                int prev = heightByPos.get(position, 0);
                if (prev != hPx) {
                    heightByPos.put(position, hPx);
                    NativeBridge.nativeSetItemHeight(handle, position, hPx);

                    if (estimatedItemHeight <= 0) estimatedItemHeight = hPx;
                    estimatedItemHeight = (estimatedItemHeight * 3 + hPx) / 4;
                }
            }

            int bottom = top + Math.max(1, getDecoratedMeasuredHeight(view));
            layoutDecorated(view, left, padTop + top, right, padTop + bottom);

            insertIndex++;
        }

        return true;
    }

    private boolean isRequiredPosition(int pos, int visibleCount) {
        for (int i = 0; i < visibleCount; i++) {
            int p = snapshotBuf[5 + i * 3];
            if (p == pos) return true;
        }
        return false;
    }

    private void applyPendingAdjust() {
        if (pendingPos == RecyclerView.NO_POSITION) return;

        View v = findViewByPosition(pendingPos);
        if (v == null) {
            postRelayout();
            return;
        }

        int desiredTopRv;
        if (pendingAlignBottom) {
            int h = getDecoratedMeasuredHeight(v);
            desiredTopRv = getPaddingTop() + Math.max(0, lastContentH - h);
        } else {
            desiredTopRv = pendingOffsetRv;
        }

        int curTop = getDecoratedTop(v);
        int dy = curTop - desiredTopRv;
        if (dy != 0 && handle != 0L) {
            lastScroll = clamp(lastScroll + dy, 0, Math.max(0, lastMaxScroll));
            NativeBridge.nativeSubmitScroll(handle, dy);
            offsetChildrenVertical(-dy);
            postRelayout();
        }

        pendingPos = RecyclerView.NO_POSITION;
        pendingAlignBottom = false;
    }

    @Override
    public int computeVerticalScrollOffset(RecyclerView.State state) {
        return Math.max(0, lastScroll);
    }

    @Override
    public int computeVerticalScrollExtent(RecyclerView.State state) {
        return Math.max(0, lastContentH);
    }

    @Override
    public int computeVerticalScrollRange(RecyclerView.State state) {
        return Math.max(0, lastMaxScroll + Math.max(0, lastContentH));
    }

    private View findTopChild() {
        View best = null;
        int bestTop = Integer.MAX_VALUE;
        final int c = getChildCount();
        for (int i = 0; i < c; i++) {
            View child = getChildAt(i);
            if (child == null) continue;
            int t = getDecoratedTop(child);
            if (t < bestTop) { bestTop = t; best = child; }
        }
        return best;
    }

    private View findBottomChild() {
        View best = null;
        int bestBottom = Integer.MIN_VALUE;
        final int c = getChildCount();
        for (int i = 0; i < c; i++) {
            View child = getChildAt(i);
            if (child == null) continue;
            int b = getDecoratedBottom(child);
            if (b > bestBottom) { bestBottom = b; best = child; }
        }
        return best;
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
