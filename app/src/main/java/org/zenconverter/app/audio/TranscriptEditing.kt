package org.zenconverter.app.audio

/**
 * Represents a transcribed word with timing, used by cut-by-transcript editing.
 */
data class TranscribedWord(
    val text: String,
    val startSeconds: Double,
    val endSeconds: Double
) {
    val durationSeconds: Double get() = (endSeconds - startSeconds).coerceAtLeast(0.0)
}

data class TranscriptSegment(
    val startSeconds: Double,
    val endSeconds: Double,
    val text: String,
    val words: List<TranscribedWord> = emptyList()
)

/**
 * Turns a transcript plus a set of deleted word indexes into the keep-segments
 * needed to rebuild the audio, and can render the surviving text. Pure Kotlin so
 * the editing math is easy to reason about and test.
 */
object TranscriptEditing {
    data class KeepSegment(val startSeconds: Double, val endSeconds: Double)

    data class EditPlan(
        val keepSegments: List<KeepSegment>,
        val remainingText: String,
        val removedSeconds: Double
    )

    fun plan(segments: List<TranscriptSegment>, deletedWordIndexes: Set<Int>): EditPlan {
        val words = segments.flatMap { it.words }
        if (words.isEmpty()) {
            return EditPlan(emptyList(), segments.joinToString(" ") { it.text }.trim(), 0.0)
        }
        val keepSegments = mutableListOf<KeepSegment>()
        var currentStart: Double? = null
        var currentEnd = 0.0
        var removedSeconds = 0.0
        words.forEachIndexed { index, word ->
            val deleted = index in deletedWordIndexes
            if (deleted) {
                if (currentStart != null) {
                    keepSegments.add(KeepSegment(currentStart!!, currentEnd))
                    currentStart = null
                }
                removedSeconds += word.durationSeconds
            } else {
                if (currentStart == null) currentStart = word.startSeconds
                currentEnd = word.endSeconds
            }
        }
        if (currentStart != null) keepSegments.add(KeepSegment(currentStart!!, currentEnd))
        val remaining = words.filterIndexed { index, _ -> index !in deletedWordIndexes }
            .joinToString(" ") { it.text }
        return EditPlan(mergeAdjacent(keepSegments), remaining, removedSeconds)
    }

    private fun mergeAdjacent(segments: List<KeepSegment>, gapToleranceSeconds: Double = 0.05): List<KeepSegment> {
        if (segments.isEmpty()) return emptyList()
        val merged = mutableListOf<KeepSegment>()
        var current = segments.first()
        for (index in 1 until segments.size) {
            val next = segments[index]
            if (next.startSeconds - current.endSeconds <= gapToleranceSeconds) {
                current = KeepSegment(current.startSeconds, maxOf(current.endSeconds, next.endSeconds))
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }
}
