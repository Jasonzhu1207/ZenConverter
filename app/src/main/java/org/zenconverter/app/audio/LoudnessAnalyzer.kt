package org.zenconverter.app.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * An ITU-R BS.1770 loudness meter, implemented in pure Kotlin so the numbers a
 * master is judged on are the same numbers a streaming platform would measure.
 *
 * It computes:
 * - integrated loudness in LUFS (gated, 400 ms blocks, 75% overlap);
 * - short-term loudness range (LRA) from 3 s blocks;
 * - true peak in dBTP (4x oversampled, catching inter-sample peaks a plain
 *   sample-peak check misses);
 * - sample peak and crest factor, which together show how compressed a track is.
 *
 * The K-weighting filter coefficients are derived for the actual sample rate,
 * so the meter is correct at 44.1 kHz and 48 kHz alike. No Android types are
 * used, which keeps the meter testable on the JVM.
 */
object LoudnessAnalyzer {

    /** Absolute gate: blocks quieter than this are ignored entirely. */
    private const val ABSOLUTE_GATE_LUFS = -70.0

    /** Relative gate: blocks more than this far below the mean are ignored. */
    private const val RELATIVE_GATE_LU = -10.0

    /** Short-term gate used only for the loudness range. */
    private const val LRA_RELATIVE_GATE_LU = -20.0

    private const val BLOCK_SECONDS = 0.400
    private const val BLOCK_STEP_SECONDS = 0.100
    private const val SHORT_TERM_SECONDS = 3.0
    private const val SHORT_TERM_STEP_SECONDS = 1.0

    /** Oversampling factor for the true-peak estimate. */
    private const val TRUE_PEAK_OVERSAMPLE = 4

    data class Measurement(
        val inputI: Double,
        val inputTp: Double,
        val inputLra: Double,
        val inputThresh: Double,
        val targetOffset: Double,
        val samplePeakDb: Double,
        val crestFactorDb: Double
    )

    /**
     * Measures interleaved [samples] (values in -1..1) with [channelCount]
     * channels. Returns null when there is not enough signal to gate.
     */
    fun measure(samples: FloatArray, sampleRateHz: Int, channelCount: Int): Measurement? {
        if (sampleRateHz <= 0 || channelCount <= 0) return null
        val frameCount = samples.size / channelCount
        if (frameCount == 0) return null

        val weighted = kWeight(samples, sampleRateHz, channelCount)
        val blockMs = (BLOCK_SECONDS * sampleRateHz).toInt()
        val stepMs = (BLOCK_STEP_SECONDS * sampleRateHz).toInt().coerceAtLeast(1)
        if (blockMs <= 0 || frameCount < blockMs) return null

        val channelCountF = channelCount
        val blocks = ArrayList<Double>()
        var start = 0
        while (start + blockMs <= frameCount) {
            var sum = 0.0
            for (channel in 0 until channelCount) {
                var channelSum = 0.0
                var index = start * channelCount + channel
                val end = index + blockMs * channelCount
                while (index < end) {
                    val value = weighted[index].toDouble()
                    channelSum += value * value
                    index += channelCount
                }
                sum += channelSum / blockMs
            }
            blocks.add(sum)
            start += stepMs
        }
        if (blocks.isEmpty()) return null

        val blockLoudness = blocks.map { -0.691 + 10.0 * log10(it + 1e-12) }

        // Absolute gate first, then the relative gate derived from what passed.
        val aboveAbsolute = blockLoudness.indices.filter { blockLoudness[it] >= ABSOLUTE_GATE_LUFS }
        if (aboveAbsolute.isEmpty()) return null
        val absoluteMeanPower = aboveAbsolute.map { blocks[it] }.average()
        val absoluteMeanLoudness = -0.691 + 10.0 * log10(absoluteMeanPower + 1e-12)
        val relativeGate = absoluteMeanLoudness + RELATIVE_GATE_LU
        val gated = aboveAbsolute.filter { blockLoudness[it] >= relativeGate }
        if (gated.isEmpty()) return null
        val integratedPower = gated.map { blocks[it] }.average()
        val integratedLufs = -0.691 + 10.0 * log10(integratedPower + 1e-12)

        val lra = loudnessRange(samples, sampleRateHz, channelCount)
        val samplePeak = samplePeak(samples)
        val truePeak = truePeakDb(samples, channelCount)
        val samplePeakDb = if (samplePeak > 0f) 20.0 * log10(samplePeak.toDouble()) else -120.0
        val crest = (samplePeakDb - rmsDb(samples)).coerceAtLeast(0.0)

        return Measurement(
            inputI = round2(integratedLufs),
            inputTp = round2(truePeak),
            inputLra = round2(lra),
            inputThresh = round2(relativeGate),
            targetOffset = 0.0,
            samplePeakDb = round2(samplePeakDb),
            crestFactorDb = round2(crest)
        )
    }

