package org.zenconverter.app.conversion

import android.net.Uri

interface ConversionEngine {
    val id: String

    fun canHandle(request: ConversionRequest): Boolean

    suspend fun run(
        request: ConversionRequest,
        emit: suspend (ConversionEvent) -> Unit
    )
}

sealed interface ConversionEvent {
    data class Progress(
        val requestId: String,
        val fraction: Float,
        val message: String
    ) : ConversionEvent

    data class Completed(
        val requestId: String,
        val outputUri: Uri?
    ) : ConversionEvent

    data class Failed(
        val requestId: String,
        val message: String,
        val cause: Throwable? = null
    ) : ConversionEvent
}
