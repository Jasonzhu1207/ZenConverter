package org.zenconverter.app.font

enum class FontFormat {
    Sfnt,
    Woff,
    Woff2
}

object FontFormatDetector {
    private const val WOFF_SIGNATURE = 0x774F4646L // 'wOFF'
    private const val WOFF2_SIGNATURE = 0x774F4632L // 'wOF2'

    fun detect(bytes: ByteArray): FontFormat? {
        if (bytes.size < 4) return null
        return when (readUInt32BE(bytes, 0)) {
            WOFF2_SIGNATURE -> FontFormat.Woff2
            WOFF_SIGNATURE -> FontFormat.Woff
            else -> if (isSfntSignature(bytes)) FontFormat.Sfnt else null
        }
    }

    private fun isSfntSignature(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val signature = readUInt32BE(bytes, 0)
        return signature in SFNT_SIGNATURES
    }

    private val SFNT_SIGNATURES = setOf(
        0x00010000L, // TrueType
        0x4F54544FL, // 'OTTO' OpenType CFF
        0x74727565L, // 'true' Apple TrueType
        0x74797031L  // 'typ1' Apple PostScript
    )
}

object FontFlavor {
    private const val OTTO_SIGNATURE = 0x4F54544FL // 'OTTO'
    private const val TYP1_SIGNATURE = 0x74797031L // 'typ1'

    /**
     * Returns "otf" when the SFNT uses a CFF/PostScript outline flavor, or
     * "ttf" for TrueType outlines (and unknown fallbacks).
     */
    fun extensionForSfnt(bytes: ByteArray): String {
        if (bytes.size < 4) return "ttf"
        return when (readUInt32BE(bytes, 0)) {
            OTTO_SIGNATURE, TYP1_SIGNATURE -> "otf"
            else -> "ttf"
        }
    }
}
