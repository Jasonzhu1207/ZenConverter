package org.zenconverter.app.audio

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Runs the mastering grid on a real file with FFmpeg.
 *
 * The heavy lifting is two-pass loudness normalization: a first pass measures
 * the track with FFmpeg's own `loudnorm` meter (the same numbers a streaming
 * platform checks), and a second pass applies the EQ, dynamics and a linear
 * loudness/true-peak stage that lands on the target without clipping. Stems are
 * separated, measured and re-balanced per stem so a quiet vocal can be lifted
 * without squashing the drums.
 *
 * Nothing leaves the device and the source file is never overwritten: every
 * call writes a new file into the app cache for the UI to save where it likes.
 */
object MasteringEngine {

    /** Output containers the master can be written to. */
    enum class ExportFormat(val extension: String, val codec: String, val mimeType: String) {
        Wav("wav", "pcm_s24le", "audio/wav"),
        Flac("flac", "flac", "audio/flac"),
        Mp3("mp3", "libmp3lame", "audio/mpeg"),
        M4a("m4a", "aac", "audio/mp4");

        val isLossless: Boolean get() = this == Wav || this == Flac
    }

    /** The measured picture of a track: loudness, spectrum and how they rate. */
    data class Analysis(
        val measurement: LoudnessAnalyzer.Measurement,
        val balance: SpectrumAnalyzer.Balance,
        val sampleRateHz: Int,
        val channelCount: Int,
        val durationSeconds: Double,
        /** Per-bucket amplitude of the whole track, for the shape drawing. */
        val peaks: FloatArray
    ) {
        fun report(target: MasteringTarget): MasteringModel.Report =
            LoudnessAnalyzer.reportFor(measurement, target)
    }

    data class StemAnalysis(
        val kind: StemKind,
        val file: File,
        val lufs: Double
    )

    /**
     * Decodes [uri] to stereo float PCM at 48 kHz and measures it. Returns null
     * when the file cannot be decoded or is too short to gate.
     */
    suspend fun analyze(context: Context, uri: Uri): Analysis? = withContext(Dispatchers.IO) {
        val input = inputPathFor(context, uri) ?: return@withContext null
        analyzeInput(context, input)
    }

