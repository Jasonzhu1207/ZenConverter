package org.zenconverter.app.metadata

/**
 * Rewrites an Exif APP1 payload so only the tags the caller asks for are gone,
 * leaving every other byte of the block intact. Android's
 * [android.media.ExifInterface] can read and write known tags but cannot delete
 * an arbitrary one, so the TIFF structure is parsed and re-serialised here.
 *
 * Safety is the priority: whenever the block uses a type, offset or nesting the
 * reader does not fully understand, [rewrite] returns null and the caller
 * removes the whole segment instead. A partial scrub therefore degrades to a
 * full one rather than producing a broken image.
 */
internal object ExifRewriter {

    private const val PREFIX = "Exif\u0000\u0000"
    private const val ENTRY_SIZE = 12
    private const val MAX_IFD_DEPTH = 4

    const val TAG_IMAGE_DESCRIPTION = 0x010E
    const val TAG_MAKE = 0x010F
    const val TAG_MODEL = 0x0110
    const val TAG_ORIENTATION = 0x0112
    const val TAG_SOFTWARE = 0x0131
    const val TAG_DATETIME = 0x0132
    const val TAG_ARTIST = 0x013B
    const val TAG_COPYRIGHT = 0x8298
    const val TAG_EXIF_IFD = 0x8769
    const val TAG_GPS_IFD = 0x8825
    const val TAG_DATETIME_ORIGINAL = 0x9003
    const val TAG_DATETIME_DIGITIZED = 0x9004
    const val TAG_OFFSET_TIME = 0x9010
    const val TAG_OFFSET_TIME_ORIGINAL = 0x9011
    const val TAG_OFFSET_TIME_DIGITIZED = 0x9012
    const val TAG_USER_COMMENT = 0x9286
    const val TAG_SUB_SEC_TIME = 0x9290
    const val TAG_SUB_SEC_TIME_ORIGINAL = 0x9291
    const val TAG_SUB_SEC_TIME_DIGITIZED = 0x9292
    const val TAG_XP_TITLE = 0x9C9B
    const val TAG_XP_COMMENT = 0x9C9C
    const val TAG_XP_AUTHOR = 0x9C9D
    const val TAG_XP_KEYWORDS = 0x9C9E
    const val TAG_XP_SUBJECT = 0x9C9F
    const val TAG_INTEROP_IFD = 0xA005
    const val TAG_CAMERA_OWNER_NAME = 0xA430
    const val TAG_BODY_SERIAL_NUMBER = 0xA431
    const val TAG_LENS_MAKE = 0xA433
    const val TAG_LENS_MODEL = 0xA434
    const val TAG_LENS_SERIAL_NUMBER = 0xA435

    /** Camera, lens and processing device. */
    val DEVICE_TAGS = setOf(
        TAG_MAKE, TAG_MODEL, TAG_SOFTWARE,
        TAG_CAMERA_OWNER_NAME, TAG_BODY_SERIAL_NUMBER,
        TAG_LENS_MAKE, TAG_LENS_MODEL, TAG_LENS_SERIAL_NUMBER
    )

    /** A person's name, credit or free-text note. */
    val AUTHOR_TAGS = setOf(
        TAG_ARTIST, TAG_COPYRIGHT, TAG_IMAGE_DESCRIPTION, TAG_USER_COMMENT,
        TAG_XP_TITLE, TAG_XP_COMMENT, TAG_XP_AUTHOR, TAG_XP_KEYWORDS, TAG_XP_SUBJECT
    )

    /** When the picture was taken or edited. */
    val TIME_TAGS = setOf(
        TAG_DATETIME, TAG_DATETIME_ORIGINAL, TAG_DATETIME_DIGITIZED,
        TAG_OFFSET_TIME, TAG_OFFSET_TIME_ORIGINAL, TAG_OFFSET_TIME_DIGITIZED,
        TAG_SUB_SEC_TIME, TAG_SUB_SEC_TIME_ORIGINAL, TAG_SUB_SEC_TIME_DIGITIZED
    )

    private val POINTER_TAGS = setOf(TAG_EXIF_IFD, TAG_GPS_IFD, TAG_INTEROP_IFD)

    /** True when the payload is an Exif APP1 block. */
    fun looksLikeExif(payload: ByteArray): Boolean =
        payload.size >= PREFIX.length && payload.startsWithAscii(PREFIX)

    /**
     * Returns a new APP1 payload with every tag [shouldDrop] accepts removed, or
     * null when the block cannot be rewritten safely.
     */
    fun rewrite(payload: ByteArray, shouldDrop: (Int) -> Boolean): ByteArray? {
        if (!looksLikeExif(payload)) return null
        val tiff = payload.copyOfRange(PREFIX.length, payload.size)
        return runCatching {
            val reader = TiffReader(tiff)
            val root = reader.readIfd(reader.ifd0Offset, 0) ?: return null
            val pruned = root.prune(shouldDrop)
            if (pruned.isEmpty) return null
            val body = TiffWriter(reader.littleEndian).write(pruned) ?: return null
            PREFIX.toByteArray(Charsets.US_ASCII) + body
        }.getOrNull()
    }

