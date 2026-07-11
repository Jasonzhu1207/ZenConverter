# Third-Party Thanks

This file records dependencies and projects that ZenConverter relies on or
learns from. Add entries before a dependency becomes part of the build.

| Project | Use | License | Status | Notes |
| --- | --- | --- | --- | --- |
| AndroidX Compose | Planned UI toolkit | Apache-2.0 | Planned | Declared in Gradle skeleton. |
| AndroidX Material Icons Extended | App action/category icons | Apache-2.0 | Active | Official Compose icon set used for UI clarity. |
| AndroidX Media3 Transformer | First real MP4 export path | Apache-2.0 | Active | Uses official AndroidX Media3 `media3-transformer` 1.10.1. |
| FFmpegKit maintained fork | First FFmpeg compatibility remux/extract path | LGPL-3.0 for current Free tier | Active | Uses `dev.ffmpegkit-maintained:ffmpeg-kit-free-71:7.1.5` or a local AAR fallback, 7.1 LTS, arm64-v8a only, no `-gpl` artifact. Gradle may prefer locally supplied non-GPL Basic/Full AARs for MP3-capable development builds once their license/source record is completed. |
| smart-exception-java / smart-exception-common | Transitive helper used by the FFmpeg AAR | BSD-3-Clause | Active transitive | Pulled by the FFmpegKit POM or matching local JAR fallback as `com.arthenica:smart-exception-java:0.2.1` and `com.arthenica:smart-exception-common:0.2.1`. |
| FFmpeg | Compatibility engine core inside the maintained FFmpegKit AAR | LGPL-3.0 for selected Free tier | Active | 7.1 Free workflow enables libaom, dav1d, libvpx, opus, libvorbis, and speex; disables arm-v7a, arm-v7a-neon, x86, and x86-64. |
| FFmpegKit | Reference only | LGPL/GPL depending on package | Reference | Original project is retired; do not rely on it blindly. |
