package org.zenconverter.app.ui.studio

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import org.zenconverter.app.audio.ChapterTimestamps
import org.zenconverter.app.audio.StudioOutput
import org.zenconverter.app.audio.TranscribedWord
import org.zenconverter.app.audio.TranscriptionEngine
import org.zenconverter.app.audio.TranscriptEditing
import org.zenconverter.app.audio.TranscriptSegment
import org.zenconverter.app.model.WhisperModelManager
import org.zenconverter.app.conversion.ConversionTaskStore
import org.zenconverter.app.ui.FileCategory
import org.zenconverter.app.i18n.localizedText
import org.zenconverter.app.conversion.QueueInbox
import org.zenconverter.app.system.FolderWatcher
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.settings.InsetGroupCard
import org.zenconverter.app.ui.settings.SettingsPage
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArchiveToolsScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var createMode by remember { mutableStateOf(true) }
    var sources by remember { mutableStateOf<List<File>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(ArchiveEngine.Format.Zip) }
    var level by remember { mutableStateOf(ArchiveEngine.Level.Normal) }
    var passwordEnabled by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var splitEnabled by remember { mutableStateOf(false) }
    var volumeIndex by remember { mutableStateOf(0) }
    var extractPassword by remember { mutableStateOf("") }
    var recent by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val volumeSizes = listOf(100L, 700L, 1024L)

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            sources = sources + uris.mapNotNull { uri ->
                runCatching { copyToCache(context, uri, "zen-archive-${System.nanoTime()}") }.getOrNull()
            }
        }
    }
    val pickFolder = rememberFolderPicker { uri ->
        busy = true
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val treeName = displayName(context, uri).ifBlank { "folder" }
                    val folder = File(context.cacheDir, "zen-folder-${System.nanoTime()}")
                    folder.mkdirs()
                    val folderFile = File(folder, treeName).also { it.mkdirs() }
                    copyTree(context, uri, folderFile)
                    sources = sources + folderFile
                }.fold(
                    onSuccess = { texts.archiveAddFolder() },
                    onFailure = { texts.studioFailed() }
                )
            }
            busy = false
        }
    }
    val pickArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val name = displayName(context, uri)
            val archive = copyToCache(
                context, uri,
                "zen-archive-input-${System.nanoTime()}" +
                    name.substringAfterLast('.', "").let { if (it.isBlank()) ".zip" else ".$it" }
            )
            val targetDir = File(context.cacheDir, "zen-extract-${System.currentTimeMillis()}")
            val encrypted = withContext(Dispatchers.IO) { ArchiveEngine.isEncrypted(archive) }
            if (encrypted && extractPassword.isBlank()) {
                status = texts.archiveEncrypted()
                busy = false
                return@launch
            }
            status = withContext(Dispatchers.IO) {
                runCatching {
                    if (encrypted) {
                        ArchiveEngine.extractZipWithPassword(archive, targetDir, extractPassword)
                    } else {
                        ArchiveEngine.extract(archive, targetDir)
                    }
                }.fold(
                    onSuccess = {
                        targetDir.listFiles().orEmpty().forEach { file ->
                            StudioOutput.save(
                                context, file, file.name, "application/octet-stream", StudioOutput.Collection.Downloads
                            )
                        }
                        recent = (listOf(name to texts.archiveExtracted()) + recent).take(5)
                        texts.archiveExtracted()
                    },
                    onFailure = { if (encrypted) texts.archivePasswordWrong() else texts.studioFailed() }
                )
            }
            archive.delete()
            targetDir.deleteRecursively()
            busy = false
        }
    }

    SettingsPage(texts.archiveTitle(), texts.studioBack(), onBack) {
        item("archive-mode") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioNote(texts.archiveDesc())
                    StudioFieldLabel(texts.archiveMode())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = createMode,
                            onClick = { createMode = true },
                            label = { Text(texts.archiveModeCreate()) }
                        )
                        FilterChip(
                            selected = !createMode,
                            onClick = { createMode = false },
                            label = { Text(texts.archiveModeExtract()) }
                        )
                    }
                }
            }
        }
        if (createMode) {
            item("archive-format") {
                InsetGroupCard {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioFieldLabel(texts.archiveFormat())
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ArchiveEngine.Format.entries.forEach { option ->
                                FilterChip(
                                    selected = format == option,
                                    onClick = { format = option },
                                    label = { Text(option.extension.uppercase()) }
                                )
                            }
                        }
                        StudioFieldLabel("${texts.archiveLevel()} · ${levelLabel(texts, level)}")
                        Slider(
                            value = level.ordinal.toFloat(),
                            onValueChange = { level = ArchiveEngine.Level.entries[it.toInt().coerceIn(0, 3)] },
                            valueRange = 0f..3f,
                            steps = 2
                        )
                        if (format == ArchiveEngine.Format.Zip) {
                            StudioSwitchRow(texts.archivePasswordProtect(), passwordEnabled) { passwordEnabled = it }
                            if (passwordEnabled) {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text(texts.archivePassword()) },
                                    placeholder = { Text(texts.archivePasswordHint()) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            StudioSwitchRow(texts.archiveSplit(), splitEnabled) { splitEnabled = it }
                            if (splitEnabled) {
                                StudioFieldLabel(texts.archiveVolumeSize())
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    volumeSizes.forEachIndexed { index, size ->
                                        FilterChip(
                                            selected = volumeIndex == index,
                                            onClick = { volumeIndex = index },
                                            label = { Text(if (size >= 1024) "${size / 1024} GB" else "$size MB") }
                                        )
                                    }
                                }
                            }
                        } else {
                            StudioNote(texts.archivePasswordNote())
                        }
                    }
                }
            }
            item("archive-contents") {
                InsetGroupCard {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(texts.archiveContents(), style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { pickFiles.launch(arrayOf("*/*")) }) {
                                Text(texts.archivePickFiles())
                            }
                            OutlinedButton(onClick = { pickFolder() }) {
                                Text(texts.archiveAddFolder())
                            }
                        }
                        sources.forEach { source ->
                            Text(
                                source.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val rawBytes = sources.sumOf { sizeOfFile(it) }
                        Text(
                            "${texts.archiveTotal()}: ${texts.archiveTotalValue(sources.size, archiveFormatBytes(rawBytes))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${texts.archiveEstimated()}: " +
                                archiveFormatBytes(ArchiveEngine.estimateArchiveBytes(sources, level)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = sources.isNotEmpty() && !busy,
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        val target = File(
                                            context.cacheDir,
                                            "zen-created-${System.currentTimeMillis()}.${format.extension}"
                                        )
                                        val options = ArchiveEngine.Options(
                                            level = level,
                                            password = password.takeIf { passwordEnabled && it.isNotBlank() },
                                            splitVolumeBytes = if (splitEnabled) {
                                                volumeSizes[volumeIndex] * 1024L * 1024L
                                            } else {
                                                0L
                                            }
                                        )
                                        status = withContext(Dispatchers.IO) {
                                            runCatching {
                                                if (format == ArchiveEngine.Format.Zip) {
                                                    ArchiveEngine.createZipWithOptions(sources, target, options)
                                                } else {
                                                    ArchiveEngine.create(format, sources, target)
                                                }
                                            }.fold(
                                                onSuccess = {
                                                    StudioOutput.save(
                                                        context, target, target.name,
                                                        "application/octet-stream", StudioOutput.Collection.Downloads
                                                    )
                                                    recent = (listOf(target.name to texts.archiveCreated()) + recent).take(5)
                                                    texts.archiveCreated()
                                                },
                                                onFailure = { texts.studioFailed() }
                                            )
                                        }
                                        target.delete()
                                        busy = false
                                    }
                                }
                            ) { Text(texts.archiveCreate()) }
                            OutlinedButton(enabled = !busy, onClick = {
                                pickArchive.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/x-tar",
                                        "application/x-7z-compressed",
                                        "application/octet-stream"
                                    )
                                )
                            }) { Text(texts.archiveExtract()) }
                        }
                    }
                }
            }
        } else {
            item("archive-extract") {
                InsetGroupCard {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioNote(texts.archiveEncrypted())
                        OutlinedTextField(
                            value = extractPassword,
                            onValueChange = { extractPassword = it },
                            label = { Text(texts.archivePassword()) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(enabled = !busy, onClick = {
                            pickArchive.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                    "application/x-tar",
                                    "application/x-7z-compressed",
                                    "application/octet-stream"
                                )
                            )
                        }) { Text(texts.archiveExtract()) }
                    }
                }
            }
        }
        item("archive-safety") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(texts.archiveSafety(), style = MaterialTheme.typography.titleSmall)
                    listOf(
                        texts.archivePathGuard() to texts.archiveOn(),
                        texts.archiveExpandCap() to "2 GB",
                        texts.archiveEntryCap() to "10,000",
                        texts.archiveOverwrite() to texts.archiveOverwriteAsk()
                    ).forEach { (label, value) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(
                                value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        item("archive-recent") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(texts.archiveRecent(), style = MaterialTheme.typography.titleSmall)
                    if (recent.isEmpty()) {
                        StudioNote(texts.archiveNoneYet())
                    } else {
                        recent.forEach { (name, note) ->
                            Text(
                                "$name · $note",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private fun levelLabel(texts: UiText, level: ArchiveEngine.Level): String = when (level) {
    ArchiveEngine.Level.Store -> texts.archiveLevelStore()
    ArchiveEngine.Level.Fast -> texts.archiveLevelFast()
    ArchiveEngine.Level.Normal -> texts.archiveLevelNormal()
    ArchiveEngine.Level.Maximum -> texts.archiveLevelMaximum()
}

private fun sizeOfFile(file: File): Long =
    if (file.isDirectory) file.listFiles().orEmpty().sumOf { sizeOfFile(it) } else file.length()

internal fun archiveFormatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/** Copies a picked folder tree into [target], one level at a time. */
private fun copyTree(context: android.content.Context, treeUri: Uri, target: File) {
    val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
        treeUri,
        android.provider.DocumentsContract.getTreeDocumentId(treeUri)
    )
    val projection = arrayOf(
        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
    )
    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
        while (cursor.moveToNext()) {
            val documentId = cursor.getString(0)
            val name = cursor.getString(1) ?: continue
            val mimeType = cursor.getString(2)
            val childUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            if (mimeType == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                val childDir = File(target, name).also { it.mkdirs() }
                copyTree(context, childUri, childDir)
            } else {
                runCatching {
                    context.contentResolver.openInputStream(childUri)?.use { input ->
                        File(target, name).outputStream().use { output -> input.copyTo(output) }
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

/** Best-effort file name for a content URI, falling back to the last path part. */
private fun displayName(context: android.content.Context, uri: Uri): String {
    val cursor = runCatching {
        context.contentResolver.query(uri, null, null, null, null)
    }.getOrNull()
    cursor?.use {
        val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && it.moveToFirst()) return it.getString(index)
    }
    return uri.lastPathSegment ?: "archive"
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
    val targetOptions = remember { FileCategory.Audio.formats }
    var targetFormat by remember { mutableStateOf(targetOptions.first()) }
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
                    Text(
                        texts.folderWatchConvertDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StudioChoiceRow(
                        label = texts.folderWatchTargetLabel(),
                        selected = targetFormat.key,
                        options = targetOptions.map { it.key },
                        onSelected = { key ->
                            targetOptions.firstOrNull { it.key == key }?.let { targetFormat = it }
                        }
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
                                        QueueInbox.enqueue(
                                            document.uri,
                                            document.mimeType,
                                            targetFormat.key
                                        )
                                        ConversionTaskStore.showMessage(
                                            localizedText(
                                                org.zenconverter.app.R.string.folder_watch_queued,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CutTranscriptScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var wordInput by remember { mutableStateOf("") }
    var deletedIndexes by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var transcribing by remember { mutableStateOf(false) }
    var words by remember { mutableStateOf<List<TranscribedWord>>(emptyList()) }
    var deleted by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var modelReady by remember { mutableStateOf(WhisperModelManager.isDownloaded(context)) }
    val engineAvailable = WhisperModelManager.INFERENCE_AVAILABLE
    val pickAudio = rememberAudioPicker { uri ->
        sourceUri = uri
        words = emptyList()
        deleted = emptySet()
        deletedIndexes = ""
    }

    SettingsPage(texts.cutTranscriptTitle(), texts.studioBack(), onBack) {
        item("cut-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.cutTranscriptDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        texts.transcribeCutWords(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = pickAudio) { Text(texts.studioPickAudio()) }
                    if (engineAvailable && !modelReady) {
                        OutlinedButton(
                            enabled = downloadProgress == null,
                            onClick = {
                                scope.launch {
                                    downloadProgress = 0f
                                    runCatching {
                                        WhisperModelManager.download(context) { downloadProgress = it }
                                    }.onSuccess {
                                        modelReady = true
                                        downloadProgress = null
                                        status = texts.studioModelReady()
                                    }.onFailure {
                                        downloadProgress = null
                                        status = texts.studioFailed()
                                    }
                                }
                            }
                        ) { Text(texts.transcribeDownloadModel()) }
                    }
                    downloadProgress?.let { progress ->
                        Text(
                            texts.transcribeDownloading((progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (engineAvailable && modelReady) {
                        OutlinedButton(
                            enabled = sourceUri != null && !transcribing && !busy,
                            onClick = {
                                val uri = sourceUri ?: return@OutlinedButton
                                transcribing = true
                                status = null
                                scope.launch {
                                    val segments = TranscriptionEngine.transcribe(context, uri)
                                    words = segments.flatMap { it.words }
                                    wordInput = words.joinToString("\n") { word ->
                                        "${word.startSeconds} ${word.endSeconds} ${word.text}"
                                    }
                                    status = if (words.isEmpty()) {
                                        texts.transcribeEmpty()
                                    } else {
                                        texts.cutTranscriptTranscribed(words.size)
                                    }
                                    transcribing = false
                                }
                            }
                        ) { Text(texts.cutTranscriptTranscribe()) }
                    }
                    OutlinedTextField(
                        value = wordInput,
                        onValueChange = {
                            wordInput = it
                            words = parseWords(it)
                            deleted = emptySet()
                            deletedIndexes = ""
                        },
                        label = { Text("start end word") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (words.isNotEmpty()) {
                        Text(
                            texts.cutTranscriptTapHint(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            words.forEachIndexed { index, word ->
                                val marked = (index + 1) in deleted
                                FilterChip(
                                    selected = marked,
                                    onClick = {
                                        deleted = if (marked) deleted - (index + 1) else deleted + (index + 1)
                                        deletedIndexes = deleted.sorted().joinToString(",")
                                    },
                                    label = { Text(word.text) }
                                )
                            }
                        }
                        if (deleted.isNotEmpty()) {
                            Text(
                                texts.cutTranscriptDeletedCount(deleted.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    OutlinedTextField(
                        value = deletedIndexes,
                        onValueChange = {
                            deletedIndexes = it
                            deleted = it.split(',').mapNotNull { part -> part.trim().toIntOrNull() }
                                .filter { it > 0 }.toSet()
                        },
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
                                val parsed = words.ifEmpty { parseWords(wordInput) }
                                val segments = listOf(
                                    TranscriptSegment(
                                        startSeconds = parsed.firstOrNull()?.startSeconds ?: 0.0,
                                        endSeconds = parsed.lastOrNull()?.endSeconds ?: 0.0,
                                        text = parsed.joinToString(" ") { it.text },
                                        words = parsed
                                    )
                                )
                                val plan = TranscriptEditing.plan(segments, deleted.map { it - 1 }.toSet())
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
                    if (transcribing) {
                        Text(
                            texts.transcribeRunning(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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

@Composable
internal fun PdfSplitScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var rangeText by remember { mutableStateOf("1-") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) sourceUri = uri
    }

    SettingsPage(texts.pdfSplitTitle(), texts.studioBack(), onBack) {
        item("pdf-split-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.pdfSplitDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) {
                            Text(texts.studioPickAudio())
                        }
                        OutlinedTextField(
                            value = rangeText,
                            onValueChange = { rangeText = it },
                            label = { Text(texts.pdfSplitRangeLabel()) },
                            singleLine = true
                        )
                    }
                    OutlinedButton(
                        enabled = sourceUri != null && !busy,
                        onClick = {
                            val uri = sourceUri ?: return@OutlinedButton
                            busy = true
                            scope.launch {
                                val source = copyToCache(context, uri, "zen-pdf-split-${System.nanoTime()}.pdf")
                                val target = File(context.cacheDir, "zen-pdf-split-${System.currentTimeMillis()}.pdf")
                                status = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val pages = com.tom_roush.pdfbox.pdmodel.PDDocument.load(source).use { doc ->
                                            org.zenconverter.app.pdf.PdfTools.parsePageRanges(rangeText, doc.numberOfPages)
                                        }
                                        if (pages.isEmpty()) throw IllegalStateException("empty range")
                                        org.zenconverter.app.pdf.PdfPageEditor.split(source, pages, target)
                                    }.fold(
                                        onSuccess = {
                                            StudioOutput.save(
                                                context, target, target.name, "application/pdf",
                                                StudioOutput.Collection.Documents
                                            )
                                            texts.pdfSplitDone()
                                        },
                                        onFailure = { texts.pdfSplitInvalid() }
                                    )
                                }
                                source.delete()
                                target.delete()
                                busy = false
                            }
                        }
                    ) { Text(texts.pdfSplitAction()) }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PdfCreateScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val pickText = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) sourceUri = uri
    }

    SettingsPage(texts.pdfCreateTitle(), texts.studioBack(), onBack) {
        item("pdf-create-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.pdfCreateDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pickText.launch(arrayOf("text/*", "application/octet-stream")) }) {
                            Text(texts.pdfCreatePick())
                        }
                        OutlinedButton(
                            enabled = sourceUri != null && !busy,
                            onClick = {
                                val uri = sourceUri ?: return@OutlinedButton
                                busy = true
                                scope.launch {
                                    status = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val text = context.contentResolver.openInputStream(uri)
                                                ?.bufferedReader()?.readText().orEmpty()
                                            val name = uri.lastPathSegment?.lowercase().orEmpty()
                                            val plain = when {
                                                name.endsWith(".html") || name.endsWith(".htm") ->
                                                    org.zenconverter.app.pdf.PdfTools.htmlToPlainText(text)
                                                name.endsWith(".md") || name.endsWith(".markdown") ->
                                                    org.zenconverter.app.pdf.PdfTools.markdownToPlainText(text)
                                                else -> text
                                            }
                                            val target = File(context.cacheDir, "zen-text-${System.currentTimeMillis()}.pdf")
                                            org.zenconverter.app.pdf.PdfWriter.writeTextPdf(
                                                org.zenconverter.app.pdf.PdfTools.layout(plain, 90), target
                                            )
                                            StudioOutput.save(
                                                context, target, target.name, "application/pdf",
                                                StudioOutput.Collection.Documents
                                            )
                                            target.delete()
                                            texts.pdfCreateDone()
                                        }.getOrElse { texts.studioFailed() }
                                    }
                                    busy = false
                                }
                            }
                        ) { Text(texts.pdfCreateAction()) }
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
internal fun EpubToolsScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) sourceUri = uri
    }

    SettingsPage(texts.epubTitle(), texts.studioBack(), onBack) {
        item("epub-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.epubDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { pickFile.launch(arrayOf("text/*", "application/epub+zip", "application/octet-stream")) }) {
                        Text(texts.epubPick())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = sourceUri != null && !busy,
                            onClick = {
                                val uri = sourceUri ?: return@OutlinedButton
                                busy = true
                                scope.launch {
                                    status = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val text = context.contentResolver.openInputStream(uri)
                                                ?.bufferedReader()?.readText().orEmpty()
                                            val target = File(context.cacheDir, "zen-book-${System.currentTimeMillis()}.epub")
                                            val title = uri.lastPathSegment?.substringBeforeLast('.') ?: "ZenConverter"
                                            org.zenconverter.app.ebook.EpubBuilder.write(title, "", text, target)
                                            StudioOutput.save(
                                                context, target, target.name, "application/epub+zip",
                                                StudioOutput.Collection.Documents
                                            )
                                            target.delete()
                                            texts.epubCreated()
                                        }.getOrElse { texts.studioFailed() }
                                    }
                                    busy = false
                                }
                            }
                        ) { Text(texts.epubCreateAction()) }
                        OutlinedButton(
                            enabled = sourceUri != null && !busy,
                            onClick = {
                                val uri = sourceUri ?: return@OutlinedButton
                                busy = true
                                scope.launch {
                                    status = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val source = copyToCache(context, uri, "zen-book-${System.nanoTime()}.epub")
                                            val text = org.zenconverter.app.ebook.EpubReader.toPlainText(source)
                                            val target = File(context.cacheDir, "zen-book-${System.currentTimeMillis()}.txt")
                                            target.writeText(text)
                                            StudioOutput.save(
                                                context, target, target.name, "text/plain",
                                                StudioOutput.Collection.Documents
                                            )
                                            source.delete()
                                            target.delete()
                                            texts.epubExtracted()
                                        }.getOrElse { texts.studioFailed() }
                                    }
                                    busy = false
                                }
                            }
                        ) { Text(texts.epubExtractAction()) }
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
internal fun SubtitleBurnScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var subtitleUri by remember { mutableStateOf<Uri?>(null) }
    var fontSize by remember { mutableStateOf(24f) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) videoUri = uri
    }
    val pickSubtitle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) subtitleUri = uri
    }

    SettingsPage(texts.burnSubsTitle(), texts.studioBack(), onBack) {
        item("burn-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.burnSubsDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickVideo.launch(arrayOf("video/*")) }) {
                            Text(texts.burnSubsPickVideo())
                        }
                        OutlinedButton(onClick = { pickSubtitle.launch(arrayOf("application/x-subrip", "text/*", "*/*")) }) {
                            Text(texts.burnSubsPickSubtitle())
                        }
                    }
                    Text(
                        listOfNotNull(
                            videoUri?.lastPathSegment,
                            subtitleUri?.lastPathSegment
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StudioFieldLabel(texts.burnSubsFontSize())
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 12f..48f,
                        steps = 8
                    )
                    Button(
                        enabled = videoUri != null && subtitleUri != null && !busy,
                        onClick = {
                            val video = videoUri ?: return@Button
                            val subtitle = subtitleUri ?: return@Button
                            busy = true
                            scope.launch {
                                val temp = tempOutput(context, "zen-burn", "mp4")
                                val ok = AudioStudioEngine.burnSubtitles(
                                    context = context,
                                    sourceUri = video,
                                    subtitleUri = subtitle,
                                    target = temp,
                                    fontSize = fontSize.toInt()
                                )
                                status = if (ok) {
                                    StudioOutput.save(
                                        context, temp,
                                        "zenconverter-subbed-${System.currentTimeMillis()}.mp4",
                                        "video/mp4", StudioOutput.Collection.Movies
                                    )
                                    temp.delete()
                                    texts.burnSubsDone()
                                } else {
                                    temp.delete()
                                    texts.studioFailed()
                                }
                                busy = false
                            }
                        }
                    ) { Text(texts.burnSubsRun()) }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChapterSplitScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var manual by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val pickAudio = rememberAudioPicker { uri ->
        sourceUri = uri
        status = null
        scope.launch {
            val embedded = AudioStudioEngine.readChapterStarts(context, uri)
            if (embedded.isNotEmpty()) {
                manual = embedded.joinToString("\n") { ChapterTimestamps.format(it) }
                status = texts.chapterSplitDone(embedded.size)
            }
        }
    }

    SettingsPage(texts.chapterSplitTitle(), texts.studioBack(), onBack) {
        item("chapter-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.chapterSplitDesc(),
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
                                    val embedded = AudioStudioEngine.readChapterStarts(context, uri)
                                    if (embedded.isEmpty()) {
                                        status = texts.chapterSplitNone()
                                    } else {
                                        manual = embedded.joinToString("\n") { ChapterTimestamps.format(it) }
                                        status = texts.chapterSplitDone(embedded.size)
                                    }
                                    busy = false
                                }
                            }
                        ) { Text(texts.chapterSplitUseEmbedded()) }
                    }
                    OutlinedTextField(
                        value = manual,
                        onValueChange = { manual = it },
                        label = { Text(texts.chapterSplitRun()) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val parsed = ChapterTimestamps.parse(manual)
                    if (parsed.starts.isNotEmpty()) {
                        Text(
                            parsed.starts.joinToString("  ·  ") { ChapterTimestamps.format(it) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        enabled = sourceUri != null && parsed.starts.isNotEmpty() && !busy,
                        onClick = {
                            val uri = sourceUri ?: return@Button
                            busy = true
                            scope.launch {
                                val outputDir = File(context.cacheDir, "zen-chapters-${System.currentTimeMillis()}")
                                val parts = AudioStudioEngine.splitAtTimestamps(
                                    context = context,
                                    sourceUri = uri,
                                    outputDir = outputDir,
                                    chapters = parsed.starts
                                )
                                status = if (parts.isEmpty()) {
                                    texts.studioFailed()
                                } else {
                                    parts.forEach { part ->
                                        StudioOutput.save(
                                            context, part, part.name, "audio/mpeg", StudioOutput.Collection.Music
                                        )
                                    }
                                    texts.chapterSplitDone(parts.size)
                                }
                                outputDir.deleteRecursively()
                                busy = false
                            }
                        }
                    ) { Text(texts.chapterSplitRun()) }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
