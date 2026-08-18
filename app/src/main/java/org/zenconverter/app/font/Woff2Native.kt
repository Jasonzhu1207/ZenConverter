package org.zenconverter.app.font

import android.os.Build

object Woff2Native {
    private const val LIBRARY_NAME = "zen_woff2"
    private const val REQUIRED_ABI = "arm64-v8a"

    @Volatile
    private var loadAttempted = false

    @Volatile
    private var loadFailure: Woff2UnavailableException? = null

    val isAvailable: Boolean
        get() = REQUIRED_ABI in supportedAbis() && loadFailure == null

    /**
     * Compresses an SFNT font (TTF or OTF) into WOFF2.
     */
    fun compressSfnt(input: ByteArray): ByteArray {
        ensureLoaded()
        return compress(input)
    }

    /**
     * Decompresses a WOFF2 file back to its SFNT representation. The returned
     * bytes keep the original outline flavor (glyf-based TTF or CFF-based OTF).
     */
    fun decompressToSfnt(input: ByteArray): ByteArray {
        ensureLoaded()
        return decompress(input)
    }

    private fun ensureLoaded() {
        loadNativeLibrary()?.let { throw it }
    }

    // JNI symbol names in libzen_woff2.so reference these exact methods.
    private external fun compress(input: ByteArray): ByteArray

    private external fun decompress(input: ByteArray): ByteArray

    private fun loadNativeLibrary(): Woff2UnavailableException? = synchronized(this) {
        if (loadAttempted) return@synchronized loadFailure

        val supportedAbis = supportedAbis()
        if (REQUIRED_ABI !in supportedAbis) {
            loadFailure = Woff2UnsupportedAbiException(supportedAbis)
            loadAttempted = true
            return@synchronized loadFailure
        }

        loadFailure = runCatching {
            System.loadLibrary(LIBRARY_NAME)
        }.exceptionOrNull()?.let { cause ->
            Woff2StartupException(cause)
        }
        loadAttempted = true
        loadFailure
    }

    private fun supportedAbis(): List<String> {
        return Build.SUPPORTED_ABIS?.toList().orEmpty()
    }
}

open class Woff2UnavailableException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class Woff2UnsupportedAbiException(
    supportedAbis: List<String>
) : Woff2UnavailableException(
    "Font converter is only available with the bundled arm64-v8a native library; device ABIs: " +
        supportedAbis.ifEmpty { listOf("unknown") }.joinToString()
)

class Woff2StartupException(
    cause: Throwable
) : Woff2UnavailableException(
    "Font converter could not start on this device",
    cause
)
