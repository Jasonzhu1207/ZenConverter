package org.zenconverter.app.system

/**
 * Classifies a file into the bucket the media library groups by. Pure Kotlin so
 * it can be unit tested on the JVM; the MediaStore query stays in MediaLibrary.
 */
object MediaKinds {

    enum class Kind { Audio, Video, Image, Document, Other }

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

    fun of(mimeType: String?, displayName: String): Kind {
        val mime = mimeType?.lowercase()
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return when {
            mime?.startsWith("audio/") == true || extension in AUDIO_EXTENSIONS -> Kind.Audio
            mime?.startsWith("video/") == true || extension in VIDEO_EXTENSIONS -> Kind.Video
            mime?.startsWith("image/") == true || extension in IMAGE_EXTENSIONS -> Kind.Image
            mime?.startsWith("text/") == true || extension in DOCUMENT_EXTENSIONS -> Kind.Document
            else -> Kind.Other
        }
    }
}
