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
| 任务队列与结果 | 已完成 | 文件基础信息、逐任务进度和失败状态、转换前后摘要、取消、输出分享，以及尽力打开输出文件或所在位置。 |
| 视频转换 | 已完成 | MP4 / MKV / MOV 输出均走 FFmpeg 视频与音频真重新编码，包含 MP4 转 MP4；编码、码率、分辨率、帧率和音频选项会实际作用于输出。 |
| 视频转动图 GIF | 已完成 | 使用 FFmpeg 调色板路线，自动取前 30 秒，最多 30 fps、900 帧；默认短边 480 px，可选 720 px 或原始尺寸。 |
| 音频提取与互转 | 已完成 | 视频音频提取和 MP3 / M4A / WAV / FLAC / WMA 目标均走 FFmpeg 真重新编码；已接入适用的码率、采样率、声道和编码器检查。 |
| 音视频高级处理 | 实验性 | 视频支持短视频倒放、淡入淡出、镜像、旋转和画幅适配/裁剪；音频支持倒放、无模型 `afftdn` 降噪、淡入淡出、音量/静音和回音。倒放有保守的安全限制。 |
| 图片转换 | 已完成 | 支持 JPG / JPEG / JFIF / JPE、PNG、WEBP、GIF、HEIC / HEIF、ICO 输入，以及 JPG / JFIF / PNG / WEBP / ICO / PDF 输出。GIF 可转首帧或拆帧到文件夹；不复制元数据和动画时序。 |
| PDF 工具 | 实验性 | 图片/PDF 互转、PDF 合并、可选择文本导出 TXT / 轻量 MD，以及基于密码的 PDF 加密和解密。不包含 OCR 或密码破解。 |
| Office 转换 | 实验性 | DOCX / PPTX / XLSX 可在本地输出 PDF、TXT 或轻量 MD。中文可用内置 CJK 字体渲染，但版式保真有限，源文件上限为 64 MiB。 |
| ZIP 压缩包处理 | 计划中 | 等当前转换路径更可信后再继续扩展。 |

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

- `Compatibility`：已接入音视频目标、GIF 输出和高级处理均走 FFmpeg 真重新编码路线。
- `Native`：用 Android 平台 API 处理图片、PDF 等不需要媒体引擎的任务。
- `Office`：用本地初版 Office 渲染路线处理 DOCX、PPTX 和 XLSX。
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
