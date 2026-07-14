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

## Current PDF Dependency

- Dependency: local Maven Central AAR
  `app/libs/pdfbox-android-2.0.27.0.aar`.
- Coordinates: `com.tom-roush:pdfbox-android:2.0.27.0`.
- Package type: Android AAR.
- Upstream source: `https://github.com/TomRoush/PdfBox-Android`.
- License: Apache License 2.0, compatible with the current
  `GPL-3.0-or-later` project direction.
- Local file size: `3,254,019` bytes.
- Local SHA-256:
  `30277f879cfd571db2a137582c95516a0d4ea6778e945519bc58ca93d57d88c7`.
- Gradle consumes the AAR through `implementation(files(...))` so Android
  Studio builds do not depend on resolving this artifact from Maven during
  every sync/build.
- Reason platform APIs are not enough: Android has `PdfDocument` for simple PDF
  writing and `PdfRenderer` for rasterizing pages, but no broad platform API for
  true multi-PDF merge that preserves normal page objects. Platform text
  extraction is only available through newer Android/PDF extension APIs, so it
  is not suitable as the first all-supported-device PDF to TXT experiment.
- Maintenance check: the repo is not archived, but release cadence is low. The
  selected `2.0.27.0` release was published on January 2, 2023, and the latest
  observed upstream push was March 18, 2024. Treat this dependency as a focused
  PDF utility, not general document-conversion infrastructure.
- Scope limits: PDF merge is best-effort for complex forms, bookmarks,
  attachments, and metadata. PDF to TXT extracts selectable text only; it does
  not do OCR. PDFBox-Android's optional Gemalto JPEG2000 helper classes are not
  bundled because the current PDFBox scope does not include JPEG2000 image
  encode/decode.

Transitive dependencies required by the PDFBox-Android POM are also local JARs:

- `org.bouncycastle:bcprov-jdk15to18:1.72`, Bouncy Castle License, size
  `9,342,484` bytes, SHA-256
  `ea66ea8a450810b2193e8bf9a7ad3e46307c9896224c0f407d1b7d96ba1221cc`.
- `org.bouncycastle:bcpkix-jdk15to18:1.72`, Bouncy Castle License, size
  `1,022,720` bytes, SHA-256
  `d9b97477b72499bcee02f5a906510810257ff36a94bf69fbca0b1e65e7ffdb6e`.
- `org.bouncycastle:bcutil-jdk15to18:1.72`, Bouncy Castle License, size
  `677,620` bytes, SHA-256
  `d92184bdeb3105a11ad9e36acbd66b5f8eed091b08b9c8f3e2549e42b7f131f1`.
- Reason included: PDFBox-Android declares these libraries for PDF
  security/encryption support. They are consumed as local JARs because local AAR
  dependencies do not bring Maven transitive dependencies with them.

## Current FFmpeg Dependency

- Dependency: Gradle requires the local self-built AAR
  `app/libs/ffmpeg-kit-next-7.1.0-lame-armeabi-v7a-arm64-v8a.aar`.
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
  --disable-x86 --disable-x86-64`.
- Enabled external libraries reported by the build: `lame`, `libiconv`.
- Build targets included: `arm-v7a`, `arm-v7a-neon`, and `arm64-v8a`.
- Android ABIs included in the AAR: `armeabi-v7a` and `arm64-v8a`.
- Android ABIs packaged in the app: `arm64-v8a` only, via Gradle `abiFilters`.
  The `armeabi-v7a` libraries remain in the recorded AAR but are not shipped in
  the release APK.
- AAR file name: `ffmpeg-kit-next-7.1.0-lame-armeabi-v7a-arm64-v8a.aar`.
- AAR size: `26,384,826` bytes.
- AAR SHA-256:
  `6f3bb932ba76ff2627bef6cbfd77fa24bb7186afe27d88da37f69cd60c207602`.
- Verification evidence:
  - AAR `classes.jar` contains `com/arthenica/ffmpegkit/FFmpegKit.class` and
    `FFmpegKitConfig.class`.
  - AAR contains `jni/armeabi-v7a` and `jni/arm64-v8a` native libraries.
  - AAR contains `jni/armeabi-v7a/libffmpegkit_armv7a_neon.so` for the
    FFmpegKit ARMv7 NEON loader path.
  - FFmpeg `config.h` contains `#define CONFIG_LIBMP3LAME 1` for
    `android-arm-24`, `android-arm-neon-24`, and `android-arm64-24`.
  - FFmpeg `config_components.h` contains
    `#define CONFIG_LIBMP3LAME_ENCODER 1` for all three build targets.

