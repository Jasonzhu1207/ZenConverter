package org.zenconverter.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEditListTest {

    private val full = listOf(AudioSegment(0.0, 10.0))

    @Test
    fun cutRemovesTheMiddleAndKeepsBothSides() {
        val result = AudioEditList.cut(full, 3.0, 6.0)
        assertEquals(listOf(AudioSegment(0.0, 3.0), AudioSegment(6.0, 10.0)), result)
        assertEquals(7.0, AudioEditList.totalDuration(result), 1e-9)
    }

    @Test
    fun cutAtTheStartTrimsWithoutSplitting() {
        assertEquals(listOf(AudioSegment(2.0, 10.0)), AudioEditList.cut(full, 0.0, 2.0))
    }

    @Test
    fun cutAcrossTheWholeRangeEmptiesTheList() {
        assertTrue(AudioEditList.cut(full, 0.0, 10.0).isEmpty())
    }

    @Test
    fun extractClipsToTheSelection() {
        val split = listOf(AudioSegment(0.0, 4.0), AudioSegment(7.0, 10.0))
        assertEquals(listOf(AudioSegment(2.0, 4.0), AudioSegment(7.0, 9.0)), AudioEditList.extract(split, 2.0, 9.0))
    }

    @Test
    fun pasteInsertsRatherThanOverwrites() {
        val pasted = listOf(AudioSegment(2.0, 4.0))
        val result = AudioEditList.paste(full, pasted, 10.0)
        assertEquals(12.0, AudioEditList.totalDuration(result), 1e-9)
        assertEquals(listOf(AudioSegment(0.0, 10.0), AudioSegment(2.0, 4.0)), result)
    }

    @Test
    fun pasteAtTheStartShiftsEverythingAfter() {
        val result = AudioEditList.paste(full, listOf(AudioSegment(1.0, 2.0)), 0.0)
        assertEquals(listOf(AudioSegment(1.0, 2.0), AudioSegment(0.0, 10.0)), result)
    }

    @Test
    fun splitAtCutsTheContainingSegmentInTwo() {
        val result = AudioEditList.splitAt(full, 4.0)
        assertEquals(listOf(AudioSegment(0.0, 4.0), AudioSegment(4.0, 10.0)), result)
        assertEquals(10.0, AudioEditList.totalDuration(result), 1e-9)
    }

    @Test
    fun splitOutsideEverySegmentLeavesTheListAlone() {
        assertEquals(full, AudioEditList.splitAt(full, 0.0))
    }

    @Test
    fun normaliseDropsEmptyRangesAndKeepsOrder() {
        val mixed = listOf(AudioSegment(5.0, 10.0), AudioSegment(3.0, 3.0), AudioSegment(0.0, 5.0))
        assertEquals(listOf(AudioSegment(5.0, 10.0), AudioSegment(0.0, 5.0)), AudioEditList.normalized(mixed))
    }

    @Test
    fun toSourceTimeWalksTheEditedTimeline() {
        val edited = listOf(AudioSegment(0.0, 3.0), AudioSegment(6.0, 10.0))
        assertEquals(1.0, AudioEditList.toSourceTime(edited, 1.0), 1e-9)
        assertEquals(7.0, AudioEditList.toSourceTime(edited, 4.0), 1e-9)
    }

    @Test
    fun ffmpegFilterUsesTrimForASingleRange() {
        val filter = AudioEditList.ffmpegFilter(listOf(AudioSegment(1.0, 2.5)))
        assertEquals("[0:a]atrim=start=1.000000:end=2.500000,asetpts=PTS-STARTPTS[out]", filter)
    }

    @Test
    fun ffmpegFilterConcatenatesMultipleRanges() {
        val filter = AudioEditList.ffmpegFilter(
            listOf(AudioSegment(0.0, 1.0), AudioSegment(2.0, 3.0))
        )
        assertTrue(filter!!.contains("concat=n=2:v=0:a=1[out]"))
        assertTrue(filter.contains("[0:a]atrim=start=0.000000:end=1.000000"))
        assertTrue(filter.contains("[0:a]atrim=start=2.000000:end=3.000000"))
    }

    @Test
    fun ffmpegFilterIsNullWhenThereIsNothingLeft() {
        assertEquals(null, AudioEditList.ffmpegFilter(emptyList()))
    }
}
