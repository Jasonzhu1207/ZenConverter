package org.zenconverter.app.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.zenconverter.app.ebook.EpubBuilder
import org.zenconverter.app.ebook.EpubReader
import org.zenconverter.app.office.Office2PdfNative
import org.zenconverter.app.pdf.PdfTools
import org.zenconverter.app.pdf.PdfWriter

/**
 * Runs document conversions entirely on device. Office inputs are rendered to
 * PDF by the bundled native renderer, everything else is reshaped with the
 * in-process PDFBox / EPUB writers. Each conversion funnels through PDF as the
 * pivot format, which is the only common denominator available offline.
 */
object DocumentConverterEngine {

    /** Page geometry options offered on the Document converter screen. */
    enum class PageSize(val label: String) {
        A4("A4"),
        Letter("Letter"),
        FitToSource("Fit to source")
    }

    data class Options(
        val pageSize: PageSize = PageSize.A4,
        val quality: Int = 3,
        val keepFormatting: Boolean = true,
        val embedFonts: Boolean = true,
        val includeImages: Boolean = true
    )

    /**
     * Converts [source] into [targetFormat], writing the result to [target].
     * Returns the number of pages written, or null when the page count is not
     * meaningful for the target (for example a CSV export).
     */
    suspend fun convert(
        context: Context,
        source: File,
        sourceFormat: DocumentFormat,
        targetFormat: DocumentFormat,
        target: File,
        options: Options = Options()
    ): Int? = withContext(Dispatchers.IO) {
        require(sourceFormat != targetFormat) { "source and target formats must differ" }
        require(targetFormat in sourceFormat.targets) {
            "unsupported document conversion: ${sourceFormat.label} to ${targetFormat.label}"
        }
        if (sourceFormat == DocumentFormat.Pdf) {
            return@withContext convertFromPdf(source, targetFormat, target, options)
        }
        if (sourceFormat == DocumentFormat.Epub) {
            val text = EpubReader.toPlainText(source)
            return@withContext writeTextTarget(text, targetFormat, target, title = source.nameWithoutExtension)
        }
        if (sourceFormat in DocumentFormat.textFormats) {
            val text = readTextSource(source, sourceFormat)
            return@withContext writeTextTarget(text, targetFormat, target, title = source.nameWithoutExtension)
        }
        // Word, PowerPoint and Excel go through the native Office renderer first.
        val pdfBytes = Office2PdfNative.convert(context, source.readBytes(), sourceFormat.extension)
        val pdfTemp = File.createTempFile("zen-doc-pivot", ".pdf", context.cacheDir)
        try {
            pdfTemp.writeBytes(pdfBytes)
            convertFromPdf(pdfTemp, targetFormat, target, options)
        } finally {
            pdfTemp.delete()
        }
    }

    private fun convertFromPdf(
        source: File,
        targetFormat: DocumentFormat,
        target: File,
        options: Options
    ): Int? = when (targetFormat) {
        DocumentFormat.Txt -> {
            val text = extractPdfText(source, markdown = false)
            target.writeText(text)
            null
        }
        DocumentFormat.Markdown -> {
            val text = extractPdfText(source, markdown = true)
            target.writeText(text)
            null
        }
        DocumentFormat.Html -> {
            val text = extractPdfText(source, markdown = false)
            target.writeText(htmlDocument(title = source.nameWithoutExtension, body = text))
            null
        }
        DocumentFormat.Epub -> {
            val text = extractPdfText(source, markdown = false)
            EpubBuilder.write(source.nameWithoutExtension, "", text, target)
            null
        }
        DocumentFormat.Docx, DocumentFormat.Pptx, DocumentFormat.Xlsx -> {
            // Rebuilt from extracted text: the layout is regenerated, so these
            // are the "approximate" conversions the UI flags.
            val text = extractPdfText(source, markdown = false)
            writeRebuiltOffice(text, targetFormat, target, title = source.nameWithoutExtension)
            null
        }
        DocumentFormat.Csv -> {
            val text = extractPdfText(source, markdown = false)
            val csv = text.lineSequence()
                .filter { it.isNotBlank() }
                .joinToString("\n") { "\"" + it.replace("\"", "\"\"") + "\"" }
            target.writeText(csv)
            null
        }
        DocumentFormat.Jpg, DocumentFormat.Png -> renderPdfPagesToImages(source, target, targetFormat)
        DocumentFormat.Pdf -> null
    }

    private fun writeTextTarget(
        text: String,
        targetFormat: DocumentFormat,
        target: File,
        title: String
    ): Int? = when (targetFormat) {
        DocumentFormat.Txt -> {
            target.writeText(text)
            null
        }
        DocumentFormat.Markdown -> {
            target.writeText("# $title\n\n$text")
            null
        }
        DocumentFormat.Html -> {
            target.writeText(htmlDocument(title, text))
            null
        }
        DocumentFormat.Epub -> {
            EpubBuilder.write(title, "", text, target)
            null
        }
        DocumentFormat.Pdf -> {
            PdfWriter.writeTextPdf(PdfTools.layout(text, MAX_CHARS_PER_LINE), target)
            countPdfPages(target)
        }
        DocumentFormat.Docx, DocumentFormat.Pptx, DocumentFormat.Xlsx -> {
            writeRebuiltOffice(text, targetFormat, target, title)
            null
        }
        DocumentFormat.Csv -> {
            target.writeText(
                text.lineSequence().filter { it.isNotBlank() }
                    .joinToString("\n") { "\"" + it.replace("\"", "\"\"") + "\"" }
            )
            null
        }
        DocumentFormat.Jpg, DocumentFormat.Png -> null
    }

