package org.zenconverter.app.font

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Pure-Kotlin WOFF 1.0 (wOFF) encoder/decoder. WOFF 1.0 stores each SFNT table
 * zlib-compressed; it has no Brotli dependency, so it runs without native code.
 *
 * Metadata and private blocks are not preserved in this milestone: encoding
 * writes empty meta/priv records and decoding skips them. Table checksums are
 * passed through from the source SFNT directory, so a round-trip keeps the
 * original checksum values.
 */
object WoffCodec {
    private const val WOFF_SIGNATURE = 0x774F4646L // 'wOFF'
    private const val WOFF_HEADER_BYTES = 44
    private const val WOFF_DIRECTORY_ENTRY_BYTES = 20

    fun encode(sfnt: ByteArray): ByteArray {
        if (sfnt.size < 12) error("Font input is too small")
        val numTables = readUInt16BE(sfnt, 4)
        val directoryBytes = 12 + numTables * 16
        if (sfnt.size < directoryBytes) error("Font directory is truncated")

        data class SourceTable(
            val tag: Long,
            val checksum: Long,
            val offset: Int,
            val length: Int
        )

        val sourceTables = (0 until numTables).map { index ->
            val base = 12 + index * 16
            val offset = readUInt32BE(sfnt, base + 8).toInt()
            val length = readUInt32BE(sfnt, base + 12).toInt()
            if (offset < 0 || length < 0 || offset.toLong() + length > sfnt.size) {
                error("Font table data is truncated")
            }
            SourceTable(
                tag = readUInt32BE(sfnt, base),
                checksum = readUInt32BE(sfnt, base + 4),
                offset = offset,
                length = length
            )
        }

        data class CompressedTable(
            val tag: Long,
            val checksum: Long,
            val origLength: Int,
            val data: ByteArray
        )

        val compressedTables = sourceTables.map { table ->
            CompressedTable(
                tag = table.tag,
                checksum = table.checksum,
                origLength = table.length,
                data = zlibCompress(sfnt.copyOfRange(table.offset, table.offset + table.length))
            )
        }

        val tableDataStart = WOFF_HEADER_BYTES + numTables * WOFF_DIRECTORY_ENTRY_BYTES
        var cursor = tableDataStart
        val offsets = compressedTables.map { table ->
            val offset = cursor
            cursor += table.data.size
            cursor += (4 - (cursor % 4)) % 4
            offset
        }
        val totalLength = cursor

        val output = ByteArrayOutputStream(totalLength)
        output.writeUInt32BE(WOFF_SIGNATURE)
        output.writeUInt32BE(readUInt32BE(sfnt, 0)) // flavor = sfnt version
        output.writeUInt32BE(totalLength.toLong())
        output.writeUInt16BE(numTables)
        output.writeUInt16BE(0) // reserved
        output.writeUInt32BE(sfnt.size.toLong()) // totalSfntSize
        output.writeUInt16BE(1) // majorVersion
        output.writeUInt16BE(0) // minorVersion
        output.writeUInt32BE(0) // metaOffset
        output.writeUInt32BE(0) // metaLength
        output.writeUInt32BE(0) // metaOrigLength
        output.writeUInt32BE(0) // privOffset
        output.writeUInt32BE(0) // privLength

        compressedTables.forEachIndexed { index, table ->
            output.writeUInt32BE(table.tag)
            output.writeUInt32BE(offsets[index].toLong())
            output.writeUInt32BE(table.data.size.toLong())
            output.writeUInt32BE(table.origLength.toLong())
            output.writeUInt32BE(table.checksum)
        }

        compressedTables.forEach { table ->
            output.write(table.data)
            var written = table.data.size
            while (written % 4 != 0) {
                output.write(0)
                written++
            }
        }

        return output.toByteArray()
    }

    fun decode(woff: ByteArray): ByteArray {
        if (woff.size < WOFF_HEADER_BYTES) error("WOFF input is too small")
        if (readUInt32BE(woff, 0) != WOFF_SIGNATURE) error("Input is not a WOFF font")

        val flavor = readUInt32BE(woff, 4)
        val numTables = readUInt16BE(woff, 12)
        val directoryBytes = WOFF_HEADER_BYTES + numTables * WOFF_DIRECTORY_ENTRY_BYTES
        if (woff.size < directoryBytes) error("WOFF directory is truncated")

        data class WoffTable(
            val tag: Long,
            val offset: Int,
            val compLength: Int,
            val origLength: Int,
            val checksum: Long
        )

        val tables = (0 until numTables).map { index ->
            val base = WOFF_HEADER_BYTES + index * WOFF_DIRECTORY_ENTRY_BYTES
            val offset = readUInt32BE(woff, base + 4).toInt()
            val compLength = readUInt32BE(woff, base + 8).toInt()
            val origLength = readUInt32BE(woff, base + 12).toInt()
            if (
                offset < 0 || compLength < 0 || origLength < 0 ||
                offset.toLong() + compLength > woff.size
            ) {
                error("WOFF table data is truncated")
            }
            WoffTable(
                tag = readUInt32BE(woff, base),
                offset = offset,
                compLength = compLength,
                origLength = origLength,
                checksum = readUInt32BE(woff, base + 16)
            )
        }

        val decompressed = tables.map { table ->
            zlibDecompress(
                woff.copyOfRange(table.offset, table.offset + table.compLength),
                table.origLength
            )
        }

        val searchRangeBase = largestPowerOfTwo(numTables)
        val searchRange = searchRangeBase * 16
        val entrySelector = log2(searchRangeBase)
        val rangeShift = numTables * 16 - searchRange

        var totalSize = 12 + numTables * 16
        tables.forEach { table ->
            totalSize += table.origLength
            totalSize += (4 - (totalSize % 4)) % 4
        }

        val output = ByteArrayOutputStream(totalSize)
        output.writeUInt32BE(flavor)
        output.writeUInt16BE(numTables)
        output.writeUInt16BE(searchRange)
        output.writeUInt16BE(entrySelector)
        output.writeUInt16BE(rangeShift)

        var dataOffset = 12 + numTables * 16
        tables.forEachIndexed { index, table ->
            output.writeUInt32BE(table.tag)
            output.writeUInt32BE(table.checksum)
            output.writeUInt32BE(dataOffset.toLong())
            output.writeUInt32BE(table.origLength.toLong())
            dataOffset += table.origLength
            dataOffset += (4 - (dataOffset % 4)) % 4
        }

        decompressed.forEach { data ->
            output.write(data)
            var written = data.size
            while (written % 4 != 0) {
                output.write(0)
                written++
            }
        }

        return output.toByteArray()
    }

    private fun zlibCompress(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, false)
        val output = ByteArrayOutputStream(input.size / 2 + 64)
        val buffer = ByteArray(8192)
        try {
            deflater.setInput(input)
            deflater.finish()
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                output.write(buffer, 0, count)
            }
        } finally {
            deflater.end()
        }
        return output.toByteArray()
    }

    private fun zlibDecompress(input: ByteArray, expectedLength: Int): ByteArray {
        val inflater = Inflater(false)
        return try {
            inflater.setInput(input)
            val output = ByteArrayOutputStream(expectedLength.coerceAtLeast(64))
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } catch (exception: DataFormatException) {
            error("WOFF table is corrupt")
        } finally {
            inflater.end()
        }
    }

    private fun largestPowerOfTwo(value: Int): Int {
        var power = 1
        while (power * 2 <= value) power *= 2
        return power
    }

    private fun log2(value: Int): Int {
        var remaining = value
        var result = 0
        while (remaining > 1) {
            remaining = remaining ushr 1
            result++
        }
        return result
    }
}
