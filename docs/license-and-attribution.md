# License And Attribution

## Project License Decision

Proposed app license: `GPL-3.0-or-later`.

Reason: the project is likely to ship or optionally support FFmpeg builds with
GPL-enabled codecs. GPLv3 keeps the legal story simpler for an open-source
converter whose trust comes from inspectable source code.

If the project later commits to an LGPL-only FFmpeg build and avoids GPL media
components, this decision can be revisited before a public release.

## Dependency Intake Checklist

Before a dependency becomes core:

- Confirm the package is actively maintained.
- Confirm license compatibility with `GPL-3.0-or-later`.
- Record homepage, source URL, license, and reason for use.
- Prefer official AndroidX APIs for platform features.
- Avoid abandoned wrappers as core infrastructure.

## Current AndroidX Dependencies

- AndroidX Compose and AndroidX Material Icons Extended are Apache-2.0
  dependencies used for the native Android UI.
- AndroidX Media3 Transformer `1.10.1` is an Apache-2.0 dependency used for the
  first real MP4 export path.

## Current FFmpeg Dependency

- Dependency: Gradle requires the local self-built AAR
  `app/libs/ffmpeg-kit-next-7.1.0-lame-arm64-v8a.aar`.
- No Maven fallback is configured for FFmpegKit. In particular, release builds
  must not download binaries from `ffmpegkit-maintained/ffmpeg`.
- Package type: Android AAR.
- Upstream source: `https://github.com/arthenica/ffmpeg-kit-next`.
- Upstream tag: `v7.1.0`.
- Source commit: `1e64a8cdda1b045b014c0a54e9d395929c7b6ccc`.
- License files packaged in the AAR:
  - FFmpegKitNext main license: GNU Lesser General Public License v3.0.
  - LAME license file: GNU Library General Public License v2.
  - libiconv license file packaged by the build: GNU General Public License v3.0.
  - cpu-features license file: Apache License 2.0.
- Project compatibility: the app's proposed license is already
  `GPL-3.0-or-later`, so the packaged libiconv GPLv3 text is compatible with
  the current project direction. Revisit this before any non-GPL distribution.
- Reason platform APIs are not enough: physical-device logs on July 5, 2026
  showed Media3 timing out on MKV before writing any muxer sample, while the
  same service pipeline completed MP4/M4A work. Physical-device logs on
  July 11, 2026 showed the earlier Free FFmpegKit AAR lacked `libmp3lame`.
- Maintenance check: the original `arthenica/ffmpeg-kit` repo is archived, but
  `arthenica/ffmpeg-kit-next` is the upstream successor used as source for this
  self-built binary. Third-party prebuilt fork binaries are not trusted by
  default.

Build record for the selected binary:

- Build date: July 12, 2026.
- Build host: Ubuntu 24.04.4 LTS x86_64, 4 vCPU, 7.6 GiB RAM, 12 GiB swap.
- Build wrapper: `./nix-android.sh`.
- Nix profile: `android-r27d`.
- NDK from the flake: `27.3.13750724`.
- Android API level used by the build: 24.
- Build command:
  `./nix-android.sh -p android-r27d --jobs=2 --enable-lame
  --disable-arm-v7a --disable-arm-v7a-neon --disable-x86 --disable-x86-64`.
- Enabled external libraries reported by the build: `lame`, `libiconv`.
- ABI included in the AAR: `arm64-v8a` only.
- AAR file name: `ffmpeg-kit-next-7.1.0-lame-arm64-v8a.aar`.
- AAR size: `9,073,515` bytes.
- AAR SHA-256:
  `14fb12d5868b23b7e16a7f17b268364973f5acca059505a42ccdcb6cba1ac9b0`.
- Verification evidence:
  - AAR `classes.jar` contains `com/arthenica/ffmpegkit/FFmpegKit.class` and
    `FFmpegKitConfig.class`.
  - AAR contains only `jni/arm64-v8a` native libraries.
  - FFmpeg `config.h` contains `#define CONFIG_LIBMP3LAME 1`.
  - FFmpeg `config_components.h` contains
    `#define CONFIG_LIBMP3LAME_ENCODER 1`.
  - `libavcodec.so` contains the `libmp3lame` string.

Transitive dependencies required when consuming the local AAR through
`implementation(files(...))`:

- `com.arthenica:smart-exception-java:0.2.1`, BSD-3-Clause, local JAR SHA-256:
  `5b96aaa5f191dedbef72fb0c38f1a2b01807920afc0d92a75a2acd6e0cc7703c`.
- `com.arthenica:smart-exception-common:0.2.1`, BSD-3-Clause, local JAR
  SHA-256: `1cad0fb4dfa01755a014331b5ed199281d2c3fab5aca5c9d7abd0b41d0ec3f7b`.

## Commercial License Guardrail

The project may later be commercialized. Avoid GPL-only media packages unless
the user explicitly changes that direction. Future FFmpeg work should prefer an
LGPL-compatible build and must not use packages with a `-gpl` suffix by default.

## FFmpeg Policy

FFmpeg is powerful, but its exact license depends on build flags and linked
libraries. Do not ship an FFmpeg binary until the build configuration is written
down.

Required record for any FFmpeg binary:

- source repo,
- version or commit,
- configure flags,
- enabled external libraries,
- GPL or LGPL status,
- architectures included,
- binary size,
- attribution text.
