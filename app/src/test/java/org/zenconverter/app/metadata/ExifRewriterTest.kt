package org.zenconverter.app.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The selective scrub depends on [ExifRewriter] dropping exactly the requested
 * tags and leaving the rest of the block intact, so these tests build a real
 * little-endian Exif APP1 payload and check the tags that survive.
 */
class ExifRewriterTest {

    @Test
    fun dropsDeviceTagsAndKeepsOrientation() {
        val payload = buildExif()
        val rewritten = ExifRewriter.rewrite(payload) { tag -> tag in ExifRewriter.DEVICE_TAGS }

        assertNotNull(rewritten)
        val tags = rootTags(rewritten!!)
        assertFalse(tags.contains(ExifRewriter.TAG_MAKE))
        assertFalse(tags.contains(ExifRewriter.TAG_MODEL))
        assertTrue(tags.contains(ExifRewriter.TAG_ORIENTATION))
    }

    @Test
    fun dropsGpsPointerWhenGpsIsSelected() {
        val payload = buildExif()
        val rewritten = ExifRewriter.rewrite(payload) { tag -> tag == ExifRewriter.TAG_GPS_IFD }

        assertNotNull(rewritten)
        assertFalse(rootTags(rewritten!!).contains(ExifRewriter.TAG_GPS_IFD))
    }

    @Test
    fun keepsEverythingWhenNothingMatches() {
        val payload = buildExif()
        val rewritten = ExifRewriter.rewrite(payload) { false }

        assertNotNull(rewritten)
        assertEquals(rootTags(payload).sorted(), rootTags(rewritten!!).sorted())
    }

    @Test
    fun refusesNonExifPayloads() {
        val notExif = "JFIF\u0000".toByteArray(Charsets.US_ASCII)
        assertNull(ExifRewriter.rewrite(notExif) { true })
    }

    @Test
    fun orientationValueSurvivesTheRewrite() {
        val payload = buildExif(orientation = 6)
        val rewritten = ExifRewriter.rewrite(payload) { tag -> tag in ExifRewriter.DEVICE_TAGS }

        assertNotNull(rewritten)
        assertEquals(6, inlineShortValue(rewritten!!, ExifRewriter.TAG_ORIENTATION))
    }

    private fun rootTags(payload: ByteArray): List<Int> {
        val tiff = payload.copyOfRange(6, payload.size)
        val ifd0 = tiff.intAt(4)
        val count = tiff.shortAt(ifd0)
        return (0 until count).map { index -> tiff.shortAt(ifd0 + 2 + index * 12) }
    }

    private fun inlineShortValue(payload: ByteArray, tag: Int): Int {
        val tiff = payload.copyOfRange(6, payload.size)
        val ifd0 = tiff.intAt(4)
        val count = tiff.shortAt(ifd0)
        for (index in 0 until count) {
            val base = ifd0 + 2 + index * 12
            if (tiff.shortAt(base) == tag) return tiff.shortAt(base + 8)
        }
        return -1
    }

    private fun ByteArray.shortAt(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.intAt(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)

    /** Builds a minimal Exif APP1 payload: Make, Model, Orientation and a GPS IFD. */
    private fun buildExif(orientation: Int = 1): ByteArray {
        val make = "Canon\u0000".toByteArray(Charsets.US_ASCII)
        val model = "EOS\u0000".toByteArray(Charsets.US_ASCII)

        val ifd0Count = 4
        val ifd0Size = 2 + ifd0Count * 12 + 4
        val ifd0Offset = 8
        val makeOffset = ifd0Offset + ifd0Size
        val modelOffset = makeOffset + make.size
        val gpsOffset = modelOffset + model.size
        val gpsSize = 2 + 1 * 12 + 4
        val total = gpsOffset + gpsSize

        val tiff = ByteArray(total)
        tiff[0] = 'I'.code.toByte()
        tiff[1] = 'I'.code.toByte()
        tiff.putShort(2, 42)
        tiff.putInt(4, ifd0Offset)

        tiff.putShort(ifd0Offset, ifd0Count)
        writeEntry(tiff, ifd0Offset + 2, ExifRewriter.TAG_MAKE, 2, make.size, makeOffset)
        writeEntry(tiff, ifd0Offset + 14, ExifRewriter.TAG_MODEL, 2, model.size, modelOffset)
        writeEntry(tiff, ifd0Offset + 26, ExifRewriter.TAG_ORIENTATION, 3, 1, orientation)
        writeEntry(tiff, ifd0Offset + 38, ExifRewriter.TAG_GPS_IFD, 4, 1, gpsOffset)
        tiff.putInt(ifd0Offset + 50, 0)

        make.copyInto(tiff, makeOffset)
        model.copyInto(tiff, modelOffset)

        tiff.putShort(gpsOffset, 1)
        writeEntry(tiff, gpsOffset + 2, 0x0001, 2, 2, 0x0000004E)
        tiff.putInt(gpsOffset + 14, 0)

        return "Exif\u0000\u0000".toByteArray(Charsets.US_ASCII) + tiff
    }

    private fun writeEntry(
        target: ByteArray,
        base: Int,
        tag: Int,
        type: Int,
        count: Int,
        value: Int
    ) {
        target.putShort(base, tag)
        target.putShort(base + 2, type)
        target.putInt(base + 4, count)
        target.putInt(base + 8, value)
    }

    private fun ByteArray.putShort(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun ByteArray.putInt(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value shr 8) and 0xFF).toByte()
        this[offset + 2] = ((value shr 16) and 0xFF).toByte()
        this[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
