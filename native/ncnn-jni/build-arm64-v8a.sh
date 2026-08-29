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

# Download and extract NCNN Android Vulkan SDK
NCNN_EXTRACT_DIR="$SCRIPT_DIR/ncnn-sdk-extract"
if [ ! -d "$NCNN_EXTRACT_DIR" ] || [ -z "$(ls -A "$NCNN_EXTRACT_DIR" 2>/dev/null)" ]; then
  echo "Downloading NCNN Android Vulkan SDK (${NCNN_VERSION})..."
  mkdir -p "$SCRIPT_DIR/download"
  if [ ! -f "$SCRIPT_DIR/download/$NCNN_ZIP" ]; then
    curl -sSfL "$NCNN_URL" -o "$SCRIPT_DIR/download/$NCNN_ZIP"
  fi
  mkdir -p "$NCNN_EXTRACT_DIR"
  unzip -q -o "$SCRIPT_DIR/download/$NCNN_ZIP" -d "$NCNN_EXTRACT_DIR"
fi

# The zip extracts with a versioned root dir, e.g. ncnn-20240410-android-vulkan/arm64-v8a
# Search for the ABI directory wherever it ended up inside the extract root
NCNN_ABI_DIR="$(find "$NCNN_EXTRACT_DIR" -type d -name "$ABI" | head -1)"
if [ -z "$NCNN_ABI_DIR" ] || [ ! -d "$NCNN_ABI_DIR/include/ncnn" ]; then
  echo "ERROR: Could not find $ABI/include/ncnn inside $NCNN_EXTRACT_DIR" >&2
  find "$NCNN_EXTRACT_DIR" -maxdepth 4 | head -30 >&2
  exit 1
fi

echo "Found NCNN SDK at: $NCNN_ABI_DIR"

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cd "$OUTPUT_DIR"

cmake "$SCRIPT_DIR" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ABI" \
  -DANDROID_PLATFORM="android-$ANDROID_API" \
  -DANDROID_STL=c++_static \
  -DCMAKE_BUILD_TYPE=Release \
  -DNCNN_ABI_DIR="$NCNN_ABI_DIR"

cmake --build . --config Release -j"$(nproc 2>/dev/null || echo 4)"

SO_PATH="$OUTPUT_DIR/libzen_ncnn.so"
if [ ! -f "$SO_PATH" ]; then
  echo "Build failed: $SO_PATH not found" >&2
  exit 1
fi

DEST_DIR="$ROOT_DIR/app/src/main/jniLibs/$ABI"
mkdir -p "$DEST_DIR"

cp "$SO_PATH" "$DEST_DIR/libzen_ncnn.so"

# Copy libncnn.so from the SDK too if present (replaces any stale prebuilt)
NCNN_SO="$NCNN_ABI_DIR/lib/libncnn.so"
if [ -f "$NCNN_SO" ]; then
  cp "$NCNN_SO" "$DEST_DIR/libncnn.so"
  echo "Copied libncnn.so from SDK -> $DEST_DIR/libncnn.so"
fi

echo "Successfully built: libzen_ncnn.so -> $DEST_DIR/libzen_ncnn.so"
sha256sum "$DEST_DIR/libzen_ncnn.so"
stat -c 'Size: %s bytes' "$DEST_DIR/libzen_ncnn.so" 2>/dev/null || ls -lh "$DEST_DIR/libzen_ncnn.so"
