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
| MP4 / other video audio tracks | MP3 / WAV / FLAC / WMA | Experimental | FFmpeg compatible | Extracts the first audio track and encodes the selected audio target. The current self-built FFmpegKitNext AAR includes `libmp3lame`; MP3 still needs physical-device sample verification. Subtitle, attachment, and extra audio tracks are not copied. |
| MP4 | MP4 | Experimental | Media3 Transformer | Native MP4 output path; physical-device verification still required across samples. |
| MKV / WEBM / AVI / 3GP / 3GPP / TS / MTS | MP4 | Experimental | FFmpeg compatible | Stream-copy remux to MP4. Success requires MP4-compatible video/audio streams; incompatible codecs fail clearly instead of re-encoding. |
| MP3 / M4A / FLAC / WAV / WMA | MP3 / WAV / FLAC / WMA | Experimental | FFmpeg compatible | Common audio conversion path. The current self-built FFmpegKitNext AAR includes `libmp3lame`; MP3 still needs physical-device sample verification. WMA uses bitrate when selected; WAV/FLAC ignore bitrate and accept sample-rate/channel controls. |
| MP3 / M4A / FLAC / WAV / OGG | M4A | Experimental | Media3 Transformer | Output is AAC in M4A. Bitrate, sample-rate, and channel controls are best-effort native settings. |
| WMA | M4A | Experimental | FFmpeg compatible | Attempts AAC/M4A through FFmpeg compatibility mode; fails clearly if the bundled FFmpeg package lacks AAC encoding. |
| MP4 video audio tracks | M4A | Experimental | Media3 Transformer | Audio extraction to AAC/M4A through the native path. Bitrate, sample-rate, and channel controls are best-effort native settings. |
| MKV / WEBM / 3GP / TS / AVI video audio tracks | M4A | Experimental | FFmpeg compatible | Audio-track copy only. Success currently requires an AAC audio stream that can be written to M4A. WebM Vorbis/Opus and AVI MP3/PCM need a future AAC-capable compatibility build. |
| JPG / JPEG / PNG / WEBP | JPG / PNG / WEBP | Experimental | Native Bitmap | Static image conversion through Android platform bitmap APIs; physical-device smoke testing is still pending. JPG/WEBP quality presets are Original 100, High 95, Balanced 85, Small 60. PNG is written as lossless output. Transparency is preserved for PNG/WEBP and flattened to white for JPG. Metadata is not copied, though JPEG EXIF orientation is applied best-effort; animated WEBP is not preserved as animation. |
| JPG / JPEG / PNG / WEBP | PDF | Experimental | Android PdfDocument | Creates one PDF page per image. A4-fit and original-ratio page modes preserve image ratio and use a white page background. Multiple selected images can become one multi-page PDF or one PDF per image. |
| PDF | JPG / PNG / WEBP | Experimental | Android PdfRenderer | Renders each PDF page to one image file. This is page rasterization, not OCR, text extraction, or embedded-image extraction. Multi-page outputs use one task and same-sized page images. |
| Multiple PDFs | PDF | Experimental | PDFBox-Android | Merges selected PDFs as page objects instead of rasterizing them. Normal text layers and vector content are preserved best-effort; complex forms, bookmarks, attachments, and metadata are not guaranteed. |
| PDF | TXT | Experimental | PDFBox-Android | Extracts selectable text with page separators. This is not OCR; scanned PDFs without a text layer fail clearly. |

## Current Native Media Limits

- Video targets are intentionally limited to MP4.
- Audio targets are connected for MP3, M4A, WAV, FLAC, and WMA. The current
  self-built FFmpegKitNext package includes `libmp3lame` for MP3 output. These
  paths remain experimental until physical-device sample tests cover the new
  combinations.
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

- The FFmpeg path uses the local self-built
  `app/libs/ffmpeg-kit-next-7.1.0-lame-armeabi-v7a-arm64-v8a.aar`, built from
  `arthenica/ffmpeg-kit-next` tag `v7.1.0`, commit
  `1e64a8cdda1b045b014c0a54e9d395929c7b6ccc`, with SHA-256
  `6f3bb932ba76ff2627bef6cbfd77fa24bb7186afe27d88da37f69cd60c207602`.
- Video compatibility output is currently a stream-copy remux to MP4:
  `-map 0:v:0 -map 0:a:0? -c copy`. It does not re-encode H.264/H.265.
- Subtitles, attachments, extra audio tracks, and unknown streams are not copied
  in this first path.
- MP3, WAV, FLAC, and WMA audio targets use FFmpeg compatibility arguments:
  `libmp3lame`, `pcm_s16le`, `flac`, and `wmav2` respectively. MP3/WMA pass
  selected bitrate, sample-rate, and channel options. WAV/FLAC pass sample-rate
  and channel options, but intentionally do not pass bitrate.
- Physical-device logs on July 11, 2026 confirmed that the earlier local Free
  AAR returned `Unknown encoder 'libmp3lame'`. The current self-built AAR has
  `CONFIG_LIBMP3LAME` and `CONFIG_LIBMP3LAME_ENCODER` enabled. The app still
  probes for `libmp3lame` before MP3 export and fails with a specific message
  if the wrong FFmpeg package is bundled.
- MP4 video-file audio extraction to M4A remains on Media3. Non-MP4 video-file
  audio extraction to M4A uses FFmpeg stream copy only, so non-AAC audio still
  needs a future route that transcodes to AAC instead of only copying streams.
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

## Current Native PDF Limits

- Image to PDF uses Android `PdfDocument`; PDF to image uses Android
  `PdfRenderer`. PDF merge and PDF to TXT use PDFBox-Android because Android
  platform APIs do not provide broad true PDF merge or cross-version text
  extraction.
- Image to PDF decodes one image at a time, caps source decode to a 4096 px long
  side and 16 MP, applies JPEG orientation best-effort, and flattens the page
  onto white.
- PDF to image opens and closes one page at a time. It pre-scans page sizes,
  reuses one `ARGB_8888` bitmap, and renders every page into a common output
  size so multi-page outputs have consistent dimensions.
- PDF render presets are Low resolution, Balanced, and High detail. High detail
  is still rasterization and is not lossless.
- Password-protected PDFs on the `PdfRenderer` image path can be retried with
  an in-memory password only on Android 15 or devices with PDF extension 13.
  Older devices fail clearly for that path.
- Non-seekable PDF providers use SafeCache only when direct `PdfRenderer`
  opening fails, and cache space is checked before copying.
- PDF to image does not do OCR, selectable text extraction, or extraction of
  embedded images.
- PDF merge copies each source PDF to task cache first, then merges with
  PDFBox-Android. The output is not re-encrypted. Advanced structures such as
  complex forms, bookmarks, attachments, and metadata are best-effort only.
- PDF to TXT extracts selectable text with PDFBox-Android and inserts stable
  page separators. It does not do OCR, so scanned PDFs without a text layer fail
  with a clear no-selectable-text message.
- PDFBox-backed PDF merge and PDF to TXT can load password-protected source PDFs
  with transient in-memory passwords. Passwords are not logged or persisted.

Manual PDF sample coverage should include ordinary multi-PDF merge, mixed
text/scanned/image PDFs, mixed page sizes, large PDFs, cancellation during cache
or write, password-protected sources, text-layer PDF to TXT, mixed-content PDF to
TXT, and scanned PDF to TXT with the no-selectable-text failure.
