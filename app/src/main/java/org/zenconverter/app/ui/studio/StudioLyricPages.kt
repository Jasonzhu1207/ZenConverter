package org.zenconverter.app.ui.studio

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zenconverter.app.audio.AudioTagEditor
import org.zenconverter.app.audio.StudioOutput
import org.zenconverter.app.audio.TranscriptionEngine
import org.zenconverter.app.model.WhisperModelManager
import org.zenconverter.app.studio.LyricStudio
import org.zenconverter.app.subtitle.SubtitleTextDecoder
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.settings.InsetGroupCard
import org.zenconverter.app.ui.settings.SettingsGroupHeader
import org.zenconverter.app.ui.settings.SettingsPage

/**
 * Reads a picked document as text, decoding legacy encodings the same way the
 * subtitle lane does.
 */
private suspend fun readPickedText(context: android.content.Context, uri: Uri): String =
    withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext ""
        runCatching { SubtitleTextDecoder.decode(bytes) }.getOrElse { "" }
    }

@Composable
private fun LyricLinesPreview(texts: UiText, document: LyricStudio.Document) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            texts.karaokeLineCount(document.lines.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        document.lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    LyricStudio.formatTimecode(line.startMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
internal fun LrcImportScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var document by remember { mutableStateOf(LyricStudio.EMPTY) }
    var status by remember { mutableStateOf<String?>(null) }
    val pickLyrics = rememberFilePicker(listOf("text/*", "application/octet-stream")) { uri ->
        scope.launch {
            val text = readPickedText(context, uri)
            val parsed = LyricStudio.importLrc(text)
            document = parsed
            status = if (parsed.isEmpty) texts.lrcImportFailed() else texts.lrcImportDone(parsed.lines.size)
        }
    }

    SettingsPage(texts.lrcImportTitle(), texts.studioBack(), onBack) {
        item("lrc-import-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.lrcImportDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = pickLyrics) { Text(texts.lrcImportPick()) }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (!document.isEmpty) {
            item("lrc-import-lines") {
                InsetGroupCard {
                    Box(Modifier.fillMaxWidth().padding(14.dp)) {
                        LyricLinesPreview(texts, document)
                    }
                }
            }
        }
    }
}

@Composable
internal fun LrcExportScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var document by remember { mutableStateOf(LyricStudio.EMPTY) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pickLyrics = rememberFilePicker(listOf("text/*", "application/octet-stream")) { uri ->
        scope.launch {
            val parsed = LyricStudio.importLrc(readPickedText(context, uri))
            document = parsed
            status = if (parsed.isEmpty) texts.lrcImportFailed() else texts.lrcImportDone(parsed.lines.size)
        }
    }
    val pickAudio = rememberAudioPicker { audioUri = it }

    SettingsPage(texts.lrcExportTitle(), texts.studioBack(), onBack) {
        item("lrc-export-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.lrcExportDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = pickLyrics) { Text(texts.lrcImportPick()) }
                        OutlinedButton(onClick = pickAudio) { Text(texts.studioPickAudio()) }
                    }
                    if (!document.isEmpty) {
                        LyricLinesPreview(texts, document)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !document.isEmpty && !busy,
                            onClick = {
                                val name = "zenconverter-lyrics-${System.currentTimeMillis()}.lrc"
                                val target = File(context.cacheDir, name)
                                target.writeText(LyricStudio.toLrc(document))
                                StudioOutput.save(
                                    context, target, name, "text/plain", StudioOutput.Collection.Documents
                                )
                                target.delete()
                                status = texts.lrcExportDone(name)
                            }
                        ) { Text(texts.lrcExportSave()) }
                        OutlinedButton(
                            enabled = !document.isEmpty && audioUri != null && !busy,
                            onClick = {
                                val uri = audioUri ?: return@OutlinedButton
                                busy = true
                                scope.launch {
                                    val tags = AudioTagEditor.read(context, uri) ?: AudioTagEditor.Tags()
                                    val temp = tempOutput(context, "zen-lyrics", "m4a")
                                    val ok = AudioTagEditor.write(
                                        context,
                                        uri,
                                        tags.copy(lyrics = document.lines.joinToString("\n") { it.text }),
                                        null,
                                        temp
                                    )
                                    status = if (ok) {
                                        StudioOutput.save(
                                            context, temp,
                                            "zenconverter-lyrics-${System.currentTimeMillis()}.m4a",
                                            "audio/mp4", StudioOutput.Collection.Music
                                        )
                                        temp.delete()
                                        texts.lrcExportEmbedded()
                                    } else {
                                        temp.delete()
                                        texts.studioFailed()
                                    }
                                    busy = false
                                }
                            }
                        ) { Text(texts.lrcExportEmbed()) }
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
internal fun LyricTranscribeScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var document by remember { mutableStateOf(LyricStudio.EMPTY) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val engineAvailable = WhisperModelManager.INFERENCE_AVAILABLE
    val pickAudio = rememberAudioPicker { uri ->
        sourceUri = uri
        document = LyricStudio.EMPTY
    }

    SettingsPage(texts.lyrTransTitle(), texts.studioBack(), onBack) {
        item("lyr-trans-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.lyrTransDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!engineAvailable) {
                        Text(
                            texts.transcribeEngineUnavailable(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = pickAudio) { Text(texts.studioPickAudio()) }
                        OutlinedButton(
                            enabled = sourceUri != null && !busy,
                            onClick = {
                                val uri = sourceUri ?: return@OutlinedButton
                                busy = true
                                status = null
                                scope.launch {
                                    val segments = TranscriptionEngine.transcribe(context, uri)
                                    val parsed = LyricStudio.fromTranscript(segments)
                                    document = parsed
                                    status = if (parsed.isEmpty) {
                                        texts.transcribeEmpty()
                                    } else {
                                        texts.lyrTransDone(parsed.lines.size)
                                    }
                                    busy = false
                                }
                            }
                        ) { Text(texts.lyrTransRun()) }
                    }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (!document.isEmpty) {
                        LyricLinesPreview(texts, document)
                    }
                }
            }
        }
    }
}

@Composable
internal fun LyricShiftScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var document by remember { mutableStateOf(LyricStudio.EMPTY) }
    var offsetMs by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf<String?>(null) }
    val pickLyrics = rememberFilePicker(listOf("text/*", "application/octet-stream")) { uri ->
        scope.launch {
            val parsed = LyricStudio.importLrc(readPickedText(context, uri))
            document = parsed
            status = if (parsed.isEmpty) texts.lrcImportFailed() else texts.lrcImportDone(parsed.lines.size)
        }
    }

    SettingsPage(texts.lyrShiftTitle(), texts.studioBack(), onBack) {
        item("lyr-shift-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.lyrShiftDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = pickLyrics) { Text(texts.lrcImportPick()) }
                    StudioFieldLabel(texts.lyrShiftOffset() + " · ${offsetMs.toInt()} ms")
                    Slider(
                        value = offsetMs,
                        onValueChange = { offsetMs = it },
                        valueRange = -500f..500f,
                        steps = 9
                    )
                    OutlinedButton(
                        enabled = !document.isEmpty,
                        onClick = {
                            document = LyricStudio.shift(document, offsetMs.toLong())
                            status = texts.lyrShiftDone(offsetMs.toInt())
                        }
                    ) { Text(texts.lyrShiftApply()) }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (!document.isEmpty) {
                        LyricLinesPreview(texts, document)
                    }
                }
            }
        }
    }
}

