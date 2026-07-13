# Third-Party Thanks

This file records dependencies and projects that ZenConverter relies on or
learns from. Add entries before a dependency becomes part of the build.

| Project | Use | License | Status | Notes |
| --- | --- | --- | --- | --- |
| AndroidX Compose | Planned UI toolkit | Apache-2.0 | Planned | Declared in Gradle skeleton. |
| AndroidX Material Icons Extended | App action/category icons | Apache-2.0 | Active | Official Compose icon set used for UI clarity. |
| AndroidX Media3 Transformer | First real MP4 export path | Apache-2.0 | Active | Uses official AndroidX Media3 `media3-transformer` 1.10.1. |
| PDFBox-Android | True PDF merge and selectable PDF text extraction | Apache-2.0 | Low-frequency maintenance | Local Maven Central AAR at `app/libs/pdfbox-android-2.0.27.0.aar`, SHA-256 `30277f879cfd571db2a137582c95516a0d4ea6778e945519bc58ca93d57d88c7`. Chosen because Android platform APIs do not provide broad loss-preserving PDF merge, and platform PDF text extraction is only available on newer API/extension levels. |
| Bouncy Castle Java APIs | Transitive crypto support required by PDFBox-Android | Bouncy Castle License, MIT-style | Active | Local Maven Central JARs `bcprov-jdk15to18`, `bcpkix-jdk15to18`, and `bcutil-jdk15to18` version `1.72`. Required by the PDFBox-Android POM, especially for PDF security/encryption handling. |
| FFmpegKitNext self-built AAR | FFmpeg compatibility remux/extract/transcode path | LGPLv3 main license; packaged LAME LGPLv2-style, libiconv GPLv3, cpu-features Apache-2.0 | Active local binary | Built from `arthenica/ffmpeg-kit-next` tag `v7.1.0`, commit `1e64a8cdda1b045b014c0a54e9d395929c7b6ccc`, with `--enable-lame`, `armeabi-v7a` and `arm64-v8a`. SHA-256: `6f3bb932ba76ff2627bef6cbfd77fa24bb7186afe27d88da37f69cd60c207602`. |
| smart-exception-java / smart-exception-common | Transitive helper used by the FFmpeg AAR | BSD-3-Clause | Active transitive | Pulled by the FFmpegKit POM or matching local JAR fallback as `com.arthenica:smart-exception-java:0.2.1` and `com.arthenica:smart-exception-common:0.2.1`. |
| FFmpeg | Compatibility engine core inside the self-built FFmpegKitNext AAR | LGPL/GPL depending on linked libraries | Active | Self-build enables LAME MP3 encoding and FFmpegKit's default libiconv path; disables x86 and x86-64. |
| FFmpegKit | Reference only | LGPL/GPL depending on package | Reference | Original project is retired; do not rely on it blindly. |
