# Localization Verification

This document does not claim device results. Android Studio Run/Debug with a
physical arm64 device remains the runtime source of truth. Do not run Gradle,
Cargo, or NDK builds from Codex for this migration.

## Static checks

- Run `python scripts/check_translations.py` with Python 3.8 or newer. No Android
  SDK or third-party Python package is needed.
- Exercise temporary fixtures for malformed XML, duplicate/unknown keys, wrong
  resource types, parameter index/type changes, and missing `other`. All must fail.
- Additional valid plural categories and missing translations must pass; missing
  translations report their completion and use English fallback.
- Inspect for remaining language-object comparisons, English-message lookup, or
  translated labels used for routing. Protocol tokens and FFmpeg log parsing are
  not translation tables.

## Recorded static results — September 8, 2026

- Resource validator: 611/611 translatable resources in each Chinese language;
  no XML, key, type, declaration, or placeholder errors.
- Eleven temporary validator fixtures passed, including malformed/duplicate
  resources, unknown keys, type/index mismatches, missing `other`, valid extra
  plural categories, and partial translations.
- AAPT2 resource-only compilation passed for all three `strings.xml` files.
  This did not build, link, install, or run an APK.
- Kotlin PSI parsing passed. Independent analysis with cached Kotlin/Android
  libraries and temporary resource symbols reported no new diagnostics compared
  with the original Git HEAD. Both analyses reported the same five baseline
  diagnostics: three forward MIME constant references and two inline-lambda
  `break`/`continue` checks. This standalone K1 analysis is not the project
  Android/Compose build and does not establish build success.
- `git diff --check` passed. No Gradle, Cargo, or NDK build was run.

## Device matrix — pending

| Scenario | Acceptance | Status |
| --- | --- | --- |
| API 26–32: each language and Follow system | Selection persists; UI and notifications agree | Pending |
| API 33+: app picker and system app-language settings | Both pickers agree after changes in either location | Pending |
| Upgrade with each legacy language preference | Migrate once; preserve existing explicit system app language | Pending |
| System zh-CN, zh-TW, zh-HK, and unsupported locales | Correct resource matching and English fallback | Pending |
| A temporary partial translation with Chinese as a secondary system language | Missing entries use English in both UI resources and background messages | Pending |
| Queued files, modified parameters, and merge groups during switching | Nothing is cleared or reset | Pending |
| Open PDF password dialog during switching | Input and pending selection survive; no password persistence | Pending |
| Model download and conversion during switching | Work continues once; visible progress/notification refresh | Pending |
| Switching with an idle service | No service or network request is started | Pending |
| PDF conversion/compression/encryption/decryption and JPG/PNG contact sheets | Identical operation dispatch in every language | Pending |
| Decimal fields with local separator or dot | Valid values accepted, ambiguous groups rejected, canonical FFmpeg arguments | Pending |
| Large fonts, long text, TalkBack, debug pseudo-locales | Usable layouts and correct accessibility/RTL behavior | Pending |

Also check normal theme modes and the Android 12+ launch screen after the
AppCompat theme change. Use temporary debug-only pseudo-locales, not production
language entries. This migration preserves live state across locale-only changes
but does not expand existing process-death persistence.
