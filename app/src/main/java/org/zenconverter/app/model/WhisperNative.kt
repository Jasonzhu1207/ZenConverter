package org.zenconverter.app.model

import android.util.Log

/**
 * JNI bridge to the offline Whisper speech-to-text runtime.
 *
 * Mirrors [NcnnNative]: the native library is optional, so every entry point is
 * guarded by [ensureLoaded]. When the library is absent the callers fall back to
 * reporting the engine as unavailable rather than failing a conversion.
 *
 * The native side is built from `native/whisper-jni` and shipped as
 * `libzen_whisper.so`. Until that library is bundled, [ensureLoaded] returns
 * false and no inference is attempted.
 */
object WhisperNative {
    private const val TAG = "WhisperNative"

    private val isLoaded: Boolean by lazy {
        try {
            System.loadLibrary("zen_whisper")
            val initResult = nativeInit()
            Log.i(TAG, "Whisper native library loaded. init=$initResult")
            initResult == 0
        } catch (t: Throwable) {
            Log.i(TAG, "Whisper native library not bundled: ${t.message}")
            false
        }
    }

    /** True when the native runtime is present and initialised. */
    fun ensureLoaded(): Boolean = isLoaded

    fun interface ProgressCallback {
        fun onProgress(progress: Float)
    }

    fun interface CancelCheck {
        fun isCancelled(): Boolean
    }

    /**
     * Transcribes [samples] (mono float PCM in `[-1, 1]`) with the GGML model at
     * [modelPath]. Returns the native JSON transcript, or `null` when the
     * runtime is missing or inference failed.
     *
     * The JSON shape is the one [org.zenconverter.app.audio.WhisperTranscriptParser]
     * reads: `{"segments":[{"start":0.0,"end":1.0,"text":" hi", "words":[...]}]}`.
     */
    fun transcribe(
        samples: FloatArray,
        sampleRateHz: Int,
        modelPath: String,
        language: String?,
        progressCallback: ProgressCallback?,
        cancelCheck: CancelCheck?
    ): String? {
        if (!ensureLoaded()) return null
        return runCatching {
            nativeTranscribe(
                samples,
                sampleRateHz,
                modelPath,
                language,
                progressCallback,
                cancelCheck
            )
        }.onFailure { throwable ->
            Log.w(TAG, "Whisper transcription failed", throwable)
        }.getOrNull()
    }

    external fun nativeInit(): Int

    external fun nativeDestroy()

    external fun nativeTranscribe(
        samples: FloatArray,
        sampleRateHz: Int,
        modelPath: String,
        language: String?,
        progressCallback: ProgressCallback?,
        cancelCheck: CancelCheck?
    ): String?
}
