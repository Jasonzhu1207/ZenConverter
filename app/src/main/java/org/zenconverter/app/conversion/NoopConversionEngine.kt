package org.zenconverter.app.conversion
import org.zenconverter.app.R
import org.zenconverter.app.i18n.localizedText


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
                message = localizedText(R.string.message_no_op_conversion_completed)
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
