package org.zenconverter.app.ui.studio

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zenconverter.app.conversion.ConversionTaskStore
import org.zenconverter.app.conversion.QueueInbox
import org.zenconverter.app.i18n.localizedText
import org.zenconverter.app.system.FolderWatcher
import org.zenconverter.app.system.MediaLibrary
import org.zenconverter.app.ui.AppIcon
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.settings.InsetGroupCard
import org.zenconverter.app.ui.settings.SettingsPage

/**
 * Shows every file the app can act on: the device MediaStore plus the app's own
 * exports, grouped by kind. The list refreshes on open and on demand, and a
 * folder can be watched so newly saved files appear without re-picking.
 */
@Composable
internal fun MediaLibraryScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember { MediaLibrary(context) }
    val watcher = remember { FolderWatcher(context) }
    var snapshot by remember { mutableStateOf<MediaLibrary.Snapshot?>(null) }
    var treeUri by remember { mutableStateOf<Uri?>(null) }
    var watching by remember { mutableStateOf(false) }

    suspend fun load() {
        snapshot = withContext(Dispatchers.IO) { library.snapshot(treeUri) }
    }

    fun refresh() {
        scope.launch { load() }
    }

    val pickFolder = rememberFolderPicker { uri ->
        treeUri = uri
        refresh()
    }

    LaunchedEffect(treeUri) { load() }

    SettingsPage(texts.libraryTitle(), texts.studioBack(), onBack) {
        item("library-actions") {
            InsetGroupCard {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { refresh() }) {
                        AppIcon(Icons.Rounded.Refresh, null, MaterialTheme.colorScheme.onPrimary, Modifier.size(18.dp))
                        Text("  " + texts.libraryRefresh())
                    }
                    OutlinedButton(onClick = pickFolder) { Text(texts.libraryWatchFolder()) }
                }
                if (treeUri != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            enabled = !watching,
                            onClick = {
                                watching = true
                                scope.launch {
                                    watcher.watchChanges(treeUri!!).collect { change ->
                                        refresh()
                                        if (change.kind != FolderWatcher.ChangeKind.Removed) {
                                            ConversionTaskStore.showMessage(
                                                localizedText(
                                                    org.zenconverter.app.R.string.library_app_output
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        ) { Text(texts.libraryWatching()) }
                    }
                }
            }
        }

        val current = snapshot
        if (current == null || current.totalCount == 0) {
            item("library-empty") {
                InsetGroupCard {
                    Text(
                        texts.libraryEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            return@SettingsPage
        }

        libraryGroup(texts.libraryAudio(), current.audio, texts) { uri, mime ->
            QueueInbox.enqueue(uri, mime)
        }
        libraryGroup(texts.libraryVideo(), current.video, texts) { uri, mime ->
            QueueInbox.enqueue(uri, mime)
        }
        libraryGroup(texts.libraryImages(), current.images, texts) { uri, mime ->
            QueueInbox.enqueue(uri, mime)
        }
        libraryGroup(texts.libraryDocuments(), current.documents, texts) { uri, mime ->
            QueueInbox.enqueue(uri, mime)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.libraryGroup(
    title: String,
    items: List<MediaLibrary.MediaItem>,
    texts: UiText,
    onSendToQueue: (Uri, String?) -> Unit
) {
    if (items.isEmpty()) return
    item(key = "library-$title-header") {
        Text(
            text = "$title · ${texts.libraryFilesCount(items.size)}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 4.dp)
        )
    }
    items(items, key = { "library-${it.uri}" }) { item ->
        MediaLibraryRow(item, texts, onSendToQueue)
    }
}

@Composable
private fun MediaLibraryRow(
    item: MediaLibrary.MediaItem,
    texts: UiText,
    onSendToQueue: (Uri, String?) -> Unit
) {
    InsetGroupCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSendToQueue(item.uri, item.mimeType) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(iconForKind(item.kind), null, MaterialTheme.colorScheme.primary, Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(item.mimeType ?: item.kind.name)
                        if (item.sizeBytes > 0) append(" · ${formatBytes(item.sizeBytes)}")
                        if (item.isAppOutput) append(" · ${texts.libraryAppOutput()}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun iconForKind(kind: MediaLibrary.MediaItem.Kind): ImageVector = when (kind) {
    MediaLibrary.MediaItem.Kind.Audio -> Icons.Rounded.AudioFile
    MediaLibrary.MediaItem.Kind.Video -> Icons.Rounded.Movie
    MediaLibrary.MediaItem.Kind.Image -> Icons.Rounded.Image
    MediaLibrary.MediaItem.Kind.Document -> Icons.Rounded.Description
    MediaLibrary.MediaItem.Kind.Other -> Icons.Rounded.FolderOpen
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format("%.1f %s", value, units[unitIndex])
}
