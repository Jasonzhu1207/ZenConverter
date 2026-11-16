package org.zenconverter.app.ui.studio

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.zenconverter.app.audio.EqBand
import org.zenconverter.app.audio.LoudnessAnalyzer
import org.zenconverter.app.audio.MasteringBandKind
import org.zenconverter.app.audio.MasteringEngine
import org.zenconverter.app.audio.MasteringModel
import org.zenconverter.app.audio.MasteringPlan
import org.zenconverter.app.audio.MasteringTarget
import org.zenconverter.app.audio.SpectrumAnalyzer
import org.zenconverter.app.audio.StemBalance
import org.zenconverter.app.audio.StemKind
import java.io.File

/**
 * The state behind the mastering grid, shared by the auto-master screen and the
 * sub-tool screens so they all read and write the same plan, stems and results.
 *
 * It also owns the auto-master pipeline itself, so the sequence of steps — read,
 * separate, balance, EQ, dynamics, loudness — is written down in exactly one
 * place and the screen only renders progress and the outcome.
 */
internal class MasteringUiState {
    var sourceUri by mutableStateOf<Uri?>(null)
    var sourceName by mutableStateOf<String?>(null)
    var plan by mutableStateOf(MasteringPlan())
    var format by mutableStateOf(MasteringEngine.ExportFormat.Wav)
    var busy by mutableStateOf(false)
    var stepIndex by mutableStateOf(-1)
    var status by mutableStateOf<String?>(null)
    var error by mutableStateOf(false)
    var showCommand by mutableStateOf(false)

    /** Measured picture of the source, before any mastering. */
    var analysis by mutableStateOf<MasteringEngine.Analysis?>(null)

    /** Separated stems and their measured loudness, when stems have been run. */
    var stems by mutableStateOf<List<MasteringEngine.StemAnalysis>>(emptyList())
    var balances by mutableStateOf<List<StemBalance>>(emptyList())

    /** The mastered file and the loudness measured on it after mastering. */
    var masteredFile by mutableStateOf<File?>(null)
    var masteredMeasurement by mutableStateOf<LoudnessAnalyzer.Measurement?>(null)

    /** The intermediate balanced mix, kept so a re-master can reuse the work. */
    var balancedMix by mutableStateOf<File?>(null)

    val target: MasteringTarget get() = plan.target

    /** The report used by the gauges: the master when there is one, else the source. */
    val report: MasteringModel.Report?
        get() = (masteredMeasurement ?: analysis?.measurement)?.let {
            LoudnessAnalyzer.reportFor(it, target)
        }

    fun setTarget(value: MasteringTarget) {
        plan = plan.copy(target = value)
    }

    fun updateBand(updated: EqBand) {
        plan = plan.withBand(updated)
    }

    fun setBalance(kind: StemKind, trimDb: Double) {
        balances = balances.map { if (it.kind == kind) it.copy(trimDb = trimDb) else it }
    }

    /** Returns every stem's trim to zero without re-running the separator. */
    fun clearBalance() {
        balances = balances.map { it.copy(trimDb = 0.0) }
    }

    /** Builds per-stem trims that put every stem on the same perceived level. */
    fun autoBalance() {
        balances = MasteringModel.balanceStems(stems.map { it.kind to it.lufs })
    }

    /**
     * Replaces the tone curve with gains that flatten the measured spectrum, so
     * the first master already sounds balanced instead of needing manual moves.
     */
    fun applyAutoEq() {
        val balance = analysis?.balance ?: return
        val gains = SpectrumAnalyzer.autoEqGains(balance)
        plan = plan.copy(
            bands = plan.bands.map { band ->
                val suggested = gains[band.band]
                if (suggested == null || band.band.kind == MasteringBandKind.HighPass) band
                else band.copy(gainDb = suggested)
            }
        )
    }

    fun reset() {
        plan = MasteringPlan()
        balances = emptyList()
        stems = emptyList()
        masteredFile = null
        masteredMeasurement = null
        balancedMix = null
        status = null
        error = false
        stepIndex = -1
    }

    /** How far through the auto-master pipeline we are, for the progress bar. */
    val progress: Float
        get() = if (stepIndex < 0) 0f else (stepIndex + 1) / STEPS.size.toFloat()

    /** A pipeline step: [titleKey] names it, [messageKey] is the running line. */
    data class Step(val titleKey: String, val messageKey: String)

    companion object {
        /** The auto-master pipeline, in the order the work runs. */
        val STEPS: List<Step> = listOf(
            Step("upload", "analyzing"),
            Step("stems", "separating"),
            Step("balance", "balancing"),
            Step("eq", "eq"),
            Step("dynamics", "dynamics"),
            Step("loudness", "loudness")
        )
    }
}
