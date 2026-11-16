package org.zenconverter.app.audio

import java.util.Locale

/**
 * The loudness target a master is aimed at. Each target carries the three
 * numbers that matter for delivery: integrated loudness (LUFS), the true-peak
 * ceiling (dBTP) and the loudness range (LU) the limiter is allowed to work
 * over. These are the values streaming platforms and broadcast actually check,
 * so "radio-ready" means the master lands on the target with headroom to spare.
 */
enum class MasteringTarget(
    val labelKey: String,
    val lufs: Double,
    val truePeakDb: Double,
    val loudnessRangeLu: Double
) {
    Streaming("Streaming", -14.0, -1.0, 7.0),
    Club("Club & loud", -8.0, -0.3, 5.0),
    Podcast("Podcast & voice", -16.0, -1.5, 9.0),
    AppleDigital("Apple Digital", -16.0, -1.0, 8.0);

    /** Streaming-style targets leave the most headroom, so they clip least. */
    val headroomDb: Double get() = -truePeakDb
}

/** How a band shapes the spectrum. */
enum class MasteringBandKind { HighPass, Bell }

/**
 * The six bands the auto-master EQ starts from. They are ordered low to high so
 * the generated filter chain reads the way an engineer would say it: roll off
 * the rumble, trim the body, cut the mud, warm the low mids, lift presence and
 * add air. Every band is editable, so the defaults are a starting point rather
 * than a fixed curve.
 */
enum class MasteringBand(
    val labelKey: String,
    val defaultHz: Double,
    val defaultGainDb: Double,
    val defaultQ: Double,
    val kind: MasteringBandKind
) {
    HighPass("High-pass", 30.0, 0.0, 0.7, MasteringBandKind.HighPass),
    Body("Body", 180.0, -1.2, 0.9, MasteringBandKind.Bell),
    MudCut("Mud cut", 320.0, -2.0, 1.1, MasteringBandKind.Bell),
    Warmth("Warmth", 900.0, 0.8, 0.8, MasteringBandKind.Bell),
    Presence("Presence", 3200.0, 1.6, 0.9, MasteringBandKind.Bell),
    Air("Air", 9500.0, 1.1, 0.7, MasteringBandKind.Bell)
}

/** One editable EQ band: on/off, frequency, gain and Q. */
data class EqBand(
    val band: MasteringBand,
    val enabled: Boolean = true,
    val hz: Double = band.defaultHz,
    val gainDb: Double = band.defaultGainDb,
    val q: Double = band.defaultQ
) {
    companion object {
        /** The default six-band curve, used for a fresh auto-master. */
        fun defaultCurve(): List<EqBand> = MasteringBand.entries.map { EqBand(it) }
    }
}

/**
 * The complete recipe for a mastering pass. It is a plain data class so the
 * whole thing can be serialised, unit tested and shown as an FFmpeg command
 * without touching the device.
 */
data class MasteringPlan(
    val target: MasteringTarget = MasteringTarget.Streaming,
    val bands: List<EqBand> = EqBand.defaultCurve(),
    val compressorRatio: Double = 2.5,
    val adaptiveLimiting: Boolean = true,
    val clipGuard: Boolean = true,
    val deEsser: Boolean = true,
    val exciter: Boolean = true,
    val monoCheck: Boolean = true
) {
    fun band(kind: MasteringBand): EqBand = bands.first { it.band == kind }

    fun withBand(updated: EqBand): MasteringPlan =
        copy(bands = bands.map { if (it.band == updated.band) updated else it })
}

/** A per-stem loudness balance: the measured level and the trim applied to it. */
data class StemBalance(
    val kind: StemKind,
    val measuredLufs: Double,
    val trimDb: Double
) {
    val targetLufs: Double get() = measuredLufs + trimDb
}

/**
 * Pure logic behind the mastering grid: it turns a plan and measured loudness
 * into the FFmpeg filter chain, the copy-ready command, the stem balance and a
 * plain-language verdict. Everything here is Android-free so it can be unit
 * tested on the JVM.
 */
object MasteringModel {
    const val SAMPLE_RATE_HZ = 48_000

    /**
     * The measured properties of a master, used by the radio-ready check.
     * [truePeakDb] is an inter-sample estimate (4x oversampled), so it can read
     * above the sample peak when the waveform would clip after a DAC's
     * reconstruction filter.
     */
    data class Report(
        val integratedLufs: Double,
        val truePeakDb: Double,
        val samplePeakDb: Double,
        val loudnessRangeLu: Double,
        val crestFactorDb: Double,
        val target: MasteringTarget
    ) {
        val loudnessDeltaLu: Double get() = integratedLufs - target.lufs
        val peakOverCeilingDb: Double get() = truePeakDb - target.truePeakDb

        /** True when the master would clip once reconstructed by a player. */
        val clipping: Boolean get() = truePeakDb > target.truePeakDb + 0.1

        /** A crest factor below this means the master is squashed flat. */
        val overCompressed: Boolean get() = crestFactorDb < 6.0

        val loudnessOnTarget: Boolean get() = kotlin.math.abs(loudnessDeltaLu) <= 1.5

        /** True when the master is loud enough, on target and not clipping. */
        val healthy: Boolean
            get() = loudnessOnTarget && !clipping && !overCompressed
    }

