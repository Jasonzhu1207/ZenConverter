package org.zenconverter.app.ui.studio

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zenconverter.app.audio.AudioDecoder
import org.zenconverter.app.audio.AudioTagEditor
import org.zenconverter.app.audio.StudioOutput
import org.zenconverter.app.audio.TranscriptionEngine
import org.zenconverter.app.studio.LyricStudio
import org.zenconverter.app.subtitle.SubtitleTextDecoder
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.settings.InsetGroupCard
import org.zenconverter.app.ui.settings.SettingsPage

/**
 * The interactive lyrics editor: paste lines, stamp them against a moving
 * playhead, nudge the whole set and export standard `.lrc`. The timing maths
 * lives in [LyricStudio] so it stays unit-tested.
 */
@Composable
internal fun LyricsEditorScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val player = rememberStudioPlayer()

    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var durationMs by remember { mutableStateOf(0L) }
    val lines = remember { mutableStateListOf<LyricStudio.Line>() }
    var title by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var positionMs by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var offsetMs by remember { mutableStateOf(0f) }
    var snapToBeat by remember { mutableStateOf(false) }
    var writeSidecar by remember { mutableStateOf(true) }
    var bpm by remember { mutableStateOf(0.0) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf(-1) }
    var editingText by remember { mutableStateOf("") }

    fun document() = LyricStudio.Document(title, lines.toList())
    fun applyDocument(next: LyricStudio.Document) {
        title = next.title
        lines.clear()
        lines.addAll(next.lines)
    }

    val pickAudio = rememberAudioPicker { uri ->
        audioUri = uri
        scope.launch {
            val decoded = AudioDecoder.decodeMono(context, uri)
            durationMs = decoded?.let { (it.samples.size.toDouble() / it.sampleRateHz * 1000.0).toLong() } ?: 0L
        }
    }

    // Advance the playhead while the clip plays; MediaPlayer has no per-frame callback.
    LaunchedEffect(playing) {
        while (playing) {
            delay(120)
            positionMs += 120
            if (durationMs > 0L && positionMs > durationMs) {
                positionMs = durationMs
                player.stop()
                playing = false
            }
        }
    }

    SettingsPage(texts.lyricsEditorTitle(), texts.studioBack(), onBack) {
        item("lyr-editor-source") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.lyricsEditorDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = pickAudio) { Text(texts.lyricsEditorPick()) }
                        OutlinedButton(
                            enabled = audioUri != null && !busy,
                            onClick = {
                                val uri = audioUri ?: return@OutlinedButton
                                busy = true
                                scope.launch {
                                    val parsed = LyricStudio.fromTranscript(TranscriptionEngine.transcribe(context, uri))
                                    if (parsed.isEmpty) {
                                        status = texts.transcribeEmpty()
                                    } else {
                                        applyDocument(parsed)
                                        status = texts.lyrTransDone(parsed.lines.size)
                                    }
                                    busy = false
                                }
                            }
                        ) { Text(texts.lyricsEditorTranscribe()) }
                    }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item("lyr-editor-paste") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioFieldLabel(texts.lyricsEditorPaste())
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text(texts.lyricsEditorOnePerRow()) },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = draft.isNotBlank(),
                            onClick = {
                                val added = LyricStudio.addLines(draft, durationMs)
                                applyDocument(
                                    LyricStudio.Document(title, lines.toList() + added.lines)
                                )
                                draft = ""
                                status = texts.lyricsEditorAdded(added.lines.size)
                            }
                        ) { Text(texts.lyricsEditorAddLines()) }
                        OutlinedButton(
                            enabled = lines.isNotEmpty() && durationMs > 0L,
                            onClick = {
                                applyDocument(LyricStudio.autoSpread(document(), durationMs))
                                status = texts.lyricsEditorSpread(lines.size)
                            }
                        ) { Text(texts.lyricsEditorAutoSpread()) }
                    }
                }
            }
        }

        if (lines.isNotEmpty()) {
            item("lyr-editor-lines") {
                InsetGroupCard {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            texts.lyricsEditorTapHint(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        lines.forEachIndexed { index, line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { positionMs = line.startMs }) {
                                    Text(
                                        LyricStudio.formatTimecode(line.startMs),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                if (editingIndex == index) {
                                    OutlinedTextField(
                                        value = editingText,
                                        onValueChange = { editingText = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    TextButton(onClick = {
                                        if (index in lines.indices) {
                                            lines[index] = lines[index].copy(text = editingText)
                                        }
                                        editingIndex = -1
                                    }) { Text(texts.lyricsEditorAddLines()) }
                                } else {
                                    Text(
                                        line.text,
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    TextButton(onClick = {
                                        editingIndex = index
                                        editingText = line.text
                                    }) { Text(texts.lyricsLinesLabel()) }
                                }
                            }
                        }
                    }
                }
            }
        }

        item("lyr-editor-playhead") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.lyricsEditorPlayhead() + " · " + LyricStudio.formatTimecode(positionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = if (durationMs > 0L) positionMs.toFloat() / durationMs else 0f,
                        onValueChange = { fraction ->
                            if (durationMs > 0L) positionMs = (fraction * durationMs).toLong()
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = audioUri != null,
                            onClick = {
                                val uri = audioUri ?: return@Button
                                if (playing) {
                                    player.stop()
                                    playing = false
                                } else {
                                    player.play(uri, "lyrics") { status = texts.studioFailed() }
                                    playing = true
                                }
                            }
                        ) { Text(texts.karaokeOpen()) }
                        OutlinedButton(
                            enabled = lines.isNotEmpty(),
                            onClick = {
                                val stamped = LyricStudio.formatTimecode(positionMs)
                                lines.add(LyricStudio.Line(positionMs, texts.lyricsEditorLinePlaceholder()))
                                status = texts.lyricsEditorStamped(stamped)
                            }
                        ) { Text(texts.lyricsEditorStamp()) }
                        TextButton(onClick = {
                            lines.clear()
                            title = null
                            status = null
                        }) { Text(texts.lyricsEditorClear()) }
                    }
                }
            }
        }

        if (lines.isNotEmpty()) {
            item("lyr-editor-align") {
                InsetGroupCard {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioFieldLabel(texts.lyricsEditorOffsetAll() + " · ${offsetMs.toInt()} ms")
                        Slider(
                            value = offsetMs,
                            onValueChange = { offsetMs = it },
                            valueRange = -500f..500f,
                            steps = 9
                        )
                        StudioSwitchRow(texts.lyricsEditorSnap(), snapToBeat) { snapToBeat = it }
                        StudioSwitchRow(texts.lyricsEditorSidecar(), writeSidecar) { writeSidecar = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                applyDocument(LyricStudio.shift(document(), offsetMs.toLong()))
                                status = texts.lyrShiftDone(offsetMs.toInt())
                            }) { Text(texts.lyrShiftApply()) }
                            Button(
                                enabled = snapToBeat,
                                onClick = {
                                    applyDocument(LyricStudio.snapToBeat(document(), bpm))
                                    status = texts.lyrShiftDone(0)
                                }
                            ) { Text(texts.lyricsEditorSnap()) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val lrc = LyricStudio.toLrc(document())
                                clipboard.setText(AnnotatedString(lrc))
                                status = texts.lyricsEditorCopied()
                            }) { Text(texts.lyricsEditorCopyLrc()) }
                            Button(
                                enabled = writeSidecar,
                                onClick = {
                                    val name = "zenconverter-lyrics-${System.currentTimeMillis()}.lrc"
                                    val target = File(context.cacheDir, name)
                                    target.writeText(LyricStudio.toLrc(document()))
                                    StudioOutput.save(
                                        context, target, name, "text/plain", StudioOutput.Collection.Documents
                                    )
                                    target.delete()
                                    status = texts.lyricsEditorSaved(name)
                                }
                            ) { Text(texts.lyricsEditorSaveLrc()) }
                            OutlinedButton(
                                enabled = audioUri != null,
                                onClick = {
                                    val uri = audioUri ?: return@OutlinedButton
                                    busy = true
                                    scope.launch {
                                        val tags = AudioTagEditor.read(context, uri) ?: AudioTagEditor.Tags()
                                        val temp = tempOutput(context, "zen-lyrics", "m4a")
                                        val ok = AudioTagEditor.write(
                                            context, uri,
                                            tags.copy(lyrics = lines.joinToString("\n") { it.text }),
                                            null, temp
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
                            ) { Text(texts.lyricsEditorEmbed()) }
                        }
                        Text(
                            LyricStudio.toLrc(document()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    // BPM powers beat snapping; it is read once the clip is known.
    LaunchedEffect(audioUri) {
        val uri = audioUri ?: return@LaunchedEffect
        bpm = withContext(Dispatchers.IO) {
            runCatching {
                val decoded = AudioDecoder.decodeMono(context, uri) ?: return@runCatching 0.0
                org.zenconverter.app.audio.AudioAnalysis.estimateBpm(decoded.samples, decoded.sampleRateHz) ?: 0.0
            }.getOrDefault(0.0)
        }
    }
}
