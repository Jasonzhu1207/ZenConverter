package org.zenconverter.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenconverter.app.recipe.ConversionRecipe
import org.zenconverter.app.recipe.RecipeExecutor
import org.zenconverter.app.recipe.RecipeStep
import org.zenconverter.app.recipe.RecipeStepType

class StemFiltersTest {
    @Test
    fun graphSplitsIntoTheFourStems() {
        val graph = StemFilters.graph()
        assertEquals(4, graph.outputs.size)
        assertEquals(
            listOf(StemKind.Bass, StemKind.Drums, StemKind.Vocals, StemKind.Other),
            graph.outputs.map { it.kind }
        )
    }

    @Test
    fun graphAsplitsTheStereoInputOncePerStem() {
        val graph = StemFilters.graph()
        assertTrue(graph.filterComplex.contains("asplit=4"))
        assertTrue(graph.filterComplex.contains("[s0]"))
        assertTrue(graph.filterComplex.contains("[s3]"))
    }

    @Test
    fun everyOutputLabelIsProducedByTheGraph() {
        val graph = StemFilters.graph()
        graph.outputs.forEach { output ->
            assertTrue("missing ${output.label}", graph.filterComplex.contains(output.label))
        }
    }
}

class SubtitleExportTest {
    private val segments = listOf(
        TranscriptSegment(0.0, 1.5, "hello"),
        TranscriptSegment(2.0, 3.25, "world")
    )

    @Test
    fun srtNumbersCuesAndUsesCommaMilliseconds() {
        val srt = SubtitleExport.render(segments, SubtitleExport.Format.Srt)
        assertTrue(srt.startsWith("1\n"))
        assertTrue(srt.contains("00:00:00,000 --> 00:00:01,500"))
        assertTrue(srt.contains("00:00:02,000 --> 00:00:03,250"))
    }

    @Test
    fun vttStartsWithTheHeaderAndUsesDots() {
        val vtt = SubtitleExport.render(segments, SubtitleExport.Format.Vtt)
        assertTrue(vtt.startsWith("WEBVTT\n\n"))
        assertTrue(vtt.contains("00:00:00.000 --> 00:00:01.500"))
    }

    @Test
    fun plainTextDropsTimings() {
        val text = SubtitleExport.render(segments, SubtitleExport.Format.Text)
        assertEquals("hello\nworld\n", text)
    }

    @Test
    fun emptySegmentsProduceNoCues() {
        val srt = SubtitleExport.render(emptyList(), SubtitleExport.Format.Srt)
        assertTrue(srt.isBlank())
    }
}

class RecipeExecutorTest {
    @Test
    fun podcastRecipeFoldsIntoDenoiseTrimEqAndLoudness() {
        val recipe = ConversionRecipe(
            id = "test",
            name = "test",
            steps = listOf(
                RecipeStep(RecipeStepType.Denoise, "Standard"),
                RecipeStep(RecipeStepType.TrimSilence, "Aggressive"),
                RecipeStep(RecipeStepType.Equalizer, "Podcast"),
                RecipeStep(RecipeStepType.Normalize, "Podcast")
            )
        )
        val compiled = RecipeExecutor.compile(recipe)
        assertTrue(compiled.effects.deHum)
        assertEquals(SilenceRemovalMode.Aggressive, compiled.effects.silenceRemoval)
        assertEquals(LoudnessTarget.Podcast, compiled.effects.loudnessTarget)
        assertTrue(compiled.effects.equalizer != EqualizerPreset.Off)
    }

    @Test
    fun convertStepChoosesTheOutputExtension() {
        val recipe = ConversionRecipe(
            id = "test",
            name = "test",
            steps = listOf(RecipeStep(RecipeStepType.Convert, "flac"))
        )
        assertEquals("flac", RecipeExecutor.compile(recipe).outputExtension)
    }

    @Test
    fun unknownExtensionFallsBackToMp3() {
        val recipe = ConversionRecipe(
            id = "test",
            name = "test",
            steps = listOf(RecipeStep(RecipeStepType.Convert, "not-a-format"))
        )
        assertEquals("mp3", RecipeExecutor.compile(recipe).outputExtension)
    }

    @Test
    fun trimStepAloneLeavesNoFiltersButStillHasAnExtension() {
        val recipe = ConversionRecipe(
            id = "test",
            name = "test",
            steps = listOf(RecipeStep(RecipeStepType.Trim, null))
        )
        val compiled = RecipeExecutor.compile(recipe)
        assertTrue(AudioEffectFilters.filtersFor(compiled.effects, null).isEmpty())
        assertEquals("mp3", compiled.outputExtension)
    }
}

class AudioStudioEngineTest {
    @Test
    fun centrePanSplitsPowerEvenlyAcrossBothSides() {
        val (left, right) = AudioStudioEngine.panGains(0f)
        assertEquals(0.7071f, left, 0.001f)
        assertEquals(0.7071f, right, 0.001f)
    }

    @Test
    fun hardLeftSilencesTheRightChannel() {
        val (left, right) = AudioStudioEngine.panGains(-1f)
        assertEquals(1f, left, 0.001f)
        assertEquals(0f, right, 0.001f)
    }

    @Test
    fun panIsClampedOutsideTheValidRange() {
        assertEquals(AudioStudioEngine.panGains(1f), AudioStudioEngine.panGains(4f))
        assertEquals(AudioStudioEngine.panGains(-1f), AudioStudioEngine.panGains(-9f))
    }
}

class VoiceAndSilenceFilterTest {
    @Test
    fun voicePresetShiftsPitchThenRestoresTempo() {
        val filters = AudioEffectFilters.filtersFor(
            AudioEffects(voiceChanger = VoiceChangerPreset.Child),
            null
        )
        assertTrue(filters.any { it.startsWith("asetrate=") })
        assertTrue(filters.any { it.startsWith("atempo=") })
    }

    @Test
    fun silenceRemovalAddsBothStartAndStopFilters() {
        val filters = AudioEffectFilters.filtersFor(
            AudioEffects(silenceRemoval = SilenceRemovalMode.Standard),
            null
        )
        assertEquals(2, filters.count { it.startsWith("silenceremove=") })
    }

    @Test
    fun reverbAndCompressorEachAddOneFilter() {
        val filters = AudioEffectFilters.filtersFor(
            AudioEffects(
                reverb = ReverbPreset.LargeHall,
                compressor = CompressorPreset.Podcast
            ),
            null
        )
        assertTrue(filters.any { it.startsWith("aecho=") })
        assertTrue(filters.any { it.startsWith("acompressor=") })
    }
}
