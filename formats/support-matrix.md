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
| MP4 / MKV / MOV / WEBM / AVI / 3GP / 3GPP / TS / MTS video audio tracks | MP3 / M4A / WAV / FLAC / WMA / OPUS | Stable | FFmpeg compatible | Extracts the first audio stream and encodes the selected audio target. M4A is AAC re-encode, not stream copy. Start/end-second trimming can limit the exported range. The app probes encoders before export where possible. Bitrate, sample-rate, and channel options are passed when the target supports them; video, subtitle, attachment, and extra audio tracks are not copied. |
| MP4 | MP4 | Stable | FFmpeg compatible | Re-encodes the first video track to H.264 or H.265 and audio to AAC in MP4. Manual mode exposes codec, bitrate, short-side resolution cap, max frame-rate, audio options, trim range, and advanced filters. Fixed compression presets own the video codec/CRF/preset/resolution/frame-rate strategy and AAC audio bitrate, while trim range still applies. Subtitles, attachments, and extra tracks are not copied. |
| MP4 | MKV | Stable | FFmpeg compatible | Re-encodes the first video track to H.264 or H.265 and audio to AAC in Matroska. Manual mode exposes codec, bitrate, short-side resolution cap, max frame-rate, audio options, and advanced filters. Fixed compression presets own the video codec/CRF/preset/resolution/frame-rate strategy and AAC audio bitrate. Subtitles, attachments, and extra tracks are not copied. |
| MP4 / MKV / MOV / WEBM / AVI / 3GP / 3GPP / TS / MTS | MOV | Stable | FFmpeg compatible | Re-encodes the first video track to H.264 or H.265 and audio to AAC in QuickTime MOV. Manual mode exposes codec, bitrate, short-side resolution cap, max frame-rate, audio options, and advanced filters. Fixed compression presets own the video codec/CRF/preset/resolution/frame-rate strategy and AAC audio bitrate. Subtitles, attachments, and extra tracks are not copied. |
| MP4 / MKV / MOV / WEBM / AVI / 3GP / 3GPP / TS / MTS | GIF | Stable | FFmpeg compatible | Creates an animated GIF from the first video track with palettegen/paletteuse. Start/end-second trimming can choose the source range; output is still limited to that range's first 30 seconds, 30 fps, and 900 frames. The default short-side cap is 480 px, with 720 px and Original options. Audio, subtitles, data streams, timing metadata, and container metadata are not copied. |
| MKV / MOV / WEBM / AVI / 3GP / 3GPP / TS / MTS | MP4 | Stable | FFmpeg compatible | Re-encodes the first video track to H.264 or H.265 and audio to AAC in MP4. Manual mode exposes codec, bitrate, short-side resolution cap, max frame-rate, audio options, and advanced filters. Fixed compression presets own the video codec/CRF/preset/resolution/frame-rate strategy and AAC audio bitrate. Subtitles, attachments, and extra tracks are not copied. |
| Multiple Videos (MP4 / MKV / MOV / WEBM / AVI / etc.) | MP4 / MKV / MOV | Stable | FFmpeg compatible | Video merge path. Concatenates multiple selected video files in order using FFmpeg `filter_complex concat`. Normalizes varying resolutions with aspect-ratio letterboxing/pillarboxing (`scale+pad`) to the reference video size (or chosen resolution limit), normalizes audio to 44.1kHz stereo AAC, and handles audio-less source clips with silent audio tracks. Supports compression presets and resolution/codec options. |
| MP3 / M4A / AAC / FLAC / WAV / WMA / OGG / OPUS | MP3 / M4A / WAV / FLAC / WMA / OPUS | Stable | FFmpeg compatible | Common audio conversion path. MP3 uses `libmp3lame`; M4A uses AAC; WAV uses PCM; FLAC uses FLAC; WMA uses WMA v2 in ASF/WMA; OPUS uses `libopus` in Ogg. Start/end-second trimming can limit the exported range. Bitrate is applied for MP3/M4A/WMA/OPUS when selected. Sample-rate, channel, reverse, fade, volume/mute, echo, and audio noise-reduction controls are applied when selected. OPUS natively operates at 48 kHz (RFC 6716 / RFC 7845) and supports {48k, 24k, 16k, 12k, 8k}; non-Opus rates like 44.1 kHz are automatically resampled to 48 kHz. WAV/FLAC ignore bitrate. |
| JPG / JPEG / JFIF / JPE / PNG / WEBP | JPG / JFIF / PNG / WEBP / ICO | Stable | Native Bitmap / ONNX Runtime | Static image conversion through Android platform bitmap APIs. JFIF output is JPEG-encoded pixels with a `.jfif` extension. JPG/JFIF/WEBP quality presets are Original 100, High 95, Balanced 85, Small 60; WEBP also offers Android 11+ lossless output. ICO output is a multi-size PNG-in-ICO file. PNG is written as lossless output. Transparency is preserved for PNG/WEBP/ICO and flattened to white for JPG/JFIF. Metadata is not copied, though JPEG EXIF orientation is applied best-effort; animated WEBP is not preserved as animation. Optional super-resolution upscales raster outputs (JPG/JFIF/PNG/WEBP) before encoding: bilinear 2×/3×/4× (`createScaledBitmap`) and Real-ESRGAN 4× AI models (`realesr-general-x4v3` 4.65 MB compact and `RealESRGAN_x4plus` 63.9 MB high-quality) through ONNX Runtime. The AI models are downloaded at runtime from an R2 direct link and SHA-256 verified; they are slower and do not preserve transparency. While a scale is active it forces original quality and hides GIF frame splitting. Output pixel budget scales with device RAM (32 MP per GiB, 64 MP minimum, 512 MP maximum). |
| JPG / JPEG / JFIF / JPE | Inspect / clean / restore metadata | Stable | Native JPEG segment tool | Separate privacy tool, not a conversion task. It inspects common EXIF values and removable JPEG metadata segments, then can remove EXIF/XMP, IPTC/Photoshop, and comment segments in place without re-encoding pixels. JFIF, ICC, and Adobe display-related segments are preserved. Removed metadata is backed up in app-private data and can be restored only when the selected image's metadata-stripped core SHA-256 and dimensions match. |
| Video files | Inspect metadata | Stable | MediaMetadataRetriever | Separate privacy tool can display basic duration, size, resolution, frame-rate, and container bitrate metadata where Android exposes it. Video metadata cleanup is intentionally not connected in this milestone. |
| ICO | JPG / JFIF / PNG / WEBP / ICO / PDF | Stable | Native Bitmap / Android PdfDocument | Reads the largest ICO layer only when that layer is PNG-in-ICO. Old BMP/DIB icon payloads are not decoded in this milestone. |
| GIF | JPG / JFIF / PNG / WEBP / ICO / PDF | Stable | Native Bitmap / FFmpeg compatible / Android PdfDocument | User can choose first-frame conversion or split-frame output. GIF split uses the FFmpeg compatibility path to decode a raw RGBA frame stream, then reuses the native image/PDF writers. Split image outputs and one-PDF-per-frame outputs are saved inside a subfolder. Animation timing, loop count, frame delay, and metadata are not preserved. |
| HEIC / HEIF | JPG / JFIF / PNG / WEBP / ICO / PDF | Beta | Native Bitmap / Android PdfDocument | Attempts platform decode through Android image APIs. Support depends on the device and Android image codec availability; failures should be clear. |
| JPG / JPEG / JFIF / JPE / PNG / WEBP | PDF | Stable | Android PdfDocument | Creates one PDF page per image. A4-fit and original-ratio page modes preserve image ratio and use a white page background. Multiple selected images can become one multi-page PDF or one PDF per image. |
| PDF | JPG / PNG / WEBP | Stable | Android PdfRenderer | Renders each PDF page to one image file. This is page rasterization, not OCR, text extraction, or embedded-image extraction. Multi-page outputs use one task and same-sized page images. |
| Multiple PDFs | PDF | Stable | PDFBox-Android | Merges selected PDFs as page objects instead of rasterizing them. Normal text layers and vector content are preserved best-effort; complex forms, bookmarks, attachments, and metadata are not guaranteed. |
| PDF | TXT / MD | Stable | PDFBox-Android | Extracts selectable text with page separators. Markdown output is lightweight: a document title plus per-page headings and extracted text. This is not OCR; scanned PDFs without a text layer fail clearly. |
| PDF | Encrypt PDF / Decrypt PDF | Stable | PDFBox-Android | Encrypt applies one open password to the output PDF. Decrypt removes security only after the source PDF opens normally or with the user-provided password. This is not password cracking; passwords are kept in memory only and are not logged or persisted. |
| PDF | Compress PDF | Stable | PDFBox-Android | Compresses PDF files by downsampling and re-encoding embedded images (High Quality 85%/2160px, Balanced 70%/1440px, Small File 50%/1080px) and stripping redundant thumbnail metadata through PDFBox-Android. Preserves selectable text, vectors, and page layouts. |
| DOCX / PPTX / XLSX | PDF / TXT / MD | Beta | office2pdf native / PDFBox-Android | Converts OOXML Office files to PDF through the bundled `arm64-v8a` `libzen_office2pdf.so`. TXT/MD outputs reuse that intermediate PDF and extract its selectable text with PDFBox-Android. The current rebuilt library receives bundled Noto Sans and Noto Serif CJK directories through the explicit font-path JNI entry, and Simplified Chinese text rendering has been verified on an arm64 physical device. This path reads each whole input into memory, caps source files at 64 MiB, and does not promise Microsoft Office layout fidelity; overlapping text, shifted shapes, and degraded slide/spreadsheet layout remain expected on complex files. |
| TTF / OTF | WOFF2 | Beta | woff2 native | Compresses a TrueType (glyf) or OpenType CFF font to WOFF2 through the bundled `arm64-v8a` `libzen_woff2.so` (Google woff2). Whole-file byte-level container conversion with no options; font metadata is not re-written. arm64-v8a only. |
| TTF / OTF | WOFF | Beta | Pure Kotlin zlib | Compresses a TrueType or OpenType CFF font to WOFF 1.0 using `java.util.zip` in pure Kotlin, with no native dependency. Whole-file byte-level container conversion; WOFF metadata and private blocks are not preserved. |
| WOFF2 | TTF / OTF | Beta | woff2 native | Decompresses WOFF2 back to its original SFNT flavor through `libzen_woff2.so`. The output extension (`.ttf` vs `.otf`) is chosen from the font flavor. arm64-v8a only. |
| WOFF | TTF / OTF | Beta | Pure Kotlin zlib | Decompresses WOFF 1.0 back to SFNT in pure Kotlin. The output extension (`.ttf` vs `.otf`) is chosen from the font flavor. |
| SRT | VTT / ASS | Beta | FFmpeg compatible | Text subtitle conversion through FFmpeg's `subrip`, `webvtt`, and `ass` demuxers/muxers. Timing is preserved best-effort. SRT carries no styling, so VTT/ASS outputs use FFmpeg defaults. Same-format conversion is not offered. |
| SRT | LRC | Beta | Pure Kotlin | SRT cues are parsed in pure Kotlin and written as LRC timestamp lines. Multi-line SRT text is collapsed to one line; end times are discarded. Same-format conversion is not offered. |
| VTT | SRT / ASS / LRC | Beta | FFmpeg compatible / Pure Kotlin | SRT/ASS use FFmpeg directly; LRC converts through a temporary SRT file in pure Kotlin. WebVTT cue settings and inline markup are dropped. Same-format conversion is not offered. |
| LRC | SRT / VTT / ASS | Beta | Pure Kotlin / FFmpeg compatible | LRC is parsed in pure Kotlin (multi-timestamp lines and `[offset:]` supported) and written as SRT, then VTT/ASS go through FFmpeg. Same-format conversion is not offered. |
| ASS | SRT / VTT / LRC | Beta | FFmpeg compatible / Pure Kotlin | SRT/VTT use FFmpeg directly; LRC converts through a temporary SRT file in pure Kotlin. ASS styling, positioning, and formatting are dropped. Same-format conversion is not offered. |

