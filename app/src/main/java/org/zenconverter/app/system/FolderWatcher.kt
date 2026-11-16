package org.zenconverter.app.system

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Watches a user-chosen folder through the Storage Access Framework and emits
 * files that appear after the watch starts. Polling is used because tree URIs
 * are not real paths, so `FileObserver` cannot see them. Everything is local.
 */
class FolderWatcher(
    private val context: Context,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) {
    data class WatchedDocument(val uri: Uri, val displayName: String, val mimeType: String?)

    /** Emits only documents not present at the time the flow started. */
    fun watch(treeUri: Uri): Flow<WatchedDocument> = flow {
        val seen = currentDocuments(treeUri).mapTo(mutableSetOf()) { it.uri.toString() }
        while (true) {
            delay(pollIntervalMs)
            val documents = currentDocuments(treeUri)
            documents.forEach { document ->
                if (seen.add(document.uri.toString())) emit(document)
            }
        }
    }

    /** One-shot listing, used to seed the watcher and to show a preview. */
    fun currentDocuments(treeUri: Uri): List<WatchedDocument> {
        val resolver = context.contentResolver
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val documents = mutableListOf<WatchedDocument>()
        runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(0) ?: continue
                    val displayName = cursor.getString(1) ?: continue
                    val mimeType = cursor.getString(2)
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    documents.add(
                        WatchedDocument(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            displayName = displayName,
                            mimeType = mimeType
                        )
                    )
                }
            }
        }
        return documents
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 10_000L
    }
}
