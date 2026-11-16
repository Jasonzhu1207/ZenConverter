package org.zenconverter.app.recipe

import org.json.JSONArray
import org.json.JSONObject

/**
 * A reusable, shareable chain of conversion steps. Recipes serialize to JSON so
 * the community can trade presets, which fits ZenConverter's open-source
 * spirit. Pure Kotlin plus org.json so it works on the JVM and on device.
 */
enum class RecipeStepType(val labelKey: String) {
    ExtractAudio("Extract audio"),
    TrimSilence("Trim silence"),
    Denoise("Reduce noise"),
    Normalize("Normalize loudness"),
    Equalizer("Equalizer preset"),
    VoiceChanger("Voice changer"),
    Convert("Convert format"),
    Trim("Trim range")
}

data class RecipeStep(
    val type: RecipeStepType,
    val parameter: String? = null
)

data class ConversionRecipe(
    val id: String,
    val name: String,
    val description: String = "",
    val steps: List<RecipeStep> = emptyList(),
    val isBuiltIn: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("version", RECIPE_SCHEMA_VERSION)
        put(
            "steps",
            JSONArray().apply {
                steps.forEach { step ->
                    put(
                        JSONObject().apply {
                            put("type", step.type.name)
                            step.parameter?.let { put("parameter", it) }
                        }
                    )
                }
            }
        )
    }

    companion object {
        const val RECIPE_SCHEMA_VERSION = 1

        fun fromJson(json: JSONObject): ConversionRecipe? {
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            val name = json.optString("name").takeIf { it.isNotBlank() } ?: return null
            val stepsArray = json.optJSONArray("steps") ?: JSONArray()
            val steps = buildList {
                for (index in 0 until stepsArray.length()) {
                    val stepJson = stepsArray.optJSONObject(index) ?: continue
                    val type = stepJson.optString("type")
                        .let { value -> RecipeStepType.entries.firstOrNull { it.name == value } }
                        ?: continue
                    add(RecipeStep(type, stepJson.optString("parameter").takeIf { it.isNotBlank() }))
                }
            }
            return ConversionRecipe(
                id = id,
                name = name,
                description = json.optString("description"),
                steps = steps
            )
        }
    }
}

object RecipeLibrary {
    val builtIn: List<ConversionRecipe> = listOf(
        ConversionRecipe(
            id = "podcast-cleanup",
            name = "Podcast cleanup in one tap",
            description = "Denoise, trim silence, level loudness and apply a voice EQ.",
            isBuiltIn = true,
            steps = listOf(
                RecipeStep(RecipeStepType.Denoise, "Standard"),
                RecipeStep(RecipeStepType.TrimSilence, "Standard"),
                RecipeStep(RecipeStepType.Equalizer, "Podcast"),
                RecipeStep(RecipeStepType.Normalize, "Podcast")
            )
        ),
        ConversionRecipe(
            id = "extract-normalized-mp3",
            name = "Extract and normalize to MP3",
            description = "Pull the audio track, normalize it and export an MP3.",
            isBuiltIn = true,
            steps = listOf(
                RecipeStep(RecipeStepType.ExtractAudio),
                RecipeStep(RecipeStepType.TrimSilence, "Light"),
                RecipeStep(RecipeStepType.Normalize, "Streaming"),
                RecipeStep(RecipeStepType.Convert, "MP3")
            )
        ),
        ConversionRecipe(
            id = "whatsapp-share",
            name = "Share-ready audio",
            description = "Trim silence, level the loudness and export a compact M4A.",
            isBuiltIn = true,
            steps = listOf(
                RecipeStep(RecipeStepType.TrimSilence, "Light"),
                RecipeStep(RecipeStepType.Normalize, "Streaming"),
                RecipeStep(RecipeStepType.Convert, "M4A")
            )
        ),
        ConversionRecipe(
            id = "voice-memo",
            name = "Voice memo cleanup",
            description = "Reduce hum and noise, then level the result for listening.",
            isBuiltIn = true,
            steps = listOf(
                RecipeStep(RecipeStepType.Denoise, "Light"),
                RecipeStep(RecipeStepType.Normalize, "Podcast")
            )
        )
    )

    fun all(custom: List<ConversionRecipe>): List<ConversionRecipe> = builtIn + custom

    fun encode(custom: List<ConversionRecipe>): String =
        JSONArray().apply { custom.forEach { put(it.toJson()) } }.toString()

    fun decode(encoded: String?): List<ConversionRecipe> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let { json ->
                        ConversionRecipe.fromJson(json)?.let { add(it) }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Accepts either a single recipe object or an array, for easy import. */
    fun decodeImported(text: String): List<ConversionRecipe> {
        val trimmed = text.trim()
        return runCatching {
            when {
                trimmed.startsWith("[") -> {
                    val array = JSONArray(trimmed)
                    buildList {
                        for (index in 0 until array.length()) {
                            array.optJSONObject(index)?.let { json ->
                                ConversionRecipe.fromJson(json)?.let { add(it) }
                            }
                        }
                    }
                }
                else -> listOfNotNull(ConversionRecipe.fromJson(JSONObject(trimmed)))
            }
        }.getOrDefault(emptyList())
    }
}
