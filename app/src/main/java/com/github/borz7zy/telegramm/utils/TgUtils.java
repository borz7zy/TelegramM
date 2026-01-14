package com.github.borz7zy.telegramm.utils;

import com.github.borz7zy.telegramm.App;
import com.github.borz7zy.telegramm.R;

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

    public static String getMessageText(TdApi.Message message) { // TODO: implement data class, String -> MessageData(text, MediaPreviewBitmap)
        if (message == null || message.content == null) return "";

        if (message.content instanceof TdApi.MessageText) {
            return ((TdApi.MessageText) message.content).text.text;
        } else if (message.content instanceof TdApi.MessagePhoto) {
            return App.getApplication().getString(R.string.photo); // TODO: add caption
        } else if (message.content instanceof TdApi.MessageVideo) {
            return App.getApplication().getString(R.string.video); // TODO: add caption
        } else if (message.content instanceof TdApi.MessageVoiceNote) {
            return App.getApplication().getString(R.string.voice_message);
        } else if (message.content instanceof TdApi.MessageSticker u) {
            return u.sticker.emoji + App.getApplication().getString(R.string.sticker);
        } else if (message.content instanceof TdApi.MessageAnimation) {
            return App.getApplication().getString(R.string.animation);
        }
        return App.getApplication().getString(R.string.message);
    }

    public static int dp(float value) {
        if (value == 0) {
            return 0;
        }
        return (int) Math.ceil(density * value);
    }
}