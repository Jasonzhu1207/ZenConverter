package org.zenconverter.app.studio

import org.zenconverter.app.conversion.AudioExportOptions
import org.zenconverter.app.conversion.ImageExportOptions
import org.zenconverter.app.conversion.VideoExportOptions
import org.zenconverter.app.conversion.VideoCompressionMode
import org.zenconverter.app.conversion.Mp3BitrateMode
import kotlin.math.roundToInt

/**
 * One-tap output presets that fill in sensible codec/bitrate/resolution choices
 * for a destination. Pure Kotlin so the numbers can be reasoned about and
 * tested without a device.
 */
enum class SmartPreset(val labelKey: String, val descriptionKey: String) {
    WhatsApp("WhatsApp-ready", "Small MP4 that fits WhatsApp's sharing limits"),
    EmailUnder25Mb("Email under 25 MB", "Targets a size most mail providers still accept"),
    InstagramReel("Instagram Reel", "1080x1920 vertical MP4 for Reels"),
    Podcast("Podcast", "Mono 44.1 kHz MP3 tuned for speech"),
    Youtube1080p("YouTube 1080p", "1080p H.264 MP4 with a generous bitrate"),
    Ringtone("Ringtone", "Short, small MP3 suitable for a ringtone");

    val isVideo: Boolean
        get() = this == WhatsApp || this == EmailUnder25Mb || this == InstagramReel || this == Youtube1080p
}

data class TargetSizeRequest(
    val targetBytes: Long,
    val audioBitrate: Int = 128_000,
    val overheadFraction: Double = 0.02
)

object BitrateCalculator {
    /** Total stream bitrate that keeps [targetBytes] within [durationSeconds]. */
    fun totalBitrate(targetBytes: Long, durationSeconds: Double): Int? {
        if (targetBytes <= 0 || durationSeconds <= 0.0) return null
        val bits = targetBytes * 8.0
        val bitrate = bits / durationSeconds
        return bitrate.roundToInt().coerceAtLeast(16_000)
    }

    /**
     * Video bitrate that fits [request] once the audio track and a small
     * container overhead are subtracted. Returns null when the target is too
     * small to be meaningful.
     */
    fun videoBitrateFor(
        targetBytes: Long,
        durationSeconds: Double,
        audioBitrate: Int = 128_000,
        overheadFraction: Double = 0.02
    ): Int? {
        val total = totalBitrate(targetBytes, durationSeconds) ?: return null
        val usable = (total * (1.0 - overheadFraction)).roundToInt()
        val video = usable - audioBitrate
        return video.takeIf { it >= 32_000 }
    }

    /** AAC bitrate to use when a preset needs a single audio figure. */
    fun recommendedAudioBitrateForVideo(targetVideoBitrate: Int): Int =
        when {
            targetVideoBitrate >= 4_000_000 -> 192_000
            targetVideoBitrate >= 1_500_000 -> 160_000
            else -> 128_000
        }
}

object SmartPresets {
    fun videoOptions(preset: SmartPreset, durationSeconds: Double?): VideoExportOptions = when (preset) {
        SmartPreset.WhatsApp -> VideoExportOptions(
            maxShortSidePixels = 720,
            videoBitrate = 1_500_000,
            compressionMode = VideoCompressionMode.BalancedShrink
        )
        SmartPreset.EmailUnder25Mb -> {
            val bitrate = durationSeconds
                ?.let { BitrateCalculator.videoBitrateFor(24L * 1024 * 1024, it) }
            VideoExportOptions(
                maxShortSidePixels = 720,
                videoBitrate = bitrate,
                compressionMode = VideoCompressionMode.SmallFile
            )
        }
        SmartPreset.InstagramReel -> VideoExportOptions(
            maxShortSidePixels = 1080,
            videoBitrate = 8_000_000
        )
        SmartPreset.Youtube1080p -> VideoExportOptions(
            maxShortSidePixels = 1080,
            videoBitrate = 12_000_000
        )
        SmartPreset.Podcast, SmartPreset.Ringtone -> VideoExportOptions()
    }

    fun audioOptions(preset: SmartPreset): AudioExportOptions = when (preset) {
        SmartPreset.Podcast -> AudioExportOptions(
            audioBitrate = 96_000,
            sampleRateHz = 44_100,
            channelCount = 1,
            mp3BitrateMode = Mp3BitrateMode.Cbr
        )
        SmartPreset.Ringtone -> AudioExportOptions(
            audioBitrate = 192_000,
            sampleRateHz = 44_100,
            channelCount = 2
        )
        else -> AudioExportOptions(audioBitrate = 160_000)
    }

    fun imageOptions(preset: SmartPreset): ImageExportOptions = ImageExportOptions(quality = 85)
}

/**
 * Predicts the output size and a rough quality band before a job runs, so users
 * do not have to convert twice to find out.
 */
object QualityEstimator {
    enum class QualityBand(val labelKey: String) {
        High("High"),
        Balanced("Balanced"),
        Reduced("Reduced"),
        Low("Low")
    }

    data class Estimate(
        val estimatedBytes: Long?,
        val bitsPerPixel: Double?,
        val quality: QualityBand
    )

    fun estimateVideo(
        durationSeconds: Double?,
        width: Int?,
        height: Int?,
        fps: Double?,
        videoBitrate: Int?
    ): Estimate {
        val estimatedBytes = if (durationSeconds != null && durationSeconds > 0 && videoBitrate != null) {
            (videoBitrate.toLong() * durationSeconds / 8.0).toLong()
        } else {
            null
        }
        val bitsPerPixel = if (
            videoBitrate != null && width != null && height != null &&
            fps != null && fps > 0 && width > 0 && height > 0
        ) {
            videoBitrate.toDouble() / (width.toDouble() * height.toDouble() * fps)
        } else {
            null
        }
        return Estimate(estimatedBytes, bitsPerPixel, qualityForBitsPerPixel(bitsPerPixel))
    }

    fun estimateAudio(durationSeconds: Double?, audioBitrate: Int?): Estimate {
        val estimatedBytes = if (durationSeconds != null && durationSeconds > 0 && audioBitrate != null) {
            (audioBitrate.toLong() * durationSeconds / 8.0).toLong()
        } else {
            null
        }
        val quality = when {
            audioBitrate == null -> QualityBand.Balanced
            audioBitrate >= 192_000 -> QualityBand.High
            audioBitrate >= 128_000 -> QualityBand.Balanced
            audioBitrate >= 96_000 -> QualityBand.Reduced
            else -> QualityBand.Low
        }
        return Estimate(estimatedBytes, null, quality)
    }

    private fun qualityForBitsPerPixel(bitsPerPixel: Double?): QualityBand = when {
        bitsPerPixel == null -> QualityBand.Balanced
        bitsPerPixel >= 0.15 -> QualityBand.High
        bitsPerPixel >= 0.07 -> QualityBand.Balanced
        bitsPerPixel >= 0.035 -> QualityBand.Reduced
        else -> QualityBand.Low
    }
}
