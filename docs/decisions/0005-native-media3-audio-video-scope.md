# 0005 Native Media3 Audio Video Scope

## Status

Superseded by [0015 Retire Media3 Transformer Path](0015-retire-media3-transformer-path.md)
for active media export behavior.

Follow-up: Decision 0006 adds the first FFmpeg compatibility path after MKV
testing hit the native Media3 muxing boundary.

## Context

The app is between the first real Media3 MP4 export path and the future FFmpeg
compatibility path. The current priority is to use the native Android and
AndroidX Media3 stack as far as it can reasonably go before adding FFmpeg
binaries, licensing work, and native distribution complexity.

Media3 Transformer defaults to MP4 output when no custom muxer is configured.
Its input support follows Media3/ExoPlayer extraction and platform decoder
support, and its output support depends on platform encoders and muxer support.

## Decision

Keep native media targets narrow:

- Video inputs are selected through `video/*` and exposed with MP4 as the only
  target.
- Audio inputs are selected through `audio/*` plus `video/*` for audio
  extraction, and exposed with M4A (AAC) as the only target.
- Video output defaults to H.264 MP4 for compatibility. HEVC remains optional
  when the device reports encoder support.
- Audio output uses AAC in an M4A container. Bitrate, sample rate, and channel
  controls are best-effort native settings; defaults preserve the source where
  Media3 and the platform can do so.
- FFmpeg compatibility remains planned, not part of this milestone.

## Consequences

- The UI no longer advertises video targets like MKV, WEBM, or GIF, or audio
  targets like MP3, WAV, FLAC, or OGG.
- MKV, WEBM, 3GP, TS, MP3, FLAC, WAV, and OGG are experimental inputs, not
  universal support promises.
- Follow-up: decision 0006 moves non-MP4 video containers and video-file audio
  extraction onto the first FFmpeg compatibility path. Audio-only inputs remain
  on the native Media3 path for now.
- Files with unsupported codecs, DRM, unusual timestamps, or device-specific
  codec failures must fail clearly.
- The native Media3 muxer-sample watchdog is relaxed from the default physical
  device timeout to 60 seconds so slow or timestamp-heavy inputs get a fair
  native attempt without allowing indefinite hangs.
- No new dependency or attribution update is needed for this decision.
