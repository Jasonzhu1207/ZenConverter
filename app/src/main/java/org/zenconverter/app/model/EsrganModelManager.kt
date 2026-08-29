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

data class ModelFileEntry(
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String
)

data class EsrganModelSpec(
    val id: String,
    val displayName: String,
    val paramFile: ModelFileEntry,
    val binFile: ModelFileEntry,
    val sizeDisplay: String,
    val sourceUrl: String = "https://github.com/xinntao/Real-ESRGAN"
) {
    val totalSizeBytes: Long get() = paramFile.sizeBytes + binFile.sizeBytes
}

/**
 * Download and verify the Real-ESRGAN NCNN models (.param and .bin) that power
 * the deep-learning image super-resolution paths.
 */
object EsrganModelManager {
    val MODEL_ANIME = EsrganModelSpec(
        id = "realesrgan-x4plus-anime",
        displayName = "realesrgan-x4plus-anime",
        paramFile = ModelFileEntry(
            fileName = "realesrgan-x4plus-anime.param",
            url = "https://assets.xlab.my/models/realesrgan-x4plus-anime.param",
            sizeBytes = 30_290L,
            sha256 = "2b8fb6e0ae4d2d85704ca08c119a2f5ea40add4f2ecd512eb7f4cd44b6127ed4"
        ),
        binFile = ModelFileEntry(
            fileName = "realesrgan-x4plus-anime.bin",
            url = "https://assets.xlab.my/models/realesrgan-x4plus-anime.bin",
            sizeBytes = 8_943_500L,
            sha256 = "fe01c269cfd10cdef8e018ab66ebe750cf79c7af4d1f9c16c737e1295229bacc"
        ),
        sizeDisplay = "8.97 MB"
    )

    val MODEL_X4PLUS = EsrganModelSpec(
        id = "realesrgan-x4plus",
        displayName = "realesrgan-x4plus",
        paramFile = ModelFileEntry(
            fileName = "realesrgan-x4plus.param",
            url = "https://assets.xlab.my/models/realesrgan-x4plus.param",
            sizeBytes = 116_029L,
            sha256 = "35330ececcea33b6c397a72548e788d5d53becee4734c50b7fada36e89f10a86"
        ),
        binFile = ModelFileEntry(
            fileName = "realesrgan-x4plus.bin",
            url = "https://assets.xlab.my/models/realesrgan-x4plus.bin",
            sizeBytes = 33_424_520L,
            sha256 = "713ee713b0353afaa27976f0563a64a5043bd70b9bd8936c2e26e25ebcdbcddf"
        ),
        sizeDisplay = "33.5 MB"
    )

    val ALL_MODELS = listOf(MODEL_ANIME, MODEL_X4PLUS)

    private const val CONNECT_TIMEOUT_MILLIS = 15_000
    private const val READ_TIMEOUT_MILLIS = 60_000
    private const val BUFFER_SIZE = 64 * 1024

    private fun modelDir(context: Context): File {
        val dir = File(context.applicationContext.filesDir, "models")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Could not create model folder")
        }
        return dir
    }

    fun paramFile(context: Context, spec: EsrganModelSpec): File {
        return File(modelDir(context), spec.paramFile.fileName)
    }

    fun binFile(context: Context, spec: EsrganModelSpec): File {
        return File(modelDir(context), spec.binFile.fileName)
    }

    /** Fast availability check (both param and bin files exist with expected length). */
    fun isDownloaded(context: Context, spec: EsrganModelSpec = MODEL_X4PLUS): Boolean {
        val p = paramFile(context, spec)
        val b = binFile(context, spec)
        return p.isFile && p.length() == spec.paramFile.sizeBytes &&
               b.isFile && b.length() == spec.binFile.sizeBytes
    }

    suspend fun download(
        context: Context,
        spec: EsrganModelSpec = MODEL_X4PLUS,
        onProgress: (Float) -> Unit
    ): Pair<File, File> {
        return withContext(Dispatchers.IO) {
            val totalBytes = spec.totalSizeBytes
            var totalDownloaded = 0L

            reportProgress(onProgress, 0f)

            val pFile = downloadSingleFile(
                context = context,
                entry = spec.paramFile,
                onChunkRead = { bytes ->
                    totalDownloaded += bytes
                    reportProgress(onProgress, totalDownloaded.toFloat() / totalBytes.toFloat())
                }
            )

            val bFile = downloadSingleFile(
                context = context,
                entry = spec.binFile,
                onChunkRead = { bytes ->
                    totalDownloaded += bytes
                    reportProgress(onProgress, totalDownloaded.toFloat() / totalBytes.toFloat())
                }
            )

            reportProgress(onProgress, 1f)
            Pair(pFile, bFile)
        }
    }

    private suspend fun downloadSingleFile(
        context: Context,
        entry: ModelFileEntry,
        onChunkRead: suspend (Int) -> Unit
    ): File {
        val dir = modelDir(context)
        val targetFile = File(dir, entry.fileName)
        val tempFile = File(dir, "${entry.fileName}.part")

        if (tempFile.exists() && !tempFile.delete()) {
            throw IOException("Could not reset previous model download: ${tempFile.name}")
        }

        val progressContext = currentCoroutineContext()
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = openDownloadConnection(entry.url)
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
                        onChunkRead(read)
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
        if (!actualSha256.equals(entry.sha256, ignoreCase = true)) {
            tempFile.delete()
            throw IOException("Downloaded ${entry.fileName} checksum mismatch (expected ${entry.sha256}, got $actualSha256)")
        }
        if (tempFile.length() != entry.sizeBytes) {
            tempFile.delete()
            throw IOException("Downloaded ${entry.fileName} size mismatch (expected ${entry.sizeBytes}, got ${tempFile.length()})")
        }

        if (targetFile.exists() && !targetFile.delete()) {
            tempFile.delete()
            throw IOException("Could not replace previous model file: ${targetFile.name}")
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }

        return targetFile
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
