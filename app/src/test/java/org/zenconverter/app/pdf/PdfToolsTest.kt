package org.zenconverter.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfToolsTest {
    @Test
    fun parsePageRangesExpandsRangesAndLists() {
        assertEquals(listOf(0, 1, 2, 4, 7), PdfTools.parsePageRanges("1-3,5,8-", 8))
    }

    @Test
    fun parsePageRangesOpenEndedRunsToLastPage() {
        assertEquals(listOf(2, 3, 4), PdfTools.parsePageRanges("3-", 5))
    }

    @Test
    fun parsePageRangesClampsAndDeduplicates() {
        assertEquals(listOf(0, 1), PdfTools.parsePageRanges("1,1,2,9", 2))
    }

    @Test
    fun parsePageRangesRejectsReversedAndGarbage() {
        assertEquals(emptyList<Int>(), PdfTools.parsePageRanges("5-2", 8))
        assertEquals(emptyList<Int>(), PdfTools.parsePageRanges("abc", 8))
        assertEquals(emptyList<Int>(), PdfTools.parsePageRanges("1-3", 0))
    }

    @Test
    fun layoutWrapsLongLinesAndMarksHeadings() {
        val lines = PdfTools.layout("# Title\n\n" + "word ".repeat(10), 12)
        assertTrue(lines.first().isHeading)
        assertEquals("Title", lines.first().text)
        assertTrue(lines.drop(1).all { it.text.length <= 12 })
    }

    @Test
    fun htmlToPlainTextDropsScriptsAndKeepsText() {
        val html = "<html><head><style>p{color:red}</style></head><body><p>Hello &amp; hi</p><script>bad()</script></body></html>"
        val text = PdfTools.htmlToPlainText(html)
        assertTrue(text.contains("Hello & hi"))
        assertTrue(!text.contains("bad()"))
        assertTrue(!text.contains("color:red"))
    }

    @Test
    fun markdownToPlainTextUnwrapsLinksAndEmphasis() {
        val markdown = "See [docs](https://example.com) and **bold** text"
        assertEquals("See docs and bold text", PdfTools.markdownToPlainText(markdown))
    }
}
