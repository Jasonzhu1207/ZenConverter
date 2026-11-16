package org.zenconverter.app.ui.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.zenconverter.app.audio.AudioAnalysis
import org.zenconverter.app.audio.AudioDecoder
import org.zenconverter.app.audio.AudioRecorder
import org.zenconverter.app.audio.AudioStudioEngine
import org.zenconverter.app.audio.StudioOutput
import org.zenconverter.app.audio.WaveformAnalyzer
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.settings.InsetGroupCard
import org.zenconverter.app.ui.settings.SettingsGroupHeader
import org.zenconverter.app.ui.settings.SettingsPage
import java.io.File

private fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
internal fun WaveformEditorScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var peaks by remember { mutableStateOf<WaveformAnalyzer.Peaks?>(null) }
    var busy by remember { mutableStateOf(false) }
    var startFraction by remember { mutableStateOf(0f) }
    var endFraction by remember { mutableStateOf(1f) }
    var fadeIn by remember { mutableStateOf(false) }
    var fadeOut by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val pickAudio = rememberAudioPicker { uri ->
        sourceUri = uri
        peaks = null
        startFraction = 0f
        endFraction = 1f
        busy = true
        scope.launch {
            val decoded = AudioDecoder.decodeMono(context, uri)
            peaks = decoded?.let {
                WaveformAnalyzer.peaksFromSamples(it.samples, it.channelCount, it.sampleRateHz)
            }
            busy = false
        }
    }

    SettingsPage(texts.waveformTitle(), texts.studioBack(), onBack) {
        item("waveform-pick") {
            InsetGroupCard {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = pickAudio) { Text(texts.waveformLoad()) }
                    if (sourceUri != null) {
                        OutlinedButton(onClick = { sourceUri = null; peaks = null }) { Text(texts.studioBack()) }
                    }
                }
            }
        }
        item("waveform-canvas") {
            InsetGroupCard {
                val current = peaks
                if (current == null) {
                    StudioNote(if (busy) texts.waveformAnalyzing() else texts.waveformEmpty())
                } else {
                    WaveformCanvas(
                        peaks = current,
                        startFraction = startFraction,
                        endFraction = endFraction,
                        selectionColor = MaterialTheme.colorScheme.primary
                    )
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(
                            texts.waveformSelection(
                                formatClock((current.durationSeconds * startFraction * 1000).toLong()),
                                formatClock((current.durationSeconds * endFraction * 1000).toLong())
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StudioFieldLabel(texts.waveformTrimStart())
                        Slider(
                            value = startFraction,
                            onValueChange = { startFraction = it.coerceAtMost(endFraction - 0.01f) },
                            valueRange = 0f..1f
                        )
                        StudioFieldLabel(texts.waveformTrimEnd())
                        Slider(
                            value = endFraction,
                            onValueChange = { endFraction = it.coerceAtLeast(startFraction + 0.01f) },
                            valueRange = 0f..1f
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { fadeIn = !fadeIn }) {
                                Text("${texts.waveformFadeIn()}${if (fadeIn) " ✓" else ""}")
                            }
                            OutlinedButton(onClick = { fadeOut = !fadeOut }) {
                                Text("${texts.waveformFadeOut()}${if (fadeOut) " ✓" else ""}")
                            }
                        }
                        Button(
                            enabled = !busy,
                            onClick = {
                                val uri = sourceUri ?: return@Button
                                val decoded = current
                                busy = true
                                scope.launch {
                                    val temp = tempOutput(context, "zen-waveform", "mp3")
                                    val ok = AudioStudioEngine.applyEffects(
                                        context = context,
                                        sourceUri = uri,
                                        filters = emptyList(),
                                        target = temp,
                                        startSeconds = decoded.durationSeconds * startFraction,
                                        endSeconds = decoded.durationSeconds * endFraction,
                                        fadeInSeconds = if (fadeIn) 1.0 else null,
                                        fadeOutSeconds = if (fadeOut) 1.0 else null
                                    )
                                    status = if (ok) {
                                        StudioOutput.save(
                                            context, temp, "zenconverter-trim-${System.currentTimeMillis()}.mp3",
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
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) { Text(texts.waveformApply()) }
                        status?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaveformCanvas(
    peaks: WaveformAnalyzer.Peaks,
    startFraction: Float,
    endFraction: Float,
    selectionColor: Color
) {
    Canvas(Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 14.dp)) {
        val centerY = size.height / 2f
        val half = size.height / 2f
        val buckets = peaks.bucketCount.coerceAtLeast(1)
        val barWidth = size.width / buckets
        val amplitude = peaks.peakAmplitude.takeIf { it > 0.001f } ?: 1f
        val selectionLeft = size.width * startFraction
        val selectionRight = size.width * endFraction
        drawRect(
            color = selectionColor.copy(alpha = 0.10f),
            topLeft = Offset(selectionLeft, 0f),
            size = Size((selectionRight - selectionLeft).coerceAtLeast(0f), size.height)
        )
        for (index in 0 until buckets) {
            val minValue = peaks.minima[index] / amplitude
            val maxValue = peaks.maxima[index] / amplitude
            val x = index * barWidth + barWidth / 2f
            drawLine(
                color = selectionColor.copy(alpha = 0.75f),
                start = Offset(x, centerY - maxValue * half),
                end = Offset(x, centerY - minValue * half),
                strokeWidth = barWidth.coerceAtLeast(1f)
            )
        }
    }
}

@Composable
internal fun AudioRecorderScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recorder = remember { AudioRecorder() }
    val state by recorder.state.collectAsStateWithLifecycle()
    val level by recorder.level.collectAsStateWithLifecycle()
    val elapsed by recorder.elapsedMs.collectAsStateWithLifecycle()
    var permissionGranted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val requestPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted = granted }

    fun saveAs(extension: String, mimeType: String, collection: StudioOutput.Collection) {
        scope.launch {
            busy = true
            val recorded = recorder.stop() ?: return@launch
            val target = if (extension == "wav") recorded else File(context.cacheDir, "zen-rec-${System.currentTimeMillis()}.$extension")
            val ok = if (extension == "wav") {
                true
            } else {
                AudioStudioEngine.transcodeFile(
                    source = recorded,
                    target = target,
                    codec = if (extension == "flac") "flac" else "aac"
                )
            }
            if (ok) {
                StudioOutput.save(
                    context, target, "zenconverter-recording-${System.currentTimeMillis()}.$extension", mimeType, collection
                )
                status = texts.recorderSaved()
            } else {
                status = texts.studioFailed()
            }
            target.delete()
            recorded.delete()
            busy = false
        }
    }

    SettingsPage(texts.recorderTitle(), texts.studioBack(), onBack) {
        item("recorder-permission") {
            if (!permissionGranted) {
                InsetGroupCard {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudioNote(texts.recorderPermissionNeeded())
                        Button(onClick = { requestPermission.launch(android.Manifest.permission.RECORD_AUDIO) }) {
                            Text(texts.recorderRecord())
                        }
                    }
                }
            }
        }
        item("recorder-controls") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        formatClock(elapsed),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    StudioFieldLabel(texts.recorderLevel())
                    Slider(
                        value = level.rms.coerceIn(0f, 1f),
                        onValueChange = {},
                        enabled = false,
                        valueRange = 0f..1f
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (state) {
                            AudioRecorder.State.Idle -> Button(
                                enabled = permissionGranted,
                                onClick = {
                                    status = null
                                    scope.launch {
                                        runCatching {
                                            recorder.start(File(context.cacheDir, "zen-recording.wav"))
                                        }.onFailure { status = texts.studioFailed() }
                                    }
                                }
                            ) { Text(texts.recorderRecord()) }
                            AudioRecorder.State.Recording -> Button(onClick = { recorder.pause() }) {
                                Text(texts.recorderPause())
                            }
                            AudioRecorder.State.Paused -> Button(onClick = { recorder.resume() }) {
                                Text(texts.recorderResume())
                            }
                        }
                        OutlinedButton(
                            enabled = state != AudioRecorder.State.Idle,
                            onClick = { saveAs("wav", "audio/wav", StudioOutput.Collection.Music) }
                        ) { Text(texts.recorderSaveWav()) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = state != AudioRecorder.State.Idle && !busy,
                            onClick = { saveAs("flac", "audio/flac", StudioOutput.Collection.Music) }
                        ) { Text(texts.recorderSaveFlac()) }
                        OutlinedButton(
                            enabled = state != AudioRecorder.State.Idle && !busy,
                            onClick = { saveAs("m4a", "audio/mp4", StudioOutput.Collection.Music) }
                        ) { Text(texts.recorderSaveM4a()) }
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
internal fun AudioMixerScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf(listOf<MixerTrack>()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val pickAudio = rememberAudioPicker { uri -> tracks = tracks + MixerTrack(uri) }

    SettingsPage(texts.mixerTitle(), texts.studioBack(), onBack) {
        item("mixer-add") {
            InsetGroupCard {
                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    Button(onClick = pickAudio) { Text(texts.mixerAddTrack()) }
                }
            }
        }
        if (tracks.isEmpty()) {
            item("mixer-empty") { InsetGroupCard { StudioNote(texts.mixerEmpty()) } }
        }
        tracks.forEachIndexed { index, track ->
            item("mixer-track-$index") {
                InsetGroupCard {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(texts.mixerTrack(index + 1), style = MaterialTheme.typography.titleSmall)
                        StudioFieldLabel(texts.mixerVolume())
                        Slider(
                            value = track.volume,
                            onValueChange = { value -> tracks = tracks.withTrack(index) { it.copy(volume = value) } },
                            valueRange = 0f..2f
                        )
                        StudioFieldLabel(texts.mixerPan())
                        Slider(
                            value = track.pan,
                            onValueChange = { value -> tracks = tracks.withTrack(index) { it.copy(pan = value) } },
                            valueRange = -1f..1f
                        )
                        StudioSwitchRow(
                            label = texts.mixerMute(),
                            checked = track.mute,
                            onCheckedChange = { value -> tracks = tracks.withTrack(index) { it.copy(mute = value) } }
                        )
                    }
                }
            }
        }
        if (tracks.isNotEmpty()) {
            item("mixer-export") {
                InsetGroupCard {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !busy && tracks.any { !it.mute },
                            onClick = {
                                busy = true
                                scope.launch {
                                    val temp = tempOutput(context, "zen-mix", "m4a")
                                    val ok = AudioStudioEngine.mix(
                                        context = context,
                                        tracks = tracks.map {
                                            AudioStudioEngine.MixTrack(it.uri, it.volume, it.pan, it.mute)
                                        },
                                        target = temp
                                    )
                                    status = if (ok) {
                                        StudioOutput.save(
                                            context, temp, "zenconverter-mix-${System.currentTimeMillis()}.m4a",
                                            "audio/mp4", StudioOutput.Collection.Music
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
                        ) { Text(texts.mixerExport()) }
                        status?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AudioAnalyzeScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AudioAnalysis.Result?>(null) }
    val pickAudio = rememberAudioPicker { uri ->
        busy = true
        result = null
        scope.launch {
            val decoded = AudioDecoder.decodeMono(context, uri)
            result = decoded?.let { AudioAnalysis.analyze(it.samples, it.sampleRateHz, it.channelCount) }
            busy = false
        }
    }

    SettingsPage(texts.analyzeTitle(), texts.studioBack(), onBack) {
        item("analyze-pick") {
            InsetGroupCard {
                Row(Modifier.fillMaxWidth().padding(14.dp)) {
                    Button(onClick = pickAudio) { Text(texts.analyzeLoad()) }
                }
            }
        }
        item("analyze-result") {
            InsetGroupCard {
                val current = result
                if (current == null) {
                    StudioNote(if (busy) texts.analyzeRunning() else texts.studioNeedsFile())
                } else {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        current.bpm?.let {
                            Text(texts.analyzeBpm("%.1f".format(it)), style = MaterialTheme.typography.bodyLarge)
                        }
                        current.key?.let {
                            Text(texts.analyzeKey(it), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
