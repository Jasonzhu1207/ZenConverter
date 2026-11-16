package org.zenconverter.app.ui.studio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zenconverter.app.audio.AudioStudioEngine
import org.zenconverter.app.audio.StudioOutput
import org.zenconverter.app.conversion.QueueInbox
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.settings.InsetGroupCard
import org.zenconverter.app.ui.settings.SettingsPage

/**
 * The audio converter: pick one file, choose an output format and quality, see
 * the exact FFmpeg recipe and the predicted size, then convert on device. The
 * source is never modified, and every option is visible before the run.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AudioConverterScreen(
    texts: UiText,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf<String?>(null) }
    var sourceDuration by remember { mutableStateOf<Double?>(null) }
    var format by remember { mutableStateOf(AudioStudioEngine.OutputFormat.Mp3) }
    var bitrateMode by remember { mutableStateOf(AudioStudioEngine.BitrateMode.Cbr) }
    var bitrate by remember { mutableStateOf(192) }
    var vbrQuality by remember { mutableStateOf(2) }
    var sampleRate by remember { mutableStateOf<Int?>(null) }
    var keepTags by remember { mutableStateOf(true) }
    var normalize by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val pickSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        sourceUri = uri
        sourceName = queryDisplayName(context, uri)
        scope.launch {
            sourceDuration = AudioStudioEngine.durationSeconds(context, uri)
        }
    }

    val profile = AudioStudioEngine.ConvertProfile(
        format = format,
        bitrateMode = bitrateMode,
        bitrateKbps = bitrate,
        vbrQuality = vbrQuality,
        sampleRateHz = sampleRate,
        keepMetadata = keepTags,
        normalizeLufs = if (normalize) -14.0 else null
    )
    val predictedBytes = sourceDuration?.let { AudioStudioEngine.estimateSizeBytes(it, profile) }
    val recipe = AudioStudioEngine.commandPreview(
        input = sourceName ?: "input.audio",
        output = "output.${format.extension}",
        profile = profile
    )

    SettingsPage(texts.studioAudioConverter(), texts.studioBack(), onBack) {
        item("converter-source") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioNote(texts.studioAudioConverterDesc())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pickSource.launch(arrayOf("audio/*")) }) {
                            Text(texts.converterSource())
                        }
                        OutlinedButton(
                            enabled = sourceUri != null && !busy,
                            onClick = {
                                val uri = sourceUri ?: return@OutlinedButton
                                QueueInbox.enqueue(uri, "audio/*", format.name.uppercase())
                                status = texts.converterAddedToQueue()
                            }
                        ) { Text(texts.converterBatchAdd()) }
                        OutlinedButton(onClick = onOpenQueue) { Text(texts.queueTitle()) }
                    }
                    Text(
                        sourceName ?: texts.converterNoFile(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item("converter-format") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioFieldLabel(texts.converterOutputFormat())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AudioStudioEngine.OutputFormat.entries.forEach { option ->
                            FilterChip(
                                selected = format == option,
                                onClick = { format = option },
                                label = { Text(option.name.uppercase()) }
                            )
                        }
                    }
                    StudioNote(
                        when (format) {
                            AudioStudioEngine.OutputFormat.Mp3 -> texts.converterMp3Note()
                            AudioStudioEngine.OutputFormat.Opus -> texts.converterOpusNote()
                            AudioStudioEngine.OutputFormat.Flac -> texts.converterFlacNote()
                            AudioStudioEngine.OutputFormat.Wav -> texts.converterWavNote()
                        }
                    )
                }
            }
        }
        item("converter-quality") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioFieldLabel(texts.converterQuality())
                    if (profile.isLossless) {
                        StudioNote(texts.converterLosslessNote())
                    } else {
                        if (format == AudioStudioEngine.OutputFormat.Mp3) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = bitrateMode == AudioStudioEngine.BitrateMode.Cbr,
                                    onClick = { bitrateMode = AudioStudioEngine.BitrateMode.Cbr },
                                    label = { Text(texts.converterModeCbr()) }
                                )
                                FilterChip(
                                    selected = bitrateMode == AudioStudioEngine.BitrateMode.Vbr,
                                    onClick = { bitrateMode = AudioStudioEngine.BitrateMode.Vbr },
                                    label = { Text(texts.converterModeVbr()) }
                                )
                            }
                        }
                        if (format == AudioStudioEngine.OutputFormat.Mp3 &&
                            bitrateMode == AudioStudioEngine.BitrateMode.Vbr
                        ) {
                            StudioFieldLabel("${texts.converterVbrQuality()} · $vbrQuality")
                            Slider(
                                value = vbrQuality.toFloat(),
                                onValueChange = { vbrQuality = it.toInt().coerceIn(0, 9) },
                                valueRange = 0f..9f,
                                steps = 8
                            )
                        } else {
                            StudioFieldLabel(
                                "${texts.converterBitrate()} · ${texts.converterBitrateValue(bitrate)}"
                            )
                            Slider(
                                value = bitrate.toFloat(),
                                onValueChange = { bitrate = (it.toInt() / 32) * 32 },
                                valueRange = 64f..320f,
                                steps = 7
                            )
                        }
                    }
                    StudioFieldLabel(texts.converterSampleRate())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = sampleRate == null,
                            onClick = { sampleRate = null },
                            label = { Text(texts.converterSampleRateAuto()) }
                        )
                        listOf(44_100, 48_000, 96_000).forEach { hz ->
                            FilterChip(
                                selected = sampleRate == hz,
                                onClick = { sampleRate = hz },
                                label = { Text(texts.converterSampleRateValue(hz)) }
                            )
                        }
                    }
                    StudioSwitchRow(texts.converterKeepTags(), keepTags) { keepTags = it }
                    StudioSwitchRow(texts.converterNormalize(), normalize) { normalize = it }
                }
            }
        }
        item("converter-output") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(texts.converterEstimated(), style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${texts.converterPredictedSize()}: " +
                            (predictedBytes?.let { archiveFormatBytes(it) } ?: "—"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StudioFieldLabel(texts.converterRecipe())
                    Text(
                        recipe,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        enabled = sourceUri != null && !busy,
                        onClick = {
                            val uri = sourceUri ?: return@Button
                            busy = true
                            status = null
                            scope.launch {
                                val target = tempOutput(context, "zen-convert", format.extension)
                                val ok = withContext(Dispatchers.IO) {
                                    AudioStudioEngine.convert(context, uri, target, profile)
                                }
                                status = if (ok && target.exists()) {
                                    StudioOutput.save(
                                        context, target,
                                        (sourceName?.substringBeforeLast('.') ?: "converted") + "." + format.extension,
                                        format.mimeType, StudioOutput.Collection.Music
                                    )
                                    texts.converterDone()
                                } else {
                                    texts.studioFailed()
                                }
                                target.delete()
                                busy = false
                            }
                        }
                    ) { Text(texts.converterRun()) }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
