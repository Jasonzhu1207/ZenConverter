package org.zenconverter.app.model

import android.util.Log

/**
 * JNI bridge to the offline Demucs-style stem separator.
 *
 * The separator runs on the NCNN runtime that `libzen_ncnn.so` already links
 * (see `native/ncnn-jni`), so this loads the same library as [NcnnNative]. The
 * separator symbols are added by the same build; when they are absent the
 * library still loads for the other NCNN features and [ensureLoaded] reports
 * that separation is unavailable.
 *
 * The model itself is a `.param`/`.bin` pair downloaded at runtime and verified
 * by [org.zenconverter.app.model.StemSeparationModelManager].
 */
object StemNative {
    private const val TAG = "StemNative"

    private val isLoaded: Boolean by lazy {
        try {
            System.loadLibrary("zen_ncnn")
            val initResult = nativeStemInit()
            Log.i(TAG, "Stem separator native symbols loaded. init=$initResult")
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
     * the four stems, writing 16-bit PCM WAV files into [outputDir]. Returns the
     * number of stems written, or a negative value on failure.
     */
    fun separate(
        samples: FloatArray,
        sampleRateHz: Int,
        paramPath: String,
        binPath: String,
        outputDir: String,
        progressCallback: ProgressCallback?,
        cancelCheck: CancelCheck?
    ): Int {
        if (!ensureLoaded()) return -1
        return runCatching {
            nativeStemSeparate(
                samples,
                sampleRateHz,
                paramPath,
                binPath,
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
        paramPath: String,
        binPath: String,
        outputDir: String,
        progressCallback: ProgressCallback?,
        cancelCheck: CancelCheck?
    ): Int
}
