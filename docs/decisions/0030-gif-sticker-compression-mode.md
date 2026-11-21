# GIF Optimization and Best-Effort Sticker (≤ 5 MB) Mode

## Status

Accepted

## Context

In GitHub Issue #28, users noted that converting WebM (VP8/VP9) clips to GIF significantly increases file size—often exceeding the 5 MB sticker import limit used by messaging apps such as WeChat. Two root causes existed in ZenConverter's baseline GIF command:

1. `ffmpegVideoGifFilterChainFor` hardcoded `fps=30`, forcing 15–24 fps clips to duplicate frames up to 30 fps.
2. `paletteuse` used `dither=sierra2_4a` without inter-frame transparency/rectangle optimization (`diff_mode=rectangle` or `-gifflags +transdiff`), inflating LZW output on static regions.

Unlike video codecs, GIF has no rate-control (`-b:v` / `-crf`) parameter, and passing FFmpeg's `-fs 5M` would brutally truncate the animation mid-playback instead of scaling quality gracefully.

## Decision

1. **Zero-dependency base GIF optimization** (`ConversionService.runFfmpegVideoGifExport` / `ffmpegVideoGifArgumentsFor`):
   - Cap GIF frame rate at `30` without upsampling lower-fps sources (`sourceFps.coerceIn(1.0f, 30.0f)`), matching the animated WebP path via a shared `formatFfmpegFps` helper.
   - Add `diff_mode=rectangle` to `paletteuse` and `-gifflags +transdiff` to the GIF muxer flags. Both filters and codec flags are already bundled in `app/libs/ffmpeg-kit-next-7.1.0.aar`.

2. **Optional `Sticker (≤ 5 MB · best effort)` GIF mode**:
   - Keep existing manual resolution options unchanged (`Original`, `1080p`, `720p`, `480p`) and expose a single `Sticker (≤ 5 MB · best effort)` preset (`VideoExportOptions.gifStickerMode`) for both single-file and batch GIF exports, hiding the manual resolution selector when active.
   - `ConversionService.runFfmpegVideoGifStickerExport` estimates a starting rung from clip duration and runs at most 4 adaptive passes across a 6-rung ladder (`480p/15fps/256c` → `360p/15fps/192c` → `320p/12fps/128c` → `240p/12fps/96c` → `240p/10fps/64c` → `180p/8fps/64c`) using `stats_mode=diff`, Bayer dithering (`bayer_scale` 3–5), `diff_mode=rectangle`, and `-gifflags +transdiff`, aiming for `≤ 4.9 MB` (`4,900,000` bytes) to clear both decimal 5 MB and binary 5 MiB limits.
   - Each retry opens and closes a fresh `FfmpegInputSource` via `openFfmpegInputSource` so SAF descriptors/pipes are never reused across passes, maps progress monotonically across `[0..0.70..0.85..0.94..1.00]`, and cleans up all non-winning candidate files immediately.
   - If even the lowest rung exceeds `5,000,000` bytes (for example on long or high-motion live-action clips), the export still completes with the smallest generated GIF and attaches a non-blocking `noticeMessage` on `ConversionTaskState` / `TaskProgress` rendered via `ResultNoticeLine` on the completed task card.

## Consequences

- Short animated clips (especially WebM stickers) produce noticeably smaller GIFs even in standard mode, with zero new dependencies or APK size growth.
- Users targeting 5 MB sticker imports get a one-tap best-effort preset without manual trial-and-error across resolution and frame rate, while still receiving an honest, usable output and actionable notice if a 30-second high-motion clip cannot fit under 5 MB.
- Tradeoff: Sticker mode prioritizes predictable size reduction via `stats_mode=diff` + Bayer dithering over smooth error-diffusion gradients (`sierra2_4a`) and may run up to 4 quick FFmpeg passes on borderline clips.
