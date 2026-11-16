package org.zenconverter.app.audio

import java.util.Locale

/**
 * Pure-Kotlin model for the audio effects chain. Everything here is translated
 * into FFmpeg filters by the conversion service, so it stays free of Android
 * and FFmpeg types and can be unit tested on the JVM.
 */
enum class EqualizerPreset(val labelKey: String) {
    Off("Off"),
    Flat("Flat"),
    BassBoost("Bass boost"),
    Podcast("Podcast"),
    Vocal("Vocal"),
    Treble("Treble"),
    Loudness("Loudness");

    /** Ten-band gain profile in dB, low frequency first. */
    val bands: List<Double>
        get() = when (this) {
            Off -> ZERO_BANDS
            Flat -> ZERO_BANDS
            BassBoost -> listOf(6.0, 5.0, 3.5, 2.0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0)
            Podcast -> listOf(-2.0, -1.0, 0.0, 2.0, 3.5, 3.5, 2.5, 1.0, -1.0, -2.0)
            Vocal -> listOf(-3.0, -2.0, -1.0, 1.0, 3.0, 4.0, 3.0, 1.5, 0.0, -1.0)
            Treble -> listOf(-2.0, -1.5, -1.0, 0.0, 0.0, 1.0, 2.5, 4.0, 5.0, 6.0)
            Loudness -> listOf(5.0, 4.0, 2.0, 0.0, -1.0, 0.0, 1.0, 2.5, 4.0, 5.0)
        }

    companion object {
        val ZERO_BANDS = List(EQUALIZER_BAND_COUNT) { 0.0 }
        const val EQUALIZER_BAND_COUNT = 10
        val CENTER_FREQUENCIES_HZ = listOf(
            31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000
        )
    }
}

enum class LoudnessTarget(val labelKey: String, val lufs: Double) {
    Off("Off", 0.0),
    Podcast("Podcast (-16 LUFS)", -16.0),
    Streaming("Streaming (-14 LUFS)", -14.0),
    YouTube("YouTube (-14 LUFS)", -14.0),
    Spotify("Spotify (-14 LUFS)", -14.0),
    Broadcast("Broadcast (-23 LUFS)", -23.0);

    val isEnabled: Boolean get() = this != Off
}

enum class CompressorPreset(val labelKey: String) {
    Off("Off"),
    Gentle("Gentle"),
    Podcast("Podcast"),
    Aggressive("Aggressive"),
    Limiter("Limiter");

    val isEnabled: Boolean get() = this != Off
}

enum class ReverbPreset(val labelKey: String) {
    Off("Off"),
    SmallRoom("Small room"),
    LargeHall("Large hall"),
    Cathedral("Cathedral"),
    Plate("Plate");

    val isEnabled: Boolean get() = this != Off
}

enum class VoiceChangerPreset(val labelKey: String) {
    Off("Off"),
    Child("Child"),
    Deep("Deep"),
    Robot("Robot"),
    Chipmunk("Chipmunk"),
    Monster("Monster");

    val isEnabled: Boolean get() = this != Off
}

enum class SilenceRemovalMode(val labelKey: String) {
    Off("Off"),
    Light("Light"),
    Standard("Standard"),
    Aggressive("Aggressive");

    val isEnabled: Boolean get() = this != Off

    val thresholdDb: Double
        get() = when (this) {
            Off -> 0.0
            Light -> -35.0
            Standard -> -45.0
            Aggressive -> -55.0
        }
}

data class AudioEffects(
    val equalizer: EqualizerPreset = EqualizerPreset.Off,
    val loudnessTarget: LoudnessTarget = LoudnessTarget.Off,
    val compressor: CompressorPreset = CompressorPreset.Off,
    val reverb: ReverbPreset = ReverbPreset.Off,
    val voiceChanger: VoiceChangerPreset = VoiceChangerPreset.Off,
    val silenceRemoval: SilenceRemovalMode = SilenceRemovalMode.Off,
    val deHum: Boolean = false
) {
    val hasEnabledEffects: Boolean
        get() = equalizer != EqualizerPreset.Off ||
            loudnessTarget.isEnabled ||
            compressor.isEnabled ||
            reverb.isEnabled ||
            voiceChanger.isEnabled ||
            silenceRemoval.isEnabled ||
            deHum
}

/**
 * Translates a preset into a list of FFmpeg audio filters. The order matters:
 * the voice changer is applied before tone shaping, and loudness normalization
 * runs last so the measured level reflects everything that came before it.
 */
