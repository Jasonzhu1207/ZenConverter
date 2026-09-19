# Office2PDF JNI source

This is the reproducible source for `libzen_office2pdf.so`. It pins the
upstream `office2pdf` release `v0.7.0` at commit
`bb6658bbb66003d0ab5131375acf4daf2f8556a6`, which exposes
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
Run/Debug. Codex must not run this build. The checked-in September 19, 2026 arm64
build is `38,124,952` bytes with SHA-256
`60bda29a607679d7f4c543d8c32a5ca60b21ea02e8e59f3c43bb4bcacb75b025`.
After rebuilding, record the new SHA-256 in the third-party attribution files
and test Chinese DOCX, PPTX, and XLSX output on a physical arm64 device.
