# 0029 Hardware Video Encoding Availability

## Status

Accepted.

## Context

The video export path can ask for the platform encoder. `VideoEncoderSelection`
maps a codec to a hardware encoder name (`h264_mediacodec`, `hevc_mediacodec`)
and falls back to the software encoder (`libx264`, `libx265`) when the binary
does not report the hardware one. The preference
`AppPreferences.useHardwareEncoding` gates whether a job may request it.

Until now that preference had no user-facing control and defaulted to off, so
the hardware path was reachable only in code. An audit of the shipped AAR was
needed to state plainly whether the hardware path can ever run.

## Investigation

The shipped `app/libs/ffmpeg-kit-next-7.1.0.aar` was unpacked and its
`jni/arm64-v8a/libavcodec.so` inspected.

- The FFmpeg configure string embedded in the binary contains
  `--disable-mediacodec`.
- `h264_mediacodec`, `hevc_mediacodec`, and the bare `mediacodec` strings are
  absent from `libavcodec.so`.
- The software encoders are present: `libx264` and `libx265`.

So the runtime probe `ffmpegEncoderAvailable("h264_mediacodec")` correctly
returns false on every device, and every video export uses the software encoder
regardless of the preference.

## Decision

State the capability honestly rather than imply hardware encoding exists.

- Keep `VideoEncoderSelection` and the runtime probe as the single source of
  truth; they already fall back safely.
- Expose `useHardwareEncoding` in Settings under a new Performance group, with a
  subtitle that says the platform encoder is used only when the build provides
  one and that off keeps output identical across devices. This makes the switch
  visible and truthful instead of dormant.
- Do not rebuild or replace the AAR here. The current AAR is a recorded
  self-build (ADR 0007) with no NDK or Nix toolchain in this environment, and
  ADR 0007 forbids silently swapping in a prebuilt artifact.

## Consequences

- The preference now drives the pipeline from a visible control, but on the
  shipped AAR it still resolves to the software encoder. The switch becomes
  effective the day a build with `--enable-mediacodec` ships.
- A real hardware path needs one of:
  - a new recorded FFmpeg self-build with `--enable-mediacodec` (and a
    compatible `--enable-jni`), or
  - a separate MediaCodec + MediaMuxer pipeline for the video export, which is
    a larger change to the export path and its tests.
- Until then, keep the wording free of any claim that hardware encoding is
  active.
