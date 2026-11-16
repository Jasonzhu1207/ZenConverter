package org.zenconverter.app.audio

import java.util.Locale

/**
 * Which of the four stems a separated output holds.
 */
enum class StemKind(val fileName: String) {
    Vocals("vocals.wav"),
    Drums("drums.wav"),
    Bass("bass.wav"),
    Other("other.wav")
}

/**
 * Builds the FFmpeg filter graph used to split a mix into four stems.
 *
 * This is a spectral and stereo-field approximation, not a neural separation:
 * the low band becomes the bass, a mid band with the centre channel emphasised
 * becomes the vocals, the remaining mid band becomes the drums and the stereo
 * side signal becomes the accompaniment. It runs offline on any device with no
 * model download, which is why it is offered as the default. A model-based split
 * would need a native runtime that this build does not bundle.
 *
 * Pure Kotlin so the graph can be asserted in JVM tests.
 */
object StemFilters {
    const val BASS_LOWPASS_HZ = 180
    const val DRUM_HIGHPASS_HZ = 200
    const val DRUM_LOWPASS_HZ = 9_000
    const val VOCAL_HIGHPASS_HZ = 150
    const val VOCAL_LOWPASS_HZ = 10_000
    const val OTHER_HIGHPASS_HZ = 1_000

    data class StemOutput(val kind: StemKind, val label: String)

    data class Graph(val filterComplex: String, val outputs: List<StemOutput>)

    /**
     * Returns the `-filter_complex` argument plus the ordered stems it produces,
     * so the caller can map each output label to a file.
     */
    fun graph(): Graph {
        val stems = StemKind.entries
        val splitLabels = stems.indices.joinToString("") { "[s$it]" }
        val parts = buildList {
            add("[0:a]aformat=channel_layouts=stereo[st]")
            add("[st]asplit=${stems.size}$splitLabels")
            add("[s0]lowpass=f=$BASS_LOWPASS_HZ[bass]")
            add("[s1]highpass=f=$DRUM_HIGHPASS_HZ,lowpass=f=$DRUM_LOWPASS_HZ[drums]")
            add(
                "[s2]pan=mono|c0=0.5*c0+0.5*c1," +
                    "highpass=f=$VOCAL_HIGHPASS_HZ,lowpass=f=$VOCAL_LOWPASS_HZ[vocals]"
            )
            add("[s3]pan=mono|c0=0.5*c0-0.5*c1,highpass=f=$OTHER_HIGHPASS_HZ[other]")
        }
        return Graph(
            filterComplex = parts.joinToString(";"),
            outputs = listOf(
                StemOutput(StemKind.Bass, "[bass]"),
                StemOutput(StemKind.Drums, "[drums]"),
                StemOutput(StemKind.Vocals, "[vocals]"),
                StemOutput(StemKind.Other, "[other]")
            )
        )
    }
}

/**
 * Renders transcript segments to subtitle text. Pure Kotlin so the timestamp
 * formatting is testable and the exported file always reflects real timings
 * instead of a fixed placeholder range.
 */
object SubtitleExport {
    enum class Format(val extension: String) {
        Srt("srt"),
        Vtt("vtt"),
        Text("txt")
    }

    fun render(segments: List<TranscriptSegment>, format: Format): String = when (format) {
        Format.Text -> segments.joinToString("\n") { it.text.trim() }.trim() + "\n"
        Format.Srt -> renderTimed(segments, srt = true)
        Format.Vtt -> "WEBVTT\n\n" + renderTimed(segments, srt = false)
    }

    private fun renderTimed(segments: List<TranscriptSegment>, srt: Boolean): String {
        val ordered = segments.filter { it.endSeconds > it.startSeconds }.sortedBy { it.startSeconds }
        return ordered.mapIndexed { index, segment ->
            buildString {
                if (srt) append(index + 1).append('\n')
                append(timestamp(segment.startSeconds, srt))
                append(" --> ")
                append(timestamp(segment.endSeconds, srt))
                append('\n')
                append(segment.text.trim())
                append('\n')
            }
        }.joinToString("\n")
    }

    /** SRT uses a comma before milliseconds, WebVTT uses a dot. */
    private fun timestamp(seconds: Double, srt: Boolean): String {
        val totalMillis = (seconds.coerceAtLeast(0.0) * 1000.0).toLong()
        val hours = totalMillis / 3_600_000
        val minutes = (totalMillis % 3_600_000) / 60_000
        val secs = (totalMillis % 60_000) / 1000
        val millis = totalMillis % 1000
        val separator = if (srt) ',' else '.'
        return String.format(Locale.US, "%02d:%02d:%02d%c%03d", hours, minutes, secs, separator, millis)
    }
}
