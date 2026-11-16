package org.zenconverter.app.conversion

/**
 * Maps the requested [AudioBitDepth] and the target codec onto an FFmpeg
 * `-sample_fmt` / encoder choice. Pure so the mapping can be unit tested
 * without FFmpeg.
 */
object AudioBitDepthResolver {

    /** FFmpeg `-sample_fmt` value for a lossless integer encoder, or null for Auto. */
    fun sampleFormat(bitDepth: AudioBitDepth): String? {
        return when (bitDepth) {
            AudioBitDepth.Auto -> null
            AudioBitDepth.Bit16 -> "s16"
            AudioBitDepth.Bit24 -> "s32"
            AudioBitDepth.Bit32 -> "s32"
        }
    }

    /** WAV encoder name for the requested depth; WAV has no single generic encoder. */
    fun wavEncoder(bitDepth: AudioBitDepth): String {
        return when (bitDepth) {
            AudioBitDepth.Bit16 -> "pcm_s16le"
            AudioBitDepth.Bit24 -> "pcm_s24le"
            AudioBitDepth.Bit32 -> "pcm_s32le"
            AudioBitDepth.Auto -> "pcm_s16le"
        }
    }

    /**
     * FLAC stores at most 24 bits; a 32-bit request is clamped rather than
     * failing the encode. Returns the `-bits_per_raw_sample` value, or null for
     * Auto (leave the source depth untouched).
     */
    fun flacBitsPerRawSample(bitDepth: AudioBitDepth): Int? {
        return when (bitDepth) {
            AudioBitDepth.Auto -> null
            AudioBitDepth.Bit16 -> 16
            AudioBitDepth.Bit24 -> 24
            AudioBitDepth.Bit32 -> 24
        }
    }

    /** ALAC supports 16/20/24/32; anything non-Auto passes through as-is. */
    fun alacSampleFormat(bitDepth: AudioBitDepth): String? = sampleFormat(bitDepth)
}
