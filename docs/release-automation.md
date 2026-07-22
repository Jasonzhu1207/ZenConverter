# Release Automation

GitHub Actions builds signed release APKs from repository secrets.

## Triggers

- Pushes to `main` update a permanent pre-release named `pre-release` only
  when APK-relevant files change: `app/`, Gradle build files/wrapper, or the
  release workflow itself. Documentation, issue-template, attribution, and
  other non-APK changes do not consume a release build.
- Tags starting with `v`, such as `v0.1.0`, create or update the matching GitHub
  Release.
- The workflow can also be started manually with `workflow_dispatch`.

## Versioning

Android has two version fields:

- `versionName`: user-facing text such as `0.1.0`.
- `versionCode`: an integer used by Android to decide whether one APK can update
  another APK. It must increase over time for normal updates.

The app defaults to `versionName=0.1.0` and `versionCode=1000001` for local
builds. Release CI overrides both values without editing source files:

- Tag builds use the pushed release tag. For example, `v0.1.0` becomes
  `versionName=0.1.0`.
- Main-branch pre-release builds use the latest semantic release tag, so the
  permanent `pre-release` asset stays on the same visible version as the latest
  formal release.
- `versionCode` is derived from the semantic version plus the number of commits
  since the latest release tag for pre-release builds. This keeps repeated
  pre-release APKs installable over earlier pre-release APKs while the next
  formal version tag still moves to a higher range.
- Before the first semantic release tag exists, pre-release builds use
  `versionName=0.1.0` with a lower bootstrap `versionCode`, so the first
  `v0.1.0` release can still install over them.

To publish the first formal release, create and push a tag such as:

```powershell
git tag v0.1.0
git push origin v0.1.0
```

## Required Repository Secrets

Create these under GitHub repository settings:

`Settings -> Secrets and variables -> Actions -> New repository secret`

| Secret | Purpose |
| --- | --- |
| `ANDROID_RELEASE_KEYSTORE_BASE64` | Base64 text form of the release `.jks` file. |
| `RELEASE_STORE_PASSWORD` | Keystore password. |
| `RELEASE_KEY_ALIAS` | Signing key alias. |
| `RELEASE_KEY_PASSWORD` | Signing key password. |

The secret value must be the actual value, not the secret name. For example,
`RELEASE_STORE_PASSWORD` should contain the password from local
`local.properties`, not the text `RELEASE_STORE_PASSWORD`.

On Windows PowerShell, generate the base64 secret from the project root:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes(".\ZenConverter.jks"))
```

Paste the command output into `ANDROID_RELEASE_KEYSTORE_BASE64`.

## Outputs

The workflow currently publishes one signed `arm64-v8a` APK:

```text
ZenConverter-pre-release-arm64-v8a.apk
ZenConverter-vX.Y.Z-arm64-v8a.apk
```

The current self-built FFmpegKitNext AAR contains `arm64-v8a` native libraries
only. This keeps the single APK smaller and matches the Office2PDF native
renderer, which is currently bundled only for `arm64-v8a`. 32-bit ARM, x86, and
x86_64 APKs are not published.

When publishing, the workflow also removes older `*-armeabi-v7a-arm64-v8a.apk`,
`*-arm64-v8a.apk`, and `*-universal.apk` assets from the same release to avoid
leaving misleading downloads beside the current arm64 build.

The CI job verifies that the recorded self-built FFmpegKitNext AAR is already
present under `app/libs` and that its SHA-256 is:

```text
d1f2512e806ac3ff99b2f4c3d2e36fcca8c5c0eec548d84da81cf94d054cf406
```

It does not download FFmpegKit binaries from third-party forks. The recorded AAR
is checked into the repository as a vetted release input. The small
`smart-exception` helper dependencies are resolved by Gradle from Maven Central
unless their local JARs are supplied; those optional local JAR caches stay
ignored by git.

Release notes include the resolved Android version line and APK SHA-256:

```text
Android version: X.Y.Z (versionCode)
APK SHA-256: ...
```

The in-app update checker reads the Android version line and verifies downloaded
APKs when the checksum is present.

## Continuous Pre-release Behavior

The `pre-release` tag is permanent and always points to the commit used for the
current continuous APK. GitHub does not update the visible `published at` time
when a release is edited or an asset is replaced. Therefore, each qualifying
main-branch build deletes and immediately recreates the release under the same
tag. This keeps one stable pre-release URL and asset name while making the
release page's publication date match the latest APK. The notes also record the
exact UTC build time.

Releases created by the workflow use the repository-scoped `GITHUB_TOKEN` and
are attributed to `github-actions[bot]`. GitHub does not allow the author of an
existing release to be changed. To retain bot attribution for future formal
releases, push the `vX.Y.Z` tag and let this workflow create the release; do
not create the release manually in the GitHub UI.

The app probes for `libmp3lame` before MP3 export, but MP3 output remains
experimental until physical-device sample conversion passes.
