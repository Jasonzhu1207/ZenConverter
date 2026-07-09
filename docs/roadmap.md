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
  `dev.ffmpegkit-maintained:ffmpeg-kit-free-71:7.1.5` LGPL Free tier, with a
  matching local Release AAR fallback when Maven Central is blocked.
- MKV/WebM/3GP/TS to MP4 stream copy. Implemented, needs physical-device smoke
  testing across samples.
- MP4 video-file audio extraction to M4A. Implemented through Media3, needs
  physical-device smoke testing across samples.
- Non-MP4 video-file audio extraction to M4A. Implemented as FFmpeg audio-track
  copy only; non-AAC tracks need a later AAC-capable compatibility build.
- MP4 to MP3.
- ffprobe metadata read.

## Milestone 4: Non-media Formats

- JPG/PNG/WEBP static image conversion. Implemented as an experimental native
  bitmap path; needs physical-device smoke testing across samples.
- PDF image export/import.
- ZIP archive handling.

## Milestone 5: Public Alpha

- GitHub Releases APK.
- Donation page linked from README.
- No ads, no accounts, no remote upload.
