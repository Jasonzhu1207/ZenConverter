package org.zenconverter.app.system

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Watches a user-chosen folder through the Storage Access Framework and emits
 * every change after the watch starts. Polling is used because tree URIs are not
 * real paths, so `FileObserver` cannot see them. Everything is local.
 *
 * The watcher reports additions, edits and removals so a file that is saved over
 * an existing name, or written in place, still shows up as a change rather than
 * being silently ignored.
 */
class FolderWatcher(
    private val context: Context,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS
) {
    data class WatchedDocument(
        val uri: Uri,
        val displayName: String,
        val mimeType: String?,
        val sizeBytes: Long = 0L,
        val lastModifiedMs: Long = 0L
    )

    /** What happened to a document between two polls. */
    enum class ChangeKind { Added, Modified, Removed }

    data class Change(val kind: ChangeKind, val document: WatchedDocument)

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

    /**
     * Emits a [Change] for every add, edit and removal observed after the flow
     * starts, keyed by document id. Size and modified time are compared so an
     * in-place overwrite counts as a [ChangeKind.Modified].
     */
    fun watchChanges(treeUri: Uri): Flow<Change> = flow {
        var previous = currentDocuments(treeUri).associateBy { it.uri.toString() }
        while (true) {
            delay(pollIntervalMs)
            val current = currentDocuments(treeUri).associateBy { it.uri.toString() }
            current.forEach { (key, document) ->
                val before = previous[key]
                when {
                    before == null -> emit(Change(ChangeKind.Added, document))
                    before.sizeBytes != document.sizeBytes ||
                        before.lastModifiedMs != document.lastModifiedMs ->
                        emit(Change(ChangeKind.Modified, document))
                }
            }
            previous.keys.filterNot { it in current }
                .forEach { key -> previous[key]?.let { emit(Change(ChangeKind.Removed, it)) } }
            previous = current
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
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val documents = mutableListOf<WatchedDocument>()
        runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idColumn) ?: continue
                    val displayName = cursor.getString(nameColumn) ?: continue
                    val mimeType = cursor.getString(mimeColumn)
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    documents.add(
                        WatchedDocument(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            displayName = displayName,
                            mimeType = mimeType,
                            sizeBytes = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0L,
                            lastModifiedMs = if (modifiedColumn >= 0) cursor.getLong(modifiedColumn) else 0L
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
