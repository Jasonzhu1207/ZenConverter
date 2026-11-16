package org.zenconverter.app.audio

import java.util.Locale

/**
 * Non-destructive edit list for the waveform editor. The original file is never
 * rewritten; instead the editor keeps an ordered list of time ranges that will
 * be concatenated on export. Cut, copy, paste and split are all list
 * operations, so the model is pure Kotlin and unit testable on the JVM.
 *
 * Ranges are half-open `[startSeconds, endSeconds)` and always reference the
 * original recording's timeline, which keeps every operation trivially
 * reversible and avoids cumulative rounding drift.
 */
data class AudioSegment(val startSeconds: Double, val endSeconds: Double) {
    val durationSeconds: Double get() = (endSeconds - startSeconds).coerceAtLeast(0.0)
    val isEmpty: Boolean get() = durationSeconds <= MIN_DURATION

    companion object {
        const val EPSILON = 1e-6
        const val MIN_DURATION = 1e-3
    }
}

object AudioEditList {

    /**
     * Removes `[fromSeconds, toSeconds)` from [segments], splitting any range
     * that straddles a boundary. This is the "cut" and "delete selection" op.
     */
    fun cut(segments: List<AudioSegment>, fromSeconds: Double, toSeconds: Double): List<AudioSegment> {
        val from = minOf(fromSeconds, toSeconds)
        val to = maxOf(fromSeconds, toSeconds)
        if (to - from <= AudioSegment.MIN_DURATION) return segments
        return normalized(segments.flatMap { segment ->
            when {
                to <= segment.startSeconds || from >= segment.endSeconds -> listOf(segment)
                from <= segment.startSeconds && to >= segment.endSeconds -> emptyList()
                from <= segment.startSeconds -> listOf(AudioSegment(to, segment.endSeconds))
                to >= segment.endSeconds -> listOf(AudioSegment(segment.startSeconds, from))
                else -> listOf(
                    AudioSegment(segment.startSeconds, from),
                    AudioSegment(to, segment.endSeconds)
                )
            }
        })
    }

    /**
     * Returns the ranges covering `[fromSeconds, toSeconds)` in source time.
     * Used to fill the clipboard before a paste.
     */
    fun extract(segments: List<AudioSegment>, fromSeconds: Double, toSeconds: Double): List<AudioSegment> {
        val from = minOf(fromSeconds, toSeconds)
        val to = maxOf(fromSeconds, toSeconds)
        if (to - from <= AudioSegment.MIN_DURATION) return emptyList()
        return normalized(segments.mapNotNull { segment ->
            val start = maxOf(from, segment.startSeconds)
            val end = minOf(to, segment.endSeconds)
            if (end - start <= AudioSegment.MIN_DURATION) null else AudioSegment(start, end)
        })
    }

    /**
     * Inserts [pasted] (source-time ranges) so that the clip starts playing at
     * [atSeconds] on the edited timeline. The paste does not overwrite the
     * existing audio; it is spliced in, matching the usual "paste inserts"
     * behaviour.
     */
    fun paste(
        segments: List<AudioSegment>,
        pasted: List<AudioSegment>,
        atSeconds: Double
    ): List<AudioSegment> {
        if (pasted.isEmpty()) return segments
        val timeline = buildList {
            var cursor = 0.0
            segments.forEach { segment ->
                add(cursor to segment)
                cursor += segment.durationSeconds
            }
        }
        val clamped = atSeconds.coerceIn(0.0, timeline.lastOrNull()?.first?.plus(timeline.last().second.durationSeconds) ?: 0.0)
        val index = timeline.indexOfFirst { it.first >= clamped - AudioSegment.EPSILON }
            .let { if (it == -1) segments.size else it }
        return normalized(segments.take(index) + pasted + segments.drop(index))
    }

    /** Splits the range containing [atSeconds] into two segments at that point. */
    fun splitAt(segments: List<AudioSegment>, atSeconds: Double): List<AudioSegment> {
        val timeline = mutableListOf<AudioSegment>()
        var cursor = 0.0
        for (segment in segments) {
            val local = atSeconds - cursor
            if (local > AudioSegment.MIN_DURATION && local < segment.durationSeconds - AudioSegment.MIN_DURATION) {
                val splitPoint = segment.startSeconds + local
                timeline += AudioSegment(segment.startSeconds, splitPoint)
                timeline += AudioSegment(splitPoint, segment.endSeconds)
            } else {
                timeline += segment
            }
            cursor += segment.durationSeconds
        }
        return normalized(timeline)
    }

    /**
     * Drops empty ranges while preserving the sequence order. Order is
     * meaningful: a paste appends a clip, and sorting or merging the result
     * would silently discard it when its source range overlaps an existing one.
     */
    fun normalized(segments: List<AudioSegment>): List<AudioSegment> =
        segments.map { AudioSegment(minOf(it.startSeconds, it.endSeconds), maxOf(it.startSeconds, it.endSeconds)) }
            .filterNot { it.isEmpty }

    /** Total edited duration in seconds. */
    fun totalDuration(segments: List<AudioSegment>): Double =
        segments.sumOf { it.durationSeconds }

    /** Maps a point on the edited timeline back to a point in the original file. */
    fun toSourceTime(segments: List<AudioSegment>, editedSeconds: Double): Double {
        var cursor = 0.0
        for (segment in segments) {
            val next = cursor + segment.durationSeconds
            if (editedSeconds <= next) {
                return segment.startSeconds + (editedSeconds - cursor).coerceAtLeast(0.0)
            }
            cursor = next
        }
        return segments.lastOrNull()?.endSeconds ?: 0.0
    }

    /**
     * Builds the FFmpeg `-filter_complex` value that concatenates [segments]
     * from input `0`. A single contiguous segment is emitted as a plain `atrim`
     * so the common trim case stays cheap.
     */
    fun ffmpegFilter(segments: List<AudioSegment>): String? {
        val ranges = normalized(segments)
        if (ranges.isEmpty()) return null
        if (ranges.size == 1) {
            val only = ranges.first()
            return "[0:a]atrim=start=${seconds(only.startSeconds)}:end=${seconds(only.endSeconds)}," +
                "asetpts=PTS-STARTPTS[out]"
        }
        val parts = ranges.mapIndexed { index, range ->
            "[0:a]atrim=start=${seconds(range.startSeconds)}:end=${seconds(range.endSeconds)}," +
                "asetpts=PTS-STARTPTS[s$index]"
        }
        val inputs = ranges.indices.joinToString("") { "[s$it]" }
        return parts.joinToString(";") + ";${inputs}concat=n=${ranges.size}:v=0:a=1[out]"
    }

    private fun seconds(value: Double): String =
        String.format(Locale.US, "%.6f", value.coerceAtLeast(0.0))
}
