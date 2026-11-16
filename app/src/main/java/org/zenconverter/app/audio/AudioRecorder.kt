package org.zenconverter.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/**
 * Microphone recorder that writes 16-bit PCM WAV directly, with pause/resume and
 * a live level meter. Other containers (FLAC/AAC) are produced afterwards by the
 * conversion service, which already knows how to encode from WAV.
 */
class AudioRecorder(
    private val sampleRateHz: Int = 44_100,
    private val channelCount: Int = 1
) {
    enum class State { Idle, Recording, Paused }

    data class Level(val rms: Float, val peak: Float)

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _level = MutableStateFlow(Level(0f, 0f))
    val level: StateFlow<Level> = _level.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    private var outputFile: File? = null
    @Volatile private var running = false
    @Volatile private var paused = false
    private var bytesWritten = 0L

    @SuppressLint("MissingPermission")
    suspend fun start(target: File) = withContext(Dispatchers.IO) {
        check(_state.value == State.Idle) { "Recorder already active" }
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRateHz,
            if (channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBuffer, sampleRateHz / 5 * channelCount * 2)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateHz,
            if (channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Microphone is unavailable" }
        outputFile = target
        bytesWritten = 0
        writeWavHeader(target, 0)
        recorder = record
        running = true
        paused = false
        _state.value = State.Recording
        _elapsedMs.value = 0
        record.startRecording()
        thread = Thread {
            val buffer = ByteArray(bufferSize)
            val startedAt = SystemClock.elapsedRealtime()
            var pausedMs = 0L
            var pauseStart = 0L
            while (running) {
                if (paused) {
                    if (pauseStart == 0L) pauseStart = SystemClock.elapsedRealtime()
                    Thread.sleep(20)
                    continue
                }
                if (pauseStart != 0L) {
                    pausedMs += SystemClock.elapsedRealtime() - pauseStart
                    pauseStart = 0L
                }
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                RandomAccessFile(target, "rw").use { raf ->
                    raf.seek(HEADER_BYTES + bytesWritten)
                    raf.write(buffer, 0, read)
                }
                bytesWritten += read
                _elapsedMs.value = SystemClock.elapsedRealtime() - startedAt - pausedMs
                _level.value = levelOf(buffer, read)
            }
            runCatching { record.stop() }
            runCatching { record.release() }
        }.also { it.isDaemon = true; it.start() }
    }

    fun pause() {
        if (_state.value == State.Recording) {
            paused = true
            _state.value = State.Paused
        }
    }

    fun resume() {
        if (_state.value == State.Paused) {
            paused = false
            _state.value = State.Recording
        }
    }

    suspend fun stop(): File? = withContext(Dispatchers.IO) {
        if (_state.value == State.Idle) return@withContext outputFile
        running = false
        paused = false
        thread?.join(1_500)
        thread = null
        recorder = null
        _state.value = State.Idle
        _level.value = Level(0f, 0f)
        val file = outputFile
        if (file != null) finalizeWavHeader(file, bytesWritten)
        file
    }

    private fun levelOf(buffer: ByteArray, length: Int): Level {
        var peak = 0f
        var sumSquares = 0.0
        val sampleCount = length / 2
        var index = 0
        while (index < sampleCount) {
            val low = buffer[index * 2].toInt() and 0xFF
            val high = buffer[index * 2 + 1].toInt()
            val value = ((high shl 8) or low).toShort() / 32768f
            val magnitude = abs(value)
            if (magnitude > peak) peak = magnitude
            sumSquares += value.toDouble() * value.toDouble()
            index += 1
        }
        val rms = if (sampleCount == 0) 0f else kotlin.math.sqrt(sumSquares / sampleCount).toFloat()
        return Level(rms.coerceIn(0f, 1f), peak.coerceIn(0f, 1f))
    }

    private fun writeWavHeader(file: File, dataBytes: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.write(ByteArray(HEADER_BYTES.toInt()))
            raf.seek(0)
            raf.write(wavHeader(dataBytes))
        }
    }

    private fun finalizeWavHeader(file: File, dataBytes: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0)
            raf.write(wavHeader(dataBytes))
        }
    }

    private fun wavHeader(dataBytes: Long): ByteArray {
        val byteRate = sampleRateHz * channelCount * 2
        val header = java.nio.ByteBuffer.allocate(HEADER_BYTES.toInt())
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt((36 + dataBytes).toInt())
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(channelCount.toShort())
        header.putInt(sampleRateHz)
        header.putInt(byteRate)
        header.putShort((channelCount * 2).toShort())
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataBytes.toInt())
        return header.array()
    }

    companion object {
        private const val HEADER_BYTES = 44L

        /** Converts a linear RMS value to dBFS for display. */
        fun toDecibels(rms: Float): Float =
            if (rms <= 0f) -80f else (20 * log10(rms.toDouble())).toFloat().coerceIn(-80f, 0f)
    }
}
