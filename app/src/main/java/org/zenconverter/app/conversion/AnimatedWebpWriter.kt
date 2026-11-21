package org.zenconverter.app.conversion

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * ByteArrayOutputStream that exposes its backing buffer directly so callers can
 * inspect encoded frame chunks without an extra array copy.
 */
internal class ExposedByteArrayOutputStream(
    initialCapacity: Int = 65_536
) : ByteArrayOutputStream(initialCapacity) {
    fun rawBuffer(): ByteArray = buf

    fun byteCount(): Int = count
}

/** Parsed header of a single PAM (P7) image frame in an `image2pipe` stream. */
internal data class PamFrameHeader(
    val width: Int,
    val height: Int,
    val depth: Int,
    val maxVal: Int
)

/** Reads concatenated PAM (P7) RGBA frames from a sequential byte stream. */
internal object PamFrameStreamReader {
    private const val MAX_HEADER_LINE_LENGTH = 256
    private const val MAX_HEADER_LINES = 32

    /**
     * Reads the next PAM `P7` header from [input], returning `null` on clean EOF
     * before any frame header bytes are encountered.
     */
    fun readNextHeader(input: BufferedInputStream): PamFrameHeader? {
        var firstLine: String?
        while (true) {
            firstLine = readAsciiLine(input)
            if (firstLine == null) {
                return null
            }
            if (firstLine.isNotBlank()) {
                break
            }
        }
        if (firstLine.trim() != "P7") {
            throw IOException("Expected PAM P7 header, got: $firstLine")
        }

        var width = -1
        var height = -1
        var depth = -1
        var maxVal = -1
        var sawEndHdr = false

        for (lineIndex in 0 until MAX_HEADER_LINES) {
            val rawLine = readAsciiLine(input)
                ?: throw EOFException("Unexpected EOF while reading PAM header")
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#')) {
                continue
            }
            if (line == "ENDHDR") {
                sawEndHdr = true
                break
            }
            val spaceIdx = line.indexOfAny(charArrayOf(' ', '\t'))
            if (spaceIdx <= 0) {
                throw IOException("Malformed PAM header line: $line")
            }
            val key = line.substring(0, spaceIdx)
            val value = line.substring(spaceIdx + 1).trim()
            when (key) {
                "WIDTH" -> width = value.toIntOrNull()
                    ?: throw IOException("Invalid PAM WIDTH: $value")
                "HEIGHT" -> height = value.toIntOrNull()
                    ?: throw IOException("Invalid PAM HEIGHT: $value")
                "DEPTH" -> depth = value.toIntOrNull()
                    ?: throw IOException("Invalid PAM DEPTH: $value")
                "MAXVAL" -> maxVal = value.toIntOrNull()
                    ?: throw IOException("Invalid PAM MAXVAL: $value")
                "TUPLTYPE" -> Unit
                else -> Unit
            }
        }

        if (!sawEndHdr) {
            throw IOException("PAM header missing ENDHDR")
        }
        if (width !in 1..AnimatedWebpWriter.MAX_DIMENSION ||
            height !in 1..AnimatedWebpWriter.MAX_DIMENSION
        ) {
            throw IOException("Unsupported PAM frame dimensions: ${width}x$height")
        }
        if (depth != 4 || maxVal != 255) {
            throw IOException("Unsupported PAM pixel format depth=$depth maxVal=$maxVal")
        }
        return PamFrameHeader(
            width = width,
            height = height,
            depth = depth,
            maxVal = maxVal
        )
    }

    /** Reads exactly [length] bytes into [buffer], returning `false` on premature EOF. */
    fun readExactBytes(input: InputStream, buffer: ByteArray, length: Int): Boolean {
        var offset = 0
        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read < 0) {
                return false
            }
            offset += read
        }
        return true
    }

    private fun readAsciiLine(input: InputStream): String? {
        val sb = StringBuilder(32)
        while (true) {
            val b = input.read()
            if (b < 0) {
                return if (sb.isEmpty()) null else sb.toString()
            }
            if (b == '\n'.code) {
                if (sb.isNotEmpty() && sb[sb.length - 1] == '\r') {
                    sb.setLength(sb.length - 1)
                }
                return sb.toString()
            }
            if (sb.length >= MAX_HEADER_LINE_LENGTH) {
                throw IOException("PAM header line exceeds $MAX_HEADER_LINE_LENGTH bytes")
            }
            sb.append(b.toChar())
        }
    }
}

/**
 * Streaming RFC 9649 animated WebP muxer that combines single-frame WebP payloads
 * into an animated RIFF/WEBP container using bounded memory.
 */
