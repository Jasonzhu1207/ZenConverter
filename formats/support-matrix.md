# Support Matrix

This file is the public truth for conversion support. Do not advertise a format
as supported until it has a tested path, sample files, and failure behavior.

## Status Labels

- `Stable`: tested on real files and large inputs where relevant.
- `Beta`: usable, but edge cases are expected.
- `Experimental`: hidden or clearly labeled.
- `Planned`: not implemented.
- `Unsupported`: intentionally unsupported, with a reason.

## Current Matrix

| Input | Output | Status | Engine | Notes |
| --- | --- | --- | --- | --- |
| Any | Any | Planned | None | Do not imply universal support. |
| MP4 / other video audio tracks | MP3 / WAV / FLAC / WMA | Experimental | FFmpeg compatible | Extracts the first audio track and encodes the selected audio target. MP3 requires an MP3-capable FFmpeg package; the default Free AAR does not provide it. Subtitle, attachment, and extra audio tracks are not copied. |
| MP4 | MP4 | Experimental | Media3 Transformer | Native MP4 output path; physical-device verification still required across samples. |
| MKV / WEBM / AVI / 3GP / 3GPP / TS / MTS | MP4 | Experimental | FFmpeg compatible | Stream-copy remux to MP4. Success requires MP4-compatible video/audio streams; incompatible codecs fail clearly instead of re-encoding. |
| MP3 / M4A / FLAC / WAV / WMA | MP3 / WAV / FLAC / WMA | Experimental | FFmpeg compatible | Common audio conversion path. MP3 requires an MP3-capable FFmpeg package; the default Free AAR does not provide it. WMA uses bitrate when selected; WAV/FLAC ignore bitrate and accept sample-rate/channel controls. |
| MP3 / M4A / FLAC / WAV / OGG | M4A | Experimental | Media3 Transformer | Output is AAC in M4A. Bitrate, sample-rate, and channel controls are best-effort native settings. |
| WMA | M4A | Experimental | FFmpeg compatible | Attempts AAC/M4A through FFmpeg compatibility mode; fails clearly if the bundled FFmpeg package lacks AAC encoding. |
| MP4 video audio tracks | M4A | Experimental | Media3 Transformer | Audio extraction to AAC/M4A through the native path. Bitrate, sample-rate, and channel controls are best-effort native settings. |
| MKV / WEBM / 3GP / TS / AVI video audio tracks | M4A | Experimental | FFmpeg compatible | Audio-track copy only. Success currently requires an AAC audio stream that can be written to M4A. WebM Vorbis/Opus and AVI MP3/PCM need a future AAC-capable compatibility build. |
| JPG / JPEG / PNG / WEBP | JPG / PNG / WEBP | Experimental | Native Bitmap | Static image conversion through Android platform bitmap APIs; physical-device smoke testing is still pending. JPG/WEBP quality presets are Original 100, High 95, Balanced 85, Small 60. PNG is written as lossless output. Transparency is preserved for PNG/WEBP and flattened to white for JPG. Metadata is not copied, though JPEG EXIF orientation is applied best-effort; animated WEBP is not preserved as animation. |
| PNG | PDF | Planned | PDF engine | Candidate after image engine. |

## Current Native Media Limits

- Video targets are intentionally limited to MP4.
- Audio targets are connected for MP3, M4A, WAV, FLAC, and WMA. MP3 additionally
  requires an MP3-capable FFmpeg package. These paths remain experimental until
  physical-device sample tests cover the new combinations.
- MP3, M4A, WAV, FLAC, and OGG inputs targeting M4A are not universal promises. They
  stay on the AndroidX Media3 path and may fail on files whose codec, DRM,
  timestamp layout, or device codec support falls outside Media3 and platform
  capabilities.
- Non-MP4 video containers are now split to the first FFmpeg compatibility path
  for MP4 remux or M4A audio-track copy. M4A extraction succeeds only when the
  source audio stream is already M4A-compatible, usually AAC. WebM Vorbis/Opus
  and AVI MP3/PCM are intentionally left unsupported in this milestone.
- Native Media3 export uses a 60 second muxer-sample watchdog. If no output
  sample is written within that window, the task fails clearly and should be
  retested later through the planned compatibility engine.

## Current FFmpeg Compatibility Limits

- The first FFmpeg path uses
  `dev.ffmpegkit-maintained:ffmpeg-kit-free-71:7.1.5` or the matching local
  Release AAR fallback, the LGPL-3.0 Free tier of the maintained Android-only
  FFmpegKit fork.
- Video compatibility output is currently a stream-copy remux to MP4:
  `-map 0:v:0 -map 0:a:0? -c copy`. It does not re-encode H.264/H.265.
- Subtitles, attachments, extra audio tracks, and unknown streams are not copied
  in this first path.
- MP3, WAV, FLAC, and WMA audio targets use FFmpeg compatibility arguments:
  `libmp3lame`, `pcm_s16le`, `flac`, and `wmav2` respectively. MP3/WMA pass
  selected bitrate, sample-rate, and channel options. WAV/FLAC pass sample-rate
  and channel options, but intentionally do not pass bitrate.
- Physical-device logs on July 11, 2026 confirmed that the default local Free
  AAR returns `Unknown encoder 'libmp3lame'`; MP3 output therefore needs an
  MP3-capable FFmpegKit tier or a later self-built LGPL FFmpeg package. The app
  probes for `libmp3lame` before MP3 export and fails with a specific message
  when the bundled package cannot encode MP3.
- MP4 video-file audio extraction to M4A remains on Media3. Non-MP4 video-file
  audio extraction to M4A uses FFmpeg stream copy only. The Free-tier AAR does
  not provide AAC encoding in the documented local setup, so non-AAC audio still
  needs a future compatibility build.
- WMA to M4A is routed through FFmpeg because Android native decode support is
  not reliable. It attempts AAC output and fails with a specific encoder message
  if the bundled package cannot encode AAC.
- SAF input is passed to FFmpeg through FFmpegKit's SAF parameter when possible,
  with `/proc/self/fd/{fd}` retained as a fallback. Cache fallback for
  non-seekable providers is still future `SafeCache` work.
- No automated audio sample suite exists yet. Physical-device smoke testing
  should cover MP3, M4A, WAV, FLAC, WMA, and at least one video audio-extraction
  sample before raising these rows beyond `Experimental`.

## Current Native Image Limits

- Image targets are intentionally limited to JPG, PNG, and WEBP.
- Image inputs are limited in the picker to JPG/JPEG, PNG, and WEBP.
- The first image path uses Android platform bitmap decode/encode APIs and does
  not add a third-party image dependency.
- JPG/WEBP `Original` means quality value 100 in a platform re-encode. It is
  not a byte-identical no-recompression copy; same-format FastCopy remains a
  future improvement.
- EXIF, color profile metadata, and other container metadata are not copied in
  this milestone. JPEG EXIF orientation is applied best-effort before writing
  the output pixels.
- Extremely large images may be sampled down by the image memory guard before
  encoding to avoid foreground-service crashes.
- Animated WEBP is not preserved as animation; this path should be treated as
  static-image conversion only.
