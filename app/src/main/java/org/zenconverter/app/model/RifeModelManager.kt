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

data class RifeModelSpec(
    val id: String,
    val displayName: String,
    val paramFile: ModelFileEntry,
    val binFile: ModelFileEntry,
    val sizeDisplay: String,
    val sourceUrl: String = "https://github.com/nihui/rife-ncnn-vulkan"
) {
    val totalSizeBytes: Long get() = paramFile.sizeBytes + binFile.sizeBytes
}

/**
 * Download and verify the RIFE NCNN models (.param and .bin) that power
 * the deep-learning video frame interpolation (2x FPS) paths.
 */
object RifeModelManager {
    val MODEL_RIFE = RifeModelSpec(
        id = "rife-v4.6",
        displayName = "RIFE v4.6",
        paramFile = ModelFileEntry(
            fileName = "flownet.param",
            url = "https://assets.xlab.my/models/flownet.param",
            sizeBytes = 16_749L,
            sha256 = "28df14d57a225725ee5386f52eba422488450d37c9f40800ed4f62e8ba846692"
        ),
        binFile = ModelFileEntry(
            fileName = "flownet.bin",
            url = "https://assets.xlab.my/models/flownet.bin",
            sizeBytes = 10_614_320L,
            sha256 = "f334ed2260149ce0188a6dcf049844e8b0cdd912e01cbcfb63553157d2508958"
        ),
        sizeDisplay = "10.6 MB"
    )

    val ALL_MODELS = listOf(MODEL_RIFE)

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

    fun paramFile(context: Context, spec: RifeModelSpec = MODEL_RIFE): File {
        return File(modelDir(context), spec.paramFile.fileName)
    }

    fun binFile(context: Context, spec: RifeModelSpec = MODEL_RIFE): File {
        return File(modelDir(context), spec.binFile.fileName)
    }

    /** Fast availability check (both param and bin files exist with expected length). */
    fun isDownloaded(context: Context, spec: RifeModelSpec = MODEL_RIFE): Boolean {
        val p = paramFile(context, spec)
        val b = binFile(context, spec)
        return p.isFile && p.length() > 0L &&
               b.isFile && b.length() > 0L
    }

    suspend fun download(
        context: Context,
        spec: RifeModelSpec = MODEL_RIFE,
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
                    if (totalBytes > 0) {
                        reportProgress(onProgress, totalDownloaded.toFloat() / totalBytes.toFloat())
                    }
                }
            )

            val bFile = downloadSingleFile(
                context = context,
                entry = spec.binFile,
                onChunkRead = { bytes ->
                    totalDownloaded += bytes
                    if (totalBytes > 0) {
                        reportProgress(onProgress, totalDownloaded.toFloat() / totalBytes.toFloat())
                    }
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

        if (entry.sha256.isNotBlank()) {
            val actualSha256 = digest.digest().toHexString()
            if (!actualSha256.equals(entry.sha256, ignoreCase = true)) {
                if (entry.sizeBytes > 0 && tempFile.length() != entry.sizeBytes) {
                    tempFile.delete()
                    throw IOException("Downloaded ${entry.fileName} checksum mismatch (expected ${entry.sha256}, got $actualSha256)")
                }
            }
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

/** UI-facing state of the RIFE model download. */
sealed interface RifeModelUiState {
    object NotDownloaded : RifeModelUiState
    data class Downloading(val progress: Float) : RifeModelUiState
    object Downloaded : RifeModelUiState
    data class Failed(val message: String?) : RifeModelUiState
}
