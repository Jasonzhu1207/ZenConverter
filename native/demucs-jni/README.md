# demucs-jni

JNI bridge that runs [demucs.cpp](https://github.com/sevagh/demucs.cpp) on device
to split a mix into vocals, drums, bass and other. It produces
`libzen_demucs.so`, the library that `org.zenconverter.app.model.StemNative`
loads.

Separation is fully offline: audio is decoded on device and handed to the native
runtime in process.

## What it exports

| JNI symbol | Kotlin declaration |
|---|---|
| `nativeStemInit` | `StemNative.nativeInit()` |
| `nativeStemSeparate` | `StemNative.nativeSeparate(...)` |

`nativeStemSeparate` takes mono float PCM in `[-1, 1]`, its sample rate, the
model path and an output directory. It writes stereo 16-bit PCM WAV files named
`drums.wav`, `bass.wav`, `other.wav` and `vocals.wav` (demucs.cpp's source
order) and returns how many were written, or a negative value on failure.

demucs.cpp expects 44.1 kHz stereo, so mono input is duplicated and other rates
are resampled.

## Building

```bash
export ANDROID_NDK_HOME=/path/to/ndk
bash native/demucs-jni/build-arm64-v8a.sh
```

The script clones demucs.cpp with its submodules (Eigen), cross-compiles for
`arm64-v8a` at API 26, and copies the result plus its `libomp.so` dependency to
`app/src/main/jniLibs/arm64-v8a/`. `libc++_shared.so` is left out because
FFmpegKit already ships one and a second copy makes AGP's native-lib merge
ambiguous. `.github/workflows/build-demucs.yml` runs the same script on CI and
commits the binaries back.

## Model

Inference needs the Demucs v4 htdemucs ggml weights. `StemSeparationModelManager`
downloads and verifies the file at runtime; the name and hash are pinned there.

## Licensing

demucs.cpp is MIT licensed, which is compatible with this project's AGPL-3.0.
No third-party proprietary code is used.
