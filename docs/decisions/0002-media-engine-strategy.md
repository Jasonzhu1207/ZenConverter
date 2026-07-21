# Decision 0002: Media Engine Strategy

## Status

Superseded by [0015 Retire Media3 Transformer Path](0015-retire-media3-transformer-path.md)
for current implementation.

## Context

FFmpegKit used to be the easy Android FFmpeg path, but the original project is
retired. Relying on it as the main foundation would create a supply-chain and
maintenance risk.

## Decision

Use a two-engine media strategy:

- Media3 Transformer for maintained, hardware-accelerated Android video work.
- FFmpeg-compatible engine for broad format compatibility, selected only after
  license and maintenance checks.

## Consequences

- Common large-video tasks can use system codecs.
- Strange formats still have a future path.
- The project must maintain a clear engine registry and user-facing mode labels.
