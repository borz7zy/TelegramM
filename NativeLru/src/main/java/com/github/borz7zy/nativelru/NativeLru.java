package com.github.borz7zy.nativelru;

import java.lang.ref.Cleaner;

public class NativeLru implements AutoCloseable {

    private static final String TAG = "NativeLru";

    static {
        System.loadLibrary("nativelru");
    }

    public enum ValueType {
        Null,
        Boolean,
        Byte,
        Char,
        Short,
        Int,
        Long,
        Float,
        Double,
        String,
        Object,
        Class,
        Throwable,
        IntArray,
        ByteArray,
        ObjectArray;

        static ValueType fromOrdinal(int x) {
            if (x < 0 || x >= values().length) return Null;
            return values()[x];
        }
    }

    private static final Cleaner CLEANER = Cleaner.create();

    private long handle;
    private final Cleaner.Cleanable cleanable;

    private static final class State implements Runnable {
        private long h;
        State(long h) { this.h = h; }
        @Override public void run() {
            if (h != 0) {
                nativeDestroy(h);
                h = 0;
            }
        }
    }

    public NativeLru(long capacity) {
        this.handle = nativeCreate(capacity);
        this.cleanable = CLEANER.register(this, new State(this.handle));
    }

    public long size() { return nativeSize(handle); }
    public long capacity() { return nativeCapacity(handle); }
    public boolean contains(long key) { return nativeContains(handle, key); }
    public void clear() { nativeClear(handle); }

    public ValueType typeOf(long key) {
        return ValueType.fromOrdinal(nativeGetTag(handle, key));
    }

    public Object get(long key) {
        return nativeGetAny(handle, key);
    }

    public void putNull(long key) { nativePutNull(handle, key); }
    public void put(long key, boolean v) { nativePutBoolean(handle, key, v); }
    public void put(long key, byte v) { nativePutByte(handle, key, v); }
    public void put(long key, char v) { nativePutChar(handle, key, v); }
    public void put(long key, short v) { nativePutShort(handle, key, v); }
    public void put(long key, int v) { nativePutInt(handle, key, v); }
    public void put(long key, long v) { nativePutLong(handle, key, v); }
    public void put(long key, float v) { nativePutFloat(handle, key, v); }
    public void put(long key, double v) { nativePutDouble(handle, key, v); }

    public void put(long key, String v) { nativePutString(handle, key, v); }
    public void putObject(long key, Object v) { nativePutObject(handle, key, v); }
    public void putClass(long key, Class<?> v) { nativePutClass(handle, key, v); }
    public void putThrowable(long key, Throwable v) { nativePutThrowable(handle, key, v); }
    public void put(long key, int[] v) { nativePutIntArray(handle, key, v); }
    public void put(long key, byte[] v) { nativePutByteArray(handle, key, v); }
    public void put(long key, Object[] v) { nativePutObjectArray(handle, key, v); }

    public void put(long key, Object value) {
        if (value == null) { putNull(key); return; }

        if (value instanceof Boolean) { put(key, (boolean) value); return; }
        if (value instanceof Byte) { put(key, (byte) value); return; }
        if (value instanceof Character) { put(key, (char) value); return; }
        if (value instanceof Short) { put(key, (short) value); return; }
        if (value instanceof Integer) { put(key, (int) value); return; }
        if (value instanceof Long) { put(key, (long) value); return; }
        if (value instanceof Float) { put(key, (float) value); return; }
        if (value instanceof Double) { put(key, (double) value); return; }

        if (value instanceof String) { put(key, (String) value); return; }
        if (value instanceof Class) { putClass(key, (Class<?>) value); return; }
        if (value instanceof Throwable) { putThrowable(key, (Throwable) value); return; }

        if (value instanceof int[]) { put(key, (int[]) value); return; }
        if (value instanceof byte[]) { put(key, (byte[]) value); return; }
        if (value instanceof Object[]) { put(key, (Object[]) value); return; }

        putObject(key, value);
    }

    @Override
    public void close() {
        cleanable.clean();
        handle = 0;
    }


    // --------------------
    // NATIVES BLYA
    // --------------------
    private static native long nativeCreate(long capacity);
    private static native void nativeDestroy(long handle);

    private static native boolean nativeContains(long handle, long key);
    private static native void nativeClear(long handle);
    private static native long nativeSize(long handle);
    private static native long nativeCapacity(long handle);

    private static native int nativeGetTag(long handle, long key);
    private static native Object nativeGetAny(long handle, long key);

    private static native void nativePutNull(long handle, long key);
    private static native void nativePutBoolean(long handle, long key, boolean v);
    private static native void nativePutByte(long handle, long key, byte v);
    private static native void nativePutChar(long handle, long key, char v);
    private static native void nativePutShort(long handle, long key, short v);
    private static native void nativePutInt(long handle, long key, int v);
    private static native void nativePutLong(long handle, long key, long v);
    private static native void nativePutFloat(long handle, long key, float v);
    private static native void nativePutDouble(long handle, long key, double v);

    private static native void nativePutString(long handle, long key, String v);
    private static native void nativePutObject(long handle, long key, Object v);
    private static native void nativePutClass(long handle, long key, Class<?> v);
    private static native void nativePutThrowable(long handle, long key, Throwable v);

    private static native void nativePutIntArray(long handle, long key, int[] v);
    private static native void nativePutByteArray(long handle, long key, byte[] v);
    private static native void nativePutObjectArray(long handle, long key, Object[] v);
}