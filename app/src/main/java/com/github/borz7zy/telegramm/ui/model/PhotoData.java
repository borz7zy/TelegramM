package com.github.borz7zy.telegramm.ui.model;

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
}
