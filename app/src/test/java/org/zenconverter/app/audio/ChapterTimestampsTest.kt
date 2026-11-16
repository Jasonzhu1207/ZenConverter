package org.zenconverter.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterTimestampsTest {

    @Test
    fun parsesPlainSeconds() {
        assertEquals(12.5, ChapterTimestamps.parseOne("12.5")!!, 1e-9)
    }

    @Test
    fun parsesMinutesAndSeconds() {
        assertEquals(95.0, ChapterTimestamps.parseOne("1:35")!!, 1e-9)
    }

    @Test
    fun parsesHoursMinutesAndSeconds() {
        assertEquals(3723.0, ChapterTimestamps.parseOne("1:02:03")!!, 1e-9)
    }

    @Test
    fun acceptsACommaAsTheDecimalSeparator() {
        assertEquals(12.5, ChapterTimestamps.parseOne("12,5")!!, 1e-9)
    }

    @Test
    fun rejectsMinutesOrSecondsOverSixty() {
        assertNull(ChapterTimestamps.parseOne("1:75"))
        assertNull(ChapterTimestamps.parseOne("1:75:00"))
    }

    @Test
    fun rejectsNegativeAndGarbageInput() {
        assertNull(ChapterTimestamps.parseOne("-3"))
        assertNull(ChapterTimestamps.parseOne("chapter one"))
    }

    @Test
    fun parseSortsDeduplicatesAndReportsRejectedLines() {
        val result = ChapterTimestamps.parse("30\n0:10\n30\nnot a time\n")
        assertEquals(listOf(10.0, 30.0), result.starts)
        assertEquals(listOf("not a time"), result.rejectedLines)
    }

    @Test
    fun parseIgnoresBlankLines() {
        assertEquals(listOf(5.0), ChapterTimestamps.parse("\n\n5\n   \n").starts)
    }

    @Test
    fun formatRoundTripsThroughParse() {
        val formatted = ChapterTimestamps.format(95.5)
        assertEquals(95.5, ChapterTimestamps.parseOne(formatted)!!, 1e-6)
    }

    @Test
    fun formatUsesHoursOnlyWhenNeeded() {
        assertEquals("0:05.000", ChapterTimestamps.format(5.0))
        assertEquals("1:05:00.000", ChapterTimestamps.format(3900.0))
    }
}
