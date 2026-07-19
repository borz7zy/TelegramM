package com.github.borz7zy.telegramm.ui.model;

import static com.github.borz7zy.telegramm.utils.TgUtils.formatTime;
import static com.github.borz7zy.telegramm.utils.TgUtils.getMessageText;

import android.content.Context;

import org.drinkless.tdlib.TdApi;

import java.util.Collections;
import java.util.List;

public final class DialogItem {
    public final long chatId;
    public long order;
    public boolean isPinned;
    public String name;
    public String time;
    public String text;
    public int unread;
    public boolean isTyping;
    public int avatarFileId;
    public String avatarPath;

    public boolean isForum;
    public boolean isExpanded;
    public List<TdApi.ForumTopic> topics;

    public DialogItem(long chatId,
                      long order,
                      boolean isPinned,
                      String name,
                      String time,
                      String text,
                      int unread,
                      boolean isTyping,
                      int avatarFileId,
                      String avatarPath,
                      boolean isForum,
                      boolean isExpanded,
                      List<TdApi.ForumTopic> topics) {
        this.chatId = chatId;
        this.order = order;
        this.isPinned = isPinned;
        this.name = name;
        this.time = time;
        this.text = text;
        this.unread = unread;
        this.isTyping = isTyping;
        this.avatarFileId = avatarFileId;
        this.avatarPath = avatarPath;
        this.isForum = isForum;
        this.isExpanded = isExpanded;
        this.topics = topics != null ? topics : Collections.emptyList();
    }

    public DialogItem(Context context, TdApi.Chat chat, long order) {
        this.chatId = chat.id;
        this.order = order;
        this.isExpanded = false;
        this.topics = Collections.emptyList();
        updateFromChat(context, chat);
    }

    public void updateFromChat(Context context, TdApi.Chat chat) {
        this.name = chat.title;
        this.unread = chat.unreadCount;
        this.isPinned = extractPinned(chat);
        this.isForum = chat.viewAsTopics;

        if (chat.lastMessage != null) {
            this.text = getMessageText(context, chat.lastMessage);
            this.time = formatTime(chat.lastMessage.date);
        } else {
            this.text = "";
            this.time = "";
        }

        updateAvatar(chat.photo);
    }

    private static boolean extractPinned(TdApi.Chat chat) {
        if (chat == null || chat.positions == null) return false;
        for (TdApi.ChatPosition p : chat.positions) {
            if (p.list instanceof TdApi.ChatListMain) {
                return p.isPinned;
            }
        }
        return false;
    }

    private void updateAvatar(TdApi.ChatPhotoInfo photo) {
        avatarFileId = 0;
        avatarPath = null;

        if (photo == null || photo.small == null) return;

        avatarFileId = photo.small.id;

        if (photo.small.local != null
                && photo.small.local.isDownloadingCompleted
                && photo.small.local.path != null
                && !photo.small.local.path.isEmpty()) {
            avatarPath = photo.small.local.path;
        }
    }

    public DialogItem copyWithOrderPinned(long newOrder, boolean pinned) {
        return new DialogItem(chatId, newOrder, pinned, name, time, text, unread, isTyping,
                avatarFileId, avatarPath, isForum, isExpanded, topics);
    }

    public DialogItem copyWithTyping(boolean typing) {
        return new DialogItem(chatId, order, isPinned, name, time, text, unread, typing,
                avatarFileId, avatarPath, isForum, isExpanded, topics);
    }

    public DialogItem copyWithExpanded(boolean expanded) {
        return new DialogItem(chatId, order, isPinned, name, time, text, unread, isTyping,
                avatarFileId, avatarPath, isForum, expanded, topics);
    }

    public DialogItem copyWithTopics(List<TdApi.ForumTopic> newTopics) {
        return new DialogItem(chatId, order, isPinned, name, time, text, unread, isTyping,
                avatarFileId, avatarPath, isForum, isExpanded, newTopics);
    }
}