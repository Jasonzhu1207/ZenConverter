# 0004 Default Output and Media3 Presets

## Status

Accepted.

## Context

The first real conversion path exports MP4 through AndroidX Media3 Transformer.
The initial UI exposed quality, resolution, bitrate, and codec controls, but
those values were not connected to the export request. The app also required a
user-selected SAF output folder before conversion.

## Decision

Use a small set of Media3-backed video options for the first MP4 path:

- H.264 video inside MP4 by default for the safest native hardware path.
- H.265/HEVC video inside MP4 remains a device-gated optional codec when the
  current device reports a usable HEVC hardware encoder.
- Optional short-side scaling to 2160p, 1440p, 1080p, 720p, or 480p. The
  default is original size, and the scaling path does not upscale smaller
  source videos.
- User-facing bitrate labels: auto, low, medium, high, very high, and ultra.
  Auto lets Media3 and the platform encoder preserve or choose a bitrate where
  possible. Explicit bitrate labels request concrete target bitrates.
- Optional frame-rate caps to roughly 25, 30, or 60 fps through Media3
  `FrameDropEffect`. This is a lowering cap only; it does not synthesize extra
  frames for low-frame-rate sources.

Default output writes videos to `Movies/ZenConverter` through MediaStore on
Android 10 and newer. On Android 9 and older, default public output requires
`WRITE_EXTERNAL_STORAGE` with `maxSdkVersion=28`. Users can still choose a
custom SAF folder instead.

## Consequences

- The first MP4 export path can preserve compatible input tracks where Media3
  allows it, and it transcodes when the chosen options or output compatibility
  require it.
- HEVC is device-gated; VP9 is not exposed as a target codec until device and
  container compatibility are validated.
- Frame-rate controls are caps, not exact encoder frame-rate settings.
- Newer Android versions do not need broad file permissions for default output.
- Older Android versions either need legacy write permission or a user-selected
  SAF folder.
