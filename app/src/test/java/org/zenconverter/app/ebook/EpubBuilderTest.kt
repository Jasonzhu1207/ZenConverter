package org.zenconverter.app.ebook

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubBuilderTest {
    private fun tempFile(): File = File.createTempFile("zen-epub", ".epub").apply { delete() }

    @Test
    fun writesMimetypeFirstAndUncompressed() {
        val target = tempFile()
        try {
            EpubBuilder.write("My Book", "Ada", "First paragraph.\n\nSecond paragraph.", target)
            ZipFile(target).use { zip ->
                val first = zip.entries().asSequence().first()
                assertEquals("mimetype", first.name)
                assertEquals(java.util.zip.ZipEntry.STORED, first.method)
                assertEquals(
                    "application/epub+zip",
                    zip.getInputStream(first).bufferedReader().readText()
                )
            }
        } finally {
            target.delete()
        }
    }

    @Test
    fun roundTripsTextThroughReader() {
        val target = tempFile()
        try {
            EpubBuilder.write("Round Trip", "", "Hello world.\n\nSecond line.", target)
            val text = EpubReader.toPlainText(target)
            assertTrue(text.contains("Hello world."))
            assertTrue(text.contains("Second line."))
        } finally {
            target.delete()
        }
    }

    @Test
    fun escapesMarkupInBody() {
        val target = tempFile()
        try {
            EpubBuilder.write("Escaped", "", "5 < 6 & 7 > 4", target)
            ZipFile(target).use { zip ->
                val chapter = zip.getEntry("OEBPS/chapter1.xhtml")
                val html = zip.getInputStream(chapter).bufferedReader().readText()
                assertTrue(html.contains("5 &lt; 6 &amp; 7 &gt; 4"))
            }
        } finally {
            target.delete()
        }
    }
}
