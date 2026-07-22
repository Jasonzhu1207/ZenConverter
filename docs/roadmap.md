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

- First MP4 compression/transcoding preset.
- Physical-device test with a large sample.
- Foreground service progress.
- Earlier hardware-engine experiment is now retired; active video outputs use
  the FFmpeg path for consistent option handling.

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
- Video compression presets. Implemented for MP4/MKV/MOV outputs through FFmpeg
  CRF presets: the off/manual state keeps the existing fixed-bitrate/Auto
  behavior, while visual lossless, balanced compression, and small-file modes
  own codec/CRF/preset/resolution/frame-rate strategy plus AAC audio bitrate
  and hide manual video/audio controls. These are visual-quality modes, not
  mathematical lossless
  compression, and already efficient sources can grow.
- Advanced audio/video processing. Implemented through existing FFmpeg filters
  for MP4/MKV/MOV video outputs and audio outputs: video reverse playback,
  fade, mirror, rotate, frame fit/crop; audio reverse playback, `afftdn` audio
  noise reduction, fade, volume/mute, and echo. Video reverse is capped to
  inputs with readable duration and size metadata, up to 60 seconds, and within
  a conservative reverse-frame memory budget. Model-based audio denoise and
  video denoise remain intentionally unconnected.
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
- Metadata safety tool. Implemented as a separate privacy path: images/videos
  can be inspected, and JPG/JPEG/JFIF metadata can be cleaned in place without
  pixel re-encoding. Removed JPEG metadata segments are backed up in app-private
  data and can be restored only for matching same-image imports. Metadata
  editing, spoofing presets, and video metadata cleanup remain intentionally
  unconnected.
- PDF image export/import, PDF merge, PDF TXT/MD text export, and PDF
  encrypt/decrypt are connected through Android PdfRenderer/PdfDocument and
  PDFBox-Android where appropriate. TXT/MD only extract selectable text; PDF
  security tools do not crack unknown passwords.
- Office DOCX/PPTX/XLSX to PDF/TXT/MD is connected through the bundled
  arm64-v8a office2pdf JNI path, with TXT/MD extracted from the intermediate
  PDF. This remains an experimental first-pass renderer with a 64 MiB source
  input cap.
- ZIP archive handling.

## Milestone 5: Public Alpha

- GitHub Releases APK.
- Donation page linked from README.
- No ads, no accounts, no remote upload.
