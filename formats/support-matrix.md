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
| MP4 / MKV / MOV / WEBM / AVI / 3GP / 3GPP / TS / MTS video audio tracks | MP3 / M4A / WAV / FLAC / WMA | Experimental | FFmpeg compatible | Extracts the first audio stream and encodes the selected audio target. M4A is AAC re-encode, not stream copy. The app probes encoders before export where possible. Bitrate, sample-rate, and channel options are passed when the target supports them; video, subtitle, attachment, and extra audio tracks are not copied. |
| MP4 | MP4 | Experimental | FFmpeg compatible | Re-encodes the first video track to H.264 or H.265 and audio to AAC in MP4. Video bitrate, codec, short-side resolution cap, max frame-rate, and advanced filters are applied where selected. Subtitles, attachments, and extra tracks are not copied. |
| MP4 | MKV | Experimental | FFmpeg compatible | Re-encodes the first video track to H.264 or H.265 and audio to AAC in Matroska. Video bitrate, codec, short-side resolution cap, max frame-rate, and advanced filters are applied where selected. Subtitles, attachments, and extra tracks are not copied. |
| MP4 / MKV / MOV / WEBM / AVI / 3GP / 3GPP / TS / MTS | MOV | Experimental | FFmpeg compatible | Re-encodes the first video track to H.264 or H.265 and audio to AAC in QuickTime MOV. Video bitrate, codec, short-side resolution cap, max frame-rate, and advanced filters are applied where selected. Subtitles, attachments, and extra tracks are not copied. |
| MP4 / MKV / MOV / WEBM / AVI / 3GP / 3GPP / TS / MTS | GIF | Experimental | FFmpeg compatible | Creates an animated GIF from the first video track with palettegen/paletteuse. Output is automatically limited to the first 30 seconds, 30 fps, and 900 frames. The default short-side cap is 480 px, with 720 px and Original options. Audio, subtitles, data streams, timing metadata, and container metadata are not copied. |
| MKV / MOV / WEBM / AVI / 3GP / 3GPP / TS / MTS | MP4 | Experimental | FFmpeg compatible | Re-encodes the first video track to H.264 or H.265 and audio to AAC in MP4. Video bitrate, codec, short-side resolution cap, max frame-rate, and advanced filters are applied where selected. Subtitles, attachments, and extra tracks are not copied. |
| MP3 / M4A / AAC / FLAC / WAV / WMA / OGG | MP3 / M4A / WAV / FLAC / WMA | Experimental | FFmpeg compatible | Common audio conversion path. MP3 uses `libmp3lame`; M4A uses AAC; WAV uses PCM; FLAC uses FLAC; WMA uses WMA v2 in ASF/WMA. Bitrate is applied for MP3/M4A/WMA when selected. Sample-rate, channel, reverse, fade, volume/mute, echo, and audio noise-reduction controls are applied when selected. WAV/FLAC ignore bitrate. |
| JPG / JPEG / JFIF / JPE / PNG / WEBP | JPG / JFIF / PNG / WEBP / ICO | Experimental | Native Bitmap | Static image conversion through Android platform bitmap APIs; physical-device smoke testing is still pending. JFIF output is JPEG-encoded pixels with a `.jfif` extension. JPG/JFIF/WEBP quality presets are Original 100, High 95, Balanced 85, Small 60; WEBP also offers Android 11+ lossless output. ICO output is a multi-size PNG-in-ICO file. PNG is written as lossless output. Transparency is preserved for PNG/WEBP/ICO and flattened to white for JPG/JFIF. Metadata is not copied, though JPEG EXIF orientation is applied best-effort; animated WEBP is not preserved as animation. |
| ICO | JPG / JFIF / PNG / WEBP / ICO / PDF | Experimental | Native Bitmap / Android PdfDocument | Reads the largest ICO layer only when that layer is PNG-in-ICO. Old BMP/DIB icon payloads are not decoded in this milestone. |
| GIF | JPG / JFIF / PNG / WEBP / ICO / PDF | Experimental | Native Bitmap / FFmpeg compatible / Android PdfDocument | User can choose first-frame conversion or split-frame output. GIF split uses the FFmpeg compatibility path to decode a raw RGBA frame stream, then reuses the native image/PDF writers. Split image outputs and one-PDF-per-frame outputs are saved inside a subfolder. Animation timing, loop count, frame delay, and metadata are not preserved. |
| HEIC / HEIF | JPG / JFIF / PNG / WEBP / ICO / PDF | Experimental | Native Bitmap / Android PdfDocument | Attempts platform decode through Android image APIs. Support depends on the device and Android image codec availability; failures should be clear. |
| JPG / JPEG / JFIF / JPE / PNG / WEBP | PDF | Experimental | Android PdfDocument | Creates one PDF page per image. A4-fit and original-ratio page modes preserve image ratio and use a white page background. Multiple selected images can become one multi-page PDF or one PDF per image. |
| PDF | JPG / PNG / WEBP | Experimental | Android PdfRenderer | Renders each PDF page to one image file. This is page rasterization, not OCR, text extraction, or embedded-image extraction. Multi-page outputs use one task and same-sized page images. |
| Multiple PDFs | PDF | Experimental | PDFBox-Android | Merges selected PDFs as page objects instead of rasterizing them. Normal text layers and vector content are preserved best-effort; complex forms, bookmarks, attachments, and metadata are not guaranteed. |
| PDF | TXT | Experimental | PDFBox-Android | Extracts selectable text with page separators. This is not OCR; scanned PDFs without a text layer fail clearly. |
| DOCX / PPTX / XLSX | PDF | Experimental | office2pdf native | Converts OOXML Office files to PDF through the bundled `arm64-v8a` `libzen_office2pdf.so`. The current rebuilt library receives bundled Noto Sans and Noto Serif CJK directories through the explicit font-path JNI entry, and Simplified Chinese text rendering has been verified on an arm64 physical device. This path reads each whole input into memory, caps source files at 64 MiB, and does not promise Microsoft Office layout fidelity; overlapping text, shifted shapes, and degraded slide/spreadsheet layout remain expected on complex files. |

