package org.zenconverter.app.audio

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * FFmpeg-backed operations the studio needs that the converter does not already
 * cover: multi-track mixing, ringtone trimming, waveform-video rendering and
 * silence-based splitting. Each call writes into a caller-provided temp file so
 * the UI owns the final destination, keeping the local-first contract.
 */
object AudioStudioEngine {

    data class MixTrack(
        val uri: Uri,
        val volume: Float = 1f,
        val pan: Float = 0f,
        val mute: Boolean = false
    )

    /** Mixes [tracks] with per-track volume/pan and optional mute using `amix`. */
    suspend fun mix(
        context: Context,
        tracks: List<MixTrack>,
        target: File,
        audioBitrateKbps: Int = 192
    ): Boolean = withContext(Dispatchers.IO) {
        val audible = tracks.filterNot { it.mute }
        if (audible.isEmpty()) return@withContext false
        val paths = audible.mapNotNull { inputPathFor(context, it.uri) }
        if (paths.size != audible.size) return@withContext false

        val arguments = mutableListOf("-hide_banner", "-nostdin")
        paths.forEach { path -> arguments += listOf("-i", path) }

        val filterParts = audible.mapIndexed { index, track ->
            val gain = track.volume.coerceIn(0f, 4f)
            val pan = track.pan.coerceIn(-1f, 1f)
            // stereo pan: left/right gains derived from a constant-power pan
            val left = (kotlin.math.cos((pan + 1f) * (Math.PI.toFloat() / 4f)) * gain)
            val right = (kotlin.math.sin((pan + 1f) * (Math.PI.toFloat() / 4f)) * gain)
            "[$index:a]aformat=channel_layouts=stereo," +
                "pan=stereo|c0=${formatGain(left)}*c0|c1=${formatGain(right)}*c1[v$index]"
        }
        val mixInputs = audible.indices.joinToString("") { "[v$it]" }
        val filter = filterParts.joinToString(";") +
            ";${mixInputs}amix=inputs=${audible.size}:duration=longest:dropout_transition=0," +
            "alimiter=limit=0.97[out]"

        arguments += listOf(
            "-filter_complex", filter,
            "-map", "[out]",
            "-c:a", "aac",
            "-b:a", "${audioBitrateKbps}k",
            "-y",
            target.absolutePath
        )
        ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments.toTypedArray()).getReturnCode())
    }

    /** Trims [startSeconds]..[endSeconds] without re-encoding where possible. */
    suspend fun trim(
        context: Context,
        sourceUri: Uri,
        startSeconds: Double,
        endSeconds: Double,
        target: File,
        reEncode: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        val input = inputPathFor(context, sourceUri) ?: return@withContext false
        val duration = (endSeconds - startSeconds).coerceAtLeast(0.0)
        if (duration <= 0.0) return@withContext false
        val arguments = mutableListOf(
            "-hide_banner", "-nostdin",
            "-ss", formatSeconds(startSeconds),
            "-i", input,
            "-t", formatSeconds(duration)
        )
        arguments += if (reEncode) {
            listOf("-c:a", "libmp3lame", "-b:a", "192k")
        } else {
            listOf("-c", "copy")
        }
        arguments += listOf("-y", target.absolutePath)
        ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments.toTypedArray()).getReturnCode())
    }

    /**
     * Renders a waveform visualizer video from an audio file plus optional
     * artwork. The audio is drawn as a `showwaves` overlay on a solid or image
     * background, producing a shareable MP4.
     */
    suspend fun renderVisualizer(
        context: Context,
        audioUri: Uri,
        artworkUri: Uri?,
        target: File,
        width: Int = 1280,
        height: Int = 720
    ): Boolean = withContext(Dispatchers.IO) {
        val audioPath = inputPathFor(context, audioUri) ?: return@withContext false
        val artworkPath = artworkUri?.let { inputPathFor(context, it) }

        val arguments = mutableListOf("-hide_banner", "-nostdin")
        if (artworkPath != null) {
            arguments += listOf("-loop", "1", "-i", artworkPath, "-i", audioPath)
        } else {
            arguments += listOf("-i", audioPath)
        }

        val filter = if (artworkPath != null) {
            "[0:v]scale=$width:$height:force_original_aspect_ratio=increase," +
                "crop=$width:$height[bg];" +
                "[1:a]showwaves=s=${width}x$height:mode=cline:rate=25:colors=white[wave];" +
                "[bg][wave]overlay=format=auto,format=yuv420p[v]"
        } else {
            "[0:a]showwaves=s=${width}x$height:mode=cline:rate=25:colors=white,format=yuv420p[v]"
        }
        arguments += listOf("-filter_complex", filter, "-map", "[v]", "-map", if (artworkPath != null) "1:a" else "0:a")
        arguments += listOf(
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
            "-c:a", "aac", "-b:a", "192k",
            "-shortest",
            "-y", target.absolutePath
        )
        ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments.toTypedArray()).getReturnCode())
    }

    /**
     * Splits [sourceUri] into segments on silence using FFmpeg's
     * `silencedetect`. Returns the generated segment files in order. The caller
     * owns the output directory.
     */
    suspend fun splitBySilence(
        context: Context,
        sourceUri: Uri,
        outputDir: File,
        thresholdDb: Double = -35.0,
        minSilenceSeconds: Double = 0.5
    ): List<File> = withContext(Dispatchers.IO) {
        val input = inputPathFor(context, sourceUri) ?: return@withContext emptyList()
        if (!outputDir.exists() && !outputDir.mkdirs()) return@withContext emptyList()
        val prefix = File(outputDir, "segment").absolutePath
        val filter = "silenceremove=start_periods=1:start_duration=0.1:" +
            "start_threshold=${formatSeconds(thresholdDb)}dB:detection=peak," +
            "silenceremove=stop_periods=-1:stop_duration=${formatSeconds(minSilenceSeconds)}:" +
            "stop_threshold=${formatSeconds(thresholdDb)}dB:detection=peak"
        val arguments = arrayOf(
            "-hide_banner", "-nostdin",
            "-i", input,
            "-af", filter,
            "-f", "segment",
            "-segment_time", formatSeconds(minSilenceSeconds),
            "-c", "copy",
            "-y", "$prefix-%03d.mp3"
        )
        val session = FFmpegKit.executeWithArguments(arguments)
        if (!ReturnCode.isSuccess(session.getReturnCode())) return@withContext emptyList()
        outputDir.listFiles { file -> file.name.startsWith("segment-") }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    /** Re-encodes a local file to another audio codec, used by the recorder. */
    suspend fun transcodeFile(source: File, target: File, codec: String): Boolean =
        withContext(Dispatchers.IO) {
            val arguments = arrayOf(
                "-hide_banner", "-nostdin",
                "-i", source.absolutePath,
                "-vn",
                "-c:a", codec,
                "-y", target.absolutePath
            )
            ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments).getReturnCode())
        }

    /**
     * Concatenates the surviving [segments] of a source file, used by
     * cut-by-transcript editing. Each segment is decoded to a common format
     * before concatenation so mixed boundaries stay sample accurate.
     */
    suspend fun cutSegments(
        context: Context,
        sourceUri: Uri,
        segments: List<TranscriptEditing.KeepSegment>,
        target: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (segments.isEmpty()) return@withContext false
        val input = inputPathFor(context, sourceUri) ?: return@withContext false
        val parts = mutableListOf<String>()
        segments.forEachIndexed { index, segment ->
            parts += "[0:a]atrim=start=${formatSeconds(segment.startSeconds)}:" +
                "end=${formatSeconds(segment.endSeconds)}," +
                "asetpts=PTS-STARTPTS[a$index]"
        }
        val inputs = segments.indices.joinToString("") { "[a$it]" }
        val filter = parts.joinToString(";") + ";${inputs}concat=n=${segments.size}:v=0:a=1[out]"
        val arguments = arrayOf(
            "-hide_banner", "-nostdin",
            "-i", input,
            "-filter_complex", filter,
            "-map", "[out]",
            "-c:a", "libmp3lame", "-b:a", "192k",
            "-y", target.absolutePath
        )
        ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments).getReturnCode())
    }

    /** Applies a chain of FFmpeg audio filters and writes the result to [target]. */
    suspend fun applyEffects(
        context: Context,
        sourceUri: Uri,
        filters: List<String>,
        target: File,
        audioBitrateKbps: Int = 192,
        startSeconds: Double? = null,
        endSeconds: Double? = null,
        fadeInSeconds: Double? = null,
        fadeOutSeconds: Double? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val input = inputPathFor(context, sourceUri) ?: return@withContext false
        val chain = buildList {
            addAll(filters)
            fadeInSeconds?.takeIf { it > 0.0 }?.let { add("afade=t=in:st=0:d=${formatSeconds(it)}") }
            fadeOutSeconds?.takeIf { it > 0.0 }?.let { duration ->
                val fadeOutStart = endSeconds?.let { end ->
                    (end - duration).coerceAtLeast(0.0)
                }
                if (fadeOutStart != null) add("afade=t=out:st=${formatSeconds(fadeOutStart)}:d=${formatSeconds(duration)}")
            }
        }
        val arguments = mutableListOf("-hide_banner", "-nostdin")
        if (startSeconds != null && endSeconds != null && endSeconds > startSeconds) {
            arguments += listOf("-ss", formatSeconds(startSeconds), "-t", formatSeconds(endSeconds - startSeconds))
        }
        arguments += listOf("-i", input)
        if (chain.isNotEmpty()) arguments += listOf("-af", chain.joinToString(","))
        arguments += listOf("-c:a", "libmp3lame", "-b:a", "${audioBitrateKbps}k", "-y", target.absolutePath)
        ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments.toTypedArray()).getReturnCode())
    }

    private fun inputPathFor(context: Context, uri: Uri): String? {
        return runCatching {
            FFmpegKitConfig.getSafParameterForRead(context, uri)
        }.getOrNull() ?: runCatching {
            "/proc/self/fd/${context.contentResolver.openFileDescriptor(uri, "r")?.fd}"
        }.getOrNull()
    }

    private fun formatGain(value: Float): String =
        String.format(Locale.US, "%.4f", value.coerceAtLeast(0f))

    private fun formatSeconds(value: Double): String =
        String.format(Locale.US, "%.3f", value)
}
