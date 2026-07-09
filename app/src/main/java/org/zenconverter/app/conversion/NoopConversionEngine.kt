package org.zenconverter.app.conversion

class NoopConversionEngine : ConversionEngine {
    override val id: String = "noop"

    override fun canHandle(request: ConversionRequest): Boolean {
        return request.mode == ConversionMode.Noop
    }

    override suspend fun run(
        request: ConversionRequest,
        emit: suspend (ConversionEvent) -> Unit
    ) {
        emit(
            ConversionEvent.Progress(
                requestId = request.id,
                fraction = 1f,
                message = "No-op conversion completed."
            )
        )
        emit(
            ConversionEvent.Completed(
                requestId = request.id,
                outputUri = null
            )
        )
    }
}
