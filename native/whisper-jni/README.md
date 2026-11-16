# whisper-jni

JNI bridge that runs [whisper.cpp](https://github.com/ggml-org/whisper.cpp) on
device for offline speech-to-text. It produces `libzen_whisper.so`, the library
that `org.zenconverter.app.model.WhisperNative` loads.

Nothing here uploads audio: samples are decoded on device and handed to the
native runtime in process.

## What it exports

| JNI symbol | Kotlin declaration |
|---|---|
| `nativeInit` | `WhisperNative.nativeInit()` |
| `nativeDestroy` | `WhisperNative.nativeDestroy()` |
| `nativeTranscribe` | `WhisperNative.nativeTranscribe(...)` |

`nativeTranscribe` takes mono float PCM in `[-1, 1]`, a sample rate, the model
path, an optional language and optional progress/cancel callbacks. It returns a
JSON transcript with the shape
`{"segments":[{"start":0.0,"end":1.0,"text":" hi","words":[{"word":"hi","start":0.0,"end":1.0}]}]}`,
which `WhisperTranscriptParser` reads.

## Building

```bash
export ANDROID_NDK_HOME=/path/to/ndk
bash native/whisper-jni/build-arm64-v8a.sh
```

The script clones whisper.cpp at the pinned tag, cross-compiles for
`arm64-v8a` at API 26, and copies the result to
`app/src/main/jniLibs/arm64-v8a/libzen_whisper.so`. `.github/workflows/build-whisper.yml`
runs the same script on CI and commits the binary back.

## Model

Inference needs a GGML model. `WhisperModelManager` downloads and verifies it at
runtime; the file name is whatever `WhisperModelManager.modelFile` returns.

## Licensing

whisper.cpp is MIT licensed, which is compatible with this project's AGPL-3.0.
No third-party proprietary code is used.
