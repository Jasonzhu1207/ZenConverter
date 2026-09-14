# Settings UI acceptance

This change has not been built, installed, or exercised on a physical device by
Codex. Android Studio Run/Debug on an arm64 device is the runtime authority.
No Gradle, Cargo, NDK, emulator, or dependency installation is required by this
checklist. Mark runtime results only after observing them.

## Static checks

- `python scripts/check_translations.py`: English resource schema and Chinese /
  French translations must pass without missing references or placeholder errors.
- `git diff --check`: no whitespace errors.
- Kotlin PSI parsing and independent type analysis against cached Android and
  Compose dependencies can detect syntax and symbol/type mistakes. These checks
  do not cover all compiler diagnostics (including experimental API opt-in), run
  the Compose compiler plugin, build an APK, or prove runtime behavior.
- Compare with the pre-change snapshot: conversion task cards, format options,
  engine selection, model callbacks, and metadata cleanup/restore callbacks must
  remain connected. Application-authored visible text stays in resources.

## Recorded static results — September 14, 2026

- Translation validator: Simplified Chinese, Traditional Chinese, and French
  each 632/632; zero errors. Kotlin string/plural references match resources.
- `git diff --check` passed; new settings sources and documentation also passed
  a trailing-whitespace check (untracked files are not covered by Git's diff).
- Kotlin PSI: 12 UI source files, zero syntax errors.
- Independent Kotlin type analysis: 45 application source files plus one
  temporary resource/build-symbol file, zero reported errors, using existing
  cached dependencies. No APK, Compose compiler validation, or device result is
  implied. Temporary analysis files live outside the repository.
- Text from `HeroAddButton` onward, including task cards and format controls,
  matches the snapshot taken before this implementation.

## User-reported Android Studio build failure and correction

- The supplied `:app:assembleDebug` log failed at `:app:compileDebugKotlin` with
  six experimental layout API opt-in errors in `MetadataSecurityScreen.kt` and
  `OfflineEnginesScreen.kt`. The standalone analysis above missed these errors.
- `AdaptiveActions` exposed the experimental `FlowRowScope` receiver to callers.
  Its callback now uses stable `RowScope`; the internal `FlowRow` invocation
  retains the local opt-in. Existing button weights and wrapping are preserved.
- Android Studio rebuild after this correction: pending. No successful APK build
  or device result has been observed for this revision.

## Layout and accessibility — pending

| Scenario | Acceptance |
| --- | --- |
| Phone portrait, narrow multi-window, tablet landscape | Settings, models/fonts, metadata, help, and privacy share a centered column of at most 680 dp. Header and list align. No horizontally stretched tablet rows. |
| Light, dark, pure black, dynamic and explicit accent colors | Surfaces and controls use the chosen Material palette; symbols and selected states remain readable. |
| Android 11 or below / Android 12 or above | Wallpaper dynamic color is absent below API 31 and selectable from API 31 onward. On older Android versions, a restored Dynamic preference falls back to Charcoal with the correct label and selection. |
| English, Simplified/Traditional Chinese, French, largest system font | Labels and instructions wrap. Theme/channel selectors become vertical when needed. Actions and metadata tags do not overlap. The home add button and metadata button remain separate and usable. |
| System animator duration scale set to zero | Page transitions finish immediately, with no stuck overlay or hidden controls. |
| RTL layout (debug testing only) | Back icons and page transitions mirror; order and alignment remain usable. |
| TalkBack and keyboard | Switch on/off and single-choice selections are announced. Color swatches announce their name and selection. Back and source links have usable targets. Covered pages cannot be activated or traversed. |
| Home to settings, settings to each subpage, direct home to metadata | System Back and visible Back return to the correct parent. Returning restores each list's scroll position. Rapid forward/back does not crash or leave multiple active pages. |
| Add/remove files while using the home entry | Add button moves between its measured empty-state origin and header; there is one interactive add button. |

## Updates — pending

Use a GitHub-updates-enabled build. Check the distribution build with updates
disabled separately: there must be no update UI or implicit update requests.

| Scenario | Acceptance |
| --- | --- |
| Launch, scroll settings, expand update controls, switch language/theme/channel | No update check starts until the actual check button is pressed. |
| Stable and preview check | Correct endpoint, channel, and version-code comparison; a failed request is never reported as up to date. |
| Available / up to date | Release version/details and GitHub source are visible; release-notes link opens the release page. |
| Network failure, invalid metadata, no APK | Localized reason is visible, with external diagnostic detail kept separate. Retry works. |
| Download, unknown total size | Known totals show determinate progress; unknown totals show indeterminate progress plus downloaded bytes. |
| Download fails, including checksum failure | Failure reason is visible. Retry and browser download remain available. |
| Scroll update controls offscreen; visit home and other subpages; return | Check/download state survives, active work is not restarted, cancel/install is available on return. |
| Double check/download taps; change channel while busy | At most one operation starts; channel stays locked during work and cancellation cleanup. |
| Cancel and immediately retry | Old work releases its file/connection before another job can start. Cancellation is shown and retry works. |
| Installation disallowed | Permission settings open with a clear instruction. Returning retains the completed APK and install button. |
| No installer / installation launch fails | Visible failure feedback; completed download and browser fallback remain available. |

## Existing features — pending

- Switch locale while a conversion or model/font download is active: work runs
  once, progress and notifications refresh, queue and modified parameters survive.
- Switch locale with a PDF input/output password prompt open: prompt and input
  remain intact; no password persistence or logging is added.
- Select default/custom output locations; cancel the SAF picker; reopen it and
  choose a different folder. Language and output selections persist as before.
- Download/cancel/retry Real-ESRGAN and RIFE models; download/delete optional CJK
  fonts. Source links, size, failure reason, and ready states remain accessible.
- Inspect JPEG and video metadata. JPEG cleanup makes a backup; eligible matching
  images can restore it. Videos and unsupported image formats are never presented
  as supporting cleanup. Busy and write-permission guards still apply.
- Add files, edit parameters, run/cancel conversion, and open/share results. Help,
  privacy, repository, and sponsor actions remain reachable.
