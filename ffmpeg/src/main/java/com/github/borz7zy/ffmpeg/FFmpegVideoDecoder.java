package com.github.borz7zy.ffmpeg;

import android.graphics.Bitmap;

final class FFmpegVideoDecoder {

    static {
        System.loadLibrary("ffmpeg_jni");
    }

    private FFmpegVideoDecoder() {}

    static native long nativeOpen(String path);

    static native void nativeGetInfo(long handle, int[] out);

    static native int nativeRenderNextFrame(long handle, Bitmap bitmap);

    static native void nativeSeekToStart(long handle);

    static native void nativeClose(long handle);
}
