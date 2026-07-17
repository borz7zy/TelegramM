cmake_minimum_required(VERSION 3.18.1)
project(ffmpeg_jni CXX)

set(PREBUILT "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}")

if(NOT EXISTS "${PREBUILT}/lib/libavcodec.a")
    message(FATAL_ERROR
        "Prebuilt FFmpeg for ${ANDROID_ABI} not found at ${PREBUILT}.\n"
        "Run: ANDROID_NDK=<ndk> bash ffmpeg/tools/build_ffmpeg.sh")
endif()

foreach(lib avformat avcodec swresample swscale avutil)
    add_library(${lib} STATIC IMPORTED)
    set_target_properties(${lib} PROPERTIES
        IMPORTED_LOCATION "${PREBUILT}/lib/lib${lib}.a")
endforeach()

add_library(ffmpeg_jni SHARED video_decoder_jni.cpp)

target_include_directories(ffmpeg_jni PRIVATE "${PREBUILT}/include")

set_target_properties(ffmpeg_jni PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON)

target_link_libraries(ffmpeg_jni
    PRIVATE
        avformat avcodec swresample swscale avutil
        android
        jnigraphics
        log
        z
        m)

target_link_options(ffmpeg_jni PRIVATE
    "-Wl,--gc-sections"
    "-Wl,--exclude-libs,ALL"
    "-Wl,-Bsymbolic")
