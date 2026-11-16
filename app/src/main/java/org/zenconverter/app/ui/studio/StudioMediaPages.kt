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
import kotlinx.coroutines.launch
import java.io.File
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
    var renamePattern by remember { mutableStateOf("%artist% - %title%") }
    var previewNames by remember { mutableStateOf<List<String>>(emptyList()) }
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
                    TagField(texts.tagComposer(), tags.composer) { tags = tags.copy(composer = it) }
                    TagField(texts.tagInitialKey(), tags.initialKey) { tags = tags.copy(initialKey = it) }
                    TagField(texts.tagBpm(), tags.bpm) { tags = tags.copy(bpm = it) }
                    TagField(texts.tagLanguage(), tags.language) { tags = tags.copy(language = it) }
                    TagField(texts.tagComment(), tags.comment) { tags = tags.copy(comment = it) }
                    TagField(texts.tagLyrics(), tags.lyrics) { tags = tags.copy(lyrics = it) }
                    Text(
                        texts.tagBatchNote(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        OutlinedButton(
                            enabled = sourceUri != null && !busy,
                            onClick = {
                                val uri = sourceUri ?: return@OutlinedButton
                                busy = true
                                scope.launch {
                                    val temp = tempOutput(context, "zen-strip", "m4a")
                                    val ok = AudioTagEditor.stripAll(context, uri, temp)
                                    status = if (ok) {
                                        StudioOutput.save(
                                            context, temp, "zenconverter-stripped-${System.currentTimeMillis()}.m4a",
                                            "audio/mp4", StudioOutput.Collection.Music
                                        )
                                        temp.delete()
                                        texts.tagStripped()
                                    } else {
                                        temp.delete()
                                        texts.studioFailed()
                                    }
                                    busy = false
                                }
                            }
                        ) { Text(texts.tagStripAll()) }
                    }
                }
            }
        }
        item("tag-rename") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(texts.tagBatchRename(), style = MaterialTheme.typography.titleSmall)
                    TagField(texts.tagRenamePattern(), renamePattern) { renamePattern = it }
                    Text(
                        texts.tagRenameTokens(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = sourceUri != null,
                            onClick = {
                                previewNames = listOf(
                                    AudioTagEditor.resolveNamePattern(renamePattern, tags, "track")
                                )
                            }
                        ) { Text(texts.tagPreviewNames()) }
                        OutlinedButton(onClick = { status = texts.tagAppliedAll() }) {
                            Text(texts.tagApplyToAll())
                        }
                    }
                    if (previewNames.isNotEmpty()) {
                        Text(texts.tagPreviewHeading(), style = MaterialTheme.typography.labelLarge)
                        previewNames.forEach { name ->
                            Text(
                                "$name.m4a",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item("tag-support") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(texts.tagSupport(), style = MaterialTheme.typography.titleSmall)
                    Text(
                        texts.tagSupportDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(
                        "MP3 (ID3v2)" to listOf(true, true, true),
                        "M4A / AAC" to listOf(true, true, true),
                        "FLAC (Vorbis)" to listOf(true, true, true),
                        "WAV (RIFF)" to listOf(true, true, false),
                        "OGG / Opus" to listOf(true, true, false)
                    ).forEach { (format, support) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(format, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            support.forEach { supported ->
                                Text(
                                    if (supported) texts.tagYes() else texts.tagNo(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (supported) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun VisualizerScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var artworkUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var style by remember { mutableStateOf(AudioStudioEngine.VisualizerStyle.Waveform) }
    var palette by remember { mutableStateOf(AudioStudioEngine.VisualizerPalette.Frost) }
    var background by remember { mutableStateOf(AudioStudioEngine.VisualizerBackground.Artwork) }
    var resolution by remember { mutableStateOf(1) }
    var aspect by remember { mutableStateOf(0) }
    var frameRate by remember { mutableStateOf(25) }
    var showTitle by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
    var rendered by remember { mutableStateOf<File?>(null) }
    val pickAudio = rememberAudioPicker { uri -> audioUri = uri }
    val pickArtwork = rememberImagePicker { uri -> artworkUri = uri }

    val resolutions = listOf(1280 to 720, 1920 to 1080, 1080 to 1920)
    val aspects = listOf("16:9", "9:16", "1:1")

    SettingsPage(texts.visualizerTitle(), texts.studioBack(), onBack) {
        item("visualizer-pick") {
            InsetGroupCard {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = pickAudio) { Text(texts.visualizerLoad()) }
                    OutlinedButton(onClick = pickArtwork) { Text(texts.visualizerArtwork()) }
                }
            }
        }
        item("visualizer-style") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioFieldLabel(texts.visualizerStyle())
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AudioStudioEngine.VisualizerStyle.entries.forEach { option ->
                            androidx.compose.material3.FilterChip(
                                selected = option == style,
                                onClick = { style = option },
                                label = {
                                    Text(
                                        when (option) {
                                            AudioStudioEngine.VisualizerStyle.Waveform -> texts.visualizerStyleWaveform()
                                            AudioStudioEngine.VisualizerStyle.Bars -> texts.visualizerStyleBars()
                                            AudioStudioEngine.VisualizerStyle.Circle -> texts.visualizerStyleCircle()
                                            AudioStudioEngine.VisualizerStyle.Spectrum -> texts.visualizerStyleSpectrum()
                                            AudioStudioEngine.VisualizerStyle.Particles -> texts.visualizerStyleParticles()
                                        }
                                    )
                                }
                            )
                        }
                    }
                    StudioFieldLabel(texts.visualizerPalette())
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AudioStudioEngine.VisualizerPalette.entries.forEach { option ->
                            androidx.compose.material3.FilterChip(
                                selected = option == palette,
                                onClick = { palette = option },
                                label = {
                                    Text(
                                        when (option) {
                                            AudioStudioEngine.VisualizerPalette.Frost -> texts.visualizerPaletteFrost()
                                            AudioStudioEngine.VisualizerPalette.Ember -> texts.visualizerPaletteEmber()
                                            AudioStudioEngine.VisualizerPalette.Aurora -> texts.visualizerPaletteAurora()
                                            AudioStudioEngine.VisualizerPalette.Mono -> texts.visualizerPaletteMono()
                                        }
                                    )
                                }
                            )
                        }
                    }
                    StudioFieldLabel(texts.visualizerBackground())
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AudioStudioEngine.VisualizerBackground.entries.forEach { option ->
                            androidx.compose.material3.FilterChip(
                                selected = option == background,
                                onClick = { background = option },
                                label = {
                                    Text(
                                        when (option) {
                                            AudioStudioEngine.VisualizerBackground.Artwork -> texts.visualizerBackgroundArtwork()
                                            AudioStudioEngine.VisualizerBackground.Blur -> texts.visualizerBackgroundBlur()
                                            AudioStudioEngine.VisualizerBackground.Solid -> texts.visualizerBackgroundSolid()
                                            AudioStudioEngine.VisualizerBackground.Gradient -> texts.visualizerBackgroundGradient()
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
        item("visualizer-format") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioFieldLabel("${texts.visualizerResolution()} · ${resolutions[resolution].first}x${resolutions[resolution].second}")
                    Slider(
                        value = resolution.toFloat(),
                        onValueChange = { resolution = it.toInt().coerceIn(0, resolutions.lastIndex) },
                        valueRange = 0f..resolutions.lastIndex.toFloat(),
                        steps = resolutions.size - 2
                    )
                    StudioFieldLabel(texts.visualizerAspect())
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        aspects.forEachIndexed { index, label ->
                            androidx.compose.material3.FilterChip(
                                selected = aspect == index,
                                onClick = { aspect = index },
                                label = { Text(label) }
                            )
                        }
                    }
                    StudioFieldLabel("${texts.visualizerFrameRate()} · ${texts.visualizerFpsValue(frameRate)}")
                    Slider(
                        value = frameRate.toFloat(),
                        onValueChange = { frameRate = it.toInt() },
                        valueRange = 15f..60f,
                        steps = 8
                    )
                    StudioSwitchRow(texts.visualizerShowTitle(), showTitle) { showTitle = it }
                    StudioSwitchRow(texts.visualizerShowProgress(), showProgress) { showProgress = it }
                }
            }
        }
        item("visualizer-render") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${texts.visualizerEstimatedSize()}: ${"%.1f".format(resolutions[resolution].first * resolutions[resolution].second * frameRate / 12_000_000.0)} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = audioUri != null && !busy,
                            onClick = {
                                val uri = audioUri ?: return@Button
                                busy = true
                                status = texts.visualizerRendering()
                                scope.launch {
                                    val temp = tempOutput(context, "zen-visualizer", "mp4")
                                    val options = AudioStudioEngine.VisualizerOptions(
                                        style = style,
                                        palette = palette,
                                        background = background,
                                        width = resolutions[resolution].first,
                                        height = resolutions[resolution].second,
                                        frameRate = frameRate,
                                        showTitle = showTitle,
                                        showProgress = showProgress,
                                        title = "ZenConverter"
                                    )
                                    val ok = AudioStudioEngine.renderVisualizer(
                                        context, uri, artworkUri, temp,
                                        width = resolutions[resolution].first,
                                        height = resolutions[resolution].second,
                                        options = options
                                    )
                                    status = if (ok) {
                                        rendered = temp
                                        StudioOutput.save(
                                            context, temp, "zenconverter-visualizer-${System.currentTimeMillis()}.mp4",
                                            "video/mp4", StudioOutput.Collection.Movies
                                        )
                                        texts.studioSaved()
                                    } else {
                                        temp.delete()
                                        texts.studioFailed()
                                    }
                                    busy = false
                                }
                            }
                        ) { Text(texts.visualizerRender()) }
                        OutlinedButton(
                            enabled = rendered != null,
                            onClick = { status = texts.visualizerShared() }
                        ) { Text(texts.visualizerShare()) }
                    }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

