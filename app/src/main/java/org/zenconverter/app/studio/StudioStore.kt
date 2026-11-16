package org.zenconverter.app.studio

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject
import org.zenconverter.app.audio.AudioEffects
import org.zenconverter.app.audio.CompressorPreset
import org.zenconverter.app.audio.EqualizerPreset
import org.zenconverter.app.audio.LoudnessTarget
import org.zenconverter.app.audio.ReverbPreset
import org.zenconverter.app.audio.SilenceRemovalMode
import org.zenconverter.app.audio.VoiceChangerPreset
import org.zenconverter.app.recipe.ConversionRecipe
import org.zenconverter.app.recipe.RecipeLibrary

/**
 * Holds the studio's persisted state: conversion history, user recipes and
 * community plugin presets. Everything is stored in a private
 * SharedPreferences file on the device; there is no account and nothing is
 * uploaded, which keeps the app's local-first promise intact.
 */
object StudioStore {
    private const val PREFERENCES_NAME = "zenconverter_studio"
    private const val KEY_HISTORY = "conversion_history"
    private const val KEY_CUSTOM_RECIPES = "custom_recipes"
    private const val KEY_PLUGIN_PRESETS = "plugin_presets"
    private const val KEY_ACCESSIBILITY_LARGE_TEXT = "accessibility_large_text"
    private const val KEY_ACCESSIBILITY_AUDIO_DESCRIPTIONS = "accessibility_audio_descriptions"
    private const val KEY_GUARD_ENABLED = "guard_enabled"
    private const val KEY_GUARD_BATTERY_THRESHOLD = "guard_battery_threshold"

    val history = mutableStateListOf<ConversionHistoryEntry>()
    val customRecipes = mutableStateListOf<ConversionRecipe>()
    val pluginPresets = mutableStateListOf<PluginPreset>()
    val largeText = mutableStateOf(false)
    val audioDescriptions = mutableStateOf(false)
    val guardEnabled = mutableStateOf(false)
    val guardBatteryThreshold = mutableStateOf(DEFAULT_BATTERY_THRESHOLD_PERCENT)

    const val DEFAULT_BATTERY_THRESHOLD_PERCENT = 15

    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        val preferences = preferences(context)
        history.clear()
        history.addAll(ConversionHistoryCodec.decode(preferences.getString(KEY_HISTORY, null)))
        customRecipes.clear()
        customRecipes.addAll(RecipeLibrary.decode(preferences.getString(KEY_CUSTOM_RECIPES, null)))
        pluginPresets.clear()
        pluginPresets.addAll(decodePluginPresets(preferences.getString(KEY_PLUGIN_PRESETS, null)))
        largeText.value = preferences.getBoolean(KEY_ACCESSIBILITY_LARGE_TEXT, false)
        audioDescriptions.value = preferences.getBoolean(KEY_ACCESSIBILITY_AUDIO_DESCRIPTIONS, false)
        guardEnabled.value = preferences.getBoolean(KEY_GUARD_ENABLED, false)
        guardBatteryThreshold.value = preferences.getInt(
            KEY_GUARD_BATTERY_THRESHOLD,
            DEFAULT_BATTERY_THRESHOLD_PERCENT
        )
        loaded = true
    }

    fun recordHistory(context: Context, entry: ConversionHistoryEntry) {
        ensureLoaded(context)
        history.add(0, entry)
        while (history.size > ConversionHistoryCodec.MAX_ENTRIES) {
            history.removeAt(history.size - 1)
        }
        persistHistory(context)
    }

    fun clearHistory(context: Context) {
        history.clear()
        persistHistory(context)
    }

    fun addRecipe(context: Context, recipe: ConversionRecipe) {
        ensureLoaded(context)
        customRecipes.removeAll { it.id == recipe.id }
        customRecipes.add(recipe)
        persistRecipes(context)
    }

    fun addRecipes(context: Context, recipes: List<ConversionRecipe>) {
        ensureLoaded(context)
        recipes.forEach { recipe ->
            customRecipes.removeAll { it.id == recipe.id }
            customRecipes.add(recipe.copy(isBuiltIn = false))
        }
        persistRecipes(context)
    }

    fun addPluginPresets(context: Context, presets: List<PluginPreset>) {
        ensureLoaded(context)
        presets.forEach { preset ->
            pluginPresets.removeAll { it.id == preset.id }
            pluginPresets.add(preset)
        }
        persistPluginPresets(context)
    }

    fun setLargeText(context: Context, enabled: Boolean) {
        ensureLoaded(context)
        largeText.value = enabled
        preferences(context).edit().putBoolean(KEY_ACCESSIBILITY_LARGE_TEXT, enabled).apply()
    }

    fun setAudioDescriptions(context: Context, enabled: Boolean) {
        ensureLoaded(context)
        audioDescriptions.value = enabled
        preferences(context).edit().putBoolean(KEY_ACCESSIBILITY_AUDIO_DESCRIPTIONS, enabled).apply()
    }

    fun setGuardEnabled(context: Context, enabled: Boolean) {
        ensureLoaded(context)
        guardEnabled.value = enabled
        preferences(context).edit().putBoolean(KEY_GUARD_ENABLED, enabled).apply()
    }

    fun setGuardBatteryThreshold(context: Context, percent: Int) {
        ensureLoaded(context)
        val clamped = percent.coerceIn(5, 50)
        guardBatteryThreshold.value = clamped
        preferences(context).edit().putInt(KEY_GUARD_BATTERY_THRESHOLD, clamped).apply()
    }

    private fun persistHistory(context: Context) {
        preferences(context).edit()
            .putString(KEY_HISTORY, ConversionHistoryCodec.encode(history.toList()))
            .apply()
    }

    private fun persistRecipes(context: Context) {
        preferences(context).edit()
            .putString(KEY_CUSTOM_RECIPES, RecipeLibrary.encode(customRecipes.toList()))
            .apply()
    }

    private fun persistPluginPresets(context: Context) {
        preferences(context).edit()
            .putString(KEY_PLUGIN_PRESETS, encodePluginPresets(pluginPresets.toList()))
            .apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun encodePluginPresets(presets: List<PluginPreset>): String =
        JSONArray().apply { presets.forEach { put(it.toJson()) } }.toString()

    private fun decodePluginPresets(encoded: String?): List<PluginPreset> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let { json ->
                        PluginPreset.fromJson(json)?.let { add(it) }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}

/**
 * A community-contributed preset shared as JSON, matching the recipe format so
 * both can travel through the same import/export path.
 */
data class PluginPreset(
    val id: String,
    val name: String,
    val description: String = "",
    val effects: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("kind", "zenconverter-preset")
        put("version", PRESET_SCHEMA_VERSION)
        put("effects", JSONObject(effects as Map<*, *>))
    }

    companion object {
        const val PRESET_SCHEMA_VERSION = 1

        fun fromJson(json: JSONObject): PluginPreset? {
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            val name = json.optString("name").takeIf { it.isNotBlank() } ?: return null
            val effectsJson = json.optJSONObject("effects") ?: JSONObject()
            val effects = buildMap {
                effectsJson.keys().forEach { key -> put(key, effectsJson.optString(key)) }
            }
            return PluginPreset(
                id = id,
                name = name,
                description = json.optString("description"),
                effects = effects
            )
        }
    }
}

