package org.zenconverter.app.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Publishes a studio result file into the shared Downloads/Music collection so
 * the user can find it from a file manager. Uses MediaStore scoped storage, so
 * no broad storage permission is required on modern Android.
 */
object StudioOutput {
    enum class Collection(val relativePath: String) {
        Music("Music/ZenConverter"),
        Movies("Movies/ZenConverter"),
        Downloads("Download/ZenConverter"),
        Documents("Documents/ZenConverter")
    }

    data class Saved(val uri: Uri, val displayName: String)

    fun save(
        context: Context,
        source: File,
        displayName: String,
        mimeType: String,
        collection: Collection
    ): Saved? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, collection.relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                put(
                    MediaStore.MediaColumns.DATA,
                    File(
                        Environment.getExternalStoragePublicDirectory(collection.relativePath.substringBefore('/')),
                        displayName
                    ).absolutePath
                )
            }
        }
        val collectionUri = collectionUriFor(collection)
        val uri = runCatching { resolver.insert(collectionUri, values) }.getOrNull() ?: return null
        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            }
        }.onFailure {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
        return Saved(uri, displayName)
    }

    private fun collectionUriFor(collection: Collection): Uri = when (collection) {
        Collection.Movies -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        Collection.Music -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        Collection.Downloads, Collection.Documents -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }
    }
}
