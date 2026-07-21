package org.zenconverter.app.conversion

import android.net.Uri

data class ConversionRequest(
    val id: String,
    val inputUri: Uri,
    val targetFormat: String,
    val outputDisplayName: String,
    val mode: ConversionMode
)

enum class ConversionMode {
    Noop,
    FastCopy,
    Compatibility,
    SafeCache
}
