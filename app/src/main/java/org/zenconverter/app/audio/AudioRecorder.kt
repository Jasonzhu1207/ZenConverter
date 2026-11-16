package org.zenconverter.app.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
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
    private val channelCount: Int = 1,
    private val inputSource: Int = MediaRecorder.AudioSource.MIC,
    private val autoGain: Boolean = false,
    private val noiseGate: Float = 0f,
    private val monitor: Boolean = false
) {
    enum class State { Idle, Recording, Paused }

    /** Microphone inputs a device may expose; the UI only offers what is present. */
    enum class Input(val source: Int, val label: String) {
        BuiltInMic(MediaRecorder.AudioSource.MIC, "Built-in mic"),
        BluetoothSco(MediaRecorder.AudioSource.VOICE_RECOGNITION, "Bluetooth"),
        Usb(MediaRecorder.AudioSource.UNPROCESSED, "USB"),
        Camcorder(MediaRecorder.AudioSource.CAMCORDER, "Camcorder")
    }

    data class Level(val rms: Float, val peak: Float)

    /** A named position in the recording, in milliseconds from the start. */
    data class Marker(val atMs: Long, val label: String)

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _level = MutableStateFlow(Level(0f, 0f))
    val level: StateFlow<Level> = _level.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _markers = MutableStateFlow<List<Marker>>(emptyList())
    val markers: StateFlow<List<Marker>> = _markers.asStateFlow()

    private var recorder: AudioRecord? = null
    private var monitorTrack: AudioTrack? = null
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
            inputSource,
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
        if (monitor) {
            monitorTrack = runCatching {
                val track = AudioTrack.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRateHz)
                            .setChannelMask(
                                if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                            )
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                    .also { it.play() }
                track
            }.getOrNull()
        }
        running = true
        paused = false
        _state.value = State.Recording
        _elapsedMs.value = 0
        _markers.value = emptyList()
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
                if (noiseGate > 0f || autoGain) applyProcessing(buffer, read)
                monitorTrack?.let { track -> runCatching { track.write(buffer, 0, read) } }
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
        monitorTrack?.let { track ->
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        monitorTrack = null
        _state.value = State.Idle
        _level.value = Level(0f, 0f)
        val file = outputFile
        if (file != null) finalizeWavHeader(file, bytesWritten)
        file
    }

    /** Adds a marker at the current elapsed position. */
    fun addMarker(label: String) {
        if (_state.value == State.Idle) return
        _markers.value = _markers.value + Marker(_elapsedMs.value, label)
    }

    /**
     * Applies the noise gate and auto gain in place on 16-bit little-endian PCM.
     * The gate zeroes samples below [noiseGate]; auto gain then scales the whole
     * block toward a -3 dBFS peak so quiet takes stay usable.
     */
    private fun applyProcessing(buffer: ByteArray, length: Int) {
        val sampleCount = length / 2
        if (sampleCount == 0) return
        var peak = 0f
        for (index in 0 until sampleCount) {
            val value = sampleAt(buffer, index)
            if (kotlin.math.abs(value) > peak) peak = kotlin.math.abs(value)
        }
        val gain = if (autoGain && peak > 0.0001f) (AUTO_GAIN_TARGET / peak).coerceAtMost(AUTO_GAIN_MAX) else 1f
        for (index in 0 until sampleCount) {
            var value = sampleAt(buffer, index)
            if (noiseGate > 0f && kotlin.math.abs(value) < noiseGate) {
                value = 0f
            }
            value = (value * gain).coerceIn(-1f, 1f)
            val scaled = (value * 32767f).toInt()
            buffer[index * 2] = (scaled and 0xFF).toByte()
            buffer[index * 2 + 1] = ((scaled shr 8) and 0xFF).toByte()
        }
    }

    private fun sampleAt(buffer: ByteArray, index: Int): Float {
        val low = buffer[index * 2].toInt() and 0xFF
        val high = buffer[index * 2 + 1].toInt()
        return ((high shl 8) or low).toShort() / 32768f
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
        private const val AUTO_GAIN_TARGET = 0.7f
        private const val AUTO_GAIN_MAX = 8f

        private const val HEADER_BYTES = 44L

        /** Converts a linear RMS value to dBFS for display. */
        fun toDecibels(rms: Float): Float =
            if (rms <= 0f) -80f else (20 * log10(rms.toDouble())).toFloat().coerceIn(-80f, 0f)
    }
}