## Subtitle / Lyrics Limits

- The subtitle lane supports exactly four formats: SRT, VTT, LRC, and ASS.
  TXT is intentionally not an input or an output. Same-format conversion is not
  offered.
- SRT/VTT/ASS directions use the FFmpeg compatibility path. The checked-in
  FFmpegKitNext build is probed at runtime for the `srt`, `webvtt`, and `ass`
  demuxers/muxers and `subrip`/`webvtt`/`ass` codecs; a missing feature fails
  with a clear message.
- LRC is not supported by FFmpeg, so it is parsed and written in pure Kotlin,
  with SRT as the interchange format. Parsing supports `[mm:ss.xx]`,
  `[mm:ss.xxx]`, and `[mm:ss]` timestamps, multiple timestamps per line, the
  `[ti:] [ar:] [al:] [by:]` metadata tags (title is kept), and applies a signed
  `[offset:...]` to all timestamps. Timestamp-less lines are ignored.
- Subtitle/lyrics files are read whole into memory with an 8 MiB input cap.
  Text is decoded as UTF-8 (with BOM stripped) and falls back to GB18030 when
  invalid UTF-8 is detected, covering common GBK-encoded Chinese lyrics.
- Styling and formatting are best-effort across formats: ASS styles/positioning,
  WebVTT cue settings/inline markup, and any rich formatting are dropped when
  converting to SRT or LRC. Timing precision is limited to what each format
  expresses (LRC has no end time; SRT output synthesizes end times from the next
  cue start or a 2-second default for the final cue).

