package org.zenconverter.app.audio

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
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

    /** How layered tracks are combined into the master. */
    enum class MixMode { Layer, Concat }

    /** Mixes [tracks] with per-track volume/pan and optional mute using `amix`. */
    suspend fun mix(
        context: Context,
        tracks: List<MixTrack>,
        target: File,
        audioBitrateKbps: Int = 192,
        mode: MixMode = MixMode.Layer,
        limiter: Boolean = true,
        normalize: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val audible = tracks.filterNot { it.mute }
        if (audible.isEmpty()) return@withContext false
        val paths = audible.mapNotNull { inputPathFor(context, it.uri) }
        if (paths.size != audible.size) return@withContext false

        val arguments = mutableListOf("-hide_banner", "-nostdin")
        paths.forEach { path -> arguments += listOf("-i", path) }
        arguments += listOf(
            "-filter_complex", buildMixFilter(audible, mode, limiter, normalize),
            "-map", "[out]",
            "-c:a", "aac",
            "-b:a", "${audioBitrateKbps}k",
            "-y",
            target.absolutePath
        )
        ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments.toTypedArray()).getReturnCode())
    }

    /** The filter graph [mix] will run, also shown verbatim in the command view. */
    fun buildMixFilter(
        tracks: List<MixTrack>,
        mode: MixMode = MixMode.Layer,
        limiter: Boolean = true,
        normalize: Boolean = false
    ): String {
        val perTrack = tracks.mapIndexed { index, track ->
            val gain = track.volume.coerceIn(0f, 4f)
            val (left, right) = panGains(track.pan)
            "[$index:a]aformat=channel_layouts=stereo," +
                "pan=mono|c0=0.5*c0+0.5*c1," +
                "pan=stereo|c0=${formatGain(left * gain)}*c0|c1=${formatGain(right * gain)}*c0[v$index]"
        }
        val inputs = tracks.indices.joinToString("") { "[v$it]" }
        val combine = when (mode) {
            MixMode.Layer -> "${inputs}amix=inputs=${tracks.size}:duration=longest:dropout_transition=0"
            MixMode.Concat -> "${inputs}concat=n=${tracks.size}:v=0:a=1"
        }
        val tail = buildString {
            if (normalize) append(",loudnorm=I=-16:TP=-1.5:LRA=11")
            if (limiter) append(",alimiter=limit=0.97")
        }
        return perTrack.joinToString(";") + ";" + combine + tail + "[out]"
    }

    /**
     * Constant-power pan gains for a -1 (hard left) to 1 (hard right) position.
     * Centre yields 0.707 on both sides so the perceived loudness stays even.
     */
    fun panGains(pan: Float): Pair<Float, Float> {
        val clamped = pan.coerceIn(-1f, 1f)
        val angle = (clamped + 1f) * (Math.PI.toFloat() / 4f)
        return kotlin.math.cos(angle) to kotlin.math.sin(angle)
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
    /** Visualizer look: which FFmpeg show* filter draws the moving shape. */
    enum class VisualizerStyle(val filter: String) {
        Waveform("showwaves=mode=cline"),
        Bars("showspectrum=mode=combined:color=intensity"),
        Circle("showcqt"),
        Spectrum("showspectrumpic=s=1920x1080:legend=disabled"),
        Particles("avectorscope=mode=lissajous")
    }

    /** Colour treatment applied to the moving shape. */
    enum class VisualizerPalette(val colors: String) {
        Frost("white"),
        Ember("0xFF6B35|0xFFD166"),
        Aurora("0x2EC4B6|0x8338EC"),
        Mono("0x9E9E9E")
    }

    /** What sits behind the shape. */
    enum class VisualizerBackground { Artwork, Blur, Solid, Gradient }

    /** Everything the visualizer screen can vary, so the render stays one call. */
    data class VisualizerOptions(
        val style: VisualizerStyle = VisualizerStyle.Waveform,
        val palette: VisualizerPalette = VisualizerPalette.Frost,
        val background: VisualizerBackground = VisualizerBackground.Artwork,
        val width: Int = 1280,
        val height: Int = 720,
        val frameRate: Int = 25,
        val showTitle: Boolean = false,
        val showProgress: Boolean = false,
        val title: String = ""
    )

    suspend fun renderVisualizer(
        context: Context,
        audioUri: Uri,
        artworkUri: Uri?,
        target: File,
        width: Int = 1280,
        height: Int = 720,
        options: VisualizerOptions = VisualizerOptions(width = width, height = height)
    ): Boolean = withContext(Dispatchers.IO) {
        val audioPath = inputPathFor(context, audioUri) ?: return@withContext false
        val artworkPath = artworkUri?.let { inputPathFor(context, it) }
        val arguments = mutableListOf("-hide_banner", "-nostdin")
        val useArtwork = artworkPath != null &&
            options.background in setOf(VisualizerBackground.Artwork, VisualizerBackground.Blur)
        if (useArtwork) {
            arguments += listOf("-loop", "1", "-i", artworkPath, "-i", audioPath)
        } else {
            arguments += listOf("-i", audioPath)
        }
        arguments += listOf(
            "-filter_complex",
            buildVisualizerFilter(options, useArtwork, artworkPath != null),
            "-map", "[v]",
            "-map", if (useArtwork) "1:a" else "0:a",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
            "-c:a", "aac", "-b:a", "192k",
            "-shortest",
            "-y", target.absolutePath
        )
        ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments.toTypedArray()).getReturnCode())
    }

    /**
     * Builds the visualizer filter graph. Pure string work so it can be shown to
     * the user and unit tested without running FFmpeg.
     */
    fun buildVisualizerFilter(
        options: VisualizerOptions,
        useArtwork: Boolean,
        artworkAvailable: Boolean
    ): String {
        val w = options.width
        val h = options.height
        val fps = options.frameRate
        val colors = options.palette.colors
        val shape = "[1:a]${options.style.filter}:s=${w}x$h:rate=$fps:colors=$colors[wave]"
        val shapeNoArt = "[0:a]${options.style.filter}:s=${w}x$h:rate=$fps:colors=$colors"

        val background = when {
            useArtwork -> "[0:v]scale=$w:$h:force_original_aspect_ratio=increase,crop=$w:$h" +
                (if (options.background == VisualizerBackground.Blur) ",boxblur=20:2" else "") + "[bg];"
            options.background == VisualizerBackground.Gradient ->
                "color=c=0x101014:s=${w}x$h:d=1,geq=r='128+96*Y/H':g='110+70*Y/H':b='180-40*Y/H'[bg];"
            else -> "color=c=0x101014:s=${w}x$h:d=1[bg];"
        }

        val overlay = if (useArtwork || options.background != VisualizerBackground.Artwork) {
            "${background}${shape};[bg][wave]overlay=format=auto"
        } else {
            shapeNoArt
        }

        val decorations = buildString {
            if (options.showTitle && options.title.isNotBlank()) {
                append(",drawtext=text='${escapeDrawText(options.title)}':x=40:y=40:fontsize=36:fontcolor=white")
            }
            if (options.showProgress) {
                append(",drawbox=x=0:y=ih-8:w=iw*t/duration:h=8:color=white@0.85:t=fill")
            }
        }
        return "$overlay$decorations,format=yuv420p[v]"
    }

    private fun escapeDrawText(text: String): String =
        text.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'")

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
     * Joins [sources] end to end into [target] with an optional silence gap.
     * Used by the text-to-speech export to stitch long documents together.
     */
    suspend fun concatFiles(sources: List<File>, target: File, gapMs: Long = 0L): Boolean =
        withContext(Dispatchers.IO) {
            if (sources.isEmpty()) return@withContext false
            if (sources.size == 1) {
                return@withContext runCatching { sources.first().copyTo(target, overwrite = true) }
                    .isSuccess
            }
            val arguments = mutableListOf("-hide_banner", "-nostdin")
            sources.forEach { arguments += listOf("-i", it.absolutePath) }
            val filter = buildString {
                if (gapMs > 0L) {
                    append("anullsrc=r=44100:cl=mono[gap];")
                }
                sources.indices.forEach { index ->
                    append("[$index:a]aformat=sample_fmts=s16:channel_layouts=mono[c$index];")
                    if (gapMs > 0L && index < sources.lastIndex) {
                        append("[c$index][gap]concat=n=2:v=0:a=1[g$index];")
                    }
                }
                val pieces = if (gapMs > 0L) {
                    sources.indices.flatMap { index ->
                        if (index < sources.lastIndex) listOf("[g$index]") else listOf("[c$index]")
                    }
                } else {
                    sources.indices.map { "[c$it]" }
                }
                append("${pieces.joinToString("")}concat=n=${pieces.size}:v=0:a=1[out]")
            }
            arguments += listOf(
                "-filter_complex", filter,
                "-map", "[out]",
                "-y", target.absolutePath
            )
            ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments.toTypedArray()).getReturnCode())
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

    /**
     * Splits [sourceUri] into four stem files inside [outputDir] using the
     * offline spectral approximation in [StemFilters]. Returns the rendered
     * stems in a stable order; the caller owns the output directory.
     */
    suspend fun separateStems(
        context: Context,
        sourceUri: Uri,
        outputDir: File
    ): List<Pair<StemKind, File>> = withContext(Dispatchers.IO) {
        val input = inputPathFor(context, sourceUri) ?: return@withContext emptyList()
        if (!outputDir.exists() && !outputDir.mkdirs()) return@withContext emptyList()
        val graph = StemFilters.graph()
        val targets = graph.outputs.map { it to File(outputDir, it.kind.fileName) }
        val arguments = mutableListOf("-hide_banner", "-nostdin", "-i", input, "-filter_complex", graph.filterComplex)
        targets.forEach { (output, file) ->
            arguments += listOf("-map", output.label, "-c:a", "pcm_s16le", file.absolutePath)
        }
        arguments += "-y"
        val session = FFmpegKit.executeWithArguments(arguments.toTypedArray())
        if (!ReturnCode.isSuccess(session.getReturnCode())) return@withContext emptyList()
        targets.mapNotNull { (output, file) -> file.takeIf { it.isFile && it.length() > 0L }?.let { output.kind to it } }
    }

    /**
     * Hard-subs [subtitleUri] onto the video in [sourceUri]. The subtitle file is
     * copied into the app cache first because the `subtitles` filter reads a real
     * path rather than a Storage Access Framework descriptor.
     */
    suspend fun burnSubtitles(
        context: Context,
        sourceUri: Uri,
        subtitleUri: Uri,
        target: File,
        fontSize: Int = 24
    ): Boolean = withContext(Dispatchers.IO) {
        val input = inputPathFor(context, sourceUri) ?: return@withContext false
        val subtitleFile = File(context.cacheDir, "zen-burn-${System.nanoTime()}.srt")
        val copied = runCatching {
            context.contentResolver.openInputStream(subtitleUri)?.use { source ->
                subtitleFile.outputStream().use { destination -> source.copyTo(destination) }
            }
            subtitleFile.length() > 0L
        }.getOrDefault(false)
        if (!copied) {
            subtitleFile.delete()
            return@withContext false
        }
        try {
            val style = "FontSize=$fontSize,Outline=1,Shadow=0,MarginV=24"
            val filter = "subtitles='${escapeFilterPath(subtitleFile.absolutePath)}':force_style='$style'"
            val arguments = arrayOf(
                "-hide_banner", "-nostdin",
                "-i", input,
                "-vf", filter,
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                "-c:a", "copy",
                "-y", target.absolutePath
            )
            ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments).getReturnCode())
        } finally {
            subtitleFile.delete()
        }
    }

    /**
     * Splits a media file at explicit chapter boundaries. [chapters] must be
     * ordered start times in seconds; each produced file covers one boundary
     * range up to the next, or the end of the file.
     */
    suspend fun splitAtTimestamps(
        context: Context,
        sourceUri: Uri,
        outputDir: File,
        chapters: List<Double>,
        extension: String = "mp3"
    ): List<File> = withContext(Dispatchers.IO) {
        val input = inputPathFor(context, sourceUri) ?: return@withContext emptyList()
        val boundaries = chapters.filter { it >= 0.0 }.distinct().sorted()
        if (boundaries.isEmpty()) return@withContext emptyList()
        if (!outputDir.exists() && !outputDir.mkdirs()) return@withContext emptyList()
        val produced = mutableListOf<File>()
        boundaries.forEachIndexed { index, start ->
            val target = File(outputDir, "chapter-%03d.$extension".format(index + 1))
            val arguments = mutableListOf("-hide_banner", "-nostdin", "-ss", formatSeconds(start), "-i", input)
            boundaries.getOrNull(index + 1)?.let { next ->
                arguments += listOf("-t", formatSeconds(next - start))
            }
            arguments += listOf("-vn", "-c:a", "libmp3lame", "-b:a", "192k", "-y", target.absolutePath)
            if (ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments.toTypedArray()).getReturnCode())) {
                if (target.isFile && target.length() > 0L) produced += target
            }
        }
        produced
    }

    /**
     * Renders an [AudioEditList] segment sequence from [sourceUri] by trimming
     * each range and concatenating the results, so the waveform editor's cut,
     * copy, paste and split operations stay non-destructive.
     */
    suspend fun renderEditList(
        context: Context,
        sourceUri: Uri,
        segments: List<AudioSegment>,
        target: File,
        audioBitrateKbps: Int = 192,
        audioCodec: String = "libmp3lame",
        fadeInSeconds: Double? = null,
        fadeOutSeconds: Double? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val input = inputPathFor(context, sourceUri) ?: return@withContext false
        val filter = AudioEditList.ffmpegFilter(segments) ?: return@withContext false
        val total = AudioEditList.totalDuration(AudioEditList.normalized(segments))
        val chain = buildList {
            add(filter)
            fadeInSeconds?.takeIf { it > 0.0 }?.let { add("afade=t=in:st=0:d=${formatSeconds(it)}") }
            fadeOutSeconds?.takeIf { it > 0.0 }?.let { duration ->
                add("afade=t=out:st=${formatSeconds((total - duration).coerceAtLeast(0.0))}:d=${formatSeconds(duration)}")
            }
        }
        val arguments = mutableListOf("-hide_banner", "-nostdin", "-i", input)
        if (chain.size == 1) {
            arguments += listOf("-af", chain.first())
        } else {
            // Two filter chains would clash on the `[out]` label, so the fades
            // are appended to the trim chain and mapped explicitly.
            arguments += listOf(
                "-filter_complex",
                chain.first().removeSuffix("[out]") + "," + chain.drop(1).joinToString(",") + "[out]",
                "-map", "[out]"
            )
        }
        arguments += listOf("-c:a", audioCodec, "-b:a", "${audioBitrateKbps}k", "-y", target.absolutePath)
        ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments.toTypedArray()).getReturnCode())
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
        fadeOutSeconds: Double? = null,
        audioCodec: String = "libmp3lame"
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
        arguments += listOf("-c:a", audioCodec, "-b:a", "${audioBitrateKbps}k", "-y", target.absolutePath)
        ReturnCode.isSuccess(FFmpegKit.executeWithArguments(arguments.toTypedArray()).getReturnCode())
    }

    /**
     * Reads the embedded chapter start times (in seconds) from [sourceUri] via
     * FFprobe. Returns an empty list when the container has no chapters.
     */
    suspend fun readChapterStarts(context: Context, sourceUri: Uri): List<Double> =
        withContext(Dispatchers.IO) {
            val path = inputPathFor(context, sourceUri) ?: return@withContext emptyList()
            val information = runCatching {
                FFprobeKit.getMediaInformation(path).getMediaInformation()
            }.getOrNull() ?: return@withContext emptyList()
            information.getChapters().orEmpty()
                .mapNotNull { chapter -> chapter.getStartTime()?.toDoubleOrNull() }
                .filter { it >= 0.0 }
                .distinct()
                .sorted()
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

    /**
     * Escapes a filesystem path for use inside an FFmpeg filter argument, where
     * the colon separates filter options and the backslash and quote characters
     * would otherwise end the option or the argument.
     */
    private fun escapeFilterPath(path: String): String =
        path.replace("\\", "\\\\")
            .replace(":", "\\:")
            .replace("'", "\\'")
            .replace(",", "\\,")
            .replace("[", "\\[")
            .replace("]", "\\]")
}
