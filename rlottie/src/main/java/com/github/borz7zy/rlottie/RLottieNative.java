package com.github.borz7zy.rlottie;

import android.graphics.Bitmap;

/**
 * Thin wrapper around the JNI bridge to Samsung rlottie. Not intended for
 * direct use — see {@link RLottieDrawable} and {@link RLottieAnimationView}.
 */
final class RLottieNative {

    static {
        System.loadLibrary("rlottie_jni");
    }

    private RLottieNative() {}

    static native long nativeCreate(String json, String cacheKey);

    static native void nativeDestroy(long handle);

    static native int nativeTotalFrame(long handle);

    static native double nativeFrameRate(long handle);

    static native double nativeDuration(long handle);

    /** Writes native width/height into {@code outSize[0..1]}. */
    static native void nativeGetSize(long handle, int[] outSize);

    /**
     * Renders {@code frameNo} into {@code bitmap}, which must be ARGB_8888.
     * Returns 0 on success.
     */
    static native int nativeRenderFrame(long handle, Bitmap bitmap, int frameNo);

    static native void nativeConfigureCacheSize(int cacheSize);
}