    private fun ByteArray.startsWithAscii(prefix: String): Boolean {
        if (size < prefix.length) return false
        return prefix.indices.all { this[it] == prefix[it].code.toByte() }
    }

    private class TiffReader(private val tiff: ByteArray) {
        val littleEndian: Boolean
        val ifd0Offset: Int

        init {
            require(tiff.size >= 8) { "short TIFF header" }
            littleEndian = when (tiff.short(0, littleEndian = true)) {
                0x4949 -> true
                0x4D4D -> false
                else -> throw IllegalArgumentException("unknown byte order")
            }
            require(tiff.short(2, littleEndian) == 42) { "bad TIFF magic" }
            ifd0Offset = tiff.int(4, littleEndian)
            require(ifd0Offset >= 8 && ifd0Offset < tiff.size) { "bad IFD0 offset" }
        }

        fun readIfd(offset: Int, depth: Int): IfdNode? {
            if (depth > MAX_IFD_DEPTH) return null
            if (offset < 8 || offset + 2 > tiff.size) return null
            val count = tiff.short(offset, littleEndian)
            val entriesEnd = offset + 2 + count * ENTRY_SIZE
            if (count < 0 || entriesEnd + 4 > tiff.size) return null

            val entries = ArrayList<Entry>(count)
            for (index in 0 until count) {
                val base = offset + 2 + index * ENTRY_SIZE
                val tag = tiff.short(base, littleEndian)
                val type = tiff.short(base + 2, littleEndian)
                val valueCount = tiff.int(base + 4, littleEndian)
                val unit = typeSize(type) ?: return null
                if (valueCount < 0) return null
                val byteCount = unit.toLong() * valueCount.toLong()
                if (byteCount > tiff.size) return null
                val data = if (byteCount <= 4) {
                    tiff.copyOfRange(base + 8, base + 8 + byteCount.toInt())
                } else {
                    val dataOffset = tiff.int(base + 8, littleEndian)
                    if (dataOffset < 0 || dataOffset.toLong() + byteCount > tiff.size) return null
                    tiff.copyOfRange(dataOffset, dataOffset + byteCount.toInt())
                }
                entries += Entry(tag, type, valueCount, data)
            }

            val nextOffset = tiff.int(entriesEnd, littleEndian)
            return IfdNode(
                entries = entries,
                exifIfd = entries.find { it.tag == TAG_EXIF_IFD }
                    ?.let { readIfd(it.pointerTarget(littleEndian), depth + 1) },
                gpsIfd = entries.find { it.tag == TAG_GPS_IFD }
                    ?.let { readIfd(it.pointerTarget(littleEndian), depth + 1) },
                interopIfd = entries.find { it.tag == TAG_INTEROP_IFD }
                    ?.let { readIfd(it.pointerTarget(littleEndian), depth + 1) },
                nextIfd = if (nextOffset in 1 until tiff.size) {
                    readIfd(nextOffset, depth + 1)
                } else {
                    null
                }
            )
        }

        private fun ByteArray.short(offset: Int, littleEndian: Boolean): Int {
            val first = this[offset].toInt() and 0xFF
            val second = this[offset + 1].toInt() and 0xFF
            return if (littleEndian) (second shl 8) or first else (first shl 8) or second
        }

