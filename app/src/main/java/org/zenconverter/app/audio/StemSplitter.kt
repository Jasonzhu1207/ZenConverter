package org.zenconverter.app.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.zenconverter.app.model.StemNative
import org.zenconverter.app.model.StemSeparationModelManager
import java.io.File

/**
 * Splits a mix into four stems.
 *
 * Two backends sit behind one entry point:
 *
 * - a neural separator (`libzen_ncnn.so` plus a downloaded Demucs-style model),
 *   used when [isNeuralAvailable] reports it is present; and
 * - the built-in FFmpeg spectral approximation in [StemFilters], which always
 *   works offline and is the fallback.
 *
 * [StemBackend] records which one produced a result so the UI can label the
 * output honestly instead of implying a neural split that did not happen.
 */
object StemSplitter {

    enum class StemBackend {
        /** Model-based separation. */
        Neural,

        /** FFmpeg spectral and stereo-field approximation. */
        Approximation
    }

    data class Result(
        val backend: StemBackend,
        val stems: List<Pair<StemKind, File>>
    )

    /** True when the native separator and its model are both usable. */
    fun isNeuralAvailable(context: Context): Boolean =
        StemNative.ensureLoaded() && StemSeparationModelManager.isDownloaded(context)

    /**
     * Separates [sourceUri] into [outputDir]. Tries the neural backend first when
     * [preferNeural] is set and it is available, then falls back to the FFmpeg
     * approximation so a user always gets stems.
     */
    suspend fun separate(
        context: Context,
        sourceUri: Uri,
        outputDir: File,
        preferNeural: Boolean = true
    ): Result = withContext(Dispatchers.IO) {
        if (preferNeural && isNeuralAvailable(context)) {
            neuralResult(context, sourceUri, outputDir)?.let { return@withContext it }
        }
        val stems = AudioStudioEngine.separateStems(context, sourceUri, outputDir)
        Result(backend = StemBackend.Approximation, stems = stems)
    }

    private suspend fun neuralResult(
        context: Context,
        sourceUri: Uri,
        outputDir: File
    ): Result? {
        val decoded = AudioDecoder.decodeMono(context, sourceUri, targetRateHz = NEURAL_SAMPLE_RATE_HZ)
            ?: return null
        if (decoded.samples.isEmpty()) return null
        if (!outputDir.exists() && !outputDir.mkdirs()) return null

        val spec = StemSeparationModelManager.MODEL_DEMUCS_LITE
        val modelFile = StemSeparationModelManager.modelFile(context, spec)
        val written = StemNative.separate(
            samples = decoded.samples,
            sampleRateHz = decoded.sampleRateHz,
            paramPath = modelFile.absolutePath,
            binPath = modelFile.absolutePath,
            outputDir = outputDir.absolutePath,
            progressCallback = null,
            cancelCheck = null
        )
        if (written <= 0) return null

        val stems = StemKind.entries.mapNotNull { kind ->
            File(outputDir, kind.fileName)
                .takeIf { it.isFile && it.length() > 0L }
                ?.let { kind to it }
        }
        if (stems.isEmpty()) return null
        return Result(backend = StemBackend.Neural, stems = stems)
    }

    /** Demucs models are trained at 44.1 kHz; the decode matches that. */
    private const val NEURAL_SAMPLE_RATE_HZ = 44_100
}