internal class AnimatedWebpWriter(
    private val outputFile: File,
    private val loopCount: Int = 0,
    private val backgroundColorBgra: Int = 0
) : Closeable {
    private val output = BufferedOutputStream(FileOutputStream(outputFile), 65_536)
    private var headerWritten = false
    private var canvasWidth = 0
    private var canvasHeight = 0
    private var frameCount = 0
    private var bytesWrittenAfterRiffHeader = 0L
    private var seenAlpha = false
    private var finished = false
    private var closed = false

    private data class ChunkSlice(
        val offset: Int,
        val payloadSize: Int
    ) {
        val totalSizeWithPadding: Long
            get() = 8L + payloadSize.toLong() + (payloadSize and 1).toLong()
    }

    /**
     * Extracts image bitstream chunks from a single-frame RIFF/WEBP payload and appends
     * them as a full-canvas `ANMF` frame with [durationMs] millisecond display time.
     */
    fun appendSingleFrameWebp(
        webpBytes: ByteArray,
        length: Int,
        width: Int,
        height: Int,
        durationMs: Int
    ) {
        check(!closed && !finished) { "AnimatedWebpWriter is already closed" }
        require(length in 12..webpBytes.size) { "Invalid single-frame WebP buffer length=$length" }
        require(width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION) {
            "WebP dimensions out of bounds: ${width}x$height"
        }
        val pixels = width.toLong() * height.toLong()
        require(pixels in 1L..MAX_FRAME_PIXELS) {
            "WebP frame pixel count exceeds limit: $pixels"
        }

        if (!headerWritten) {
            writeContainerHeader(width, height)
            canvasWidth = width
            canvasHeight = height
            headerWritten = true
        } else {
            require(width == canvasWidth && height == canvasHeight) {
                "Frame dimensions ${width}x$height do not match canvas ${canvasWidth}x$canvasHeight"
            }
        }

        val slices = extractFrameSubChunks(webpBytes, length)
        var totalSubChunkBytes = 0L
        var frameHasAlpha = false
        for (slice in slices) {
            totalSubChunkBytes += slice.totalSizeWithPadding
            if (matchesFourCc(webpBytes, slice.offset, FOURCC_ALPH)) {
                frameHasAlpha = true
            }
        }
        val anmfPayloadSize = 16L + totalSubChunkBytes
        if (anmfPayloadSize > MAX_RIFF_PAYLOAD_BYTES) {
            throw IOException("Animated WebP frame exceeds RIFF chunk limit")
        }
        if (bytesWrittenAfterRiffHeader + 8L + anmfPayloadSize > MAX_RIFF_PAYLOAD_BYTES) {
            throw IOException("Animated WebP file exceeds 4 GiB RIFF size limit")
        }

        if (frameHasAlpha) {
            seenAlpha = true
        }

        writeFourCc(output, FOURCC_ANMF)
        writeUint32LE(output, anmfPayloadSize)
        writeUint24LE(output, 0) // Frame X / 2
        writeUint24LE(output, 0) // Frame Y / 2
        writeUint24LE(output, width - 1)
        writeUint24LE(output, height - 1)
        writeUint24LE(output, durationMs.coerceIn(1, 0xFF_FFFF))
        output.write(ANMF_FLAG_NO_BLEND_NO_DISPOSE)

        for (slice in slices) {
            output.write(webpBytes, slice.offset, 8 + slice.payloadSize)
            if ((slice.payloadSize and 1) != 0) {
                output.write(0)
            }
        }

        bytesWrittenAfterRiffHeader += 8L + anmfPayloadSize
        frameCount += 1
    }

    /**
     * Flushes the stream, patches the RIFF payload size and `VP8X` feature flags,
     * and returns the total number of written frames.
     */
    fun finish(): Int {
        check(!finished) { "AnimatedWebpWriter already finished" }
        closeOutputStream()
        if (!headerWritten || frameCount <= 0) {
            throw IOException("No frames written to animated WebP")
        }
        RandomAccessFile(outputFile, "rw").use { raf ->
            raf.seek(4L)
            val sizeBytes = ByteArray(4)
            encodeUint32LE(bytesWrittenAfterRiffHeader, sizeBytes)
            raf.write(sizeBytes)
            if (seenAlpha) {
                raf.seek(20L)
                raf.write(VP8X_FLAG_ANIMATION or VP8X_FLAG_ALPHA)
            }
        }
        finished = true
        return frameCount
    }

    override fun close() {
        closeOutputStream()
    }

    private fun closeOutputStream() {
        if (!closed) {
            closed = true
            output.flush()
            output.close()
        }
    }

    private fun writeContainerHeader(width: Int, height: Int) {
        // 12-byte RIFF/WEBP header
        writeFourCc(output, FOURCC_RIFF)
        writeUint32LE(output, 0L)
        writeFourCc(output, FOURCC_WEBP)

        // 18-byte VP8X extended header chunk
        writeFourCc(output, FOURCC_VP8X)
        writeUint32LE(output, 10L)
        output.write(VP8X_FLAG_ANIMATION)
        output.write(0)
        output.write(0)
        output.write(0)
        writeUint24LE(output, width - 1)
        writeUint24LE(output, height - 1)

        // 14-byte ANIM chunk
        writeFourCc(output, FOURCC_ANIM)
        writeUint32LE(output, 6L)
        writeUint32LE(output, backgroundColorBgra.toLong() and 0xFFFF_FFFFL)
        writeUint16LE(output, loopCount and 0xFFFF)

        // Bytes after the 8-byte RIFF chunk header: "WEBP" (4) + VP8X (18) + ANIM (14)
        bytesWrittenAfterRiffHeader = 4L + 18L + 14L
    }

    private fun extractFrameSubChunks(webpBytes: ByteArray, length: Int): List<ChunkSlice> {
        if (!matchesFourCc(webpBytes, 0, FOURCC_RIFF) ||
            !matchesFourCc(webpBytes, 8, FOURCC_WEBP)
        ) {
            throw IOException("Encoded frame is not a valid RIFF/WEBP container")
        }

        val slices = ArrayList<ChunkSlice>(2)
        var hasImagePayload = false
        var offset = 12
        while (offset + 8 <= length) {
            val chunkSize = readUint32LE(webpBytes, offset + 4)
            if (chunkSize > (length - offset - 8).toLong()) {
                throw IOException("Truncated chunk in single-frame WebP at offset=$offset")
            }
            val payloadInt = chunkSize.toInt()
            if (matchesFourCc(webpBytes, offset, FOURCC_ALPH)) {
                slices.add(ChunkSlice(offset, payloadInt))
            } else if (matchesFourCc(webpBytes, offset, FOURCC_VP8) ||
                matchesFourCc(webpBytes, offset, FOURCC_VP8L)
            ) {
                slices.add(ChunkSlice(offset, payloadInt))
                hasImagePayload = true
            }
            val nextOffset = offset.toLong() + 8L + chunkSize + (chunkSize and 1L)
            if (nextOffset > length.toLong()) {
                break
            }
            offset = nextOffset.toInt()
        }
        if (!hasImagePayload) {
            throw IOException("Single-frame WebP contains no VP8/VP8L bitstream chunk")
        }
        return slices
    }

    private fun matchesFourCc(bytes: ByteArray, offset: Int, fourCc: ByteArray): Boolean {
        return offset + 4 <= bytes.size &&
            bytes[offset] == fourCc[0] &&
            bytes[offset + 1] == fourCc[1] &&
            bytes[offset + 2] == fourCc[2] &&
            bytes[offset + 3] == fourCc[3]
    }

    private fun readUint32LE(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24)
    }

    private fun writeFourCc(out: OutputStream, fourCc: ByteArray) {
        out.write(fourCc, 0, 4)
    }

    private fun writeUint16LE(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun writeUint24LE(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
    }

    private fun writeUint32LE(out: OutputStream, value: Long) {
        out.write((value and 0xFFL).toInt())
        out.write(((value ushr 8) and 0xFFL).toInt())
        out.write(((value ushr 16) and 0xFFL).toInt())
        out.write(((value ushr 24) and 0xFFL).toInt())
    }

    private fun encodeUint32LE(value: Long, dest: ByteArray) {
        dest[0] = (value and 0xFFL).toByte()
        dest[1] = ((value ushr 8) and 0xFFL).toByte()
        dest[2] = ((value ushr 16) and 0xFFL).toByte()
        dest[3] = ((value ushr 24) and 0xFFL).toByte()
    }

    companion object {
        const val MAX_DIMENSION = 16_383
        const val MAX_FRAME_PIXELS = 16_000_000L
        private const val MAX_RIFF_PAYLOAD_BYTES = 0xFFFF_FFFEL
        private const val VP8X_FLAG_ANIMATION = 0x02
        private const val VP8X_FLAG_ALPHA = 0x10
        private const val ANMF_FLAG_NO_BLEND_NO_DISPOSE = 0x02

        private val FOURCC_RIFF = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())
        private val FOURCC_WEBP = byteArrayOf('W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte())
        private val FOURCC_VP8X = byteArrayOf('V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), 'X'.code.toByte())
        private val FOURCC_ANIM = byteArrayOf('A'.code.toByte(), 'N'.code.toByte(), 'I'.code.toByte(), 'M'.code.toByte())
        private val FOURCC_ANMF = byteArrayOf('A'.code.toByte(), 'N'.code.toByte(), 'M'.code.toByte(), 'F'.code.toByte())
        private val FOURCC_ALPH = byteArrayOf('A'.code.toByte(), 'L'.code.toByte(), 'P'.code.toByte(), 'H'.code.toByte())
        private val FOURCC_VP8 = byteArrayOf('V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), ' '.code.toByte())
        private val FOURCC_VP8L = byteArrayOf('V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), 'L'.code.toByte())
    }
}
