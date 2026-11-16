package org.zenconverter.app.studio

import org.zenconverter.app.conversion.ConversionMediaCategory

/**
 * Turns basic file facts into a likely next action, so the app can suggest
 * "this is a 4K video, compress it for sharing?" instead of making the user
 * choose from an empty grid. Pure Kotlin and deterministic.
 */
data class FileFacts(
    val displayName: String,
    val sizeBytes: Long?,
    val category: ConversionMediaCategory,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val durationSeconds: Double? = null,
    val bitrate: Int? = null
)

data class SmartSuggestion(
    val id: String,
    val titleKey: String,
    val detailKey: String,
    val preset: SmartPreset? = null,
    val targetFormat: String? = null,
    val privacyScrub: Boolean = false
)

object SmartSuggestions {
    private const val FOUR_K_WIDTH = 3840
    private const val LARGE_VIDEO_BYTES = 100L * 1024 * 1024
    private const val LARGE_AUDIO_BYTES = 40L * 1024 * 1024

    fun suggestionsFor(facts: FileFacts): List<SmartSuggestion> = buildList {
        when (facts.category) {
            ConversionMediaCategory.Video -> {
                val is4k = (facts.widthPx ?: 0) >= FOUR_K_WIDTH || (facts.heightPx ?: 0) >= FOUR_K_WIDTH
                if (is4k) {
                    add(
                        SmartSuggestion(
                            id = "compress-4k",
                            titleKey = "This is a 4K video",
                            detailKey = "Compress it for sharing",
                            preset = SmartPreset.WhatsApp,
                            targetFormat = "MP4"
                        )
                    )
                }
                if ((facts.sizeBytes ?: 0) >= LARGE_VIDEO_BYTES) {
                    add(
                        SmartSuggestion(
                            id = "email-size",
                            titleKey = "Large video",
                            detailKey = "Target an email-friendly size",
                            preset = SmartPreset.EmailUnder25Mb,
                            targetFormat = "MP4"
                        )
                    )
                }
                add(
                    SmartSuggestion(
                        id = "extract-audio",
                        titleKey = "Need just the sound?",
                        detailKey = "Extract the audio track as MP3",
                        targetFormat = "MP3"
                    )
                )
                add(
                    SmartSuggestion(
                        id = "scrub-video",
                        titleKey = "Remove location data",
                        detailKey = "Strip GPS and device metadata",
                        privacyScrub = true
                    )
                )
            }
            ConversionMediaCategory.Audio -> {
                add(
                    SmartSuggestion(
                        id = "podcast-cleanup",
                        titleKey = "Sounds like speech",
                        detailKey = "Clean it up as a podcast",
                        preset = SmartPreset.Podcast
                    )
                )
                if ((facts.sizeBytes ?: 0) >= LARGE_AUDIO_BYTES) {
                    add(
                        SmartSuggestion(
                            id = "smaller-audio",
                            titleKey = "Large audio file",
                            detailKey = "Re-encode to a smaller MP3",
                            targetFormat = "MP3"
                        )
                    )
                }
                add(
                    SmartSuggestion(
                        id = "ringtone",
                        titleKey = "Make it a ringtone",
                        detailKey = "Trim a clip and set it as a ringtone",
                        preset = SmartPreset.Ringtone
                    )
                )
            }
            ConversionMediaCategory.Image -> {
                add(
                    SmartSuggestion(
                        id = "scrub-image",
                        titleKey = "Remove location data",
                        detailKey = "Strip GPS, device and author metadata",
                        privacyScrub = true
                    )
                )
                add(
                    SmartSuggestion(
                        id = "webp-shrink",
                        titleKey = "Shrink for the web",
                        detailKey = "Convert to WebP at quality 85",
                        targetFormat = "WEBP"
                    )
                )
            }
            ConversionMediaCategory.Pdf -> {
                add(
                    SmartSuggestion(
                        id = "compress-pdf",
                        titleKey = "Large PDF",
                        detailKey = "Compress it with the Balanced preset",
                        targetFormat = "pdf_compress"
                    )
                )
                add(
                    SmartSuggestion(
                        id = "scrub-pdf",
                        titleKey = "Remove author metadata",
                        detailKey = "Scrub document metadata",
                        privacyScrub = true
                    )
                )
            }
            ConversionMediaCategory.Document -> {
                add(
                    SmartSuggestion(
                        id = "doc-to-pdf",
                        titleKey = "Convert to PDF",
                        detailKey = "Turn this document into a PDF",
                        targetFormat = "PDF"
                    )
                )
            }
            ConversionMediaCategory.Font -> {
                add(
                    SmartSuggestion(
                        id = "woff2",
                        titleKey = "Optimize for the web",
                        detailKey = "Convert to WOFF2",
                        targetFormat = "WOFF2"
                    )
                )
            }
            ConversionMediaCategory.Subtitle -> {
                add(
                    SmartSuggestion(
                        id = "srt",
                        titleKey = "Standardize subtitles",
                        detailKey = "Convert to SRT",
                        targetFormat = "SRT"
                    )
                )
            }
        }
    }
}