object PluginPresetCodec {
    fun encode(presets: List<PluginPreset>): String =
        JSONArray().apply { presets.forEach { put(it.toJson()) } }.toString()

    /** Accepts a single preset object or an array, for easy clipboard import. */
    fun decodeImported(text: String): List<PluginPreset> {
        val trimmed = text.trim()
        return runCatching {
            when {
                trimmed.startsWith("[") -> {
                    val array = JSONArray(trimmed)
                    buildList {
                        for (index in 0 until array.length()) {
                            array.optJSONObject(index)?.let { json ->
                                PluginPreset.fromJson(json)?.let { add(it) }
                            }
                        }
                    }
                }
                else -> listOfNotNull(PluginPreset.fromJson(JSONObject(trimmed)))
            }
        }.getOrDefault(emptyList())
    }
}

/**
 * Bridges community [PluginPreset] JSON to the typed [AudioEffects] the converter
 * consumes. Unknown keys are ignored and unrecognised values leave the previous
 * setting in place, so a preset from a newer build degrades instead of failing.
 */
object PluginPresets {
    const val KEY_EQUALIZER = "equalizer"
    const val KEY_LOUDNESS = "loudness"
    const val KEY_COMPRESSOR = "compressor"
    const val KEY_REVERB = "reverb"
    const val KEY_VOICE_CHANGER = "voiceChanger"
    const val KEY_SILENCE_REMOVAL = "silenceRemoval"
    const val KEY_DE_HUM = "deHum"

    /** Merges [preset] onto [base], returning the resulting effects chain. */
    fun apply(preset: PluginPreset, base: AudioEffects): AudioEffects {
        var effects = base
        preset.effects.forEach { (key, value) ->
            effects = when (key) {
                KEY_EQUALIZER -> resolve(value, EqualizerPreset.entries) { it.labelKey }
                    ?.let { effects.copy(equalizer = it) } ?: effects
                KEY_LOUDNESS -> resolve(value, LoudnessTarget.entries) { it.labelKey }
                    ?.let { effects.copy(loudnessTarget = it) } ?: effects
                KEY_COMPRESSOR -> resolve(value, CompressorPreset.entries) { it.labelKey }
                    ?.let { effects.copy(compressor = it) } ?: effects
                KEY_REVERB -> resolve(value, ReverbPreset.entries) { it.labelKey }
                    ?.let { effects.copy(reverb = it) } ?: effects
                KEY_VOICE_CHANGER -> resolve(value, VoiceChangerPreset.entries) { it.labelKey }
                    ?.let { effects.copy(voiceChanger = it) } ?: effects
                KEY_SILENCE_REMOVAL -> resolve(value, SilenceRemovalMode.entries) { it.labelKey }
                    ?.let { effects.copy(silenceRemoval = it) } ?: effects
                KEY_DE_HUM -> effects.copy(deHum = value.equals("true", ignoreCase = true))
                else -> effects
            }
        }
        return effects
    }

    /** Serialises an effects chain into the shareable preset representation. */
    fun toPreset(id: String, name: String, description: String, effects: AudioEffects): PluginPreset =
        PluginPreset(
            id = id,
            name = name,
            description = description,
            effects = mapOf(
                KEY_EQUALIZER to effects.equalizer.name,
                KEY_LOUDNESS to effects.loudnessTarget.name,
                KEY_COMPRESSOR to effects.compressor.name,
                KEY_REVERB to effects.reverb.name,
                KEY_VOICE_CHANGER to effects.voiceChanger.name,
                KEY_SILENCE_REMOVAL to effects.silenceRemoval.name,
                KEY_DE_HUM to effects.deHum.toString()
            )
        )

    private fun <T> resolve(value: String, entries: List<T>, label: (T) -> String): T? =
        entries.firstOrNull { label(it).equals(value, ignoreCase = true) }
            ?: entries.firstOrNull { it.toString().equals(value, ignoreCase = true) }
}