## Current Font Limits

- Font conversion is whole-file byte-level container conversion with no options.
  It does not subset, re-hint, re-encode outlines, edit variable-font axes,
  split TrueType Collections, or generate CSS.
- TTF/OTF to WOFF2 and WOFF2 to TTF/OTF use the bundled `arm64-v8a`
  `libzen_woff2.so` (Google woff2). These directions require an arm64-v8a device
  and fail with a clear message on other ABIs.
- TTF/OTF to WOFF and WOFF to TTF/OTF are pure-Kotlin zlib and would work on any
  ABI, but the release APK remains arm64-v8a only.
- WOFF 1.0 metadata and private blocks are not preserved. WOFF2 metadata is
  carried through the codec, but the app does not add or rewrite it.
- For WOFF2/WOFF inputs the output extension is `.ttf` for TrueType (glyf)
  outlines and `.otf` for CFF/PostScript outlines, chosen from the font flavor.
- Fonts are read whole into memory with a 32 MiB input cap.

## Current Native Media Limits

- Video targets are intentionally limited to MP4, MKV, MOV, and GIF. GIF is
  output-only for video sources in this milestone, not a normal image output
  target.
- Audio targets are connected for MP3, M4A, WAV, FLAC, WMA, and OPUS. Audio category
  tasks always use the FFmpeg compatibility path and true audio re-encoding.
  M4A output is AAC encoding, not audio-track copy.
