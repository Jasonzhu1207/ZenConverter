package org.zenconverter.app.updates

import android.content.Context
import android.os.Environment
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

object ApkUpdateDownloader {
    private const val CONNECT_TIMEOUT_MILLIS = 15_000
    private const val READ_TIMEOUT_MILLIS = 30_000
    private const val BUFFER_SIZE = 64 * 1024

    suspend fun download(
        context: Context,
        release: UpdateRelease,
        onProgress: (DownloadProgress) -> Unit
    ): DownloadedUpdate {
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            val updateRoot = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: appContext.filesDir
            val downloadDir = File(
                updateRoot,
                "updates"
            )
            if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                throw IOException("Could not create update download folder")
            }

            val assetName = release.assetName.safeApkFileName()
            val targetFile = File(downloadDir, assetName)
            val tempFile = File(downloadDir, "$assetName.part")
            if (tempFile.exists() && !tempFile.delete()) {
                throw IOException("Could not reset previous update download")
            }

            val progressContext = currentCoroutineContext()
            val digest = MessageDigest.getInstance("SHA-256")
            var bytesDownloaded = 0L

            val connection = openDownloadConnection(release.downloadUrl)
            try {
                val totalBytes = connection.contentLengthLong
                    .takeIf { it > 0L }
                    ?: release.sizeBytes
                reportProgress(onProgress, DownloadProgress(0L, totalBytes))

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
                                DownloadProgress(bytesDownloaded, totalBytes)
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
            val expectedSha256 = release.sha256
            if (
                expectedSha256 != null &&
                !actualSha256.equals(expectedSha256, ignoreCase = true)
            ) {
                tempFile.delete()
                throw IOException("Downloaded APK checksum did not match")
            }

            if (targetFile.exists() && !targetFile.delete()) {
                tempFile.delete()
                throw IOException("Could not replace previous update APK")
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            DownloadedUpdate(file = targetFile, sha256 = actualSha256)
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
        onProgress: (DownloadProgress) -> Unit,
        progress: DownloadProgress
    ) {
        withContext(Dispatchers.Main) {
            onProgress(progress)
        }
    }

    private fun String.safeApkFileName(): String {
        val cleaned = substringAfterLast('/')
            .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
            .takeIf { it.endsWith(".apk", ignoreCase = true) }
        return cleaned ?: "ZenConverter-update.apk"
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte)
        }
    }
}
