# 0017 Woff2 Native Font Conversion

## Status

Accepted.

## Context

Font conversion (TTF/OTF to web fonts and back) is a common offline task, and the
core algorithm needs no Android platform API. Android has no built-in SFNT to
WOFF2 codec. WOFF2 uses Brotli plus a glyf/loca/hmtx transform that is not
practical to reimplement in app code. The project already has a native
integration pattern: a checked-in `arm64-v8a` shared library plus a thin Kotlin
JNI wrapper, used by the Office path.

## Decision

Add two font paths behind a new `Font` conversion category, both zero-option:

- TTF/OTF <-> WOFF2 through the Google woff2 C++ reference implementation,
  cross-compiled to `libzen_woff2.so` for `arm64-v8a` and wrapped by
  `org.zenconverter.app.font.Woff2Native` (`compress` and `decompress`
  `byte[] -> byte[]` JNI methods).
- TTF/OTF <-> WOFF 1.0 through a pure-Kotlin codec (`WoffCodec`) using
  `java.util.zip` only. WOFF 1.0 is zlib-per-table and needs no native code or
  third-party dependency.

Input format is detected by magic bytes (`wOF2`, `wOFF`, SFNT signatures), not
SAF MIME. For WOFF2/WOFF inputs the output extension is resolved at conversion
time from the font flavor: `.ttf` for TrueType (glyf) and `.otf` for
CFF/PostScript outlines. Fonts are read whole into memory with a 32 MiB cap and
written to `Documents/ZenConverter`.

## Consequences

- License: woff2 is Apache-2.0 and Brotli is MIT, both compatible with the
  `AGPL-3.0-or-later` project license. The WOFF 1.0 codec adds no third-party
  code.
- WOFF2 is arm64-only, matching the app's single-ABI release. The pure-Kotlin
  WOFF path would work on other ABIs but is not shipped that way.
- The native library is built manually (NDK + CMake) and checked in; Codex does
  not run the build. Its SHA-256 and upstream pin are recorded in the
  attribution files.
- No font subsetting, hinting control, variable-font editing, or TrueType
  Collection splitting in this milestone.
