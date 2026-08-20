package org.zenconverter.app.subtitle

import java.util.Locale

/**
 * Pure-Kotlin SubRip (.srt) parser and serializer.
 *
 * SRT is the canonical interchange format for the subtitle lane: LRC is
 * parsed/serialized here and bridged through SRT so the FFmpeg compatibility
 * path only ever sees SRT/VTT/ASS.
 */
object SrtCodec {

    private val TIMESTAMP_REGEX = Regex(
        """(\d{1,2}):(\d{1,2}):(\d{1,2})[,.](\d{1,3})"""
    )

    fun parse(text: String): SubtitleDocument {
        val blocks = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val cues = mutableListOf<SubtitleCue>()
        for (block in blocks) {
            val lines = block.lineSequence()
                .map { it.trimEnd('\r') }
                .filter { it.isNotBlank() }
                .toList()
            if (lines.isEmpty()) continue

            val timingIndex = lines.indexOfFirst { it.contains("-->") }
            if (timingIndex < 0) continue

            val timingLine = lines[timingIndex]
            val match = TIMESTAMP_REGEX.findAll(timingLine).toList()
            if (match.size < 2) continue

            val startMs = srtTimestampToMs(match[0])
            val endMs = srtTimestampToMs(match[1])
            val textLines = lines.drop(timingIndex + 1)
            if (textLines.isEmpty()) continue

            cues.add(
                SubtitleCue(
                    startMs = startMs,
                    endMs = endMs,
                    text = textLines.joinToString("\n").trim()
                )
            )
        }

        if (cues.isEmpty()) error("Could not parse subtitle file (SRT)")
        return SubtitleDocument(cues = sortAndDedupe(cues))
    }

    fun write(document: SubtitleDocument): String {
        val cues = document.cues
        val builder = StringBuilder()
        cues.forEachIndexed { index, cue ->
            val start = cue.startMs.coerceAtLeast(0L)
            val end = cue.endMs
                ?: cues.getOrNull(index + 1)?.startMs
                ?: (start + DEFAULT_CUE_DURATION_MS)
            builder.append(index + 1)
            builder.append('\n')
            builder.append(msToSrtTimestamp(start))
            builder.append(" --> ")
            builder.append(msToSrtTimestamp(end.coerceAtLeast(start)))
            builder.append('\n')
            builder.append(cue.text.trim())
            builder.append("\n\n")
        }
        return builder.toString()
    }

    private fun srtTimestampToMs(match: MatchResult): Long {
        val hours = match.groupValues[1].toLong()
        val minutes = match.groupValues[2].toLong()
        val seconds = match.groupValues[3].toLong()
        val fraction = match.groupValues[4].padEnd(3, '0').take(3).toLong()
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + fraction
    }

    private fun msToSrtTimestamp(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val hours = total / 3_600_000L
        val minutes = (total % 3_600_000L) / 60_000L
        val seconds = (total % 60_000L) / 1_000L
        val millis = total % 1_000L
        return String.format(
            Locale.US,
            "%02d:%02d:%02d,%03d",
            hours,
            minutes,
            seconds,
            millis
        )
    }

    private fun sortAndDedupe(cues: List<SubtitleCue>): List<SubtitleCue> {
        return cues
            .sortedBy { it.startMs }
            .distinctBy { it.startMs to (it.endMs ?: -1L) to it.text }
    }

    private const val DEFAULT_CUE_DURATION_MS = 2_000L
}