object AudioEffectFilters {
    private const val SAMPLE_RATE = 48000

    fun filtersFor(effects: AudioEffects, durationMs: Long?): List<String> = buildList {
        if (!effects.hasEnabledEffects) return@buildList
        silenceRemovalFiltersFor(effects.silenceRemoval)?.let { addAll(it) }
        if (effects.deHum) add("highpass=f=80")
        voiceChangerFiltersFor(effects.voiceChanger).let { addAll(it) }
        equalizerFiltersFor(effects.equalizer).let { addAll(it) }
        reverbFiltersFor(effects.reverb)?.let { add(it) }
        compressorFiltersFor(effects.compressor)?.let { add(it) }
        if (effects.loudnessTarget.isEnabled) {
            add(
                "loudnorm=I=${formatNumber(effects.loudnessTarget.lufs)}:" +
                    "TP=-1.5:LRA=11:print_format=summary"
            )
        }
    }

    private fun silenceRemovalFiltersFor(mode: SilenceRemovalMode): List<String>? {
        if (!mode.isEnabled) return null
        val threshold = formatNumber(mode.thresholdDb) + "dB"
        return listOf(
            "silenceremove=start_periods=1:start_duration=0.1:" +
                "start_threshold=$threshold:detection=peak",
            "silenceremove=stop_periods=-1:stop_duration=0.3:" +
                "stop_threshold=$threshold:detection=peak"
        )
    }

    private fun voiceChangerFiltersFor(preset: VoiceChangerPreset): List<String> = when (preset) {
        VoiceChangerPreset.Off -> emptyList()
        // asetrate shifts pitch and speed together; atempo restores the original
        // tempo so only the pitch changes.
        VoiceChangerPreset.Child -> listOf("asetrate=$SAMPLE_RATE*1.28", "aresample=$SAMPLE_RATE", "atempo=0.78")
        VoiceChangerPreset.Chipmunk -> listOf("asetrate=$SAMPLE_RATE*1.5", "aresample=$SAMPLE_RATE", "atempo=0.66")
        VoiceChangerPreset.Deep -> listOf("asetrate=$SAMPLE_RATE*0.82", "aresample=$SAMPLE_RATE", "atempo=1.22")
        VoiceChangerPreset.Monster -> listOf("asetrate=$SAMPLE_RATE*0.7", "aresample=$SAMPLE_RATE", "atempo=1.43")
        VoiceChangerPreset.Robot -> listOf(
            "afftfilt=real='hypot(re,im)*sin(0)':imag='hypot(re,im)*cos(0)':win_size=512:overlap=0.75"
        )
    }

    private fun equalizerFiltersFor(preset: EqualizerPreset): List<String> = when (preset) {
        EqualizerPreset.Off, EqualizerPreset.Flat -> emptyList()
        else -> preset.bands.mapIndexedNotNull { index, gain ->
            if (gain == 0.0) null
            else {
                val frequency = EqualizerPreset.CENTER_FREQUENCIES_HZ.getOrNull(index) ?: return@mapIndexedNotNull null
                "equalizer=f=$frequency:t=q:w=1.2:g=${formatNumber(gain)}"
            }
        }
    }

    private fun reverbFiltersFor(preset: ReverbPreset): String? = when (preset) {
        ReverbPreset.Off -> null
        ReverbPreset.SmallRoom -> "aecho=0.8:0.7:12|24:0.3|0.2"
        ReverbPreset.LargeHall -> "aecho=0.8:0.85:40|80|120:0.3|0.25|0.2"
        ReverbPreset.Cathedral -> "aecho=0.85:0.9:60|140|220:0.35|0.3|0.25"
        ReverbPreset.Plate -> "aecho=0.9:0.8:8|16:0.4|0.3"
    }

    private fun compressorFiltersFor(preset: CompressorPreset): String? = when (preset) {
        CompressorPreset.Off -> null
        CompressorPreset.Gentle -> "acompressor=threshold=0.15:ratio=2:attack=20:release=250"
        CompressorPreset.Podcast -> "acompressor=threshold=0.1:ratio=3:attack=10:release=200"
        CompressorPreset.Aggressive -> "acompressor=threshold=0.05:ratio=6:attack=5:release=120"
        CompressorPreset.Limiter -> "alimiter=limit=0.95"
    }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value)

    /** Extracts the FFmpeg filter name from a filter graph fragment such as `equalizer=f=31:...`. */
    fun filterNameOf(filter: String): String? =
        filter.substringBefore('=').takeIf { it.isNotBlank() }
}
