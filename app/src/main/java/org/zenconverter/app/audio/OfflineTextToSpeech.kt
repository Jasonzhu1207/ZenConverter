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
    private var currentLocale: Locale = Locale.getDefault()

    /** A selectable on-device voice, with the locale it speaks. */
    data class Voice(val name: String, val locale: Locale, val label: String)

    /** Rate, pitch and pause controls, all in the ranges the platform accepts. */
    data class SpeechOptions(
        val rate: Float = 1f,
        val pitch: Float = 1f,
        val pauseMs: Long = 0L
    )

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

    /** Switches the active language, returning false when the device lacks it. */
    fun setLocale(locale: Locale): Boolean {
        val tts = engine ?: return false
        val result = tts.setLanguage(locale)
        val ok = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        if (ok) currentLocale = locale
        return ok
    }

    /** Picks a specific installed voice, returning false when it is gone. */
    fun setVoice(voice: Voice): Boolean {
        val tts = engine ?: return false
        val match = runCatching { tts.voices?.firstOrNull { it.name == voice.name } }.getOrNull() ?: return false
        val ok = runCatching { tts.voice = match }.isSuccess
        if (ok) currentLocale = match.locale
        return ok
    }

    /** Every installed voice the engine reports, for the picker. */
    fun voices(): List<Voice> = runCatching {
        engine?.voices.orEmpty()
            .filterNot { it.isNetworkConnectionRequired }
            .map { Voice(it.name, it.locale, "${it.locale.displayLanguage} · ${it.name}") }
            .sortedBy { it.label }
    }.getOrDefault(emptyList())

    suspend fun synthesizeToFile(
        text: String,
        target: File,
        options: SpeechOptions = SpeechOptions()
    ): Boolean =
        withContext(Dispatchers.Main) {
            val tts = engine ?: return@withContext false
            if (!ready) return@withContext false
            tts.setSpeechRate(options.rate.coerceIn(0.1f, 3f))
            tts.setPitch(options.pitch.coerceIn(0.1f, 3f))
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
                val result = tts.synthesizeToFile(text.take(MAX_UTTERANCE_CHARS), Bundle(), target, utteranceId)
                if (result != TextToSpeech.SUCCESS) {
                    continuation.resumeWithException(IllegalStateException("tts-start-failed"))
                }
                continuation.invokeOnCancellation { runCatching { tts.stop() } }
            }
        }

    fun availableLanguages(): Set<Locale> = engine?.availableLanguages ?: emptySet()

    /**
     * Splits [text] into utterance-sized chunks on sentence boundaries so long
     * documents are not silently truncated by the platform engine.
     */
    fun splitForSpeech(text: String, maxChars: Int = MAX_UTTERANCE_CHARS): List<String> {
        val cleaned = text.replace("\r\n", "\n").trim()
        if (cleaned.isEmpty()) return emptyList()
        val chunks = mutableListOf<String>()
        val builder = StringBuilder()
        cleaned.split(Regex("(?<=[.!?])\\s+")).forEach { sentence ->
            if (builder.isNotEmpty() && builder.length + sentence.length > maxChars) {
                chunks += builder.toString().trim()
                builder.clear()
            }
            if (sentence.length > maxChars) {
                sentence.chunked(maxChars).forEach { part ->
                    if (builder.isNotEmpty()) {
                        chunks += builder.toString().trim()
                        builder.clear()
                    }
                    chunks += part.trim()
                }
            } else {
                if (builder.isNotEmpty()) builder.append(' ')
                builder.append(sentence)
            }
        }
        if (builder.isNotEmpty()) chunks += builder.toString().trim()
        return chunks.filter { it.isNotBlank() }
    }

    /** Rough duration estimate used for the pre-export summary. */
    fun estimateSeconds(text: String, options: SpeechOptions): Double {
        val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val wordsPerSecond = 2.6 * options.rate
        val speech = if (words == 0) 0.0 else words / wordsPerSecond
        val pauses = splitForSpeech(text).size * (options.pauseMs / 1000.0)
        return speech + pauses
    }

    companion object {
        const val MAX_UTTERANCE_CHARS = 3_500
    }

    fun shutdown() {
        ready = false
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
    }
}