Transitive dependencies required when consuming the local AAR through
`implementation(files(...))`:

- `com.arthenica:smart-exception-java:0.2.1`, BSD-3-Clause, local JAR SHA-256:
  `5b96aaa5f191dedbef72fb0c38f1a2b01807920afc0d92a75a2acd6e0cc7703c`.
- `com.arthenica:smart-exception-common:0.2.1`, BSD-3-Clause, local JAR
  SHA-256: `1cad0fb4dfa01755a014331b5ed199281d2c3fab5aca5c9d7abd0b41d0ec3f7b`.

## Current Office Document Native Binary

- Runtime path: local native library
  `app/src/main/jniLibs/arm64-v8a/libzen_office2pdf.so` for `arm64-v8a` only.
- Reproducible source: `native/office2pdf-jni`.
- Upstream source dependency: `developer0hye/office2pdf` commit
  `e9129b3558f7d758922a5530766d19545ebaa28c`, crate version `0.6.1`,
  Apache License 2.0. The local Apache text is at
  `third_party/licenses/office2pdf/Apache-2.0.txt`.
- JNI binding dependency: `jni` version `0.21.1`, Apache-2.0 OR MIT.
- JNI symbols consumed by the app:
  `Java_org_zenconverter_app_office_Office2PdfNative_convertBytesWithFontPaths`
  and `Java_org_zenconverter_app_office_Office2PdfNative_convertBytes`.
- Preferred native API: `convertBytesWithFontPaths`. It receives explicit font
  directories and assigns them to `office2pdf::config::ConvertOptions.font_paths`;
  this is required for Typst to search app-private CJK font files on Android.
- Current binary: rebuilt on July 14, 2026, `72,348,456` bytes with SHA-256
  `46779f04fc231fb1b1104ba766636e372a1be7cd49b71909346953e512a8e09c`.
  It exports both `convertBytesWithFontPaths` and the legacy `convertBytes`
  entry. The legacy entry is retained only as a compatibility fallback for
  older local test binaries; it cannot use the bundled CJK font directory.
- Reason platform APIs are not enough: Android platform APIs do not provide a
  DOCX/PPTX/XLSX to PDF renderer.
- Current scope: experimental local DOCX/PPTX/XLSX to PDF conversion. The
  Kotlin service reads each input into memory and caps the source file at
  64 MiB before calling the native library.
- Release guardrail: generate and record a full transitive Cargo dependency
  license inventory and broader sample results before raising this path beyond
  experimental.

## Bundled Office CJK Font

- Dependencies: Noto Sans CJK Regular and Noto Serif CJK Regular.
- Package paths: `app/src/main/assets/fonts/NotoSansCJK-Regular.ttc` and
  `app/src/main/assets/fonts/NotoSerifCJK-Regular.ttc`.
- Package type: TrueType Collection fonts copied at runtime to app-private
  storage and passed directly to the native font search API.
- Source snapshots: Android Studio layoutlib fonts at
  `E:\AndroidDev\android-studio\plugins\design-tools\resources\layoutlib\data\fonts\NotoSansCJK-Regular.ttc` and
  `E:\AndroidDev\android-studio\plugins\design-tools\resources\layoutlib\data\fonts\NotoSerifCJK-Regular.ttc`.
- Upstream project: Noto CJK / notofonts.
- Maintenance status: active upstream font family.
- License: SIL Open Font License 1.1; local copy at
  `third_party/licenses/noto-cjk/OFL-1.1.txt`.
- Local files:
  - `NotoSansCJK-Regular.ttc`: `32,355,424` bytes, SHA-256
    `3e7e5afaac2c6d872592d76abedac03a51c6f0fc42d11e311ff2816a6c368afe`.
  - `NotoSerifCJK-Regular.ttc`: `26,273,008` bytes, SHA-256
    `5dec6bbce13a3bbf1487a022392c23e571abd0696a102f3715697420dd94b47a`.
- Reason platform APIs are not enough: Android devices expose CJK-capable system
  fonts under vendor-specific family names. `office2pdf` maps Microsoft YaHei
  to Noto Sans CJK SC and SimSun to Noto Serif CJK SC, so bundling both fonts
  provides a predictable local fallback for those common Chinese Office fonts.
- Manual verification note: Simplified Chinese text rendered visibly after the
  July 14, 2026 arm64 physical-device rebuild. Layout fidelity remains limited;
  overlapping text and shifted Office shapes are still expected for complex
  DOCX/PPTX/XLSX files.

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
