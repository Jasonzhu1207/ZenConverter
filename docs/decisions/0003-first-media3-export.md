# 0003 First Media3 Export

## Status

Accepted for implementation, pending manual device verification.

## Context

ZenConverter needs one real local conversion path before adding broader media
engines. The app already has SAF file picking, an output directory picker, and a
foreground service for conversion progress.

## Decision

Use official AndroidX Media3 Transformer `1.10.1` for the first real video path:
MP4 input to MP4 output.

The service reads the selected input URI directly through Media3. The export is
written to app cache first, then copied to either the default public media
destination or a user-selected SAF output directory. Custom SAF output uses
`DocumentsContract.createDocument`. This avoids treating `content://` URIs as
absolute file paths and avoids `DocumentFile.listFiles()`.

## Consequences

- This is not a universal video converter.
- Non-MP4 targets remain planned and should fail clearly until connected.
- The first path may require cache space roughly matching the export size.
- Compression presets are connected for the Media3 MP4 path and still need
  physical-device verification.
- FFmpeg compatibility work remains separate and should prefer LGPL-compatible
  builds by default.
