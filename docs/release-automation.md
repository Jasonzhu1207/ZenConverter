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

On Windows PowerShell, generate the base64 secret from the project root:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes(".\ZenConverter.jks"))
```

Paste the command output into `ANDROID_RELEASE_KEYSTORE_BASE64`.

## Outputs

The workflow currently publishes one signed universal APK:

```text
ZenConverter-pre-release-universal.apk
ZenConverter-vX.Y.Z-universal.apk
```

ABI-specific APKs can be added later if FFmpeg/native libraries make the
universal APK too large.
