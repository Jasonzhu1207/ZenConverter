package org.zenconverter.app.conversion

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale

data class FileBasicInfo(
    val formatLabel: String? = null,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitrateBitsPerSecond: Long? = null,
    val frameRate: Float? = null,
    val pageCount: Int? = null,
    val itemCount: Int? = null
)

object FileBasicInfoReader {
    fun read(
        context: Context,
        uri: Uri,
        displayName: String,
        mimeType: String?,
        fallbackSizeBytes: Long? = null,
        formatOverride: String? = null,
        itemCount: Int? = null
    ): FileBasicInfo {
        val normalizedMimeType = mimeType.orEmpty().lowercase(Locale.US)
        val extension = extensionFor(displayName)
        val sizeBytes = querySizeBytes(context, uri) ?: fallbackSizeBytes
        val baseInfo = FileBasicInfo(
            formatLabel = formatOverride ?: formatLabelFor(extension, normalizedMimeType),
            sizeBytes = sizeBytes,
            itemCount = itemCount?.takeIf { it > 1 }
        )

        return when {
            isLikelyMedia(normalizedMimeType, extension) -> {
                readMediaInfo(context, uri, baseInfo)
            }
            isLikelyPdf(normalizedMimeType, extension) -> {
                readPdfInfo(context, uri, baseInfo)
            }
            isLikelyImage(normalizedMimeType, extension) -> {
                readImageInfo(context, uri, baseInfo)
            }
            else -> baseInfo
        }
    }

    fun aggregate(
        documents: List<FileBasicInfo?>,
        fallbackSizeBytes: Long?,
        formatLabel: String? = null
    ): FileBasicInfo {
        val sizes = documents.mapNotNull { it?.sizeBytes }
        val totalSize = sizes.takeIf { it.size == documents.size }?.sum() ?: fallbackSizeBytes
        return FileBasicInfo(
            formatLabel = formatLabel,
            sizeBytes = totalSize,
            itemCount = documents.size.takeIf { it > 1 }
        )
    }

    private fun readMediaInfo(
        context: Context,
        uri: Uri,
        baseInfo: FileBasicInfo
    ): FileBasicInfo {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(context, uri)
            baseInfo.copy(
                durationMs = retriever.metadataLong(MediaMetadataRetriever.METADATA_KEY_DURATION),
                width = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                height = retriever.metadataInt(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                bitrateBitsPerSecond = retriever.metadataLong(MediaMetadataRetriever.METADATA_KEY_BITRATE),
                frameRate = retriever.metadataFloat(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            )
        }.getOrDefault(baseInfo).also {
            runCatching { retriever.release() }
        }
    }

    private fun readImageInfo(
        context: Context,
        uri: Uri,
        baseInfo: FileBasicInfo
    ): FileBasicInfo {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            val width = options.outWidth.takeIf { it > 0 }
            val height = options.outHeight.takeIf { it > 0 }
            baseInfo.copy(width = width, height = height)
        }.getOrDefault(baseInfo)
    }

    private fun readPdfInfo(
        context: Context,
        uri: Uri,
        baseInfo: FileBasicInfo
    ): FileBasicInfo {
        val descriptor = runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")
        }.getOrNull() ?: return baseInfo
        return runCatching {
            descriptor.use {
                PdfRenderer(it).use { renderer ->
                    baseInfo.copy(pageCount = renderer.pageCount.takeIf { count -> count > 0 })
                }
            }
        }.getOrDefault(baseInfo)
    }

    private fun querySizeBytes(context: Context, uri: Uri): Long? {
        if (uri.scheme == URI_SCHEME_FILE) {
            return uri.path?.let { path -> File(path).length().takeIf { it >= 0L } }
        }
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex < 0 || cursor.isNull(sizeIndex)) return@use null
                cursor.getLong(sizeIndex).takeIf { it >= 0L }
            }
        }.getOrNull()
    }

    private fun MediaMetadataRetriever.metadataLong(keyCode: Int): Long? {
        return extractMetadata(keyCode)?.toLongOrNull()?.takeIf { it > 0L }
    }

    private fun MediaMetadataRetriever.metadataInt(keyCode: Int): Int? {
        return extractMetadata(keyCode)?.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun MediaMetadataRetriever.metadataFloat(keyCode: Int): Float? {
        return extractMetadata(keyCode)?.toFloatOrNull()?.takeIf { it > 0f }
    }

    private fun extensionFor(displayName: String): String {
        return displayName.substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.length in 1..12 }
            ?.lowercase(Locale.US)
            .orEmpty()
    }

    private fun formatLabelFor(extension: String, mimeType: String): String? {
        if (extension.isNotBlank()) return extension.uppercase(Locale.US)
        return when {
            mimeType == "video/quicktime" -> "MOV"
            mimeType == "video/x-matroska" -> "MKV"
            mimeType == "image/jpeg" -> "JPEG"
            mimeType == "application/pdf" -> "PDF"
            mimeType.contains('/') -> mimeType.substringAfter('/').uppercase(Locale.US)
            else -> null
        }
    }

    private fun isLikelyMedia(mimeType: String, extension: String): Boolean {
        return mimeType.startsWith("video/") ||
            mimeType.startsWith("audio/") ||
            extension in MEDIA_EXTENSIONS
    }

    private fun isLikelyImage(mimeType: String, extension: String): Boolean {
        return mimeType.startsWith("image/") || extension in IMAGE_EXTENSIONS
    }

    private fun isLikelyPdf(mimeType: String, extension: String): Boolean {
        return mimeType == "application/pdf" || extension == "pdf"
    }

    private const val URI_SCHEME_FILE = "file"
    private val MEDIA_EXTENSIONS = setOf(
        "mp4",
        "m4v",
        "mov",
        "mkv",
        "webm",
        "avi",
        "3gp",
        "3gpp",
        "ts",
        "mts",
        "m2ts",
        "mpg",
        "mpeg",
        "vob",
        "flv",
        "ogv",
        "mp3",
        "m4a",
        "aac",
        "wav",
        "flac",
        "wma",
        "ogg"
    )
    private val IMAGE_EXTENSIONS = setOf(
        "jpg",
        "jpeg",
        "jfif",
        "jpe",
        "png",
        "webp",
        "gif",
        "heic",
        "heif",
        "ico"
    )
}
