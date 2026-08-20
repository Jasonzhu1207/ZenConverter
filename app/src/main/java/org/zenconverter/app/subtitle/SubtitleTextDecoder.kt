package org.zenconverter.app.subtitle

import java.nio.charset.Charset

/**
 * Decodes subtitle/lyrics bytes to text. UTF-8 (with optional BOM) is tried
 * first; if the result contains the replacement character (invalid UTF-8), the
 * decoder falls back to GB18030, which covers the GBK-encoded Chinese lyrics
 * common in the wild. This is best-effort and does not promise detection for
 * every legacy encoding.
 */
object SubtitleTextDecoder {

    private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private const val REPLACEMENT_CHAR = '\uFFFD'

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) error("Subtitle file is empty")

        val offset = if (bytes.startsWithUtf8Bom()) UTF8_BOM.size else 0
        val length = bytes.size - offset
        if (length <= 0) error("Subtitle file is empty")

        val utf8 = String(bytes, offset, length, Charsets.UTF_8)
        if (!utf8.contains(REPLACEMENT_CHAR)) return utf8

        return runCatching {
            String(bytes, offset, length, Charset.forName(GB18030_CHARSET))
        }.getOrElse { utf8 }
    }

    private fun ByteArray.startsWithUtf8Bom(): Boolean {
        if (size < UTF8_BOM.size) return false
        return UTF8_BOM.indices.all { index -> this[index] == UTF8_BOM[index] }
    }

    private const val GB18030_CHARSET = "GB18030"
}
