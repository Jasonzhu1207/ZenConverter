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
- New `ImageSuperResolutionMode.RealEsrgan4x(4)` enum value carried on
  `ImageExportOptions`; no other data-model change.
- `RealEsrganUpscaler` loads the model via `OrtEnvironment.getEnvironment()` +
  `createSession(modelPath, options)` and runs tiled inference (tile=512,
  tilePad=16, constant and tunable) so model memory is bounded to one tile
  instead of the whole image. Input/output are NCHW RGB in `[0,1]`. Every ONNX
  `AutoCloseable` object (input `OnnxTensor`, `OrtSession.Result`, `OrtSession`)
  is closed with `.use {}` / `finally`; the shared `OrtEnvironment` singleton is
  not closed per call. Semi-transparent pixels are composited onto white because
  the model emits opaque RGB only.
- `EsrganModelManager` downloads `RealESRGAN_x4plus.onnx` from
  `https://assets.xlab.my/models/RealESRGAN_x4plus.onnx` (63.9 MB,
  `67,051,973` bytes) into app-private storage, streaming to a `.part` file,
  and rejects the result unless the SHA-256 equals
  `39d5218cfcef542d667821a0d2072cfa51bfd857ab0e4ae7dc067c399a88d323`.
- The model list is hardcoded; there is no remote hot-updated config. Adding or
  changing a model is a code change that ships with an app update.
- UI: the super-resolution dropdown lists `2× Bilinear`, `3× Bilinear`,
  `4× Bilinear`, and `Real-ESRGAN 4× (AI)`. The AI option renders grayed out
  with a lock icon and is non-clickable until the model is downloaded; a hint
  under the control points to Settings. Settings gains a "Model download"
  section showing the model name, purpose, source, size, download progress, and
  a foreground-only download note.

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
