package com.github.borz7zy.telegramm.utils;

import android.content.Context;

import com.github.borz7zy.sharedutils.R;

import org.drinkless.tdlib.TdApi;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TgUtils {

    public static float density = 1;

    public static String formatTime(int date) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(date * 1000L));
    }

    public static String getMessageText(Context context, TdApi.Message message) { // TODO: implement data class, String -> MessageData(text, MediaPreviewBitmap)
        if (message == null || message.content == null) return "";

        if (message.content instanceof TdApi.MessageText) {
            return ((TdApi.MessageText) message.content).text.text;
        } else if (message.content instanceof TdApi.MessagePhoto) {
            return context.getString(R.string.photo); // TODO: add caption
        } else if (message.content instanceof TdApi.MessageVideo) {
            return context.getString(R.string.video); // TODO: add caption
        } else if (message.content instanceof TdApi.MessageVoiceNote) {
            return context.getString(R.string.voice_message);
        } else if (message.content instanceof TdApi.MessageSticker u) {
            return u.sticker.emoji + context.getString(R.string.sticker);
        } else if (message.content instanceof TdApi.MessageAnimation) {
            return context.getString(R.string.animation);
        }
        return context.getString(R.string.message);
    }

    public static int dp(float value) {
        if (value == 0) {
            return 0;
        }
        return (int) Math.ceil(density * value);
    }
}