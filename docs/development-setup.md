# Development Setup

The preferred development setup is VS Code plus Codex, backed by the shared
Android toolchain in `E:\AndroidDev`. Android Studio is installed and can be
used for Logcat, profiling, layout inspection, and SDK management, but it is not
required for day-to-day edits.

## Current Local Toolchain

| Purpose | Path |
| --- | --- |
| Android Studio | `E:\AndroidDev\android-studio` |
| Gradle JDK | `E:\AndroidDev\android-studio\jbr` |
| Legacy JDK 17 | `E:\AndroidDev\JDK\jdk-17.0.15+6` |
| Android SDK | `E:\AndroidDev\SDK` |
| Gradle user home | `E:\AndroidDev\.gradle` |
| Android user home | `E:\AndroidDev\.android` |

Project scripts source `scripts/dev-env.ps1` so command-line builds, Codex
runs, and Android Studio use the same SDK and cache locations.

Current project toolchain:

- Android Gradle Plugin: `9.2.1`
- Gradle wrapper: `9.4.1`
- Gradle runtime JDK: Android Studio bundled JBR 21 on `E:\AndroidDev`
- App source/target compatibility: Java 17
- Compile SDK: Android API 36 with extension 20
- Target SDK: 35 for now

Repository order matters. `settings.gradle.kts` keeps official `google()` and
`mavenCentral()` before the Aliyun mirrors, limits `google()` to Android/Google
groups, and leaves the Aliyun public mirror last.

FFmpegKit local core: `app/build.gradle.kts` requires the self-built local AAR
below. There is no Maven fallback for FFmpegKit, and release CI verifies the
recorded SHA-256 instead of downloading a prebuilt third-party fork artifact.

- `app/libs/ffmpeg-kit-next-7.1.0.aar`
  - source: `https://github.com/arthenica/ffmpeg-kit-next`
  - tag: `v7.1.0`
  - commit: `1e64a8cdda1b045b014c0a54e9d395929c7b6ccc`
  - build command: replacement build command must be recorded before a tagged release
  - SHA-256: `d1f2512e806ac3ff99b2f4c3d2e36fcca8c5c0eec548d84da81cf94d054cf406`
  - ABI: `arm64-v8a`
  - packaged license files include LAME, libiconv, libvpx, Opus, x264, x265,
    and cpu-features notices

The published APK is arm64-only to keep size down and match the Office2PDF
native library.
- `app/libs/smart-exception-common-0.2.1.jar`
  - source: `https://repo1.maven.org/maven2/com/arthenica/smart-exception-common/0.2.1/smart-exception-common-0.2.1.jar`
  - SHA-256: `1cad0fb4dfa01755a014331b5ed199281d2c3fab5aca5c9d7abd0b41d0ec3f7b`
- `app/libs/smart-exception-java-0.2.1.jar`
  - source: `https://repo1.maven.org/maven2/com/arthenica/smart-exception-java/0.2.1/smart-exception-java-0.2.1.jar`
  - SHA-256: `5b96aaa5f191dedbef72fb0c38f1a2b01807920afc0d92a75a2acd6e0cc7703c`

The recorded FFmpegKitNext AAR is a checked-in release input so clean CI
checkouts can build without downloading a third-party fork artifact. Other
`app/libs` binaries, including optional smart-exception JAR caches, stay ignored
by git. If the FFmpegKitNext AAR is absent, Gradle fails during configuration
with a clear message.

MP3 output needs `libmp3lame`. The app probes FFmpeg's encoder list before MP3
export and keeps the MP3 rows experimental until physical-device samples pass.

## Office2PDF Native Rebuild

The experimental Office renderer is reproducible from
`native/office2pdf-jni`. It pins `developer0hye/office2pdf` at commit
`e9129b3558f7d758922a5530766d19545ebaa28c` and exposes
`convertBytesWithFontPaths`, which passes app-private CJK font directories
through `ConvertOptions.font_paths`.

This rebuild needs the Android Rust target, an installed Android NDK, and
`cargo-ndk`. Run it manually from the native source directory only when the
toolchain is ready:

```powershell
cargo ndk -t arm64-v8a -o ../../app/src/main/jniLibs build --release
```

The command replaces
`app/src/main/jniLibs/arm64-v8a/libzen_office2pdf.so`. The checked-in July 14,
2026 rebuild is `72,348,456` bytes with SHA-256
`46779f04fc231fb1b1104ba766636e372a1be7cd49b71909346953e512a8e09c`
and exports `convertBytesWithFontPaths`. Do not validate the CJK fix with a
Kotlin-only build: an older shared library can still start through the legacy
`convertBytes` fallback, but it cannot pass the bundled CJK font directory.
Chinese text rendering has been manually verified on an arm64 physical device;
layout fidelity remains experimental. Codex does not run this build or install
step.

`settings.gradle.kts` maps the `com.android.application` plugin id to
`com.android.tools.build:gradle` through `pluginManagement.resolutionStrategy`.
Keep that mapping while using AGP 9.x, because the plugin marker may not resolve
cleanly in this local setup.

## Common Commands

Run commands from the project root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\install-debug.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\launch-debug.ps1
```

Use `build-debug.ps1` for a local smoke build, `install-debug.ps1` for physical
device testing, and `launch-debug.ps1` after the app is installed.

## Minimal Disk Budget

| Component | Estimated Size |
| --- | ---: |
| JDK 17 or 21 | 0.3 to 0.6 GB |
| Android command-line tools | 0.3 to 0.5 GB |
| platform-tools / adb | 0.03 to 0.08 GB |
| build-tools | 0.08 to 0.15 GB |
| Android SDK platform | 0.15 to 0.3 GB |
| Gradle cache | 1.5 to 3.0 GB |
| Kotlin, AGP, Compose, AndroidX cache | 1.0 to 2.5 GB |
| Project build cache | 0.8 to 2.0 GB |

Expected practical minimum: 4 to 6 GB.
Recommended breathing room: 8 to 10 GB.

Do not install the Android emulator or NDK until the project explicitly needs
them. Self-building FFmpeg can raise the disk requirement to 20 to 40 GB. The
July 12, 2026 FFmpegKitNext build succeeded on a 4 vCPU / 7.6 GiB RAM Ubuntu
24.04 server after adding a 12 GiB swap file; peak root filesystem usage was
about 24 GB.

## First Build Checklist

1. Confirm `E:\AndroidDev\android-studio\jbr\bin\java.exe -version` works.
2. Confirm `E:\AndroidDev\SDK\platform-tools\adb.exe devices` sees the phone.
3. Run `powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1`.
4. Run `powershell -ExecutionPolicy Bypass -File .\scripts\install-debug.ps1`.
5. Launch from the phone or run `scripts\launch-debug.ps1`.

## Dependency Rule

Add dependencies one at a time. After adding each dependency:

- build the app,
- run on a physical device if it touches media or file access,
- record it in `third_party/THANKS.md`,
- update `docs/license-and-attribution.md`.
