package org.zenconverter.app.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenconverter.app.audio.AudioEffects
import org.zenconverter.app.conversion.ConversionMediaCategory
import org.zenconverter.app.recipe.RecipeLibrary
import org.zenconverter.app.recipe.RecipeStepType
import org.zenconverter.app.audio.AudioEffectFilters

class BitrateCalculatorTest {
    @Test
    fun totalBitrateScalesWithTargetSize() {
        val tenMb = 10L * 1024 * 1024
        val bitrate = BitrateCalculator.totalBitrate(tenMb, 60.0)
        assertEquals(1_398_101, bitrate)
    }

    @Test
    fun videoBitrateLeavesRoomForAudio() {
        val bitrate = BitrateCalculator.videoBitrateFor(10L * 1024 * 1024, 60.0, audioBitrate = 128_000)
        assertTrue(bitrate != null && bitrate < 1_398_101)
    }

    @Test
    fun impossibleTargetReturnsNull() {
        assertNull(BitrateCalculator.videoBitrateFor(1_000, 3_600.0))
        assertNull(BitrateCalculator.totalBitrate(0, 60.0))
        assertNull(BitrateCalculator.totalBitrate(1_000, 0.0))
    }
}

class SmartPresetsTest {
    @Test
    fun whatsappPresetCapsResolution() {
        val options = SmartPresets.videoOptions(SmartPreset.WhatsApp, 30.0)
        assertEquals(720, options.maxShortSidePixels)
    }

    @Test
    fun podcastPresetUsesMonoSpeechBitrate() {
        val options = SmartPresets.audioOptions(SmartPreset.Podcast)
        assertTrue(options.audioBitrate != null && options.audioBitrate!! <= 128_000)
    }

    @Test
    fun onlyVideoPresetsReportAsVideo() {
        assertTrue(SmartPreset.WhatsApp.isVideo)
        assertTrue(!SmartPreset.Podcast.isVideo)
    }
}

class SmartSuggestionsTest {
    @Test
    fun fourKVideoSuggestsCompression() {
        val suggestions = SmartSuggestions.suggestionsFor(
            FileFacts(
                displayName = "clip.mp4",
                sizeBytes = 200L * 1024 * 1024,
                category = ConversionMediaCategory.Video,
                widthPx = 3840,
                heightPx = 2160
            )
        )
        assertTrue(suggestions.any { it.id == "compress-4k" })
    }

    @Test
    fun audioFileDoesNotSuggestVideoPresets() {
        val suggestions = SmartSuggestions.suggestionsFor(
            FileFacts("song.mp3", 5L * 1024 * 1024, ConversionMediaCategory.Audio)
        )
        assertTrue(suggestions.none { it.preset?.isVideo == true })
    }
}

class ConversionHistoryCodecTest {
    @Test
    fun roundTripsAnEntry() {
        val entry = ConversionHistoryEntry(
            id = "a",
            displayName = "clip.mp4",
            sourceUri = "content://source",
            outputUri = "content://output",
            targetFormat = "MP4",
            inputBytes = 100L,
            outputBytes = 40L,
            timestampMs = 1_700_000_000_000,
            success = true,
            ffmpegCommand = "ffmpeg -i in out"
        )
        val decoded = ConversionHistoryCodec.decode(ConversionHistoryCodec.encode(listOf(entry)))
        assertEquals(1, decoded.size)
        assertEquals(entry, decoded.first())
        assertEquals(-60L, decoded.first().sizeDeltaBytes)
    }

    @Test
    fun malformedInputDecodesToEmpty() {
        assertTrue(ConversionHistoryCodec.decode("{not json").isEmpty())
        assertTrue(ConversionHistoryCodec.decode(null).isEmpty())
    }

    @Test
    fun entryCountIsCapped() {
        val entries = (0 until 200).map { index ->
            ConversionHistoryEntry(
                id = "id-$index",
                displayName = "file-$index",
                sourceUri = "content://$index",
                outputUri = null,
                targetFormat = "MP3",
                inputBytes = null,
                outputBytes = null,
                timestampMs = index.toLong(),
                success = true
            )
        }
        val decoded = ConversionHistoryCodec.decode(ConversionHistoryCodec.encode(entries))
        assertEquals(ConversionHistoryCodec.MAX_ENTRIES, decoded.size)
    }
}

