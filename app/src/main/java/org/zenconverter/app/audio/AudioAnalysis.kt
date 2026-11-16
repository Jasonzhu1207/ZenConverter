package org.zenconverter.app.audio

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Estimates tempo (BPM) and musical key from mono PCM samples. Both are
 * heuristic: tempo comes from an energy-based onset envelope and autocorrelation,
 * key from a chroma profile matched against major/minor templates. Good enough
 * for DJ and remix sorting, and it runs entirely offline.
 */
object AudioAnalysis {
    private const val MIN_BPM = 60.0
    private const val MAX_BPM = 200.0

    data class Result(val bpm: Double?, val key: String?, val confidence: Double)

    private val PITCH_CLASSES = listOf(
        "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    )

    // Krumhansl-Schmuckler key profiles.
    private val MAJOR_PROFILE = doubleArrayOf(
        6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88
    )
    private val MINOR_PROFILE = doubleArrayOf(
        6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17
    )

    fun analyze(samples: FloatArray, sampleRateHz: Int, channelCount: Int): Result {
        val mono = toMono(samples, channelCount)
        if (mono.isEmpty()) return Result(null, null, 0.0)
        val bpm = estimateBpm(mono, sampleRateHz)
        val (key, confidence) = estimateKey(mono, sampleRateHz)
        return Result(bpm, key, confidence)
    }

    private fun toMono(samples: FloatArray, channelCount: Int): FloatArray {
        if (channelCount <= 1) return samples
        val frames = samples.size / channelCount
        val mono = FloatArray(frames)
        for (frame in 0 until frames) {
            var sum = 0f
            for (channel in 0 until channelCount) sum += samples[frame * channelCount + channel]
            mono[frame] = sum / channelCount
        }
        return mono
    }

    fun estimateBpm(mono: FloatArray, sampleRateHz: Int): Double? {
        val hop = 512
        val frameCount = mono.size / hop
        if (frameCount < 8) return null
        val energy = DoubleArray(frameCount)
        for (frame in 0 until frameCount) {
            var sum = 0.0
            val start = frame * hop
            val end = (start + hop).coerceAtMost(mono.size)
            for (index in start until end) sum += mono[index].toDouble() * mono[index].toDouble()
            energy[frame] = sqrt(sum / (end - start).coerceAtLeast(1))
        }
        // Onset strength is the positive half-wave rectified difference.
        val onset = DoubleArray(frameCount)
        for (frame in 1 until frameCount) onset[frame] = (energy[frame] - energy[frame - 1]).coerceAtLeast(0.0)
        val mean = onset.average()
        if (mean <= 1e-9) return null
        for (frame in onset.indices) onset[frame] = (onset[frame] - mean).coerceAtLeast(0.0)
        val framesPerSecond = sampleRateHz.toDouble() / hop
        val minLag = (framesPerSecond * 60.0 / MAX_BPM).toInt().coerceAtLeast(1)
        val maxLag = (framesPerSecond * 60.0 / MIN_BPM).toInt().coerceAtLeast(minLag + 1)
        var bestLag = -1
        var bestScore = 0.0
        for (lag in minLag..maxLag.coerceAtMost(frameCount - 1)) {
            var score = 0.0
            for (frame in lag until frameCount) score += onset[frame] * onset[frame - lag]
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag <= 0 || bestScore <= 0.0) return null
        var bpm = 60.0 * framesPerSecond / bestLag
        while (bpm < MIN_BPM) bpm *= 2
        while (bpm > MAX_BPM) bpm /= 2
        return bpm
    }

    fun estimateKey(mono: FloatArray, sampleRateHz: Int): Pair<String?, Double> {
        val fftSize = 4096
        if (mono.size < fftSize) return null to 0.0
        val chroma = DoubleArray(12)
        val window = FloatArray(fftSize) { index ->
            (0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * index / (fftSize - 1))).toFloat()
        }
        val real = DoubleArray(fftSize)
        val imaginary = DoubleArray(fftSize)
        var offset = 0
        var frames = 0
        while (offset + fftSize <= mono.size && frames < 64) {
            for (index in 0 until fftSize) real[index] = (mono[offset + index] * window[index]).toDouble()
            java.util.Arrays.fill(imaginary, 0.0)
            Fft.transform(real, imaginary)
            val binHz = sampleRateHz.toDouble() / fftSize
            for (bin in 1 until fftSize / 2) {
                val frequency = bin * binHz
                if (frequency < 65.0 || frequency > 2000.0) continue
                val magnitude = sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin])
                val midi = 69 + 12 * (kotlin.math.ln(frequency / 440.0) / kotlin.math.ln(2.0))
                val pitchClass = ((midi.roundToInt() % 12) + 12) % 12
                chroma[pitchClass] += magnitude
            }
            offset += fftSize / 2
            frames += 1
        }
        if (frames == 0) return null to 0.0
        val total = chroma.sum()
        if (total <= 0.0) return null to 0.0
        for (index in chroma.indices) chroma[index] /= total
        var bestKey: String? = null
        var bestScore = -Double.MAX_VALUE
        for (root in 0 until 12) {
            val major = correlation(chroma, MAJOR_PROFILE, root)
            val minor = correlation(chroma, MINOR_PROFILE, root)
            if (major > bestScore) {
                bestScore = major
                bestKey = PITCH_CLASSES[root] + " major"
            }
            if (minor > bestScore) {
                bestScore = minor
                bestKey = PITCH_CLASSES[root] + " minor"
            }
        }
        return bestKey to bestScore
    }

    private fun correlation(chroma: DoubleArray, profile: DoubleArray, root: Int): Double {
        var chromaMean = 0.0
        var profileMean = 0.0
        for (index in 0 until 12) {
            chromaMean += chroma[index]
            profileMean += profile[index]
        }
        chromaMean /= 12
        profileMean /= 12
        var numerator = 0.0
        var chromaVariance = 0.0
        var profileVariance = 0.0
        for (index in 0 until 12) {
            val rotated = profile[(index - root + 12) % 12] - profileMean
            val centered = chroma[index] - chromaMean
            numerator += centered * rotated
            chromaVariance += centered * centered
            profileVariance += rotated * rotated
        }
        val denominator = sqrt(chromaVariance * profileVariance)
        return if (denominator <= 1e-12) 0.0 else numerator / denominator
    }
}
