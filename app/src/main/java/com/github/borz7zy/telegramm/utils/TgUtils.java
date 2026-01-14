package com.github.borz7zy.telegramm.utils;

import org.drinkless.tdlib.TdApi;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TgUtils {

    public static String formatTime(int date) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(date * 1000L));
    }

    public static String getMessageText(TdApi.Message message) { // TODO: implement data class, String -> MessageData(text, MediaPreviewBitmap)
        if (message == null || message.content == null) return "";

        if (message.content instanceof TdApi.MessageText) {
            return ((TdApi.MessageText) message.content).text.text;
        } else if (message.content instanceof TdApi.MessagePhoto) {
            return "Фотография"; // TODO: remove hardcoded strings
        } else if (message.content instanceof TdApi.MessageVideo) {
            return "Видео"; // TODO: remove hardcoded strings
        } else if (message.content instanceof TdApi.MessageVoiceNote) {
            return "Голосовое сообщение"; // TODO: remove hardcoded strings
        } else if (message.content instanceof TdApi.MessageSticker u) {
            return u.sticker.emoji + "sticker"; // TODO: remove hardcoded strings
        } else if (message.content instanceof TdApi.MessageAnimation) {
            return "GIF"; // TODO: remove hardcoded strings
        }
        return "Сообщение"; // TODO: remove hardcoded strings
    }
}