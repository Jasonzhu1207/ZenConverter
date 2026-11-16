package org.zenconverter.app.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

/**
 * Page-level PDF edits backed by PDFBox. Splitting copies only the requested
 * pages into a fresh document, so the source is never modified.
 */
object PdfPageEditor {
    /** Writes the zero-based [pages] of [source] to [target] as a new PDF. */
    fun split(source: File, pages: List<Int>, target: File) {
        PDDocument.load(source).use { document ->
            PDDocument().use { output ->
                pages.filter { it in 0 until document.numberOfPages }.forEach { index ->
                    output.importPage(document.getPage(index))
                }
                output.save(target)
            }
        }
    }
}
