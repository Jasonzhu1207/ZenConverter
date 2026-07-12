# Release Automation

GitHub Actions builds signed release APKs from repository secrets.

## Triggers

- Pushes to `main` update a permanent pre-release named `pre-release`.
- Tags starting with `v`, such as `v0.1.0`, create or update the matching GitHub
  Release.
- The workflow can also be started manually with `workflow_dispatch`.

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

The workflow currently publishes one signed ARM APK containing `armeabi-v7a`
and `arm64-v8a` native libraries:

```text
ZenConverter-pre-release-armeabi-v7a-arm64-v8a.apk
ZenConverter-vX.Y.Z-armeabi-v7a-arm64-v8a.apk
```

The current self-built FFmpegKitNext AAR includes only ARM native libraries, so
release builds intentionally filter native libraries to `armeabi-v7a` and
`arm64-v8a`. x86 and x86_64 emulator/Chromebook APKs are not published yet.

When publishing, the workflow also removes older `*-arm64-v8a.apk` and
`*-universal.apk` assets from the same release to avoid leaving misleading
downloads beside the current ARM build.

The CI job verifies that the recorded self-built FFmpegKitNext AAR is already
present under `app/libs` and that its SHA-256 is:

```text
6f3bb932ba76ff2627bef6cbfd77fa24bb7186afe27d88da37f69cd60c207602
```

It does not download FFmpegKit binaries from third-party forks. The recorded AAR
is checked into the repository as a vetted release input. The small
`smart-exception` helper dependencies are resolved by Gradle from Maven Central
unless their local JARs are supplied; those optional local JAR caches stay
ignored by git.

The documented AAR includes `libmp3lame`, but MP3 output remains experimental
until physical-device sample conversion passes.
