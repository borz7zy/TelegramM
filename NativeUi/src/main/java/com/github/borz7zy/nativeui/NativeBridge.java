package com.github.borz7zy.nativeui;

public final class NativeBridge {
    private NativeBridge(){}
    static {
        System.loadLibrary("nativeui");
    }
    public static native long nativeCreate();
    public static native void nativeDestroy(long handle);
    public static native void nativeSubmitScroll(long handle, int dy);
    public static native void nativeSetItemCount(long handle, int count);
    public static native void nativeSetViewport(long handle, int w, int h);
    public static native void nativeSetInsets(long handle, int top, int bottom);
    public static native void nativeSetItemHeight(long handle, int position, int px);
    public static native void nativeSetScroll(long handle, int absPx);
    public static native int nativeCopyLayoutSnapshot(long handle, int[] out);
}
