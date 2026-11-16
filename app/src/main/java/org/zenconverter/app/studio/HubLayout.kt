package org.zenconverter.app.studio

/**
 * Chunk sizes for the studio hub grid. Each section fills one full-width card
 * first, then square tiles split into chunks of 6, 5, 4, 3, 2, 1. Each chunk is
 * laid out in a two- or three-column grid, so a smaller trailing chunk reads as
 * a deliberately lighter row rather than an unfinished one.
 */
object HubLayout {
    fun rowSizes(tileCount: Int): List<Int> {
        if (tileCount <= 0) return emptyList()
        val rows = mutableListOf<Int>()
        var remaining = tileCount
        var size = MAX_ROW
        while (remaining > 0) {
            val take = minOf(remaining, size)
            rows += take
            remaining -= take
            size = maxOf(1, size - 1)
        }
        return rows
    }

    const val MAX_ROW = 6
}
