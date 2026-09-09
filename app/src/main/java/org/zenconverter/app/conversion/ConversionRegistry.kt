package org.zenconverter.app.conversion
import org.zenconverter.app.R
import org.zenconverter.app.i18n.localizedText
import org.zenconverter.app.i18n.LocalizedFailure


class ConversionRegistry(
    private val engines: List<ConversionEngine>
) {
    fun engineFor(request: ConversionRequest): ConversionEngine {
        return engines.firstOrNull { it.canHandle(request) }
            ?: throw LocalizedFailure(localizedText(R.string.message_no_conversion_engine_registered_for_1_s, request.targetFormat))
    }
}
