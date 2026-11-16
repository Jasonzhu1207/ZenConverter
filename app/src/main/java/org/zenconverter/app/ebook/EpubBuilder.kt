package org.zenconverter.app.ebook

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Builds and reads EPUB 3 files with only the JDK, so it works offline and is
 * unit-testable. An EPUB is a ZIP with a fixed layout: a `mimetype` entry stored
 * first and uncompressed, a container descriptor, an OPF package and one or
 * more XHTML chapters.
 */
object EpubBuilder {

    /** Escapes text for safe inclusion in XML/XHTML content. */
    fun escapeXml(text: String): String = buildString(text.length) {
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

    /** Splits plain text into paragraphs for XHTML, dropping blank runs. */
    fun paragraphs(text: String): List<String> =
        text.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
            .map { it.trim().replace('\n', ' ') }
            .filter { it.isNotEmpty() }

    /**
     * Writes an EPUB with a single chapter titled [title]. [body] is treated as
     * plain text; paragraphs are split on blank lines and XML-escaped.
     */
    fun write(title: String, author: String, body: String, target: File) {
        val safeTitle = if (title.isBlank()) "Untitled" else title
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            // The mimetype entry must be first and stored uncompressed.
            val mimetype = ZipEntry("mimetype")
            mimetype.method = ZipEntry.STORED
            val mimetypeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
            mimetype.size = mimetypeBytes.size.toLong()
            mimetype.compressedSize = mimetypeBytes.size.toLong()
            mimetype.crc = crc32(mimetypeBytes)
            zip.putNextEntry(mimetype)
            zip.write(mimetypeBytes)
            zip.closeEntry()

            put(zip, "META-INF/container.xml", containerXml())
            put(zip, "OEBPS/content.opf", packageOpf(safeTitle, author))
            put(zip, "OEBPS/nav.xhtml", navXhtml(safeTitle))
            put(zip, "OEBPS/chapter1.xhtml", chapterXhtml(safeTitle, body))
        }
    }

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun crc32(bytes: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(bytes)
        return crc.value
    }

    private fun containerXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    private fun packageOpf(title: String, author: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="bookid">urn:uuid:${java.util.UUID.nameUUIDFromBytes(title.toByteArray())}</dc:identifier>
            <dc:title>${escapeXml(title)}</dc:title>
            <dc:language>en</dc:language>
            <dc:creator>${escapeXml(author.ifBlank { "ZenConverter" })}</dc:creator>
          </metadata>
          <manifest>
            <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="chapter1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine>
            <itemref idref="chapter1"/>
          </spine>
        </package>
    """.trimIndent()

    private fun navXhtml(title: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
        <head><title>${escapeXml(title)}</title></head>
        <body>
        <nav epub:type="toc"><ol><li><a href="chapter1.xhtml">${escapeXml(title)}</a></li></ol></nav>
        </body>
        </html>
    """.trimIndent()

    private fun chapterXhtml(title: String, body: String): String {
        val bodyHtml = paragraphs(body).joinToString("\n") { "<p>${escapeXml(it)}</p>" }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>${escapeXml(title)}</title></head>
            <body>
            <h1>${escapeXml(title)}</h1>
            $bodyHtml
            </body>
            </html>
        """.trimIndent()
    }
}

/**
 * Reads an EPUB back to plain text by pulling the readable content out of its
 * XHTML chapters. Enough for a "EPUB to text" conversion without a full parser.
 */
object EpubReader {
    fun toPlainText(epub: File): String {
        ZipFile(epub).use { zip ->
            val chapterEntries = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".xhtml", ignoreCase = true) }
                .sortedBy { it.name }
                .toList()
            return chapterEntries.joinToString("\n\n") { entry ->
                val html = zip.getInputStream(entry).bufferedReader().readText()
                org.zenconverter.app.pdf.PdfTools.htmlToPlainText(html)
            }.trim()
        }
    }
}
