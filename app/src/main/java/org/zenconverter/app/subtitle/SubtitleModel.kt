package org.zenconverter.app.subtitle

import java.util.Locale

/**
 * Text subtitle/lyrics formats supported by the subtitle conversion lane.
 *
 * TXT is intentionally not represented here: the product treats TXT as neither
 * an input nor an output for the lyrics/subtitle converter.
 */
enum class SubtitleFormat(val extension: String) {
    SRT("srt"),
    VTT("vtt"),
    LRC("lrc"),
    ASS("ass");

    companion object {
        fun fromExtension(extension: String): SubtitleFormat? {
            val normalized = extension.trim().lowercase(Locale.US)
            return entries.firstOrNull { it.extension == normalized }
        }
    }
}

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long?,
    val text: String
)

data class SubtitleDocument(
    val title: String? = null,
    val cues: List<SubtitleCue>
)
