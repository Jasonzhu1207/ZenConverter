# 0026 Broader providers in the file import picker

## Status

Accepted; physical-device verification is required.

## Context

The file import action used `ACTION_OPEN_DOCUMENT` through
`OpenMultipleDocuments`. Android's document picker can then show providers
that implement `DocumentsProvider`, but some file managers, media apps, and
other local tools expose file selection through `ACTION_GET_CONTENT` instead.
Those apps were missing from the import picker's "browse other apps" section
even though the same apps could send files to ZenConverter through Share or
Open with.

## Decision

Launch the existing From files entry with a custom `ACTION_GET_CONTENT` intent
using `*/*`, `EXTRA_ALLOW_MULTIPLE`, and read URI permission. Do not add
`CATEGORY_OPENABLE`, so apps that declare a broader GET_CONTENT activity can
participate. Parse both the returned data URI and `ClipData` with the existing
deduplication path, then enqueue documents through the existing metadata and
category detection flow.

Keep `OpenMultipleDocuments` as a fallback when no GET_CONTENT activity can be
resolved. Keep gallery, folder, Share, and Open with flows unchanged. URI
persistable permission remains best effort; non-persistable providers continue
through the existing direct-read and SafeCache fallback paths.

## Consequences

The system picker can show more installed applications, including providers
that do not implement `DocumentsProvider`. The exact list remains dependent on
the Android version, ROM, and installed applications. GET_CONTENT grants may
be temporary, so imported files must be read while the current task has its
grant; the app must not treat failure to persist that grant as an import error.