class PluginPresetCodecTest {
    @Test
    fun roundTripsASinglePreset() {
        val preset = PluginPreset("p1", "Warm tape", "Adds gentle saturation", mapOf("equalizer" to "bass"))
        val decoded = PluginPresetCodec.decodeImported(PluginPresetCodec.encode(listOf(preset)))
        assertEquals(1, decoded.size)
        assertEquals("Warm tape", decoded.first().name)
        assertEquals("bass", decoded.first().effects["equalizer"])
    }

    @Test
    fun invalidPresetJsonIsRejected() {
        assertTrue(PluginPresetCodec.decodeImported("{\"name\":\"no id\"}").isEmpty())
    }
}

class RecipeLibraryTest {
    @Test
    fun builtInRecipesAreNonEmpty() {
        assertTrue(RecipeLibrary.builtIn.isNotEmpty())
        assertTrue(RecipeLibrary.builtIn.all { it.steps.isNotEmpty() })
    }

    @Test
    fun importAcceptsASingleRecipeObject() {
        val recipe = RecipeLibrary.builtIn.first()
        val imported = RecipeLibrary.decodeImported(recipe.toJson().toString())
        assertEquals(1, imported.size)
        assertEquals(recipe.id, imported.first().id)
    }

    @Test
    fun unknownStepTypesAreSkipped() {
        val json = """{"id":"x","name":"X","steps":[{"type":"NotAStep"},{"type":"Denoise"}]}"""
        val imported = RecipeLibrary.decodeImported(json)
        assertEquals(1, imported.first().steps.size)
        assertEquals(RecipeStepType.Denoise, imported.first().steps.first().type)
    }

    @Test
    fun encodeAndDecodeRoundTripsCustomRecipes() {
        val custom = RecipeLibrary.builtIn.take(2).map { it.copy(isBuiltIn = false) }
        val decoded = RecipeLibrary.decode(RecipeLibrary.encode(custom))
        assertEquals(custom.map { it.id }, decoded.map { it.id })
    }
}

class PluginPresetsTest {
    @Test
    fun appliesKnownKeysOntoTheBaseEffects() {
        val preset = PluginPreset(
            id = "p",
            name = "Podcast",
            effects = mapOf(
                "equalizer" to "Podcast",
                "loudness" to "Podcast",
                "deHum" to "true"
            )
        )
        val result = PluginPresets.apply(preset, AudioEffects())
        assertEquals(org.zenconverter.app.audio.EqualizerPreset.Podcast, result.equalizer)
        assertEquals(org.zenconverter.app.audio.LoudnessTarget.Podcast, result.loudnessTarget)
        assertTrue(result.deHum)
    }

    @Test
    fun unknownKeysAndValuesLeaveTheBaseUntouched() {
        val preset = PluginPreset(
            id = "p",
            name = "Future",
            effects = mapOf("unknownKey" to "x", "equalizer" to "NotAPreset")
        )
        assertEquals(AudioEffects(), PluginPresets.apply(preset, AudioEffects()))
    }

    @Test
    fun toPresetRoundTripsThroughTheCodec() {
        val effects = AudioEffects(
            equalizer = org.zenconverter.app.audio.EqualizerPreset.Vocal,
            reverb = org.zenconverter.app.audio.ReverbPreset.SmallRoom
        )
        val preset = PluginPresets.toPreset("id", "Name", "Desc", effects)
        val decoded = PluginPresetCodec.decodeImported(PluginPresetCodec.encode(listOf(preset)))
        assertEquals(1, decoded.size)
        val restored = PluginPresets.apply(decoded.first(), AudioEffects())
        assertEquals(effects.equalizer, restored.equalizer)
        assertEquals(effects.reverb, restored.reverb)
    }
}

class AudioEffectFiltersTest {
    @Test
    fun disabledEffectsProduceNoFilters() {
        assertTrue(AudioEffectFilters.filtersFor(AudioEffects(), null).isEmpty())
    }

    @Test
    fun loudnessNormalizationIsAppliedLast() {
        val effects = AudioEffects(
            equalizer = org.zenconverter.app.audio.EqualizerPreset.BassBoost,
            loudnessTarget = org.zenconverter.app.audio.LoudnessTarget.Streaming
        )
        val filters = AudioEffectFilters.filtersFor(effects, null)
        assertTrue(filters.isNotEmpty())
        assertTrue(filters.last().startsWith("loudnorm"))
    }

    @Test
    fun filterNameIsExtracted() {
        assertEquals("equalizer", AudioEffectFilters.filterNameOf("equalizer=f=31:t=q:w=1.2:g=3"))
        assertNull(AudioEffectFilters.filterNameOf(""))
    }
}
