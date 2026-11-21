# Video to Animated WebP and Animated Output Resolution Defaults

## Status

Accepted

## Context

In GitHub Issue #32, users requested direct MP4/video-to-animated-WebP output and changing the default animated output resolution from `480p` to `Original` (while keeping downscale options available for users who prefer smaller files). The repository owner confirmed support for animated WebP and updating the default GIF/animated resolution to `Original`, while deferring batch trim/split, a separate "end second = duration" button, and global default-output settings.

Inspection of the checked-in `app/libs/ffmpeg-kit-next-7.1.0.aar` binary showed it was configured with `--enable-libmp3lame --enable-libopus --enable-libvpx --enable-libx264 --enable-libx265` and **omits `libwebp` / `libwebp_anim`** (`libavcodec.so` includes only FFmpeg's built-in static WebP decoder, not a WebP encoder). However, `libavcodec.so` does bundle the `pam` encoder (`P7` RGBA frames), `libavformat.so` includes `image2pipe`, `FFmpegKitConfig` provides POSIX FIFO pipe registration (`registerNewFFmpegPipe` / `closeFFmpegPipe`), and Android natively bundles Google `libwebp` via `Bitmap.compress(WEBP_LOSSY / WEBP, quality)`.

## Decision

1. **Streaming Video-to-Animated-WebP Pipeline with Zero Native Rebuilds**:
   - **Decode & filter in FFmpeg**: `ConversionService.runFfmpegVideoAnimatedWebpExport` decodes the trimmed/split source video stream in FFmpeg, caps frame rate to at most 30 fps without upsampling lower-fps sources, optionally applies the shared `ffmpegVideoAnimatedScaleFilterFor` downscale, enforces the 30-second / 900-frame bounds, and streams `-pix_fmt rgba -c:v pam -f image2pipe` into an `FFmpegKitConfig` FIFO pipe.
   - **Encode & mux in Kotlin in O(1) memory**: `PamFrameStreamReader` reads one `P7` RGBA frame at a time into a reused `ByteArray` and a single recycled/reused `ARGB_8888` `Bitmap` (`setHasAlpha(false)`), avoiding multi-gigabyte raw frame temp files on disk. Android's platform WebP encoder compresses each frame at quality `75` into a reused `ExposedByteArrayOutputStream`, and `AnimatedWebpWriter` extracts the `ALPH` / `VP8 ` / `VP8L` sub-chunks (padding each to even bytes per RFC 9649) and streams full-canvas `ANMF` chunks (`B=1, D=0`, infinite loop) before patching the RIFF size and `VP8X` flags in `finish()`.
   - **FIFO deadlock prevention**: If FFmpeg fails or cancels before opening the FIFO writer end, an `Os.open(..., O_WRONLY | O_NONBLOCK)` unblocker wakes any reader blocked on `FileInputStream(pipePath)`, and `FFmpegKitConfig.closeFFmpegPipe` always runs after `ffmpegDone.await()`.
2. **Unified Animated Video Output Options & Defaults**:
   - Expose `TargetFormat(TargetId.Webp, "webp", "30s WebP")` under `FileCategory.Video` alongside `TargetId.Gif`.
   - Replace `VIDEO_GIF_RESOLUTION_OPTIONS` with `VIDEO_ANIMATED_RESOLUTION_OPTIONS` (`Original`, `1080p`, `720p`, `480p`) and change the initial/switched default for both GIF and Animated WebP from `480p` / `30 fps` to `Original` (`maxShortSidePixels = null`, `maxFrameRate = null`).
   - Route multi-segment split (`trimWindow.isMultiSegment`) cleanly through `runFfmpegVideoAnimatedExport` for both GIF and Animated WebP, exporting each split part into a subfolder.

## Consequences

- Video-to-animated-WebP ships with **0 bytes of additional native libraries**, no `ffmpeg-kit-next` rebuild, and no new third-party dependencies or license changes.
- Peak memory stays bounded to a single frame buffer and single `Bitmap` (capped at `16,000,000` pixels per frame) regardless of clip frame count.
- Both GIF and Animated WebP now default to `Original` resolution while preserving `1080p`, `720p`, and `480p` downscale options and single-file trim/split controls.
- Tradeoff: `AnimatedWebpWriter` writes independent full-frame lossy `ANMF` chunks (`Q75`) without `libwebp_anim` inter-frame delta/keyframe optimization, so animated WebP output is larger than desktop `libwebp -minimize_size` output; this is documented honestly under `Beta` in `formats/support-matrix.md` and `formats/registry.yaml`.
