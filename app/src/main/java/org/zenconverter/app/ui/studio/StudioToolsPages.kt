package org.zenconverter.app.ui.studio

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import org.zenconverter.app.ui.FileCategory
import org.zenconverter.app.i18n.localizedText
import org.zenconverter.app.conversion.QueueInbox
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
    var format by remember { mutableStateOf(ArchiveEngine.Format.Zip) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) sources = uris
    }
    val pickArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            // Keep the original extension so ArchiveEngine can tell TAR and 7z
            // apart from ZIP when it picks the extractor.
            val name = displayName(context, uri)
            val archive = copyToCache(context, uri, "zen-archive-input-${System.nanoTime()}${name.substringAfterLast('.', "").let { if (it.isBlank()) ".zip" else ".$it" }}")
            val targetDir = File(context.cacheDir, "zen-extract-${System.currentTimeMillis()}")
            status = withContext(Dispatchers.IO) {
                runCatching {
                    ArchiveEngine.extract(archive, targetDir)
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
            }
            archive.delete()
            targetDir.deleteRecursively()
            busy = false
        }
    }
    val pickTarget = rememberFolderPicker { _ ->
        busy = true
        scope.launch {
            val archive = File(context.cacheDir, "zen-created-${System.currentTimeMillis()}.${format.extension}")
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val files = sources.mapNotNull { copyToCache(context, it, "zen-archive-${System.nanoTime()}") }
                    ArchiveEngine.create(format, files, archive)
                    files.forEach { it.delete() }
                }.fold(
                    onSuccess = {
                        StudioOutput.save(
                            context, archive, archive.name, "application/octet-stream", StudioOutput.Collection.Downloads
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArchiveEngine.Format.entries.forEach { option ->
                            FilterChip(
                                selected = format == option,
                                onClick = { format = option },
                                label = { Text(option.extension.uppercase()) }
                            )
                        }
                    }
                    Text(
                        "${sources.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    }) {
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
