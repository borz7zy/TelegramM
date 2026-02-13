package com.github.borz7zy.telegramm.ui.model;

import android.text.TextUtils;

import java.util.Objects;

public class PhotoData {
    public final int fileId;
    public final String localPath;
    public final int width;
    public final int height;
    public final float aspectRatio;

    public PhotoData(int fileId, String localPath, int width, int height) {
        this.fileId = fileId;
        this.localPath = localPath;
        this.width = width;
        this.height = height;
        this.aspectRatio = (height != 0) ? (float) width / height : 1f;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PhotoData photoData = (PhotoData) o;
        return fileId == photoData.fileId &&
                width == photoData.width &&
                height == photoData.height &&
                TextUtils.equals(localPath, photoData.localPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileId, localPath, width, height);
    }
}
