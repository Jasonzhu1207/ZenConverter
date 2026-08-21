package org.zenconverter.app.subtitle

import java.util.Locale

/**
 * Pure-Kotlin LRC lyrics parser and serializer.
 *
 * FFmpeg has no LRC demuxer/muxer, so LRC is handled entirely here and bridged
 * through SRT for the FFmpeg-compatible directions. Parser behavior:
 *
 * - timestamp form `[mm:ss.xx]`, `[mm:ss.xxx]`, and `[mm:ss]`;
 * - multiple timestamps per line expand to one cue each;
 * - metadata tags `[ti:] [ar:] [al:] [by:]` are captured (title is kept);
 * - `[offset:...]` (milliseconds, signed) is applied to all timestamps;
 * - timestamp-less lines are ignored.
 */
object LrcCodec {

    private val METADATA_REGEX = Regex("""^\[(ti|ar|al|by):(.*)]$""", RegexOption.IGNORE_CASE)
    private val OFFSET_REGEX = Regex("""^\[offset:([+-]?\d+)]$""", RegexOption.IGNORE_CASE)
    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val ASS_TAG_REGEX = Regex("""\{[^}]*\}""")

    fun parse(text: String): SubtitleDocument {
        val normalized = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        var title: String? = null
        var offsetMs = 0L
        val cues = mutableListOf<SubtitleCue>()

        for (rawLine in normalized.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            METADATA_REGEX.matchEntire(line)?.let { match ->
                val key = match.groupValues[1].lowercase(Locale.US)
                val value = match.groupValues[2].trim()
                if (key == "ti" && title == null && value.isNotEmpty()) {
                    title = value
                }
                continue
            }

            OFFSET_REGEX.matchEntire(line)?.let { match ->
                offsetMs = match.groupValues[1].toLongOrNull() ?: 0L
                continue
            }

            val timestamps = TIMESTAMP_REGEX.findAll(line).toList()
            if (timestamps.isEmpty()) continue

            val lyric = TIMESTAMP_REGEX.replace(line, "").trim()
            if (lyric.isEmpty()) continue

            for (match in timestamps) {
                val startMs = lrcTimestampToMs(match) + offsetMs
                cues.add(SubtitleCue(startMs = startMs, endMs = null, text = lyric))
            }
        }

        if (cues.isEmpty()) error("Could not parse lyrics file (LRC)")
        val sorted = cues.sortedBy { it.startMs }
        return SubtitleDocument(title = title, cues = sorted)
    }

    fun write(document: SubtitleDocument): String {
        val builder = StringBuilder()
        document.title?.takeIf { it.isNotBlank() }?.let { title ->
            builder.append("[ti:")
            builder.append(title)
            builder.append("]\n")
        }
        for (cue in document.cues) {
            builder.append(msToLrcTimestamp(cue.startMs))
            builder.append(singleLine(cue.text))
            builder.append('\n')
        }
        return builder.toString()
    }

    private fun lrcTimestampToMs(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLong()
        val seconds = match.groupValues[2].toLong()
        val fractionRaw = match.groupValues[3]
        val fractionMs = if (fractionRaw.isEmpty()) {
            0L
        } else {
            when (fractionRaw.length) {
                1 -> fractionRaw.toLong() * 100L
                2 -> fractionRaw.toLong() * 10L
                else -> fractionRaw.take(3).padEnd(3, '0').toLong()
            }
        }
        return minutes * 60_000L + seconds * 1_000L + fractionMs
    }

    private fun msToLrcTimestamp(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val minutes = total / 60_000L
        val seconds = (total % 60_000L) / 1_000L
        val centis = (total % 1_000L) / 10L
        return String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, centis)
    }

    private fun singleLine(text: String): String {
        return text
            .replace(HTML_TAG_REGEX, "")
            .replace(ASS_TAG_REGEX, "")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .trim()
    }
}
