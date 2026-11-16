package org.zenconverter.app.studio

import org.json.JSONArray
import org.json.JSONObject

/**
 * One completed conversion, kept so a user can re-run it with tweaks or restore
 * the original. Serialized to JSON for local persistence; nothing leaves the
 * device.
 */
data class ConversionHistoryEntry(
    val id: String,
    val displayName: String,
    val sourceUri: String,
    val outputUri: String?,
    val targetFormat: String,
    val inputBytes: Long?,
    val outputBytes: Long?,
    val timestampMs: Long,
    val success: Boolean,
    val ffmpegCommand: String? = null
) {
    val sizeDeltaBytes: Long?
        get() = if (inputBytes != null && outputBytes != null) outputBytes - inputBytes else null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("sourceUri", sourceUri)
        outputUri?.let { put("outputUri", it) }
        put("targetFormat", targetFormat)
        inputBytes?.let { put("inputBytes", it) }
        outputBytes?.let { put("outputBytes", it) }
        put("timestampMs", timestampMs)
        put("success", success)
        ffmpegCommand?.let { put("ffmpegCommand", it) }
    }

    companion object {
        fun fromJson(json: JSONObject): ConversionHistoryEntry? {
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            val sourceUri = json.optString("sourceUri").takeIf { it.isNotBlank() } ?: return null
            return ConversionHistoryEntry(
                id = id,
                displayName = json.optString("displayName"),
                sourceUri = sourceUri,
                outputUri = json.optString("outputUri").takeIf { it.isNotBlank() },
                targetFormat = json.optString("targetFormat"),
                inputBytes = json.optLong("inputBytes").takeIf { json.has("inputBytes") && it > 0 },
                outputBytes = json.optLong("outputBytes").takeIf { json.has("outputBytes") && it > 0 },
                timestampMs = json.optLong("timestampMs"),
                success = json.optBoolean("success", true),
                ffmpegCommand = json.optString("ffmpegCommand").takeIf { it.isNotBlank() }
            )
        }
    }
}

object ConversionHistoryCodec {
    const val MAX_ENTRIES = 100

    fun encode(entries: List<ConversionHistoryEntry>): String =
        JSONArray().apply {
            entries.take(MAX_ENTRIES).forEach { put(it.toJson()) }
        }.toString()

    fun decode(encoded: String?): List<ConversionHistoryEntry> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let { json ->
                        ConversionHistoryEntry.fromJson(json)?.let { add(it) }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
