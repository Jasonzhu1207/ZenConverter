# Office2PDF JNI source

This is the reproducible source for `libzen_office2pdf.so`. It pins the
upstream `office2pdf` source at commit
`e9129b3558f7d758922a5530766d19545ebaa28c`, which exposes
`ConvertOptions.font_paths` to Typst's font searcher.

The Kotlin wrapper copies Noto Sans CJK and Noto Serif CJK into app-private
storage and passes that directory to `convertBytesWithFontPaths`. The app keeps
the original `convertBytes` entry as a legacy fallback so an earlier shared
library still starts, but it cannot use the explicit CJK font directories.

Build the native library manually from this directory after configuring the
Android Rust target and NDK. The manifest includes an empty `[workspace]` table
so this crate can be copied under a server directory that also has a parent
`Cargo.toml`:

```powershell
cargo ndk -t arm64-v8a -o ../../app/src/main/jniLibs build --release
```

The command must replace
`app/src/main/jniLibs/arm64-v8a/libzen_office2pdf.so` before Android Studio
Run/Debug. Codex must not run this build. The checked-in July 14, 2026 arm64
build is `72,348,456` bytes with SHA-256
`46779f04fc231fb1b1104ba766636e372a1be7cd49b71909346953e512a8e09c`.
After rebuilding, record the new SHA-256 in the third-party attribution files
and test Chinese DOCX, PPTX, and XLSX output on a physical arm64 device.
