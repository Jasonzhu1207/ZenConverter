package org.zenconverter.app.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenconverter.app.audio.TranscriptSegment

class LyricStudioTest {

    @Test
    fun `importLrc parses timestamps and text`() {
        val document = LyricStudio.importLrc(
            """
            [ti:Midnight Drive]
            [00:01.00]City lights
            [00:03.50]are bleeding
            """.trimIndent()
        )
        assertEquals("Midnight Drive", document.title)
        assertEquals(2, document.lines.size)
        assertEquals(1000L, document.lines[0].startMs)
        assertEquals("City lights", document.lines[0].text)
        assertEquals(3500L, document.lines[1].startMs)
    }

    @Test
    fun `importLrc on garbage yields empty document`() {
        val document = LyricStudio.importLrc("not a lyric file at all")
        assertTrue(document.isEmpty)
    }

    @Test
    fun `fromTranscript converts seconds to milliseconds and sorts`() {
        val document = LyricStudio.fromTranscript(
            listOf(
                TranscriptSegment(2.5, 3.0, "second"),
                TranscriptSegment(1.0, 1.5, "first"),
                TranscriptSegment(4.0, 4.2, "   ")
            )
        )
        assertEquals(2, document.lines.size)
        assertEquals(1000L, document.lines[0].startMs)
        assertEquals("first", document.lines[0].text)
        assertEquals(2500L, document.lines[1].startMs)
    }

    @Test
    fun `shift nudges all lines and clamps at zero`() {
        val document = LyricStudio.Document(
            title = null,
            lines = listOf(LyricStudio.Line(200L, "a"), LyricStudio.Line(5000L, "b"))
        )
        val shifted = LyricStudio.shift(document, -500L)
        assertEquals(0L, shifted.lines[0].startMs)
        assertEquals(4500L, shifted.lines[1].startMs)
    }

    @Test
    fun `toLrc round trips through importLrc`() {
        val document = LyricStudio.Document(
            title = "Round Trip",
            lines = listOf(LyricStudio.Line(0L, "one"), LyricStudio.Line(12_340L, "two"))
        )
        val restored = LyricStudio.importLrc(LyricStudio.toLrc(document))
        assertEquals("Round Trip", restored.title)
        assertEquals(document.lines, restored.lines)
    }

    @Test
    fun `activeIndex follows the playhead`() {
        val lines = listOf(
            LyricStudio.Line(1000L, "a"),
            LyricStudio.Line(2000L, "b"),
            LyricStudio.Line(3000L, "c")
        )
        assertEquals(-1, LyricStudio.activeIndex(lines, 500L))
        assertEquals(0, LyricStudio.activeIndex(lines, 1500L))
        assertEquals(2, LyricStudio.activeIndex(lines, 9999L))
    }

    @Test
    fun `formatTimecode renders minutes seconds centiseconds`() {
        assertEquals("0:01.00", LyricStudio.formatTimecode(1000L))
        assertEquals("1:03.40", LyricStudio.formatTimecode(63_400L))
    }
}
