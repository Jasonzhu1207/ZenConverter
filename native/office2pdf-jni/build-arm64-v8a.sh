#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$ROOT_DIR/built-jniLibs"
ABI="arm64-v8a"

find_ndk_home() {
  local candidate

  for candidate in \
    "${ANDROID_NDK_HOME:-}" \
    "${ANDROID_NDK_ROOT:-}" \
    "/opt/android-ndk"
  do
    if [ -n "$candidate" ] && [ -f "$candidate/source.properties" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  for candidate in /opt/android-ndk/* /opt/android-sdk/ndk/* /root/Android/Sdk/ndk/* /root/android-ndk*; do
    if [ -d "$candidate" ] && [ -f "$candidate/source.properties" ]; then
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

if ! command -v cargo >/dev/null 2>&1; then
  echo "cargo is not installed or not on PATH" >&2
  exit 1
fi

if ! cargo ndk --version >/dev/null 2>&1; then
  echo "cargo-ndk is not installed or not on PATH" >&2
  exit 1
fi

if [ -z "$NDK_HOME" ] || [ ! -f "$NDK_HOME/source.properties" ]; then
  echo "Android NDK root not found." >&2
  echo "Set ANDROID_NDK_HOME to the NDK root, or copy the NDK root to /opt/android-ndk." >&2
  echo "A valid NDK root must contain source.properties and toolchains/." >&2
  exit 1
fi

if ! rustup target list --installed | grep -qx 'aarch64-linux-android'; then
  echo "Missing Rust target: aarch64-linux-android" >&2
  echo "Run: rustup target add aarch64-linux-android" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
export ANDROID_NDK_HOME="$NDK_HOME"

cd "$SCRIPT_DIR"
cargo ndk -t "$ABI" -o "$OUTPUT_DIR" build --release

SO_PATH="$OUTPUT_DIR/$ABI/libzen_office2pdf.so"
if [ ! -f "$SO_PATH" ]; then
  echo "Build finished but shared library was not found: $SO_PATH" >&2
  exit 1
fi

echo "Built: $SO_PATH"
sha256sum "$SO_PATH"
stat -c 'Size: %s bytes' "$SO_PATH"
echo "Replace: app/src/main/jniLibs/arm64-v8a/libzen_office2pdf.so"
