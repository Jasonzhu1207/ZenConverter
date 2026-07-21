# 0006 First FFmpeg Compatibility Path

## Status

Superseded by [0007 Self-Built FFmpegKitNext Android Core](0007-self-built-ffmpegkit-next.md).

This record remains as the historical first integration decision. The active
FFmpeg binary dependency is now the self-built FFmpegKitNext AAR recorded in
ADR 0007, not the `ffmpegkit-maintained/ffmpeg` prebuilt Free AAR.
Decision 0011 later replaces the current FFmpeg video output behavior with true
video/audio re-encoding; the remux notes below describe only this first
compatibility milestone.
Decision 0015 later retires the Media3 Transformer path entirely, so any notes
below about Media3 fallback or M4A routing are historical only.

## Context

Physical-device testing on July 5, 2026 showed the native Media3 path timing out
on MKV input with `ERROR_CODE_MUXING_TIMEOUT` after 60 seconds without an output
sample. The same service, progress, and save pipeline completed MP4 to M4A, so
the next smallest step is a compatibility engine rather than more Media3 tuning.

The original high-star `arthenica/ffmpeg-kit` project is archived and remains
reference-only. The maintained Android-only fork at
`ffmpegkit-maintained/ffmpeg` is active as of July 2026, LGPL-3.0 licensed, and
publishes Maven Central AARs for Free tier builds.

On this development machine, Gradle receives `403 Forbidden` from Maven Central
for the selected FFmpegKit coordinate. The same release also publishes an
official GitHub AAR asset, so the local build can prefer that file while keeping
the Maven coordinate as the fallback for environments where Central works.

## Decision

Add one FFmpeg dependency:

- Gradle coordinate: `dev.ffmpegkit-maintained:ffmpeg-kit-free-71:7.1.5`
- Local fallback: `app/libs/ffmpeg-kit-free71-7.1.5-arm64-v8a.aar`
- Tier: 7.1 LTS Free
- License: LGPL-3.0
- ABI: `arm64-v8a` only
- Release asset: `ffmpeg-kit-free71-7.1.5-arm64-v8a.aar`
- Release asset SHA-256:
  `78b57215ca8790264cf48ea755ca4629ccebe79660d37ab14f41d8077e9dece7`
- Android baseline recorded by upstream: minSdk 24, compileSdk/targetSdk 35
- Upstream workflow: `.github/workflows/build-71-free.yml`
- Free-tier build flags recorded from the upstream workflow:
  `--enable-libaom --enable-dav1d --enable-libvpx --enable-opus
  --enable-libvorbis --enable-speex --disable-arm-v7a
  --disable-arm-v7a-neon --disable-x86 --disable-x86-64`

Wire the first compatibility operations:

- Non-MP4 video containers selected for MP4 output use FFmpeg stream-copy remux:
  `-map 0:v:0 -map 0:a:0? -sn -dn -c copy -f mp4`.
- MP4 video files selected for M4A output stay on the Media3 native path.
- Non-MP4 video files selected for M4A output use FFmpeg audio-track copy:
  `-map 0:a:0 -vn -c:a copy -f ipod`. This succeeds only when the source audio
  stream can be written to M4A, usually AAC in this first Free-tier path.
- Input SAF URIs are passed through `FFmpegKitConfig.getSafParameterForRead`
  when possible, with `/proc/self/fd/{fd}` retained as a fallback while the
  descriptor remains open.
- FFmpeg output is captured through FFmpegKit log callbacks and progress is
  updated through FFmpegKit statistics plus log time parsing.
- Cancellation calls `FFmpegKit.cancel(sessionId)` and still updates the
  existing foreground-service task state.
- FFmpegKit initializes lazily when a compatibility job actually starts, not
  when the foreground service is created.
- FFmpeg native libraries use Gradle `jniLibs.useLegacyPackaging = true`, so
  they are extracted on install instead of loaded directly from `base.apk`.

## Consequences

- MKV/WebM/3GP/TS to MP4 is no longer forced through Media3.
- The first FFmpeg video path is remux-only; it cannot make every codec valid in
  MP4. Incompatible codecs should fail with a compatibility-engine message.
- M4A extraction from MP4 containers uses Media3. Non-MP4 extraction no longer
  depends on Media3 reading the container, but the first Free-tier path can only
  copy a compatible source audio stream into M4A. WebM Vorbis/Opus and AVI
  MP3/PCM need a later AAC-capable compatibility build.
- MP3 output is not available with the default Free-tier AAR. Physical-device
  logs on July 11, 2026 showed `Unknown encoder 'libmp3lame'`; MP3 export now
  probes for that encoder and requires an MP3-capable FFmpegKit tier or a later
  self-built LGPL FFmpeg package.
- The Free tier does not include Android `MediaCodec` hardware acceleration and
  does not add H.264/H.265/AAC encoders. If future work needs broad H.264/AAC
  transcoding, choose an LGPL Basic/Full tier or a self-built LGPL FFmpeg AAR
  deliberately and record the exact flags again.
- Because the AAR is arm64-only, emulator/x86 support is not a goal for this
  milestone.
- The old local AAR/JAR policy has been superseded by ADR 0007. The vetted
  FFmpegKitNext AAR is tracked as a release input; optional helper JAR caches
  remain ignored and can resolve from Maven Central.
- Extracted native libraries use more installed storage, but avoid device
  linker failures seen as RELRO `Out of memory` while loading FFmpegKit from
  `base.apk!/lib/...`.
- If FFmpegKit still cannot start on a device, the active task should fail with
  a compatibility-engine message instead of crashing the process.
