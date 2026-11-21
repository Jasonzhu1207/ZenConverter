# PDF splitting and Markdown to PDF conversion

## Status

Accepted

## Context

Users requested two document capabilities (tracked in upstream Issues #35 and #39):

1. **PDF splitting**: ZenConverter already bundles `PDFBox-Android 2.0.27.0` for PDF merging, compression, encryption/decryption, and selectable text extraction, while the media lanes already established a tactile trim-and-split slider UX (start/end thumbs with draggable scissor cut markers and numeric precise fields). Splitting PDFs needed to preserve original vector content and text layers while keeping peak memory predictable on multi-hundred-page files and offering a one-tap "Every page" workflow.
2. **Markdown to PDF**: Converting `.md` / `.markdown` / `.mdown` / `.mkd` files to PDF on Android had to preserve rich Markdown and common embedded HTML/CSS formatting (headings, bold/italic/strikethrough/underline, `<font>` / `<span style="...">` sizes and colors, text alignment, GFM/HTML tables, task lists, code blocks with syntax highlighting, callouts, `<details>`, footnotes, frontmatter, and math notation), produce **real vector PDF text with CJK & emoji support**, generate **clickable PDF hyperlinks and PDF outline bookmarks**, support **embedded images**, extract **video first frames** as interactive media cards, and honor ZenConverter's **offline-first** privacy rule.

## Decision

### 1. PDF Splitting via Chunked `PDFBox-Android` `Splitter`

- Keep PDF splitting inside the existing `NativePdf` / `PDFBox-Android` path with zero new native binaries.
- Reuse the media trim-and-split mental model in `PdfSplitOptionsPanel`:
  - Switch `[ Custom split | Every page ]` modes (`PdfSplitMode`).
  - Custom mode supports a 1-based page `RangeSlider` (`PdfPageSplitSlider`) with draggable scissor cut markers positioned between pages, `[- N +]` split-point stepper (pre-seeding the midpoint when selected on 2+ page PDFs), `Precise` numeric page inputs (`PrecisePdfSplitFields`), and live segment preview chips (`P1–5 · P6–10`).
  - Pure validation lives in `PdfSplitValidator` and runs both in UI state and right before execution in `MainActivity.kt` / `ConversionService.kt` (supporting encrypted PDFs by probing/unlocking and backfilling `pageCount` first).
- In `ConversionService.writeSplitPdfFiles`, load the source PDF once with `MemoryUsageSetting.setupTempFileOnly()` and execute `Splitter` **one range at a time** (calling `close()` on each output `PDDocument` after saving). Peak open `PDDocument` count stays at 2 rather than materializing every output page tree simultaneously. Single-range trims export one `<name>_pX-Y.pdf` file; multi-part or every-page exports write numbered PDFs into a subfolder.

### 2. Markdown to PDF via `commonmark-java` + `StaticLayout`/`PdfDocument` + `PDFBox` Post-Pass

Evaluated and rejected alternatives:
- **Headless WebView `PrintDocumentAdapter`**: Rejected because `LayoutResultCallback` / `WriteResultCallback` constructors are package-private and break under Android API 34–36 runtime verification (`IllegalAccessError`), Android Chromium WebView strips interactive PDF URI link annotations and document outline bookmarks on print, and background service threading conflicts with UI WebView requirements.
- **Direct `PDFBox` `PDPageContentStream` typesetting**: Rejected because PDFBox 2.0 cannot load CFF-based `.ttc` system CJK fonts (`NotoSansCJK-Regular.ttc`), lacks HarfBuzz complex script shaping/fallback and color emoji support, and would bloat APK size with static fonts.
- **Rebuilding `libzen_office2pdf.so` with Typst Markdown**: Rejected because it requires an NDK/Rust `.so` rebuild, couples to upstream Typst version constraints, and Typst ignores raw HTML/CSS tags common in GitHub/Obsidian/Typora `.md` files.

Chosen pipeline under `app/src/main/java/org/zenconverter/app/markdown/`:
1. **Dependency**: Add `org.commonmark:commonmark:0.21.0` + 5 official extensions (`gfm-tables`, `gfm-strikethrough`, `autolink`, `task-list-items`, `yaml-front-matter`). Pinned at `0.21.0` (BSD-2-Clause, transitive `autolink-java` MIT) for Java 8 bytecode compatibility at `minSdk 26` without core library desugaring and zero extra `.so` footprint (~200 KB).
2. **Parsing (`MarkdownStyleParser`, `MarkdownAstParser`, `MarkdownCodeHighlighter`)**: Reads up to 16 MiB as UTF-8 (BOM stripped) with GB18030 fallback; preprocesses footnotes, block math (`$$...$$`), and Obsidian embeds; walks CommonMark AST plus paired HTML tag stacks (`<font>`, `<span style>`, `align`, `<center>`, `<details>`, `<u>`, `<sub>`/`<sup>`, `<kbd>`, `==highlight==`, `<video><source>`, HTML `<table>` with `colspan`), GitHub Callouts, inline currency-safe `$math$` Unicode formatting, and GitHub Light code syntax highlighting.
3. **Media Resolution (`MarkdownMediaResolver`)**:
   - `data:` Base64 images and SVG/shields badges decode 100% offline.
   - Relative local images and videos resolve via a user-granted SAF `assetsTreeUri` (auto-bound when importing via folder picker, or manually linked via an optional button per file/batch) with path-traversal protection.
   - Remote HTTP/HTTPS images and video URLs remain **OFF by default** (`MarkdownExportOptions.fetchRemoteMedia = false`), rendering cleanly as clickable URL cards to protect offline-first privacy; when explicitly enabled by the user, downloads are strictly bounded (5 scheme-checked redirects, 10s/15s timeouts, 12 MiB/image, 40 items / 60 MiB cap).
   - Video first-frame extraction uses a 3-tier fallback: `MediaMetadataRetriever` (local FD or 15s-timeout remote streaming) -> bounded (<=25 MiB) FFmpegKit first-frame extraction -> `<video poster>` image -> clean clickable video card.
4. **Vector Rendering & PDF Post-Pass (`MarkdownPdfRenderer`)**: Renders blocks pagination-aware on Android `android.graphics.pdf.PdfDocument` at `2f` supersampled layout scale (`StaticLayout`) using system CJK/emoji typefaces, repeating multi-page table headers automatically and decoding/recycling bitmaps per page. Runs a `PDFBox-Android` post-pass adding borderless clickable `PDAnnotationLink`/`PDActionURI` boxes and hierarchical H1–H3 `PDDocumentOutline` bookmarks (`PDPageXYZDestination`).

## Consequences

- PDF splitting works completely offline, keeps vector clarity intact, reuses the familiar trim/split gesture design, and bounds memory during large splits.
- Markdown to PDF avoids WebView fragility and native `.so` bloat while supporting rich Markdown + inline HTML/CSS, CJK typography, clickable links, bookmarks, images, and video first frames with strict offline defaults.
- All Markdown rendering tuning (pagination, inline style stack, HTML tags, math symbol tables, code lexer tokens) lives cleanly in self-contained Kotlin files under `org.zenconverter.app.markdown` and can evolve independently without native build workflows.
