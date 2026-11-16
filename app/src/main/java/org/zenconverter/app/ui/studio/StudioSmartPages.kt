package org.zenconverter.app.ui.studio

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import org.zenconverter.app.audio.AudioDecoder
import org.zenconverter.app.audio.AudioStudioEngine
import org.zenconverter.app.audio.StemKind
import org.zenconverter.app.audio.StemSplitter
import org.zenconverter.app.audio.StudioOutput
import org.zenconverter.app.audio.SubtitleExport
import org.zenconverter.app.audio.TranscriptSegment
import org.zenconverter.app.audio.TranscriptionEngine
import org.zenconverter.app.audio.WhisperTranscriptParser
import org.zenconverter.app.conversion.ConversionTaskStore
import org.zenconverter.app.model.WhisperModelManager
import org.zenconverter.app.recipe.ConversionRecipe
import org.zenconverter.app.recipe.RecipeExecutor
import org.zenconverter.app.recipe.RecipeLibrary
import org.zenconverter.app.studio.PluginPresetCodec
import org.zenconverter.app.studio.StudioStore
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.settings.InsetGroupCard
import org.zenconverter.app.ui.settings.SettingsGroupHeader
import org.zenconverter.app.ui.settings.SettingsPage

@Composable
internal fun TranscribeScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var transcript by remember { mutableStateOf("") }
    var durationSeconds by remember { mutableStateOf(0.0) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var modelReady by remember { mutableStateOf(WhisperModelManager.isDownloaded(context)) }
    var segments by remember { mutableStateOf<List<TranscriptSegment>>(emptyList()) }
    val pickAudio = rememberAudioPicker { uri -> sourceUri = uri }
    val engineAvailable = WhisperModelManager.INFERENCE_AVAILABLE

    SettingsPage(texts.transcribeTitle(), texts.studioBack(), onBack) {
        item("transcribe-engine") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!engineAvailable) {
                        Text(
                            texts.transcribeEngineUnavailable(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = pickAudio) { Text(texts.transcribeLoad()) }
                        OutlinedButton(
                            enabled = !modelReady && downloadProgress == null,
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
                    Text(
                        if (modelReady) texts.studioModelReady() else texts.transcribeModelNeeded(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item("transcribe-run") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.transcribeManualHint(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = transcript,
                        onValueChange = { transcript = it },
                        label = { Text(texts.transcribeTitle()) },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = sourceUri != null && !busy,
                        onClick = {
                            val uri = sourceUri ?: return@Button
                            busy = true
                            scope.launch {
                                val decoded = AudioDecoder.decodeMono(context, uri)
                                durationSeconds = decoded?.let { it.samples.size.toDouble() / it.sampleRateHz } ?: 0.0
                                if (engineAvailable && modelReady) {
                                    val result = TranscriptionEngine.transcribe(context, uri)
                                    if (result.isNotEmpty()) {
                                        segments = result
                                        transcript = WhisperTranscriptParser.toPlainText(result)
                                        status = texts.studioModelReady()
                                    } else {
                                        status = texts.transcribeEngineUnavailable()
                                    }
                                } else {
                                    // Manual path: the typed transcript covers the clip.
                                    segments = emptyList()
                                    status = texts.studioModelReady()
                                }
                                busy = false
                            }
                        }
                    ) { Text(texts.transcribeStart()) }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (transcript.isNotBlank()) {
            // Prefer the timed segments from the engine; a typed transcript has
            // only the measured clip range to attach to.
            val exportSegments = segments.ifEmpty {
                listOf(
                    TranscriptSegment(0.0, durationSeconds.coerceAtLeast(0.0), transcript.trim())
                )
            }
            item("transcribe-edit") {
                InsetGroupCard {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { exportTranscript(context, texts, exportSegments, "srt") { status = it } }) {
                                Text(texts.transcribeExportSrt())
                            }
                            OutlinedButton(onClick = { exportTranscript(context, texts, exportSegments, "vtt") { status = it } }) {
                                Text(texts.transcribeExportVtt())
                            }
                            OutlinedButton(onClick = { exportTranscript(context, texts, exportSegments, "txt") { status = it } }) {
                                Text(texts.transcribeExportTxt())
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun exportTranscript(
    context: android.content.Context,
    texts: UiText,
    segments: List<TranscriptSegment>,
    extension: String,
    onStatus: (String) -> Unit
) {
    val format = SubtitleExport.Format.entries.firstOrNull { it.extension == extension }
        ?: SubtitleExport.Format.Text
    val payload = SubtitleExport.render(segments, format)
    val file = java.io.File(context.cacheDir, "zen-transcript-${System.currentTimeMillis()}.$extension")
    val written = runCatching { file.writeText(payload) }.isSuccess
    if (!written) {
        file.delete()
        onStatus(texts.transcribeExportFailed())
        return
    }
    runCatching {
        StudioOutput.save(context, file, file.name, "text/plain", StudioOutput.Collection.Documents)
    }.onSuccess { onStatus(texts.studioSaved()) }
        .onFailure { onStatus(texts.transcribeExportFailed()) }
    file.delete()
}

@Composable
internal fun StemSeparationScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val pickAudio = rememberAudioPicker { uri -> sourceUri = uri }
    val neuralAvailable = StemSplitter.isNeuralAvailable(context)

    SettingsPage(texts.stemsTitle(), texts.studioBack(), onBack) {
        item("stems-note") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!neuralAvailable) {
                        Text(
                            texts.stemsEngineUnavailable(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            texts.stemsApproximateNote(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item("stems-run") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = pickAudio) { Text(texts.studioPickAudio()) }
                        OutlinedButton(
                            enabled = sourceUri != null && !busy,
                            onClick = {
                                val uri = sourceUri ?: return@OutlinedButton
                                busy = true
                                scope.launch {
                                    val outputDir = File(context.cacheDir, "zen-stems-${System.currentTimeMillis()}")
                                    val result = StemSplitter.separate(context, uri, outputDir)
                                    status = if (result.stems.isEmpty()) {
                                        texts.studioFailed()
                                    } else {
                                        result.stems.forEach { (kind, file) ->
                                            StudioOutput.save(
                                                context,
                                                file,
                                                "zenconverter-${kind.name.lowercase()}.wav",
                                                "audio/wav",
                                                StudioOutput.Collection.Music
                                            )
                                        }
                                        texts.stemsDone(result.stems.size)
                                    }
                                    outputDir.deleteRecursively()
                                    busy = false
                                }
                            }
                        ) { Text(texts.stemsRunOffline()) }
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
internal fun RecipesScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var sourceUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    StudioStore.ensureLoaded(context)
    val customRecipes = StudioStore.customRecipes.toList()
    val pickAudio = rememberAudioPicker { uri -> sourceUri = uri }

    fun runRecipe(recipe: ConversionRecipe) {
        val uri = sourceUri
        if (uri == null) {
            status = texts.studioNeedsFile()
            return
        }
        busy = true
        scope.launch {
            val extension = RecipeExecutor.compile(recipe).outputExtension
            val temp = tempOutput(context, "zen-recipe", extension)
            val ok = RecipeExecutor.execute(context, uri, recipe, temp)
            status = if (ok) {
                StudioOutput.save(
                    context,
                    temp,
                    "zenconverter-${recipe.id}-${System.currentTimeMillis()}.$extension",
                    "audio/*",
                    StudioOutput.Collection.Music
                )
                temp.delete()
                texts.recipesRan()
            } else {
                temp.delete()
                texts.recipesNoSteps()
            }
            busy = false
        }
    }

    SettingsPage(texts.recipesTitle(), texts.studioBack(), onBack) {
        item("recipes-source") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = pickAudio) { Text(texts.studioPickAudio()) }
                    }
                    Text(
                        if (sourceUri == null) texts.studioNeedsFile() else texts.studioSaved(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item("recipes-builtin-header") { SettingsGroupHeader(texts.recipesBuiltin()) }
        item("recipes-builtin") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RecipeLibrary.builtIn.forEach { recipe ->
                        RecipeCard(texts, recipe, enabled = sourceUri != null && !busy, onRun = { runRecipe(recipe) })
                    }
                }
            }
        }
        item("recipes-custom-header") { SettingsGroupHeader(texts.recipesCustom()) }
        item("recipes-custom") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (customRecipes.isEmpty()) {
                        Text(
                            texts.studioNeedsFile(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        customRecipes.forEach { recipe ->
                            RecipeCard(texts, recipe, enabled = sourceUri != null && !busy, onRun = { runRecipe(recipe) })
                        }
                    }
                }
            }
        }
        item("recipes-import") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text(texts.recipesImport()) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = importText.isNotBlank(),
                            onClick = {
                                val imported = RecipeLibrary.decodeImported(importText)
                                if (imported.isEmpty()) {
                                    status = texts.recipesImportFailed()
                                } else {
                                    StudioStore.addRecipes(context, imported)
                                    status = texts.recipesImported(imported.size)
                                    importText = ""
                                }
                            }
                        ) { Text(texts.recipesImport()) }
                        OutlinedButton(
                            enabled = customRecipes.isNotEmpty(),
                            onClick = {
                                val encoded = RecipeLibrary.encode(customRecipes)
                                runCatching {
                                    val manager = context.getSystemService(android.content.ClipboardManager::class.java)
                                    manager?.setPrimaryClip(android.content.ClipData.newPlainText("recipes", encoded))
                                }
                                status = texts.recipesExported()
                            }
                        ) { Text(texts.recipesExport()) }
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
private fun RecipeCard(
    texts: UiText,
    recipe: ConversionRecipe,
    enabled: Boolean,
    onRun: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(recipe.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            recipe.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            recipe.steps.mapIndexed { index, step -> "${index + 1}. ${step.type.labelKey}" }.joinToString("  "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        OutlinedButton(enabled = enabled, onClick = onRun) { Text(texts.recipesRun()) }
    }
}

@Composable
internal fun HistoryScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    StudioStore.ensureLoaded(context)
    val entries = StudioStore.history.toList()
    val player = rememberStudioPlayer()

    SettingsPage(texts.historyTitle(), texts.studioBack(), onBack) {
        item("history-list") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (entries.isEmpty()) {
                        Text(
                            texts.historyEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        entries.forEach { entry ->
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(entry.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${entry.targetFormat} · ${entry.timestampMs}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                entry.sizeDeltaBytes?.let { delta ->
                                    Text(
                                        if (delta <= 0) texts.historySaved("${-delta / 1024} KB")
                                        else texts.historyGrew("${delta / 1024} KB"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                entry.outputUri?.let { uri ->
                                    OutlinedButton(
                                        onClick = { player.play(Uri.parse(uri), entry.displayName) }
                                    ) { Text(texts.studioPlay()) }
                                }
                            }
                        }
                        Button(onClick = { StudioStore.clearHistory(context) }) { Text(texts.historyClear()) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AbPlayerScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = rememberStudioPlayer()
    StudioStore.ensureLoaded(context)
    val last = StudioStore.history.firstOrNull()

    SettingsPage(texts.abPlayerTitle(), texts.studioBack(), onBack) {
        item("ab-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (last == null) {
                        Text(
                            texts.abNoOutput(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { player.play(Uri.parse(last.sourceUri), texts.abOriginal()) }) {
                                Text(texts.abOriginal())
                            }
                            OutlinedButton(
                                onClick = {
                                    last.outputUri?.let { player.play(Uri.parse(it), texts.abConverted()) }
                                }
                            ) { Text(texts.abConverted()) }
                        }
                        OutlinedButton(onClick = { player.stop() }) { Text(texts.studioStop()) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CommandViewScreen(texts: UiText, onBack: () -> Unit) {
    val command by ConversionTaskStore.lastFfmpegCommand
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    SettingsPage(texts.commandTitle(), texts.studioBack(), onBack) {
        item("command-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.commandDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val current = command
                    if (current.isNullOrBlank()) {
                        Text(
                            texts.commandEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            current,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        )
                        Button(
                            onClick = {
                                clipboard.setText(AnnotatedString("ffmpeg $current"))
                                copied = true
                            }
                        ) { Text(if (copied) texts.commandCopied() else texts.commandCopy()) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PluginsScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    var importText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    StudioStore.ensureLoaded(context)
    val presets = StudioStore.pluginPresets.toList()

    SettingsPage(texts.pluginsTitle(), texts.studioBack(), onBack) {
        item("plugins-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.pluginsDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (presets.isEmpty()) {
                        Text(
                            texts.pluginsNone(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        presets.forEach { preset ->
                            Column(Modifier.fillMaxWidth()) {
                                Text(preset.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    preset.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        label = { Text(texts.pluginsImport()) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = importText.isNotBlank(),
                            onClick = {
                                val imported = PluginPresetCodec.decodeImported(importText)
                                if (imported.isEmpty()) {
                                    status = texts.pluginsImportFailed()
                                } else {
                                    StudioStore.addPluginPresets(context, imported)
                                    status = texts.pluginsImported(imported.size)
                                    importText = ""
                                }
                            }
                        ) { Text(texts.pluginsImport()) }
                        OutlinedButton(
                            enabled = presets.isNotEmpty(),
                            onClick = {
                                val encoded = PluginPresetCodec.encode(presets)
                                runCatching {
                                    val manager = context.getSystemService(android.content.ClipboardManager::class.java)
                                    manager?.setPrimaryClip(android.content.ClipData.newPlainText("presets", encoded))
                                }
                                status = texts.pluginsExported()
                            }
                        ) { Text(texts.pluginsExport()) }
                    }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
