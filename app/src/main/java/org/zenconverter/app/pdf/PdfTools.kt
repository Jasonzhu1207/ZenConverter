package org.zenconverter.app.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File

/**
 * Pure PDF page maths and text layout, kept free of Android imports so the
 * parsing and pagination can be unit tested on the JVM. The Android-facing
 * screen in `ui/studio` does the file IO and PDFBox calls.
 */
object PdfTools {

    /**
     * Parses a human page-range expression like `1-3,5,8-` into zero-based page
     * indexes. `-` alone means "to the end". Invalid or out-of-range parts are
     * dropped, duplicates removed and the result sorted, so a typo never crashes
     * a split; the caller can detect "nothing matched" from an empty result.
     */
    fun parsePageRanges(expression: String, pageCount: Int): List<Int> {
        if (pageCount <= 0) return emptyList()
        val selected = linkedSetOf<Int>()
        expression.split(',', ';', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { part ->
                val dash = part.indexOf('-')
                if (dash < 0) {
                    part.toIntOrNull()?.let { page -> addIfValid(selected, page, pageCount) }
                    return@forEach
                }
                val startText = part.substring(0, dash).trim()
                val endText = part.substring(dash + 1).trim()
                val start = startText.toIntOrNull() ?: 1
                val end = endText.toIntOrNull() ?: pageCount
                if (start > end) return@forEach
                for (page in start..end) addIfValid(selected, page, pageCount)
            }
        return selected.sorted()
    }

    private fun addIfValid(target: MutableSet<Int>, oneBased: Int, pageCount: Int) {
        if (oneBased in 1..pageCount) target.add(oneBased - 1)
    }

    /** A single text line to render, already wrapped to the content width. */
    data class Line(val text: String, val isHeading: Boolean)

    /**
     * Wraps [source] into lines no wider than [maxChars], honouring Markdown
     * heading markers (`#`, `##`, …) and collapsing blank lines. Character-based
     * wrapping is deliberate: it needs no font metrics and stays testable.
     */
    fun layout(source: String, maxChars: Int): List<Line> {
        val limit = maxChars.coerceAtLeast(8)
        val lines = mutableListOf<Line>()
        source.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { raw ->
            val headingMatch = Regex("^(#{1,6})\\s+(.*)$").find(raw.trim())
            val isHeading = headingMatch != null
            val body = (headingMatch?.groupValues?.get(2) ?: raw).trim()
            if (body.isEmpty()) {
                lines.add(Line("", false))
                return@forEach
            }
            wrap(body, limit).forEach { wrapped -> lines.add(Line(wrapped, isHeading)) }
        }
        return lines
    }

    private fun wrap(text: String, limit: Int): List<String> {
        val out = mutableListOf<String>()
        var remaining = text
        while (remaining.length > limit) {
            val breakAt = remaining.lastIndexOf(' ', limit).takeIf { it > 0 } ?: limit
            out.add(remaining.substring(0, breakAt).trimEnd())
            remaining = remaining.substring(breakAt).trimStart()
        }
        if (remaining.isNotEmpty()) out.add(remaining)
        return out
    }

    /**
     * Strips HTML tags and decodes the handful of entities that matter for a
     * plain-text PDF. Script and style contents are removed first so their text
     * never leaks into the document.
     */
    fun htmlToPlainText(html: String): String {
        val withoutBlocks = Regex(
            "<(script|style)[^>]*>.*?</\\1>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).replace(html, " ")
        val withBreaks = withoutBlocks
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|h[1-6]|li|tr)\\s*>", RegexOption.IGNORE_CASE), "\n")
        val noTags = Regex("<[^>]+>").replace(withBreaks, "")
        return decodeEntities(noTags)
            .split('\n').joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun decodeEntities(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")

    /** Converts a Markdown document to the plain text used for the PDF body. */
    fun markdownToPlainText(markdown: String): String = markdown
        .replace(Regex("!\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("`([^`]*)`"), "$1")
        .replace(Regex("(\\*\\*|__)(.*?)\\1"), "$2")
        .replace(Regex("(\\*|_)(.*?)\\1"), "$2")
}

/**
 * Renders plain text into a PDF using PDFBox. Kept separate from the layout
 * logic so the maths stays unit-testable.
 */
object PdfWriter {
    private const val FONT_SIZE = 12f
    private const val HEADING_SIZE = 16f
    private const val MARGIN = 56f
    private const val LEADING = 16f

    fun writeTextPdf(lines: List<PdfTools.Line>, target: File) {
        val document = PDDocument()
        try {
            val pageWidth = PDRectangle.A4.width
            val pageHeight = PDRectangle.A4.height
            val maxCharsPerLine = ((pageWidth - MARGIN * 2) / (FONT_SIZE * 0.5f)).toInt()
            var page = PDPage(PDRectangle.A4)
            document.addPage(page)
            var stream = PDPageContentStream(document, page)
            var y = pageHeight - MARGIN

            fun newPage() {
                stream.close()
                page = PDPage(PDRectangle.A4)
                document.addPage(page)
                stream = PDPageContentStream(document, page)
                y = pageHeight - MARGIN
            }

            for (line in lines) {
                if (y < MARGIN + LEADING) newPage()
                if (line.text.isNotEmpty()) {
                    val font = if (line.isHeading) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA
                    val size = if (line.isHeading) HEADING_SIZE else FONT_SIZE
                    stream.beginText()
                    stream.setFont(font, size)
                    stream.newLineAtOffset(MARGIN, y)
                    // PDFBox's standard fonts are Latin-1; drop characters they
                    // cannot encode rather than throwing mid-document.
                    stream.showText(sanitizeForStandardFont(line.text, maxCharsPerLine))
                    stream.endText()
                }
                y -= LEADING
            }
            stream.close()
            document.save(target)
        } finally {
            document.close()
        }
    }

    private fun sanitizeForStandardFont(text: String, maxChars: Int): String {
        val truncated = if (text.length > maxChars) text.substring(0, maxChars) else text
        return buildString(truncated.length) {
            truncated.forEach { ch -> append(if (ch.code in 32..255) ch else '?') }
        }
    }
}
