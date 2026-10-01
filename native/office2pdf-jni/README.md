# Office2PDF JNI source

This is the reproducible source for `libzen_office2pdf.so`. It pins the
upstream `office2pdf` release `v0.8.0` at commit
`0676e599a7a90d164ebabc7341fc814343cf48e2`, which exposes
`ConvertOptions.font_paths` to Typst's font searcher.

For the server workflow, copy this directory to
`/root/zenconverter-office2pdf-build/office2pdf-jni` and run the build script
from there.
The script looks for `ANDROID_NDK_HOME` first, then a few common NDK install
paths.

The Kotlin wrapper copies Noto Sans CJK and Noto Serif CJK into app-private
storage and passes that directory to `convertBytesWithFontPaths`. The app keeps
the original `convertBytes` entry as a legacy fallback so an earlier shared
library still starts, but it cannot use the explicit CJK font directories.

Build the native library manually from this directory after configuring the
Android Rust target and NDK. The manifest includes an empty `[workspace]` table
so this crate can live under a server directory that also has a parent
`Cargo.toml`:

```bash
bash build-arm64-v8a.sh
```

The script writes the compiled shared library to
`../built-jniLibs/arm64-v8a/libzen_office2pdf.so`. Copy that file over
`app/src/main/jniLibs/arm64-v8a/libzen_office2pdf.so` before Android Studio
Run/Debug. Codex must not run this build. The checked-in October 01, 2026 arm64
build is `38,213,128` bytes with SHA-256
`e4a7a1d845a8654efe39eb022a09a8864935bc04573ab599b5bd64dabc350cd9`.
After rebuilding, record the new SHA-256 in the third-party attribution files
and test Chinese DOCX, PPTX, and XLSX output on a physical arm64 device.
