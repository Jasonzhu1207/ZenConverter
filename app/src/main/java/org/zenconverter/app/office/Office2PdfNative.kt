package org.zenconverter.app.office

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.Locale

object Office2PdfNative {
    private const val TAG = "Office2PdfNative"
    private const val LIBRARY_NAME = "zen_office2pdf"
    private const val REQUIRED_ABI = "arm64-v8a"
    private const val FONT_COPY_BUFFER_BYTES = 64 * 1024

    private data class BundledFont(
        val assetPath: String,
        val fileName: String,
        val byteCount: Long
    )

    private val bundledCjkFonts = listOf(
        BundledFont(
            assetPath = "fonts/NotoSansCJK-Regular.ttc",
            fileName = "NotoSansCJK-Regular.ttc",
            byteCount = 32_355_424L
        ),
        BundledFont(
            assetPath = "fonts/NotoSerifCJK-Regular.ttc",
            fileName = "NotoSerifCJK-Regular.ttc",
            byteCount = 26_273_008L
        )
    )

    @Volatile
    private var loadAttempted = false

    @Volatile
    private var loadFailure: Office2PdfUnavailableException? = null

    @Volatile
    private var nativeFontPathsAvailable: Boolean? = null

    val isAvailable: Boolean
        get() = REQUIRED_ABI in supportedAbis() && loadFailure == null

    fun convert(context: Context, input: ByteArray, extension: String): ByteArray {
        loadNativeLibrary()?.let { throw it }
        val fontDirectory = ensureBundledCjkFonts(context.applicationContext)
        val normalizedExtension = extension.lowercase(Locale.US)

        if (nativeFontPathsAvailable != false) {
            try {
                return convertBytesWithFontPaths(
                    input = input,
                    extension = normalizedExtension,
                    fontDirectories = arrayOf(fontDirectory.absolutePath)
                ).also {
                    nativeFontPathsAvailable = true
                }
            } catch (error: UnsatisfiedLinkError) {
                nativeFontPathsAvailable = false
                Log.w(
                    TAG,
                    "Bundled Office native library has no explicit font-path API; using legacy converter",
                    error
                )
            }
        }

        return convertBytes(input, normalizedExtension)
    }

    private external fun convertBytes(input: ByteArray, extension: String): ByteArray

    private external fun convertBytesWithFontPaths(
        input: ByteArray,
        extension: String,
        fontDirectories: Array<String>
    ): ByteArray

    private fun loadNativeLibrary(): Office2PdfUnavailableException? = synchronized(this) {
        if (loadAttempted) return@synchronized loadFailure

        val supportedAbis = supportedAbis()
        if (REQUIRED_ABI !in supportedAbis) {
            loadFailure = Office2PdfUnsupportedAbiException(supportedAbis)
            loadAttempted = true
            return@synchronized loadFailure
        }

        loadFailure = runCatching {
            System.loadLibrary(LIBRARY_NAME)
        }.exceptionOrNull()?.let { cause ->
            Office2PdfStartupException(cause)
        }
        loadAttempted = true
        loadFailure
    }

    private fun supportedAbis(): List<String> {
        return Build.SUPPORTED_ABIS?.toList().orEmpty()
    }

    private fun ensureBundledCjkFonts(context: Context): File {
        val fontDirectory = File(context.noBackupFilesDir, "office2pdf-fonts").apply {
            if (!isDirectory && !mkdirs()) {
                throw IOException("Could not create Office font directory")
            }
        }

        var copiedFont = false
        bundledCjkFonts.forEach { font ->
            val fontFile = File(fontDirectory, font.fileName)
            if (!fontFile.isFile || fontFile.length() != font.byteCount) {
                context.assets.open(font.assetPath).use { input ->
                    fontFile.outputStream().use { output ->
                        input.copyTo(output, FONT_COPY_BUFFER_BYTES)
                        output.flush()
                    }
                }
                copiedFont = true
            }

            if (!fontFile.isFile || fontFile.length() != font.byteCount) {
                throw IOException("Bundled Office CJK font was not prepared: ${font.fileName}")
            }
        }
        if (copiedFont) {
            Log.i(TAG, "Prepared bundled Office CJK fonts at ${fontDirectory.absolutePath}")
        }
        return fontDirectory
    }
}

open class Office2PdfUnavailableException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class Office2PdfUnsupportedAbiException(
    supportedAbis: List<String>
) : Office2PdfUnavailableException(
    "Office converter is only available with the bundled arm64-v8a native library; device ABIs: " +
        supportedAbis.ifEmpty { listOf("unknown") }.joinToString()
)

class Office2PdfStartupException(
    cause: Throwable
) : Office2PdfUnavailableException(
    "Office converter could not start on this device",
    cause
)
