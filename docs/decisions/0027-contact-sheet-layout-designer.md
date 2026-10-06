# Contact sheet layout designer

## Status

Accepted

## Context

The first contact-sheet implementation fixed the canvas width, margins, gaps, cell height, and four grid presets. That made the output predictable but prevented users from matching a storyboard, review sheet, or narrow mobile layout.

## Decision

Keep contact sheets on the native Bitmap/Canvas path and make the layout explicit in `VideoContactSheetOptions`. The editor controls rows, columns, canvas or cell width, aspect-ratio or fixed cell height, gaps, margins, alignment, fit mode, background, metadata header height, watermark, and timestamps. Presets are shortcuts that populate the same custom fields.

The layout is shared by single-file and batch video tasks. The editor deliberately does not decode or display source-video preview frames: this keeps the batch editor independent of any one file and avoids background extraction, bitmap lifetime issues, and unnecessary work while editing. The sheet card shows the calculated output summary, while final output remains evenly sampled over the full video or selected trim range.

To protect device memory, layouts are limited to 100 frames, 8192 pixels per dimension, and approximately 32 megapixels. Transparent backgrounds are retained for PNG; JPG uses white when transparency is selected.

## Consequences

The renderer now has one geometry calculation shared by the editor and final export. Large or invalid layouts are rejected before conversion. This adds more state and localized controls, but it avoids hidden fixed dimensions and keeps the offline native path and existing FFmpeg fallback unchanged.
