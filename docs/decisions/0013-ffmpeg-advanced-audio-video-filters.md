# 0013 FFmpeg Advanced Audio Video Filters

## Status

Accepted.

## Context

After video and audio outputs moved to true FFmpeg re-encoding, the app can make
more of the visible controls real without adding another media engine. The user
asked for a first batch of Format Factory-style advanced controls while keeping
the APK size and dependency surface stable.

## Decision

Use the existing FFmpegKitNext compatibility path for the first advanced
audio/video processing batch.

- MP4/MKV/MOV video outputs may apply video fade in/out, horizontal/vertical
  mirror, 90/180 degree rotation, and fit/crop frame-shape filters.
- Audio outputs and video-output audio tracks may apply audio fade in/out,
  volume/mute, and echo filters.
- Video mute removes the output audio track. Audio-output mute uses `volume=0`
  so the selected audio target is still written as a silent re-encode.
- Video-to-GIF keeps its existing capped palette pipeline and does not expose
  these advanced controls in this decision.
- Fade-out requires readable duration metadata. If duration cannot be read, the
  task fails clearly instead of silently skipping the selected effect.
- The app probes selected FFmpeg filters where possible and fails clearly if the
  bundled compatibility package is missing one.

Reverse and denoise are left out of this batch. Reverse can be expensive for
long or high-resolution media, and denoise needs separate filter-capability and
quality testing before being advertised.

## Consequences

- The advanced menu adds almost no APK size because it reuses the checked-in
  FFmpegKitNext AAR.
- Advanced processing is always a destructive re-encode; it does not promise
  lossless output, metadata retention, subtitles, attachments, or multi-track
  preservation.
- MP4-to-MP4 now uses the FFmpeg compatibility path so video/audio options and
  advanced filters apply consistently across MP4/MKV/MOV outputs.
- Physical-device samples remain required before promoting these features beyond
  Experimental.
