# 0022: Android Resources And Per-App Languages

Status: Accepted; runtime verification pending

Date: 2026-09-08

## Context

Translations were embedded in Kotlin UI code, blocking community contributions.
Language branches and display-label routing made each new language risky. Live
queue and selection state also reside in the Activity.

## Decision

Use complete default English string/plural resources with script-based Chinese
translations. Keep a thin resource-backed `UiText` adapter, structured
`LocalizedText` messages, and stable `TargetId` keys. Separate external diagnostic
details from app-authored explanations; remove English-message lookup tables.

Use AndroidX AppCompat 1.7.1 for per-app languages on all supported API levels.
The framework alone does not provide equivalent language preferences on older
supported Android versions. This is a deliberate compatibility pin, not a
latest-version claim or a general dependency upgrade.

One manual locale declaration supplies both pickers; dependencies cannot silently
add supported app languages. Migrate old preferences once, then use AppCompat or
framework storage. Handle locale and layout-direction changes without rebuilding
the live Activity flow; update existing service notifications without starting
an idle service.

Resolve one matching app locale before reading resources so missing entries use
the English default rather than a secondary system language. Supply these same
resources to Compose and background messages through a resource-context wrapper
that delegates UI actions to the Activity. This works with the existing Compose
version without a dependency upgrade.

Use a standard-library Python resource validator and an unprivileged PR check.
Accept partial translations with English fallback. French remains a separate
community contribution; do not ship an empty placeholder.

## Consequences

Contributors edit XML rather than business logic and need no Android toolchain.
Maintainers still verify behavior through Android Studio and a physical device.
AppCompat adds JVM dependencies, not native binaries or an NDK requirement. Keep
its Apache-2.0 attribution separate from the application's AGPL grant.

The device checklist in `docs/testing-localization.md` remains a release gate.