    private fun readTextSource(source: File, format: DocumentFormat): String {
        val raw = source.readText()
        return when (format) {
            DocumentFormat.Html -> PdfTools.htmlToPlainText(raw)
            DocumentFormat.Markdown -> PdfTools.markdownToPlainText(raw)
            else -> raw
        }
    }

    private fun extractPdfText(source: File, markdown: Boolean): String =
        PDDocument.load(source).use { document ->
            val stripper = PDFTextStripper()
            val pageCount = document.numberOfPages
            buildString {
                for (pageIndex in 0 until pageCount) {
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    val pageText = stripper.getText(document).trimEnd()
                    if (markdown) {
                        append("## Page ").append(pageIndex + 1).append("\n\n")
                    } else {
                        append("--- Page ").append(pageIndex + 1).append(" ---\n")
                    }
                    if (pageText.isNotEmpty()) {
                        append(pageText).append('\n')
                    }
                    append('\n')
                }
            }.trim()
        }

    /**
     * Renders each PDF page to a PNG/JPG next to [firstTarget]. Multi-page
     * sources produce numbered siblings; the first page keeps the requested
     * name so a single-page document behaves as expected.
     */
    private fun renderPdfPagesToImages(
        source: File,
        firstTarget: File,
        targetFormat: DocumentFormat
    ): Int {
        val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        descriptor.use {
            PdfRenderer(it).use { renderer ->
                val pageCount = renderer.pageCount
                for (pageIndex in 0 until pageCount) {
                    renderer.openPage(pageIndex).use { page ->
                        val bitmap = Bitmap.createBitmap(
                            page.width * RENDER_SCALE,
                            page.height * RENDER_SCALE,
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        val output = if (pageIndex == 0) {
                            firstTarget
                        } else {
                            File(
                                firstTarget.parentFile,
                                "${firstTarget.nameWithoutExtension}-${pageIndex + 1}.${targetFormat.extension}"
                            )
                        }
                        output.outputStream().use { stream ->
                            if (targetFormat == DocumentFormat.Png) {
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            } else {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                            }
                        }
                        bitmap.recycle()
                    }
                }
                return pageCount
            }
        }
    }

    /**
     * Writes a minimal Office Open XML package holding the extracted text, so an
     * approximate conversion still opens in Word, PowerPoint or Excel rather
     * than producing a renamed text file.
     */
    private fun writeRebuiltOffice(
        text: String,
        targetFormat: DocumentFormat,
        target: File,
        title: String
    ) {
        val documentXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")
            text.lineSequence().forEach { line ->
                append("<w:p><w:r><w:t xml:space=\"preserve\">")
                append(escapeXml(line))
                append("</w:t></w:r></w:p>")
            }
            append("</w:body></w:document>")
        }
        java.util.zip.ZipOutputStream(target.outputStream().buffered()).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypesXml(targetFormat))
            put("_rels/.rels", rootRelsXml(targetFormat))
            put(officeMainPart(targetFormat), documentXml)
        }
    }

    private fun officeMainPart(format: DocumentFormat): String = when (format) {
        DocumentFormat.Pptx -> "ppt/slides/slide1.xml"
        DocumentFormat.Xlsx -> "xl/worksheets/sheet1.xml"
        else -> "word/document.xml"
    }

    private fun contentTypesXml(format: DocumentFormat): String {
        val mainType = when (format) {
            DocumentFormat.Pptx -> "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"
            DocumentFormat.Xlsx -> "application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"
            else -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
        }
        val mainPart = "/" + officeMainPart(format)
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"$mainPart\" ContentType=\"$mainType\"/></Types>"
    }

    private fun rootRelsXml(format: DocumentFormat): String {
        val mainPart = officeMainPart(format)
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\"" +
            " Target=\"$mainPart\"/></Relationships>"
    }

    private fun htmlDocument(title: String, body: String): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
        append("<title>").append(escapeXml(title)).append("</title></head><body>")
        body.lineSequence().forEach { line ->
            if (line.isBlank()) {
                append("<p></p>")
            } else {
                append("<p>").append(escapeXml(line)).append("</p>")
            }
        }
        append("</body></html>")
    }

    private fun escapeXml(text: String): String = buildString(text.length) {
        text.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    private fun countPdfPages(file: File): Int =
        PDDocument.load(file).use { it.numberOfPages }

    private const val MAX_CHARS_PER_LINE = 90
    private const val RENDER_SCALE = 2
}
