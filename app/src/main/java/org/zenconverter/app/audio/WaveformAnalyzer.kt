package org.zenconverter.app.audio

import kotlin.math.abs
import kotlin.math.max

/**
 * Down-samples PCM samples into peak buckets for waveform rendering. Pure
 * Kotlin so the peak math can be unit tested without an Android device.
 */
object WaveformAnalyzer {
    const val MIN_BUCKETS = 64
    const val MAX_BUCKETS = 8192

    data class Peaks(
        val minima: FloatArray,
        val maxima: FloatArray,
        val sampleCount: Long,
        val sampleRateHz: Int,
        val channelCount: Int
    ) {
        val bucketCount: Int get() = maxima.size

        val durationSeconds: Double
            get() = if (sampleRateHz > 0) sampleCount.toDouble() / sampleRateHz else 0.0

        /** Largest absolute amplitude across every bucket, used for vertical scaling. */
        val peakAmplitude: Float
            get() {
                var peak = 0f
                for (index in maxima.indices) {
                    peak = max(peak, abs(minima[index]))
                    peak = max(peak, abs(maxima[index]))
                }
                return peak
            }

        fun isSilent(threshold: Float = 0.005f): Boolean = peakAmplitude <= threshold
    }

    /**
     * Buckets interleaved [samples] (values in -1..1) into [bucketCount] buckets,
     * recording the minimum and maximum per bucket.
     */
    fun peaksFromSamples(
        samples: FloatArray,
        channelCount: Int,
        sampleRateHz: Int,
        bucketCount: Int = 1024
    ): Peaks {
        require(channelCount >= 1) { "channelCount must be >= 1" }
        val buckets = bucketCount.coerceIn(MIN_BUCKETS, MAX_BUCKETS)
        val frameCount = samples.size / channelCount
        val minima = FloatArray(buckets)
        val maxima = FloatArray(buckets)
        if (frameCount == 0) {
            return Peaks(minima, maxima, 0, sampleRateHz, channelCount)
        }
        for (bucket in 0 until buckets) {
            val start = (bucket.toLong() * frameCount / buckets).toInt()
            val end = ((bucket + 1L) * frameCount / buckets).toInt().coerceAtLeast(start + 1)
            var minValue = Float.MAX_VALUE
            var maxValue = -Float.MAX_VALUE
            for (frame in start until end.coerceAtMost(frameCount)) {
                var mixed = 0f
                val base = frame * channelCount
                for (channel in 0 until channelCount) {
                    mixed += samples[base + channel]
                }
                mixed /= channelCount
                if (mixed < minValue) minValue = mixed
                if (mixed > maxValue) maxValue = mixed
            }
            if (minValue == Float.MAX_VALUE) {
                minValue = 0f
                maxValue = 0f
            }
            minima[bucket] = minValue
            maxima[bucket] = maxValue
        }
        return Peaks(minima, maxima, frameCount.toLong(), sampleRateHz, channelCount)
    }

    /** Interleaved 16-bit little-endian PCM to normalized floats. */
    fun samplesFromPcm16(pcm: ByteArray, channelCount: Int): FloatArray {
        val sampleCount = pcm.size / 2
        val samples = FloatArray(sampleCount)
        var index = 0
        while (index < sampleCount) {
            val low = pcm[index * 2].toInt() and 0xFF
            val high = pcm[index * 2 + 1].toInt()
            val value = (high shl 8) or low
            samples[index] = (value.toShort().toInt() / 32768f)
            index += 1
        }
        return samples
    }
}
