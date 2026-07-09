<a id="readme"></a>

<h1 align="center">ZenConverter</h1>

<p align="center">
  <strong>Private, local-first file conversion for Android.</strong>
</p>

<p align="center">
  <a href="#readme">English</a> |
  <a href="#中文">中文</a>
</p>

<p align="center">
  <img alt="Version" src="https://img.shields.io/badge/version-0.1.0--dev-0A7E8C">
  <img alt="GitHub stars" src="https://img.shields.io/github/stars/Jasonzhu1207/ZenConverter?style=flat&logo=github&color=F59E0B">
  <img alt="GitHub downloads" src="https://img.shields.io/github/downloads/Jasonzhu1207/ZenConverter/total?label=downloads&logo=github&color=16A34A">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4">
  <img alt="Offline first" src="https://img.shields.io/badge/default-offline--first-111827">
  <img alt="No ads" src="https://img.shields.io/badge/ads-none-16A34A">
  <img alt="License direction" src="https://img.shields.io/badge/license-planned%20GPL--3.0--or--later-2563EB">
</p>

<p align="center">
  <img src="ZenConverter.jpeg" alt="ZenConverter app artwork" width="720">
</p>

ZenConverter is an open-source Android file converter built around a simple
rule: private files should not have to leave your phone.

It is native Kotlin + Jetpack Compose, uses Android's Storage Access Framework
for file access, and runs long conversions through a foreground service. It is
also intentionally honest. ZenConverter is not a universal converter yet; each
format is added one path at a time, with limits documented before it is
advertised as ready.

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

The short version: the app is useful enough to test, but every experimental
format still needs real sample files and physical-device smoke tests before it
should be treated as stable.

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

<p align="center">
  <a href="https://www.star-history.com/?repos=Jasonzhu1207%2FZenConverter&type=date&legend=top-left">
    <img alt="ZenConverter star history" src="https://api.star-history.com/svg?repos=Jasonzhu1207/ZenConverter&type=Date&legend=top-left">
  </a>
</p>

---

<a id="中文"></a>

# ZenConverter 中文说明

<p align="center">
  <a href="#readme">English</a> |
  <a href="#中文">中文</a>
</p>

ZenConverter 是一个 Android 本地文件转换器。它的目标很直接：能在手机上处理的文件，就不要上传到别人的服务器。

项目使用原生 Kotlin + Jetpack Compose。文件访问走 Android Storage Access
Framework，长任务放进前台服务里跑。它现在还不是万能转换器，也不会假装自己是。每个格式都会先做一条能验证的路径，再把限制写进文档。

## 为什么做它

在线转换器很方便，但遇到隐私文件、大视频、公司资料时，问题就来了。ZenConverter 选择本地优先：

- 默认文件留在设备上，
- 没有广告、强制账号、远程上传兜底，
- 当前 Android manifest 没有 `INTERNET` 权限，
- 大视频按正常使用场景处理，
- 支持范围写在公开的 [support matrix](formats/support-matrix.md) 里。

## 当前状态

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| 原生 Android 外壳 | 已完成 | Kotlin、Compose、Material 3、前台服务管线。 |
| 空转换任务流 | 已完成 | 文件选择、任务状态、进度、取消、失败状态。 |
| MP4 转 MP4 | 实验性 | Media3 Transformer 路径，还需要更多真机样本验证。 |
| MKV / WEBM / AVI / 3GP / TS / MTS 转 MP4 | 实验性 | FFmpeg 兼容路径，当前是 stream-copy remux，只适合已经兼容 MP4 的音视频流。 |
| 音频 / 视频提取音频到 M4A | 实验性 | 支持范围受 Media3、设备编解码器和当前 FFmpeg free tier 限制。 |
| JPG / PNG / WEBP 图片转换 | 实验性 | 使用 Android 原生 bitmap 路径。只处理静态图，不复制元数据。 |
| MP4 转 MP3、PDF、ZIP | 计划中 | 按路线图和支持矩阵继续推进。 |

一句话：现在可以开始测试，但 experimental 的格式还不能当成稳定承诺。

## 架构

```mermaid
flowchart LR
    Pick["选择文件"]
    Preset["选择预设"]
    Queue["任务队列"]
    Service["前台服务"]
    Engine["Media3 / FFmpeg / Native"]
    Output["保存输出"]

    Pick --> Preset --> Queue --> Service --> Engine --> Output
```

UI 不直接做转换。每个任务会根据输入、输出和设备能力选择模式：

- `FastCopy`：尽量不重编码，只做封装转换或提取。
- `Hardware`：使用 AndroidX Media3 / MediaCodec 处理常见视频任务。
- `Compatibility`：用 FFmpeg 补上 Android API 做不了的格式和操作。
- `SafeCache`：后续用于处理无法提供可用文件描述符的文件来源。

更多细节见 [docs/architecture.md](docs/architecture.md) 和
[docs/technical-route.md](docs/technical-route.md)。

## 路线图

1. 继续打磨第一条 Media3 视频路径。
2. 在真机上验证第一条 FFmpeg remux / extract 兼容路径。
3. 稳定静态图片转换。
4. 增加 MP4 转 MP3，再做 PDF / 图片和 ZIP 流程。
5. 通过 GitHub Releases 发布签名 APK。

工作计划见 [docs/roadmap.md](docs/roadmap.md)。

## 开发

当前推荐用 VS Code 编辑，用 Android Studio Run/Debug 在实体 Android
设备上做验证。本机把 Android 工具链统一放在 `E:\AndroidDev`，避免重复占用 SDK 和 Gradle 缓存。

需要命令行冒烟测试时，可以从项目根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\install-debug.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\launch-debug.ps1
```

环境说明见 [docs/development-setup.md](docs/development-setup.md)。

## 发布

签名 APK 的发布流程已经接入 GitHub Actions。Secrets 和触发方式见
[docs/release-automation.md](docs/release-automation.md)。

公开 alpha 准备好后，下载入口会放到
[GitHub Releases](https://github.com/Jasonzhu1207/ZenConverter/releases)。

## 支持项目

现在最有用的支持是点 star、提交带样本说明的问题、或者在实体 Android 设备上帮忙测试。

付费支持和赞助入口还没有接入。等你确定用 GitHub Sponsors、爱发电、Buy Me a Coffee
或其他方式后，可以把链接放到这里，避免 README 先放一个打不开的支付入口。

## Star 曲线

<p align="center">
  <a href="https://www.star-history.com/?repos=Jasonzhu1207%2FZenConverter&type=date&legend=top-left">
    <img alt="ZenConverter star history" src="https://api.star-history.com/svg?repos=Jasonzhu1207/ZenConverter&type=Date&legend=top-left">
  </a>
</p>
