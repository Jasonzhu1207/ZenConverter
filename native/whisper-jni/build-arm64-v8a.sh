#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/build-arm64"
ABI="arm64-v8a"
ANDROID_API="26"
WHISPER_CPP_TAG="v1.9.5"
WHISPER_CPP_DIR="$SCRIPT_DIR/whisper.cpp"

find_ndk_home() {
  local candidate
  for candidate in \
    "${ANDROID_NDK_HOME:-}" \
    "${ANDROID_NDK_ROOT:-}" \
    "/opt/android-ndk" \
    "$HOME/Android/Sdk/ndk"
  do
    if [ -n "$candidate" ] && [ -f "$candidate/source.properties" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [ -z "$NDK_HOME" ] || [ ! -f "$NDK_HOME/source.properties" ]; then
  NDK_HOME="$(find_ndk_home || true)"
fi

if [ -z "$NDK_HOME" ] || [ ! -f "$NDK_HOME/source.properties" ]; then
  echo "Android NDK root not found. Set ANDROID_NDK_HOME." >&2
  exit 1
fi

echo "Using NDK: $NDK_HOME"

if [ ! -f "$WHISPER_CPP_DIR/include/whisper.h" ]; then
  echo "Fetching whisper.cpp ${WHISPER_CPP_TAG}..."
  rm -rf "$WHISPER_CPP_DIR"
  git clone --depth 1 --branch "$WHISPER_CPP_TAG" \
    https://github.com/ggml-org/whisper.cpp.git "$WHISPER_CPP_DIR"
fi

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cd "$OUTPUT_DIR"

cmake "$SCRIPT_DIR" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ABI" \
  -DANDROID_PLATFORM="android-$ANDROID_API" \
  -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release \
  -DWHISPER_CPP_DIR="$WHISPER_CPP_DIR"

cmake --build . --config Release -j"$(nproc 2>/dev/null || echo 4)"

SO_PATH="$OUTPUT_DIR/libzen_whisper.so"
if [ ! -f "$SO_PATH" ]; then
  echo "Build failed: $SO_PATH not found" >&2
  exit 1
fi

DEST_DIR="$ROOT_DIR/app/src/main/jniLibs/$ABI"
mkdir -p "$DEST_DIR"
cp "$SO_PATH" "$DEST_DIR/libzen_whisper.so"

# whisper.cpp's ggml is built with OpenMP, so libomp.so must ship alongside it or
# System.loadLibrary("zen_whisper") fails at runtime.
OMP_SO="$(find "$NDK_HOME" -path "*linux/aarch64/libomp.so" | head -1)"
if [ -z "$OMP_SO" ]; then
  echo "ERROR: libomp.so not found under $NDK_HOME" >&2
  exit 1
fi
cp "$OMP_SO" "$DEST_DIR/libomp.so"
echo "Copied libomp.so -> $DEST_DIR/libomp.so"

echo "Successfully built: libzen_whisper.so -> $DEST_DIR/libzen_whisper.so"
sha256sum "$DEST_DIR/libzen_whisper.so"
stat -c 'Size: %s bytes' "$DEST_DIR/libzen_whisper.so" 2>/dev/null || ls -lh "$DEST_DIR/libzen_whisper.so"
