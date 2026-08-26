# 0019 - Real-ESRGAN Deep-Learning Super-Resolution (ONNX Runtime)

## Status

Accepted.

## Context

The image super-resolution feature shipped as a bilinear algorithmic first step
(decision 0018), with `applySuperResolutionIfNeeded` explicitly named as the
replacement seam for a future deep-learning upscaler. That future is now here:
add a Real-ESRGAN 4× model path that runs locally through ONNX Runtime, keeping
the existing bilinear path untouched.

Requirements that shaped this decision:

- The deep-learning option must be clearly distinguished from the bilinear
  upscales in the existing image export menu.
- The `.onnx` model must not be bundled in the APK; it is downloaded at runtime
  from an R2 direct link and verified against a hardcoded SHA-256.
- The app targets Google Play, so the model must be loaded only through the
  normal ONNX Runtime API (`OrtEnvironment` + `OrtSession`) as data — never via
  `ClassLoader`, `System.load`, or reflection, which would violate Play's
  dynamic-code-loading rules.
- Before the model is downloaded, the AI option must stay visible but disabled,
  pointing the user to Settings.
- ONNX Runtime objects wrap native C++ pointers; the JVM GC cannot reclaim that
  native memory, so every `AutoCloseable` ONNX object must be closed explicitly.

## Decision

Add a Real-ESRGAN 4× deep-learning super-resolution path alongside bilinear:

- New dependency: `com.microsoft.onnxruntime:onnxruntime-android:1.29.0`
  (MIT), packaged `arm64-v8a` only through the existing `abiFilters`.
- New `ImageSuperResolutionMode.RealEsrGeneral4xV3(4)` and
  `ImageSuperResolutionMode.RealEsrgan4x(4)` enum values carried on
  `ImageExportOptions`; no other data-model change.
- `RealEsrganUpscaler` loads the model via `OrtEnvironment.getEnvironment()` +
  `createSession(modelPath, options)` and runs tiled inference (tile=256,
  tilePad=16, constant and tunable) so model memory is bounded to one tile
  instead of the whole image. Input/output are NCHW RGB in `[0,1]`. Every ONNX
  `AutoCloseable` object (input `OnnxTensor`, `OrtSession.Result`, `OrtSession`)
  is closed with `.use {}` / `finally`; the shared `OrtEnvironment` singleton is
  not closed per call. Semi-transparent pixels are composited onto white because
  the model emits opaque RGB only.
- `EsrganModelManager` supports two hardcoded model choices:
  1. `realesr-general-x4v3` (4.65 MB, `4,873,412` bytes, SHA-256 `04c4cfea5759f94e5b5ab98b5d1ef176b904bbcd670a3b661e99e623374fc370`)
     from `https://assets.xlab.my/models/realesr-general-x4v3.onnx`
  2. `RealESRGAN_x4plus` (63.9 MB, `67,051,973` bytes, SHA-256 `39d5218cfcef542d667821a0d2072cfa51bfd857ab0e4ae7dc067c399a88d323`)
     from `https://assets.xlab.my/models/RealESRGAN_x4plus.onnx`
  Each model downloads into app-private storage, streaming to a `.part` file,
  and rejects the result unless size and SHA-256 match.
- The model list is hardcoded; there is no remote hot-updated config. Adding or
  changing a model is a code change that ships with an app update.
- UI: the super-resolution dropdown lists `2× Bilinear`, `3× Bilinear`,
  `4× Bilinear`, `realesr-general-x4v3 4× (AI)`, and `Real-ESRGAN 4× (AI)`. AI
  options render disabled until their respective model is downloaded; a hint
  under the control points to Settings if none are downloaded. Settings provides
  a "Model download" section displaying all available models with download buttons,
  progress bars, size displays, and source links.

## Consequences

- The existing bilinear path is unchanged; the new mode is expressed through the
  same `applySuperResolutionIfNeeded` seam that 0018 reserved for this purpose.
- Output pixel budget and `OutOfMemoryError` backstop are shared with bilinear,
  but tiling bounds the model's own peak memory to a single tile.
- The AI path is much slower than bilinear (fp32 CPU inference) and drops
  transparency; both facts are stated in the option summary.
- Cancellation is checked between tiles; a single tile's forward pass is not
  interruptible.
- Native memory safety is a hard code-review rule: any ONNX `AutoCloseable` that
  escapes a `.use {}` block in the tile loop would leak and eventually crash
  with a native OOM.
- The model download adds a network call, gated behind an explicit user action
  in Settings (consistent with the update-check policy) and verified by hash.
- Emulator limitation: ONNX Runtime 1.29.0 segfaults inside its ELF constructors
  (CPU topology detection reads `/sys/devices/system/cpu/.../shared_cpu_list`)
  when the arm64 library runs on an x86 emulator through the ARM-to-x86
  translation bridge. `RealEsrganUpscaler` detects emulators and refuses to load
  the library there, failing the conversion with a clear message instead of
  crashing. AI upscaling is therefore unavailable on emulators; it remains
  available on arm64 physical devices.
