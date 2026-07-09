# ZenConverter

ZenConverter is a planned offline-first, open-source Android file converter.
The project goal is simple: files stay on the device, supported conversions are
listed honestly, and the app never depends on ads, dark-pattern subscriptions,
or remote upload.

Current status: project skeleton. The first milestone is a native Android
Hello World with a no-op conversion pipeline, followed by one real media
conversion path at a time.

## Direction

- Android first: Kotlin, Jetpack Compose, Material 3.
- Native performance first: Media3 Transformer for common hardware-accelerated
  video work, FFmpeg native compatibility engine later.
- Local files first: Android Storage Access Framework, file-descriptor paths
  where possible, cache fallback only when needed.
- Long-running jobs: foreground service with notification progress.
- Open source hygiene: every dependency must be recorded before it becomes core.

## Local Development

This machine uses the Android toolchain under `E:\AndroidDev`. From the project
root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\install-debug.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\launch-debug.ps1
```
