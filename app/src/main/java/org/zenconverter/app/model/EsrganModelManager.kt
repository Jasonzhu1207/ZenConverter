package org.zenconverter.app.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * Download and verify the Real-ESRGAN ONNX model that powers the deep-learning
 * image super-resolution path. The model is treated as a static file on our R2
 * direct link, not a hot-updated remote config: adding or changing a model is
 * a code change that ships with an app update.
 */
object EsrganModelManager {
    const val MODEL_NAME = "RealESRGAN_x4plus"
    const val MODEL_URL = "https://assets.xlab.my/models/RealESRGAN_x4plus.onnx"
    const val MODEL_SIZE_BYTES = 67_051_973L
    const val MODEL_SIZE_DISPLAY = "63.9 MB"
    const val MODEL_SHA256 = "39d5218cfcef542d667821a0d2072cfa51bfd857ab0e4ae7dc067c399a88d323"
    const val MODEL_SOURCE_URL = "https://github.com/xinntao/Real-ESRGAN"

    private const val CONNECT_TIMEOUT_MILLIS = 15_000
    private const val READ_TIMEOUT_MILLIS = 60_000
    private const val BUFFER_SIZE = 64 * 1024

    fun modelFile(context: Context): File {
        val appContext = context.applicationContext
        val dir = File(appContext.filesDir, "models")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Could not create model folder")
        }
        return File(dir, "$MODEL_NAME.onnx")
    }

    /** Fast availability check (file exists with the expected byte length). */
    fun isDownloaded(context: Context): Boolean {
        val file = modelFile(context)
        return file.isFile && file.length() == MODEL_SIZE_BYTES
    }

    suspend fun download(
        context: Context,
        onProgress: (Float) -> Unit
    ): File {
        val targetFile = modelFile(context)
        return withContext(Dispatchers.IO) {
            val tempFile = File(targetFile.parentFile, "$MODEL_NAME.onnx.part")
            if (tempFile.exists() && !tempFile.delete()) {
                throw IOException("Could not reset previous model download")
            }

            val progressContext = currentCoroutineContext()
            val digest = MessageDigest.getInstance("SHA-256")
            var bytesDownloaded = 0L
            val totalBytes = MODEL_SIZE_BYTES

            reportProgress(onProgress, 0f)
            val connection = openDownloadConnection(MODEL_URL)
            try {
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            progressContext.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            bytesDownloaded += read.toLong()
                            reportProgress(
                                onProgress,
                                bytesDownloaded.toFloat() / totalBytes.toFloat()
                            )
                        }
                    }
                }
            } catch (throwable: Throwable) {
                tempFile.delete()
                throw throwable
            } finally {
                connection.disconnect()
            }

            val actualSha256 = digest.digest().toHexString()
            if (!actualSha256.equals(MODEL_SHA256, ignoreCase = true)) {
                tempFile.delete()
                throw IOException("Downloaded model checksum did not match")
            }
            if (tempFile.length() != MODEL_SIZE_BYTES) {
                tempFile.delete()
                throw IOException("Downloaded model size did not match")
            }

            if (targetFile.exists() && !targetFile.delete()) {
                tempFile.delete()
                throw IOException("Could not replace previous model file")
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            targetFile
        }
    }

    private fun openDownloadConnection(downloadUrl: String): HttpURLConnection {
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
            throw IOException("Download server returned HTTP $responseCode")
        }
        return connection
    }

    private suspend fun reportProgress(
        onProgress: (Float) -> Unit,
        progress: Float
    ) {
        withContext(Dispatchers.Main) {
            onProgress(progress.coerceIn(0f, 1f))
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte)
        }
    }
}

/** UI-facing state of the Real-ESRGAN model download. */
sealed interface EsrganModelUiState {
    object NotDownloaded : EsrganModelUiState
    data class Downloading(val progress: Float) : EsrganModelUiState
    object Downloaded : EsrganModelUiState
    data class Failed(val message: String?) : EsrganModelUiState
}
