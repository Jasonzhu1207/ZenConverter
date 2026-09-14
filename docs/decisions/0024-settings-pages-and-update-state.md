# Settings pages and update state ownership

Status: Accepted; Android Studio and physical-device acceptance pending.

## Context

The settings UI was split out of the home screen, but its full-width layout did
not suit tablets. Custom switches and selectors lacked complete accessibility
semantics. The new update row also removed preview-channel selection, browser
fallback, and failure feedback, and owned its coroutine scope inside a lazy item.
Disposing that item could discard the update state and cancel its work.

## Decision

- Keep native Compose pages and the existing stable `AppScreen` enum. Use one
  `SubpageHost` for transitions and a saveable state provider per destination.
  Home remains composed but is not placed while covered; outgoing pages have
  their semantics cleared and pointer events blocked during the short transition.
- Use `SettingsPage` for safe window insets, a centered content width of at most
  680 dp, and a consistent app bar and lazy list. Horizontal margins are 16 dp
  below 600 dp and at least 24 dp otherwise. Use the existing Material theme.
- Prefer Material switches, segmented buttons, and selectable/toggleable
  semantics. Measure translated labels before choosing horizontal selectors;
  narrow layouts and large fonts use vertical choices.
- Own `UpdateStateHolder` and its coroutine scope in the mounted app content,
  outside lazy items and destination transitions. Use application context for
  downloads and installation. Store localized errors as `LocalizedText`.
- Check only on an explicit button press. Keep stable and preview channels,
  version-code comparison, release details, browser fallback, download errors,
  cancellation, and installation-permission feedback. Keep the channel locked
  until an active check/download has finished, including cancellation cleanup.

## Consequences

Scrolling, changing destinations, and locale-only configuration changes do not
own or restart update downloads. Leaving settings is not a cancellation action;
the user can return to cancel or install. Disposal of the app content cancels its
scope; process-death persistence and resumable downloads are not added.

No dependency, update network API, preference schema, conversion engine, or native
binary changes are required. Actual window layout, focus, animation, lifecycle,
and installation behavior must be verified on a physical device using
`docs/testing-settings-ui.md`.
