package org.zenconverter.app.system

import android.content.Context
import android.net.Uri
import android.provider.MediaStore

/**
 * Local index of everything the user has imported, converted or saved through
 * ZenConverter. Nothing here leaves the device: it is a fast lookup over the
 * system MediaStore plus the app's own output folder, so the library shows a
 * file the moment it is written and never has to re-scan on open.
 */
class MediaLibrary(private val context: Context) {

    /** A single file the app can act on, whether it came from MediaStore or an output. */
    data class MediaItem(
        val uri: Uri,
        val displayName: String,
        val mimeType: String?,
        val sizeBytes: Long,
        val dateModifiedMs: Long,
        val relativePath: String?,
        val kind: Kind,
        /** True when the file was produced by a ZenConverter export. */
        val isAppOutput: Boolean
    ) {
        enum class Kind { Audio, Video, Image, Document, Other }

        val isMedia: Boolean get() = kind == Kind.Audio || kind == Kind.Video || kind == Kind.Image
    }

    /** Buckets the library is grouped into for the UI. */
    data class Snapshot(
        val audio: List<MediaItem>,
        val video: List<MediaItem>,
        val images: List<MediaItem>,
        val documents: List<MediaItem>
    ) {
        val totalCount: Int get() = audio.size + video.size + images.size + documents.size
        val all: List<MediaItem> get() = audio + video + images + documents
    }

    /**
     * Queries the external MediaStore for audio, video and images. Documents are
     * not indexed by MediaStore in a portable way, so they come from the app's
     * own output directory via [appOutputs].
     */
    fun queryMediaStore(
        includeAudio: Boolean = true,
        includeVideo: Boolean = true,
        includeImages: Boolean = true,
        limitPerKind: Int = DEFAULT_LIMIT
    ): List<MediaItem> {
        val results = mutableListOf<MediaItem>()
        if (includeAudio) {
            results += queryCollection(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaItem.Kind.Audio,
                limitPerKind
            )
        }
        if (includeVideo) {
            results += queryCollection(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaItem.Kind.Video,
                limitPerKind
            )
        }
        if (includeImages) {
            results += queryCollection(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaItem.Kind.Image,
                limitPerKind
            )
        }
        return results
    }

    private fun queryCollection(
        collection: Uri,
        kind: MediaItem.Kind,
        limit: Int
    ): List<MediaItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH
        )
        val items = mutableListOf<MediaItem>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                while (cursor.moveToNext() && items.size < limit) {
                    val id = cursor.getLong(idColumn)
                    val relativePath = cursor.getString(pathColumn)
                    items += MediaItem(
                        uri = Uri.withAppendedPath(collection, id.toString()),
                        displayName = cursor.getString(nameColumn) ?: continue,
                        mimeType = cursor.getString(mimeColumn),
                        sizeBytes = cursor.getLong(sizeColumn),
                        dateModifiedMs = cursor.getLong(dateColumn) * 1000L,
                        relativePath = relativePath,
                        kind = kind,
                        isAppOutput = relativePath?.contains(OUTPUT_DIRECTORY) == true
                    )
                }
            }
        }
        return items
    }

    /**
     * Files produced by the app's own exports. [treeUri] is the user-granted
     * output folder when they picked one; otherwise the app's default output
     * directory is used.
     */
    fun appOutputs(treeUri: Uri?): List<MediaItem> {
        if (treeUri == null) return emptyList()
        return runCatching {
            FolderWatcher(context).currentDocuments(treeUri).map { document ->
                MediaItem(
                    uri = document.uri,
                    displayName = document.displayName,
                    mimeType = document.mimeType,
                    sizeBytes = 0L,
                    dateModifiedMs = 0L,
                    relativePath = null,
                    kind = kindForMime(document.mimeType, document.displayName),
                    isAppOutput = true
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Builds the grouped view the library screen renders. */
    fun snapshot(treeUri: Uri? = null, limitPerKind: Int = DEFAULT_LIMIT): Snapshot {
        val media = queryMediaStore(limitPerKind = limitPerKind)
        val outputs = appOutputs(treeUri)
        val combined = (media + outputs).distinctBy { it.uri.toString() }
        return Snapshot(
            audio = combined.filter { it.kind == MediaItem.Kind.Audio },
            video = combined.filter { it.kind == MediaItem.Kind.Video },
            images = combined.filter { it.kind == MediaItem.Kind.Image },
            documents = combined.filter { it.kind == MediaItem.Kind.Document }
        )
    }

    companion object {
        const val DEFAULT_LIMIT = 500
        const val OUTPUT_DIRECTORY = "ZenConverter"

        fun kindForMime(mimeType: String?, displayName: String): MediaItem.Kind {
            val mime = mimeType?.lowercase()
            val extension = displayName.substringAfterLast('.', "").lowercase()
            return when {
                mime?.startsWith("audio/") == true || extension in AUDIO_EXTENSIONS -> MediaItem.Kind.Audio
                mime?.startsWith("video/") == true || extension in VIDEO_EXTENSIONS -> MediaItem.Kind.Video
                mime?.startsWith("image/") == true || extension in IMAGE_EXTENSIONS -> MediaItem.Kind.Image
                mime?.startsWith("text/") == true || extension in DOCUMENT_EXTENSIONS -> MediaItem.Kind.Document
                else -> MediaItem.Kind.Other
            }
        }

        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "alac", "aiff", "amr", "wma", "mka"
        )
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "m4v", "ts", "gif"
        )
        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "bmp", "tiff", "svg"
        )
        private val DOCUMENT_EXTENSIONS = setOf(
            "pdf", "txt", "md", "html", "htm", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
            "epub", "srt", "vtt", "lrc", "json", "zip", "7z", "tar", "gz", "csv", "rtf"
        )
    }
}