- Video files selected in the Audio lane map only the first audio stream and
  encode the selected audio target. Video, subtitles, data streams, attachments,
  extra audio tracks, and metadata are not copied.
- Video and audio FFmpeg outputs can apply start/end-second trimming and multi-point splitting into multiple segment files.
  Trimming is a re-encode range selection, not byte-exact lossless cutting. If a
  range is set, start must be before the readable source duration and end must
  be greater than start and not exceed the source duration.
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
- Video compression presets are fixed CRF-based visual compression, not mathematical
  lossless compression. The off/manual state keeps the existing fixed-bitrate
  or Auto CRF behavior and exposes manual video/audio controls. Visual lossless,
  balanced compression, and small-file modes force CRF output and use preset
  `medium`; they hide/ignore manual video codec, bitrate, resolution,
  frame-rate, audio options, and advanced controls. High-bitrate sources usually
  shrink substantially, but already efficient low-bitrate sources can become
  larger.
- Video frame interpolation provides 2× deep-learning frame rate multiplication using
  RIFE optical flow running through Tencent NCNN with Vulkan GPU compute acceleration
  (`libzen_ncnn.so`). This feature is currently **Experimental**: due to mobile GPU driver,
  Vulkan compute extensions, and dynamic memory allocator variance across chipsets, inference
  runs with adaptive 1080p+ downscaling and lightmode memory deallocation.
  The pipeline decodes frames via FFmpeg, applies sliding-window
  RIFE inference ($N, N+1 \to N.5$), and re-encodes at 2× fps with high quality CRF 18
  libx264 while remuxing original audio. While active, conflicting options (compression
  presets, manual bitrate, manual codec, manual frame rate, and advanced video filters)
  are locked/hidden, while video trimming remains available. The paired `.param` and `.bin`
  RIFE model files are downloaded together from an R2 direct link into app-private storage
  and SHA-256 verified before use.
