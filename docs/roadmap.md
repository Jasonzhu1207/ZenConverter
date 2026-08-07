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
- Physical-device verification with large samples.
- Foreground service progress.
- Earlier hardware-engine experiment is now retired; active video outputs use
  the FFmpeg path for consistent option handling.

## Milestone 3: FFmpeg Compatibility

- The self-built `arthenica/ffmpeg-kit-next` `v7.1.0` AAR is connected and
  verified on `arm64-v8a` devices. Its exact rebuild flags remain a release
  provenance item before a future binary replacement.
- Verified video re-encode: MP4/MKV/MOV/WEBM/AVI/3GP/3GPP/TS/MTS inputs can
  produce MP4/MKV/MOV outputs through FFmpeg, including MP4-to-MP4. Visible
  video and audio controls apply to the real re-encode path.
- Verified CRF compression presets for MP4/MKV/MOV. Manual mode keeps
  fixed-bitrate/Auto behavior; visual-lossless, balanced, and small-file modes
  own codec/CRF/preset/resolution/frame-rate strategy plus AAC audio bitrate.
  These are visual-quality modes, not mathematical lossless compression.
- Verified integer start/end-second trimming for FFmpeg video and audio outputs.
  Trimming is a re-encode range selection, not byte-exact lossless cutting; GIF
  output uses the selected range before its 30 second cap.
- Verified advanced FFmpeg filters: video reverse, fade, mirror, rotate, and
  fit/crop; audio reverse, `afftdn` noise reduction, fade, volume/mute, and
  echo. Video reverse remains capped to inputs with readable duration and size
  metadata, up to 60 seconds, and within its conservative memory budget.
- Verified animated GIF output using palettegen/paletteuse, with a 30 second,
  30 fps, 900 frame cap and a 480 px default short-side limit.
- Verified audio extraction and true re-encode for MP3/M4A/WAV/FLAC/WMA,
  including video-source audio extraction. Applicable bitrate, sample-rate,
  channel, and advanced audio options are mapped to FFmpeg arguments.

## Milestone 4: Non-media Formats

- Verified JPG/JFIF/PNG/WEBP/ICO static image conversion, GIF first-frame and
  split-frame handling, PNG-in-ICO output, and largest-layer PNG-in-ICO input.
  HEIC/HEIF remains device-dependent because it relies on the platform decoder.
- Verified metadata safety tool: images/videos
  can be inspected, and JPG/JPEG/JFIF metadata can be cleaned in place without
  pixel re-encoding. Removed JPEG metadata segments are backed up in app-private
  data and can be restored only for matching same-image imports. Metadata
  editing, spoofing presets, and video metadata cleanup remain intentionally
  unconnected.
- Verified PDF image export/import, PDF merge, PDF TXT/MD text export, and PDF
  encrypt/decrypt through Android PdfRenderer/PdfDocument and
  PDFBox-Android where appropriate. TXT/MD only extract selectable text; PDF
  security tools do not crack unknown passwords.
- DOCX/PPTX/XLSX to PDF/TXT/MD is usable through the bundled `arm64-v8a`
  Office2PDF JNI path, with TXT/MD extracted from the intermediate PDF. It is a
  limited compatibility route, not a high-fidelity Microsoft Office renderer;
  source input remains capped at 64 MiB.

## Milestone 5: Public Release Foundation

- GitHub Releases APK and continuous pre-release automation.
- Donation page linked from README.
- No ads, no accounts, no remote upload.
- Future quality work: an automated sample suite, a `SafeCache` fallback for
  non-seekable file providers, and documented rebuild provenance for replacement
  FFmpeg binaries.

## Later Scope

- ZIP archive handling, after its streaming, traversal, and archive-bomb safety
  boundaries are designed explicitly.
