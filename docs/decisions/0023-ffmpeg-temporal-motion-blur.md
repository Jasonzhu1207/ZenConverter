# 0023 FFmpeg Temporal Motion Blur

## Status

Accepted.

## Context

In video post-production and screen recording playback, motion blur simulates physical
camera shutter exposure, smoothing rapid motion and reducing strobe/flickering. Mobile
screen recordings (such as 60fps/120fps high refresh rate gaming clips), action camera footage,
and drone shots are often captured with high shutter speeds, creating harsh inter-frame
jumps and artificial sharpness without natural motion streaks.

We evaluated candidate FFmpeg filters within the checked-in `arm64-v8a` FFmpeg library:
- `tmix` (temporal multi-frame weighted averaging) is compiled and efficient, operating
  as an uncompressed in-memory temporal sliding window;
- `dblur` (directional blur) is a 2D spatial static convolution without motion awareness
  or ARM64 NEON optimization;
- `lagfun` (color trail / ghosting) is designed for artistic trails rather than shutter simulation.

Therefore, `tmix` was chosen as the core engine for temporal motion blur.

## Decision

Add a temporal motion blur option to the FFmpeg compatibility path for MP4, MKV, and MOV
video outputs:

- Define `VideoMotionBlurMode` with four modes:
  - `Off`: default, no temporal blending;
  - `Subtle (3 frames)`: `tmix=frames=3:weights='1 2 1'`, suitable for 30fps or subtle movement;
  - `Standard (5 frames)`: `tmix=frames=5:weights='1 2 4 2 1'`, Gaussian bell curve weights, recommended for 60fps+ inputs;
  - `Heavy (7 frames)`: `tmix=frames=7:weights='1 1 2 4 2 1 1'`, strong streak effect for high-speed action.
- Position `motionBlur` in the FFmpeg filter chain after resolution scaling and reverse playback,
  but before fade-in/fade-out: `rotation` -> `mirror` -> `aspectRatio` -> `scale` -> `reverse` -> `motionBlur` -> `fadeIn` -> `fadeOut`.
- Require `"tmix"` during runtime FFmpeg filter validation and map user-facing diagnostics if missing.
- Maintain mutual exclusion: while video compression presets or video frame interpolation modes are active,
  advanced video options (including motion blur) remain locked/reset.
- Wire localization across all four supported languages (`en`, `zh-Hans`, `zh-Hant`, `fr`).

## Consequences

- Zero APK size increase: leverages the prebuilt `tmix` filter already inside `ffmpeg-kit-next-7.1.0.aar`.
- Zero disk I/O and minimal memory overhead: buffers only 3 to 7 frames in memory during streaming encoding.
- Audio/video lip synchronization is preserved: the initial 3–7 frame buffering offset (33ms–116ms) remains within standard presentation offset tolerance and container edit lists.
- Users are honestly informed via UI labels of frame mixing numbers (3 / 5 / 7 frames), avoiding misconceptions regarding ghosting artifacts on low frame-rate inputs.
- GIF output does not expose this advanced filter panel.
- Phase 2 supersampled optical-flow motion blur (interpolating up to 120fps before temporal downsampling) remains reserved for future milestones.
