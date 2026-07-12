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

## Phase 2: Hardware Media Path

Goal: support one high-value 4K-safe path using maintained Android APIs.

- Add AndroidX Media3 Transformer.
- Implement MP4 compression/transcoding presets.
- Prefer MediaCodec hardware acceleration.
- Add clear errors when a device codec cannot handle the file.

Success: a real MP4 video can be transcoded on a physical device without loading
the full file into memory.

## Phase 3: FFmpeg Compatibility Path

Goal: add flexible media conversion without making FFmpeg the only foundation.

- Choose one maintained FFmpeg Android strategy:
  - maintained FFmpegKit fork,
  - self-built FFmpeg AAR,
  - or another actively maintained wrapper.
- Record license flags before shipping binaries.
- Start with MP4 to MP3 and MKV to MP4 stream copy.
- Use file descriptors or SAF-aware APIs when available.

Success: FFmpeg jobs run from Kotlin, report progress, and do not require
duplicating normal local files into cache.

Current first step: a self-built `arthenica/ffmpeg-kit-next` `v7.1.0` AAR is
wired for non-MP4 video container remux to MP4, non-MP4 video-file audio
extraction to M4A by FFmpeg audio-track copy, and experimental audio targets for
MP3/WAV/FLAC/WMA through FFmpeg arguments. The AAR is arm64-v8a only and was
built with `--enable-lame`, so the package now contains the `libmp3lame` encoder
that the earlier Free AAR lacked. This is still not universal video or audio
transcoding: MP3 output needs physical-device sample verification, and WebM
Vorbis/Opus to M4A plus AVI MP3/PCM to M4A need a later route that actually
transcodes non-AAC audio instead of only copying streams.

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
