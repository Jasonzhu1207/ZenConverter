# 0007 Self-Built FFmpegKitNext Android Core

## Status

Accepted.

## Context

The first FFmpeg compatibility path used a prebuilt AAR from
`ffmpegkit-maintained/ffmpeg`. That removed early integration risk, but it left
two problems:

- the Free AAR did not include `libmp3lame`, so MP3 output failed with
  `Unknown encoder 'libmp3lame'`;
- the binary came from an unofficial low-signal fork, which is not acceptable as
  a default trust anchor for a local-first converter.

The original `arthenica/ffmpeg-kit` project is archived. The
`arthenica/ffmpeg-kit-next` repository provides the current Nix-based Android
build scripts and is a better source base for a recorded self-build.

## Decision

Build and ship a local FFmpegKitNext AAR from source instead of resolving a
third-party FFmpegKit Maven coordinate.

Recorded source and build:

- Source repo: `https://github.com/arthenica/ffmpeg-kit-next`.
- Tag: `v7.1.0`.
- Commit: `1e64a8cdda1b045b014c0a54e9d395929c7b6ccc`.
- Replacement inspected on July 17, 2026. The exact replacement build host,
  command, package flags, and source revision must be recorded before a tagged
  release.
- Output AAR:
  `app/libs/ffmpeg-kit-next-7.1.0.aar`.
- SHA-256:
  `d1f2512e806ac3ff99b2f4c3d2e36fcca8c5c0eec548d84da81cf94d054cf406`.
- Build targets: `arm64-v8a`.
- Android ABIs: `arm64-v8a`.
- FFmpegKit package namespace remains `com.arthenica.ffmpegkit`.
- Replacement note: the July 17, 2026 AAR was inspected locally and contains
  `classes.jar`, only `jni/arm64-v8a` native libraries, and packaged notices
  for LAME, libiconv, libvpx, Opus, x264, x265, and cpu-features. Its exact
  rebuild command still needs to be recorded before a tagged release.

Gradle must fail if the recorded AAR is missing. The release workflow must not
download `ffmpegkit-maintained/ffmpeg` assets as a fallback.

## Consequences

- MP3 export can move from "package missing encoder" to physical-device sample
  verification.
- Clean checkouts include the recorded AAR, and release CI verifies its SHA-256
  before Gradle builds.
- The current AAR includes `arm64-v8a` only; 32-bit ARM and emulator/x86
  support remain out of scope.
- The AAR packages LGPLv3 plus GPL-family and codec license texts including
  LAME, libiconv, libvpx, Opus, x264, x265, and Apache-2.0 cpu-features. This
  is compatible with the project's `AGPL-3.0-or-later` license, but must be
  revisited before any non-GPL-family distribution.
- Broader codec support should be added by another recorded self-build, not by
  silently swapping in a prebuilt fork artifact.