    /**
     * Builds the FFmpeg audio filter list for [plan]. Order matters: the tone
     * curve runs first, then dynamics, then the true-peak limiting, so the
     * limiter is the last thing the signal sees and the ceiling is guaranteed.
     */
    fun filtersFor(plan: MasteringPlan, measured: LoudnessAnalyzer.Measurement? = null): List<String> =
        buildList {
            plan.bands.filter { it.enabled }.forEach { band ->
                when (band.band.kind) {
                    MasteringBandKind.HighPass -> add("highpass=f=${hz(band.hz)}")
                    MasteringBandKind.Bell -> add(
                        "equalizer=f=${hz(band.hz)}:t=q:w=${decimal(band.q)}:g=${decimal(band.gainDb)}"
                    )
                }
            }
            if (plan.deEsser) {
                // Split the sibilance band and duck it only when it exceeds the
                // rest of the signal, so harsh "s" sounds soften without a lisp.
                add("deesser=i=0.4:m=0.5:f=0.5:s=o")
            }
            if (plan.exciter) {
                // A touch of harmonic air above 6 kHz, kept low to avoid hiss.
                add("equalizer=f=6500:t=h:w=3500:g=1.5")
            }
            add("acompressor=threshold=${decimal(compressorThreshold(plan.compressorRatio))}:" +
                "ratio=${decimal(plan.compressorRatio)}:attack=20:release=250:makeup=1")
            add(loudnormFilter(plan, measured))
        }

    /**
     * The `loudnorm` filter. With a completed [measured] pass the linear mode
     * gives a transparent result; without one it runs a single dynamic pass.
     */
    fun loudnormFilter(plan: MasteringPlan, measured: LoudnessAnalyzer.Measurement? = null): String {
        val target = plan.target
        val base = "loudnorm=I=${decimal(target.lufs)}:TP=${decimal(target.truePeakDb)}:" +
            "LRA=${decimal(target.loudnessRangeLu)}"
        if (measured == null) return "$base:print_format=summary"
        return "$base:measured_I=${decimal(measured.inputI)}:" +
            "measured_TP=${decimal(measured.inputTp)}:" +
            "measured_LRA=${decimal(measured.inputLra)}:" +
            "measured_thresh=${decimal(measured.inputThresh)}:" +
            "offset=${decimal(measured.targetOffset)}:linear=true:print_format=summary"
    }

    /**
     * A single `-af` value for the whole chain, plus the output codec/bit-depth
     * flags that keep the master lossless where the container allows it.
     */
    fun filterChain(plan: MasteringPlan, measured: LoudnessAnalyzer.Measurement? = null): String =
        filtersFor(plan, measured).joinToString(",")

    /** The human-readable FFmpeg command shown by the command-line view. */
    fun commandFor(
        plan: MasteringPlan,
        inputName: String = "in.wav",
        outputName: String = "out.wav",
        measured: LoudnessAnalyzer.Measurement? = null
    ): String {
        val chain = filterChain(plan, measured)
        return "-i $inputName -af \"$chain\" -c:a pcm_s24le $outputName"
    }

    /**
     * Chooses a per-stem trim so every stem lands on [targetLufs]. Moves are
     * clamped to ±[maxTrimDb] so a stem that is simply quiet or loud is not
     * pushed so far that it changes the mix balance.
     */
    fun balanceStems(
        measured: List<Pair<StemKind, Double>>,
        targetLufs: Double = -18.0,
        maxTrimDb: Double = 6.0
    ): List<StemBalance> = measured.map { (kind, lufs) ->
        val trim = (targetLufs - lufs).coerceIn(-maxTrimDb, maxTrimDb)
        StemBalance(kind = kind, measuredLufs = lufs, trimDb = trim)
    }

    /** Applies [trimDb] as a `volume` filter for a stem's re-render. */
    fun volumeFilter(trimDb: Double): String = "volume=${decimal(trimDb)}dB"

    /** The RMS (crest) shape of a waveform, used for the shape verdict. */
    enum class WaveformShape(val labelKey: String) {
        Thick("Thick & dense"),
        Dynamic("Dynamic & spiky"),
        Sparse("Thin & sparse")
    }

    fun shapeFor(report: Report): WaveformShape = when {
        report.crestFactorDb < 8.0 -> WaveformShape.Thick
        report.crestFactorDb < 14.0 -> WaveformShape.Dynamic
        else -> WaveformShape.Sparse
    }

    /**
     * A short, honest verdict: whether the master is radio-ready and, when it is
     * not, the single most likely reason. The wording stays non-technical so a
     * musician who is not an engineer can act on it.
     */
    enum class Verdict(val labelKey: String) {
        Healthy("Healthy"),
        Clipping("Clipping"),
        TooQuiet("Too quiet"),
        OverCompressed("Over-compressed")
    }

    fun verdictFor(report: Report): Verdict = when {
        report.clipping -> Verdict.Clipping
        report.overCompressed -> Verdict.OverCompressed
        !report.loudnessOnTarget -> Verdict.TooQuiet
        else -> Verdict.Healthy
    }

    /** Compressor threshold in linear gain, derived from the ratio. */
    private fun compressorThreshold(ratio: Double): Double =
        when {
            ratio <= 1.5 -> 0.25
            ratio <= 3.0 -> 0.15
            else -> 0.08
        }

    /** Frequencies are rendered without a trailing ".0" so the command stays clean. */
    private fun hz(value: Double): String = value.toLong().toString()

    private fun decimal(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value)
}
