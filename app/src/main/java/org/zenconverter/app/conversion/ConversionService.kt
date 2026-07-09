package org.zenconverter.app.conversion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.VideoEncoderSettings
import org.zenconverter.app.MainActivity
import org.zenconverter.app.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class ConversionService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val progressHolder = ProgressHolder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var taskIndex = 0
    private var activeTransformer: Transformer? = null
    private var activeFfmpegSession: FFmpegSession? = null
    private var activeTempFile: File? = null
    private var progressRunnable: Runnable? = null
    private var copyThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        FFmpegKitConfig.enableRedirection()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelRun()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (ConversionTaskStore.taskCount() == 0) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                handler.removeCallbacksAndMessages(null)
                startForeground(NOTIFICATION_ID, buildNotification("Preparing", 0))
                taskIndex = 0
                processNextTask()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        activeTransformer?.cancel()
        cancelActiveFfmpegSession()
        copyThread?.interrupt()
        activeTempFile?.delete()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun processNextTask() {
        if (ConversionTaskStore.isCancelled()) {
            cancelRun()
            return
        }

        if (taskIndex >= ConversionTaskStore.taskCount()) {
            finishRun()
            return
        }

        val input = ConversionTaskStore.inputAt(taskIndex)
        if (input == null) {
            failCurrentTask("Conversion failed")
            return
        }

        val outputProfile = outputProfileFor(input)
        if (outputProfile == null) {
            failCurrentTask("Only video MP4, audio M4A, and JPG/PNG/WEBP images are connected")
            return
        }

        val useCompatibilityEngine = shouldUseCompatibilityEngine(input)
        Log.i(
            TAG,
            "Routing conversion task index=$taskIndex category=${input.category} " +
                "target=${input.targetFormat} displayName=${input.displayName} " +
                "mimeType=${input.mimeType} extension=${input.extension} " +
                "engine=${
                    when {
                        input.category == ConversionMediaCategory.Image -> "NativeBitmap"
                        useCompatibilityEngine -> "Compatibility"
                        else -> "Hardware"
                    }
                }"
        )
        if (
            !useCompatibilityEngine &&
            input.category == ConversionMediaCategory.Video &&
            !VideoEncoderSupport.canEncode(input.videoOptions.videoMimeType)
        ) {
            failCurrentTask("Selected video encoder is not supported on this device")
            return
        }

        val tempFile = createTempFileFor(input, outputProfile.extension)
        activeTempFile = tempFile
        ConversionTaskStore.markRunning(taskIndex)
        updateNotification("Processing", (ConversionTaskStore.aggregateProgress() * 100).toInt())

        if (input.category == ConversionMediaCategory.Image) {
            startImageExport(input, tempFile, outputProfile)
            return
        }

        if (useCompatibilityEngine) {
            startCompatibilityExport(input, tempFile)
            return
        }

        startMedia3Export(input, tempFile)
    }

    private fun startMedia3Export(
        input: ConversionTaskInput,
        tempFile: File
    ) {
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                Log.i(
                    TAG,
                    "Media3 export completed category=${input.category} " +
                        "target=${input.targetFormat} displayName=${input.displayName} " +
                        "durationMs=${exportResult.approximateDurationMs} " +
                        "fileSizeBytes=${exportResult.fileSizeBytes} " +
                        "audioMime=${exportResult.audioMimeType} " +
                        "videoMime=${exportResult.videoMimeType}"
                )
                handler.post {
                    saveCompletedExport(input, tempFile)
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                val failureMessage = failureMessageFor(exportException)
                Log.w(
                    TAG,
                    "Media3 export failed category=${input.category} " +
                        "target=${input.targetFormat} displayName=${input.displayName} " +
                        "errorCode=${exportException.getErrorCodeName()} " +
                        "audioMime=${exportResult.audioMimeType} " +
                        "videoMime=${exportResult.videoMimeType}",
                    exportException
                )
                handler.post {
                    stopProgressPolling()
                    activeTransformer = null
                    activeTempFile?.delete()
                    activeTempFile = null
                    failCurrentTask(failureMessage)
                }
            }

            override fun onFallbackApplied(
                composition: Composition,
                originalTransformationRequest: TransformationRequest,
                fallbackTransformationRequest: TransformationRequest
            ) {
                Log.i(
                    TAG,
                    "Media3 fallback applied category=${input.category} " +
                        "target=${input.targetFormat} displayName=${input.displayName} " +
                        "originalAudio=${originalTransformationRequest.audioMimeType} " +
                        "fallbackAudio=${fallbackTransformationRequest.audioMimeType} " +
                        "originalVideo=${originalTransformationRequest.videoMimeType} " +
                        "fallbackVideo=${fallbackTransformationRequest.videoMimeType}"
                )
            }
        }

        runCatching {
            tempFile.delete()
            val encoderFactoryBuilder = DefaultEncoderFactory.Builder(this)
            if (input.category == ConversionMediaCategory.Video) {
                input.videoOptions.videoBitrate?.let { bitrate ->
                    encoderFactoryBuilder.setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(bitrate)
                            .build()
                    )
                }
            }
            if (input.category == ConversionMediaCategory.Audio) {
                input.audioOptions.audioBitrate?.let { bitrate ->
                    encoderFactoryBuilder.setRequestedAudioEncoderSettings(
                        AudioEncoderSettings.Builder()
                            .setBitrate(bitrate)
                            .build()
                    )
                }
            }

            val transformerBuilder = Transformer.Builder(this)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setMaxDelayBetweenMuxerSamplesMs(MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS)
                .setEncoderFactory(encoderFactoryBuilder.build())
                .addListener(listener)

            if (input.category == ConversionMediaCategory.Video) {
                transformerBuilder.setVideoMimeType(input.videoOptions.videoMimeType)
            }

            activeTransformer = transformerBuilder.build()
            Log.i(
                TAG,
                "Starting Media3 export category=${input.category} " +
                    "target=${input.targetFormat} displayName=${input.displayName} " +
                    "temp=${tempFile.name} videoOptions=${input.videoOptions} " +
                    "audioOptions=${input.audioOptions} " +
                    "maxDelayBetweenMuxerSamplesMs=$MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS"
            )
            activeTransformer?.start(buildEditedMediaItem(input), tempFile.absolutePath)
            startProgressPolling()
        }.onFailure { exception ->
            Log.w(TAG, "Could not start Media3 export", exception)
            stopProgressPolling()
            activeTransformer = null
            activeTempFile?.delete()
            activeTempFile = null
            failCurrentTask("Could not start native export")
        }
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        val runnable = object : Runnable {
            override fun run() {
                val transformer = activeTransformer ?: return
                val progressState = runCatching {
                    transformer.getProgress(progressHolder)
                }.getOrDefault(Transformer.PROGRESS_STATE_UNAVAILABLE)

                if (progressState == Transformer.PROGRESS_STATE_AVAILABLE) {
                    ConversionTaskStore.updateProgress(
                        taskIndex,
                        progressHolder.progress / 100f
                    )
                }
                updateNotification(
                    "Processing",
                    (ConversionTaskStore.aggregateProgress() * 100).toInt()
                )
                handler.postDelayed(this, PROGRESS_DELAY_MS)
            }
        }
        progressRunnable = runnable
        handler.post(runnable)
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun startImageExport(
        input: ConversionTaskInput,
        tempFile: File,
        outputProfile: OutputProfile
    ) {
        serviceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    writeImageExport(input, tempFile, outputProfile)
                }
            }.onSuccess {
                if (ConversionTaskStore.isCancelled()) {
                    tempFile.delete()
                    cancelRun()
                    return@onSuccess
                }
                ConversionTaskStore.updateProgress(taskIndex, PROGRESS_BEFORE_SAVE)
                saveCompletedExport(input, tempFile)
            }.onFailure { exception ->
                tempFile.delete()
                if (exception is CancellationException) {
                    return@onFailure
                }
                Log.w(TAG, "Could not run image export", exception)
                failCurrentTask(imageFailureMessageFor(exception))
            }
        }
    }

    private fun writeImageExport(
        input: ConversionTaskInput,
        outputFile: File,
        outputProfile: OutputProfile
    ) {
        if (ConversionTaskStore.isCancelled()) throw CancellationException()
        updateImageProgress(0.15f)

        val decodedBitmap = decodeImageBitmap(
            input.inputUri,
            maxLongSidePixels = null
        ) ?: error("Image engine could not decode this input")
        updateImageProgress(0.55f)

        val bitmapForOutput = bitmapForImageOutput(
            decodedBitmap,
            outputProfile.extension,
            flattenTransparency = false
        )
        val compressFormat = imageCompressFormatFor(outputProfile.extension)
            ?: error("Image engine could not write this output")
        val quality = imageQualityFor(outputProfile.extension, input.imageOptions.quality)

        try {
            outputFile.outputStream().use { output ->
                if (!bitmapForOutput.compress(compressFormat, quality, output)) {
                    error("Image engine could not write this output")
                }
                output.flush()
            }
            updateImageProgress(0.95f)
        } finally {
            if (bitmapForOutput !== decodedBitmap) {
                bitmapForOutput.recycle()
            }
            decodedBitmap.recycle()
        }
    }

    private fun decodeImageBitmap(
        uri: Uri,
        maxLongSidePixels: Int?
    ): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeImageBitmapWithImageDecoder(uri, maxLongSidePixels)?.let { bitmap ->
                return scaleBitmapIfNeeded(bitmap, maxLongSidePixels)
            }
        }
        decodeImageBitmapWithFileDescriptor(uri, maxLongSidePixels)?.let { bitmap ->
            return bitmap
        }
        return decodeImageBitmapWithBitmapFactory(uri, maxLongSidePixels)
    }

    private fun decodeImageBitmapWithImageDecoder(
        uri: Uri,
        maxLongSidePixels: Int?
    ): Bitmap? {
        return runCatching {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                if (info.size.width > 0 && info.size.height > 0) {
                    val targetSize = imageDecodeSizeFor(
                        info.size.width,
                        info.size.height,
                        maxLongSidePixels
                    )
                    decoder.setTargetSize(targetSize.width, targetSize.height)
                }
            }
        }.onFailure { exception ->
            Log.w(TAG, "ImageDecoder could not decode input; trying BitmapFactory fallback", exception)
        }.getOrNull()
    }

    private fun decodeImageBitmapWithFileDescriptor(
        uri: Uri,
        maxLongSidePixels: Int?
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, bounds)
            }
        }.onFailure { exception ->
            Log.w(TAG, "BitmapFactory could not open image file descriptor for bounds", exception)
        }

        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "BitmapFactory file descriptor bounds failed width=$width height=$height")
            return null
        }
        val orientation = readImageOrientation(uri)

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = imageSampleSize(width, height, maxLongSidePixels)
        }
        val decoded = runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
            }
        }.onFailure { exception ->
            Log.w(TAG, "BitmapFactory could not decode image file descriptor", exception)
        }.getOrNull() ?: run {
            Log.w(TAG, "BitmapFactory file descriptor pixel decode returned null")
            return null
        }

        val scaled = scaleBitmapIfNeeded(decoded, maxLongSidePixels)
        return applyImageOrientationIfNeeded(scaled, orientation)
    }

    private fun decodeImageBitmapWithBitmapFactory(
        uri: Uri,
        maxLongSidePixels: Int?
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: run {
            Log.w(TAG, "BitmapFactory could not open image input stream for bounds")
            return null
        }

        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "BitmapFactory could not read image bounds width=$width height=$height")
            return null
        }
        val orientation = readImageOrientation(uri)

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = imageSampleSize(width, height, maxLongSidePixels)
        }
        val decoded = contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: run {
            Log.w(TAG, "BitmapFactory could not decode image pixels")
            return null
        }

        val scaled = scaleBitmapIfNeeded(decoded, maxLongSidePixels)
        return applyImageOrientationIfNeeded(scaled, orientation)
    }

    private fun readImageOrientation(uri: Uri): Int {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    }

    private fun imageSampleSize(
        width: Int,
        height: Int,
        maxLongSidePixels: Int?
    ): Int {
        var sampleSize = 1
        while (
            imagePixelsAtSample(width, height, sampleSize) > MAX_IMAGE_DECODE_PIXELS ||
            (
                maxLongSidePixels != null &&
                    maxOf(width, height) / sampleSize > maxLongSidePixels * 2
                )
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun imagePixelsAtSample(width: Int, height: Int, sampleSize: Int): Long {
        val sampledWidth = (width / sampleSize).coerceAtLeast(1)
        val sampledHeight = (height / sampleSize).coerceAtLeast(1)
        return sampledWidth.toLong() * sampledHeight.toLong()
    }

    private fun imageDecodeSizeFor(
        width: Int,
        height: Int,
        maxLongSidePixels: Int?
    ): ImageDecodeSize {
        val sampleSize = imageSampleSize(width, height, maxLongSidePixels)
        return ImageDecodeSize(
            width = (width / sampleSize).coerceAtLeast(1),
            height = (height / sampleSize).coerceAtLeast(1)
        )
    }

    private fun scaleBitmapIfNeeded(
        bitmap: Bitmap,
        maxLongSidePixels: Int?
    ): Bitmap {
        val maxLongSide = maxLongSidePixels ?: return bitmap
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= maxLongSide) return bitmap

        val scale = maxLongSide.toFloat() / longSide.toFloat()
        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        bitmap.recycle()
        return scaled
    }

    private fun applyImageOrientationIfNeeded(
        bitmap: Bitmap,
        orientation: Int
    ): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }

        val transformed = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
        if (transformed !== bitmap) {
            bitmap.recycle()
        }
        return transformed
    }

    private fun bitmapForImageOutput(
        bitmap: Bitmap,
        extension: String,
        flattenTransparency: Boolean
    ): Bitmap {
        val shouldFlatten = flattenTransparency ||
            (extension.equals("jpg", ignoreCase = true) && bitmap.hasAlpha())
        if (!shouldFlatten) return bitmap

        val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return flattened
    }

    @Suppress("DEPRECATION")
    private fun imageCompressFormatFor(extension: String): Bitmap.CompressFormat? {
        return when (extension.lowercase(Locale.US)) {
            "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            else -> null
        }
    }

    private fun imageQualityFor(extension: String, requestedQuality: Int): Int {
        return if (extension.equals("png", ignoreCase = true)) {
            100
        } else {
            requestedQuality.coerceIn(1, 100)
        }
    }

    private fun updateImageProgress(progress: Float) {
        handler.post {
            if (ConversionTaskStore.isCancelled()) return@post
            ConversionTaskStore.updateProgress(taskIndex, progress.coerceIn(0f, PROGRESS_BEFORE_SAVE))
            updateNotification(
                "Processing",
                (ConversionTaskStore.aggregateProgress() * 100).toInt()
            )
        }
    }

    private fun imageFailureMessageFor(exception: Throwable): String {
        return exception.message?.takeIf { it.startsWith("Image engine") }
            ?: "Image conversion failed"
    }

    private fun startCompatibilityExport(
        input: ConversionTaskInput,
        tempFile: File
    ) {
        serviceScope.launch {
            runCatching {
                runFfmpegCompatibilityExport(input, tempFile)
            }.onSuccess { result ->
                activeFfmpegSession = null
                if (ConversionTaskStore.isCancelled() || result.cancelled) {
                    tempFile.delete()
                    cancelRun()
                    return@onSuccess
                }
                if (result.success) {
                    saveCompletedExport(input, tempFile)
                } else {
                    Log.e(
                        TAG,
                        "Compatibility export returned failure category=${input.category} " +
                            "target=${input.targetFormat} displayName=${input.displayName} " +
                            "message=${result.message} outputTail=${result.outputTail.orEmpty()}"
                    )
                    tempFile.delete()
                    failCurrentTask(result.message ?: compatibilityFailureMessageFor(input))
                }
            }.onFailure { exception ->
                activeFfmpegSession = null
                tempFile.delete()
                if (exception is CancellationException) {
                    return@onFailure
                }
                Log.e(TAG, "Could not run FFmpeg compatibility export", exception)
                failCurrentTask("Compatibility engine failed before export")
            }
        }
    }

    private suspend fun runFfmpegCompatibilityExport(
        input: ConversionTaskInput,
        tempFile: File
    ): FfmpegRunResult = withContext(Dispatchers.IO) {
        val durationMs = readDurationMs(input.inputUri)
        val logTail = mutableListOf<String>()

        val inputSource = openFfmpegInputSource(input.inputUri)
            ?: return@withContext FfmpegRunResult(
                success = false,
                cancelled = false,
                message = "Compatibility engine could not open SAF input"
            )

        try {
            val arguments = ffmpegArgumentsFor(input, inputSource.path, tempFile)
            executeFfmpeg(input, arguments, durationMs, logTail, inputSource.label)
        } finally {
            inputSource.close()
        }
    }

    private fun openFfmpegInputSource(uri: Uri): FfmpegInputSource? {
        val safPath = runCatching {
            FFmpegKitConfig.getSafParameterForRead(this, uri)
        }.onFailure { exception ->
            Log.w(TAG, "Could not create FFmpegKit SAF input parameter", exception)
        }.getOrNull()

        if (!safPath.isNullOrBlank()) {
            return FfmpegInputSource(
                label = "saf",
                path = safPath,
                descriptor = null
            )
        }

        val inputDescriptor = openInputDescriptor(uri) ?: return null
        return FfmpegInputSource(
            label = "fd",
            path = "/proc/self/fd/${inputDescriptor.fd}",
            descriptor = inputDescriptor
        )
    }

    private fun openInputDescriptor(uri: Uri): ParcelFileDescriptor? {
        return runCatching {
            contentResolver.openFileDescriptor(uri, "r")
        }.onFailure { exception ->
            Log.w(TAG, "Could not open SAF input descriptor for FFmpeg", exception)
        }.getOrNull()
    }

    private fun ffmpegArgumentsFor(
        input: ConversionTaskInput,
        inputPath: String,
        outputFile: File
    ): List<String> {
        return when (input.category) {
            ConversionMediaCategory.Video -> listOf(
                "-hide_banner",
                "-nostdin",
                "-y",
                "-i",
                inputPath,
                "-ignore_unknown",
                "-map",
                "0:v:0",
                "-map",
                "0:a:0?",
                "-sn",
                "-dn",
                "-c",
                "copy",
                "-movflags",
                "+faststart",
                "-f",
                "mp4",
                outputFile.absolutePath
            )
            ConversionMediaCategory.Audio -> buildList {
                add("-hide_banner")
                add("-nostdin")
                add("-y")
                add("-i")
                add(inputPath)
                add("-map")
                add("0:a:0")
                add("-vn")
                add("-c:a")
                add("copy")
                add("-movflags")
                add("+faststart")
                add("-f")
                add("ipod")
                add(outputFile.absolutePath)
            }
            ConversionMediaCategory.Image -> error("Compatibility engine is not connected for images")
        }
    }

    private suspend fun executeFfmpeg(
        input: ConversionTaskInput,
        arguments: List<String>,
        durationMs: Long?,
        logTail: MutableList<String>,
        inputSourceLabel: String
    ): FfmpegRunResult = suspendCancellableCoroutine { continuation ->
        val resumed = AtomicBoolean(false)
        Log.i(
            TAG,
            "Starting FFmpeg compatibility export category=${input.category} " +
                "target=${input.targetFormat} displayName=${input.displayName} " +
                "durationMs=$durationMs inputSource=$inputSourceLabel " +
                "command=${formatFfmpegArguments(arguments)}"
        )

        val session = FFmpegKit.executeWithArgumentsAsync(
            arguments.toTypedArray(),
            { session ->
                if (resumed.compareAndSet(false, true)) {
                    activeFfmpegSession = null
                    val returnCode = session.returnCode
                    val result = when {
                        ReturnCode.isSuccess(returnCode) -> {
                            Log.i(
                                TAG,
                                "FFmpeg compatibility export completed category=${input.category} " +
                                    "target=${input.targetFormat} displayName=${input.displayName}"
                            )
                            FfmpegRunResult(success = true, cancelled = false)
                        }
                        ReturnCode.isCancel(returnCode) -> {
                            Log.i(
                                TAG,
                                "FFmpeg compatibility export cancelled category=${input.category} " +
                                    "target=${input.targetFormat} displayName=${input.displayName}"
                            )
                            FfmpegRunResult(success = false, cancelled = true)
                        }
                        else -> {
                            val outputTail = ffmpegOutputTail(
                                session.getAllLogsAsString(FFMPEG_LOG_DRAIN_TIMEOUT_MS)
                            ).ifBlank {
                                snapshotFfmpegLogTail(logTail)
                            }
                            Log.e(
                                TAG,
                                "FFmpeg compatibility export failed category=${input.category} " +
                                    "target=${input.targetFormat} displayName=${input.displayName} " +
                                    "inputSource=$inputSourceLabel returnCode=$returnCode " +
                                    "outputTail=$outputTail"
                            )
                            FfmpegRunResult(
                                success = false,
                                cancelled = false,
                                message = compatibilityFailureMessageFor(input, outputTail),
                                outputTail = outputTail
                            )
                        }
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            },
            { log ->
                val message = log.message
                if (message != null) {
                    appendFfmpegLogTail(logTail, message)
                    ffmpegProgressFromMessage(message, durationMs)?.let { progress ->
                        updateCompatibilityProgress(progress)
                    }
                }
            },
            { statistics ->
                val duration = durationMs
                if (duration != null && duration > 0L) {
                    val progress = statistics.time.toFloat() / duration.toFloat()
                    updateCompatibilityProgress(progress)
                }
            }
        )
        activeFfmpegSession = session
        continuation.invokeOnCancellation {
            FFmpegKit.cancel(session.sessionId)
        }
    }

    private fun updateCompatibilityProgress(progress: Float) {
        handler.post {
            if (ConversionTaskStore.isCancelled()) return@post
            ConversionTaskStore.updateProgress(
                taskIndex,
                progress.coerceIn(0f, FFMPEG_MAX_PROGRESS_BEFORE_SAVE)
            )
            updateNotification(
                "Compatibility processing",
                (ConversionTaskStore.aggregateProgress() * 100).toInt()
            )
        }
    }

    private fun appendFfmpegLogTail(logTail: MutableList<String>, message: String) {
        synchronized(logTail) {
            message.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { line ->
                    while (logTail.size >= FFMPEG_LOG_TAIL_LINES) {
                        logTail.removeAt(0)
                    }
                    logTail.add(line.take(FFMPEG_LOG_LINE_LIMIT))
                }
        }
    }

    private fun snapshotFfmpegLogTail(logTail: MutableList<String>): String {
        return synchronized(logTail) {
            logTail.joinToString(separator = "\n")
        }
    }

    private fun ffmpegOutputTail(output: String?): String {
        if (output.isNullOrBlank()) return ""
        val lines = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.take(FFMPEG_LOG_LINE_LIMIT) }
            .toList()
        return lines.takeLast(FFMPEG_LOG_TAIL_LINES).joinToString(separator = "\n")
    }

    private fun ffmpegProgressFromMessage(message: String, durationMs: Long?): Float? {
        val duration = durationMs?.takeIf { it > 0L } ?: return null
        message.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("out_time_ms=") || line.startsWith("out_time_us=")) {
                val rawValue = line.substringAfter('=').toLongOrNull() ?: return@forEach
                val progressTimeMs = rawValue / 1000L
                return progressTimeMs.toFloat() / duration.toFloat()
            }
            if (line.startsWith("out_time=")) {
                val progressTimeMs = parseFfmpegTimestamp(line.substringAfter('='))
                if (progressTimeMs != null) {
                    return progressTimeMs.toFloat() / duration.toFloat()
                }
            }
            FFMPEG_TIME_REGEX.find(line)?.let { match ->
                val progressTimeMs = parseFfmpegTimestamp(match.groupValues[1])
                if (progressTimeMs != null) {
                    return progressTimeMs.toFloat() / duration.toFloat()
                }
            }
        }
        return null
    }

    private fun parseFfmpegTimestamp(value: String): Long? {
        val match = FFMPEG_TIMESTAMP_REGEX.matchEntire(value.trim()) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: return null
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toDoubleOrNull() ?: return null
        return ((hours * 3600L + minutes * 60L) * 1000L + (seconds * 1000.0).toLong())
    }

    private fun compatibilityFailureMessageFor(
        input: ConversionTaskInput,
        outputTail: String = ""
    ): String {
        val normalizedTail = outputTail.lowercase(Locale.US)
        if (
            input.category == ConversionMediaCategory.Audio &&
            (
                normalizedTail.contains("codec not currently supported in container") ||
                    normalizedTail.contains("could not find tag for codec") ||
                    normalizedTail.contains("unknown encoder 'aac'")
            )
        ) {
            return "Compatibility engine needs an AAC audio stream for M4A copy"
        }
        if (
            input.category == ConversionMediaCategory.Video &&
            (
                normalizedTail.contains("codec not currently supported in container") ||
                    normalizedTail.contains("could not find tag for codec")
            )
        ) {
            return "Compatibility engine cannot copy this codec into MP4 yet"
        }
        return when (input.category) {
            ConversionMediaCategory.Video ->
                "Compatibility engine could not remux this file to MP4"
            ConversionMediaCategory.Audio ->
                "Compatibility engine could not extract AAC M4A audio"
            ConversionMediaCategory.Image ->
                "Compatibility engine is not connected for images"
        }
    }

    private fun readDurationMs(uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(this, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
        }.getOrNull().also {
            runCatching { retriever.release() }
        }
    }

    private fun shouldUseCompatibilityEngine(input: ConversionTaskInput): Boolean {
        return when (input.category) {
            ConversionMediaCategory.Video ->
                isLikelyVideoInput(input) && !isLikelyMp4VideoInput(input)
            ConversionMediaCategory.Audio ->
                isLikelyVideoInput(input) && !isLikelyMp4VideoInput(input)
            ConversionMediaCategory.Image -> false
        }
    }

    private fun isLikelyVideoInput(input: ConversionTaskInput): Boolean {
        val mimeType = input.mimeType.orEmpty().lowercase(Locale.US)
        val extension = input.extension.lowercase(Locale.US)
        return mimeType.startsWith("video/") || extension in VIDEO_INPUT_EXTENSIONS
    }

    private fun isLikelyMp4VideoInput(input: ConversionTaskInput): Boolean {
        val mimeType = input.mimeType.orEmpty().lowercase(Locale.US)
        val extension = input.extension.lowercase(Locale.US)
        return extension == "mp4" || mimeType == MIME_TYPE_MP4
    }

    private val ConversionTaskInput.extension: String
        get() = displayName.substringAfterLast('.', missingDelimiterValue = "")

    private fun cancelActiveFfmpegSession() {
        activeFfmpegSession?.sessionId?.let { sessionId ->
            FFmpegKit.cancel(sessionId)
        }
        activeFfmpegSession = null
    }

    private fun formatFfmpegArguments(arguments: List<String>): String {
        return arguments.joinToString(separator = " ") { argument ->
            if (argument.any { it.isWhitespace() }) {
                "\"${argument.replace("\"", "\\\"")}\""
            } else {
                argument
            }
        }
    }

    private fun buildEditedMediaItem(input: ConversionTaskInput): EditedMediaItem {
        val videoEffects = if (input.category == ConversionMediaCategory.Video) {
            videoEffectsFor(input)
        } else {
            emptyList()
        }
        return EditedMediaItem.Builder(MediaItem.fromUri(input.inputUri))
            .setRemoveVideo(input.category == ConversionMediaCategory.Audio)
            .setEffects(Effects(audioProcessorsFor(input), videoEffects))
            .build()
    }

    private fun audioProcessorsFor(input: ConversionTaskInput): List<AudioProcessor> {
        if (input.category != ConversionMediaCategory.Audio) return emptyList()
        return buildList {
            input.audioOptions.sampleRateHz?.let { sampleRateHz ->
                add(
                    SonicAudioProcessor().apply {
                        setOutputSampleRateHz(sampleRateHz)
                    }
                )
            }
            input.audioOptions.channelCount?.let { channelCount ->
                add(channelMixingProcessorFor(channelCount))
            }
        }
    }

    private fun channelMixingProcessorFor(outputChannelCount: Int): ChannelMixingAudioProcessor {
        return ChannelMixingAudioProcessor().apply {
            for (inputChannelCount in MIN_MIXABLE_CHANNEL_COUNT..MAX_MIXABLE_CHANNEL_COUNT) {
                putChannelMixingMatrix(
                    ChannelMixingMatrix.createForConstantPower(
                        inputChannelCount,
                        outputChannelCount
                    )
                )
            }
        }
    }

    private fun videoEffectsFor(input: ConversionTaskInput): List<Effect> {
        return buildList {
            val targetShortSide = input.videoOptions.maxShortSidePixels
            if (targetShortSide != null) {
                val sourceSize = readVideoSize(input.inputUri)
                if (sourceSize == null || sourceSize.shortSide > targetShortSide) {
                    add(
                        Presentation.createForShortSide(targetShortSide)
                            .copyWithUnsetSideRoundedTo(2)
                    )
                }
            }

            input.videoOptions.maxFrameRate
                ?.takeIf { it > 0 }
                ?.let { maxFrameRate ->
                    add(FrameDropEffect.createDefaultFrameDropEffect(maxFrameRate.toFloat()))
                }
        }
    }

    private fun readVideoSize(uri: Uri): VideoSize? {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(this, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
            if (width == null || height == null || width <= 0 || height <= 0) {
                null
            } else {
                VideoSize(width, height)
            }
        }.getOrNull().also {
            runCatching { retriever.release() }
        }
    }

    private fun saveCompletedExport(
        input: ConversionTaskInput,
        tempFile: File
    ) {
        stopProgressPolling()
        activeTransformer = null
        ConversionTaskStore.markSaving(taskIndex)
        updateNotification("Saving", (ConversionTaskStore.aggregateProgress() * 100).toInt())

        copyThread = Thread {
            var outputUri: Uri? = null
            try {
                val outputProfile = outputProfileFor(input)
                    ?: error("Unsupported output format")
                val createdOutputUri = createOutput(
                    input,
                    outputNameFor(input, outputProfile.extension),
                    outputProfile.mimeType
                )
                outputUri = createdOutputUri
                copyFileToOutput(tempFile, createdOutputUri)
                finalizeOutput(input, createdOutputUri, outputProfile.mimeType)
                tempFile.delete()
                handler.post {
                    if (ConversionTaskStore.isCancelled()) {
                        deleteOutputQuietly(createdOutputUri)
                        cancelRun()
                        return@post
                    }
                    activeTempFile = null
                    ConversionTaskStore.markCompleted(taskIndex, createdOutputUri)
                    taskIndex += 1
                    processNextTask()
                }
            } catch (exception: InterruptedException) {
                deleteOutputQuietly(outputUri)
                tempFile.delete()
                handler.post { cancelRun() }
            } catch (exception: Throwable) {
                Log.w(TAG, "Could not save export", exception)
                deleteOutputQuietly(outputUri)
                tempFile.delete()
                handler.post {
                    activeTempFile = null
                    failCurrentTask("Could not save output file")
                }
            }
        }.also { it.start() }
    }

    private fun failureMessageFor(exportException: ExportException): String {
        return when (exportException.errorCode) {
            ExportException.ERROR_CODE_MUXING_TIMEOUT ->
                "Native engine timed out before writing output"
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                "Native engine cannot decode this input on this device"
            ExportException.ERROR_CODE_DECODING_FAILED ->
                "Native engine failed while decoding this input"
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
            ExportException.ERROR_CODE_ENCODING_FAILED ->
                "Native engine cannot encode the selected output"
            ExportException.ERROR_CODE_MUXING_FAILED ->
                "Native engine could not write this MP4/M4A output"
            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "Input file could not be opened"
            ExportException.ERROR_CODE_IO_NO_PERMISSION ->
                "Input file permission was lost"
            else -> "Conversion failed"
        }
    }

    private fun failCurrentTask(message: String) {
        val input = ConversionTaskStore.inputAt(taskIndex)
        Log.e(
            TAG,
            "Task failed index=$taskIndex message=$message " +
                "category=${input?.category} target=${input?.targetFormat} " +
                "displayName=${input?.displayName} mimeType=${input?.mimeType}"
        )
        stopProgressPolling()
        activeTransformer = null
        activeFfmpegSession = null
        activeTempFile?.delete()
        activeTempFile = null
        ConversionTaskStore.markFailed(taskIndex, message)
        updateNotification(message, (ConversionTaskStore.aggregateProgress() * 100).toInt())
        taskIndex += 1
        handler.post { processNextTask() }
    }

    private fun finishRun() {
        stopProgressPolling()
        activeTransformer = null
        activeFfmpegSession = null
        activeTempFile = null
        ConversionTaskStore.markRunFinished()
        updateNotification(
            ConversionTaskStore.summaryMessage.value ?: "Conversion complete",
            (ConversionTaskStore.aggregateProgress() * 100).toInt()
        )
        detachForeground()
        stopSelf()
    }

    private fun cancelRun() {
        handler.removeCallbacksAndMessages(null)
        stopProgressPolling()
        activeTransformer?.cancel()
        activeTransformer = null
        cancelActiveFfmpegSession()
        copyThread?.interrupt()
        activeTempFile?.delete()
        activeTempFile = null
        ConversionTaskStore.cancelAll()
        updateNotification("Cancelled", (ConversionTaskStore.aggregateProgress() * 100).toInt())
        detachForeground()
        stopSelf()
    }

    private fun createTempFileFor(input: ConversionTaskInput, extension: String): File {
        val cacheRoot = externalCacheDir ?: cacheDir
        val tempDirectory = File(cacheRoot, "exports").apply { mkdirs() }
        return File(tempDirectory, "${input.fileId}.$extension").apply {
            if (exists()) delete()
        }
    }

    private fun createOutput(
        input: ConversionTaskInput,
        displayName: String,
        mimeType: String
    ): Uri {
        return when (val destination = input.outputDestination) {
            OutputDestination.DefaultPublicDirectory -> createDefaultOutput(
                input,
                displayName,
                mimeType
            )
            is OutputDestination.CustomDirectory -> createOutputDocument(
                destination.uri,
                displayName,
                mimeType
            )
        }
    }

    private fun createDefaultOutput(
        input: ConversionTaskInput,
        displayName: String,
        mimeType: String
    ): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return createLegacyDefaultOutput(input, displayName)
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${defaultPublicDirectoryFor(input)}/$DEFAULT_OUTPUT_DIRECTORY"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return contentResolver.insert(defaultCollectionFor(input), values)
            ?: error("Could not create output file")
    }

    @Suppress("DEPRECATION")
    private fun createLegacyDefaultOutput(
        input: ConversionTaskInput,
        displayName: String
    ): Uri {
        val publicDirectory = Environment.getExternalStoragePublicDirectory(
            defaultPublicDirectoryFor(input)
        )
        val outputDirectory = File(publicDirectory, DEFAULT_OUTPUT_DIRECTORY)
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            error("Could not create output directory")
        }
        return Uri.fromFile(File(outputDirectory, displayName))
    }

    private fun defaultCollectionFor(input: ConversionTaskInput): Uri {
        return when (input.category) {
            ConversionMediaCategory.Audio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            ConversionMediaCategory.Image -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ConversionMediaCategory.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun defaultPublicDirectoryFor(input: ConversionTaskInput): String {
        return when (input.category) {
            ConversionMediaCategory.Audio -> Environment.DIRECTORY_MUSIC
            ConversionMediaCategory.Image -> Environment.DIRECTORY_PICTURES
            ConversionMediaCategory.Video -> Environment.DIRECTORY_MOVIES
        }
    }

    private fun createOutputDocument(
        outputDirectoryUri: Uri,
        displayName: String,
        mimeType: String
    ): Uri {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(outputDirectoryUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(
            outputDirectoryUri,
            treeDocumentId
        )
        return DocumentsContract.createDocument(
            contentResolver,
            parentUri,
            mimeType,
            displayName
        ) ?: error("Could not create output file")
    }

    private fun copyFileToOutput(source: File, outputUri: Uri) {
        if (outputUri.scheme == URI_SCHEME_FILE) {
            val path = outputUri.path ?: error("Could not open output file")
            copyFileToFile(source, File(path))
            return
        }
        copyFileToUri(source, outputUri)
    }

    private fun copyFileToUri(source: File, outputUri: Uri) {
        contentResolver.openOutputStream(outputUri, "w")?.use { output ->
            source.inputStream().use { input ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    if (Thread.currentThread().isInterrupted || ConversionTaskStore.isCancelled()) {
                        throw InterruptedException()
                    }
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        } ?: error("Could not open output file")
    }

    private fun copyFileToFile(source: File, outputFile: File) {
        outputFile.outputStream().use { output ->
            source.inputStream().use { input ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                while (true) {
                    if (Thread.currentThread().isInterrupted || ConversionTaskStore.isCancelled()) {
                        throw InterruptedException()
                    }
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
    }

    private fun finalizeOutput(
        input: ConversionTaskInput,
        outputUri: Uri,
        mimeType: String
    ) {
        if (input.outputDestination != OutputDestination.DefaultPublicDirectory) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            contentResolver.update(outputUri, values, null, null)
            return
        }

        val path = outputUri.path ?: return
        MediaScannerConnection.scanFile(
            this,
            arrayOf(path),
            arrayOf(mimeType),
            null
        )
    }

    private fun deleteOutputQuietly(uri: Uri?) {
        if (uri == null) return
        if (uri.scheme == URI_SCHEME_FILE) {
            runCatching { File(uri.path.orEmpty()).delete() }
            return
        }
        runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
        runCatching { contentResolver.delete(uri, null, null) }
    }

    private fun outputProfileFor(input: ConversionTaskInput): OutputProfile? {
        return when (input.category) {
            ConversionMediaCategory.Video -> {
                if (input.targetFormat.equals("MP4", ignoreCase = true)) {
                    OutputProfile(extension = "mp4", mimeType = MIME_TYPE_MP4)
                } else {
                    null
                }
            }
            ConversionMediaCategory.Audio -> {
                if (
                    input.targetFormat.contains("M4A", ignoreCase = true) ||
                    input.targetFormat.contains("AAC", ignoreCase = true)
                ) {
                    OutputProfile(extension = "m4a", mimeType = MIME_TYPE_M4A)
                } else {
                    null
                }
            }
            ConversionMediaCategory.Image -> {
                when {
                    input.targetFormat.equals("JPG", ignoreCase = true) ||
                        input.targetFormat.equals("JPEG", ignoreCase = true) ->
                        OutputProfile(extension = "jpg", mimeType = MIME_TYPE_JPEG)
                    input.targetFormat.equals("PNG", ignoreCase = true) ->
                        OutputProfile(extension = "png", mimeType = MIME_TYPE_PNG)
                    input.targetFormat.equals("WEBP", ignoreCase = true) ->
                        OutputProfile(extension = "webp", mimeType = MIME_TYPE_WEBP)
                    else -> null
                }
            }
        }
    }

    private data class OutputProfile(
        val extension: String,
        val mimeType: String
    )

    private data class FfmpegRunResult(
        val success: Boolean,
        val cancelled: Boolean,
        val message: String? = null,
        val outputTail: String? = null
    )

    private data class FfmpegInputSource(
        val label: String,
        val path: String,
        val descriptor: ParcelFileDescriptor?
    ) {
        fun close() {
            descriptor?.close()
        }
    }

    private data class VideoSize(
        val width: Int,
        val height: Int
    ) {
        val shortSide: Int = minOf(width, height)
    }

    private data class ImageDecodeSize(
        val width: Int,
        val height: Int
    )

    private fun outputNameFor(input: ConversionTaskInput, extension: String): String {
        val baseName = input.displayName
            .substringBeforeLast('.', input.displayName)
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "ZenConverter" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val shortId = input.fileId.take(8)
        return "${baseName}_converted_${timestamp}_$shortId.$extension"
    }

    private fun updateNotification(title: String, progress: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, progress.coerceIn(0, 100)))
    }

    private fun buildNotification(title: String, progress: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancelIntent = Intent(this, ConversionService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_zenconverter)
            .setContentTitle("ZenConverter")
            .setContentText(title)
            .setContentIntent(openPendingIntent)
            .setOngoing(ConversionTaskStore.isRunning.value)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .addAction(R.drawable.ic_stat_zenconverter, "Cancel", cancelPendingIntent)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun detachForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            stopForeground(false)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Conversion progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows local conversion progress"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ConversionService"
        private const val CHANNEL_ID = "conversion_progress"
        private const val NOTIFICATION_ID = 1001
        private const val PROGRESS_DELAY_MS = 500L
        private const val MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS = 60_000L
        private const val COPY_BUFFER_SIZE = 1024 * 1024
        private const val PROGRESS_BEFORE_SAVE = 0.98f
        private const val MAX_IMAGE_DECODE_PIXELS = 64_000_000L
        private const val FFMPEG_MAX_PROGRESS_BEFORE_SAVE = 0.98f
        private const val FFMPEG_LOG_DRAIN_TIMEOUT_MS = 1_000
        private const val FFMPEG_LOG_TAIL_LINES = 16
        private const val FFMPEG_LOG_LINE_LIMIT = 600
        private const val MIME_TYPE_MP4 = "video/mp4"
        private const val MIME_TYPE_M4A = "audio/mp4"
        private const val MIME_TYPE_JPEG = "image/jpeg"
        private const val MIME_TYPE_PNG = "image/png"
        private const val MIME_TYPE_WEBP = "image/webp"
        private const val DEFAULT_OUTPUT_DIRECTORY = "ZenConverter"
        private const val URI_SCHEME_FILE = "file"
        private const val MIN_MIXABLE_CHANNEL_COUNT = 1
        private const val MAX_MIXABLE_CHANNEL_COUNT = 6
        private const val ACTION_START = "org.zenconverter.app.conversion.START"
        private const val ACTION_CANCEL = "org.zenconverter.app.conversion.CANCEL"
        private val VIDEO_INPUT_EXTENSIONS = setOf(
            "mp4",
            "m4v",
            "mov",
            "mkv",
            "webm",
            "avi",
            "3gp",
            "3gpp",
            "3g2",
            "ts",
            "mts",
            "m2ts",
            "mpg",
            "mpeg",
            "vob",
            "flv",
            "ogv"
        )
        private val FFMPEG_TIME_REGEX = Regex("""time=(\d+:\d{2}:\d{2}(?:\.\d+)?)""")
        private val FFMPEG_TIMESTAMP_REGEX = Regex("""(\d+):(\d{2}):(\d{2}(?:\.\d+)?)""")

        fun start(context: Context) {
            val intent = Intent(context, ConversionService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, ConversionService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
