# 0022 MP3 VBR Quality Mode

## Status

Accepted.

## Context

MP3 conversion previously exposed only fixed bitrate choices and passed them
to `libmp3lame` as `-b:a`. MP3 VBR is quality-based, so treating it as another
fixed bitrate would either produce misleading UI or pass conflicting FFmpeg
options.

## Decision

MP3 exposes a separate CBR/VBR encoding mode. CBR keeps the existing Auto,
96/128/192/256 kbps choices. VBR exposes LAME quality values V0, V2, V4, and
V6, with V2 as the default recommendation.

When MP3 VBR is selected, the compatibility engine passes only `-q:a` to
`libmp3lame` and omits `-b:a`. Sample rate, channel count, trimming, and audio
filters remain independent options. The mode is ignored for AAC, WMA, Opus,
and video-output audio tracks, which retain their existing bitrate behavior.

## Consequences

VBR output has no fixed target bitrate and its average bitrate depends on the
source audio. File size is therefore not guaranteed to decrease. No new
dependency or FFmpeg binary is required.
