package org.zenconverter.app.office

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

data class OfficeFontSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sizeDisplay: String,
    val sha256: String,
    val description: String = ""
)

/**
 * Manages system font discovery and optional on-demand downloads for CJK fonts used
 * by the Office-to-PDF Typst renderer.
 */
object OfficeFontManager {
    val FONT_NOTO_SANS_CJK = OfficeFontSpec(
        id = "noto-sans-cjk",
        displayName = "Noto Sans CJK (黑体 / 无衬线)",
        fileName = "NotoSansCJK-Regular.ttc",
        url = "https://assets.xlab.my/models/NotoSansCJK-Regular.ttc",
        sizeBytes = 32_355_424L,
        sizeDisplay = "30.8 MB",
        sha256 = "3e7e5afaac2c6d872592d76abedac03a51c6f0fc42d11e311ff2816a6c368afe",
        description = "增强简体/繁体/日韩无衬线字体与微软雅黑回退渲染"
    )

    val FONT_NOTO_SERIF_CJK = OfficeFontSpec(
        id = "noto-serif-cjk",
        displayName = "Noto Serif CJK (宋体 / 明朝体)",
        fileName = "NotoSerifCJK-Regular.ttc",
        url = "https://assets.xlab.my/models/NotoSerifCJK-Regular.ttc",
        sizeBytes = 26_273_008L,
        sizeDisplay = "25.0 MB",
        sha256 = "5dec6bbce13a3bbf1487a022392c23e571abd0696a102f3715697420dd94b47a",
        description = "增强宋体、仿宋等衬线排版与 SimSun 回退渲染"
    )

    val ALL_FONTS = listOf(FONT_NOTO_SANS_CJK, FONT_NOTO_SERIF_CJK)

    private val SYSTEM_FONT_CANDIDATE_PATHS = listOf(
        "/system/fonts",
        "/system/product/fonts",
        "/system/fonts_system",
        "/apex/com.android.i18n/fonts",
        "/apex/com.android.runtime/fonts",
        "/data/fonts/files"
    )

    private const val CONNECT_TIMEOUT_MILLIS = 15_000
    private const val READ_TIMEOUT_MILLIS = 60_000
    private const val BUFFER_SIZE = 64 * 1024

    fun fontDirectory(context: Context): File {
        val appContext = context.applicationContext
        val dir = File(appContext.filesDir, "fonts")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Could not create font folder")
        }
        return dir
    }

    fun fontFile(context: Context, spec: OfficeFontSpec): File {
        return File(fontDirectory(context), spec.fileName)
    }

    fun isDownloaded(context: Context, spec: OfficeFontSpec): Boolean {
        val file = fontFile(context, spec)
        return file.isFile && file.length() == spec.sizeBytes
    }

    fun hasDownloadedAnyFonts(context: Context): Boolean {
        return ALL_FONTS.any { isDownloaded(context, it) }
    }

    fun deleteFont(context: Context, spec: OfficeFontSpec): Boolean {
        val file = fontFile(context, spec)
        return if (file.exists()) file.delete() else true
    }

    /**
     * Discovers all available system font directories on the current Android device.
     */
    fun systemFontDirectories(): List<File> {
        return SYSTEM_FONT_CANDIDATE_PATHS
            .map { File(it) }
            .filter { it.exists() && it.isDirectory }
    }

    /**
     * Aggregates all usable font directories for Typst / fontdb:
     * 1. Downloaded font directory (if it exists)
     * 2. Available Android system font directories
     */
    fun availableFontDirectories(context: Context): List<File> {
        val result = mutableListOf<File>()

        // 1. App-downloaded fonts directory
        runCatching {
            val appFontDir = fontDirectory(context)
            if (appFontDir.exists() && appFontDir.isDirectory) {
                result.add(appFontDir)
            }
        }

        // 2. System font directories
        result.addAll(systemFontDirectories())

        return result
    }

    suspend fun download(
        context: Context,
        spec: OfficeFontSpec,
        onProgress: (Float) -> Unit
    ): File {
        val targetFile = fontFile(context, spec)
        return withContext(Dispatchers.IO) {
            val tempFile = File(targetFile.parentFile, "${spec.fileName}.part")
            if (tempFile.exists() && !tempFile.delete()) {
                throw IOException("Could not reset previous font download")
            }

            val progressContext = currentCoroutineContext()
            val digest = MessageDigest.getInstance("SHA-256")
            var bytesDownloaded = 0L
            val totalBytes = spec.sizeBytes

            reportProgress(onProgress, 0f)
            val connection = openDownloadConnection(spec.url)
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
            if (!actualSha256.equals(spec.sha256, ignoreCase = true)) {
                tempFile.delete()
                throw IOException("Downloaded font checksum did not match")
            }
            if (tempFile.length() != spec.sizeBytes) {
                tempFile.delete()
                throw IOException("Downloaded font size did not match")
            }

            if (targetFile.exists() && !targetFile.delete()) {
                tempFile.delete()
                throw IOException("Could not replace previous font file")
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

/** UI-facing state of an Office font download. */
sealed interface OfficeFontUiState {
    object NotDownloaded : OfficeFontUiState
    data class Downloading(val progress: Float) : OfficeFontUiState
    object Downloaded : OfficeFontUiState
    data class Failed(val message: String?) : OfficeFontUiState
}
