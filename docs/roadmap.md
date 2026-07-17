# Roadmap

## Milestone 0: Skeleton

- Native Android project.
- Compose placeholder UI.
- Conversion interfaces.
- Support matrix and project guidance.

## Milestone 1: No-op Jobs

- File picker.
- Job queue.
- ViewModel state.
- Fake progress.
- Cancel and failure states.

## Milestone 2: First Real Video Path

- Media3 Transformer dependency.
- MP4 compression preset.
- Physical-device test with a large sample.
- Foreground service progress.

## Milestone 3: FFmpeg Compatibility

- Select maintained FFmpeg strategy. Done for first path:
  self-built `arthenica/ffmpeg-kit-next` `v7.1.0` AAR with `--enable-lame`,
  `arm64-v8a` only, recorded in ADR 0007.
- MKV/WebM/3GP/TS to MP4 stream copy. Implemented, needs physical-device smoke
  testing across samples.
- MP4 video-file audio extraction to M4A. Implemented through Media3, needs
  physical-device smoke testing across samples.
- Non-MP4 video-file audio extraction to M4A. Implemented as FFmpeg audio-track
  copy only; non-AAC tracks need a later AAC-capable compatibility build.
- MP4 to MP3. Routing and argument generation are connected, and the current
  self-built FFmpegKitNext AAR includes `libmp3lame`; physical-device sample
  verification is still required before this can be treated as working.
- MP3/M4A/WAV/FLAC/WMA audio target conversion. Implemented as experimental
  routing and argument generation; needs physical-device sample coverage.
- ffprobe metadata read.

## Milestone 4: Non-media Formats

- JPG/JFIF/PNG/WEBP/ICO static image conversion. Implemented as an experimental
  native bitmap path with GIF first-frame or FFmpeg-backed split-frame input,
  best-effort HEIC/HEIF input, PNG-in-ICO output, and largest-layer PNG-in-ICO
  input; needs physical-device smoke testing across samples.
- PDF image export/import.
- ZIP archive handling.

## Milestone 5: Public Alpha

- GitHub Releases APK.
- Donation page linked from README.
- No ads, no accounts, no remote upload.
