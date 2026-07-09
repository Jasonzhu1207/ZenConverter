# Decision 0001: Native Android First

## Status

Accepted.

## Context

The project wants to handle large local files, including 1 GB class 4K videos.
Browser and WebView based WASM approaches are attractive for speed of
development, but large file access, background execution, memory limits, and
mobile performance are weak points.

## Decision

Build Android first with Kotlin and Jetpack Compose. Use native Android media
APIs and native compatibility engines instead of making WebView the core.

## Consequences

- Better performance and background reliability.
- More setup complexity than a PWA.
- Android app stores can wait; GitHub Releases can be first.
- iOS support becomes a later separate effort.