@Composable
internal fun KaraokeScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var document by remember { mutableStateOf(LyricStudio.EMPTY) }
    var positionMs by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val player = rememberStudioPlayer()
    val listState = rememberLazyListState()
    val pickLyrics = rememberFilePicker(listOf("text/*", "application/octet-stream")) { uri ->
        scope.launch {
            val parsed = LyricStudio.importLrc(readPickedText(context, uri))
            document = parsed
            status = if (parsed.isEmpty) texts.lrcImportFailed() else texts.lrcImportDone(parsed.lines.size)
        }
    }
    val pickAudio = rememberAudioPicker { audioUri = it }
    val activeIndex = LyricStudio.activeIndex(document.lines, positionMs)

    // Drives the highlight forward while the clip plays; the platform player has
    // no position callback that fires per frame.
    LaunchedEffect(playing) {
        while (playing) {
            delay(120)
            positionMs += 120
        }
    }
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }

    SettingsPage(texts.karaokeTitle(), texts.studioBack(), onBack) {
        item("karaoke-controls") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.karaokeDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = pickAudio) { Text(texts.studioPickAudio()) }
                        OutlinedButton(onClick = pickLyrics) { Text(texts.lrcImportPick()) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = audioUri != null,
                            onClick = {
                                val uri = audioUri ?: return@Button
                                if (playing) {
                                    player.stop()
                                    playing = false
                                } else {
                                    positionMs = 0L
                                    player.play(uri, "karaoke") { status = texts.studioFailed() }
                                    playing = true
                                }
                            }
                        ) { Text(texts.karaokeOpen()) }
                    }
                    if (document.isEmpty) {
                        Text(
                            texts.karaokeEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            texts.karaokeLineCount(document.lines.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (!document.isEmpty) {
            item("karaoke-lines") {
                InsetGroupCard {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(document.lines) { index, line ->
                            val isActive = index == activeIndex
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isActive) {
                                            Brush.horizontalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                                                )
                                            )
                                        } else {
                                            Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Transparent))
                                        },
                                        MaterialTheme.shapes.medium
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = line.text,
                                    style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Start
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
