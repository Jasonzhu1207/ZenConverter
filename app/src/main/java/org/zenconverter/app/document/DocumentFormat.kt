package org.zenconverter.app.document

/**
 * Document formats the on-device converter understands, and which conversions
 * between them are exact versus approximate. Everything runs locally: PDF and
 * text-like formats are handled by the in-process engines, while Word,
 * PowerPoint and Excel go through the bundled Office renderer, which emits a
 * PDF first. Kept free of Android imports so the routing rules stay unit
 * testable on the JVM.
 */
enum class DocumentFormat(val label: String, val extension: String) {
    Pdf("PDF", "pdf"),
    Docx("DOCX", "docx"),
    Pptx("PPTX", "pptx"),
    Xlsx("XLSX", "xlsx"),
    Txt("TXT", "txt"),
    Markdown("MD", "md"),
    Html("HTML", "html"),
    Epub("EPUB", "epub"),
    Csv("CSV", "csv"),
    Jpg("JPG", "jpg"),
    Png("PNG", "png");

    /** MIME types accepted by the picker for this format. */
    val mimeTypes: List<String>
        get() = when (this) {
            Pdf -> listOf("application/pdf")
            Docx -> listOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            Pptx -> listOf("application/vnd.openxmlformats-officedocument.presentationml.presentation")
            Xlsx -> listOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            Txt -> listOf("text/plain")
            Markdown -> listOf("text/plain", "text/markdown")
            Html -> listOf("text/html")
            Epub -> listOf("application/epub+zip")
            Csv -> listOf("text/csv", "text/comma-separated-values")
            Jpg, Png -> listOf("image/jpeg", "image/png")
        }

    /** The formats a file of this kind can be converted into. */
    val targets: List<DocumentFormat>
        get() = when (this) {
            Pdf -> listOf(Docx, Pptx, Xlsx, Txt, Markdown, Html, Epub, Jpg, Png)
            Docx -> listOf(Pdf, Pptx, Txt, Markdown, Html, Epub)
            Pptx -> listOf(Pdf, Docx, Txt, Markdown, Png)
            Xlsx -> listOf(Pdf, Csv, Txt, Markdown)
            Txt -> listOf(Pdf, Docx, Markdown, Html, Epub)
            Markdown -> listOf(Pdf, Docx, Html, Epub, Txt)
            Html -> listOf(Pdf, Docx, Txt, Markdown, Epub)
            Epub -> listOf(Pdf, Docx, Txt, Markdown, Html)
            Csv -> listOf(Pdf, Xlsx, Txt, Markdown)
            Jpg, Png -> listOf(Pdf)
        }

    /**
     * Conversions whose layout is rebuilt from content rather than carried over
     * verbatim, so the UI can warn that the result is approximate.
     */
    fun isApproximateTarget(target: DocumentFormat): Boolean = when (this) {
        Pdf -> target in setOf(Docx, Pptx, Xlsx, Csv)
        Xlsx -> target in setOf(Pdf, Csv)
        Pptx -> target in setOf(Docx)
        else -> false
    }

    companion object {
        /** Resolves a format from a file name, or null when unrecognised. */
        fun fromFileName(name: String): DocumentFormat? {
            val extension = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.extension == extension }
        }

        /** Formats that carry a document the Office renderer can turn into a PDF. */
        val officeFormats: Set<DocumentFormat> = setOf(Docx, Pptx, Xlsx)

        /** Formats handled as plain text in process. */
        val textFormats: Set<DocumentFormat> = setOf(Txt, Markdown, Html, Csv)
    }
}

/** Extra operations the Document tools card offers alongside format conversion. */
enum class DocumentOperation {
    MergePdfs,
    SplitPdf,
    CompressPdf,
    RotatePages,
    ExtractImages
}
