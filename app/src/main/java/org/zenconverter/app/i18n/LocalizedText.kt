package org.zenconverter.app.i18n

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import org.zenconverter.app.R

sealed interface LocalizedText {
    data class Resource(@param:StringRes val id: Int, val arguments: List<Any> = emptyList()) : LocalizedText

    data class Quantity(
        @param:PluralsRes val id: Int,
        val count: Int,
        val arguments: List<Any> = emptyList()
    ) : LocalizedText

    data class Diagnostic(val description: LocalizedText, val detail: String?) : LocalizedText

    data class ExternalDetail(val value: String) : LocalizedText

    fun resolve(context: Context): String {
        val localized = AppLanguages.localizedContext(context)
        fun resolveArguments(values: List<Any>): Array<Any> = values.map { value ->
            if (value is LocalizedText) value.resolve(localized) else value
        }.toTypedArray()
        return when (this) {
            is Resource -> if (arguments.isEmpty()) localized.getString(id)
                else localized.getString(id, *resolveArguments(arguments))
            is Quantity -> if (arguments.isEmpty()) localized.resources.getQuantityString(id, count)
                else localized.resources.getQuantityString(id, count, *resolveArguments(arguments))
            is Diagnostic -> if (detail.isNullOrBlank()) description.resolve(localized) else
                localized.getString(R.string.format_label_value, description.resolve(localized), detail)
            is ExternalDetail -> value
        }
    }
}

fun localizedText(@StringRes id: Int, vararg arguments: Any): LocalizedText =
    LocalizedText.Resource(id, arguments.toList())

fun Throwable.localizedFailure(@StringRes fallback: Int): LocalizedText =
    if (this is LocalizedFailure) description else LocalizedText.Diagnostic(localizedText(fallback), message)

open class LocalizedFailure(val description: LocalizedText, cause: Throwable? = null) :
    IllegalStateException("Localized operation failure", cause)

inline fun requireLocalized(condition: Boolean, description: () -> LocalizedText) {
    if (!condition) throw LocalizedFailure(description())
}

inline fun checkLocalized(condition: Boolean, description: () -> LocalizedText) {
    if (!condition) throw LocalizedFailure(description())
}
