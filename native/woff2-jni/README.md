# WOFF2 JNI source

This documents the reproducible build for `libzen_woff2.so`, the WOFF2
compression/decompression native library behind the font converter. The app
bundles the compiled binary at
`app/src/main/jniLibs/arm64-v8a/libzen_woff2.so`.

The upstream codec is Google woff2 (`https://github.com/google/woff2`,
Apache-2.0) plus Brotli (MIT).

## JNI interface (exact contract)

The Kotlin wrapper is `org.zenconverter.app.font.Woff2Native`. The shared
library must export exactly these two symbols, both `byte[] -> byte[]`:

- `Java_org_zenconverter_app_font_Woff2Native_compress` — SFNT (TTF/OTF) to
  WOFF2 via `woff2::MaxWOFF2CompressedSize` + `woff2::ConvertTTFToWOFF2`.
- `Java_org_zenconverter_app_font_Woff2Native_decompress` — WOFF2 to SFNT via
  `woff2::ComputeWOFF2FinalSize` + `woff2::ConvertWOFF2ToTTF`.

On failure, throw `java.lang.IllegalStateException`.

## 16 KB page-size alignment (required)

Android 15+ devices and Google Play (from November 2025) require native
libraries to support 16 KB page sizes. If the linker keeps the default 4 KB
alignment, the APK build reports:

```
APK is not compatible with 16 KB devices. Some libraries have LOAD segments
not aligned at 16 KB boundaries: lib/arm64-v8a/libzen_woff2.so
```

Fix: link with

```
-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
```

- CMake: `target_link_options(zen_woff2 PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")`
- ndk-build: `LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`
- direct clang: append the same flags to the shared-library link command.

## Reference JNI source

```cpp
#include <jni.h>
#include <cstdint>
#include <vector>

#include "woff2/encode.h"  // or "woff2_enc.h" depending on your include layout
#include "woff2/decode.h"  // or "woff2_dec.h"

namespace {

void ThrowIllegalState(JNIEnv* env, const char* message) {
  jclass clazz = env->FindClass("java/lang/IllegalStateException");
  if (clazz != nullptr) {
    env->ThrowNew(clazz, message);
  }
}

std::vector<uint8_t> ReadByteArray(JNIEnv* env, jbyteArray input) {
  jsize length = env->GetArrayLength(input);
  std::vector<uint8_t> bytes(static_cast<size_t>(length));
  if (length > 0) {
    env->GetByteArrayRegion(input, 0, length, reinterpret_cast<jbyte*>(bytes.data()));
  }
  return bytes;
}

jbyteArray ToByteArray(JNIEnv* env, const std::vector<uint8_t>& bytes) {
  jbyteArray result = env->NewByteArray(static_cast<jsize>(bytes.size()));
  if (result == nullptr) {
    ThrowIllegalState(env, "Font conversion failed: out of memory");
    return nullptr;
  }
  if (!bytes.empty()) {
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(bytes.size()),
                            reinterpret_cast<const jbyte*>(bytes.data()));
  }
  return result;
}

}  // namespace

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_zenconverter_app_font_Woff2Native_compress(JNIEnv* env, jclass, jbyteArray input) {
  if (input == nullptr) {
    ThrowIllegalState(env, "Font input is empty");
    return nullptr;
  }
  std::vector<uint8_t> input_bytes = ReadByteArray(env, input);
  if (input_bytes.empty()) {
    ThrowIllegalState(env, "Font input is empty");
    return nullptr;
  }

  size_t max_size = woff2::MaxWOFF2CompressedSize(input_bytes.data(), input_bytes.size());
  std::vector<uint8_t> output_bytes(max_size);
  size_t output_length = max_size;
  if (!woff2::ConvertTTFToWOFF2(input_bytes.data(), input_bytes.size(),
                                output_bytes.data(), &output_length)) {
    ThrowIllegalState(env, "Font conversion failed");
    return nullptr;
  }
  output_bytes.resize(output_length);
  return ToByteArray(env, output_bytes);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_zenconverter_app_font_Woff2Native_decompress(JNIEnv* env, jclass, jbyteArray input) {
  if (input == nullptr) {
    ThrowIllegalState(env, "Font input is empty");
    return nullptr;
  }
  std::vector<uint8_t> input_bytes = ReadByteArray(env, input);
  if (input_bytes.empty()) {
    ThrowIllegalState(env, "Font input is empty");
    return nullptr;
  }

  size_t final_size = woff2::ComputeWOFF2FinalSize(input_bytes.data(), input_bytes.size());
  if (final_size == 0) {
    ThrowIllegalState(env, "Font conversion failed");
    return nullptr;
  }
  std::vector<uint8_t> output_bytes(final_size);
  if (!woff2::ConvertWOFF2ToTTF(output_bytes.data(), final_size,
                                input_bytes.data(), input_bytes.size())) {
    ThrowIllegalState(env, "Font conversion failed");
    return nullptr;
  }
  return ToByteArray(env, output_bytes);
}
```

## Build and verify

Build on a machine with the Android NDK and a google/woff2 checkout (brotli
submodule initialized), with the 16 KB link flags above. Before checking the
binary in, verify alignment:

```bash
python3 check-16kb-alignment.py app/src/main/jniLibs/arm64-v8a/libzen_woff2.so
# must print PASS for every LOAD segment
```

Then record the new size and SHA-256 in `third_party/THANKS.md` and
`docs/license-and-attribution.md`. Codex must not run this build.

The checked-in binary is 16 KB-aligned (linked with
`-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`).
