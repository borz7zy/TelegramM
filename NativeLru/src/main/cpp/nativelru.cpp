#include <jni.h>
#include <string>

#include "lru_cache.h"

static JavaVM* gJvm = nullptr;

static JNIEnv* getEnv() {
    if (!gJvm) return nullptr;
    JNIEnv* env = nullptr;
    jint res = gJvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (res == JNI_OK) return env;

    if (gJvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return nullptr;
    return env;
}

static void throwJava(JNIEnv* env, const char* className, const char* msg) {
    jclass cls = env->FindClass(className);
    if (cls) env->ThrowNew(cls, msg);
}

static void requireHandle(JNIEnv* env, jlong handle) {
    if (handle == 0) {
        throwJava(env, "java/lang/IllegalStateException", "Native handle is null (cache is closed?)");
    }
}

enum class Tag : uint8_t {
    Null = 0,
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
    ObjectArray
};

struct NativeValue final {
    Tag tag = Tag::Null;

    union {
        jboolean z;
        jbyte    b;
        jchar    c;
        jshort   s;
        jint     i;
        jlong    j;
        jfloat   f;
        jdouble  d;
    } prim{};

    std::u16string str16;

    std::vector<jint>  ints;
    std::vector<jbyte> bytes;
    std::vector<jobject> objects;

    jobject obj = nullptr;

    NativeValue() = default;
    NativeValue(const NativeValue&) = delete;
    NativeValue& operator=(const NativeValue&) = delete;

    ~NativeValue() { clear(); }

    void clear() {
        JNIEnv* env = getEnv();

        if (env) {
            if (obj) {
                env->DeleteGlobalRef(obj);
                obj = nullptr;
            }
            if (!objects.empty()) {
                for (jobject r : objects) {
                    if (r) env->DeleteGlobalRef(r);
                }
                objects.clear();
            }
        } else {
            obj = nullptr;
            objects.clear();
        }

        tag = Tag::Null;
        str16.clear();
        ints.clear();
        bytes.clear();
        prim = {};
    }

    void setNull() { clear(); }

    void setBoolean(jboolean v) { clear(); tag = Tag::Boolean; prim.z = v; }
    void setByte(jbyte v)       { clear(); tag = Tag::Byte;    prim.b = v; }
    void setChar(jchar v)       { clear(); tag = Tag::Char;    prim.c = v; }
    void setShort(jshort v)     { clear(); tag = Tag::Short;   prim.s = v; }
    void setInt(jint v)         { clear(); tag = Tag::Int;     prim.i = v; }
    void setLong(jlong v)       { clear(); tag = Tag::Long;    prim.j = v; }
    void setFloat(jfloat v)     { clear(); tag = Tag::Float;   prim.f = v; }
    void setDouble(jdouble v)   { clear(); tag = Tag::Double;  prim.d = v; }

    void setString(JNIEnv* env, jstring s) {
        clear();
        if (!s) { tag = Tag::Null; return; }
        tag = Tag::String;
        const jchar* chars = env->GetStringChars(s, nullptr);
        jsize len = env->GetStringLength(s);
        str16.assign(reinterpret_cast<const char16_t*>(chars),
                     reinterpret_cast<const char16_t*>(chars) + len);
        env->ReleaseStringChars(s, chars);
    }

    void setObject(JNIEnv* env, jobject o, Tag asTag) {
        clear();
        if (!o) { tag = Tag::Null; return; }
        tag = asTag;
        obj = env->NewGlobalRef(o);
        if (!obj) {
            tag = Tag::Null;
            throwJava(env, "java/lang/OutOfMemoryError", "NewGlobalRef failed");
        }
    }

    void setIntArray(JNIEnv* env, jintArray a) {
        clear();
        if (!a) { tag = Tag::Null; return; }
        tag = Tag::IntArray;
        jsize n = env->GetArrayLength(a);
        ints.resize(static_cast<size_t>(n));
        if (n > 0) env->GetIntArrayRegion(a, 0, n, ints.data());
    }

    void setByteArray(JNIEnv* env, jbyteArray a) {
        clear();
        if (!a) { tag = Tag::Null; return; }
        tag = Tag::ByteArray;
        jsize n = env->GetArrayLength(a);
        bytes.resize(static_cast<size_t>(n));
        if (n > 0) env->GetByteArrayRegion(a, 0, n, bytes.data());
    }

    void setObjectArray(JNIEnv* env, jobjectArray a) {
        clear();
        if (!a) { tag = Tag::Null; return; }
        tag = Tag::ObjectArray;
        jsize n = env->GetArrayLength(a);
        objects.reserve(static_cast<size_t>(n));
        for (jsize idx = 0; idx < n; ++idx) {
            jobject el = env->GetObjectArrayElement(a, idx);
            if (el) {
                jobject gr = env->NewGlobalRef(el);
                env->DeleteLocalRef(el);
                if (!gr) {
                    throwJava(env, "java/lang/OutOfMemoryError", "NewGlobalRef failed for element");
                    return;
                }
                objects.push_back(gr);
            } else {
                objects.push_back(nullptr);
            }
        }
    }
};

using Key = int64_t;
using Val = std::shared_ptr<NativeValue>;
using Cache = LRUCache<Key, Val>;

struct CacheHolder final {
    Cache cache;
    explicit CacheHolder(size_t cap) : cache(cap) {}
};

static CacheHolder* fromHandle(jlong h) {
    return reinterpret_cast<CacheHolder*>(h);
}

static std::once_flag gBoxOnce;
static jclass gObjectClass = nullptr;

static jclass gBooleanClass=nullptr;   static jmethodID gBooleanValueOf=nullptr;
static jclass gByteClass=nullptr;      static jmethodID gByteValueOf=nullptr;
static jclass gCharClass=nullptr;      static jmethodID gCharValueOf=nullptr;
static jclass gShortClass=nullptr;     static jmethodID gShortValueOf=nullptr;
static jclass gIntClass=nullptr;       static jmethodID gIntValueOf=nullptr;
static jclass gLongClass=nullptr;      static jmethodID gLongValueOf=nullptr;
static jclass gFloatClass=nullptr;     static jmethodID gFloatValueOf=nullptr;
static jclass gDoubleClass=nullptr;    static jmethodID gDoubleValueOf=nullptr;

static void initBoxing(JNIEnv* env) {
    std::call_once(gBoxOnce, [&]{
        auto makeGlobalClass = [&](const char* name) -> jclass {
            jclass local = env->FindClass(name);
            if (!local) return nullptr;
            jclass global = (jclass)env->NewGlobalRef(local);
            env->DeleteLocalRef(local);
            return global;
        };

        gObjectClass   = makeGlobalClass("java/lang/Object");

        gBooleanClass  = makeGlobalClass("java/lang/Boolean");
        gByteClass     = makeGlobalClass("java/lang/Byte");
        gCharClass     = makeGlobalClass("java/lang/Character");
        gShortClass    = makeGlobalClass("java/lang/Short");
        gIntClass      = makeGlobalClass("java/lang/Integer");
        gLongClass     = makeGlobalClass("java/lang/Long");
        gFloatClass    = makeGlobalClass("java/lang/Float");
        gDoubleClass   = makeGlobalClass("java/lang/Double");

        if (gBooleanClass) gBooleanValueOf = env->GetStaticMethodID(gBooleanClass, "valueOf", "(Z)Ljava/lang/Boolean;");
        if (gByteClass)    gByteValueOf    = env->GetStaticMethodID(gByteClass,    "valueOf", "(B)Ljava/lang/Byte;");
        if (gCharClass)    gCharValueOf    = env->GetStaticMethodID(gCharClass,    "valueOf", "(C)Ljava/lang/Character;");
        if (gShortClass)   gShortValueOf   = env->GetStaticMethodID(gShortClass,   "valueOf", "(S)Ljava/lang/Short;");
        if (gIntClass)     gIntValueOf     = env->GetStaticMethodID(gIntClass,     "valueOf", "(I)Ljava/lang/Integer;");
        if (gLongClass)    gLongValueOf    = env->GetStaticMethodID(gLongClass,    "valueOf", "(J)Ljava/lang/Long;");
        if (gFloatClass)   gFloatValueOf   = env->GetStaticMethodID(gFloatClass,   "valueOf", "(F)Ljava/lang/Float;");
        if (gDoubleClass)  gDoubleValueOf  = env->GetStaticMethodID(gDoubleClass,  "valueOf", "(D)Ljava/lang/Double;");
    });
}

static Val getOrNull(CacheHolder* h, Key k) {
    auto opt = h->cache.get(k);
    if (!opt.has_value()) return nullptr;
    return opt.value();
}

static Val getOrCreate(CacheHolder* h, Key k) {
    auto v = getOrNull(h, k);
    if (v) return v;

    auto nv = std::make_shared<NativeValue>();
    h->cache.insert(k, nv);
    return nv;
}

static void typeMismatch(JNIEnv* env, Tag expected, Tag actual) {
    (void)expected; (void)actual;
    throwJava(env, "java/lang/IllegalStateException", "Type mismatch for this key");
}

static jobject toJavaObject(JNIEnv* env, const NativeValue& v) {
    initBoxing(env);

    switch (v.tag) {
        case Tag::Null: return nullptr;

        case Tag::Boolean: return env->CallStaticObjectMethod(gBooleanClass, gBooleanValueOf, v.prim.z);
        case Tag::Byte:    return env->CallStaticObjectMethod(gByteClass,    gByteValueOf,    v.prim.b);
        case Tag::Char:    return env->CallStaticObjectMethod(gCharClass,    gCharValueOf,    v.prim.c);
        case Tag::Short:   return env->CallStaticObjectMethod(gShortClass,   gShortValueOf,   v.prim.s);
        case Tag::Int:     return env->CallStaticObjectMethod(gIntClass,     gIntValueOf,     v.prim.i);
        case Tag::Long:    return env->CallStaticObjectMethod(gLongClass,    gLongValueOf,    v.prim.j);
        case Tag::Float:   return env->CallStaticObjectMethod(gFloatClass,   gFloatValueOf,   v.prim.f);
        case Tag::Double:  return env->CallStaticObjectMethod(gDoubleClass,  gDoubleValueOf,  v.prim.d);

        case Tag::String: {
            return env->NewString(reinterpret_cast<const jchar*>(v.str16.data()),
                                  static_cast<jsize>(v.str16.size()));
        }

        case Tag::Object:
        case Tag::Class:
        case Tag::Throwable: {
            if (!v.obj) return nullptr;
            return env->NewLocalRef(v.obj);
        }

        case Tag::IntArray: {
            jintArray arr = env->NewIntArray(static_cast<jsize>(v.ints.size()));
            if (!arr) return nullptr;
            if (!v.ints.empty()) env->SetIntArrayRegion(arr, 0, (jsize)v.ints.size(), v.ints.data());
            return arr;
        }

        case Tag::ByteArray: {
            jbyteArray arr = env->NewByteArray(static_cast<jsize>(v.bytes.size()));
            if (!arr) return nullptr;
            if (!v.bytes.empty()) env->SetByteArrayRegion(arr, 0, (jsize)v.bytes.size(), v.bytes.data());
            return arr;
        }

        case Tag::ObjectArray: {
            if (!gObjectClass) return nullptr;
            jobjectArray arr = env->NewObjectArray((jsize)v.objects.size(), gObjectClass, nullptr);
            if (!arr) return nullptr;
            for (jsize i = 0; i < (jsize)v.objects.size(); ++i) {
                env->SetObjectArrayElement(arr, i, v.objects[(size_t)i]);
            }
            return arr;
        }
    }

    return nullptr;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativeCreate(JNIEnv *env, jclass clazz,
                                                         jlong capacity) {
    if (capacity <= 0) {
        throwJava(env, "java/lang/IllegalArgumentException", "capacity must be > 0");
        return 0;
    }
    initBoxing(env);
    auto* h = new CacheHolder(static_cast<size_t>(capacity));
    return reinterpret_cast<jlong>(h);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativeDestroy(JNIEnv *env, jclass clazz, jlong handle) {
    auto* h = fromHandle(handle);
    delete h;
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativeContains(JNIEnv *env, jclass clazz, jlong handle,
                                                           jlong key) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return JNI_FALSE;
    auto* h = fromHandle(handle);
    return h->cache.contains((Key)key) ? JNI_TRUE : JNI_FALSE;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativeClear(JNIEnv *env, jclass clazz, jlong handle) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    h->cache.clear();
}
extern "C"
JNIEXPORT jlong JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativeSize(JNIEnv *env, jclass clazz, jlong handle) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return 0;
    auto* h = fromHandle(handle);
    return (jlong)h->cache.size();
}
extern "C"
JNIEXPORT jlong JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativeCapacity(JNIEnv *env, jclass clazz,
                                                           jlong handle) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return 0;
    auto* h = fromHandle(handle);
    return (jlong)h->cache.capacity();
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativeGetTag(JNIEnv *env, jclass clazz, jlong handle,
                                                         jlong key) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return (jint)Tag::Null;
    auto* h = fromHandle(handle);
    auto v = getOrNull(h, (Key)key);
    if (!v) return (jint)Tag::Null;
    return (jint)v->tag;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativeGetAny(JNIEnv *env, jclass clazz, jlong handle,
                                                         jlong key) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return nullptr;
    auto* h = fromHandle(handle);
    auto v = getOrNull(h, (Key)key);
    if (!v) return nullptr;
    return toJavaObject(env, *v);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutNull(JNIEnv *env, jclass clazz, jlong handle,
                                                          jlong key) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setNull();
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutBoolean(JNIEnv *env, jclass clazz,
                                                             jlong handle, jlong key, jboolean v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setBoolean(v);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutByte(JNIEnv *env, jclass clazz, jlong handle,
                                                          jlong key, jbyte v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setByte(v);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutChar(JNIEnv *env, jclass clazz, jlong handle,
                                                          jlong key, jchar v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setChar(v);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutShort(JNIEnv *env, jclass clazz, jlong handle,
                                                           jlong key, jshort v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setShort(v);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutInt(JNIEnv *env, jclass clazz, jlong handle,
                                                         jlong key, jint v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setInt(v);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutLong(JNIEnv *env, jclass clazz, jlong handle,
                                                          jlong key, jlong v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setLong(v);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutFloat(JNIEnv *env, jclass clazz, jlong handle,
                                                           jlong key, jfloat v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setFloat(v);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutDouble(JNIEnv *env, jclass clazz, jlong handle,
                                                            jlong key, jdouble v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setDouble(v);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutString(JNIEnv *env, jclass clazz, jlong handle,
                                                            jlong key, jstring v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setString(env, v);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutObject(JNIEnv *env, jclass clazz, jlong handle,
                                                            jlong key, jobject v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setObject(env, v, Tag::Object);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutClass(JNIEnv *env, jclass clazz, jlong handle,
                                                           jlong key, jclass v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setObject(env, v, Tag::Class);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutThrowable(JNIEnv *env, jclass clazz,
                                                               jlong handle, jlong key,
                                                               jthrowable v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setObject(env, v, Tag::Throwable);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutIntArray(JNIEnv *env, jclass clazz,
                                                              jlong handle, jlong key,
                                                              jintArray v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setIntArray(env, v);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutByteArray(JNIEnv *env, jclass clazz,
                                                               jlong handle, jlong key,
                                                               jbyteArray v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setByteArray(env, v);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_github_borz7zy_nativelru_NativeLru_nativePutObjectArray(JNIEnv *env, jclass clazz,
                                                                 jlong handle, jlong key,
                                                                 jobjectArray v) {
    requireHandle(env, handle);
    if (env->ExceptionCheck()) return;
    auto* h = fromHandle(handle);
    getOrCreate(h, (Key)key)->setObjectArray(env, v);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    gJvm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM*, void*) {
    gJvm = nullptr;
}
