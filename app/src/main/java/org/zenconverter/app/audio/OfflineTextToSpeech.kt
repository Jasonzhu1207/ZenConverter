package org.zenconverter.app.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps the platform text-to-speech engine and renders text to a WAV file using
 * `synthesizeToFile`. This uses the on-device engine, so nothing is uploaded.
 */
class OfflineTextToSpeech(private val context: Context) {
    private var engine: TextToSpeech? = null
    private var ready = false

    suspend fun initialize(locale: Locale = Locale.getDefault()): Boolean =
        withContext(Dispatchers.Main) {
            if (ready) return@withContext true
            suspendCancellableCoroutine { continuation ->
                var resumed = false
                val tts = TextToSpeech(context) { status ->
                    if (resumed) return@TextToSpeech
                    resumed = true
                    if (status == TextToSpeech.SUCCESS) {
                        ready = true
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }
                engine = tts
                tts.language = locale
                continuation.invokeOnCancellation {
                    runCatching { tts.shutdown() }
                    engine = null
                }
            }
        }

    suspend fun synthesizeToFile(text: String, target: File): Boolean =
        withContext(Dispatchers.Main) {
            val tts = engine ?: return@withContext false
            if (!ready) return@withContext false
            suspendCancellableCoroutine { continuation ->
                val utteranceId = "zenconverter-tts-${System.nanoTime()}"
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) = Unit

                    override fun onDone(id: String?) {
                        if (id == utteranceId) continuation.resume(true)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        if (id == utteranceId) continuation.resumeWithException(IllegalStateException("tts-error"))
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId) continuation.resumeWithException(IllegalStateException("tts-error-$errorCode"))
                    }
                })
                val result = tts.synthesizeToFile(text, Bundle(), target, utteranceId)
                if (result != TextToSpeech.SUCCESS) {
                    continuation.resumeWithException(IllegalStateException("tts-start-failed"))
                }
                continuation.invokeOnCancellation { runCatching { tts.stop() } }
            }
        }

    fun availableLanguages(): Set<Locale> = engine?.availableLanguages ?: emptySet()

    fun shutdown() {
        ready = false
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
    }
}
