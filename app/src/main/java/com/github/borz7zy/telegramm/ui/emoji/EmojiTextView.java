package com.github.borz7zy.telegramm.ui.emoji;

import android.content.Context;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import org.drinkless.tdlib.TdApi;

public class EmojiTextView extends AppCompatTextView {

    private final Runnable invalidator = this::invalidate;

    private boolean attached;
    private boolean tickScheduled;

    /**
     * ~30 fps invalidate loop while custom emoji spans are present in the
     * current text. Keeps animation cost bounded; CustomEmojiSpan derives its
     * progress from monotonic time, so we don't need 60 fps to look smooth.
     */
    private final Runnable animationTick = new Runnable() {
        @Override
        public void run() {
            tickScheduled = false;
            if (!attached) return;
            CharSequence cs = getText();
            if (!(cs instanceof Spannable)) return;
            Spannable s = (Spannable) cs;
            CustomEmojiSpan[] spans = s.getSpans(0, s.length(), CustomEmojiSpan.class);
            if (spans.length == 0) return;
            invalidate();
            scheduleNextTick();
        }
    };

    public EmojiTextView(Context context) { super(context); }
    public EmojiTextView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); }
    public EmojiTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (TextUtils.isEmpty(text)) {
            super.setText(text, type);
            maybeStopTick();
            return;
        }
        CharSequence processed = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), true);
        super.setText(processed, BufferType.SPANNABLE);
        maybeStartTick();
    }

    /**
     * Bind text + TDLib entities so inline custom emojis (TextEntityTypeCustomEmoji)
     * are rendered as animated stickers via {@link CustomEmojiSpan}.
     */
    public void setFormattedText(@Nullable String text, @Nullable TdApi.TextEntity[] entities) {
        if (TextUtils.isEmpty(text)) {
            super.setText(text, BufferType.NORMAL);
            maybeStopTick();
            return;
        }
        // Build a Spannable up front so Apple-emoji + custom-emoji passes can
        // both mutate it in place.
        Spannable s = Spannable.Factory.getInstance().newSpannable(text);
        Emoji.replaceEmoji(s, getPaint().getFontMetricsInt(), false);

        if (entities != null && entities.length > 0) {
            int len = s.length();
            for (TdApi.TextEntity e : entities) {
                if (e == null || !(e.type instanceof TdApi.TextEntityTypeCustomEmoji)) continue;
                long id = ((TdApi.TextEntityTypeCustomEmoji) e.type).customEmojiId;
                int from = e.offset;
                int to = e.offset + e.length;
                if (from < 0 || to > len || from >= to) continue;

                // The Apple-emoji span pass already covered the placeholder
                // emoji; strip it before installing the custom-emoji span so
                // the two don't double-draw at the same range.
                Emoji.EmojiSpan[] existing = s.getSpans(from, to, Emoji.EmojiSpan.class);
                for (Emoji.EmojiSpan sp : existing) s.removeSpan(sp);

                CustomEmojiSpan ce = new CustomEmojiSpan(id, getPaint().getFontMetricsInt());
                s.setSpan(ce, from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        super.setText(s, BufferType.SPANNABLE);
        maybeStartTick();
    }

    private void maybeStartTick() {
        CharSequence cs = getText();
        if (!(cs instanceof Spannable)) return;
        Spannable s = (Spannable) cs;
        CustomEmojiSpan[] spans = s.getSpans(0, s.length(), CustomEmojiSpan.class);
        if (spans.length > 0 && attached) scheduleNextTick();
    }

    private void maybeStopTick() {
        if (tickScheduled) {
            removeCallbacks(animationTick);
            tickScheduled = false;
        }
    }

    private void scheduleNextTick() {
        if (tickScheduled) return;
        tickScheduled = true;
        postDelayed(animationTick, 33L);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        Emoji.registerInvalidator(invalidator);
        maybeStartTick();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        Emoji.unregisterInvalidator(invalidator);
        maybeStopTick();
    }
}