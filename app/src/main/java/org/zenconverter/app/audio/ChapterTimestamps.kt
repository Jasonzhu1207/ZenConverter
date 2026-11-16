package org.zenconverter.app.audio

/**
 * Parses user-entered chapter start times for the chapter splitter. Accepts one
 * timestamp per line in `seconds`, `mm:ss` or `hh:mm:ss[.mmm]` form, which is
 * how most players and podcast apps display chapter marks. Pure Kotlin so the
 * parsing rules can be unit tested on the JVM.
 */
object ChapterTimestamps {

    data class Parsed(
        val starts: List<Double>,
        val rejectedLines: List<String>
    ) {
        val isEmpty: Boolean get() = starts.isEmpty()
    }

    fun parse(text: String): Parsed {
        val starts = mutableListOf<Double>()
        val rejected = mutableListOf<String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val seconds = parseOne(line)
            if (seconds == null) rejected += line else starts += seconds
        }
        return Parsed(starts.distinct().sorted(), rejected)
    }

    /** Parses a single timestamp, returning `null` when the line is not one. */
    fun parseOne(line: String): Double? {
        val normalised = line.replace(',', '.').trim()
        val parts = normalised.split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()
            2 -> {
                val minutes = parts[0].toDoubleOrNull() ?: return null
                val secs = parts[1].toDoubleOrNull() ?: return null
                if (secs >= 60.0) return null
                minutes * 60.0 + secs
            }
            3 -> {
                val hours = parts[0].toDoubleOrNull() ?: return null
                val minutes = parts[1].toDoubleOrNull() ?: return null
                val secs = parts[2].toDoubleOrNull() ?: return null
                if (minutes >= 60.0 || secs >= 60.0) return null
                hours * 3600.0 + minutes * 60.0 + secs
            }
            else -> null
        } ?: return null
        return seconds.takeIf { it >= 0.0 && it.isFinite() }
    }

    /** Formats seconds back into `hh:mm:ss.mmm`, used for the preview list. */
    fun format(seconds: Double): String {
        val total = seconds.coerceAtLeast(0.0)
        val hours = (total / 3600).toInt()
        val minutes = ((total % 3600) / 60).toInt()
        val secs = total % 60
        return if (hours > 0) {
            "%d:%02d:%06.3f".format(hours, minutes, secs)
        } else {
            "%d:%06.3f".format(minutes, secs)
        }
    }
}