        private fun ByteArray.int(offset: Int, littleEndian: Boolean): Int {
            val b0 = this[offset].toInt() and 0xFF
            val b1 = this[offset + 1].toInt() and 0xFF
            val b2 = this[offset + 2].toInt() and 0xFF
            val b3 = this[offset + 3].toInt() and 0xFF
            return if (littleEndian) {
                (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
            } else {
                (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            }
        }
    }

    private class TiffWriter(private val littleEndian: Boolean) {
        private val order = ArrayList<IfdNode>()
        private val offsets = HashMap<IfdNode, Int>()
        private val dataOffsets = HashMap<Entry, Int>()

        fun write(root: IfdNode): ByteArray? {
            collect(root, 0)
            var cursor = 8
            order.forEach { node ->
                offsets[node] = cursor
                cursor += 2 + node.entries.size * ENTRY_SIZE + 4
            }
            order.forEach { node ->
                node.entries.forEach { entry ->
                    if (entry.data.size > 4) {
                        dataOffsets[entry] = cursor
                        cursor += entry.data.size
                        if (cursor % 2 != 0) cursor++
                    }
                }
            }
            if (cursor > MAX_TIFF_BYTES) return null

            val output = ByteArray(cursor)
            output.putShort(0, if (littleEndian) 0x4949 else 0x4D4D)
            output.putShort(2, 42)
            output.putInt(4, offsets[root] ?: return null)

            order.forEach { node ->
                val start = offsets[node] ?: return null
                output.putShort(start, node.entries.size)
                node.entries.forEachIndexed { index, entry ->
                    val base = start + 2 + index * ENTRY_SIZE
                    output.putShort(base, entry.tag)
                    output.putShort(base + 2, entry.type)
                    output.putInt(base + 4, entry.valueCount)
                    val pointerTarget = if (entry.tag in POINTER_TAGS) node.childFor(entry.tag) else null
                    when {
                        pointerTarget != null -> output.putInt(base + 8, offsets[pointerTarget] ?: return null)
                        entry.data.size > 4 -> {
                            val location = dataOffsets[entry] ?: return null
                            output.putInt(base + 8, location)
                            entry.data.copyInto(output, destinationOffset = location)
                        }
                        else -> entry.data.copyInto(output, destinationOffset = base + 8)
                    }
                }
                val nextStart = start + 2 + node.entries.size * ENTRY_SIZE
                output.putInt(nextStart, node.nextIfd?.let { offsets[it] } ?: 0)
            }
            return output
        }

        private fun collect(node: IfdNode, depth: Int) {
            if (depth > MAX_IFD_DEPTH || offsets.containsKey(node) || order.contains(node)) return
            order += node
            node.exifIfd?.let { collect(it, depth + 1) }
            node.gpsIfd?.let { collect(it, depth + 1) }
            node.interopIfd?.let { collect(it, depth + 1) }
            node.nextIfd?.let { collect(it, depth + 1) }
        }

        private fun ByteArray.putShort(offset: Int, value: Int) {
            if (littleEndian) {
                this[offset] = (value and 0xFF).toByte()
                this[offset + 1] = ((value shr 8) and 0xFF).toByte()
            } else {
                this[offset] = ((value shr 8) and 0xFF).toByte()
                this[offset + 1] = (value and 0xFF).toByte()
            }
        }

        private fun ByteArray.putInt(offset: Int, value: Int) {
            if (littleEndian) {
                this[offset] = (value and 0xFF).toByte()
                this[offset + 1] = ((value shr 8) and 0xFF).toByte()
                this[offset + 2] = ((value shr 16) and 0xFF).toByte()
                this[offset + 3] = ((value shr 24) and 0xFF).toByte()
            } else {
                this[offset] = ((value shr 24) and 0xFF).toByte()
                this[offset + 1] = ((value shr 16) and 0xFF).toByte()
                this[offset + 2] = ((value shr 8) and 0xFF).toByte()
                this[offset + 3] = (value and 0xFF).toByte()
            }
        }
    }

    private data class Entry(
        val tag: Int,
        val type: Int,
        val valueCount: Int,
        val data: ByteArray
    ) {
        /** The IFD offset this pointer entry holds, or -1 when it is malformed. */
        fun pointerTarget(littleEndian: Boolean): Int {
            if (data.size < 4) return -1
            val b0 = data[0].toInt() and 0xFF
            val b1 = data[1].toInt() and 0xFF
            val b2 = data[2].toInt() and 0xFF
            val b3 = data[3].toInt() and 0xFF
            return if (littleEndian) {
                (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
            } else {
                (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            }
        }
    }

    private data class IfdNode(
        val entries: List<Entry>,
        val exifIfd: IfdNode? = null,
        val gpsIfd: IfdNode? = null,
        val interopIfd: IfdNode? = null,
        val nextIfd: IfdNode? = null
    ) {
        val isEmpty: Boolean
            get() = entries.isEmpty() && exifIfd == null && gpsIfd == null && nextIfd == null

        fun childFor(tag: Int): IfdNode? = when (tag) {
            TAG_EXIF_IFD -> exifIfd
            TAG_GPS_IFD -> gpsIfd
            TAG_INTEROP_IFD -> interopIfd
            else -> null
        }

        /** Drops matching tags here and in everything this IFD points at. */
        fun prune(shouldDrop: (Int) -> Boolean): IfdNode {
            val kept = entries.filter { entry ->
                val child = childFor(entry.tag)
                when {
                    entry.tag in POINTER_TAGS -> child != null && !shouldDrop(entry.tag)
                    else -> !shouldDrop(entry.tag)
                }
            }
            val prunedExif = exifIfd?.prune(shouldDrop)?.takeIf { !it.isEmpty }
            val prunedGps = gpsIfd?.prune(shouldDrop)?.takeIf { !it.isEmpty }
            return IfdNode(
                entries = kept,
                exifIfd = prunedExif,
                gpsIfd = prunedGps,
                interopIfd = prunedExif?.interopIfd?.prune(shouldDrop)?.takeIf { !it.isEmpty },
                nextIfd = nextIfd?.prune(shouldDrop)?.takeIf { !it.isEmpty }
            )
        }
    }

    private const val TYPE_BYTE = 1
    private const val TYPE_ASCII = 2
    private const val TYPE_SHORT = 3
    private const val TYPE_LONG = 4
    private const val TYPE_RATIONAL = 5
    private const val TYPE_UNDEFINED = 7
    private const val TYPE_SLONG = 9
    private const val TYPE_SRATIONAL = 10
    private const val MAX_TIFF_BYTES = 8 * 1024 * 1024

    private fun typeSize(type: Int): Int? = when (type) {
        TYPE_BYTE, TYPE_ASCII, TYPE_UNDEFINED -> 1
        TYPE_SHORT -> 2
        TYPE_LONG, TYPE_SLONG -> 4
        TYPE_RATIONAL, TYPE_SRATIONAL -> 8
        else -> null
    }

}
