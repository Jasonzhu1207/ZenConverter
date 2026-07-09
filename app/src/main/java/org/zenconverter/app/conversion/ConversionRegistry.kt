package org.zenconverter.app.conversion

class ConversionRegistry(
    private val engines: List<ConversionEngine>
) {
    fun engineFor(request: ConversionRequest): ConversionEngine {
        return engines.firstOrNull { it.canHandle(request) }
            ?: error("No conversion engine registered for ${request.targetFormat}")
    }
}
