// JNI bridge to Samsung rlottie. Renders TGS / Lottie JSON content into
// Android Bitmap (ARGB_8888). rlottie produces ARGB32 premultiplied in
// native (little-endian) byte order — bytes [B, G, R, A] — while Android
// Bitmap ARGB_8888 stores bytes [R, G, B, A]. We swap the R and B channels
// in-place after rendering.

#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstdint>
#include <cstring>
#include <memory>
#include <string>

#include <rlottie.h>

#define LOG_TAG "rlottie_jni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

inline rlottie::Animation* fromHandle(jlong h) {
    return reinterpret_cast<rlottie::Animation*>(static_cast<uintptr_t>(h));
}

inline jlong toHandle(rlottie::Animation* p) {
    return static_cast<jlong>(reinterpret_cast<uintptr_t>(p));
}

// Swap R and B channels in a 32bpp RGBA/BGRA buffer. Works in place.
// `stride` is bytes-per-row; `width` is pixels per row.
void swapRB(uint8_t* pixels, int width, int height, int stride) {
    for (int y = 0; y < height; ++y) {
        uint32_t* row = reinterpret_cast<uint32_t*>(pixels + y * stride);
        for (int x = 0; x < width; ++x) {
            uint32_t px = row[x];
            // px = AARRGGBB (native order). Want AABBGGRR.
            uint32_t a = px & 0xFF000000u;
            uint32_t r = (px & 0x00FF0000u) >> 16;
            uint32_t g = px & 0x0000FF00u;
            uint32_t b = (px & 0x000000FFu) << 16;
            row[x] = a | b | g | r;
        }
    }
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_github_borz7zy_rlottie_RLottieNative_nativeCreate(
        JNIEnv* env, jclass, jstring jJson, jstring jKey) {
    if (jJson == nullptr) return 0;
    const char* json = env->GetStringUTFChars(jJson, nullptr);
    const char* key  = jKey ? env->GetStringUTFChars(jKey, nullptr) : "";

    std::unique_ptr<rlottie::Animation> anim =
            rlottie::Animation::loadFromData(std::string(json),
                                             std::string(key),
                                             /*resourcePath=*/"",
                                             /*cachePolicy=*/true);

    env->ReleaseStringUTFChars(jJson, json);
    if (jKey) env->ReleaseStringUTFChars(jKey, key);

    if (!anim) {
        LOGE("loadFromData returned null");
        return 0;
    }
    return toHandle(anim.release());
}

JNIEXPORT void JNICALL
Java_com_github_borz7zy_rlottie_RLottieNative_nativeDestroy(
        JNIEnv*, jclass, jlong handle) {
    rlottie::Animation* a = fromHandle(handle);
    delete a;
}

JNIEXPORT jint JNICALL
Java_com_github_borz7zy_rlottie_RLottieNative_nativeTotalFrame(
        JNIEnv*, jclass, jlong handle) {
    rlottie::Animation* a = fromHandle(handle);
    if (!a) return 0;
    return static_cast<jint>(a->totalFrame());
}

JNIEXPORT jdouble JNICALL
Java_com_github_borz7zy_rlottie_RLottieNative_nativeFrameRate(
        JNIEnv*, jclass, jlong handle) {
    rlottie::Animation* a = fromHandle(handle);
    if (!a) return 0.0;
    return a->frameRate();
}

JNIEXPORT jdouble JNICALL
Java_com_github_borz7zy_rlottie_RLottieNative_nativeDuration(
        JNIEnv*, jclass, jlong handle) {
    rlottie::Animation* a = fromHandle(handle);
    if (!a) return 0.0;
    return a->duration();
}

JNIEXPORT void JNICALL
Java_com_github_borz7zy_rlottie_RLottieNative_nativeGetSize(
        JNIEnv* env, jclass, jlong handle, jintArray outSize) {
    rlottie::Animation* a = fromHandle(handle);
    if (!a || !outSize) return;
    if (env->GetArrayLength(outSize) < 2) return;
    size_t w = 0, h = 0;
    a->size(w, h);
    jint vals[2] = { static_cast<jint>(w), static_cast<jint>(h) };
    env->SetIntArrayRegion(outSize, 0, 2, vals);
}

// Renders frameNo into the Android Bitmap. Returns 0 on success, non-zero on
// error. The bitmap must be ARGB_8888 with width/height >= 1.
JNIEXPORT jint JNICALL
Java_com_github_borz7zy_rlottie_RLottieNative_nativeRenderFrame(
        JNIEnv* env, jclass, jlong handle, jobject bitmap, jint frameNo) {
    rlottie::Animation* a = fromHandle(handle);
    if (!a || !bitmap) return -1;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("AndroidBitmap_getInfo failed");
        return -2;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("unsupported bitmap format: %d", info.format);
        return -3;
    }

    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS
        || !pixels) {
        LOGE("AndroidBitmap_lockPixels failed");
        return -4;
    }

    rlottie::Surface surface(
            reinterpret_cast<uint32_t*>(pixels),
            info.width, info.height, info.stride);

    a->renderSync(static_cast<size_t>(frameNo), surface, /*keepAspectRatio=*/true);

    swapRB(reinterpret_cast<uint8_t*>(pixels),
           static_cast<int>(info.width),
           static_cast<int>(info.height),
           static_cast<int>(info.stride));

    AndroidBitmap_unlockPixels(env, bitmap);
    return 0;
}

JNIEXPORT void JNICALL
Java_com_github_borz7zy_rlottie_RLottieNative_nativeConfigureCacheSize(
        JNIEnv*, jclass, jint cacheSize) {
    rlottie::configureModelCacheSize(static_cast<size_t>(cacheSize));
}

} // extern "C"