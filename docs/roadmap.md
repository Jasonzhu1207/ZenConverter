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
  self-built `arthenica/ffmpeg-kit-next` `v7.1.0` AAR, `arm64-v8a` only,
  recorded in ADR 0007. The current replacement AAR is connected; exact
  rebuild flags still need to be recorded before a tagged release.
- MKV/MOV/WebM/3GP/TS to MP4 re-encode. Implemented, needs physical-device
  smoke testing across samples.
- MP4 to MP4 re-encode. Implemented through the FFmpeg compatibility path so
  visible video/audio options and advanced filters apply consistently; needs
  physical-device smoke testing.
- MP4 to MKV re-encode. FFmpeg Matroska output is connected and needs
  physical-device smoke testing.
- Video to MOV re-encode. FFmpeg QuickTime MOV output is connected and needs
  physical-device smoke testing.
- First-batch advanced audio/video processing. Implemented through existing
  FFmpeg filters for MP4/MKV/MOV video outputs and audio outputs: fade, mirror,
  rotate, frame fit/crop, volume/mute, and echo. Reverse and denoise remain
  future experimental items.
- Video to animated GIF. FFmpeg palettegen/paletteuse output is connected with
  a 30 second, 30 fps, 900 frame cap and 480p default short-side limit; needs
  physical-device smoke testing across MP4/MOV/MKV/WEBM/AVI samples.
- Video-file audio extraction to M4A. Implemented as FFmpeg AAC re-encode for
  MP4 and non-MP4 video sources; needs physical-device smoke testing across
  AAC, MP3, PCM, Vorbis, and Opus source samples.
- MP4 to MP3. Routing and argument generation are connected, and the current
  self-built FFmpegKitNext AAR includes `libmp3lame`; physical-device sample
  verification is still required before this can be treated as working.
- MP3/M4A/WAV/FLAC/WMA audio target conversion. Implemented as FFmpeg true
  re-encode with bitrate, sample-rate, and channel options mapped where the
  target supports them; needs physical-device sample coverage.
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
