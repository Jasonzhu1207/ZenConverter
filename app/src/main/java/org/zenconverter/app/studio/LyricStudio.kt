package org.zenconverter.app.studio

import org.zenconverter.app.audio.TranscriptSegment
import org.zenconverter.app.subtitle.LrcCodec
import org.zenconverter.app.subtitle.SubtitleCue
import org.zenconverter.app.subtitle.SubtitleDocument

/**
 * Pure-Kotlin timed-lyric model shared by the lyric importer, the transcript
 * to lyrics conversion, the offset shift and the karaoke view. Kept free of
 * Android types so the timing maths is unit-testable on the JVM.
 */
object LyricStudio {

    data class Line(val startMs: Long, val text: String)

    data class Document(val title: String?, val lines: List<Line>) {
        val isEmpty: Boolean get() = lines.isEmpty()
    }

    val EMPTY = Document(title = null, lines = emptyList())

    /** Reads an `.lrc` file body. Blank or unparsable input yields an empty document. */
    fun importLrc(text: String): Document = runCatching {
        fromSubtitleDocument(LrcCodec.parse(text))
    }.getOrElse { EMPTY }

    fun fromSubtitleDocument(document: SubtitleDocument): Document = Document(
        title = document.title,
        lines = document.cues.map { Line(it.startMs, it.text.trim()) }.filter { it.text.isNotEmpty() }
    )

    /** Maps Whisper transcript segments onto timed lyric lines. */
    fun fromTranscript(segments: List<TranscriptSegment>): Document = Document(
        title = null,
        lines = segments
            .filter { it.text.isNotBlank() }
            .sortedBy { it.startSeconds }
            .map { Line((it.startSeconds.coerceAtLeast(0.0) * 1000.0).toLong(), it.text.trim()) }
    )

    /** Serializes to `.lrc`, reusing the shared LRC writer so timings round-trip. */
    fun toLrc(document: Document): String = LrcCodec.write(
        SubtitleDocument(
            title = document.title,
            cues = document.lines.map { SubtitleCue(startMs = it.startMs, endMs = null, text = it.text) }
        )
    )

    /**
     * Nudges every line by [offsetMs] to fix a constant timing offset. A line is
     * never allowed to start before zero, which is what the player can render.
     */
    fun shift(document: Document, offsetMs: Long): Document = document.copy(
        lines = document.lines.map { it.copy(startMs = (it.startMs + offsetMs).coerceAtLeast(0L)) }
    )

    /**
     * Index of the line that should be highlighted at [positionMs], or -1 before
     * the first line. Lines are assumed sorted by start time.
     */
    fun activeIndex(lines: List<Line>, positionMs: Long): Int {
        var active = -1
        for ((index, line) in lines.withIndex()) {
            if (line.startMs <= positionMs) active = index else break
        }
        return active
    }

    /**
     * Splits pasted lyrics into timed lines. When [durationMs] is known the
     * lines are spread evenly across the clip, which is the "auto-spread"
     * starting point the user then nudges line by line.
     */
    fun addLines(text: String, durationMs: Long): Document {
        val rows = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (rows.isEmpty()) return EMPTY
        val spread = if (durationMs > 0L) durationMs / rows.size else DEFAULT_LINE_GAP_MS
        return Document(
            title = null,
            lines = rows.mapIndexed { index, row -> Line(index * spread, row) }
        )
    }

    /** Re-spaces existing lines evenly across [durationMs], keeping the text order. */
    fun autoSpread(document: Document, durationMs: Long): Document {
        if (document.lines.isEmpty() || durationMs <= 0L) return document
        val spread = durationMs / document.lines.size
        return document.copy(lines = document.lines.mapIndexed { index, line -> line.copy(startMs = index * spread) })
    }

    /** Rounds every line to the nearest beat of a [bpm] grid, useful after a nudge. */
    fun snapToBeat(document: Document, bpm: Double): Document {
        if (document.lines.isEmpty() || bpm <= 0.0) return document
        val beatMs = 60_000.0 / bpm
        return document.copy(
            lines = document.lines.map { line ->
                val beats = Math.round(line.startMs / beatMs)
                line.copy(startMs = (beats * beatMs).toLong().coerceAtLeast(0L))
            }
        )
    }

    private const val DEFAULT_LINE_GAP_MS = 4_000L

    fun formatTimecode(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val minutes = total / 60_000L
        val seconds = (total % 60_000L) / 1_000L
        val centis = (total % 1_000L) / 10L
        return String.format(java.util.Locale.US, "%d:%02d.%02d", minutes, seconds, centis)
    }
}
