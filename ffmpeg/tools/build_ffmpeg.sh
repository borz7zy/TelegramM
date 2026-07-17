#!/usr/bin/env bash
#
# Cross-compiles a minimal, LGPL FFmpeg (video + swscale/swresample) as static
# libraries for the four Android ABIs the app ships. Produces:
#
#   ffmpeg/src/main/cpp/prebuilt/<abi>/lib/*.a
#   ffmpeg/src/main/cpp/prebuilt/<abi>/include/**
#
# These artifacts are NOT committed (see ffmpeg/.gitignore); run this once per
# machine/CI before building the :ffmpeg Gradle module.
#
# Requirements: Android NDK r27, GNU make, a POSIX shell. x86/x86_64 asm is
# disabled (nasm/yasm not required); ARM NEON is built via clang.
#
# Usage:
#   ANDROID_NDK=/path/to/ndk/27.0.12077973 bash ffmpeg/tools/build_ffmpeg.sh
#   # optional: ABIS="arm64-v8a x86_64" to build a subset
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"           # .../ffmpeg
SRC_DIR="$MODULE_DIR/src/main/cpp/ffmpeg"            # FFmpeg source (submodule)
PREBUILT_DIR="$MODULE_DIR/src/main/cpp/prebuilt"
BUILD_ROOT="$MODULE_DIR/.build"

# --- Locate the NDK -----------------------------------------------------------
if [[ -z "${ANDROID_NDK:-}" ]]; then
  # Fall back to sdk.dir from local.properties + the ndkVersion we pin.
  SDK_DIR="$(sed -n 's/^sdk.dir=//p' "$MODULE_DIR/../local.properties" 2>/dev/null || true)"
  if [[ -n "$SDK_DIR" && -d "$SDK_DIR/ndk/27.0.12077973" ]]; then
    ANDROID_NDK="$SDK_DIR/ndk/27.0.12077973"
  fi
fi
if [[ -z "${ANDROID_NDK:-}" || ! -d "$ANDROID_NDK" ]]; then
  echo "ERROR: set ANDROID_NDK to your NDK r27 path." >&2
  exit 1
fi

HOST_TAG="linux-x86_64"
case "$(uname -s)" in
  Darwin) HOST_TAG="darwin-x86_64" ;;
esac
TOOLCHAIN="$ANDROID_NDK/toolchains/llvm/prebuilt/$HOST_TAG"
SYSROOT="$TOOLCHAIN/sysroot"
API=33

if [[ ! -x "$TOOLCHAIN/bin/clang" ]]; then
  echo "ERROR: toolchain not found at $TOOLCHAIN" >&2
  exit 1
fi

if [[ ! -x "$SRC_DIR/configure" ]]; then
  echo "ERROR: FFmpeg source missing at $SRC_DIR." >&2
  echo "       Run: git submodule update --init ffmpeg/src/main/cpp/ffmpeg" >&2
  exit 1
fi

ABIS="${ABIS:-arm64-v8a armeabi-v7a x86 x86_64}"

# Minimal, LGPL, video-only-plus-groundwork feature set.
COMMON_FLAGS=(
  --target-os=android
  --enable-cross-compile
  --enable-static --disable-shared
  --enable-pic
  --disable-programs --disable-doc --disable-avdevice --disable-postproc
  --disable-network --disable-debug --disable-symver --disable-autodetect
  --disable-everything
  --enable-avformat --enable-avcodec --enable-avutil
  --enable-swscale --enable-swresample
  --enable-demuxer=matroska,mov,ogg
  --enable-decoder=vp8,vp9,h264,hevc,aac,mp3,opus,vorbis
  --enable-parser=vp8,vp9,h264,hevc,aac,opus,vorbis
  --enable-bsf=vp9_superframe,vp9_raw_reorder,hevc_mp4toannexb,h264_mp4toannexb,aac_adtstoasc
  --enable-protocol=file
  --nm="$TOOLCHAIN/bin/llvm-nm"
  --ar="$TOOLCHAIN/bin/llvm-ar"
  --ranlib="$TOOLCHAIN/bin/llvm-ranlib"
  --strip="$TOOLCHAIN/bin/llvm-strip"
)

for ABI in $ABIS; do
  echo ""
  echo "==================== Building FFmpeg for $ABI ===================="

  case "$ABI" in
    arm64-v8a)
      ARCH=aarch64;  CPU=armv8-a
      TRIPLE=aarch64-linux-android
      CC_PREFIX=aarch64-linux-android
      EXTRA_CFLAGS=""; EXTRA_LDFLAGS=""
      X86ASM=""
      ;;
    armeabi-v7a)
      ARCH=arm;      CPU=armv7-a
      TRIPLE=arm-linux-androideabi
      CC_PREFIX=armv7a-linux-androideabi
      EXTRA_CFLAGS="-march=armv7-a -mfpu=neon -mfloat-abi=softfp -mthumb"
      EXTRA_LDFLAGS="-Wl,--fix-cortex-a8"
      X86ASM=""
      ;;
    x86)
      ARCH=x86;      CPU=i686
      TRIPLE=i686-linux-android
      CC_PREFIX=i686-linux-android
      EXTRA_CFLAGS=""; EXTRA_LDFLAGS=""
      # nasm/yasm not required; also drop MMX/SSE inline asm — on 32-bit x86 it
      # emits absolute (R_386_32) relocations that lld rejects in a PIC shared
      # object and Android forbids as text relocations.
      X86ASM="--disable-x86asm --disable-inline-asm"
      ;;
    x86_64)
      ARCH=x86_64;   CPU=x86-64
      TRIPLE=x86_64-linux-android
      CC_PREFIX=x86_64-linux-android
      EXTRA_CFLAGS=""; EXTRA_LDFLAGS=""
      X86ASM="--disable-x86asm --disable-inline-asm"
      ;;
    *)
      echo "Unknown ABI: $ABI" >&2; exit 1 ;;
  esac

  CC="$TOOLCHAIN/bin/${CC_PREFIX}${API}-clang"
  CXX="$TOOLCHAIN/bin/${CC_PREFIX}${API}-clang++"

  BUILD_DIR="$BUILD_ROOT/$ABI"
  OUT_DIR="$PREBUILT_DIR/$ABI"
  rm -rf "$BUILD_DIR" "$OUT_DIR"
  mkdir -p "$BUILD_DIR"

  ( cd "$BUILD_DIR"
    "$SRC_DIR/configure" \
      "${COMMON_FLAGS[@]}" \
      --prefix="$OUT_DIR" \
      --arch="$ARCH" \
      --cpu="$CPU" \
      --cross-prefix="$TOOLCHAIN/bin/${TRIPLE}-" \
      --cc="$CC" \
      --cxx="$CXX" \
      --sysroot="$SYSROOT" \
      $X86ASM \
      --extra-cflags="-O2 -fPIC -DANDROID $EXTRA_CFLAGS" \
      --extra-ldflags="$EXTRA_LDFLAGS"

    make -j"$(nproc 2>/dev/null || echo 4)"
    make install
  )

  echo "---- $ABI done: $(ls "$OUT_DIR/lib"/*.a 2>/dev/null | wc -l) static libs ----"
done

echo ""
echo "All requested ABIs built into $PREBUILT_DIR"
