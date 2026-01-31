package com.github.borz7zy.telegramm.ui.model;

public class ContactItem {
    public final long userId;
    public final String name;
    public final String lastOnline;
    public final int avatarFileId;
    public final String avatarPath;

    public ContactItem(long userId, String name, String lastOnline, int avatarFileId, String avatarPath) {
        this.userId = userId;
        this.name = name;
        this.lastOnline = lastOnline;
        this.avatarFileId = avatarFileId;
        this.avatarPath = avatarPath;
    }
}
