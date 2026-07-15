# 0010 Manual GitHub Update Checks

## Status

Accepted.

## Context

ZenConverter is distributed through GitHub Releases first. Users need a real
manual update check from the About panel, with separate stable and preview
channels. The app is offline-first, so update checks must not become background
network traffic or blur the promise that conversions stay local.

GitHub connectivity can be poor for China mainland users, but the smallest
healthy step is to implement the GitHub path first and add mainland mirror
source ordering later.

## Decision

Add a manual update checker that uses platform networking APIs instead of a new
HTTP dependency:

- stable checks read GitHub Releases `latest`;
- preview checks read the permanent `pre-release` release tag;
- version comparison uses Android `versionCode`;
- check failures are shown as failures, not as "already latest";
- APK download can happen inside the app with visible progress;
- browser download remains available as a fallback.

The release workflow now writes APK SHA-256 into release notes. The app verifies
downloaded APKs when that checksum is present, and otherwise falls back to the
HTTPS download without claiming checksum verification.

## Consequences

- The app needs `INTERNET` for user-triggered update checks and downloads.
- The app needs `REQUEST_INSTALL_PACKAGES` plus a `FileProvider` so downloaded
  APKs can be handed to the Android package installer.
- No new third-party dependency is added.
- F-Droid distribution may need a build flavor or policy review later if
  self-update behavior conflicts with repository rules.
- Mainland update mirrors should be added later as trusted endpoints with clear
  fallback order, not as random GitHub proxy URLs.
