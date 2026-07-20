# 0011 FFmpeg Video Re-Encode Path

## Status

Accepted.

## Context

The first FFmpeg video compatibility path used stream-copy remux to prove the
new local FFmpegKitNext AAR, SAF input, temporary output, and public output flow.
That path validated the engine, but it left two product problems:

- container changes only worked when the source codecs already fit the target
  container;
- visible video options such as bitrate, codec, resolution, and frame-rate cap
  did not affect FFmpeg video outputs.

After physical-device testing confirmed that MP4 to MKV fast-copy output played,
the next step is to make advertised video conversion behavior real instead of
only changing containers.

## Decision

FFmpeg compatibility video outputs now re-encode instead of using `-c copy`.

- MP4 output uses the FFmpeg compatibility path for non-MP4 video inputs.
- MKV output uses the FFmpeg compatibility path for video inputs.
- MOV output uses the FFmpeg compatibility path for video inputs.
- The first video track is encoded with `libx264` or `libx265` according to the
  selected video codec option.
- The optional first audio track is encoded to AAC.
- Video bitrate, codec, short-side resolution cap, and max frame-rate options
  are mapped into FFmpeg arguments.
- Audio bitrate, sample-rate, and channel-count options are mapped into FFmpeg
  AAC arguments when they are present on the task.
- Auto bitrate uses CRF defaults: 23 for H.264 and 28 for H.265.
- Frame-rate uses `-fpsmax` so it caps high-FPS sources without forcing low-FPS
  sources upward.
- Subtitles, attachments, unknown streams, extra video tracks, and extra audio
  tracks are still not copied in this milestone.

## Consequences

- More source containers and codecs can produce playable MP4/MKV/MOV outputs.
- Conversions are slower and use more CPU/battery than remux.
- Output is generationally recompressed, not byte-identical.
- H.264/H.265/AAC encoder availability is checked through FFmpeg before export
  where possible, with clear failure messages when the bundled AAR is wrong.
- Exact FFmpegKitNext replacement build flags remain a release gate because the
  current AAR packages codec notices for LAME, libiconv, libvpx, Opus, x264, and
  x265.
