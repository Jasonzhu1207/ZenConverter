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

- Dependency: Gradle prefers local release files under `app/libs` when present,
  then falls back to `dev.ffmpegkit-maintained:ffmpeg-kit-free-71:7.1.5`.
  Local preference order is `ffmpeg-kit-basic71-7.1.5-arm64-v8a.aar`,
  `ffmpeg-kit-full71-7.1.5-arm64-v8a.aar`, then
  `ffmpeg-kit-free71-7.1.5-arm64-v8a.aar`.
- Package type: Android AAR.
- Upstream source: `https://github.com/ffmpegkit-maintained/ffmpeg`.
- Maven POM URL:
  `https://repo1.maven.org/maven2/dev/ffmpegkit-maintained/ffmpeg-kit-free-71/7.1.5/ffmpeg-kit-free-71-7.1.5.pom`.
- License recorded by the POM: GNU Lesser General Public License v3.0.
- Selected default tier: 7.1 LTS Free. Do not replace it with a `-gpl` artifact
  by default. MP3-capable local Basic/Full tier files are development inputs
  only until their source, license terms, and SHA-256 are recorded here.
- Release asset: `ffmpeg-kit-free71-7.1.5-arm64-v8a.aar`.
- Reason platform APIs are not enough: physical-device logs on July 5, 2026
  showed Media3 timing out on MKV before writing any muxer sample, while the
  same service pipeline completed MP4/M4A work.
- Maintenance check: the original `arthenica/ffmpeg-kit` repo is archived; the
  selected fork is not archived and publishes Android-only SDK 35 / 16 KB page
  aligned releases.
- Local fallback reason: on this development machine, Gradle receives
  `403 Forbidden` from Maven Central for the selected FFmpegKit coordinate.
  The checked build therefore uses the official GitHub Release AAR when present.

Build record for the selected binary:

- Upstream workflow:
  `https://github.com/ffmpegkit-maintained/ffmpeg/blob/main/.github/workflows/build-71-free.yml`.
- NDK line: r26c.
- Android SDK line: compileSdk/targetSdk 35, minSdk 24.
- ABI published by upstream CI: `arm64-v8a` only.
- Free-tier flags recorded from the workflow:
  `--enable-libaom --enable-dav1d --enable-libvpx --enable-opus
  --enable-libvorbis --enable-speex --disable-arm-v7a
  --disable-arm-v7a-neon --disable-x86 --disable-x86-64`.
- Upstream workflow verifies 16 KB page-size alignment before upload.
- Free-tier limitation: no Android `MediaCodec` hardware acceleration and no
  H.264/H.265/AAC encoders. Physical-device logs on July 11, 2026 also confirm
  that the local Free AAR lacks `libmp3lame`, so MP3 output requires an
  MP3-capable FFmpegKit tier or a later self-built LGPL FFmpeg package.
- Release AAR SHA-256:
  `78b57215ca8790264cf48ea755ca4629ccebe79660d37ab14f41d8077e9dece7`.

Transitive dependencies recorded from the POM:

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
