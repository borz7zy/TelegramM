package com.github.borz7zy.telegramm.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class JustifiedLayout extends ViewGroup {

    private int spacingPx = 0;

    private int targetRowHeightPx = 220;
    private int minRowHeightPx = 160;
    private int maxRowHeightPx = 320;

    private boolean justifyLastRow = true;

    private final List<Row> rows = new ArrayList<>();

    public JustifiedLayout(@NonNull Context context) { super(context); }
    public JustifiedLayout(@NonNull Context context, @Nullable AttributeSet attrs) { super(context, attrs); }
    public JustifiedLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setSpacingPx(int spacingPx) { this.spacingPx = Math.max(0, spacingPx); requestLayout(); }

    public void setTargetRowHeightPx(int px) { targetRowHeightPx = Math.max(1, px); requestLayout(); }

    public void setRowHeightBoundsPx(int minPx, int maxPx) {
        minRowHeightPx = Math.max(1, minPx);
        maxRowHeightPx = Math.max(minRowHeightPx, maxPx);
        requestLayout();
    }

    public void setJustifyLastRow(boolean justify) { justifyLastRow = justify; requestLayout(); }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof JustifiedLayout.LayoutParams;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new JustifiedLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new JustifiedLayout.LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        if (p instanceof JustifiedLayout.LayoutParams) return new JustifiedLayout.LayoutParams((JustifiedLayout.LayoutParams) p);
        return new JustifiedLayout.LayoutParams(p);
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public float aspectRatio = 1f;

        public LayoutParams(Context c, AttributeSet attrs) { super(c, attrs); }
        public LayoutParams(int width, int height) { super(width, height); }
        public LayoutParams(ViewGroup.LayoutParams source) { super(source); }
        public LayoutParams(LayoutParams source) { super(source); this.aspectRatio = source.aspectRatio; }
    }

    private static class Row {
        final int startIndex;
        final int count;
        final int heightPx;
        final int[] widthsPx;

        Row(int startIndex, int count, int heightPx, int[] widthsPx) {
            this.startIndex = startIndex;
            this.count = count;
            this.heightPx = heightPx;
            this.widthsPx = widthsPx;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        rows.clear();
        rowTags.clear();

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        if (widthMode == MeasureSpec.UNSPECIFIED) {
            setMeasuredDimension(0, 0);
            return;
        }

        int contentW = widthSize - getPaddingLeft() - getPaddingRight();
        contentW = Math.max(0, contentW);

        int childCount = getChildCount();
        List<Integer> visible = new ArrayList<>(childCount);
        for (int i = 0; i < childCount; i++) {
            if (getChildAt(i).getVisibility() != GONE) visible.add(i);
        }

        List<Integer> currentRow = new ArrayList<>();
        float sumAr = 0f;

        int vCount = visible.size();
        int rowStartVisiblePos = 0;

        for (int pos = 0; pos < vCount; pos++) {
            int childIndex = visible.get(pos);
            View child = getChildAt(childIndex);
            float ar = getAspectRatio(child);
            if (ar <= 0f) ar = 1f;

            currentRow.add(childIndex);
            sumAr += ar;

            boolean isLast = (pos == vCount - 1);
            boolean rowIsFullAtTarget = (sumAr * targetRowHeightPx) >= contentW;

            if (!rowIsFullAtTarget && !isLast) continue;

            if (isLast && !justifyLastRow) {
                int h = clamp(targetRowHeightPx, minRowHeightPx, maxRowHeightPx);
                addRow(contentW, currentRow, sumAr, h, /*forceJustify*/ false, rowStartVisiblePos, visible);
                break;
            }

            int n = currentRow.size();
            int innerW = contentW - spacingPx * (n - 1);
            innerW = Math.max(0, innerW);
            int hExact = (sumAr > 0f) ? (int) Math.floor(innerW / sumAr) : targetRowHeightPx;

            if (hExact < minRowHeightPx && n > 1 && !isLast) {
                int lastChildIndex = currentRow.remove(n - 1);
                float lastAr = getAspectRatio(getChildAt(lastChildIndex));
                if (lastAr <= 0f) lastAr = 1f;
                sumAr -= lastAr;

                int n2 = currentRow.size();
                int innerW2 = contentW - spacingPx * (n2 - 1);
                innerW2 = Math.max(0, innerW2);
                int hExact2 = (sumAr > 0f) ? (int) Math.floor(innerW2 / sumAr) : targetRowHeightPx;
                int h2 = clamp(hExact2, minRowHeightPx, maxRowHeightPx);

                addRow(contentW, currentRow, sumAr, h2, /*forceJustify*/ true, rowStartVisiblePos, visible);

                currentRow = new ArrayList<>();
                currentRow.add(lastChildIndex);
                sumAr = lastAr;

                rowStartVisiblePos = pos;
                continue;
            }

            int h = clamp(hExact, minRowHeightPx, maxRowHeightPx);
            addRow(contentW, currentRow, sumAr, h, /*forceJustify*/ true, rowStartVisiblePos, visible);

            currentRow = new ArrayList<>();
            sumAr = 0f;
            rowStartVisiblePos = pos + 1;
        }

        int totalH = getPaddingTop() + getPaddingBottom();
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            totalH += row.heightPx;
            if (r != rows.size() - 1) totalH += spacingPx;
        }

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            Row row = rows.get(rowIdx);
            int[] childIndices = rowTags.get(rowIdx);

            for (int i = 0; i < row.count; i++) {
                int childIndex = childIndices[i];
                View child = getChildAt(childIndex);

                int w = row.widthsPx[i];
                int h = row.heightPx;
                child.measure(
                        MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
                );
            }
        }

        int resolvedH = resolveSize(totalH, heightMeasureSpec);
        setMeasuredDimension(widthSize, resolvedH);
    }

    private void addRow(int contentW,
                        List<Integer> rowChildren,
                        float sumAr,
                        int rowHeightPx,
                        boolean forceJustify,
                        int rowStartVisiblePos,
                        List<Integer> visible) {

        int n = rowChildren.size();
        int[] widths = new int[n];

        int innerW = contentW - spacingPx * (n - 1);
        innerW = Math.max(0, innerW);

        float sumIdeal = 0f;
        float[] ideal = new float[n];
        for (int i = 0; i < n; i++) {
            View child = getChildAt(rowChildren.get(i));
            float ar = getAspectRatio(child);
            if (ar <= 0f) ar = 1f;
            ideal[i] = rowHeightPx * ar;
            sumIdeal += ideal[i];
        }

        float scale = 1f;
        if (forceJustify && sumIdeal > 0f) {
            scale = innerW / sumIdeal;
        }

        int used = 0;
        for (int i = 0; i < n; i++) {
            int w = Math.max(1, Math.round(ideal[i] * scale));
            widths[i] = w;
            used += w;
        }

        int delta = innerW - used;
        widths[n - 1] = Math.max(1, widths[n - 1] + delta);

        int[] childIndices = new int[n];
        for (int i = 0; i < n; i++) childIndices[i] = rowChildren.get(i);

        Row row = new Row(/*startIndex*/ 0, n, rowHeightPx, widths);
        rowTags.add(childIndices);
        rows.add(row);
    }

    private final List<int[]> rowTags = new ArrayList<>();

    private int rowChildIndex(Row row, int i) {
        int idx = rows.indexOf(row);
        return rowTags.get(idx)[i];
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int x0 = getPaddingLeft();
        int y = getPaddingTop();

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            Row row = rows.get(rowIdx);
            int[] childIndices = rowTags.get(rowIdx);

            int x = x0;
            for (int i = 0; i < row.count; i++) {
                int childIndex = childIndices[i];
                View child = getChildAt(childIndex);
                if (child.getVisibility() == GONE) continue;

                int w = row.widthsPx[i];
                int h = row.heightPx;

                child.layout(x, y, x + w, y + h);
                x += w + spacingPx;
            }
            y += row.heightPx;
            if (rowIdx != rows.size() - 1) y += spacingPx;
        }
    }

    private float getAspectRatio(View child) {
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp instanceof JustifiedLayout.LayoutParams) {
            return ((JustifiedLayout.LayoutParams) lp).aspectRatio;
        }
        return 1f;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