## Current Native Media Limits

- Video targets are intentionally limited to MP4, MKV, MOV, and GIF. GIF is
  output-only for video sources in this milestone, not a normal image output
  target.
- Audio targets are connected for MP3, M4A, WAV, FLAC, and WMA. Audio category
  tasks always use the FFmpeg compatibility path and true audio re-encoding.
  M4A output is AAC encoding, not audio-track copy.
- Video files selected in the Audio lane map only the first audio stream and
  encode the selected audio target. Video, subtitles, data streams, attachments,
  extra audio tracks, and metadata are not copied.
- MP4/MKV/MOV video outputs use the FFmpeg compatibility path with true
  video/audio re-encoding, including MP4-to-MP4.
- No hidden hardware transcode fallback is active. Current connected video
  outputs stay on FFmpeg so visible options and advanced filters are applied
  consistently.

## Current FFmpeg Compatibility Limits

- The FFmpeg path uses the local self-built
  `app/libs/ffmpeg-kit-next-7.1.0.aar`, built from
  `arthenica/ffmpeg-kit-next` tag `v7.1.0`, commit
  `1e64a8cdda1b045b014c0a54e9d395929c7b6ccc`, with SHA-256
  `d1f2512e806ac3ff99b2f4c3d2e36fcca8c5c0eec548d84da81cf94d054cf406`.
  The AAR contains only `arm64-v8a` native libraries. The exact rebuild command
  for this replacement binary still needs to be recorded before a tagged
  release.
- Video compatibility output is true re-encoding, not stream-copy remux:
  `-map 0:v:0 -map 0:a:0? -sn -dn -c:v libx264|libx265 -c:a aac`.
  MP4 output writes `-f mp4` plus `+faststart`; MKV output writes
  `-f matroska`; MOV output writes `-f mov` plus `+faststart`.
- Advanced filters are experimental and only apply to MP4/MKV/MOV video outputs
  and audio outputs. Video outputs support reverse playback, fade, mirror,
  rotate, and fit/crop frame shape. Audio outputs and video-output audio tracks
  support reverse playback, fade, volume/mute, echo, and `afftdn` audio noise
  reduction. Video reverse is capped to inputs with readable duration and size
  metadata, up to 60 seconds, and within a conservative reverse-frame memory
  budget because FFmpeg reverse filters buffer the selected stream. Video mute
  omits the output audio track. Fade-out needs readable duration metadata. GIF
  output does not use these advanced controls.
- Audio noise reduction is non-model `afftdn` only. Model-based `arnndn` and
  video denoise filters are intentionally not connected in this milestone.
- Video-to-GIF uses the FFmpeg compatibility path with an inline
  palettegen/paletteuse filter graph. It writes `image/gif`, defaults to a
  480 px short-side cap, offers 720 px and Original size options, forces
  `fps=30`, applies `-frames:v 900`, clips processing to the first 30 seconds,
  drops audio/subtitle/data streams, and loops by default.
- Video compatibility options are wired as follows: codec selects
  `libx264`/`libx265`; selected bitrate becomes `-b:v`; Auto bitrate uses CRF
  23 for H.264 or CRF 28 for H.265; resolution caps the short side and keeps
  even dimensions; frame-rate uses `-fpsmax` as an upper bound rather than
  forcing low-FPS sources upward.
- Video compatibility AAC audio options are wired as follows: selected audio
  bitrate becomes `-b:a`; selected sample-rate becomes `-ar`; selected channel
  count becomes `-ac`; selected audio advanced filters become `-af` unless
  video mute removes audio entirely.
