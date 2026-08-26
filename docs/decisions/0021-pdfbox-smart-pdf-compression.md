# 0021 - PDFBox Smart PDF Compression

## Status

Accepted.

## Context

Users frequently need to compress large PDF documents on mobile devices (e.g. scanned documents, reports with high-resolution photos) for email transmission, archiving, or sharing.

ZenConverter already bundles `PDFBox-Android` (`com.tom-roush:pdfbox-android:2.0.27.0`) for PDF merging, selectable text extraction, and password encryption/decryption. The library contains image XObject inspection and JPEG creation helpers (`JPEGFactory`, `LosslessFactory`), enabling object-level raster image downsampling and re-encoding without external rasterizer engines or remote services.

## Decision

Add a dedicated `Compress PDF` target format for PDF inputs with three preset options:

- **High quality** (`0.85` JPEG quality, max dimension `2160` px): For design documents and presentations where image clarity is paramount.
- **Balanced** (`0.70` JPEG quality, max dimension `1440` px): Default recommendation, providing 40%–75% file size reduction on image-heavy PDFs.
- **Small file** (`0.50` JPEG quality, max dimension `1080` px): Aggressive size reduction for emails and fast uploading.

Key implementation rules:
1. Traverse both page resources (`page.resources.xObjectNames`) and nested form resources (`PDFormXObject.resources`).
2. Deduplicate images using a `visited` set of `COSBase` objects to prevent redundant decompression/re-compression when the same image is referenced multiple times across pages.
3. Clean redundant page thumbnail streams (`/Thumb`) from page dictionaries.
4. Scale extracted bitmaps with `Bitmap.createScaledBitmap` only when exceeding `maxDimension`, and explicitly call `recycle()` on both source and scaled bitmaps to protect against Android OOM.
5. Re-encode images using `JPEGFactory.createFromImage(doc, bitmap, quality)`, which correctly preserves alpha channels via soft masks (`SMask`).
6. Preserve vector paths, text layers, and fonts completely untouched.
7. Support password-protected source PDFs transparently using provided credentials.

## Consequences

- Completely offline, zero new dependencies, and zero APK size increase.
- Works for both single PDF files and batch imports.
- Vector graphics, fonts, and selectable text remain sharp and fully searchable.
