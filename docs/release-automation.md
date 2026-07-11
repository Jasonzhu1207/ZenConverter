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

The workflow currently publishes one signed arm64-v8a APK:

```text
ZenConverter-pre-release-arm64-v8a.apk
ZenConverter-vX.Y.Z-arm64-v8a.apk
```

The current FFmpegKit Free fallback AAR is arm64-v8a only, so release builds
intentionally filter native libraries to `arm64-v8a`. A true universal APK can
be added later only after the compatibility FFmpeg dependency provides matching
native libraries for every advertised ABI.

When publishing, the workflow also removes older `*-universal.apk` assets from
the same release to avoid leaving a misleading download beside the current
arm64-v8a build.

The CI job downloads the documented FFmpegKit local fallback AAR into `app/libs`
and verifies its SHA-256 checksum before running Gradle. The small
`smart-exception` helper dependencies are resolved by Gradle from Maven Central.
Binary fallback files stay ignored by git.

The documented CI fallback is the Free tier, which does not include the
`libmp3lame` MP3 encoder. Publishing an MP3-capable APK requires replacing that
CI input with a recorded non-GPL MP3-capable AAR or a self-built LGPL FFmpeg
package and updating the license/SHA records first.
