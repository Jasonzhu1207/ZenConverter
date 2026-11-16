package org.zenconverter.app.conversion

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf

/**
 * Hand-off point for files that should join the conversion queue without the user
 * picking them, such as files the folder watcher notices. The home screen drains
 * it, so the watcher never needs a reference to the activity or the queue itself.
 */
object QueueInbox {
    data class PendingFile(
        val uri: Uri,
        val mimeType: String? = null,
        /** Optional output format key (see [org.zenconverter.app.conversion.TargetId]) chosen by the user. */
        val targetFormatKey: String? = null
    )

    private val pending = mutableStateListOf<PendingFile>()

    /** Number of files waiting to be added; read this to react to new arrivals. */
    val size: Int get() = pending.size

    fun enqueue(uri: Uri, mimeType: String? = null, targetFormatKey: String? = null) {
        pending.add(PendingFile(uri, mimeType, targetFormatKey))
    }

    /** Removes and returns everything queued so far. */
    fun drain(): List<PendingFile> {
        val drained = pending.toList()
        pending.clear()
        return drained
    }
}
