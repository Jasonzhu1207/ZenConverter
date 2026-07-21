# 0014 FFmpeg Reverse And Audio Denoise

## Status

Accepted.

## Context

After MP4/MKV/MOV video outputs and audio outputs moved to FFmpeg true
re-encoding, the app can expose more advanced processing without adding another
engine. The user asked for reverse playback and sound noise reduction, but did
not want new model files, new dependencies, or video denoise because it is too
hardware-expensive.

FFmpeg's `reverse` and `areverse` filters buffer the selected stream, so they
are risky for long or high-resolution media. FFmpeg's `afftdn` audio filter can
provide basic FFT-based audio noise reduction without a model file. Model-based
`arnndn` is intentionally not used because it requires an external model.

## Decision

Add a limited second advanced-processing batch on the existing FFmpegKitNext
compatibility path.

- MP4/MKV/MOV video outputs may reverse the video stream. If output audio is not
  muted, the audio track is reversed as well so normal reverse playback stays in
  sync.
- Video reverse is limited to inputs with readable duration and size metadata,
  up to 60 seconds, and within a conservative reverse-frame memory budget.
  Longer or larger files fail clearly instead of attempting an unstable export.
- Audio outputs and video-output audio tracks may use audio reverse playback.
- Audio outputs and video-output audio tracks may use `afftdn` noise reduction
  with Light and Standard presets.
- The app probes `reverse`, `areverse`, and `afftdn` before export when those
  filters are selected.
- Video-to-GIF keeps its capped palette pipeline and does not expose these
  advanced controls.
- Video denoise and model-based audio denoise are not connected in this
  milestone.

## Consequences

- The feature adds almost no APK size because it reuses the existing FFmpeg AAR.
- Reverse playback is still an experimental destructive re-encode. It should be
  smoke-tested only with very short low-resolution samples; normal phone videos
  can exceed the memory budget quickly.
- Audio noise reduction is basic, non-AI denoise. It may change speech/music
  tone and should not be advertised as studio restoration.
- Metadata, subtitles, attachments, multiple audio tracks, and animation timing
  are still not preserved by this path.