- Subtitles, attachments, extra audio tracks, and unknown streams are not copied
  in this first path.
- MP3, M4A, WAV, FLAC, and WMA audio targets use FFmpeg compatibility arguments:
  `libmp3lame`, `aac`, `pcm_s16le`, `flac`, and `wmav2` respectively.
  MP3/M4A/WMA pass selected bitrate, sample-rate, channel options, and advanced
  audio filters. WAV/FLAC pass sample-rate, channel options, and advanced audio
  filters, but intentionally do not pass bitrate.
- Physical-device logs on July 11, 2026 confirmed that the earlier local Free
  AAR returned `Unknown encoder 'libmp3lame'`. The app probes for
  `libmp3lame` before MP3 export and fails with a specific message if the wrong
  FFmpeg package is bundled.
- M4A extraction/conversion is now AAC re-encode through FFmpeg for both audio
  files and video audio tracks. It fails with a specific encoder message if the
  bundled package cannot encode AAC.
- SAF input is passed to FFmpeg through FFmpegKit's SAF parameter when possible,
  with `/proc/self/fd/{fd}` retained as a fallback. Cache fallback for
  non-seekable providers is still future `SafeCache` work.
- No automated audio sample suite exists yet. Physical-device smoke testing
  should cover MP3, M4A, WAV, FLAC, WMA, and at least one video audio-extraction
  sample before raising these rows beyond `Experimental`.

## Current Native Image Limits

- Image targets are intentionally limited to JPG, JFIF, PNG, WEBP, ICO, and PDF.
- Image inputs are limited in the picker to JPG/JPEG/JFIF/JPE where providers
  expose them as JPEG images, PNG, WEBP, GIF, HEIC, HEIF, and ICO where
  providers expose ICO as an image MIME type.
- The first image path uses Android platform bitmap decode/encode APIs and does
  not add a third-party image dependency.
- JFIF output is JPEG-encoded pixels with a `.jfif` extension. It is not a
  separate codec from JPG/JPEG in this app.
- JPG/JFIF/WEBP `Original` means quality value 100 in a platform re-encode. It
  is not a byte-identical no-recompression copy; same-format FastCopy remains a
  future improvement.
- WEBP lossless output is exposed only on Android 11/API 30 and newer. Older
  devices keep the existing lossy WEBP path.
- ICO output is a modern PNG-in-ICO container with 16, 32, 48, 64, 128, and
  256 px entries. Images are scaled down proportionally and centered on a
  transparent square canvas.
- ICO input reads only the largest ICO layer when that layer is PNG-in-ICO. Old
  BMP/DIB icon payloads are not decoded in this milestone.
- GIF input can be converted as first-frame only, or split into numbered frames
  through the FFmpeg compatibility path. Split image outputs and one-PDF-per-frame
  outputs are saved inside a subfolder. Animation timing, loop count, frame
  delay, and metadata are not preserved.
- HEIC/HEIF input is best-effort platform decode. It may fail on devices whose
  Android image stack cannot decode the selected file.
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

## Current Office Document Limits

- Office document targets are intentionally limited to DOCX, PPTX, and XLSX to
  PDF. Legacy DOC, PPT, XLS, ODT, RTF, and encrypted/password-protected Office
  files are not connected.
- The native library is currently bundled only at
  `app/src/main/jniLibs/arm64-v8a/libzen_office2pdf.so`. Published APKs are
  arm64-only, so 32-bit ARM devices are not supported by the release package.
  Its reproducible source is at `native/office2pdf-jni`. The July 14, 2026
  bundled build exports the explicit
  font-path JNI entry used for CJK fonts; the older legacy conversion entry is
  retained only so older local test binaries can still start.
- The JNI surface accepts and returns byte arrays. The service therefore reads
  the whole OOXML source into memory, rejects files larger than 64 MiB, and then
  writes the returned PDF bytes to the normal output flow.
- Before conversion, the Kotlin wrapper copies bundled Noto Sans CJK and Noto
  Serif CJK into app-private storage and passes that directory to the JNI v2
  `ConvertOptions.font_paths` API when the native library exports it. This
  avoids relying on Android vendor font names or a CLI-only `TYPST_FONT_PATHS`
  environment convention; the legacy JNI fallback cannot use this directory.
- Layout fidelity, fonts, charts, comments, slide effects, spreadsheet print
  areas, and advanced Office features are experimental. Manual testing showed
  that Chinese text renders after the CJK rebuild, but complex pages can still
  show overlapping text, shifted Office shapes, and degraded slide layout. Treat
  this as a local first-pass renderer, not a replacement for Microsoft Office
  export.
- Manual sample coverage should include small and large DOCX, PPTX, and XLSX
  files, Simplified Chinese text, missing fonts, embedded images, charts,
  cancellation, unsupported legacy formats, oversized files, and an arm32-device
  startup failure check.
