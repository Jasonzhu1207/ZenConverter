package org.zenconverter.app.ui.studio

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zenconverter.app.archive.ArchiveEngine
import org.zenconverter.app.audio.AudioStudioEngine
import org.zenconverter.app.audio.StudioOutput
import org.zenconverter.app.audio.TranscribedWord
import org.zenconverter.app.audio.TranscriptEditing
import org.zenconverter.app.audio.TranscriptSegment
import org.zenconverter.app.conversion.ConversionTaskStore
import org.zenconverter.app.i18n.localizedText
import org.zenconverter.app.system.FolderWatcher
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.settings.InsetGroupCard
import org.zenconverter.app.ui.settings.SettingsPage
import java.io.File

@Composable
internal fun ArchiveToolsScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) sources = uris
    }
    val pickArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val archive = copyToCache(context, uri, "zen-archive-input.zip")
            val targetDir = File(context.cacheDir, "zen-extract-${System.currentTimeMillis()}")
            status = runCatching {
                ArchiveEngine.extractZip(archive, targetDir)
            }.fold(
                onSuccess = {
                    targetDir.listFiles().orEmpty().forEach { file ->
                        StudioOutput.save(
                            context, file, file.name, "application/octet-stream", StudioOutput.Collection.Downloads
                        )
                    }
                    texts.archiveExtracted()
                },
                onFailure = { texts.studioFailed() }
            )
            archive.delete()
            busy = false
        }
    }
    val pickTarget = rememberFolderPicker { treeUri ->
        busy = true
        scope.launch {
            val archive = File(context.cacheDir, "zen-created-${System.currentTimeMillis()}.zip")
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val files = sources.mapNotNull { copyToCache(context, it, "zen-archive-${System.nanoTime()}") }
                    ArchiveEngine.createZip(files, archive)
                    files.forEach { it.delete() }
                }.fold(
                    onSuccess = {
                        StudioOutput.save(
                            context, archive, archive.name, "application/zip", StudioOutput.Collection.Downloads
                        )
                        texts.archiveCreated()
                    },
                    onFailure = { texts.studioFailed() }
                )
            }
            archive.delete()
            busy = false
        }
    }

    SettingsPage(texts.archiveTitle(), texts.studioBack(), onBack) {
        item("archive-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.archiveDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        texts.archiveSevenZipNote(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pickFiles.launch(arrayOf("*/*")) }) {
                            Text(texts.archivePickFiles())
                        }
                        OutlinedButton(enabled = sources.isNotEmpty() && !busy, onClick = { pickTarget() }) {
                            Text(texts.archiveCreate())
                        }
                    }
                    Text(
                        "${sources.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(enabled = !busy, onClick = { pickArchive.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) {
                        Text(texts.archiveExtract())
                    }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private fun copyToCache(context: android.content.Context, uri: Uri, name: String): File {
    val target = File(context.cacheDir, name)
    context.contentResolver.openInputStream(uri)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }
    return target
}

@Composable
internal fun FolderWatchScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val watcher = remember { FolderWatcher(context) }
    var watching by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf<List<String>>(emptyList()) }
    var treeUri by remember { mutableStateOf<Uri?>(null) }
    var watchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val pickFolder = rememberFolderPicker { uri ->
        treeUri = uri
        detected = watcher.currentDocuments(uri).map { it.displayName }
    }

    SettingsPage(texts.folderWatchTitle(), texts.studioBack(), onBack) {
        item("watch-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.folderWatchDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = pickFolder) { Text(texts.studioPickFolder()) }
                        OutlinedButton(
                            enabled = treeUri != null && !watching,
                            onClick = {
                                val uri = treeUri ?: return@OutlinedButton
                                watching = true
                                watchJob = scope.launch {
                                    watcher.watch(uri).collect { document ->
                                        detected = detected + document.displayName
                                        ConversionTaskStore.showMessage(
                                            localizedText(
                                                org.zenconverter.app.R.string.folder_watch_found,
                                                document.displayName
                                            )
                                        )
                                    }
                                }
                            }
                        ) { Text(texts.folderWatchStart()) }
                        OutlinedButton(
                            enabled = watching,
                            onClick = {
                                watchJob?.cancel()
                                watchJob = null
                                watching = false
                            }
                        ) { Text(texts.folderWatchStop()) }
                    }
                    if (watching) {
                        Text(
                            texts.folderWatchActive(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    detected.take(20).forEach { name ->
                        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SplitSilenceScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var thresholdDb by remember { mutableStateOf(-35f) }
    val pickAudio = rememberAudioPicker { uri -> sourceUri = uri }

    SettingsPage(texts.splitSilenceTitle(), texts.studioBack(), onBack) {
        item("split-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.splitSilenceDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = pickAudio) { Text(texts.studioPickAudio()) }
                        OutlinedButton(
                            enabled = sourceUri != null && !busy,
                            onClick = {
                                val uri = sourceUri ?: return@OutlinedButton
                                busy = true
                                scope.launch {
                                    val outputDir = File(context.cacheDir, "zen-split-${System.currentTimeMillis()}")
                                    val parts = AudioStudioEngine.splitBySilence(
                                        context, uri, outputDir, thresholdDb.toDouble()
                                    )
                                    status = if (parts.isEmpty()) {
                                        texts.studioFailed()
                                    } else {
                                        parts.forEach { part ->
                                            StudioOutput.save(
                                                context, part, part.name, "audio/mpeg", StudioOutput.Collection.Music
                                            )
                                        }
                                        texts.splitSilenceDone(parts.size)
                                    }
                                    outputDir.deleteRecursively()
                                    busy = false
                                }
                            }
                        ) { Text(texts.splitSilenceRun()) }
                    }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CutTranscriptScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var wordInput by remember { mutableStateOf("") }
    var deletedIndexes by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val pickAudio = rememberAudioPicker { uri -> sourceUri = uri }

    SettingsPage(texts.cutTranscriptTitle(), texts.studioBack(), onBack) {
        item("cut-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.cutTranscriptDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = pickAudio) { Text(texts.studioPickAudio()) }
                    OutlinedTextField(
                        value = wordInput,
                        onValueChange = { wordInput = it },
                        label = { Text("start end word") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = deletedIndexes,
                        onValueChange = { deletedIndexes = it },
                        label = { Text("1,3,5") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = sourceUri != null && wordInput.isNotBlank() && !busy,
                        onClick = {
                            val uri = sourceUri ?: return@Button
                            busy = true
                            scope.launch {
                                val words = parseWords(wordInput)
                                val segments = listOf(
                                    TranscriptSegment(
                                        startSeconds = words.firstOrNull()?.startSeconds ?: 0.0,
                                        endSeconds = words.lastOrNull()?.endSeconds ?: 0.0,
                                        text = words.joinToString(" ") { it.text },
                                        words = words
                                    )
                                )
                                val deleted = deletedIndexes.split(',').mapNotNull { it.trim().toIntOrNull() }
                                    .map { it - 1 }.toSet()
                                val plan = TranscriptEditing.plan(segments, deleted)
                                val temp = tempOutput(context, "zen-cut", "mp3")
                                val ok = AudioStudioEngine.cutSegments(context, uri, plan.keepSegments, temp)
                                status = if (ok) {
                                    StudioOutput.save(
                                        context, temp, "zenconverter-cut-${System.currentTimeMillis()}.mp3",
                                        "audio/mpeg", StudioOutput.Collection.Music
                                    )
                                    temp.delete()
                                    texts.studioSaved()
                                } else {
                                    temp.delete()
                                    texts.studioFailed()
                                }
                                busy = false
                            }
                        }
                    ) { Text(texts.cutTranscriptApply()) }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private fun parseWords(input: String): List<TranscribedWord> =
    input.lineSequence().mapNotNull { line ->
        val parts = line.trim().split(Regex("\\s+"), limit = 3)
        if (parts.size < 3) return@mapNotNull null
        val start = parts[0].toDoubleOrNull() ?: return@mapNotNull null
        val end = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        TranscribedWord(parts[2], start, end)
    }.toList()
