# Native methods reflect onto JNI symbols.
-keepclasseswithmembernames class * {
    native <methods>;
}