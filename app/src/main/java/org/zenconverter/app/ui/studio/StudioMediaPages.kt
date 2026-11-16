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
import kotlinx.coroutines.launch
import org.zenconverter.app.audio.AudioStudioEngine
import org.zenconverter.app.audio.AudioTagEditor
import org.zenconverter.app.audio.OfflineTextToSpeech
import org.zenconverter.app.audio.RingtoneMaker
import org.zenconverter.app.audio.StudioOutput
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.settings.InsetGroupCard
import org.zenconverter.app.ui.settings.SettingsPage

@Composable
internal fun TagEditorScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var artworkUri by remember { mutableStateOf<Uri?>(null) }
    var tags by remember { mutableStateOf(AudioTagEditor.Tags()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val pickAudio = rememberAudioPicker { uri ->
        sourceUri = uri
        busy = true
        scope.launch {
            tags = AudioTagEditor.read(context, uri) ?: AudioTagEditor.Tags()
            busy = false
        }
    }
    val pickArtwork = rememberImagePicker { uri -> artworkUri = uri }

    SettingsPage(texts.tagTitle(), texts.studioBack(), onBack) {
        item("tag-pick") {
            InsetGroupCard {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = pickAudio) { Text(texts.tagLoad()) }
                    OutlinedButton(onClick = pickArtwork) { Text(texts.studioPickImage()) }
                    if (artworkUri != null) {
                        Text(
                            texts.tagArtwork(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        item("tag-fields") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagField(texts.tagTitleField(), tags.title) { tags = tags.copy(title = it) }
                    TagField(texts.tagArtist(), tags.artist) { tags = tags.copy(artist = it) }
                    TagField(texts.tagAlbum(), tags.album) { tags = tags.copy(album = it) }
                    TagField(texts.tagAlbumArtist(), tags.albumArtist) { tags = tags.copy(albumArtist = it) }
                    TagField(texts.tagTrack(), tags.track) { tags = tags.copy(track = it) }
                    TagField(texts.tagYear(), tags.year) { tags = tags.copy(year = it) }
                    TagField(texts.tagGenre(), tags.genre) { tags = tags.copy(genre = it) }
                    TagField(texts.tagComment(), tags.comment) { tags = tags.copy(comment = it) }
                    TagField(texts.tagLyrics(), tags.lyrics) { tags = tags.copy(lyrics = it) }
                    Text(
                        texts.tagBatchNote(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        enabled = sourceUri != null && !busy,
                        onClick = {
                            val uri = sourceUri ?: return@Button
                            busy = true
                            scope.launch {
                                val temp = tempOutput(context, "zen-tags", "m4a")
                                val ok = AudioTagEditor.write(context, uri, tags, artworkUri, temp)
                                status = if (ok) {
                                    StudioOutput.save(
                                        context, temp, "zenconverter-tagged-${System.currentTimeMillis()}.m4a",
                                        "audio/mp4", StudioOutput.Collection.Music
                                    )
                                    temp.delete()
                                    texts.tagSaved()
                                } else {
                                    temp.delete()
                                    texts.studioFailed()
                                }
                                busy = false
                            }
                        }
                    ) { Text(texts.tagSave()) }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TagField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun RingtoneMakerScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var startSeconds by remember { mutableStateOf(0.0) }
    var endSeconds by remember { mutableStateOf(30.0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val pickAudio = rememberAudioPicker { uri -> sourceUri = uri }

    fun install(slot: RingtoneMaker.Slot) {
        val uri = sourceUri ?: return
        busy = true
        scope.launch {
            val temp = tempOutput(context, "zen-ringtone", "mp3")
            val ok = AudioStudioEngine.trim(context, uri, startSeconds, endSeconds, temp)
            status = if (ok) {
                runCatching {
                    RingtoneMaker.install(context, temp, "zenconverter-${System.currentTimeMillis()}.mp3", slot)
                }.isSuccess.let { installed ->
                    if (installed) texts.ringtoneApplied() else texts.studioFailed()
                }
            } else {
                texts.studioFailed()
            }
            temp.delete()
            busy = false
        }
    }

    SettingsPage(texts.ringtoneTitle(), texts.studioBack(), onBack) {
        item("ringtone-pick") {
            InsetGroupCard {
                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    Button(onClick = pickAudio) { Text(texts.ringtoneLoad()) }
                }
            }
        }
        item("ringtone-range") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioFieldLabel(texts.ringtoneStart())
                    androidx.compose.material3.Slider(
                        value = startSeconds.toFloat(),
                        onValueChange = { startSeconds = it.toDouble().coerceAtMost(endSeconds - 1.0) },
                        valueRange = 0f..600f
                    )
                    StudioFieldLabel(texts.ringtoneEnd())
                    androidx.compose.material3.Slider(
                        value = endSeconds.toFloat(),
                        onValueChange = { endSeconds = it.toDouble().coerceAtLeast(startSeconds + 1.0) },
                        valueRange = 0f..600f
                    )
                }
            }
        }
        item("ringtone-actions") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = sourceUri != null && !busy, onClick = { install(RingtoneMaker.Slot.Ringtone) }) {
                        Text(texts.ringtoneSetRingtone())
                    }
                    OutlinedButton(enabled = sourceUri != null && !busy, onClick = { install(RingtoneMaker.Slot.Alarm) }) {
                        Text(texts.ringtoneSetAlarm())
                    }
                    OutlinedButton(enabled = sourceUri != null && !busy, onClick = { install(RingtoneMaker.Slot.Notification) }) {
                        Text(texts.ringtoneSetNotification())
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
internal fun TextToSpeechScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tts = remember { OfflineTextToSpeech(context) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.DisposableEffect(tts) {
        onDispose { tts.shutdown() }
    }

    SettingsPage(texts.ttsTitle(), texts.studioBack(), onBack) {
        item("tts-input") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(texts.ttsTextHint()) },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = text.isNotBlank() && !busy,
                            onClick = {
                                scope.launch {
                                    val ready = tts.initialize()
                                    if (!ready) {
                                        status = texts.ttsUnavailable()
                                        return@launch
                                    }
                                    val temp = tempOutput(context, "zen-tts", "wav")
                                    val ok = runCatching { tts.synthesizeToFile(text, temp) }.getOrDefault(false)
                                    if (ok) {
                                        StudioOutput.save(
                                            context, temp, "zenconverter-speech-${System.currentTimeMillis()}.wav",
                                            "audio/wav", StudioOutput.Collection.Music
                                        )
                                        status = texts.ttsExported()
                                    } else {
                                        status = texts.ttsUnavailable()
                                    }
                                    temp.delete()
                                }
                            }
                        ) { Text(texts.ttsSpeak()) }
                        Button(
                            enabled = text.isNotBlank() && !busy,
                            onClick = {
                                busy = true
                                status = texts.ttsExporting()
                                scope.launch {
                                    val ready = tts.initialize()
                                    if (!ready) {
                                        status = texts.ttsUnavailable()
                                        busy = false
                                        return@launch
                                    }
                                    val temp = tempOutput(context, "zen-tts", "wav")
                                    val ok = runCatching { tts.synthesizeToFile(text, temp) }.getOrDefault(false)
                                    status = if (ok) {
                                        StudioOutput.save(
                                            context, temp, "zenconverter-speech-${System.currentTimeMillis()}.wav",
                                            "audio/wav", StudioOutput.Collection.Music
                                        )
                                        temp.delete()
                                        texts.ttsExported()
                                    } else {
                                        temp.delete()
                                        texts.ttsUnavailable()
                                    }
                                    busy = false
                                }
                            }
                        ) { Text(texts.ttsExport()) }
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
internal fun VisualizerScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var artworkUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val pickAudio = rememberAudioPicker { uri -> audioUri = uri }
    val pickArtwork = rememberImagePicker { uri -> artworkUri = uri }

    SettingsPage(texts.visualizerTitle(), texts.studioBack(), onBack) {
        item("visualizer-pick") {
            InsetGroupCard {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = pickAudio) { Text(texts.visualizerLoad()) }
                    OutlinedButton(onClick = pickArtwork) { Text(texts.visualizerArtwork()) }
                }
            }
        }
        item("visualizer-render") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = audioUri != null && !busy,
                        onClick = {
                            val uri = audioUri ?: return@Button
                            busy = true
                            status = texts.visualizerRendering()
                            scope.launch {
                                val temp = tempOutput(context, "zen-visualizer", "mp4")
                                val ok = AudioStudioEngine.renderVisualizer(context, uri, artworkUri, temp)
                                status = if (ok) {
                                    StudioOutput.save(
                                        context, temp, "zenconverter-visualizer-${System.currentTimeMillis()}.mp4",
                                        "video/mp4", StudioOutput.Collection.Movies
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
                    ) { Text(texts.visualizerRender()) }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
