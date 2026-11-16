#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/build-arm64"
ABI="arm64-v8a"
ANDROID_API="26"
DEMUCS_CPP_DIR="$SCRIPT_DIR/demucs.cpp"

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

if [ ! -f "$DEMUCS_CPP_DIR/src/model.hpp" ]; then
  echo "Fetching demucs.cpp (with submodules)..."
  rm -rf "$DEMUCS_CPP_DIR"
  git clone --depth 1 --recurse-submodules \
    https://github.com/sevagh/demucs.cpp.git "$DEMUCS_CPP_DIR"
fi

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
cd "$OUTPUT_DIR"

cmake "$SCRIPT_DIR" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ABI" \
  -DANDROID_PLATFORM="android-$ANDROID_API" \
  -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=Release \
  -DDEMUCS_CPP_DIR="$DEMUCS_CPP_DIR"

cmake --build . --config Release -j"$(nproc 2>/dev/null || echo 4)"

SO_PATH="$OUTPUT_DIR/libzen_demucs.so"
if [ ! -f "$SO_PATH" ]; then
  echo "Build failed: $SO_PATH not found" >&2
  exit 1
fi

DEST_DIR="$ROOT_DIR/app/src/main/jniLibs/$ABI"
mkdir -p "$DEST_DIR"
cp "$SO_PATH" "$DEST_DIR/libzen_demucs.so"

# demucs.cpp is built with OpenMP, so libomp.so must ship alongside it.
OMP_SO="$(find "$NDK_HOME" -path "*linux/aarch64/libomp.so" | head -1)"
if [ -n "$OMP_SO" ]; then
  cp "$OMP_SO" "$DEST_DIR/libomp.so"
fi
# libc++_shared.so is deliberately not copied: FFmpegKit already ships it and a
# second copy makes AGP's native-lib merge ambiguous.

echo "Successfully built: libzen_demucs.so -> $DEST_DIR/libzen_demucs.so"
sha256sum "$DEST_DIR/libzen_demucs.so"
stat -c 'Size: %s bytes' "$DEST_DIR/libzen_demucs.so" 2>/dev/null || ls -lh "$DEST_DIR/libzen_demucs.so"
