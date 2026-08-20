# 0018 - Image Super-Resolution (Bilinear Algorithmic First Step)

## Status

Accepted.

## Context

Users asked for an image super-resolution feature. The long-term plan is a
deep-learning model, but the first milestone must ship a small, verifiable step
using only the Android platform bitmap API. The feature must integrate into the
existing image conversion flow (no separate entry point) and behave like the
video compression presets: once super-resolution is selected, the other image
options are hidden and ignored.

Any input the platform can already decode to a `Bitmap` (JPG/JFIF/PNG/WEBP/GIF/
HEIC/ICO and others) can be upscaled with `Bitmap.createScaledBitmap`, so input
format handling stays on the existing decode path and does not need to change.

## Decision

Add an algorithmic super-resolution option to the image export path:

- Model the choice as `ImageSuperResolutionMode` with `Off(1)`, `X2(2)`,
  `X3(3)`, and `X4(4)` scale factors, carried on `ImageExportOptions` alongside
  quality and WEBP lossless.
- In the UI, render a "Super resolution" dropdown inside `ImageOptions` for
  raster image targets (JPG/JFIF/PNG/WEBP). While a scale is active, hide and
  ignore the quality control and force output quality to 100 (original), and
  hide the GIF frame-splitting control so only the first frame path applies.
  PDF (image-to-PDF) and ICO outputs do not expose the option.
- In the conversion service, upscale the decoded bitmap with
  `Bitmap.createScaledBitmap(bitmap, width * scale, height * scale, true)`
  immediately after decode and before the existing ICO/compress/PDF writers.
- Guard memory safety: reject outputs whose pixel count exceeds
  `SUPER_RESOLUTION_MAX_PIXELS` (64,000,000, matching the existing decode
  budget) and translate `OutOfMemoryError` into a clear failure. Cancellation is
  checked before the upscale.

This is a bilinear algorithmic upscale, not a learning-based or lossless
enhancement. It does not add new output formats or a new dependency.

## Consequences

- The feature is a single seam (`applySuperResolutionIfNeeded`); a future deep
  learning model can replace `createScaledBitmap` inside it, and new
  `ImageSuperResolutionMode` values can express model-based modes without
  restructuring the UI or data model.
- Upscaling large sources is bounded by the pixel cap, and sources larger than
  the decode budget are first downsampled to at most 64 MP and then upscaled, so
  very large inputs do not get a true full-resolution upscale.
- The UI labels it "bilinear upscale" and "original quality", not "AI" or
  "lossless", to keep the honest-support promise.
- No new dependency, license, or attribution changes are required.
