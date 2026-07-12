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
- Build host used on July 12, 2026: Ubuntu 24.04.4 LTS x86_64, 4 vCPU,
  7.6 GiB RAM, 12 GiB swap.
- Build wrapper: `./nix-android.sh`.
- Nix profile: `android-r27d`.
- Command:
  `./nix-android.sh -p android-r27d --jobs=2 --enable-lame
  --disable-x86 --disable-x86-64`.
- Output AAR:
  `app/libs/ffmpeg-kit-next-7.1.0-lame-armeabi-v7a-arm64-v8a.aar`.
- SHA-256:
  `6f3bb932ba76ff2627bef6cbfd77fa24bb7186afe27d88da37f69cd60c207602`.
- Build targets: `arm-v7a`, `arm-v7a-neon`, and `arm64-v8a`.
- Android ABIs: `armeabi-v7a` and `arm64-v8a`.
- FFmpegKit package namespace remains `com.arthenica.ffmpegkit`.
- MP3 evidence: `CONFIG_LIBMP3LAME=1` and
  `CONFIG_LIBMP3LAME_ENCODER=1` are present in the generated FFmpeg config for
  all three build targets.

Gradle must fail if the recorded AAR is missing. The release workflow must not
download `ffmpegkit-maintained/ffmpeg` assets as a fallback.

## Consequences

- MP3 export can move from "package missing encoder" to physical-device sample
  verification.
- Clean checkouts include the recorded AAR, and release CI verifies its SHA-256
  before Gradle builds.
- The current AAR includes ARM ABIs only; emulator/x86 support remains out of
  scope.
- The AAR packages LGPLv3, LAME LGPLv2-style, libiconv GPLv3, and Apache-2.0
  cpu-features license texts. This is compatible with the project's proposed
  `GPL-3.0-or-later` license, but must be revisited before any non-GPL
  distribution.
- Broader codec support should be added by another recorded self-build, not by
  silently swapping in a prebuilt fork artifact.
