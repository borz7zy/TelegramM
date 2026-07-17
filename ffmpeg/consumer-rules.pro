# Keep JNI entry points reachable from native code.
-keepclasseswithmembernames class com.github.borz7zy.ffmpeg.FFmpegVideoDecoder {
    native <methods>;
}
