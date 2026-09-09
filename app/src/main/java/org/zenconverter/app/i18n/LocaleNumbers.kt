package org.zenconverter.app.i18n

import java.text.DecimalFormatSymbols
import java.util.Locale

fun String.toLocalizedDoubleOrNull(locale: Locale = Locale.getDefault()): Double? {
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val normalized = trim().map { character ->
        when {
            character == separator -> '.'
            character.isDigit() -> Character.digit(character, 10).digitToChar()
            else -> character
        }
    }.joinToString("")
    if (!Regex("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)").matches(normalized)) return null
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}

fun String.toLocalizedFloatOrNull(locale: Locale = Locale.getDefault()): Float? =
    toLocalizedDoubleOrNull(locale)?.toFloat()?.takeIf { it.isFinite() }