    /** Builds the radio-ready report a target expects from a raw measurement. */
    fun reportFor(measurement: Measurement, target: MasteringTarget): MasteringModel.Report =
        MasteringModel.Report(
            integratedLufs = measurement.inputI,
            truePeakDb = measurement.inputTp,
            samplePeakDb = measurement.samplePeakDb,
            loudnessRangeLu = measurement.inputLra,
            crestFactorDb = measurement.crestFactorDb,
            target = target
        )

    /**
     * Loudness range: the spread between the 10th and 95th percentile of gated
     * 3 s short-term loudness. A wide range means quiet and loud passages sit far
     * apart; a narrow range means the track is already squashed flat.
     */
    private fun loudnessRange(samples: FloatArray, sampleRateHz: Int, channelCount: Int): Double {
        val windowMs = (SHORT_TERM_SECONDS * sampleRateHz).toInt()
        val stepMs = (SHORT_TERM_STEP_SECONDS * sampleRateHz).toInt().coerceAtLeast(1)
        val frameCount = samples.size / channelCount
        if (windowMs <= 0 || frameCount < windowMs) return 0.0
        val weighted = kWeight(samples, sampleRateHz, channelCount)
        val loudness = ArrayList<Double>()
        var start = 0
        while (start + windowMs <= frameCount) {
            var sum = 0.0
            for (channel in 0 until channelCount) {
                var channelSum = 0.0
                var index = start * channelCount + channel
                val end = index + windowMs * channelCount
                while (index < end) {
                    val value = weighted[index].toDouble()
                    channelSum += value * value
                    index += channelCount
                }
                sum += channelSum / windowMs
            }
            loudness.add(-0.691 + 10.0 * log10(sum + 1e-12))
            start += stepMs
        }
        if (loudness.isEmpty()) return 0.0
        val aboveAbsolute = loudness.filter { it >= ABSOLUTE_GATE_LUFS }
        if (aboveAbsolute.isEmpty()) return 0.0
        val mean = aboveAbsolute.average()
        val gated = aboveAbsolute.filter { it >= mean + LRA_RELATIVE_GATE_LU }.sorted()
        if (gated.size < 2) return 0.0
        return percentile(gated, 0.95) - percentile(gated, 0.10)
    }

    private fun percentile(sorted: List<Double>, fraction: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val position = fraction * (sorted.size - 1)
        val lower = position.toInt().coerceIn(0, sorted.size - 1)
        val upper = (lower + 1).coerceAtMost(sorted.size - 1)
        val weight = position - lower
        return sorted[lower] * (1 - weight) + sorted[upper] * weight
    }

    /**
     * Applies the two-stage K-weighting filter (a high shelf then a high-pass).
     * Channel weights are 1.0 for mono and stereo; surround channels would use
     * 1.41 but the studio only ever decodes mono or stereo.
     */
    private fun kWeight(samples: FloatArray, sampleRateHz: Int, channelCount: Int): FloatArray {
        val (shelf, highPass) = coefficients(sampleRateHz)
        val output = FloatArray(samples.size)
        // Filter each channel with its own state so the channels stay independent.
        for (channel in 0 until channelCount) {
            var shelfX1 = 0.0; var shelfX2 = 0.0; var shelfY1 = 0.0; var shelfY2 = 0.0
            var hpX1 = 0.0; var hpX2 = 0.0; var hpY1 = 0.0; var hpY2 = 0.0
            var index = channel
            while (index < samples.size) {
                val input = samples[index].toDouble()
                val shelfOut = shelf.b0 * input + shelf.b1 * shelfX1 + shelf.b2 * shelfX2 -
                    shelf.a1 * shelfY1 - shelf.a2 * shelfY2
                shelfX2 = shelfX1; shelfX1 = input
                shelfY2 = shelfY1; shelfY1 = shelfOut
                val hpOut = highPass.b0 * shelfOut + highPass.b1 * hpX1 + highPass.b2 * hpX2 -
                    highPass.a1 * hpY1 - highPass.a2 * hpY2
                hpX2 = hpX1; hpX1 = shelfOut
                hpY2 = hpY1; hpY1 = hpOut
                output[index] = hpOut.toFloat()
                index += channelCount
            }
        }
        return output
    }

