# Audio metadata and cover-art preservation

Status: Implemented in the compatibility path; Android Studio and arm64 physical-device acceptance pending.

## Context

The original audio command mapped the first audio stream and disabled video,
subtitle, and data streams. That avoided accidental video output, but it also
discarded attached pictures and relied on muxer-specific tag guesses. Two real
outputs exposed the boundary: an MP3 contained a JPEG APIC but stored lyrics as
`TXXX:USLT`, while an Opus file contained `LYRICS` but no
`METADATA_BLOCK_PICTURE`.

The audio stream must still be encoded exactly once with the user-selected
codec and options. Metadata work must never decode, filter, or re-encode audio.

## Decision

The conversion service now takes a bounded `AudioMetadataSnapshot` for MP3,
Opus, and FLAC targets. It collects common music fields, scans ordinary lyric
keys (`lyrics`, `LYRICS`, `UNSYNCEDLYRICS`, `USLT`, and `lyrics-*` variants),
and keeps one validated JPEG/PNG primary cover with its MIME type, dimensions,
and SHA-256. A source cover that is advertised by the probe but cannot be
extracted and validated fails the task with a localized diagnostic.

The normal FFmpeg command still performs the one requested audio encode. It
maps global and audio-stream metadata, copies only attached-picture streams for
containers that support that path, drops chapters for trim/split timelines, and
never maps a real video stream into an audio output. Snapshot fields and lyrics
are passed explicitly with bounded arguments; logs redact lyric and picture
payloads.

After encoding, the target is read back from its native container:

- MP3 is parsed without loading audio frames into memory. Existing malformed
  `TXXX` frames whose description is `USLT` are removed, a real UTF-16 ID3
  `USLT` frame is written with language and description, missing standard text
  frames are filled, and the new ID3 tag is followed by a byte-for-byte copy of
  the original MP3 audio bytes. APIC bytes are checked against the snapshot.
- Opus is written without an attached-picture video stream. If its first mux
  does not pass verification, one metadata-only FFmpeg remux uses `-c:a copy`
  and an RFC 7845 FLAC picture block in `OpusTags`. Ogg page lacing and
  continuation packets are parsed before accepting the result.
- FLAC is checked for Vorbis comments and native type-6 picture blocks. If the
  first mux is incomplete, one metadata-only `-c:a copy` remux is attempted;
  failure after that is reported instead of silently returning an incomplete
  file.

A required cover, lyric, or common field that cannot be read back causes the
conversion to fail. The service does not claim metadata preservation for M4A,
WAV, or WMA at this strength level.

## Format boundary

| Target | Native cover path | Guaranteed in this milestone |
| --- | --- | --- |
| MP3 | ID3v2 APIC | Common music fields, one JPEG/PNG cover, ordinary embedded lyrics with real `USLT`; MP3 frames are not re-encoded during repair |
| Opus | Ogg OpusTags `METADATA_BLOCK_PICTURE` | Common Vorbis comments, one JPEG/PNG cover, ordinary embedded lyrics; one metadata-only remux may run |
| FLAC | type-6 FLAC picture block plus Vorbis comments | Common Vorbis comments, one JPEG/PNG cover, ordinary embedded lyrics; one metadata-only remux may run |
| M4A | MP4 `covr` item | Existing best-effort FFmpeg behavior only |
| WAV | RIFF/WAVE tags vary by reader | Tags where the muxer exposes them; no portable cover guarantee |
| WMA | ASF/WM tags vary by reader | Tags where the muxer exposes them; no portable cover guarantee |

Multiple covers, complete cover-type ordering, private vendor tags, external
`.lrc` association, timed-lyrics playback semantics, and arbitrary attachments
are outside the first guarantee. Chapters remain intentionally dropped for
trim and split outputs because the source time axis no longer applies.

## Quality boundary

The metadata repair path does not alter audio samples. MP3 and Opus conversion
remain lossy because the selected target codec is lossy; this change prevents a
second lossy encode during metadata repair. FLAC-to-FLAC keeps decoded PCM
identical when the user does not change sample rate, channels, or audio filters.
Trimming, splitting, reversing, denoising, fades, echo, and other selected
filters still change audio by design.

## Verification

The pure Kotlin codec layer has byte-level readers for ID3v2.3/v2.4 synchsafe
sizes, UTF-16/UTF-8 text, Ogg page lacing and OpusTags, FLAC metadata headers,
Vorbis comments, and FLAC picture blocks. Local checks cover compilation of the
codec file, translation resources, format registry parsing, and whitespace. A
byte-level harness also repaired the supplied MP3's `TXXX:USLT` into `USLT`,
confirmed the JPEG cover SHA-256 was unchanged, and confirmed the MP3 audio
payload fingerprint was unchanged. Synthetic OpusTags and FLAC picture blocks
were read back successfully.
Android Studio Run/Debug on an arm64 physical device remains the acceptance
authority. The required matrix is FLAC/MP3/Opus in every direction, JPEG/PNG
and no-cover inputs, ordinary and `lyrics-*` lyrics, video-source extraction,
trim/split, cancellation, native container readback, and decoded audio
parameter/PCM checks before and after metadata-only repair.
