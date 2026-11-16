package org.zenconverter.app.recipe

import android.content.Context
import android.net.Uri
import org.zenconverter.app.audio.AudioEffects
import org.zenconverter.app.audio.AudioEffectFilters
import org.zenconverter.app.audio.AudioStudioEngine
import org.zenconverter.app.audio.CompressorPreset
import org.zenconverter.app.audio.EqualizerPreset
import org.zenconverter.app.audio.LoudnessTarget
import org.zenconverter.app.audio.SilenceRemovalMode
import org.zenconverter.app.audio.VoiceChangerPreset
import java.io.File

/**
 * Turns a [ConversionRecipe] into the concrete FFmpeg work needed to apply it to
 * a source file. The recipe model stays pure Kotlin; this object is the device
 * side that knows how to interpret each step.
 *
 * Steps are folded into a single effects chain plus, optionally, an output
 * container, so a recipe runs as one pass instead of one pass per step.
 */
object RecipeExecutor {
    data class Compiled(
        val effects: AudioEffects,
        val outputExtension: String,
        val trimToSilence: SilenceRemovalMode
    )

    /** Folds the recipe's steps into one effects configuration. */
    fun compile(recipe: ConversionRecipe): Compiled {
        var effects = AudioEffects()
        var extension = "mp3"
        recipe.steps.forEach { step ->
            effects = applyStep(effects, step)
            if (step.type == RecipeStepType.Convert) {
                extension = extensionFor(step.parameter)
            }
        }
        return Compiled(effects, extension, effects.silenceRemoval)
    }

    private fun applyStep(effects: AudioEffects, step: RecipeStep): AudioEffects = when (step.type) {
        RecipeStepType.ExtractAudio -> effects
        RecipeStepType.Denoise -> effects.copy(deHum = true)
        RecipeStepType.TrimSilence -> effects.copy(
            silenceRemoval = when (step.parameter?.lowercase()) {
                "light" -> SilenceRemovalMode.Light
                "aggressive" -> SilenceRemovalMode.Aggressive
                else -> SilenceRemovalMode.Standard
            }
        )
        RecipeStepType.Normalize -> effects.copy(loudnessTarget = loudnessFor(step.parameter))
        RecipeStepType.Equalizer -> effects.copy(equalizer = equalizerFor(step.parameter))
        RecipeStepType.VoiceChanger -> effects.copy(voiceChanger = voiceChangerFor(step.parameter))
        RecipeStepType.Convert -> effects
        // A fixed trim range needs a start and end that a recipe cannot express
        // today, so it is deliberately left to the waveform editor.
        RecipeStepType.Trim -> effects
    }

    private fun loudnessFor(parameter: String?): LoudnessTarget =
        when (parameter?.lowercase()) {
            "podcast" -> LoudnessTarget.Podcast
            "streaming" -> LoudnessTarget.Streaming
            "youtube" -> LoudnessTarget.YouTube
            "spotify" -> LoudnessTarget.Spotify
            "broadcast" -> LoudnessTarget.Broadcast
            else -> LoudnessTarget.Streaming
        }

    private fun equalizerFor(parameter: String?): EqualizerPreset =
        EqualizerPreset.entries.firstOrNull { it.labelKey.equals(parameter, ignoreCase = true) }
            ?: EqualizerPreset.entries.firstOrNull { it.name.equals(parameter, ignoreCase = true) }
            ?: EqualizerPreset.Off

    private fun voiceChangerFor(parameter: String?): VoiceChangerPreset =
        VoiceChangerPreset.entries.firstOrNull { it.labelKey.equals(parameter, ignoreCase = true) }
            ?: VoiceChangerPreset.entries.firstOrNull { it.name.equals(parameter, ignoreCase = true) }
            ?: VoiceChangerPreset.Off

    private fun extensionFor(parameter: String?): String {
        val candidate = parameter?.trim()?.lowercase()?.removePrefix(".").orEmpty()
        return if (candidate in SUPPORTED_EXTENSIONS) candidate else "mp3"
    }

    private val SUPPORTED_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")

    /**
     * Runs [recipe] against [sourceUri] and writes the result to [target].
     * Returns false when the recipe has nothing to do or the engine fails.
     */
    suspend fun execute(
        context: Context,
        sourceUri: Uri,
        recipe: ConversionRecipe,
        target: File
    ): Boolean {
        val compiled = compile(recipe)
        val filters = AudioEffectFilters.filtersFor(compiled.effects, null)
        // Filters and a container change can run in the same pass; a recipe that
        // only changes the container still gets a real transcode.
        return AudioStudioEngine.applyEffects(
            context = context,
            sourceUri = sourceUri,
            filters = filters,
            target = target,
            startSeconds = null,
            endSeconds = null,
            audioCodec = codecFor(compiled.outputExtension)
        )
    }

    private fun codecFor(extension: String): String = when (extension) {
        "m4a", "aac" -> "aac"
        "wav" -> "pcm_s16le"
        "flac" -> "flac"
        "ogg", "opus" -> "libopus"
        else -> "libmp3lame"
    }
}
