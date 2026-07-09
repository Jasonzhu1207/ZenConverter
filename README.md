<h1 align="center">ZenConverter</h1>

<p align="center">
  <strong>Private, local-first file conversion for Android.</strong>
</p>

<p align="center">
  English |
  <a href="README_zh.md">中文</a>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-0.1.0--dev-0A7E8C">
  <img alt="WIP" src="https://img.shields.io/badge/%F0%9F%9A%A7%20WIP-Work%20in%20Progress-F59E0B">
  <img alt="GitHub stars" src="https://img.shields.io/github/stars/Jasonzhu1207/ZenConverter?style=flat&logo=github&color=F59E0B">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4">
  <img alt="Offline first" src="https://img.shields.io/badge/default-offline--first-111827">
  <img alt="No ads" src="https://img.shields.io/badge/ads-none-16A34A">
  <img alt="License direction" src="https://img.shields.io/badge/license-planned%20GPL--3.0--or--later-2563EB">
</p>

<p align="center">
  <img src="docs/assets/zenconverter-cover.png" alt="ZenConverter app icon" width="240">
</p>

ZenConverter is an open-source Android file converter built around a simple
rule: private files should not have to leave your phone.

It is native Kotlin plus Jetpack Compose, uses Android's Storage Access
Framework for file access, and runs long conversions through a foreground
service. It is also intentionally honest. ZenConverter is not a universal
converter yet. Each format is added one path at a time, with limits documented
before it is advertised as ready.

## Why It Exists

Most online converters are convenient until the file is private, huge, or both.
ZenConverter takes the slower, sturdier route:

- files stay on device by default,
- no ads, forced accounts, or remote-upload fallback,
- no `INTERNET` permission in the current app manifest,
- large videos are treated as normal inputs, not edge cases,
- format support is tracked in the public [support matrix](formats/support-matrix.md).

## Current Status

| Area | Status | Notes |
| --- | --- | --- |
| Native Android shell | Done | Kotlin, Compose, Material 3, foreground service pipeline. |
| No-op conversion jobs | Done | File selection, task state, progress, cancel, and failure states. |
| MP4 to MP4 | Experimental | Media3 Transformer path; still needs wider physical-device verification. |
| MKV / WEBM / AVI / 3GP / TS / MTS to MP4 | Experimental | FFmpeg-compatible stream-copy remux. It only works when the streams already fit MP4. |
| Audio / video audio to M4A | Experimental | Media3 for supported native inputs; FFmpeg copy path for compatible non-MP4 audio tracks. |
| JPG / PNG / WEBP conversion | Experimental | Native Android bitmap path. Static images only; metadata is not copied. |
| MP4 to MP3, PDF, ZIP | Planned | Tracked in the roadmap and support matrix. |

The short version: the app is ready for testing, but every experimental format
still needs real sample files and physical-device smoke tests before it should
be treated as stable.

## Architecture

```mermaid
flowchart LR
    Pick["Select file"]
    Preset["Choose preset"]
    Queue["Task queue"]
    Service["Foreground service"]
    Engine["Media3 / FFmpeg / Native"]
    Output["Save output"]

    Pick --> Preset --> Queue --> Service --> Engine --> Output
```

The conversion engine is deliberately split from the UI. The app chooses a mode
per task:

- `FastCopy`: remux or extract without re-encoding where possible.
- `Hardware`: AndroidX Media3 / MediaCodec for common Android-supported video work.
- `Compatibility`: FFmpeg path for containers and operations Android APIs cannot cover.
- `SafeCache`: future fallback for file providers that cannot provide usable descriptors.

More detail lives in [docs/architecture.md](docs/architecture.md) and
[docs/technical-route.md](docs/technical-route.md).

## Roadmap

1. Keep hardening the first real Media3 video path.
2. Verify the first FFmpeg compatibility remux/extract path on physical devices.
3. Stabilize static image conversion.
4. Add MP4 to MP3, then PDF/image and ZIP workflows.
5. Publish signed APKs through GitHub Releases.

See [docs/roadmap.md](docs/roadmap.md) for the working plan.

## Development

The preferred workflow right now is VS Code for editing plus Android Studio
Run/Debug on a physical Android device for verification. This local setup keeps
the shared Android toolchain under `E:\AndroidDev` to avoid duplicate SDK and
Gradle caches.

Command-line smoke scripts are available when needed:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\install-debug.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\launch-debug.ps1
```

Setup notes are in [docs/development-setup.md](docs/development-setup.md).

## Releases

Signed APK publishing is wired through GitHub Actions. Release setup and secrets
are documented in [docs/release-automation.md](docs/release-automation.md).

Download links will point to
[GitHub Releases](https://github.com/Jasonzhu1207/ZenConverter/releases) once a
public alpha is ready.

## Support

If ZenConverter saves you time, the best support today is a star, a useful bug
report with a sample-file description, or testing on a real Android device.

Paid support and donation links are not connected yet. They will be added here
before the public alpha so the README does not send people to a dead checkout.

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=Jasonzhu1207/ZenConverter&type=Date)](https://star-history.com/#Jasonzhu1207/ZenConverter&Date)
