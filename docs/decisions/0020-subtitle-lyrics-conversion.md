# 0020 Subtitle / Lyrics Conversion (SRT · VTT · LRC · ASS)

## Status

Accepted.

## Context

Users want to convert between common timed-text lyrics and subtitle formats.
The requested set is SRT, VTT, LRC, and ASS, with TXT deliberately excluded
(neither input nor output). The compatibility path is already powered by the
self-built FFmpegKitNext AAR, which natively demuxes/muxes SRT (SubRip),
WebVTT, and ASS, but has no LRC support at all.

## Decision

Add a dedicated subtitle conversion lane with a hybrid engine split:

- **FFmpeg** handles SRT/VTT/ASS in any direction. Because inputs arrive through
  SAF or `/proc/self/fd/N` (no usable file extension), the demuxer, subtitle
  codec, and muxer are passed explicitly (`-f <demuxer> -i <path> -map 0:s:0
  -c:s <codec> -f <muxer>`).
- **Pure Kotlin** handles LRC, since FFmpeg has no LRC demuxer/muxer. SRT is the
  single interchange format: LRC is parsed into a cue model and serialized to
  SRT (and vice versa), so LRC is never fed to FFmpeg directly.

Routing:

| Input → Output | Mechanism |
| --- | --- |
| SRT ↔ VTT, SRT ↔ ASS, VTT ↔ ASS | FFmpeg direct |
| SRT → LRC | Kotlin (SRT parse → LRC write) |
| VTT/ASS → LRC | FFmpeg → temp SRT → Kotlin |
| LRC → SRT | Kotlin (LRC parse → SRT write) |
| LRC → VTT/ASS | Kotlin → temp SRT → FFmpeg |

Same-format conversion is not offered (and is defensively rejected). The FFmpeg
build is probed at runtime for the required demuxers/muxers/codecs, mirroring
the existing encoder/filter probing, and fails with a clear message if a token
is missing rather than assuming the AAR includes it.

Text decoding is UTF-8 first (BOM stripped), falling back to GB18030 when
invalid UTF-8 is detected, to cover GBK-encoded Chinese lyrics. Inputs are read
whole with an 8 MiB cap.

## Consequences

- Twelve directed conversions become available (4 formats × 3 targets), gated
  behind a new `ConversionMediaCategory.Subtitle` and `FileCategory.Subtitle`.
- The new `org.zenconverter.app.subtitle` package (model, SRT codec, LRC codec,
  text decoder) is pure Kotlin with no new third-party dependency; attribution
  files are unchanged.
- Styling fidelity is best-effort and documented: ASS styles/positioning,
  WebVTT cue settings/inline markup, and rich formatting are dropped when
  converting to SRT/LRC. LRC has no end time, so SRT output synthesizes end
  times from the next cue start (2-second default for the final cue).
- Non-UTF-8 SRT/VTT/ASS inputs are still assumed to be UTF-8 by FFmpeg; only the
  Kotlin-parsed LRC path gains the GB18030 fallback.
- Outputs are written to the Documents collection with format-specific MIME
  types (`application/x-subrip`, `text/vtt`, `text/x-ssa`, `text/plain` for
  LRC). Same-format and TXT targets are excluded in the UI.
