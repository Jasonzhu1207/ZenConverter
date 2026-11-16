package org.zenconverter.app.studio

import org.junit.Assert.assertEquals
import org.junit.Test

class HubLayoutTest {

    @Test
    fun emptySectionHasNoRows() {
        assertEquals(emptyList<Int>(), HubLayout.rowSizes(0))
    }

    @Test
    fun singleTileFillsOneRow() {
        assertEquals(listOf(1), HubLayout.rowSizes(1))
    }

    @Test
    fun sixTilesFillTheLeadingRowExactly() {
        assertEquals(listOf(6), HubLayout.rowSizes(6))
    }

    @Test
    fun sevenTilesSpillOneIntoTheNextRow() {
        assertEquals(listOf(6, 1), HubLayout.rowSizes(7))
    }

    @Test
    fun rowsDescendSixFiveFour() {
        assertEquals(listOf(6, 5), HubLayout.rowSizes(11))
        assertEquals(listOf(6, 5, 4), HubLayout.rowSizes(15))
    }

    @Test
    fun everyRowSizeIsPreservedForLargeSections() {
        val sizes = HubLayout.rowSizes(20)
        assertEquals(listOf(6, 5, 4, 3, 2), sizes)
        assertEquals(20, sizes.sum())
    }

    @Test
    fun rowsNeverExceedTheMaxRowSize() {
        (1..40).forEach { count ->
            val sizes = HubLayout.rowSizes(count)
            assertEquals(count, sizes.sum())
            assert(sizes.all { it <= HubLayout.MAX_ROW }) { "row too wide for $count: $sizes" }
            assertEquals(sizes.sortedDescending(), sizes)
        }
    }

    @Test
    fun chunksFollowTheDescendingPreviewOrder() {
        assertEquals(listOf(6, 5, 1), HubLayout.rowSizes(12))
        assertEquals(listOf(6, 5, 2), HubLayout.rowSizes(13))
    }

    @Test
    fun tilesBeyondTheDescendingRunAreStillPlaced() {
        // 6+5+4+3+2+1 = 21; anything past that continues in single-tile chunks
        // rather than being dropped.
        assertEquals(listOf(6, 5, 4, 3, 2, 1), HubLayout.rowSizes(21))
        assertEquals(listOf(6, 5, 4, 3, 2, 1, 1), HubLayout.rowSizes(22))
        assertEquals(30, HubLayout.rowSizes(30).sum())
    }
}
