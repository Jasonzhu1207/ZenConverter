# Technical Route

## Product Thesis

Build a local-first file converter that people can trust with private files.
The project should win on honesty, stability, and performance instead of
claiming every format works.

## Phase 0: Native Shell

Goal: prove the Android project structure works before adding heavy libraries.

- Kotlin + Jetpack Compose.
- One activity.
- No-op conversion engine.
- Support matrix and dependency policy in the repository.
- No FFmpegKit dependency at this phase.

Success: the app builds, opens on a physical device, and the placeholder UI is
visible.

## Phase 1: Real Task Pipeline

Goal: make conversion jobs observable and cancelable before real transcoding.

- File picker through Storage Access Framework.
- `ConversionRequest`, `ConversionEvent`, and task registry.
- ViewModel with StateFlow.
- Foreground service skeleton.
- Notification progress for long-running jobs.

Success: a selected file can create a no-op job, show progress, and finish.

## Phase 2: Retired Hardware Media Path

Original goal: support one high-value 4K-safe path using maintained Android
hardware APIs.

This phase was useful as the first real video experiment, but it is no longer
an active engine in the app. It was retired after the FFmpeg path became the
single connected route for MP4/MKV/MOV video outputs and audio outputs, because
hidden engine switching made options and advanced filters harder to reason
about.

Future hardware acceleration should return only as an explicit user-visible
mode, not as an automatic fallback.

## Phase 3: FFmpeg Compatibility Path

Goal: add flexible media conversion with one predictable audio/video engine.

- Choose one maintained FFmpeg Android strategy:
  - maintained FFmpegKit fork,
  - self-built FFmpeg AAR,
  - or another actively maintained wrapper.
- Record license flags before shipping binaries.
- Start with MP4 to MP3, non-MP4 video to MP4 re-encode, MP4 to MKV
  re-encode, and MOV output.
- Use file descriptors or SAF-aware APIs when available.

Success: FFmpeg jobs run from Kotlin, report progress, and do not require
duplicating normal local files into cache.

Current first step: a self-built `arthenica/ffmpeg-kit-next` `v7.1.0` AAR is
wired for MP4/MKV/MOV video re-encode, video-to-GIF output, video-file audio
extraction to M4A by FFmpeg AAC re-encode, and experimental audio targets for
MP3/M4A/WAV/FLAC/WMA through FFmpeg arguments. The advanced filter set is also
connected for MP4/MKV/MOV video outputs and audio outputs: video reverse
playback, fade, mirror, rotate, frame fit/crop; audio reverse playback,
non-model `afftdn` noise reduction, volume/mute, fade, and echo. Video reverse
is limited to inputs with readable duration and size metadata, up to 60 seconds,
and within a conservative reverse-frame memory budget. The current AAR is
`arm64-v8a`
only, and the app probes for needed encoders and selected filters before export
where possible so the wrong package fails clearly. This is still not universal
video or audio transcoding: physical-device sample verification is needed
across MP3, AAC/M4A, WAV, FLAC, WMA, Vorbis, Opus, PCM, mixed video containers,
and advanced filter combinations before these paths should be treated as stable.

## Phase 4: Image, PDF, Archive

Goal: expand only after media task reliability exists.

- Image: JPG, PNG, WEBP, HEIC where feasible.
- PDF: images to PDF, PDF to images, merge and split.
- Archive: ZIP first, then 7Z/RAR extraction if licensing allows.

Success: every new format appears in `formats/support-matrix.md` with tested
examples and known limits.

## Phase 5: Distribution

Goal: stay low-maintenance and open.

- GitHub Releases APK.
- F-Droid metadata if feasible.
- Optional website later.
- China mainland app stores are not a V1 target.

Success: users can install without a store account and inspect the source.