    private data class Biquad(
        val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double
    )

    private fun coefficients(sampleRateHz: Int): Pair<Biquad, Biquad> {
        val fs = sampleRateHz.toDouble()

        val shelfF0 = 1681.974450955533
        val shelfGain = 3.999843853973347
        val shelfQ = 0.7071752369554196
        val shelfK = tan(Math.PI * shelfF0 / fs)
        val vh = 10.0.pow(shelfGain / 20.0)
        val vb = vh.pow(0.4996667741545416)
        val shelfA0 = 1.0 + shelfK / shelfQ + shelfK * shelfK
        val shelf = Biquad(
            b0 = (vh + vb * shelfK / shelfQ + shelfK * shelfK) / shelfA0,
            b1 = 2.0 * (shelfK * shelfK - vh) / shelfA0,
            b2 = (vh - vb * shelfK / shelfQ + shelfK * shelfK) / shelfA0,
            a1 = 2.0 * (shelfK * shelfK - 1.0) / shelfA0,
            a2 = (1.0 - shelfK / shelfQ + shelfK * shelfK) / shelfA0
        )

        val hpF0 = 38.13547087602444
        val hpQ = 0.5003270373238773
        val hpK = tan(Math.PI * hpF0 / fs)
        val hpA0 = 1.0 + hpK / hpQ + hpK * hpK
        val highPass = Biquad(
            b0 = 1.0,
            b1 = -2.0,
            b2 = 1.0,
            a1 = 2.0 * (hpK * hpK - 1.0) / hpA0,
            a2 = (1.0 - hpK / hpQ + hpK * hpK) / hpA0
        )
        return shelf to highPass
    }

    private fun samplePeak(samples: FloatArray): Float {
        var peak = 0f
        for (value in samples) {
            val magnitude = abs(value)
            if (magnitude > peak) peak = magnitude
        }
        return peak
    }

    private fun rmsDb(samples: FloatArray): Double {
        if (samples.isEmpty()) return -120.0
        var sum = 0.0
        for (value in samples) sum += value.toDouble() * value.toDouble()
        val rms = sqrt(sum / samples.size)
        return if (rms > 0.0) 20.0 * log10(rms) else -120.0
    }

    /**
     * Inter-sample true peak via 4x cubic (Catmull-Rom) interpolation. This
     * catches the overshoots a real DAC's reconstruction filter would produce,
     * which is exactly what a "not clipping" check has to catch.
     */
    private fun truePeakDb(samples: FloatArray, channelCount: Int): Double {
        if (samples.isEmpty()) return -120.0
        val frameCount = samples.size / channelCount
        var peak = 0.0
        for (channel in 0 until channelCount) {
            var frame = 0
            while (frame < frameCount) {
                val y1 = samples[frame * channelCount + channel].toDouble()
                if (abs(y1) > peak) peak = abs(y1)
                frame += 1
            }
            // Oversample each segment between neighbours.
            for (frame in 0 until frameCount - 1) {
                val y0 = samples[(frame - 1).coerceAtLeast(0) * channelCount + channel].toDouble()
                val y1 = samples[frame * channelCount + channel].toDouble()
                val y2 = samples[(frame + 1).coerceAtMost(frameCount - 1) * channelCount + channel].toDouble()
                val y3 = samples[(frame + 2).coerceAtMost(frameCount - 1) * channelCount + channel].toDouble()
                for (step in 1 until TRUE_PEAK_OVERSAMPLE) {
                    val t = step.toDouble() / TRUE_PEAK_OVERSAMPLE
                    val value = catmullRom(y0, y1, y2, y3, t)
                    if (abs(value) > peak) peak = abs(value)
                }
            }
        }
        return if (peak > 0.0) 20.0 * log10(peak) else -120.0
    }

    private fun catmullRom(y0: Double, y1: Double, y2: Double, y3: Double, t: Double): Double {
        val a0 = -0.5 * y0 + 1.5 * y1 - 1.5 * y2 + 0.5 * y3
        val a1 = y0 - 2.5 * y1 + 2.0 * y2 - 0.5 * y3
        val a2 = -0.5 * y0 + 0.5 * y2
        val a3 = y1
        return ((a0 * t + a1) * t + a2) * t + a3
    }

    private fun round2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
}
