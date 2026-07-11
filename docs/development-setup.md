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
groups, and excludes `dev.ffmpegkit-maintained` from the Aliyun public fallback.
The maintained FFmpeg AAR can appear as metadata on mirrors before the `.aar`
itself is available, which makes Gradle stop at the mirror and fail artifact
resolution.

FFmpegKit local fallback: this machine currently receives `403 Forbidden` from
Maven Central for `dev.ffmpegkit-maintained:ffmpeg-kit-free-71:7.1.5`, so
`app/build.gradle.kts` prefers local files when they exist. Gradle checks for
locally supplied non-GPL MP3-capable tiers first, then falls back to the Free
tier:

- `app/libs/ffmpeg-kit-basic71-7.1.5-arm64-v8a.aar`
  - optional local input for MP3/AAC-capable development builds
  - record source, license terms, and SHA-256 before shipping a build with it
- `app/libs/ffmpeg-kit-full71-7.1.5-arm64-v8a.aar`
  - optional local input for broader non-GPL codec coverage
  - record source, license terms, and SHA-256 before shipping a build with it

- `app/libs/ffmpeg-kit-free71-7.1.5-arm64-v8a.aar`
  - source: `https://github.com/ffmpegkit-maintained/ffmpeg/releases/download/v7.1.5-lts-android/ffmpeg-kit-free71-7.1.5-arm64-v8a.aar`
  - SHA-256: `78b57215ca8790264cf48ea755ca4629ccebe79660d37ab14f41d8077e9dece7`
- `app/libs/smart-exception-common-0.2.1.jar`
  - source: `https://repo1.maven.org/maven2/com/arthenica/smart-exception-common/0.2.1/smart-exception-common-0.2.1.jar`
  - SHA-256: `1cad0fb4dfa01755a014331b5ed199281d2c3fab5aca5c9d7abd0b41d0ec3f7b`
- `app/libs/smart-exception-java-0.2.1.jar`
  - source: `https://repo1.maven.org/maven2/com/arthenica/smart-exception-java/0.2.1/smart-exception-java-0.2.1.jar`
  - SHA-256: `5b96aaa5f191dedbef72fb0c38f1a2b01807920afc0d92a75a2acd6e0cc7703c`

The `app/libs` binary files are local build inputs and are ignored by git. If
they are absent, Gradle falls back to the normal Maven coordinate.

MP3 output needs `libmp3lame`. The documented Free AAR does not include that
encoder, so physical-device MP3 exports fail fast with a specific message unless
an MP3-capable FFmpegKit AAR is supplied locally or the project later moves to a
self-built LGPL FFmpeg package.

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
them. Self-building FFmpeg can raise the disk requirement to 20 to 40 GB.

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
