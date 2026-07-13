# 0008 Native Image PDF Conversion

## Status

Accepted.

## Context

The next V1 format step is image/PDF conversion. The project should avoid new
dependencies unless platform APIs are not enough. Android provides
`PdfDocument` for writing simple PDF pages and `PdfRenderer` for rendering PDF
pages to bitmaps, but both APIs require careful memory handling.

## Decision

Use platform PDF APIs for the first image/PDF milestone:

- Image to PDF uses `android.graphics.pdf.PdfDocument`.
- PDF to image uses `android.graphics.pdf.PdfRenderer`.
- Image to PDF exposes A4-fit and original-ratio page modes. A4 fit preserves
  image ratio and chooses portrait or landscape. Original ratio keeps the image
  aspect ratio with a capped physical long edge.
- Image decode for PDF output is capped at a 4096 px long side and 16 MP.
- PDF to image pre-scans page sizes one page at a time, computes one bounded
  common bitmap size, reuses a single `ARGB_8888` bitmap, and renders each page
  into a centered fit rectangle.
- Password-protected PDFs are retried with an in-memory password only when the
  platform supports `LoadParams`, currently Android 15 or PDF extension 13.
- Non-seekable provider PDFs use SafeCache only after direct descriptor opening
  fails, with available storage checked before copying.

## Consequences

- No dependency, attribution, or license update is needed for this milestone.
- PDF to image is rasterization only. It does not provide OCR, text extraction,
  editable document conversion, or embedded-image extraction.
- High-detail PDF rendering is not lossless and remains bounded to reduce
  foreground-service crash risk.
- Multi-page PDF output creates one image per page and treats final output as
  all-or-nothing for cancellation and failure cleanup.
- Physical-device testing should cover pure text PDFs, scanned PDFs, mixed
  content PDFs, mixed page sizes, many-page PDFs, image-to-single-PDF,
  image-to-multiple-PDF, cancellation, and password-protected PDFs.
