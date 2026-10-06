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
- Global audio tags are copied with `-map_metadata 0`, and unchanged chapters
  with `-map_chapters 0`.
- MP3, M4A, and FLAC map an input `attached_pic` stream with `-c:v copy`.
  OPUS serializes the picture as the Ogg/Vorbis
  `METADATA_BLOCK_PICTURE` value because Ogg cannot carry a video stream. WAV
  and WMA keep tags where their muxers support them but do not claim portable
  cover art.

## Consequences

- M4A extraction no longer depends on the source already containing AAC.
- Audio outputs are slower than stream-copy routes, but the output options are
  real and predictable.
- Same-format audio conversions are still re-encodes, not byte-identical copies.
- Only the first audio stream is used; extra audio streams, video, subtitles,
  attachments, and timed lyric streams are not copied. Supported global tags,
  unchanged chapters, and attached pictures are copied according to the target
  container's capability. Common plain lyric tags are normalized to the
  `lyrics` key when present; trimmed/split outputs drop chapters whose original
  timestamps would no longer be correct.
- Existing physical-device coverage has verified the connected FFmpeg audio
  targets and video-source audio extraction. The native MP3/Opus/FLAC metadata
  repair path still requires the Android Studio arm64 acceptance matrix;
  automated sample coverage remains a future quality improvement.
