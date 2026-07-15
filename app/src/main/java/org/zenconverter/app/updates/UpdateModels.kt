package org.zenconverter.app.updates

import java.io.File

enum class UpdateChannel {
    Stable,
    Preview
}

data class InstalledAppVersion(
    val versionName: String,
    val versionCode: Long
)

data class UpdateRelease(
    val channel: UpdateChannel,
    val versionName: String,
    val versionCode: Long,
    val tagName: String,
    val releasePageUrl: String,
    val downloadUrl: String,
    val assetName: String,
    val sizeBytes: Long?,
    val sha256: String?,
    val publishedAt: String?,
    val notes: String?
)

sealed interface UpdateCheckResult {
    data class Available(val release: UpdateRelease) : UpdateCheckResult
    data class UpToDate(val latest: UpdateRelease) : UpdateCheckResult
    data class Failed(
        val reason: UpdateFailureReason,
        val detail: String? = null
    ) : UpdateCheckResult
}

enum class UpdateFailureReason {
    Network,
    NoRelease,
    NoApkAsset,
    MissingVersionMetadata,
    InvalidResponse
}

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long?
) {
    val fraction: Float?
        get() {
            val total = totalBytes?.takeIf { it > 0L } ?: return null
            return (bytesDownloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}

data class DownloadedUpdate(
    val file: File,
    val sha256: String
)
