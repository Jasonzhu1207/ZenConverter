# 0012 FFmpeg Audio Re-Encode Path

## Status

Accepted.

## Context

The earlier audio path mixed Media3 AAC output, FFmpeg audio targets, and a
temporary FFmpeg stream-copy route for non-MP4 video audio extraction to M4A.
That proved the UI flow, but it left two problems:

- M4A success depended on whether the source audio stream was already
  container-compatible.
- Audio bitrate, sample-rate, and channel options were not consistently applied
  across all audio targets and source containers.

The current checked-in FFmpegKitNext replacement AAR includes the encoders
needed for a smaller, clearer audio scope.

## Decision

Audio-lane tasks now use the FFmpeg compatibility path for every connected
audio target.

- MP3 uses `libmp3lame`.
- M4A uses AAC encoding in an M4A/iPod container.
- WAV uses `pcm_s16le`.
- FLAC uses `flac`.
- WMA uses `wmav2` in an ASF/WMA container.
- Video files selected in the Audio lane map only the first audio stream and
  encode the selected target.
- Audio bitrate is passed for MP3, M4A, and WMA when selected.
- Sample-rate and channel-count options are passed for all connected audio
  targets when selected.
- WAV and FLAC intentionally ignore bitrate options because the targets are
  lossless/PCM style outputs.

## Consequences

- M4A extraction no longer depends on the source already containing AAC.
- Audio outputs are slower than stream-copy routes, but the output options are
  real and predictable.
- Same-format audio conversions are still re-encodes, not byte-identical copies.
- Only the first audio stream is used; extra audio streams, video, subtitles,
  attachments, and metadata are not copied.
- Physical-device sample coverage is still required before promoting these
  rows beyond Experimental.
