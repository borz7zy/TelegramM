package com.github.borz7zy.telegramm.ui.model;

import android.text.TextUtils;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContactItem that)) return false;
        return userId == that.userId
                && avatarFileId == that.avatarFileId
                && TextUtils.equals(name, that.name)
                && TextUtils.equals(lastOnline, that.lastOnline)
                && TextUtils.equals(avatarPath, that.avatarPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, name, lastOnline, avatarFileId, avatarPath);
    }
}