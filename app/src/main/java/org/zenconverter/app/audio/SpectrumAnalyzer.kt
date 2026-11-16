package org.zenconverter.app.audio

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * Analyses the frequency balance of a track so the spectrum screen can draw it
 * and the auto-EQ can propose corrections.
 *
 * The band layout follows the way the ear divides the spectrum (roughly octave
 * bands from 20 Hz to 20 kHz) rather than raw FFT bins, so what the user sees
 * maps onto the EQ bands they can actually move. Everything is pure Kotlin and
 * runs on the JVM, so the balance logic is unit tested without a device.
 */
object SpectrumAnalyzer {

    const val MIN_HZ = 20.0
    const val MAX_HZ = 20_000.0

    /** Classic octave-band centres from 31 Hz to 16 kHz, plus the outer edges. */
    val BAND_CENTERS_HZ = doubleArrayOf(
        31.0, 63.0, 125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0
    )

    /** Band edges are the geometric midpoints between neighbouring centres. */
    val BAND_EDGES_HZ: DoubleArray = run {
        val edges = DoubleArray(BAND_CENTERS_HZ.size + 1)
        edges[0] = MIN_HZ
        edges[edges.size - 1] = MAX_HZ
        for (index in 1 until edges.size - 1) {
            edges[index] = kotlin.math.sqrt(BAND_CENTERS_HZ[index - 1] * BAND_CENTERS_HZ[index])
        }
        edges
    }

    data class Balance(
        /** Per-band level in dB relative to the track's overall average. */
        val bandsDb: DoubleArray,
        /** 0..1 bars for rendering, with the loudest band at 1. */
        val bars: DoubleArray,
        /** Bands that stand well above the average and will sound harsh or boomy. */
        val problemPeakIndices: List<Int>
    ) {
        val isBalanced: Boolean get() = problemPeakIndices.isEmpty()
    }

    /**
     * Reduces [samples] (interleaved, values in -1..1) to a per-band balance.
     * Frames are windowed and transformed, then averaged in the power domain so a
     * single loud transient does not dominate the picture.
     */
    fun analyze(samples: FloatArray, sampleRateHz: Int, channelCount: Int): Balance {
        val mono = toMono(samples, channelCount)
        val fftSize = 4096
        val bands = DoubleArray(BAND_CENTERS_HZ.size)
        val counts = IntArray(BAND_CENTERS_HZ.size)
        if (mono.size < fftSize || sampleRateHz <= 0) return emptyBalance()

        val binHz = sampleRateHz.toDouble() / fftSize
        val frame = DoubleArray(fftSize)
        var offset = 0
        var frames = 0
        while (offset + fftSize <= mono.size && frames < 128) {
            for (index in 0 until fftSize) frame[index] = mono[offset + index].toDouble()
            val magnitudes = Fft.magnitudes(frame, fftSize)
            for (band in BAND_CENTERS_HZ.indices) {
                val low = BAND_EDGES_HZ[band]
                val high = BAND_EDGES_HZ[band + 1]
                var power = 0.0
                var bin = max(1, (low / binHz).toInt())
                val lastBin = minOf(magnitudes.size - 1, (high / binHz).toInt())
                while (bin <= lastBin) {
                    power += magnitudes[bin] * magnitudes[bin]
                    bin += 1
                }
                bands[band] += power
                counts[band] += 1
            }
            offset += fftSize / 2
            frames += 1
        }
        if (frames == 0) return emptyBalance()
        for (band in bands.indices) {
            bands[band] = if (counts[band] > 0) bands[band] / counts[band] else 0.0
        }
        return balanceFromPower(bands)
    }

    /** Shared shaping so the renderer and the auto-EQ agree on what "balanced" means. */
    private fun balanceFromPower(power: DoubleArray): Balance {
        val db = DoubleArray(power.size) { index ->
            if (power[index] > 1e-12) 10.0 * kotlin.math.log10(power[index]) else -120.0
        }
        val audible = db.filter { it > -90.0 }
        val reference = if (audible.isEmpty()) -120.0 else audible.average()
        val relative = DoubleArray(db.size) { db[it] - reference }
        val maxRelative = relative.maxOrNull() ?: 0.0
        val bars = DoubleArray(relative.size) { index ->
            val normalized = if (maxRelative > 0.0) (relative[index] + 30.0) / (maxRelative + 30.0) else 0.0
            normalized.coerceIn(0.0, 1.0)
        }
        // A band more than 6 dB above the average, in the audible range, is a peak.
        val problems = relative.indices.filter { index ->
            relative[index] > 6.0 && BAND_CENTERS_HZ[index] in MIN_HZ..MAX_HZ && bars[index] > 0.55
        }
        return Balance(relative, bars, problems)
    }

    private fun emptyBalance(): Balance =
        Balance(DoubleArray(BAND_CENTERS_HZ.size), DoubleArray(BAND_CENTERS_HZ.size), emptyList())

    /**
     * Suggests EQ gains that flatten the measured balance toward the average:
     * bands above the average get cut, bands below get a small lift, and the
     * result is clamped so auto-EQ never makes an extreme move. The output maps
     * onto the six mastering bands by nearest centre frequency.
     */
    fun autoEqGains(balance: Balance, maxGainDb: Double = 4.0): Map<MasteringBand, Double> {
        return MasteringBand.entries.associateWith { band ->
            if (band.kind == MasteringBandKind.HighPass) {
                0.0
            } else {
                val index = nearestBandIndex(band.defaultHz)
                val relative = balance.bandsDb.getOrNull(index) ?: 0.0
                (-relative * 0.6).coerceIn(-maxGainDb, maxGainDb)
            }
        }
    }

    private fun nearestBandIndex(hz: Double): Int {
        var best = 0
        var bestDistance = Double.MAX_VALUE
        for (index in BAND_CENTERS_HZ.indices) {
            val distance = abs(ln(BAND_CENTERS_HZ[index] / hz))
            if (distance < bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
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

    /** Frequency in Hz for the left edge of a drawn bar, for axis labels. */
    fun labelFor(index: Int): String {
        val hz = BAND_CENTERS_HZ.getOrNull(index) ?: return ""
        return if (hz >= 1000.0) "${(hz / 1000.0).toInt()}k" else hz.toInt().toString()
    }
}
