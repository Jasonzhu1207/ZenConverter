package org.zenconverter.app.conversion

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OggVorbisMetadataTest {

    @Test
    fun readsTitleAndArtistFromVorbisComments() {
        val file = oggFile(comments = listOf("TITLE=Hello", "ARTIST=World"))
        val snapshot = file.inputStream().use { AudioMetadataCodec.readSource(it) }
        assertTrue(snapshot != null)
        assertTrue(snapshot!!.fields.containsValue("Hello"))
        assertTrue(snapshot.fields.containsValue("World"))
    }

    @Test
    fun verifyAcceptsMatchingMetadata() {
        val file = oggFile(comments = listOf("TITLE=Hello"))
        val snapshot = AudioMetadataCodec.readSource(file.inputStream())!!
        assertTrue(AudioMetadataCodec.verifyOggVorbis(file, snapshot).success)
    }

    @Test
    fun verifyRejectsMissingRequiredMetadata() {
        val file = oggFile(comments = emptyList())
        val expected = AudioMetadataCodec.readSource(oggFile(listOf("TITLE=Hello")).inputStream())!!
        assertFalse(AudioMetadataCodec.verifyOggVorbis(file, expected).success)
    }

    @Test
    fun fingerprintIgnoresHeaderPackets() {
        val file = oggFile(comments = listOf("TITLE=Hello"))
        val fingerprint = AudioMetadataCodec.audioFingerprint(file, "ogg")
        assertTrue(fingerprint.isNotEmpty())
    }

    private fun oggFile(comments: List<String>): File {
        val identification = ByteArrayOutputStream().apply {
            write(1)
            write("vorbis".toByteArray(Charsets.US_ASCII))
            write(intLe(0))          // vorbis_version
            write(2)                 // channels
            write(intLe(44_100))     // sample_rate
            write(intLe(0))          // bitrate_maximum
            write(intLe(128_000))    // bitrate_nominal
            write(intLe(0))          // bitrate_minimum
            write(0xB8)              // blocksize_0/1
            write(1)                 // framing bit
        }.toByteArray()
        val comment = ByteArrayOutputStream().apply {
            write(3)
            write("vorbis".toByteArray(Charsets.US_ASCII))
            write(intLe(4))
            write("test".toByteArray(Charsets.US_ASCII))
            write(intLe(comments.size))
            comments.forEach { entry ->
                val bytes = entry.toByteArray(Charsets.UTF_8)
                write(intLe(bytes.size))
                write(bytes)
            }
        }.toByteArray()
        val file = File.createTempFile("ogg-vorbis-", ".ogg")
        file.outputStream().buffered().use { output ->
            output.write(page(flags = 2, sequence = 0, packet = identification))
            output.write(page(flags = 0, sequence = 1, packet = comment))
            // Vorbis carries a third header packet (setup) before any audio.
            output.write(page(flags = 0, sequence = 2, packet = byteArrayOf(5, 118, 111, 114, 98, 105, 115)))
            output.write(page(flags = 4, sequence = 3, packet = byteArrayOf(0x11, 0x22, 0x33, 0x44)))
        }
        return file
    }

    private fun page(flags: Int, sequence: Int, packet: ByteArray): ByteArray {
        val laces = mutableListOf<Int>()
        var offset = 0
        while (packet.size - offset >= 255) {
            laces += 255
            offset += 255
        }
        laces += packet.size - offset
        val header = ByteArrayOutputStream().apply {
            write("OggS".toByteArray(Charsets.US_ASCII))
            write(0)                 // stream_structure_version
            write(flags)
            write(ByteArray(8))      // granule_position
            write(intLe(1))          // bitstream_serial_number
            write(intLe(sequence))
            write(ByteArray(4))      // page_checksum placeholder
            write(laces.size)
        }.toByteArray()
        val segmentTable = laces.map { it.toByte() }.toByteArray()
        val body = header + segmentTable + packet
        val checksum = crc(body)
        val result = body.copyOf()
        for (index in 0 until 4) result[22 + index] = ((checksum ushr (8 * index)) and 0xFF).toByte()
        return result
    }

    private fun crc(bytes: ByteArray): Int {
        val table = IntArray(256) { value -> var crc = value shl 24; repeat(8) { crc = (crc shl 1) xor if (crc < 0) 0x04c11db7 else 0 }; crc }
        var crc = 0
        bytes.forEach { crc = (crc shl 8) xor table[((crc ushr 24) xor (it.toInt() and 0xFF)) and 255] }
        return crc
    }

    private fun intLe(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte()
    )
}
