#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/build-arm64"
ABI="arm64-v8a"
ANDROID_API="26"
NCNN_VERSION="20240410"
NCNN_ZIP="ncnn-${NCNN_VERSION}-android-vulkan.zip"
NCNN_URL="https://github.com/Tencent/ncnn/releases/download/${NCNN_VERSION}/${NCNN_ZIP}"

find_ndk_home() {
  local candidate
  for candidate in \
    "${ANDROID_NDK_HOME:-}" \
    "${ANDROID_NDK_ROOT:-}" \
    "/opt/android-ndk" \
    "/opt/android-sdk/ndk/*" \
    "/root/Android/Sdk/ndk/*" \
    "/root/android-ndk*" \
    "$HOME/Android/Sdk/ndk/*"
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

# Ensure NCNN Android Vulkan SDK is present
NCNN_SDK_DIR="$SCRIPT_DIR/ncnn-android-vulkan"
if [ ! -d "$NCNN_SDK_DIR/$ABI" ]; then
  echo "Downloading NCNN Android Vulkan SDK (${NCNN_VERSION})..."
  mkdir -p "$SCRIPT_DIR/download"
  if [ ! -f "$SCRIPT_DIR/download/$NCNN_ZIP" ]; then
    curl -sSL "$NCNN_URL" -o "$SCRIPT_DIR/download/$NCNN_ZIP"
  fi
  unzip -q -o "$SCRIPT_DIR/download/$NCNN_ZIP" -d "$NCNN_SDK_DIR"
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
  -DNCNN_SDK_DIR="$NCNN_SDK_DIR/$ABI"

cmake --build . --config Release -j"$(nproc 2>/dev/null || echo 4)"

SO_PATH="$OUTPUT_DIR/libzen_ncnn.so"
if [ ! -f "$SO_PATH" ]; then
  echo "Build failed: $SO_PATH not found" >&2
  exit 1
fi

DEST_DIR="$ROOT_DIR/app/src/main/jniLibs/$ABI"
mkdir -p "$DEST_DIR"

# Copy libzen_ncnn.so
cp "$SO_PATH" "$DEST_DIR/libzen_ncnn.so"

# Also ensure libncnn.so from the SDK is copied to jniLibs
if [ -f "$NCNN_SDK_DIR/$ABI/lib/libncnn.so" ]; then
  cp "$NCNN_SDK_DIR/$ABI/lib/libncnn.so" "$DEST_DIR/libncnn.so"
fi

echo "Successfully built and copied libzen_ncnn.so -> $DEST_DIR/libzen_ncnn.so"
sha256sum "$DEST_DIR/libzen_ncnn.so"
stat -c 'Size: %s bytes' "$DEST_DIR/libzen_ncnn.so" 2>/dev/null || ls -lh "$DEST_DIR/libzen_ncnn.so"
