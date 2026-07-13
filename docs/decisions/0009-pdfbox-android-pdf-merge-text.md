# 0009 PDFBox Android PDF Merge Text

## Status

Accepted.

## Context

ZenConverter now has native image/PDF conversion through Android `PdfDocument`
and `PdfRenderer`. The next smallest PDF step is different: PDF merge should be
a true merge that keeps normal PDF page objects, selectable text, and vector
content instead of rasterizing every page, and PDF to TXT needs a text-layer
experiment that works beyond the newest platform PDF APIs.

Android platform APIs are not enough for this scope. `PdfDocument` writes new
pages but does not merge existing PDFs. `PdfRenderer` rasterizes pages and is
not a text or object extraction API. Platform text extraction through PDF APIs is
available only on newer Android/PDF extension levels, which is too narrow for
the first experiment.

## Decision

Add `com.tom-roush:pdfbox-android:2.0.27.0` for two focused PDF operations,
consumed as a local Maven Central AAR under `app/libs`:

- PDF to PDF merges multiple selected PDFs as one task with `PDFMergerUtility`
  and `PDDocument`.
- PDF to TXT extracts selectable text with `PDFTextStripper`, one page at a
  time, writing stable page separators.

PDFBox is initialized with `PDFBoxResourceLoader.init(applicationContext)` before
use. PDF inputs are copied one at a time into a task-specific cache directory so
SAF providers behave consistently and storage checks happen before copying.
Passwords remain transient in memory, aligned to input URIs, and are cleared
when tasks finish, fail, cancel, or the queue is cleared. Output PDFs are not
re-encrypted.

Because local AAR dependencies do not bring Maven transitive dependencies, also
bundle the PDFBox-Android POM dependencies as local JARs:

- `org.bouncycastle:bcprov-jdk15to18:1.72`
- `org.bouncycastle:bcpkix-jdk15to18:1.72`
- `org.bouncycastle:bcutil-jdk15to18:1.72`

## Consequences

- License: PDFBox-Android is Apache-2.0. Bouncy Castle uses the Bouncy Castle
  License, an MIT-style license. Both are compatible with the current
  `GPL-3.0-or-later` project direction.
- Maintenance risk: the repository is not archived, but release cadence is low.
  Version `2.0.27.0` was released on January 2, 2023, and the latest observed
  upstream push was March 18, 2024. Keep usage narrow and documented.
- PDF merge is best-effort for advanced structures such as complex forms,
  bookmarks, attachments, and metadata. It should not be advertised as a perfect
  archival merge.
- PDF to TXT is not OCR. Scanned PDFs or image-only pages without selectable
  text fail with `PDF has no selectable text; OCR is not included`.
- PDFBox-Android references optional Gemalto JPEG2000 helper classes from its
  JPX filter. The app does not use PDFBox for JPEG2000 image encode/decode, so
  release shrinking suppresses those missing optional classes instead of adding
  another binary dependency for a path outside the current feature scope.
- This dependency does not change the existing Android `PdfDocument` and
  `PdfRenderer` image/PDF paths.
