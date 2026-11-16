package org.zenconverter.app.audio

import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads the JSON that the Whisper native runtime returns into [TranscriptSegment]s.
 *
 * Kept separate from the JNI call so the parsing is exercised on the JVM without
 * a model or a device. The expected shape is:
 *
 * ```
 * { "segments": [ { "start": 0.0, "end": 2.5, "text": " hello world",
 *                   "words": [ { "word": "hello", "start": 0.0, "end": 1.0 } ] } ] }
 * ```
 *
 * Unknown or malformed entries are skipped rather than throwing, because a
 * partially decoded transcript is still useful for cut-by-transcript editing.
 */
object WhisperTranscriptParser {

    fun parse(json: String?): List<TranscriptSegment> {
        if (json.isNullOrBlank()) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val segments = root.optJSONArray("segments") ?: return emptyList()
        return buildList {
            for (index in 0 until segments.length()) {
                val segment = segments.optJSONObject(index) ?: continue
                val start = segment.optDouble("start", Double.NaN)
                val end = segment.optDouble("end", Double.NaN)
                if (start.isNaN() || end.isNaN() || end < start) continue
                add(
                    TranscriptSegment(
                        startSeconds = start,
                        endSeconds = end,
                        text = segment.optString("text", "").trim(),
                        words = parseWords(segment.optJSONArray("words"))
                    )
                )
            }
        }
    }

    /** Flattens [segments] into the plain text used for subtitles and search. */
    fun toPlainText(segments: List<TranscriptSegment>): String =
        segments.joinToString(" ") { it.text }.trim()

    private fun parseWords(array: JSONArray?): List<TranscribedWord> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val word = array.optJSONObject(index) ?: continue
                val start = word.optDouble("start", Double.NaN)
                val end = word.optDouble("end", Double.NaN)
                val text = word.optString("word", "").ifBlank { word.optString("text", "") }
                if (start.isNaN() || end.isNaN() || end < start || text.isBlank()) continue
                add(TranscribedWord(text = text.trim(), startSeconds = start, endSeconds = end))
            }
        }
    }
}
