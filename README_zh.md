# ZenConverter

<p align="center">
  <a href="README.md">English</a> |
  中文
</p>

<p align="center">
  <a href="https://github.com/Jasonzhu1207/ZenConverter/releases/latest"><img alt="最新版本" src="https://img.shields.io/github/v/release/Jasonzhu1207/ZenConverter?display_name=tag&sort=semver&color=0A7E8C"></a>
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4">
  <img alt="No ads" src="https://img.shields.io/badge/ads-none-16A34A">
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-AGPL--3.0--or--later-2563EB"></a>
</p>

<p align="center">
  <img src="docs/assets/zenconverter-cover.png" alt="ZenConverter app icon" width="240">
</p>

ZenConverter 是一个 Android 本地文件转换器。选择手机里的文件，在手机上完成转换，不把文件上传到别人的服务器。

项目使用原生 Kotlin 和 Jetpack Compose。文件访问走 Android Storage Access Framework，耗时任务在前台服务中运行。项目仍处于早期阶段，所以不会假装自己是万能转换器：格式会一项一项接入，限制也会直接写清楚。

**注意：** 运行内存较小的旧设备处理大文件时可能崩溃。即使是新设备，超大文件也仍建议谨慎测试。

## 为什么做它

桌面端已经有很多优秀的开源转换工具，但 Android 端体验仍然粗糙。很多转换类 App 广告多、收费混乱、界面臃肿，或者默认要求把文件上传到云端。

ZenConverter 想做的是一个本地优先的 Android 转换器：

- 转换过程不需要网络传输，
- 没有广告、账号、付费墙或远程上传，
- 仅申请 `INTERNET` 权限用于手动检查应用更新，
- 不申请不必要的权限，
- 大视频被当作真实使用场景处理，即使这条路仍然需要继续打磨，
- 支持范围公开写在 [support matrix](formats/support-matrix.md) 中。

## 当前状态

已完成的功能放在上面，实验性功能放在中间，计划中的功能放在最后。

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| 原生 Android 外壳 | 已完成 | Kotlin、Compose、Material 3、前台服务任务管线。 |
| 空转换任务流 | 已完成 | 文件选择、任务状态、进度、取消和失败状态。 |
| MP4 转 MP4 | 已完成 | 已接入 FFmpeg 真重新编码路径，确保可见的视频/音频选项和高级滤镜一致生效；大文件仍建议谨慎测试。 |
| MP4 转 MP3 | 已完成 | FFmpeg 兼容路径已接入，可提取第一条音轨并编码 MP3，已通过当前真机测试。 |
| 音频格式互转 | 已完成 | MP3 / M4A / WAV / FLAC / WMA 目标已接入并通过当前测试；边缘文件仍受内置 FFmpeg 构建和源文件情况影响。 |
| JPG / PNG / WEBP 图片互转 | 已完成 | 使用 Android 原生 bitmap 路径。仅处理静态图片，不复制元数据。 |
| MKV / MOV / WEBM / AVI 等容器转 MP4 / MOV | 实验性 | FFmpeg 兼容路径，按所选视频选项重新编码。 |
| 图片与 PDF 互转 | 实验性 | 图片转 PDF 使用 Android `PdfDocument`，PDF 转图片使用 Android `PdfRenderer`。按图片/页面逐个处理，并限制 bitmap 尺寸。PDF 转图片是页面栅格化，不是 OCR、文本提取或嵌入图片提取。 |
| DOCX / PPTX / XLSX 转 PDF | 实验性 | 本地 Office 转 PDF 路径，面向现代 Office 文件。中文文本可以通过内置 CJK 字体渲染，但版式保真有限：格式可能混乱，文字或形状可能错位、重叠。 |
| 更多视频格式支持 | 计划中 | 等当前路径更可信后再继续扩展。 |

## 架构

```mermaid
flowchart LR
    Pick["选择文件"]
    Preset["选择预设"]
    Queue["任务队列"]
    Service["前台服务"]
    Engine["FFmpeg / Native / Office"]
    Output["保存输出"]

    Pick --> Preset --> Queue --> Service --> Engine --> Output
```

UI 不直接做转换。每个任务会根据输入、输出和所选模式选择引擎：

- `FastCopy`：尽量不重编码，只做封装转换或提取。
- `Compatibility`：用 FFmpeg 处理已接入的音视频目标，以及 Android API 做不了的格式和操作。
- `Native`：用 Android 平台 API 处理图片、PDF 等不需要媒体引擎的任务。
- `SafeCache`：后续用于处理无法提供可用文件描述符的文件来源。

更多细节见 [docs/architecture.md](docs/architecture.md) 和
[docs/technical-route.md](docs/technical-route.md)。

开发环境说明见 [docs/development-setup.md](docs/development-setup.md)。

## 许可证

ZenConverter 自有源码基于
[GNU Affero General Public License v3.0 or later](LICENSE) 发布。

第三方库、原生二进制文件和内置字体仍保留各自许可证。详细记录见
[docs/license-and-attribution.md](docs/license-and-attribution.md) 和
[third_party/THANKS.md](third_party/THANKS.md)。

## 鸣谢

- [OhMyGPT](https://www.ohmygpt.com/) 提供 AI API 支持。
- [**ForZTN**](https://sponsorship.forztn.com/github/Jasonzhu1207/ZenConverter) 提供内核编译服务器支持。

## Star History

<a href="https://www.star-history.com/?repos=Jasonzhu1207%2FZenConverter&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=Jasonzhu1207/ZenConverter&type=date&theme=dark&legend=top-left&sealed_token=GKYtAachk5lOjo5_QTPLRheqRQbTo7ghEf74sSUtxDuyIVl84AIZeuMD5HD9SmJHlHYCAZRMXZAJcEgItcdaSiIPJfGjesVzujSGLqF0mxMwuXo7IbqRJNH1av_2KxhQ9d9xJXbmWoQ2cOQpDTOHmxIKs-N8wWa3aehBGBUd8jBNnJbvRKCo-RcAuEhO" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=Jasonzhu1207/ZenConverter&type=date&legend=top-left&sealed_token=GKYtAachk5lOjo5_QTPLRheqRQbTo7ghEf74sSUtxDuyIVl84AIZeuMD5HD9SmJHlHYCAZRMXZAJcEgItcdaSiIPJfGjesVzujSGLqF0mxMwuXo7IbqRJNH1av_2KxhQ9d9xJXbmWoQ2cOQpDTOHmxIKs-N8wWa3aehBGBUd8jBNnJbvRKCo-RcAuEhO" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=Jasonzhu1207/ZenConverter&type=date&legend=top-left&sealed_token=GKYtAachk5lOjo5_QTPLRheqRQbTo7ghEf74sSUtxDuyIVl84AIZeuMD5HD9SmJHlHYCAZRMXZAJcEgItcdaSiIPJfGjesVzujSGLqF0mxMwuXo7IbqRJNH1av_2KxhQ9d9xJXbmWoQ2cOQpDTOHmxIKs-N8wWa3aehBGBUd8jBNnJbvRKCo-RcAuEhO" />
 </picture>
</a>
