# License And Attribution

## Project License Decision

Project source license: `AGPL-3.0-or-later`.

Reason: ZenConverter's trust comes from inspectable source code and local-first
behavior. AGPLv3 keeps the project in the GPL family required by the current
media stack while adding a stronger copyleft boundary for modified versions
made available over a network.

The full license text is kept at the repository root in `LICENSE`.

## Dependency Intake Checklist

Before a dependency becomes core:

- Confirm the package is actively maintained.
- Confirm license compatibility with `AGPL-3.0-or-later`.
- Record homepage, source URL, license, and reason for use.
- Prefer official AndroidX APIs for platform features.
- Avoid abandoned wrappers as core infrastructure.

## Current AndroidX Dependencies

- AndroidX Compose and AndroidX Material Icons Extended are Apache-2.0
  dependencies used for the native Android UI.

## Current PDF Dependency

- Dependency: local Maven Central AAR
  `app/libs/pdfbox-android-2.0.27.0.aar`.
- Coordinates: `com.tom-roush:pdfbox-android:2.0.27.0`.
- Package type: Android AAR.
- Upstream source: `https://github.com/TomRoush/PdfBox-Android`.
- License: Apache License 2.0, compatible with the current
  `AGPL-3.0-or-later` project license.
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
  `app/libs/ffmpeg-kit-next-7.1.0.aar`.
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
  - libvpx license file.
  - Opus license file.
  - x264 license file.
  - x265 license file.
  - cpu-features license file: Apache License 2.0.
- Project compatibility: the app's license is `AGPL-3.0-or-later`, so the
  packaged GPL-family texts are compatible with the current project direction.
  Revisit this before any non-GPL-family distribution.
- Reason platform APIs are not enough: physical-device logs on July 5, 2026
  showed the earlier Android hardware media path timing out on MKV before
  writing any muxer sample, while the same service pipeline completed MP4/M4A
  work. Physical-device logs on
  July 11, 2026 showed the earlier Free FFmpegKit AAR lacked `libmp3lame`.
- Maintenance check: the original `arthenica/ffmpeg-kit` repo is archived, but
  `arthenica/ffmpeg-kit-next` is the upstream successor used as source for this
  self-built binary. Third-party prebuilt fork binaries are not trusted by
  default.

Build record for the selected binary:

- Replacement inspected: July 17, 2026.
- Build command: not recorded in this repository yet. Record the exact command,
  package flags, and source revision before a tagged release.
- Packaged external license files observed in the AAR: `lame`, `libiconv`,
  `libvpx`, `opus`, `x264`, `x265`, and `cpu_features`.
- Build targets included: `arm64-v8a`.
- Android ABIs included in the AAR: `arm64-v8a`.
- Android ABIs packaged in the app: `arm64-v8a` only, via Gradle `abiFilters`.
- AAR file name: `ffmpeg-kit-next-7.1.0.aar`.
- AAR size: `12,323,954` bytes.
- AAR SHA-256:
  `d1f2512e806ac3ff99b2f4c3d2e36fcca8c5c0eec548d84da81cf94d054cf406`.
- Verification evidence:
  - AAR contains `classes.jar`.
  - AAR contains `jni/arm64-v8a` native libraries.
  - AAR contains only `arm64-v8a` native libraries.
  - AAR packages `source.txt` with the upstream source-code request notice.
  - MP3 encoder availability is still runtime-probed by the app before export.

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
  `8f34766a1d1567b9d81d606e45ea690987a7c6ed`, release `v0.6.7`,
  Apache License 2.0. The local Apache text is at
  `third_party/licenses/office2pdf/Apache-2.0.txt`.
- JNI binding dependency: `jni` version `0.21.1`, Apache-2.0 OR MIT.
- JNI symbols consumed by the app:
  `Java_org_zenconverter_app_office_Office2PdfNative_convertBytesWithFontPaths`
  and `Java_org_zenconverter_app_office_Office2PdfNative_convertBytes`.
- Preferred native API: `convertBytesWithFontPaths`. It receives explicit font
  directories and assigns them to `office2pdf::config::ConvertOptions.font_paths`;
  this is required for Typst to search app-private CJK font files on Android.
- Current binary: rebuilt on August 21, 2026, `32,823,808` bytes with SHA-256
  `2b36e907b772514cf8b5840de338dc21ffd6100b5e3e8f0487b6cd77b2958d5b`.
  It exports both `convertBytesWithFontPaths` and the legacy `convertBytes`
  entry. The legacy entry is retained only as a compatibility fallback for
  older local test binaries; it cannot use the bundled CJK font directory.
- Reason platform APIs are not enough: Android platform APIs do not provide a
  DOCX/PPTX/XLSX to PDF renderer.
- Current scope: limited local DOCX/PPTX/XLSX to PDF conversion. The
  Kotlin service reads each input into memory and caps the source file at
  64 MiB before calling the native library.
- Release guardrail: generate and record a full transitive Cargo dependency
  license inventory before promoting this Beta compatibility path to Stable.

## Office CJK Fonts (System & On-Demand)

- Dependencies: Noto Sans CJK Regular and Noto Serif CJK Regular.
- Delivery model: System font discovery (`/system/fonts`, `/apex/...`) is used by
  default (0 MB download, 100% offline). High-fidelity Noto CJK font packages are
  hosted on Cloudflare R2 and downloadable on-demand into app-private storage.
- Download endpoints:
  - `https://assets.xlab.my/models/NotoSansCJK-Regular.ttc`
  - `https://assets.xlab.my/models/NotoSerifCJK-Regular.ttc`
- Upstream project: Noto CJK / notofonts.
- Maintenance status: active upstream font family.
- License: SIL Open Font License 1.1; local copy at
  `third_party/licenses/noto-cjk/OFL-1.1.txt`.