- Advanced filters are stable within their documented limits and only apply to MP4/MKV/MOV video outputs
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
  `fps=30`, applies `-frames:v 900`, clips processing to the selected range's
  first 30 seconds or the source's first 30 seconds when no range is set, drops
  audio/subtitle/data streams, and loops by default.
- Video compatibility options are wired as follows: codec selects
  `libx264`/`libx265`; off/manual selected bitrate becomes `-b:v`; off/manual
  Auto bitrate uses CRF 23 for H.264 or CRF 28 for H.265 with preset
  `veryfast`. Visual lossless prefers H.265 when available and uses CRF 18 for
  H.264 or CRF 20 for H.265 while keeping original resolution and frame rate.
  Balanced compression prefers H.265, uses CRF 21 for H.264 or CRF 24 for
  H.265, and caps the short side at 1080 px. Small-file mode prefers H.265,
  uses CRF 24 for H.264 or CRF 28 for H.265, caps the short side at 720 px, and
  caps frame rate at 30 fps. The three fixed presets use preset `medium`; their
  resolution caps keep even dimensions and their frame-rate cap uses `-fpsmax`
  rather than forcing low-FPS sources upward. Their AAC audio bitrate is 192k,
  160k, and 128k respectively.
- Video compatibility AAC audio options are wired as follows: selected audio
  bitrate becomes `-b:a`; selected sample-rate becomes `-ar`; selected channel
  count becomes `-ac`; selected audio advanced filters become `-af` unless
  video mute removes audio entirely.
- Subtitles, attachments, extra audio tracks, and unknown streams are not copied
  in this first path.
- MP3, M4A, WAV, FLAC, WMA, and OPUS audio targets use FFmpeg compatibility arguments:
  `libmp3lame`, `aac`, `pcm_s16le`, `flac`, `wmav2`, and `libopus` respectively.
  MP3/M4A/WMA/OPUS pass selected bitrate, sample-rate, channel options, and advanced
  audio filters. WAV/FLAC pass sample-rate, channel options, and advanced audio
  filters, but intentionally do not pass bitrate. OPUS uses `libopus` which natively operates
  at 48 kHz (RFC 6716 / RFC 7845) and strictly supports only {48000, 24000, 16000, 12000, 8000} Hz.
  Non-Opus sample rates (such as 44.1 kHz or 32 kHz) are automatically resampled to 48 kHz.
  The UI restricts Opus sample-rate choices to supported rates, and the conversion engine guards
  against passing unsupported rates to `libopus`.
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
- No automated audio sample suite exists yet. Physical-device verification has
  covered MP3, M4A, WAV, FLAC, WMA, OPUS, and video audio extraction; an automated
  sample suite remains a future quality improvement.

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

## Current Metadata Safety Limits

- Metadata safety is a separate privacy tool and does not use the conversion
  queue or conversion engines.
- JPG/JPEG/JFIF/JPE images can be inspected and cleaned by removing removable
  JPEG metadata segments without re-encoding image pixels. Cleaned metadata is
  backed up under app-private data before writeback.
- Restore only works when the selected image matches a backup by metadata-
  stripped JPEG core SHA-256 and dimensions. Renaming a file can still match;
  re-encoding, resizing, or editing pixels should not.
