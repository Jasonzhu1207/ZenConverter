package org.zenconverter.app.model

import android.util.Log

/**
 * JNI bridge to the offline Demucs source separator.
 *
 * The separator is its own library (`libzen_demucs.so`, built from
 * `native/demucs-jni`) because the Demucs hybrid transformer runs on ggml/Eigen
 * rather than NCNN. It is optional, so [ensureLoaded] reports whether this build
 * can separate; callers fall back to the built-in FFmpeg approximation when it
 * cannot.
 *
 * The model is a single ggml weight file downloaded and verified at runtime by
 * [StemSeparationModelManager].
 */
object StemNative {
    private const val TAG = "StemNative"

    private val isLoaded: Boolean by lazy {
        try {
            // demucs.cpp is built with OpenMP, so libomp.so must be present too.
            System.loadLibrary("omp")
            System.loadLibrary("zen_demucs")
            val initResult = nativeStemInit()
            Log.i(TAG, "Demucs native library loaded. init=$initResult")
            initResult == 0
        } catch (t: Throwable) {
            Log.i(TAG, "Stem separator not bundled: ${t.message}")
            false
        }
    }

    /** True when the native separator is present and initialised. */
    fun ensureLoaded(): Boolean = isLoaded

    fun interface ProgressCallback {
        fun onProgress(progress: Float)
    }

    fun interface CancelCheck {
        fun isCancelled(): Boolean
    }

    /**
     * Separates [samples] (mono float PCM in `[-1, 1]` at [sampleRateHz]) into
     * the four stems, writing 16-bit PCM WAV files named `vocals.wav`,
     * `drums.wav`, `bass.wav` and `other.wav` into [outputDir]. Returns the
     * number of stems written, or a negative value on failure.
     */
    fun separate(
        samples: FloatArray,
        sampleRateHz: Int,
        modelPath: String,
        outputDir: String,
        progressCallback: ProgressCallback?,
        cancelCheck: CancelCheck?
    ): Int {
        if (!ensureLoaded()) return -1
        return runCatching {
            nativeStemSeparate(
                samples,
                sampleRateHz,
                modelPath,
                outputDir,
                progressCallback,
                cancelCheck
            )
        }.onFailure { throwable ->
            Log.w(TAG, "Stem separation failed", throwable)
        }.getOrDefault(-1)
    }

    external fun nativeStemInit(): Int

    external fun nativeStemSeparate(
        samples: FloatArray,
        sampleRateHz: Int,
        modelPath: String,
        outputDir: String,
        progressCallback: ProgressCallback?,
        cancelCheck: CancelCheck?
    ): Int
}
