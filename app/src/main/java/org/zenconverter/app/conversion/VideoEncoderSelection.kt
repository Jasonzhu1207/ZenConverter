package org.zenconverter.app.conversion

/**
 * Chooses the software or platform hardware encoder for a video export, and the
 * rate control that encoder understands.
 *
 * The bundled FFmpeg build ships no MediaCodec encoders (see
 * `docs/decisions/0007-self-built-ffmpegkit-next.md`), so a hardware encoder can
 * only be used once [available] reports it is actually present in the linked
 * binary. Everything here is pure Kotlin so the decision can be asserted in JVM
 * tests; the caller supplies what FFmpeg reported.
 *
 * Hardware encoders take a target bitrate rather than a CRF, so the profile has
 * to switch rate control when it switches codec.
 */
object VideoEncoderSelection {

    /** FFmpeg codec name for the software H.264 encoder. */
    const val SOFTWARE_H264 = "libx264"

    /** FFmpeg codec name for the platform H.264 encoder. */
    const val HARDWARE_H264 = "h264_mediacodec"

    /** FFmpeg codec name for the platform HEVC encoder. */
    const val HARDWARE_H265 = "hevc_mediacodec"

    /** Which encoder family an export asked for. */
    enum class Codec {
        H264,
        H265,
        Vp9,
        Vp8
    }

    /** What the caller wants, before availability is considered. */
    enum class Preference {
        /** Prefer the platform encoder, fall back to software. */
        HardwareWhenAvailable,

        /** Always use the software encoder. */
        Software
    }

    /** The chosen encoder plus the rate control it supports. */
    data class Choice(
        val codec: String,
        val usesTargetBitrate: Boolean
    ) {
        /** True when the platform encoder was selected. */
        val isHardware: Boolean
            get() = usesTargetBitrate
    }

    /**
     * Picks the encoder for [codec], consulting [available] for each candidate in
     * preference order. A `null` from [available] means "unknown", which is
     * treated as unavailable so an unprobed build never selects an encoder it
     * cannot run.
     */
    fun select(
        codec: Codec,
        preference: Preference,
        available: (String) -> Boolean?
    ): Choice {
        val hardware = hardwareCodecFor(codec)
        if (preference == Preference.HardwareWhenAvailable && hardware != null &&
            available(hardware) == true
        ) {
            return Choice(codec = hardware, usesTargetBitrate = true)
        }
        return Choice(codec = softwareCodecFor(codec), usesTargetBitrate = false)
    }

    /** The platform encoder for [codec], or `null` when none exists. */
    fun hardwareCodecFor(codec: Codec): String? = when (codec) {
        Codec.H264 -> HARDWARE_H264
        Codec.H265 -> HARDWARE_H265
        // FFmpeg exposes no VP8/VP9 MediaCodec encoders; those stay software.
        Codec.Vp9, Codec.Vp8 -> null
    }

    /** The software encoder for [codec]. */
    fun softwareCodecFor(codec: Codec): String = when (codec) {
        Codec.H264 -> SOFTWARE_H264
        Codec.H265 -> "libx265"
        Codec.Vp9 -> "libvpx-vp9"
        Codec.Vp8 -> "libvpx"
    }

    /**
     * A conservative bitrate for a hardware encoder, used when the caller has no
     * explicit target. Scaled by resolution and frame rate so a 4K clip is not
     * squeezed into a 720p budget, and clamped so a tiny clip still gets enough
     * bits to avoid visible blocking.
     */
    fun fallbackBitrateFor(
        width: Int?,
        height: Int?,
        frameRate: Float?,
        compressionMode: VideoCompressionMode
    ): Int {
        val pixels = (width ?: 1280).toLong() * (height ?: 720).toLong()
        val fps = (frameRate ?: 30f).takeIf { it > 0f } ?: 30f
        // 0.07 bits per pixel per frame at the balanced point.
        val baseBitsPerSecond = pixels * fps * 0.07
        val modeFactor = when (compressionMode) {
            VideoCompressionMode.VisualLossless -> 2.0
            VideoCompressionMode.Standard -> 1.0
            VideoCompressionMode.BalancedShrink -> 0.7
            VideoCompressionMode.SmallFile -> 0.45
        }
        val target = (baseBitsPerSecond * modeFactor).toLong()
        return target.coerceIn(MIN_HARDWARE_BITRATE, MAX_HARDWARE_BITRATE).toInt()
    }

    private const val MIN_HARDWARE_BITRATE = 500_000L
    private const val MAX_HARDWARE_BITRATE = 40_000_000L
}
