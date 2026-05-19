cmake_minimum_required(VERSION 3.18.1)
project(rlottie_jni CXX)

# Configure the bundled Samsung rlottie sources.
set(LOTTIE_MODULE      OFF CACHE BOOL "" FORCE)  # no dlopen image-loader plugin on Android
set(LOTTIE_THREAD      ON  CACHE BOOL "" FORCE)
set(LOTTIE_CACHE       ON  CACHE BOOL "" FORCE)
set(LOTTIE_TEST        OFF CACHE BOOL "" FORCE)
set(LOTTIE_CCACHE      OFF CACHE BOOL "" FORCE)
set(LOTTIE_ASAN        OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS  OFF CACHE BOOL "" FORCE)  # static-link rlottie into rlottie_jni

# rlottie ships hand-written 32-bit ARM NEON in pixman-arm-neon-asm.S, but it
# uses legacy GAS directives (.func/.endfunc/.object_arch) that NDK r27's
# LLVM integrated assembler rejects, and that .S is only wired up when
# ARCH=="arm". vdrawhelper_neon.cpp is compiled whenever __ARM_NEON__ is
# defined (NDK enables NEON by default on armeabi-v7a) and would then need
# those asm symbols. Disable NEON on the 32-bit ARM build so __ARM_NEON__ is
# undefined and rlottie uses its portable C raster path instead. arm64-v8a is
# unaffected — it uses a different codepath and keeps full SIMD.
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_options(-mfpu=vfpv3-d16)
endif()

# EXCLUDE_FROM_ALL skips the lottie2gif example executable: it is still
# defined as a target but not built unless something depends on it.
add_subdirectory(rlottie EXCLUDE_FROM_ALL)

# rlottie applies `-Wl,--version-script=rlottie.expmap` (which ends with
# `local: *;`) as a PRIVATE link option. Because we build rlottie as a STATIC
# library, CMake propagates its PRIVATE link deps to consumers — so that
# version script would land on rlottie_jni too and hide every JNI symbol,
# yielding UnsatisfiedLinkError at runtime (and a near-empty stripped .so on
# armeabi-v7a). Strip just that entry from rlottie's interface; keep the rest
# (pthread, etc.).
get_target_property(_rlottie_iface rlottie INTERFACE_LINK_LIBRARIES)
if(_rlottie_iface)
    list(FILTER _rlottie_iface EXCLUDE REGEX "version-script")
    set_target_properties(rlottie PROPERTIES
        INTERFACE_LINK_LIBRARIES "${_rlottie_iface}")
endif()

add_library(rlottie_jni SHARED rlottie_jni.cpp)

set_target_properties(rlottie_jni PROPERTIES
    CXX_STANDARD 14
    CXX_STANDARD_REQUIRED ON
)

target_link_libraries(rlottie_jni
    PRIVATE
        rlottie
        android
        jnigraphics
        log
)

# Strip and gc-sections to keep the .so small.
target_link_options(rlottie_jni PRIVATE
    "-Wl,--gc-sections"
    "-Wl,--exclude-libs,ALL"
)