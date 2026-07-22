# 0016 - FFmpeg Video Compression Presets

## Status

Accepted.

## Context

ZenConverter already routes MP4/MKV/MOV video outputs through the checked-in
FFmpegKitNext compatibility engine and probes for `libx264`, `libx265`, and AAC
encoding support. The existing Auto bitrate path uses CRF defaults, but users
need a clearer way to choose high-quality compression without setting a fixed
bitrate by hand.

The requested behavior is "visually lossless large size reduction", but this
must not be represented as mathematical lossless compression. CRF encoding can
preserve perceived quality well on many sources and often shrinks high-bitrate
phone or camera videos significantly, but already efficient or low-bitrate
sources can become larger after re-encoding.

## Decision

Add a compact fixed compression preset control for MP4/MKV/MOV outputs only:

- Off/manual: keep the existing behavior. Fixed bitrate uses `-b:v`; Auto
  bitrate uses CRF 23 for H.264 or CRF 28 for H.265 with the current fast
  preset. Manual video codec, resolution, frame-rate, and video advanced
  controls remain visible, as do video-output audio controls.
- Visual lossless: force CRF output, using H.264 CRF 18 or H.265 CRF 20 with
  preset `medium`; prefer H.265 when available; keep original resolution and
  original frame rate; encode AAC audio at 192 kbps.
- Balanced compression: force CRF output, using H.264 CRF 21 or H.265 CRF 24
  with preset `medium`; prefer H.265 when available; cap the short side at
  1080 px; encode AAC audio at 160 kbps.
- Small file: force CRF output, using H.264 CRF 24 or H.265 CRF 28 with preset
  `medium`; prefer H.265 when available; cap the short side at 720 px and frame
  rate at 30 fps; encode AAC audio at 128 kbps.

The three fixed presets hide and ignore manual video codec, fixed bitrate,
resolution, frame-rate, audio options, and advanced controls. This keeps them
honest as one-click size/quality strategies instead of partial bitrate presets.

GIF output does not expose this option and keeps its capped palette pipeline.
Audio remains AAC for video outputs; the visual compression label does not imply
audio losslessness.

## Consequences

- Users get a direct compression workflow without adding a new dependency or
  changing the FFmpeg binary.
- MP4/MKV/MOV output behavior remains true re-encoding, so advanced filters and
  audio options continue to apply.
- Fixed presets may be slower than off/manual mode because they use preset
  `medium`.
- The app should continue showing output size changes. If output is larger than
  the input, the UI should explain that the source may already be efficiently
  compressed.
- Source files are never deleted automatically, even when the compressed result
  is larger than the input.
