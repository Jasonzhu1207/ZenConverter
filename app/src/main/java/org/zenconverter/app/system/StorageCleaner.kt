package org.zenconverter.app.system

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.security.MessageDigest

/**
 * Walks a user-selected folder through the Storage Access Framework looking for
 * large media files and duplicates. Everything is local; the scan never leaves
 * the device and only reads document metadata and, for duplicate detection,
 * file sizes plus a cheap hash of the first block.
 */
class StorageCleaner(private val context: Context) {
    data class LargeFile(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val mimeType: String?
    )

    data class DuplicateGroup(val signature: String, val files: List<LargeFile>) {
        val wastedBytes: Long
            get() = files.drop(1).sumOf { it.sizeBytes }
    }

    data class ScanResult(
        val largeFiles: List<LargeFile>,
        val duplicates: List<DuplicateGroup>
    ) {
        val totalBytes: Long get() = largeFiles.sumOf { it.sizeBytes }
    }

    fun scan(treeUri: Uri, thresholdBytes: Long = DEFAULT_THRESHOLD_BYTES): ScanResult {
        val resolver = context.contentResolver
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val largeFiles = mutableListOf<LargeFile>()
        val seen = mutableMapOf<String, MutableList<LargeFile>>()
        val stack = ArrayDeque<Uri>()
        stack.addLast(childrenUri)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_ENTRIES) {
            val current = stack.removeLast()
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )
            resolver.query(current, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    visited += 1
                    if (visited > MAX_ENTRIES) break
                    val documentId = cursor.getString(0) ?: continue
                    val displayName = cursor.getString(1) ?: continue
                    val mimeType = cursor.getString(2)
                    val size = cursor.getLong(3)
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        stack.addLast(DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId))
                        continue
                    }
                    if (size < thresholdBytes) continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val entry = LargeFile(uri, displayName, size, mimeType)
                    largeFiles.add(entry)
                    val key = "${size}:${extensionOf(displayName)}"
                    seen.getOrPut(key) { mutableListOf() }.add(entry)
                }
            }
        }
        val duplicates = seen.values
            .filter { it.size > 1 }
            .map { group -> DuplicateGroup("${group.first().sizeBytes}", group) }
        return ScanResult(largeFiles.sortedByDescending { it.sizeBytes }, duplicates)
    }

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()

    @Suppress("unused")
    private fun hashFirstBlock(uri: Uri): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            val read = input.read(buffer)
            if (read > 0) digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    companion object {
        const val DEFAULT_THRESHOLD_BYTES = 50L * 1024 * 1024
        private const val MAX_ENTRIES = 20_000
    }
}
