package org.zenconverter.app.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.zenconverter.app.model.WhisperModelManager
import org.zenconverter.app.model.WhisperNative

/**
 * Runs offline speech-to-text for the transcribe screen and cut-by-transcript
 * editing.
 *
 * Inference needs two things: the GGML model downloaded through
 * [WhisperModelManager], and the `libzen_whisper.so` runtime. The runtime is an
 * optional native library, so [isEngineAvailable] reports whether this build can
 * actually transcribe. When it cannot, callers keep the manual transcript path
 * instead of showing a failure.
 */
object TranscriptionEngine {

    /** True when this build bundles a working Whisper runtime. */
    fun isEngineAvailable(): Boolean = WhisperNative.ensureLoaded()

    /**
     * Decodes [sourceUri], transcribes it, and returns the parsed segments. An
     * empty list means either the engine is unavailable, the model is not
     * downloaded, or inference produced nothing usable.
     */
    suspend fun transcribe(
        context: Context,
        sourceUri: Uri,
        language: String? = null,
        onProgress: (Float) -> Unit = {}
    ): List<TranscriptSegment> = withContext(Dispatchers.IO) {
        if (!isEngineAvailable()) return@withContext emptyList()
        if (!WhisperModelManager.isDownloaded(context)) return@withContext emptyList()

        val decoded = AudioDecoder.decodeMono(context, sourceUri) ?: return@withContext emptyList()
        if (decoded.samples.isEmpty()) return@withContext emptyList()

        val json = WhisperNative.transcribe(
            samples = decoded.samples,
            sampleRateHz = decoded.sampleRateHz,
            modelPath = WhisperModelManager.modelFile(context).absolutePath,
            language = language,
            progressCallback = { progress -> onProgress(progress) },
            cancelCheck = null
        )
        WhisperTranscriptParser.parse(json)
    }
}
