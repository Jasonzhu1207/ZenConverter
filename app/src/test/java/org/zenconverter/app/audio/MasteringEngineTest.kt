package org.zenconverter.app.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MasteringEngineTest {
    private fun wavFile(
        channels: Int,
        sampleRate: Int,
        bits: Int,
        payload: ByteArray,
        withListChunk: Boolean = false
    ): File {
        val file = File.createTempFile("zen-test-", ".wav")
        val out = java.io.ByteArrayOutputStream()
        fun ascii(text: String) = out.write(text.toByteArray(Charsets.US_ASCII))
        fun int(value: Int) = out.write(
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
        )
        fun short(value: Int) = out.write(
            ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
        )
        ascii("RIFF")
        int(36 + payload.size)
        ascii("WAVE")
        if (withListChunk) {
            ascii("LIST")
            int(4)
            ascii("INFO")
        }
        ascii("fmt ")
        int(16)
        short(1)
        short(channels)
        int(sampleRate)
        int(sampleRate * channels * bits / 8)
        short(channels * bits / 8)
        short(bits)
        ascii("data")
        int(payload.size)
        out.write(payload)
        file.writeBytes(out.toByteArray())
        return file
    }

    @Test
    fun readsFloat32StereoPcm() {
        val frames = intArrayOf(0, 0, 16384, 32768) // 0.0, 0.0, 0.5, 1.0
        val payload = ByteArray(frames.size * 4)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        frames.forEach { buffer.putFloat(it / 32768f) }
        val decoded = MasteringEngine.readPcm(wavFile(2, 48_000, 32, payload))!!
        assertEquals(2, decoded.channelCount)
        assertEquals(48_000, decoded.sampleRateHz)
        assertEquals(4, decoded.samples.size)
        assertEquals(0.5f, decoded.samples[2], 0.0001f)
        assertEquals(1.0f, decoded.samples[3], 0.0001f)
    }

    @Test
    fun readsSixteenBitPcm() {
        val payload = ByteArray(4)
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).putShort(16384).putShort(0)
        val decoded = MasteringEngine.readPcm(wavFile(1, 44_100, 16, payload))!!
        assertEquals(1, decoded.channelCount)
        assertEquals(44_100, decoded.sampleRateHz)
        assertEquals(0.5f, decoded.samples[0], 0.0001f)
    }

    @Test
    fun skipsAListChunkBeforeTheDataChunk() {
        val payload = ByteArray(4)
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).putShort(32767).putShort(0)
        val decoded = MasteringEngine.readPcm(wavFile(1, 48_000, 16, payload, withListChunk = true))!!
        assertEquals(2, decoded.samples.size)
        assertEquals(1.0f, decoded.samples[0], 0.001f)
    }

    @Test
    fun rejectsATooSmallFile() {
        val file = File.createTempFile("zen-small-", ".wav")
        file.writeBytes(ByteArray(10))
        assertNull(MasteringEngine.readPcm(file))
    }

    @Test
    fun losslessFormatsKeepPcmAndFlacCodecs() {
        assertTrue(MasteringEngine.codecArguments(MasteringEngine.ExportFormat.Wav).contains("pcm_s24le"))
        assertTrue(MasteringEngine.codecArguments(MasteringEngine.ExportFormat.Flac).contains("flac"))
        assertTrue(MasteringEngine.ExportFormat.Wav.isLossless)
        assertTrue(!MasteringEngine.ExportFormat.Mp3.isLossless)
    }
}
