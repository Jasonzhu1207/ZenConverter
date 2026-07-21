# 0015 Retire Media3 Transformer Path

## Status

Accepted.

## Context

The app originally used AndroidX Media3 Transformer as the first real MP4 export
path. Since then, the checked-in FFmpegKitNext AAR has become the connected
engine for MP4, MKV, MOV, video-to-GIF, audio extraction, audio conversion, and
the first advanced audio/video filters.

Keeping an unused Media3 branch created two problems:

- APK and dependency surface stayed larger than the current product needed.
- Hidden engine switching made visible options harder to reason about. FFmpeg
  filters, audio options, and re-encode settings only apply consistently when
  the media task uses the same engine.

## Decision

Remove the active Media3 Transformer implementation and Gradle dependency.

All currently connected video container outputs, video-to-GIF, audio extraction,
and audio conversion should use the FFmpeg compatibility path. Platform Android
APIs remain in use for image/PDF work and media metadata helpers. Video codec
availability should be validated through the FFmpeg encoder probes used by the
actual export path, not through device hardware encoder enumeration.

Future hardware acceleration can be reconsidered, but only as an explicit
user-visible mode such as a fast hardware MP4 preset. It should not return as an
automatic hidden fallback that bypasses visible FFmpeg-only options.

## Consequences

- Dependency and APK surface are smaller.
- Current audio/video behavior is easier to document and test because options
  route through one engine.
- Simple MP4 hardware acceleration may be less efficient until a deliberate
  hardware mode is designed and tested.
- Historical ADRs that describe the first Media3 experiments remain unchanged
  as project history.