- PNG, WEBP, HEIC/HEIF, GIF, ICO, and video cleanup are not connected in this
  milestone. Unsupported images may still show basic format/size/dimensions.
- Clearing app data or uninstalling the app removes metadata backups. Backups
  are not uploaded and are not logged.

## Current Native PDF Limits

- Image to PDF uses Android `PdfDocument`; PDF to image uses Android
  `PdfRenderer`. PDF merge, PDF to TXT/MD, and PDF encrypt/decrypt use
  PDFBox-Android because Android platform APIs do not provide broad true PDF
  merge, cross-version text extraction, or PDF security editing.
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
- PDF to TXT/MD extracts selectable text with PDFBox-Android. TXT inserts
  stable page separators; MD writes a document title and per-page headings. It
  does not do OCR, table reconstruction, or heading inference, so scanned PDFs
  without a text layer fail with a clear no-selectable-text message.
- PDF encryption applies a single open password using standard PDFBox
  protection. It does not expose separate owner/user passwords or a
  print/copy/modify permissions matrix.
- PDF decryption removes security only after the document can be opened
  normally or with the correct source password. It does not brute-force or
  recover unknown passwords; unencrypted PDFs can still be saved as normal PDF
  copies.
- PDFBox-backed PDF merge, PDF to TXT/MD, and PDF encrypt/decrypt can load
  password-protected source PDFs with transient in-memory passwords. Source and
  output passwords are not logged or persisted.

Manual PDF sample coverage should include ordinary multi-PDF merge, mixed
text/scanned/image PDFs, mixed page sizes, large PDFs, cancellation during cache
or write, password-protected sources, text-layer PDF to TXT/MD, mixed-content PDF
to TXT/MD, scanned PDF to TXT/MD with the no-selectable-text failure, encrypted
PDF creation, correct-password decryption, wrong-password failure, and
unencrypted PDF decryption-as-copy.

## Current Office Document Limits

- Office document inputs are intentionally limited to DOCX, PPTX, and XLSX, with
  PDF, TXT, and MD outputs. Legacy DOC, PPT, XLS, ODT, RTF, and
  encrypted/password-protected Office files are not connected.
- The native library is currently bundled only at
  `app/src/main/jniLibs/arm64-v8a/libzen_office2pdf.so`. Published APKs are
  arm64-only, so 32-bit ARM devices are not supported by the release package.
  Its reproducible source is at `native/office2pdf-jni`. The July 14, 2026
  bundled build exports the explicit
  font-path JNI entry used for CJK fonts; the older legacy conversion entry is
  retained only so older local test binaries can still start.
- The JNI surface accepts and returns byte arrays. The service therefore reads
  the whole OOXML source into memory, rejects files larger than 64 MiB, and then
  writes the returned PDF bytes to the normal output flow or extracts text from
  that intermediate PDF for TXT/MD.
- Before conversion, the Kotlin wrapper discovers available Android system
  font directories (`/system/fonts`, `/apex/...`) and merges any user-downloaded
  high-fidelity Noto CJK fonts from app storage, passing them to the JNI
  `ConvertOptions.font_paths` API. This gives 0-MB download, 100% offline CJK
  text rendering by default while keeping optional high-fidelity Noto Sans and
  Noto Serif CJK typography available on-demand.
- Layout fidelity, fonts, charts, comments, slide effects, spreadsheet print
  areas, and advanced Office features remain Beta compatibility areas. Testing
  showed that Chinese text renders properly using system fonts, but complex pages
  can still show overlapping text, shifted Office shapes, and degraded slide
  layout. Treat this as a local first-pass renderer, not a replacement for
  Microsoft Office export.
- Office TXT/MD output inherits the Office-to-PDF rendering limits and then
  extracts only selectable text from the intermediate PDF. It does not do OCR or
  reconstruct rich document structure.
- Manual sample coverage should include small and large DOCX, PPTX, and XLSX
  files, Simplified Chinese text, missing fonts, embedded images, charts,
  TXT/MD text output, cancellation, unsupported legacy formats, oversized files,
  and an arm32-device startup failure check.
