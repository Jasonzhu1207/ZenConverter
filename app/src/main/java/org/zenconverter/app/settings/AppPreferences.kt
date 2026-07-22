package org.zenconverter.app.settings

import android.content.Context
import android.net.Uri

data class SavedOutputDirectory(
    val uri: Uri,
    val label: String
)

/** Stores small user choices only; files remain in their user-selected locations. */
object AppPreferences {
    private const val PREFERENCES_NAME = "zenconverter_preferences"
    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_USE_CUSTOM_OUTPUT = "use_custom_output"
    private const val KEY_OUTPUT_DIRECTORY_URI = "output_directory_uri"
    private const val KEY_OUTPUT_DIRECTORY_LABEL = "output_directory_label"

    fun accentColor(context: Context): String? =
        preferences(context).getString(KEY_ACCENT_COLOR, null)

    fun setAccentColor(context: Context, accentColor: String) {
        preferences(context)
            .edit()
            .putString(KEY_ACCENT_COLOR, accentColor)
            .apply()
    }

    fun language(context: Context): String? =
        preferences(context).getString(KEY_LANGUAGE, null)

    fun setLanguage(context: Context, language: String) {
        preferences(context)
            .edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }

    fun usesCustomOutput(context: Context): Boolean =
        preferences(context).getBoolean(KEY_USE_CUSTOM_OUTPUT, false)

    fun setUsesCustomOutput(context: Context, usesCustomOutput: Boolean) {
        preferences(context)
            .edit()
            .putBoolean(KEY_USE_CUSTOM_OUTPUT, usesCustomOutput)
            .apply()
    }

    fun savedOutputDirectory(context: Context): SavedOutputDirectory? {
        val preferences = preferences(context)
        val uriValue = preferences.getString(KEY_OUTPUT_DIRECTORY_URI, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val label = preferences.getString(KEY_OUTPUT_DIRECTORY_LABEL, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return runCatching {
            SavedOutputDirectory(uri = Uri.parse(uriValue), label = label)
        }.getOrNull()
    }

    fun saveOutputDirectory(context: Context, directory: SavedOutputDirectory) {
        preferences(context)
            .edit()
            .putString(KEY_OUTPUT_DIRECTORY_URI, directory.uri.toString())
            .putString(KEY_OUTPUT_DIRECTORY_LABEL, directory.label)
            .apply()
    }

    fun clearOutputDirectory(context: Context) {
        preferences(context)
            .edit()
            .remove(KEY_OUTPUT_DIRECTORY_URI)
            .remove(KEY_OUTPUT_DIRECTORY_LABEL)
            .apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
