package org.zenconverter.app.audio

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Decodes any supported media into mono PCM samples using FFmpeg, so the
 * waveform, analysis and transcript editors all share one decoding path. The
 * audio never leaves the device; the only intermediate is a short-lived WAV in
 * the app cache.
 */
object AudioDecoder {
    private const val SAMPLE_RATE_HZ = 16_000
    private const val DECODE_TIMEOUT_MS = 120_000L

    data class Decoded(
        val samples: FloatArray,
        val sampleRateHz: Int,
        val channelCount: Int
    )

    suspend fun decodeMono(context: Context, uri: Uri, targetRateHz: Int = SAMPLE_RATE_HZ): Decoded? =
        withContext(Dispatchers.IO) {
            val input = runCatching {
                FFmpegKitConfig.getSafParameterForRead(context, uri)
            }.getOrNull() ?: runCatching {
                "/proc/self/fd/${context.contentResolver.openFileDescriptor(uri, "r")?.fd}"
            }.getOrNull() ?: return@withContext null

            val temp = File.createTempFile("zen-decode-", ".wav", context.cacheDir)
            try {
                val session = FFmpegKit.executeWithArguments(
                    arrayOf(
                        "-hide_banner",
                        "-nostdin",
                        "-i", input,
                        "-vn",
                        "-ac", "1",
                        "-ar", targetRateHz.toString(),
                        "-f", "wav",
                        "-c:a", "pcm_s16le",
                        "-y",
                        temp.absolutePath
                    )
                )
                if (!ReturnCode.isSuccess(session.getReturnCode())) return@withContext null
                val pcm = readPcmData(temp) ?: return@withContext null
                Decoded(
                    samples = WaveformAnalyzer.samplesFromPcm16(pcm, 1),
                    sampleRateHz = targetRateHz,
                    channelCount = 1
                )
            } finally {
                temp.delete()
            }
        }

    /** Extracts the raw PCM payload from a canonical 44-byte-header WAV file. */
    private fun readPcmData(file: File): ByteArray? {
        val bytes = file.readBytes()
        if (bytes.size <= 44) return null
        // Walk the chunk list rather than assuming a fixed header size, because
        // FFmpeg may emit a LIST/INFO chunk before "data".
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = littleEndianInt(bytes, offset + 4)
            val body = offset + 8
            if (id == "data") {
                val end = (body + size).coerceAtMost(bytes.size)
                return bytes.copyOfRange(body, end)
            }
            if (size <= 0) break
            offset = body + size + (size % 2)
        }
        return bytes.copyOfRange(44, bytes.size)
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int {
        if (offset + 4 > bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    const val DEFAULT_SAMPLE_RATE_HZ = SAMPLE_RATE_HZ
    const val TIMEOUT_MS = DECODE_TIMEOUT_MS
}
