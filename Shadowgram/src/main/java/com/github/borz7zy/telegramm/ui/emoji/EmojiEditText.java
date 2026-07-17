package com.github.borz7zy.telegramm.ui.emoji;

import android.content.Context;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

public class EmojiEditText extends AppCompatEditText {

    private final Runnable invalidator = this::invalidate;
    private boolean applyingSpans;

    public EmojiEditText(Context context) {
        super(context);
        installWatcher();
    }

    public EmojiEditText(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        installWatcher();
    }

    public EmojiEditText(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        installWatcher();
    }

    private void installWatcher() {
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (applyingSpans || s == null || s.length() == 0) return;
                applyingSpans = true;
                try {
                    Emoji.EmojiSpan[] existing = s.getSpans(0, s.length(), Emoji.EmojiSpan.class);
                    for (Emoji.EmojiSpan sp : existing) s.removeSpan(sp);
                    Emoji.replaceEmoji(s, getPaint().getFontMetricsInt(), false);
                } finally {
                    applyingSpans = false;
                }
            }
        });
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (TextUtils.isEmpty(text)) {
            super.setText(text, type);
            return;
        }
        CharSequence processed = Emoji.replaceEmoji(text, getPaint().getFontMetricsInt(), true);
        super.setText(processed, BufferType.EDITABLE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Emoji.registerInvalidator(invalidator);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Emoji.unregisterInvalidator(invalidator);
    }
}