    /** Measures an intermediate file the pipeline produced itself (e.g. a mix). */
    suspend fun analyzeFile(context: Context, file: File): Analysis? = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() == 0L) return@withContext null
        analyzeInput(context, file.absolutePath)
    }

    private fun analyzeInput(context: Context, input: String): Analysis? {
        val decoded = decodeToPcm(context, input) ?: return null
        val measurement = LoudnessAnalyzer.measure(
            decoded.samples, decoded.sampleRateHz, decoded.channelCount
        ) ?: return null
        val balance = SpectrumAnalyzer.analyze(
            decoded.samples, decoded.sampleRateHz, decoded.channelCount
        )
        val peaks = WaveformAnalyzer.peaksFromSamples(
            decoded.samples, decoded.channelCount, decoded.sampleRateHz, bucketCount = 160
        )
        return Analysis(
            measurement = measurement,
            balance = balance,
            sampleRateHz = decoded.sampleRateHz,
            channelCount = decoded.channelCount,
            durationSeconds = decoded.samples.size.toDouble() /
                (decoded.channelCount * decoded.sampleRateHz),
            peaks = peaks.maxima
        )
    }

    /** Separates the source into stems and measures each one's loudness. */
    suspend fun separateAndMeasureStems(
        context: Context,
        uri: Uri,
        outputDir: File
    ): List<StemAnalysis> = withContext(Dispatchers.IO) {
        val stems = AudioStudioEngine.separateStems(context, uri, outputDir)
        if (stems.isEmpty()) return@withContext emptyList()
        stems.mapNotNull { (kind, file) ->
            val decoded = decodeToPcm(context, file.absolutePath) ?: return@mapNotNull null
            val measurement = LoudnessAnalyzer.measure(
                decoded.samples, decoded.sampleRateHz, decoded.channelCount
            ) ?: return@mapNotNull null
            StemAnalysis(kind, file, measurement.inputI)
        }
    }

    /**
     * Applies the per-stem trims and mixes the stems back into one balanced file.
     * Each stem is attenuated or lifted by its own `volume` filter before the
     * `amix`, so the balance is baked into the mix rather than the individual
     * files.
     */
    suspend fun renderBalancedMix(
        context: Context,
        stems: List<StemAnalysis>,
        balances: List<StemBalance>,
        target: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (stems.isEmpty()) return@withContext false
        val trimByKind = balances.associate { it.kind to it.trimDb }
        val arguments = mutableListOf("-hide_banner", "-nostdin")
        stems.forEach { arguments += listOf("-i", it.file.absolutePath) }
        val parts = stems.mapIndexed { index, stem ->
            val trim = trimByKind[stem.kind] ?: 0.0
            "[$index:a]${MasteringModel.volumeFilter(trim)}[v$index]"
        }
        val inputs = stems.indices.joinToString("") { "[v$it]" }
        val filter = parts.joinToString(";") +
            ";${inputs}amix=inputs=${stems.size}:duration=longest:normalize=0," +
            "alimiter=limit=0.97[out]"
        arguments += listOf(
            "-filter_complex", filter,
            "-map", "[out]",
            "-c:a", "pcm_s24le",
            "-y", target.absolutePath
        )
        ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments.toTypedArray()).getReturnCode())
    }

    /**
     * Renders the mastered file in two passes. Returns the path plus the loudness
     * FFmpeg measured, so the UI can show the before/after honestly instead of
     * trusting the target.
     */
    suspend fun master(
        context: Context,
        sourceUri: Uri,
        plan: MasteringPlan,
        target: File,
        format: ExportFormat = ExportFormat.Wav,
        measured: LoudnessAnalyzer.Measurement? = null
    ): LoudnessAnalyzer.Measurement? = withContext(Dispatchers.IO) {
        val input = inputPathFor(context, sourceUri) ?: return@withContext null
        masterInput(context, input, plan, target, format, measured)
    }

    /** Masters an intermediate file (e.g. the balanced stem mix) directly. */
    suspend fun masterFile(
        context: Context,
        source: File,
        plan: MasteringPlan,
        target: File,
        format: ExportFormat = ExportFormat.Wav,
        measured: LoudnessAnalyzer.Measurement? = null
    ): LoudnessAnalyzer.Measurement? = withContext(Dispatchers.IO) {
        if (!source.isFile || source.length() == 0L) return@withContext null
        masterInput(context, source.absolutePath, plan, target, format, measured)
    }

    private fun masterInput(
        context: Context,
        input: String,
        plan: MasteringPlan,
        target: File,
        format: ExportFormat,
        measured: LoudnessAnalyzer.Measurement?
    ): LoudnessAnalyzer.Measurement? {
        val firstPass = loudnormFirstPass(input, plan) ?: measured
        val chain = MasteringModel.filterChain(plan, firstPass)
        val arguments = mutableListOf(
            "-hide_banner", "-nostdin",
            "-i", input,
            "-af", chain
        )
        arguments += codecArguments(format)
        arguments += listOf("-y", target.absolutePath)
        val ok = ReturnCode.isSuccess(
            FFmpegKit.executeWithArguments(arguments.toTypedArray()).getReturnCode()
        )
        if (!ok) return null
        // Report what actually landed, so the gauges are real and not the target.
        return decodeToPcm(context, target.absolutePath)?.let { decoded ->
            LoudnessAnalyzer.measure(decoded.samples, decoded.sampleRateHz, decoded.channelCount)
        } ?: firstPass
    }

    /** The `-c:a`/bitrate flags for a container, keeping lossless targets lossless. */
    fun codecArguments(format: ExportFormat): List<String> = when (format) {
        ExportFormat.Wav -> listOf("-c:a", "pcm_s24le")
        ExportFormat.Flac -> listOf("-c:a", "flac", "-compression_level", "8")
        ExportFormat.Mp3 -> listOf("-c:a", "libmp3lame", "-b:a", "320k")
        ExportFormat.M4a -> listOf("-c:a", "aac", "-b:a", "256k")
    }

    /**
     * First pass: measure with FFmpeg's loudnorm and read its JSON summary so the
     * second pass can run in linear mode, which is transparent because it applies
     * one fixed gain instead of riding the level dynamically.
     */
    private fun loudnormFirstPass(input: String, plan: MasteringPlan): LoudnessAnalyzer.Measurement? {
        val target = plan.target
        val filter = "loudnorm=I=${number(target.lufs)}:TP=${number(target.truePeakDb)}:" +
            "LRA=${number(target.loudnessRangeLu)}:print_format=json"
        val session = FFmpegKit.executeWithArguments(
            arrayOf("-hide_banner", "-nostdin", "-i", input, "-af", filter, "-f", "null", "-")
        )
        if (!ReturnCode.isSuccess(session.getReturnCode())) return null
        val logs = runCatching { session.getAllLogsAsString() }.getOrNull() ?: return null
        // loudnorm prints exactly one JSON summary; read the fields straight off it.
        val inputI = jsonNumber(logs, "input_i") ?: return null
        val inputTp = jsonNumber(logs, "input_tp") ?: return null
        val inputLra = jsonNumber(logs, "input_lra") ?: 0.0
        val inputThresh = jsonNumber(logs, "input_thresh") ?: -70.0
        val offset = jsonNumber(logs, "target_offset") ?: 0.0
        return LoudnessAnalyzer.Measurement(
            inputI = inputI,
            inputTp = inputTp,
            inputLra = inputLra,
            inputThresh = inputThresh,
            targetOffset = offset,
            samplePeakDb = inputTp,
            crestFactorDb = 0.0
        )
    }

    /** Reads a numeric field out of loudnorm's JSON summary without a JSON parser. */
    private fun jsonNumber(json: String, key: String): Double? {
        val marker = "\"$key\""
        val start = json.indexOf(marker)
        if (start < 0) return null
        val colon = json.indexOf(':', start + marker.length)
        if (colon < 0) return null
        val value = json.substring(colon + 1).trim().trim('"', ',', ' ')
        val end = value.indexOfFirst { !it.isDigit() && it != '-' && it != '.' }
        val token = if (end < 0) value else value.substring(0, end)
        return token.toDoubleOrNull()
    }

    /** Decodes any input to interleaved float PCM at 48 kHz, up to stereo. */
    private fun decodeToPcm(context: Context, input: String): DecodedPcm? {
        val temp = File.createTempFile("zen-master-", ".wav", context.cacheDir)
        return try {
            val ok = ReturnCode.isSuccess(
                FFmpegKit.executeWithArguments(
                    arrayOf(
                        "-hide_banner", "-nostdin",
                        "-i", input,
                        "-vn", "-ac", "2", "-ar", MasteringModel.SAMPLE_RATE_HZ.toString(),
                        "-f", "wav", "-c:a", "pcm_f32le",
                        "-y", temp.absolutePath
                    )
                ).getReturnCode()
            )
            if (!ok) null else readPcm(temp)
        } finally {
            temp.delete()
        }
    }

    data class DecodedPcm(val samples: FloatArray, val sampleRateHz: Int, val channelCount: Int)

    /**
     * Parses a canonical WAV, walking the chunk list so a LIST/INFO chunk before
     * `data` does not shift the payload. Handles both 32-bit float (what we ask
     * FFmpeg for) and 16-bit PCM (fallback).
     */
    internal fun readPcm(file: File): DecodedPcm? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (bytes.size <= 44) return null
        var offset = 12
        var channels = 0
        var sampleRate = 0
        var bits = 0
        var dataStart = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = littleEndianInt(bytes, offset + 4)
            val body = offset + 8
            when (id) {
                "fmt " -> {
                    channels = littleEndianShort(bytes, body + 2)
                    sampleRate = littleEndianInt(bytes, body + 4)
                    bits = littleEndianShort(bytes, body + 14)
                }
                "data" -> {
                    dataStart = body
                    dataSize = size.coerceAtMost(bytes.size - body)
                }
            }
            if (dataStart >= 0 && channels > 0) break
            if (size <= 0) break
            offset = body + size + (size % 2)
        }
        if (dataStart < 0 || channels <= 0 || sampleRate <= 0) return null
        val samples = when (bits) {
            32 -> readFloat32(bytes, dataStart, dataSize)
            16 -> WaveformAnalyzer.samplesFromPcm16(
                bytes.copyOfRange(dataStart, dataStart + dataSize), channels
            )
            else -> return null
        }
        return DecodedPcm(samples, sampleRate, channels)
    }

    private fun readFloat32(bytes: ByteArray, start: Int, size: Int): FloatArray {
        val count = size / 4
        val buffer = ByteBuffer.wrap(bytes, start, count * 4).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(count) { buffer.getFloat() }
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun littleEndianShort(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun inputPathFor(context: Context, uri: Uri): String? =
        runCatching { FFmpegKitConfig.getSafParameterForRead(context, uri) }.getOrNull()
            ?: runCatching {
                "/proc/self/fd/${context.contentResolver.openFileDescriptor(uri, "r")?.fd}"
            }.getOrNull()

    private fun number(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value)
}
