package org.zenconverter.app.ui.studio

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.zenconverter.app.audio.EqBand
import org.zenconverter.app.audio.MasteringBandKind
import org.zenconverter.app.audio.MasteringEngine
import org.zenconverter.app.audio.MasteringModel
import org.zenconverter.app.audio.MasteringTarget
import org.zenconverter.app.audio.SpectrumAnalyzer
import org.zenconverter.app.audio.StemBalance
import org.zenconverter.app.audio.StudioOutput
import org.zenconverter.app.system.BatteryThermalGuard
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.settings.InsetGroupCard
import org.zenconverter.app.ui.settings.SettingsGroupHeader
import org.zenconverter.app.ui.settings.SettingsPage

/**
 * The auto-mastering screen. One button runs the whole pipeline and each step
 * lights up as it finishes, so the user always knows what is happening and that
 * nothing is being uploaded. The sub-tools below reuse the same state, so a
 * change made here shows up in the EQ, spectrum and loudness screens too.
 */
@Composable
internal fun AutoMasteringScreen(
    texts: UiText,
    state: MasteringUiState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = rememberStudioPlayer()
    val announce = rememberResultAnnouncer()
    val guard = remember(context) { BatteryThermalGuard(context) }
    var job by remember { mutableStateOf<Job?>(null) }
    val pickAudio = rememberAudioPicker { uri ->
        state.sourceUri = uri
        state.sourceName = displayNameFor(context, uri)
        state.analysis = null
        state.masteredFile = null
        state.masteredMeasurement = null
        state.stems = emptyList()
        state.balances = emptyList()
        state.status = null
        state.error = false
        state.stepIndex = -1
    }

    fun masterNow() {
        val uri = state.sourceUri ?: return
        job = scope.launch {
            state.busy = true
            state.error = false
            state.masteredFile = null
            state.masteredMeasurement = null
            state.balancedMix = null

            state.stepIndex = 0
            state.status = texts.masteringAnalyzing()
            val analysis = MasteringEngine.analyze(context, uri)
            if (analysis == null) {
                state.busy = false
                state.error = true
                state.status = texts.masteringFailed()
                return@launch
            }
            state.analysis = analysis
            state.applyAutoEq()

            // Stems are the heaviest step in the app, so hold off on a hot or
            // nearly flat battery and pick up where we left off when it recovers.
            guard.awaitReady(
                onPaused = { reason ->
                    state.status = if (reason == BatteryThermalGuard.PauseReason.Thermal) {
                        texts.guardPausedThermal()
                    } else {
                        texts.guardPausedBattery()
                    }
                },
                onResumed = { state.status = texts.masteringSeparating() }
            )

            state.stepIndex = 1
            state.status = texts.masteringSeparating()
            val stemsDir = File(context.cacheDir, "zen-master-stems-${System.nanoTime()}")
            val stems = MasteringEngine.separateAndMeasureStems(context, uri, stemsDir)
            state.stems = stems

            // The balanced mix becomes the master's source when the stems came
            // out; otherwise the master runs on the original so the flow still
            // works on a file the separator cannot handle.
            if (stems.isNotEmpty()) {
                state.stepIndex = 2
                state.status = texts.masteringBalancing()
                state.autoBalance()
                val mix = File(context.cacheDir, "zen-master-mix-${System.nanoTime()}.wav")
                val mixed = MasteringEngine.renderBalancedMix(context, stems, state.balances, mix)
                state.balancedMix = if (mixed) mix else null
            } else {
                state.balances = emptyList()
            }

            state.stepIndex = 3
            state.status = texts.masteringEqStep()
            state.stepIndex = 4
            state.status = texts.masteringDynamicsStep()

            val output = File(context.cacheDir, "zen-master-${System.nanoTime()}.${state.format.extension}")
            state.stepIndex = 5
            state.status = texts.masteringLoudnessStep()
            val mix = state.balancedMix
            val result = if (mix != null) {
                MasteringEngine.masterFile(context, mix, state.plan, output, state.format, analysis.measurement)
            } else {
                MasteringEngine.master(context, uri, state.plan, output, state.format, analysis.measurement)
            }
            state.busy = false
            if (result == null) {
                state.error = true
                state.status = texts.masteringFailed()
                return@launch
            }
            state.masteredFile = output
            state.masteredMeasurement = result
            state.status = texts.masteringDone(formatDecimal(result.inputI), formatDecimal(result.inputTp))
            announce(state.status.orEmpty())
        }
    }

    SettingsPage(texts.masteringTitle(), texts.studioBack(), onBack) {
        item("master-file") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        state.sourceName ?: texts.masteringEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.sourceName == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pickAudio() }) { Text(texts.masteringLoad()) }
                        if (state.busy) {
                            OutlinedButton(onClick = { job?.cancel(); state.busy = false; state.status = texts.masteringStopped() }) {
                                Text(texts.masteringStop())
                            }
                        } else {
                            Button(enabled = state.sourceUri != null, onClick = { masterNow() }) {
                                Text(texts.masteringStart())
                            }
                        }
                        if (state.sourceUri != null) {
                            OutlinedButton(onClick = { state.reset() }) { Text(texts.masteringReset()) }
                        }
                    }
                    if (state.busy) {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        Text(texts.masteringRunning(), style = MaterialTheme.typography.labelMedium)
                    }
                    state.status?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item("master-steps-header") { SettingsGroupHeader(texts.masteringRunning()) }
        item("master-steps") {
            InsetGroupCard {
                MasteringSteps(texts, state.stepIndex)
            }
        }

        item("master-target-header") { SettingsGroupHeader(texts.masteringTarget()) }
        item("master-target") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MasteringTarget.entries.forEach { option ->
                            FilterChip(
                                selected = state.target == option,
                                onClick = { state.setTarget(option) },
                                label = { Text(texts.masteringTargetLabel(option)) }
                            )
                        }
                    }
                    Text(
                        texts.masteringTargetNote(state.target),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item("master-gauges") {
            InsetGroupCard {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // Tablets and foldables get the meters beside the visualizers;
                    // phones stack them.
                    val twoPane = maxWidth >= 720.dp
                    val meters: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            LoudnessGauges(texts, state.report)
                            if (state.masteredFile != null) {
                                MasteredActions(
                                    texts = texts,
                                    state = state,
                                    onPlayOriginal = {
                                        state.sourceUri?.let { player.play(it, texts.masteringAb()) }
                                    },
                                    onPlayMaster = {
                                        state.masteredFile?.let {
                                            player.play(Uri.fromFile(it), texts.masteringExport())
                                        }
                                    },
                                    onStop = { player.stop() },
                                    onExport = {
                                        val file = state.masteredFile ?: return@MasteredActions
                                        val name = "${state.sourceName?.substringBeforeLast('.') ?: "master"}-master.${state.format.extension}"
                                        StudioOutput.save(context, file, name, state.format.mimeType, StudioOutput.Collection.Music)
                                        state.status = texts.masteringExported()
                                    },
                                    onToggleCommand = { state.showCommand = !state.showCommand }
                                )
                            }
                        }
                    }
                    val visuals: @Composable () -> Unit = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val analysis = state.analysis
                            if (analysis != null) {
                                WaveformShapeBlock(texts, analysis.peaks, state.report)
                            }
                            SpectrumCanvas(
                                texts = texts,
                                balance = state.analysis?.balance,
                                modifier = Modifier.fillMaxWidth().height(120.dp)
                            )
                        }
                    }
                    val padding = Modifier.padding(14.dp)
                    if (twoPane) {
                        Row(padding, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f)) { meters() }
                            Column(Modifier.weight(1f)) { visuals() }
                        }
                    } else {
                        Column(padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            meters()
                            visuals()
                        }
                    }
                }
            }
        }

        if (state.showCommand) {
            item("master-command") {
                InsetGroupCard {
                    CommandView(
                        texts,
                        MasteringModel.commandFor(
                            state.plan,
                            state.sourceName ?: "in.wav",
                            "master.${state.format.extension}",
                            state.analysis?.measurement
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun MasteringSteps(texts: UiText, stepIndex: Int) {
    val steps = MasteringUiState.STEPS
    Column(Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            val done = index < stepIndex
            val active = index == stepIndex
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (done) "✓" else "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        done -> MaterialTheme.colorScheme.primary
                        active -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Column(Modifier.weight(1f)) {
                    Text(stepTitle(texts, step.titleKey), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stepSubtitle(texts, step.titleKey),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun stepTitle(texts: UiText, key: String): String = when (key) {
    "upload" -> texts.masteringStepUpload()
    "stems" -> texts.masteringStepStems()
    "balance" -> texts.masteringStepBalance()
    "eq" -> texts.masteringStepEq()
    "dynamics" -> texts.masteringStepDynamics()
    else -> texts.masteringStepLoudness()
}

private fun stepSubtitle(texts: UiText, key: String): String = when (key) {
    "upload" -> texts.masteringStepUploadDesc()
    "stems" -> texts.masteringStepStemsDesc()
    "balance" -> texts.masteringStepBalanceDesc()
    "eq" -> texts.masteringStepEqDesc()
    "dynamics" -> texts.masteringStepDynamicsDesc()
    else -> texts.masteringStepLoudnessDesc()
}

@Composable
private fun MasteredActions(
    texts: UiText,
    state: MasteringUiState,
    onPlayOriginal: () -> Unit,
    onPlayMaster: () -> Unit,
    onStop: () -> Unit,
    onExport: () -> Unit,
    onToggleCommand: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPlayOriginal) { Text(texts.masteringAb()) }
            OutlinedButton(onClick = onPlayMaster) { Text(texts.masteringExport()) }
            OutlinedButton(onClick = onStop) { Text(texts.studioStop()) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MasteringEngine.ExportFormat.entries.forEach { option ->
                FilterChip(
                    selected = state.format == option,
                    onClick = { state.format = option },
                    label = { Text(option.extension.uppercase()) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onExport) { Text(texts.masteringExport()) }
            OutlinedButton(onClick = onToggleCommand) {
                Text(if (state.showCommand) texts.masteringHideCommand() else texts.masteringShowCommand())
            }
        }
    }
}

/**
 * The stem screen. Runs the separator once and shows each stem with a slider for
 * its loudness trim, so a quiet vocal can be lifted without touching the drums.
 */
@Composable
internal fun StemBalanceScreen(texts: UiText, state: MasteringUiState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun separate() {
        val uri = state.sourceUri ?: return
        busy = true
        scope.launch {
            val dir = File(context.cacheDir, "zen-balance-stems-${System.nanoTime()}")
            val stems = MasteringEngine.separateAndMeasureStems(context, uri, dir)
            state.stems = stems
            if (stems.isNotEmpty()) state.autoBalance()
            busy = false
        }
    }

    SettingsPage(texts.masteringStemsTitle(), texts.studioBack(), onBack) {
        item("stems-note") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(texts.masteringStemsDesc(), style = MaterialTheme.typography.bodySmall)
                    if (state.sourceUri == null) {
                        StudioNote(texts.masteringEmpty())
                    } else {
                        Text(
                            state.sourceName.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = state.sourceUri != null && !busy, onClick = { separate() }) {
                            Text(texts.masteringStemsRun())
                        }
                        OutlinedButton(enabled = state.balances.isNotEmpty(), onClick = { state.autoBalance() }) {
                            Text(texts.masteringAutoBalance())
                        }
                        OutlinedButton(enabled = state.balances.isNotEmpty(), onClick = { state.clearBalance() }) {
                            Text(texts.masteringResetBalance())
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        if (state.balances.isEmpty()) {
            item("stems-empty") { InsetGroupCard { StudioNote(texts.masteringStemsEmpty()) } }
        } else {
            item("stems-list") {
                InsetGroupCard {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.balances.forEach { balance ->
                            StemBalanceRow(texts, balance) { trimmed -> state.setBalance(balance.kind, trimmed) }
                        }
                        Text(
                            texts.masteringBalanceNote(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StemBalanceRow(texts: UiText, balance: StemBalance, onChange: (Double) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(texts.stemLabel(balance.kind), style = MaterialTheme.typography.labelLarge)
            Text(
                "${signedDecimal(balance.trimDb)} dB",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = balance.trimDb.toFloat(),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = -6f..6f
        )
    }
}

/** The spectrum screen: octave bars with problem peaks highlighted. */
@Composable
internal fun SpectrumAnalyzerScreen(texts: UiText, state: MasteringUiState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun analyze() {
        val uri = state.sourceUri ?: return
        busy = true
        scope.launch {
            state.analysis = MasteringEngine.analyze(context, uri)
            busy = false
        }
    }

    SettingsPage(texts.masteringSpectrumTitle(), texts.studioBack(), onBack) {
        item("spectrum-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpectrumCanvas(
                        texts = texts,
                        balance = state.analysis?.balance,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                    if (state.analysis == null) {
                        Text(
                            texts.masteringSpectrumEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Legend(texts)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = state.sourceUri != null && !busy, onClick = { analyze() }) {
                            Text(texts.masteringSpectrumAnalyze())
                        }
                        OutlinedButton(enabled = state.analysis != null, onClick = { state.applyAutoEq() }) {
                            Text(texts.masteringSpectrumFlatten())
                        }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.analysis?.balance?.let { balance ->
                        Text(
                            if (balance.isBalanced) texts.masteringSpectrumBalanced() else texts.masteringSpectrumFlatten(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(texts: UiText) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LegendItem(MaterialTheme.colorScheme.primary, texts.masteringSpectrumLegendAudio())
        LegendItem(MaterialTheme.colorScheme.outline, texts.masteringSpectrumLegendReference())
        LegendItem(MaterialTheme.colorScheme.error, texts.masteringSpectrumLegendPeak())
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.height(10.dp).fillMaxWidth(0.04f).background(color, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Draws the octave-band balance. Bars above the healthy reference line are the
 * bands the auto-EQ would cut; a problem peak is drawn in the error color so it
 * is obvious at a glance.
 */
@Composable
private fun SpectrumCanvas(
    texts: UiText,
    balance: SpectrumAnalyzer.Balance?,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val peakColor = MaterialTheme.colorScheme.error
    val referenceColor = MaterialTheme.colorScheme.outline
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val bands = SpectrumAnalyzer.BAND_CENTERS_HZ.size
        val gap = 4f
        val barWidth = (size.width - gap * (bands - 1)) / bands
        val referenceY = size.height * 0.45f
        drawLine(
            color = referenceColor,
            start = Offset(0f, referenceY),
            end = Offset(size.width, referenceY),
            strokeWidth = 2f
        )
        if (balance == null) {
            drawLine(
                color = emptyColor,
                start = Offset(0f, size.height * 0.9f),
                end = Offset(size.width, size.height * 0.9f),
                strokeWidth = 2f
            )
            return@Canvas
        }
        for (index in 0 until bands) {
            val value = balance.bars.getOrNull(index)?.toFloat() ?: 0f
            val isPeak = index in balance.problemPeakIndices
            val barHeight = (size.height * 0.9f * value).coerceAtLeast(2f)
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = if (isPeak) peakColor else barColor,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 3f)
            )
        }
    }
}

/** The EQ screen: six bands with a curve drawn from their real frequencies. */
@Composable
internal fun MasteringEqScreen(texts: UiText, state: MasteringUiState, onBack: () -> Unit) {
    SettingsPage(texts.masteringEqTitle(), texts.studioBack(), onBack) {
        item("eq-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(texts.masteringEqDesc(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    EqCurveCanvas(state.plan.bands, Modifier.fillMaxWidth().height(150.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { state.applyAutoEq() }) { Text(texts.masteringEqAuto()) }
                        OutlinedButton(onClick = { state.plan = state.plan.copy(bands = EqBand.defaultCurve()) }) {
                            Text(texts.masteringEqFlat())
                        }
                    }
                    state.plan.bands.forEach { band ->
                        EqBandRow(texts, band) { state.updateBand(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqBandRow(texts: UiText, band: EqBand, onChange: (EqBand) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(texts.masteringBandLabel(band.band), style = MaterialTheme.typography.labelLarge)
            Text(
                "${band.hz.toInt()} Hz · " + texts.masteringEqGain(signedDecimal(band.gainDb)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (band.band.kind == MasteringBandKind.HighPass) {
            Text(
                texts.masteringEqQ(formatDecimal(band.q)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Slider(
                value = band.gainDb.toFloat(),
                onValueChange = { onChange(band.copy(gainDb = it.toDouble())) },
                valueRange = -12f..12f
            )
        }
    }
}

/**
 * Approximates the EQ's frequency response so the drawn curve matches what the
 * filters will actually do. Each bell is a smooth bump centred on its own
 * frequency, scaled by its gain and widened by its Q.
 */
@Composable
private fun EqCurveCanvas(bands: List<EqBand>, modifier: Modifier = Modifier) {
    val curveColor = MaterialTheme.colorScheme.primary
    val zeroColor = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val zeroY = size.height / 2f
        drawLine(zeroColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 2f)
        val minLog = ln(SpectrumAnalyzer.MIN_HZ)
        val maxLog = ln(SpectrumAnalyzer.MAX_HZ)
        val path = Path()
        val steps = 64
        for (step in 0..steps) {
            val fraction = step / steps.toFloat()
            val logHz = minLog + (maxLog - minLog) * fraction
            val hz = kotlin.math.exp(logHz)
            val gain = responseAt(bands, hz)
            val x = size.width * fraction
            val y = zeroY - (gain / 12.0).toFloat() * (size.height / 2f - 8f)
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, curveColor, style = Stroke(width = 3f))
    }
}

private fun responseAt(bands: List<EqBand>, hz: Double): Double {
    var total = 0.0
    bands.filter { it.enabled && it.band.kind == MasteringBandKind.Bell }.forEach { band ->
        val octaves = ln(hz / band.hz) / ln(2.0)
        val width = 1.0 / band.q.coerceAtLeast(0.2)
        total += band.gainDb * kotlin.math.exp(-(octaves * octaves) / (2.0 * width * width))
    }
    return total
}

/** The loudness and dynamics screen: the four numbers an engineer checks. */
@Composable
internal fun LoudnessDynamicsScreen(texts: UiText, state: MasteringUiState, onBack: () -> Unit) {
    SettingsPage(texts.masteringLoudnessTitle(), texts.studioBack(), onBack) {
        item("loudness-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LoudnessGauges(texts, state.report)
                    StudioSwitchRow(texts.masteringClipGuard(), state.plan.clipGuard) {
                        state.plan = state.plan.copy(clipGuard = it)
                    }
                    StudioSwitchRow(texts.masteringAdaptiveLimiting(), state.plan.adaptiveLimiting) {
                        state.plan = state.plan.copy(adaptiveLimiting = it)
                    }
                    StudioSwitchRow(texts.masteringDeEsser(), state.plan.deEsser) {
                        state.plan = state.plan.copy(deEsser = it)
                    }
                    StudioSwitchRow(texts.masteringMonoCheck(), state.plan.monoCheck) {
                        state.plan = state.plan.copy(monoCheck = it)
                    }
                }
            }
        }
        item("loudness-ceiling") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        texts.masteringCeilingLabel(formatDecimal(state.target.truePeakDb)),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        texts.masteringLoudnessCeiling(formatDecimal(state.target.lufs)),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        texts.masteringTargetNote(state.target),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LoudnessGauges(texts: UiText, report: MasteringModel.Report?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GaugeRow(
            texts.masteringLoudnessIntegrated(),
            report?.let { texts.masteringLoudnessIntegratedHint(formatDecimal(it.target.lufs)) }
                ?: texts.masteringLoudnessPlaceholder(),
            report?.let { texts.masteringTargetLufs(formatDecimal(it.integratedLufs)) } ?: texts.masteringLoudnessPlaceholder()
        )
        GaugeRow(
            texts.masteringLoudnessTruePeak(),
            report?.let { texts.masteringLoudnessTruePeakHint(formatDecimal(it.target.truePeakDb)) }
                ?: texts.masteringLoudnessPlaceholder(),
            report?.let { texts.masteringTargetPeak(formatDecimal(it.truePeakDb)) } ?: texts.masteringLoudnessPlaceholder()
        )
        GaugeRow(
            texts.masteringLoudnessRange(),
            texts.masteringLoudnessRangeHint(),
            report?.let { "${formatDecimal(it.loudnessRangeLu)} LU" } ?: texts.masteringLoudnessPlaceholder()
        )
        GaugeRow(
            texts.masteringLoudnessDynamics(),
            texts.masteringLoudnessDynamicsHint(),
            report?.let { "${formatDecimal(it.crestFactorDb)} dB" } ?: texts.masteringLoudnessPlaceholder()
        )
    }
}

@Composable
private fun GaugeRow(label: String, hint: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** A simple waveform drawn from the decoded peaks, with the shape verdict. */
@Composable
private fun WaveformShapeBlock(texts: UiText, peaks: FloatArray, report: MasteringModel.Report?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(texts.masteringShapeTitle(), style = MaterialTheme.typography.labelLarge)
        WaveformShapeCanvas(peaks, Modifier.fillMaxWidth().height(70.dp))
        Text(
            texts.masteringShapeDesc(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        report?.let {
            val shape = MasteringModel.shapeFor(it)
            Text(texts.masteringShapeLabel(shape), style = MaterialTheme.typography.labelLarge)
            Text(
                texts.masteringShapeNote(shape),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WaveformShapeCanvas(peaks: FloatArray, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (peaks.isEmpty()) return@Canvas
        val centerY = size.height / 2f
        val half = size.height / 2f
        val step = size.width / peaks.size
        val amplitude = peaks.maxOrNull()?.takeIf { it > 0.001f } ?: 1f
        for (index in peaks.indices) {
            val value = (peaks[index] / amplitude).coerceIn(0f, 1f)
            val x = index * step + step / 2f
            drawLine(
                color = color.copy(alpha = 0.8f),
                start = Offset(x, centerY - value * half),
                end = Offset(x, centerY + value * half),
                strokeWidth = step.coerceAtLeast(1f)
            )
        }
    }
}

/** The radio-ready check: each criterion against its target. */
@Composable
internal fun RadioReadyCheckScreen(texts: UiText, state: MasteringUiState, onBack: () -> Unit) {
    val report = state.report
    SettingsPage(texts.masteringCheckTitle(), texts.studioBack(), onBack) {
        item("check-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (report == null) {
                        StudioNote(texts.masteringEmpty())
                    } else {
                        CheckRow(
                            texts.masteringCheckLoudness(),
                            texts.masteringCheckValue("${formatDecimal(report.integratedLufs)} LUFS", "${formatDecimal(report.target.lufs)} LUFS"),
                            report.loudnessOnTarget
                        )
                        CheckRow(
                            texts.masteringCheckTruePeak(),
                            texts.masteringCheckValue("${formatDecimal(report.truePeakDb)} dBTP", "${formatDecimal(report.target.truePeakDb)} dBTP"),
                            !report.clipping
                        )
                        CheckRow(
                            texts.masteringCheckRange(),
                            "${formatDecimal(report.loudnessRangeLu)} LU",
                            !report.overCompressed
                        )
                        CheckRow(
                            texts.masteringCheckClipping(),
                            if (report.clipping) texts.masteringCheckClippingYes() else texts.masteringCheckClippingNo(),
                            !report.clipping
                        )
                        CheckRow(
                            texts.masteringCheckPhase(),
                            if (state.plan.monoCheck) texts.masteringCheckMonoSafe() else texts.masteringCheckClippingNo(),
                            state.plan.monoCheck
                        )
                        val sampleRate = state.analysis?.sampleRateHz
                        CheckRow(
                            texts.masteringCheckSampleRate(),
                            if (sampleRate != null) texts.masteringCheckKept("${sampleRate / 1000} kHz")
                            else texts.masteringCheckClippingNo(),
                            sampleRate != null
                        )
                        MasteringVerdictCard(texts, report)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, value: String, pass: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (pass) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun MasteringVerdictCard(texts: UiText, report: MasteringModel.Report) {
    val verdict = MasteringModel.verdictFor(report)
    val accent = if (report.healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Column(
        Modifier.fillMaxWidth().background(accent.copy(alpha = 0.08f), RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(texts.masteringVerdictLabel(verdict), style = MaterialTheme.typography.titleSmall, color = accent)
        Text(
            texts.masteringVerdictNote(verdict),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The one-tap loudness normalizer, aimed at the −14 LUFS streaming target. */
@Composable
internal fun LoudnessNormalizationScreen(texts: UiText, state: MasteringUiState, onBack: () -> Unit) {
    SettingsPage(texts.studioLoudnessNorm(), texts.studioBack(), onBack) {
        item("loudnorm-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(texts.studioLoudnessNormDesc(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MasteringTarget.entries.forEach { option ->
                            FilterChip(
                                selected = state.target == option,
                                onClick = { state.setTarget(option) },
                                label = { Text(texts.masteringTargetLabel(option)) }
                            )
                        }
                    }
                    LoudnessGauges(texts, state.report)
                    CommandView(
                        texts,
                        MasteringModel.loudnormFilter(state.plan, state.analysis?.measurement)
                    )
                }
            }
        }
    }
}

/** The harmonic exciter: a single switch with a live preview of the added air. */
@Composable
internal fun HarmonicExciterScreen(texts: UiText, state: MasteringUiState, onBack: () -> Unit) {
    SettingsPage(texts.studioExciter(), texts.studioBack(), onBack) {
        item("exciter-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(texts.studioExciterDesc(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StudioSwitchRow(texts.masteringExciterSwitch(), state.plan.exciter) {
                        state.plan = state.plan.copy(exciter = it)
                    }
                    EqCurveCanvas(state.plan.bands, Modifier.fillMaxWidth().height(150.dp))
                    CommandView(texts, MasteringModel.filterChain(state.plan, state.analysis?.measurement))
                }
            }
        }
    }
}

/** Shows a generated FFmpeg command so power users can learn from and copy it. */
@Composable
internal fun CommandView(texts: UiText, command: String) {
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(texts.studioCommandView(), style = MaterialTheme.typography.labelLarge)
        Text(
            command,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDecimal(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.1f", value)

private fun signedDecimal(value: Double): String {
    val formatted = formatDecimal(abs(value))
    return if (value > 0) "+$formatted" else if (value < 0) "-$formatted" else "0"
}

private fun displayNameFor(context: android.content.Context, uri: Uri): String {
    val cursor = runCatching { context.contentResolver.query(uri, null, null, null, null) }.getOrNull()
    cursor?.use {
        val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && it.moveToFirst()) return it.getString(index)
    }
    return uri.lastPathSegment ?: "audio"
}
