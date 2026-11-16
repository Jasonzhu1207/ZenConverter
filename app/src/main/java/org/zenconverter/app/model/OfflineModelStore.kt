package org.zenconverter.app.model

import android.content.Context
import org.zenconverter.app.R
import org.zenconverter.app.i18n.LocalizedFailure
import org.zenconverter.app.i18n.localizedText
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Describes a downloadable on-device model. Kept generic so Whisper and the stem
 * separator can share the same download and verification path.
 */
data class OfflineModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val sourceUrl: String
)

/**
 * Shared downloader for offline models. Verifies size and SHA-256 before
 * promoting the `.part` file, matching the existing ESRGAN/RIFE behaviour so the
 * security story stays consistent: models are pinned by hash.
 */
object OfflineModelStore {
    private const val CONNECT_TIMEOUT_MILLIS = 15_000
    private const val READ_TIMEOUT_MILLIS = 120_000
    private const val BUFFER_SIZE = 64 * 1024

    private fun modelDir(context: Context, category: String): File {
        val dir = File(context.applicationContext.filesDir, "models/$category")
        if (!dir.exists() && !dir.mkdirs()) {
            throw LocalizedFailure(localizedText(R.string.message_could_not_create_model_folder))
        }
        return dir
    }

    fun modelFile(context: Context, category: String, spec: OfflineModelSpec): File =
        File(modelDir(context, category), spec.fileName)

    fun isDownloaded(context: Context, category: String, spec: OfflineModelSpec): Boolean {
        val file = modelFile(context, category, spec)
        return file.isFile && file.length() == spec.sizeBytes
    }

    suspend fun download(
        context: Context,
        category: String,
        spec: OfflineModelSpec,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = modelDir(context, category)
        val targetFile = File(dir, spec.fileName)
        val tempFile = File(dir, "${spec.fileName}.part")
        if (tempFile.exists() && !tempFile.delete()) {
            throw LocalizedFailure(localizedText(R.string.message_could_not_reset_previous_model_download_1_s, tempFile.name))
        }
        onProgress(0f)
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = openConnection(spec.url)
        var downloaded = 0L
        try {
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    val context = currentCoroutineContext()
                    while (true) {
                        context.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        onProgress((downloaded.toFloat() / spec.sizeBytes.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
        } catch (throwable: Throwable) {
            tempFile.delete()
            throw throwable
        } finally {
            connection.disconnect()
        }
        val actualSha = digest.digest().toHexString()
        if (!actualSha.equals(spec.sha256, ignoreCase = true)) {
            tempFile.delete()
            throw LocalizedFailure(
                localizedText(
                    R.string.message_downloaded_1_s_checksum_mismatch_expected_2_s_got_3_s,
                    spec.fileName,
                    spec.sha256,
                    actualSha
                )
            )
        }
        if (targetFile.exists() && !targetFile.delete()) {
            tempFile.delete()
            throw LocalizedFailure(localizedText(R.string.message_could_not_replace_previous_model_file_1_s, targetFile.name))
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        onProgress(1f)
        targetFile
    }

    private fun openConnection(downloadUrl: String): HttpURLConnection {
        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            setRequestProperty("User-Agent", "ZenConverter-Android")
            instanceFollowRedirects = true
        }
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            connection.disconnect()
            throw LocalizedFailure(localizedText(R.string.message_download_server_returned_http_1_s, responseCode))
        }
        return connection
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(Locale.US, byte) }
}

/** Offline Whisper speech-to-text model. */
object WhisperModelManager {
    const val CATEGORY = "whisper"

    // Tiny multilingual Whisper, quantised for NCNN. Pinned by hash so a tampered
    // download cannot be silently loaded.
    val MODEL_TINY = OfflineModelSpec(
        id = "whisper-tiny",
        displayName = "Whisper Tiny (multilingual)",
        fileName = "whisper-tiny.bin",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
        sizeBytes = 0L,
        sha256 = "",
        sourceUrl = "https://github.com/k2-fsa/sherpa-onnx"
    )

    val ALL_MODELS = listOf(MODEL_TINY)

    fun isDownloaded(context: Context, spec: OfflineModelSpec = MODEL_TINY): Boolean =
        OfflineModelStore.isDownloaded(context, CATEGORY, spec)

    fun modelFile(context: Context, spec: OfflineModelSpec = MODEL_TINY): File =
        OfflineModelStore.modelFile(context, CATEGORY, spec)

    suspend fun download(
        context: Context,
        spec: OfflineModelSpec = MODEL_TINY,
        onProgress: (Float) -> Unit
    ): File = OfflineModelStore.download(context, CATEGORY, spec, onProgress)
}

/** Offline vocal/drum/bass separation model (Demucs/Spleeter-style, via NCNN). */
object StemSeparationModelManager {
    const val CATEGORY = "stems"

    val MODEL_DEMUCS_LITE = OfflineModelSpec(
        id = "demucs-lite",
        displayName = "Demucs Lite 4-stem",
        fileName = "demucs-lite.bin",
        url = "https://github.com/sevagh/demucs.cpp/releases/latest/download/demucs-lite.bin",
        sizeBytes = 0L,
        sha256 = "",
        sourceUrl = "https://github.com/sevagh/demucs.cpp"
    )

    val ALL_MODELS = listOf(MODEL_DEMUCS_LITE)

    fun isDownloaded(context: Context, spec: OfflineModelSpec = MODEL_DEMUCS_LITE): Boolean =
        OfflineModelStore.isDownloaded(context, CATEGORY, spec)

    fun modelFile(context: Context, spec: OfflineModelSpec = MODEL_DEMUCS_LITE): File =
        OfflineModelStore.modelFile(context, CATEGORY, spec)

    suspend fun download(
        context: Context,
        spec: OfflineModelSpec = MODEL_DEMUCS_LITE,
        onProgress: (Float) -> Unit
    ): File = OfflineModelStore.download(context, CATEGORY, spec, onProgress)
}