- Managed files:
  - `NotoSansCJK-Regular.ttc`: `32,355,424` bytes, SHA-256
    `3e7e5afaac2c6d872592d76abedac03a51c6f0fc42d11e311ff2816a6c368afe`.
  - `NotoSerifCJK-Regular.ttc`: `26,273,008` bytes, SHA-256
    `5dec6bbce13a3bbf1487a022392c23e571abd0696a102f3715697420dd94b47a`.
- Manual verification note: Chinese text renders using Android system fonts without
  downloading extra fonts; downloading the optional packages provides enhanced
  Noto Sans/Serif CJK fallbacks for Microsoft YaHei and SimSun typography.

## Current Font Native Binary

- Runtime path: local native library
  `app/src/main/jniLibs/arm64-v8a/libzen_woff2.so` for `arm64-v8a` only.
- Upstream source: Google woff2 (`https://github.com/google/woff2`), the WOFF 2.0
  reference implementation, Apache License 2.0. It links Brotli (MIT) statically.
- JNI symbols consumed by the app:
  `Java_org_zenconverter_app_font_Woff2Native_compress` and
  `Java_org_zenconverter_app_font_Woff2Native_decompress`, both `byte[] -> byte[]`.
- Current binary: `1,147,520` bytes with SHA-256
  `dcc4ed659a792c70fa78f0daac13c8ff1102bc8dcf423c89d861b80843d205af`.
- Reason platform APIs are not enough: Android has no SFNT to WOFF2 codec, and
  WOFF2 needs Brotli plus a glyf/loca/hmtx transform that is not practical to
  reimplement in app code.
- Scope: TTF/OTF <-> WOFF2 byte-level container conversion with no options.
  WOFF 1.0 is handled by a separate pure-Kotlin zlib codec and adds no
  third-party code.
- 16 KB page-size alignment (required): link with `-Wl,-z,max-page-size=16384
  -Wl,-z,common-page-size=16384`. The checked-in binary is 16 KB-aligned; verify
  any future rebuild with `native/woff2-jni/check-16kb-alignment.py`.
- Release guardrail: record the exact upstream commit and NDK/CMake build command
  before promoting this Beta path to Stable.

## Current ONNX Runtime Dependency

- Dependency: `com.microsoft.onnxruntime:onnxruntime-android:1.29.0`, consumed
  from Maven Central.
- Package type: Android AAR, MIT License.
- Upstream source: `https://github.com/microsoft/onnxruntime`.
- Reason platform APIs are not enough: Android has no platform API for
  deep-learning image super-resolution. ONNX Runtime runs the Real-ESRGAN model
  locally on CPU without sending pixels off-device.
- Android ABIs packaged in the app: `arm64-v8a` only, via Gradle `abiFilters`.
- Play compliance: the `.onnx` model is loaded through the normal
  `OrtEnvironment`/`OrtSession` API as a data file. The app never loads it as
  executable code (no ClassLoader, no System.load, no reflection-based dynamic
  loading). ONNX Runtime's own `libonnxruntime.so` ships inside the APK as a
  normal library dependency.
- Telemetry disabled: the AAR ships `ai.onnxruntime.TelemetryInitializer`, a
  ContentProvider that eagerly calls `System.loadLibrary("onnxruntime")` at app
  startup to set up Microsoft 1DS telemetry. The app removes this provider with
  `tools:node="remove"` (see `app/src/main/AndroidManifest.xml`) so the native
  library is only loaded lazily when the upscaler runs. This fixes a startup
  SIGSEGV on Android 16/17 (ONNX Runtime's CPU feature detection reads the
  now-restricted `ro.hardware.chipname` property and dereferences null) and keeps
  the app offline-first by never initializing the telemetry transport.
- R8 keep rule (release builds): `libonnxruntime4j_jni.so` resolves
  `ai.onnxruntime.*` classes and members by name at runtime. Release builds run
  R8 full mode (shrink + obfuscate), which renames/removes them and aborts with
  `JNI DETECTED ERROR ... java_class == null in call to GetMethodID`. The app
  keeps the package with `-keep class ai.onnxruntime.** { *; }` in
  `app/proguard-rules.pro` (see microsoft/onnxruntime#17847).

## Real-ESRGAN Model (Runtime Download)

- Model: `RealESRGAN_x4plus` (RRDBNet, fp32 ONNX, 4× upscale).
- Source: `https://github.com/xinntao/Real-ESRGAN`, BSD-3-Clause.
- Export script: `scripts/onnx_export/export_realesrgan.py` (opset 14, dynamic
  H/W, input `input`, output `output`, NCHW RGB in `[0,1]`).
- Distribution: the model is not bundled in the repository or APK. It is
  downloaded at runtime from the project's R2 direct link and verified against a
  hardcoded SHA-256 before use.
- Download URL: `https://assets.xlab.my/models/RealESRGAN_x4plus.onnx`
- Size: `67,051,973` bytes (displayed as `63.9 MB`).
- SHA-256: `39d5218cfcef542d667821a0d2072cfa51bfd857ab0e4ae7dc067c399a88d323`.
- The model list is hardcoded in the app; adding or changing a model is a code
  change that ships with an app update, not a remote hot-updated config.

## License Guardrail

ZenConverter's own source is AGPL-licensed. Do not introduce dependencies or
binary packages that conflict with `AGPL-3.0-or-later`, and keep third-party
license notices separate from the project's own license grant. Future FFmpeg
work should still prefer LGPL-compatible builds where practical, but GPL-family
components are acceptable when they are documented and compatible with the app's
AGPL distribution.

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
