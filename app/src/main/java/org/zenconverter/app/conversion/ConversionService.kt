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
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
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
import android.os.ext.SdkExtensions
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.zenconverter.app.MainActivity
import org.zenconverter.app.R
import org.zenconverter.app.office.Office2PdfNative
import org.zenconverter.app.office.Office2PdfUnavailableException
import org.zenconverter.app.office.Office2PdfUnsupportedAbiException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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
    private var ffmpegKitReady = false
    private var ffmpegKitLoadFailure: Throwable? = null
    private var pdfBoxReady = false
    private val ffmpegEncoderAvailability = mutableMapOf<String, Boolean>()

    override fun onCreate() {
        super.onCreate()
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
            failCurrentTask("Only connected video, audio, image, PDF, and document targets can run")
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
                        input.category == ConversionMediaCategory.Pdf -> "NativePdf"
                        input.category == ConversionMediaCategory.Document -> "Office2Pdf"
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

        if (input.category == ConversionMediaCategory.Pdf) {
            if (
                outputProfile.extension.equals("pdf", ignoreCase = true) ||
                outputProfile.extension.equals("txt", ignoreCase = true)
            ) {
                startPdfDocumentExport(input, tempFile, outputProfile)
            } else {
                startPdfImageExport(input, tempFile, outputProfile)
            }
            return
        }

        if (input.category == ConversionMediaCategory.Document) {
            startOfficeDocumentExport(input, tempFile)
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
                    when (input.gifFrameMode) {
                        GifFrameExportMode.FramesAsImages -> ImageExportResult.FolderFiles(
                            files = writeGifFramesToImageFiles(input, tempFile, outputProfile),
                            outputProfile = outputProfile,
                            folderName = outputFolderNameForFrames(input)
                        )
                        GifFrameExportMode.FramesAsPdfFiles -> ImageExportResult.FolderFiles(
                            files = writeGifFramesToPdfFiles(input, tempFile),
                            outputProfile = outputProfile,
                            folderName = outputFolderNameForFrames(input)
                        )
                        GifFrameExportMode.FirstFrame,
                        GifFrameExportMode.FramesAsSinglePdf -> {
                            writeImageExport(input, tempFile, outputProfile)
                            ImageExportResult.SingleFile(tempFile)
                        }
                    }
                }
            }.onSuccess { result ->
                if (ConversionTaskStore.isCancelled()) {
                    result.deleteTempFiles()
                    cancelRun()
                    return@onSuccess
                }
                ConversionTaskStore.updateProgress(taskIndex, PROGRESS_BEFORE_SAVE)
                when (result) {
                    is ImageExportResult.SingleFile -> saveCompletedExport(input, result.file)
                    is ImageExportResult.FolderFiles -> saveCompletedExportFilesInFolder(
                        input = input,
                        tempFiles = result.files,
                        outputProfile = result.outputProfile,
                        folderName = result.folderName
                    )
                }
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

    private fun startPdfImageExport(
        input: ConversionTaskInput,
        firstTempFile: File,
        outputProfile: OutputProfile
    ) {
        serviceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    writePdfToImageFiles(input, firstTempFile, outputProfile)
                }
            }.onSuccess { tempFiles ->
                if (ConversionTaskStore.isCancelled()) {
                    tempFiles.forEach { it.delete() }
                    cancelRun()
                    return@onSuccess
                }
                ConversionTaskStore.updateProgress(taskIndex, PROGRESS_BEFORE_SAVE)
                saveCompletedExportFiles(input, tempFiles, outputProfile)
            }.onFailure { exception ->
                firstTempFile.delete()
                if (exception is CancellationException) {
                    return@onFailure
                }
                Log.w(TAG, "Could not run PDF image export", exception)
                failCurrentTask(pdfFailureMessageFor(exception))
            }
        }
    }

    private fun startPdfDocumentExport(
        input: ConversionTaskInput,
        tempFile: File,
        outputProfile: OutputProfile
    ) {
        serviceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    writePdfDocumentExport(input, tempFile, outputProfile)
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
                Log.w(TAG, "Could not run PDF document export", exception)
                failCurrentTask(pdfDocumentFailureMessageFor(exception, outputProfile))
            }
        }
    }

    private fun startOfficeDocumentExport(
        input: ConversionTaskInput,
        tempFile: File
    ) {
        serviceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    writeOfficeDocumentExport(input, tempFile)
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
                Log.w(TAG, "Could not run Office document export", exception)
                failCurrentTask(officeFailureMessageFor(exception))
            }
        }
    }

    private fun writeOfficeDocumentExport(
        input: ConversionTaskInput,
        outputFile: File
    ) {
        val extension = officeInputExtensionFor(input)
            ?: error("Unsupported Office document")

        throwIfConversionCancelled()
        val inputBytes = readOfficeInputBytes(input)
        updateImageProgress(0.25f)

        throwIfConversionCancelled()
        val pdfBytes = Office2PdfNative.convert(this, inputBytes, extension)
        throwIfConversionCancelled()

        if (!looksLikePdf(pdfBytes)) {
            error("Office engine did not return a PDF")
        }

        outputFile.outputStream().use { output ->
            output.write(pdfBytes)
            output.flush()
        }
        throwIfConversionCancelled()
        updateImageProgress(0.95f)
    }

    private fun writePdfDocumentExport(
        input: ConversionTaskInput,
        outputFile: File,
        outputProfile: OutputProfile
    ) {
        when (outputProfile.extension.lowercase(Locale.US)) {
            "pdf" -> writeMergedPdf(input, outputFile)
            "txt" -> writePdfTextFile(input, outputFile)
            else -> error("PDF conversion failed")
        }
    }

    private fun writeMergedPdf(
        input: ConversionTaskInput,
        outputFile: File
    ) {
        val inputUris = input.inputUris.ifEmpty { listOf(input.inputUri) }
        if (inputUris.size < 2) error("Select at least two PDFs to merge")

        throwIfConversionCancelled()
        ensurePdfBoxReady()
        val cachedInputs = cachePdfInputsForPdfBox(input, inputUris)
        val destination = PDDocument(MemoryUsageSetting.setupTempFileOnly())
        val merger = PDFMergerUtility()

        try {
            cachedInputs.files.forEachIndexed { index, cachedPdf ->
                throwIfConversionCancelled()
                val source = loadPdfBoxDocument(
                    cachedPdf,
                    input.pdfPasswordAt(index)
                )
                try {
                    if (source.numberOfPages <= 0) error("PDF has no pages")
                    merger.appendDocument(destination, source)
                    throwIfConversionCancelled()
                } finally {
                    runCatching { source.close() }
                }
                updateImageProgress(
                    progressForIndexedWork(index, cachedInputs.files.size, 0.08f, 0.85f)
                )
            }

            throwIfConversionCancelled()
            destination.save(outputFile)
            throwIfConversionCancelled()
            updateImageProgress(0.95f)
        } catch (throwable: Throwable) {
            outputFile.delete()
            throw throwable
        } finally {
            runCatching { destination.close() }
            cachedInputs.delete()
        }
    }

    private fun writePdfTextFile(
        input: ConversionTaskInput,
        outputFile: File
    ) {
        val inputUris = input.inputUris.ifEmpty { listOf(input.inputUri) }
        if (inputUris.size != 1) error("PDF text extraction failed")

        throwIfConversionCancelled()
        ensurePdfBoxReady()
        val cachedInputs = cachePdfInputsForPdfBox(input, inputUris)
        var document: PDDocument? = null

        try {
            document = loadPdfBoxDocument(cachedInputs.files.first(), input.pdfPasswordAt(0))
            val pageCount = document.numberOfPages
            if (pageCount <= 0) error("PDF has no pages")

            val stripper = PDFTextStripper()
            var hasSelectableText = false

            outputFile.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                for (pageIndex in 0 until pageCount) {
                    throwIfConversionCancelled()
                    stripper.startPage = pageIndex + 1
                    stripper.endPage = pageIndex + 1
                    val pageText = stripper.getText(document).trimEnd()
                    throwIfConversionCancelled()

                    if (pageText.isNotBlank()) {
                        hasSelectableText = true
                    }

                    writer.write("--- Page ${pageNumberForTextOutput(pageIndex)} ---")
                    writer.newLine()
                    if (pageText.isNotEmpty()) {
                        writer.write(pageText)
                        writer.newLine()
                    }
                    writer.newLine()
                    writer.flush()
                    throwIfConversionCancelled()

                    updateImageProgress(progressForIndexedWork(pageIndex, pageCount, 0.08f, 0.92f))
                }
            }

            throwIfConversionCancelled()
            if (!hasSelectableText) {
                error("PDF has no selectable text; OCR is not included")
            }
            updateImageProgress(0.95f)
        } catch (throwable: Throwable) {
            outputFile.delete()
            throw throwable
        } finally {
            runCatching { document?.close() }
            cachedInputs.delete()
        }
    }

    private fun writePdfToImageFiles(
        input: ConversionTaskInput,
        firstTempFile: File,
        outputProfile: OutputProfile
    ): List<File> {
        throwIfConversionCancelled()
        val rendererSource = openPdfRendererSource(input)
        val tempFiles = mutableListOf<File>()
        var reusableBitmap: Bitmap? = null
        try {
            val renderer = rendererSource.renderer
            val pageCount = renderer.pageCount
            if (pageCount <= 0) error("PDF has no pages")

            val pageSizes = mutableListOf<PdfPageSize>()
            for (pageIndex in 0 until pageCount) {
                throwIfConversionCancelled()
                val page = renderer.openPage(pageIndex)
                try {
                    pageSizes.add(PdfPageSize(width = page.width, height = page.height))
                } finally {
                    page.close()
                }
                updateImageProgress(progressForIndexedWork(pageIndex, pageCount, 0.03f, 0.18f))
            }

            val renderSize = pdfRenderBitmapSizeFor(pageSizes, input.pdfOptions.renderQuality)
            reusableBitmap = Bitmap.createBitmap(
                renderSize.width,
                renderSize.height,
                Bitmap.Config.ARGB_8888
            ).apply {
                setHasAlpha(false)
            }

            for (pageIndex in 0 until pageCount) {
                throwIfConversionCancelled()
                val page = renderer.openPage(pageIndex)
                try {
                    reusableBitmap.eraseColor(Color.WHITE)
                    page.render(
                        reusableBitmap,
                        null,
                        pdfRenderMatrixFor(page, renderSize),
                        PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                    )
                    throwIfConversionCancelled()
                    val outputFile = if (pageIndex == 0) {
                        firstTempFile.apply { if (exists()) delete() }
                    } else {
                        createTempFileForPdfPage(input, outputProfile.extension, pageIndex)
                    }
                    throwIfConversionCancelled()
                    tempFiles.add(outputFile)
                    writeBitmapImageFile(
                        reusableBitmap,
                        outputFile,
                        outputProfile,
                        input.imageOptions.quality,
                        shouldUseWebpLossless(outputProfile, input.imageOptions)
                    )
                    throwIfConversionCancelled()
                } finally {
                    page.close()
                }
                updateImageProgress(progressForIndexedWork(pageIndex, pageCount, 0.20f, 0.95f))
            }

            return tempFiles
        } catch (throwable: Throwable) {
            tempFiles.forEach { it.delete() }
            throw throwable
        } finally {
            reusableBitmap?.recycle()
            rendererSource.close()
        }
    }

    private fun openPdfRendererSource(input: ConversionTaskInput): PdfRendererSource {
        val password = input.pdfPasswordAt(0)
        return try {
            PdfRendererSource(renderer = openPdfRenderer(input.inputUri, password))
        } catch (exception: IllegalArgumentException) {
            Log.i(TAG, "PDF input descriptor is not seekable; trying SafeCache", exception)
            val cachedPdf = cachePdfInputForRenderer(input)
            try {
                PdfRendererSource(
                    renderer = openPdfRenderer(Uri.fromFile(cachedPdf), password),
                    cacheFile = cachedPdf
                )
            } catch (throwable: Throwable) {
                cachedPdf.delete()
                throw throwable
            }
        }
    }

    private fun openPdfRenderer(uri: Uri, password: String?): PdfRenderer {
        val descriptor = if (uri.scheme == URI_SCHEME_FILE) {
            ParcelFileDescriptor.open(
                File(uri.path ?: throw IOException("Could not open PDF")),
                ParcelFileDescriptor.MODE_READ_ONLY
            )
        } else {
            contentResolver.openFileDescriptor(uri, "r")
        } ?: throw IOException("Could not open PDF")

        return try {
            if (password != null) {
                if (!supportsPdfPassword()) {
                    throw SecurityException("Password-protected PDFs need Android 15 or PDF extension 13")
                }
                PdfRenderer(
                    descriptor,
                    LoadParams.Builder()
                        .setPassword(password)
                        .build()
                )
            } else {
                PdfRenderer(descriptor)
            }
        } catch (throwable: Throwable) {
            runCatching { descriptor.close() }
            throw throwable
        }
    }

    private fun cachePdfInputForRenderer(input: ConversionTaskInput): File {
        throwIfConversionCancelled()
        val cacheRoot = externalCacheDir ?: cacheDir
        val cacheDirectory = File(cacheRoot, "pdf-safe-cache").apply { mkdirs() }
        val inputSize = queryOpenableSize(input.inputUri)
        if (inputSize != null) {
            val requiredBytes = inputSize + PDF_CACHE_HEADROOM_BYTES
            if (cacheDirectory.usableSpace < requiredBytes) {
                error("Not enough cache space for this PDF")
            }
        } else if (cacheDirectory.usableSpace < PDF_UNKNOWN_CACHE_MIN_FREE_BYTES) {
            error("Not enough cache space for this PDF")
        }

        val cachedPdf = File(cacheDirectory, "${input.fileId}.pdf").apply {
            if (exists()) delete()
        }
        try {
            contentResolver.openInputStream(input.inputUri)?.use { source ->
                cachedPdf.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        throwIfConversionCancelled()
                        val read = source.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            } ?: error("Could not open PDF")
            return cachedPdf
        } catch (throwable: Throwable) {
            cachedPdf.delete()
            throw throwable
        }
    }

    private fun cachePdfInputsForPdfBox(
        input: ConversionTaskInput,
        inputUris: List<Uri>
    ): PdfBoxCachedInputs {
        throwIfConversionCancelled()
        val cacheRoot = externalCacheDir ?: cacheDir
        val cacheDirectory = File(cacheRoot, "pdfbox-cache/${input.fileId}").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        val cachedFiles = mutableListOf<File>()

        try {
            inputUris.forEachIndexed { index, uri ->
                throwIfConversionCancelled()
                val inputSize = queryOpenableSize(uri)
                if (inputSize != null) {
                    val requiredBytes = inputSize + PDF_CACHE_HEADROOM_BYTES
                    if (cacheDirectory.usableSpace < requiredBytes) {
                        error("Not enough cache space for this PDF")
                    }
                } else if (cacheDirectory.usableSpace < PDF_UNKNOWN_CACHE_MIN_FREE_BYTES) {
                    error("Not enough cache space for this PDF")
                }

                val cachedPdf = File(
                    cacheDirectory,
                    "input_${(index + 1).toString().padStart(3, '0')}.pdf"
                ).apply {
                    if (exists()) delete()
                }
                copyPdfInputToCache(uri, cachedPdf)
                cachedFiles.add(cachedPdf)
                updateImageProgress(progressForIndexedWork(index, inputUris.size, 0.01f, 0.07f))
            }

            return PdfBoxCachedInputs(cacheDirectory, cachedFiles)
        } catch (throwable: Throwable) {
            cacheDirectory.deleteRecursively()
            throw throwable
        }
    }

    private fun copyPdfInputToCache(uri: Uri, cachedPdf: File) {
        try {
            contentResolver.openInputStream(uri)?.use { source ->
                cachedPdf.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        throwIfConversionCancelled()
                        val read = source.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            } ?: error("Could not open PDF")
            throwIfConversionCancelled()
        } catch (throwable: Throwable) {
            cachedPdf.delete()
            throw throwable
        }
    }

    private fun loadPdfBoxDocument(file: File, password: String?): PDDocument {
        throwIfConversionCancelled()
        return if (password.isNullOrEmpty()) {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
        } else {
            PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly())
        }
    }

    private fun queryOpenableSize(uri: Uri): Long? {
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index < 0 || cursor.isNull(index)) return@use null
                cursor.getLong(index).takeIf { it >= 0L }
            }
        }.getOrNull()
    }

    private fun supportsPdfPassword(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) return true
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= PDF_PASSWORD_EXTENSION
    }

    @Synchronized
    private fun ensurePdfBoxReady() {
        if (pdfBoxReady) return
        PDFBoxResourceLoader.init(applicationContext)
        pdfBoxReady = true
    }

    private fun pdfRenderBitmapSizeFor(
        pageSizes: List<PdfPageSize>,
        quality: PdfRenderQuality
    ): PdfBitmapSize {
        val profile = pdfRenderProfileFor(quality)
        val maxPageWidth = pageSizes.maxOf { it.width }.coerceAtLeast(1)
        val maxPageHeight = pageSizes.maxOf { it.height }.coerceAtLeast(1)
        val scale = profile.dpi / PDF_POINTS_PER_INCH
        var width = (maxPageWidth * scale).roundToInt().coerceAtLeast(1)
        var height = (maxPageHeight * scale).roundToInt().coerceAtLeast(1)

        val longSideScale = profile.maxLongSidePixels.toFloat() / maxOf(width, height).toFloat()
        if (longSideScale < 1f) {
            width = (width * longSideScale).roundToInt().coerceAtLeast(1)
            height = (height * longSideScale).roundToInt().coerceAtLeast(1)
        }

        val pixels = width.toLong() * height.toLong()
        if (pixels > profile.maxPixels) {
            val pixelScale = kotlin.math.sqrt(profile.maxPixels.toDouble() / pixels.toDouble())
            width = (width * pixelScale).toInt().coerceAtLeast(1)
            height = (height * pixelScale).toInt().coerceAtLeast(1)
        }
        return PdfBitmapSize(width = width, height = height)
    }

    private fun pdfRenderProfileFor(quality: PdfRenderQuality): PdfRenderProfile {
        return when (quality) {
            PdfRenderQuality.LowResolution -> PdfRenderProfile(
                dpi = 96f,
                maxLongSidePixels = 1600,
                maxPixels = 6_000_000L
            )
            PdfRenderQuality.HighDetail -> PdfRenderProfile(
                dpi = 288f,
                maxLongSidePixels = 4096,
                maxPixels = 32_000_000L
            )
            PdfRenderQuality.Balanced -> PdfRenderProfile(
                dpi = 144f,
                maxLongSidePixels = 2400,
                maxPixels = 16_000_000L
            )
        }
    }

    private fun pdfRenderMatrixFor(
        page: PdfRenderer.Page,
        renderSize: PdfBitmapSize
    ): Matrix {
        val targetRect = centeredFitRect(
            sourceWidth = page.width,
            sourceHeight = page.height,
            targetWidth = renderSize.width,
            targetHeight = renderSize.height
        )
        val scale = minOf(
            targetRect.width() / page.width.toFloat(),
            targetRect.height() / page.height.toFloat()
        )
        return Matrix().apply {
            postScale(scale, scale)
            postTranslate(targetRect.left, targetRect.top)
        }
    }

    private suspend fun writeImageExport(
        input: ConversionTaskInput,
        outputFile: File,
        outputProfile: OutputProfile
    ) {
        if (outputProfile.extension.equals("pdf", ignoreCase = true)) {
            writeImagesToPdf(input, outputFile)
            return
        }

        throwIfConversionCancelled()
        updateImageProgress(0.15f)

        val decodedBitmap = decodeImageBitmap(
            input.inputUri,
            maxLongSidePixels = null
        ) ?: error("Image engine could not decode this input")
        throwIfConversionCancelled()
        updateImageProgress(0.55f)

        if (outputProfile.extension.equals("ico", ignoreCase = true)) {
            try {
                writeIcoImageFile(decodedBitmap, outputFile)
                throwIfConversionCancelled()
                updateImageProgress(0.95f)
            } finally {
                decodedBitmap.recycle()
            }
            return
        }

        val bitmapForOutput = bitmapForImageOutput(
            decodedBitmap,
            outputProfile.extension,
            flattenTransparency = false
        )
        val useWebpLossless = shouldUseWebpLossless(outputProfile, input.imageOptions)
        val compressFormat = imageCompressFormatFor(outputProfile.extension, useWebpLossless)
            ?: error("Image engine could not write this output")
        val quality = imageQualityFor(
            outputProfile.extension,
            input.imageOptions.quality,
            useWebpLossless
        )

        try {
            throwIfConversionCancelled()
            outputFile.outputStream().use { output ->
                if (!bitmapForOutput.compress(compressFormat, quality, output)) {
                    error("Image engine could not write this output")
                }
                output.flush()
            }
            throwIfConversionCancelled()
            updateImageProgress(0.95f)
        } finally {
            if (bitmapForOutput !== decodedBitmap) {
                bitmapForOutput.recycle()
            }
            decodedBitmap.recycle()
        }
    }

    private suspend fun writeGifFramesToImageFiles(
        input: ConversionTaskInput,
        firstTempFile: File,
        outputProfile: OutputProfile
    ): List<File> {
        throwIfConversionCancelled()
        updateImageProgress(0.05f)
        val extraction = extractGifFrames(input, input.inputUri)
        val outputFiles = mutableListOf<File>()
        try {
            val frameCount = extraction.frameCount
            forEachGifFrameBitmap(extraction, GIF_FRAME_MAX_PIXELS) { index, bitmap ->
                throwIfConversionCancelled()
                val outputFile = if (index == 0) {
                    firstTempFile.apply { if (exists()) delete() }
                } else {
                    createTempFileForGifFrameOutput(input, outputProfile.extension, index)
                }
                outputFiles.add(outputFile)

                writeFrameBitmapToImageFile(bitmap, outputFile, outputProfile, input.imageOptions)
                updateImageProgress(progressForIndexedWork(index, frameCount, 0.25f, 0.95f))
            }
            return outputFiles
        } catch (throwable: Throwable) {
            outputFiles.forEach { it.delete() }
            throw throwable
        } finally {
            extraction.delete()
        }
    }

    private suspend fun writeGifFramesToPdfFiles(
        input: ConversionTaskInput,
        firstTempFile: File
    ): List<File> {
        throwIfConversionCancelled()
        updateImageProgress(0.05f)
        val extraction = extractGifFrames(input, input.inputUri)
        val outputFiles = mutableListOf<File>()
        try {
            val frameCount = extraction.frameCount
            forEachGifFrameBitmap(extraction, PDF_IMAGE_MAX_PIXELS) { index, bitmap ->
                throwIfConversionCancelled()
                val outputFile = if (index == 0) {
                    firstTempFile.apply { if (exists()) delete() }
                } else {
                    createTempFileForGifFrameOutput(input, "pdf", index)
                }
                outputFiles.add(outputFile)

                writeSingleBitmapPdf(bitmap, input, outputFile)
                updateImageProgress(progressForIndexedWork(index, frameCount, 0.25f, 0.95f))
            }
            return outputFiles
        } catch (throwable: Throwable) {
            outputFiles.forEach { it.delete() }
            throw throwable
        } finally {
            extraction.delete()
        }
    }

    private fun writeFrameBitmapToImageFile(
        bitmap: Bitmap,
        outputFile: File,
        outputProfile: OutputProfile,
        imageOptions: ImageExportOptions
    ) {
        if (outputProfile.extension.equals("ico", ignoreCase = true)) {
            writeIcoImageFile(bitmap, outputFile)
            return
        }
        writeBitmapImageFile(
            bitmap = bitmap,
            outputFile = outputFile,
            outputProfile = outputProfile,
            requestedQuality = imageOptions.quality,
            useWebpLossless = shouldUseWebpLossless(outputProfile, imageOptions)
        )
    }

    private fun writeBitmapImageFile(
        bitmap: Bitmap,
        outputFile: File,
        outputProfile: OutputProfile,
        requestedQuality: Int,
        useWebpLossless: Boolean = false
    ) {
        val bitmapForOutput = bitmapForImageOutput(
            bitmap,
            outputProfile.extension,
            flattenTransparency = false
        )
        val compressFormat = imageCompressFormatFor(outputProfile.extension, useWebpLossless)
            ?: error("Image engine could not write this output")
        val quality = imageQualityFor(outputProfile.extension, requestedQuality, useWebpLossless)

        try {
            throwIfConversionCancelled()
            outputFile.outputStream().use { output ->
                if (!bitmapForOutput.compress(compressFormat, quality, output)) {
                    error("Image engine could not write this output")
                }
                output.flush()
            }
            throwIfConversionCancelled()
        } finally {
            if (bitmapForOutput !== bitmap) {
                bitmapForOutput.recycle()
            }
        }
    }

    private fun writeIcoImageFile(
        sourceBitmap: Bitmap,
        outputFile: File
    ) {
        val iconImages = ICO_IMAGE_SIZES.map { size ->
            IcoPngImage(size = size, pngBytes = pngBytesForIcoSize(sourceBitmap, size))
        }

        outputFile.outputStream().use { output ->
            writeLittleEndianShort(output, 0)
            writeLittleEndianShort(output, 1)
            writeLittleEndianShort(output, iconImages.size)

            var imageOffset = ICO_HEADER_BYTES + ICO_DIRECTORY_ENTRY_BYTES * iconImages.size
            iconImages.forEach { image ->
                output.write(if (image.size >= ICO_MAX_DIRECTORY_SIZE) 0 else image.size)
                output.write(if (image.size >= ICO_MAX_DIRECTORY_SIZE) 0 else image.size)
                output.write(0)
                output.write(0)
                writeLittleEndianShort(output, 1)
                writeLittleEndianShort(output, ICO_BITS_PER_PIXEL)
                writeLittleEndianInt(output, image.pngBytes.size)
                writeLittleEndianInt(output, imageOffset)
                imageOffset += image.pngBytes.size
            }

            iconImages.forEach { image ->
                output.write(image.pngBytes)
            }
            output.flush()
        }
    }

    private fun pngBytesForIcoSize(sourceBitmap: Bitmap, iconSize: Int): ByteArray {
        val iconBitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        try {
            val targetRect = centeredFitRect(
                sourceWidth = sourceBitmap.width,
                sourceHeight = sourceBitmap.height,
                targetWidth = iconSize,
                targetHeight = iconSize
            )
            Canvas(iconBitmap).drawBitmap(sourceBitmap, null, targetRect, ICON_BITMAP_PAINT)
            return ByteArrayOutputStream().use { output ->
                if (!iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    error("Image engine could not write this output")
                }
                output.toByteArray()
            }
        } finally {
            iconBitmap.recycle()
        }
    }

    private fun writeLittleEndianShort(output: OutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
    }

    private fun writeLittleEndianInt(output: OutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
        output.write((value ushr 16) and 0xff)
        output.write((value ushr 24) and 0xff)
    }

    private suspend fun writeImagesToPdf(
        input: ConversionTaskInput,
        outputFile: File
    ) {
        val inputUris = input.inputUris.ifEmpty { listOf(input.inputUri) }
        if (inputUris.isEmpty()) error("Image engine could not decode this input")

        val pdfDocument = PdfDocument()
        var pageNumber = 1
        try {
            inputUris.forEachIndexed { index, uri ->
                throwIfConversionCancelled()
                updateImageProgress(progressForIndexedWork(index, inputUris.size, 0.05f, 0.85f))

                if (
                    input.gifFrameMode == GifFrameExportMode.FramesAsSinglePdf &&
                    isLikelyGifInputUri(uri)
                ) {
                    val extraction = extractGifFrames(input, uri)
                    try {
                        forEachGifFrameBitmap(extraction, PDF_IMAGE_MAX_PIXELS) { _, bitmap ->
                            throwIfConversionCancelled()
                            addBitmapPageToPdf(pdfDocument, bitmap, input, pageNumber)
                            pageNumber += 1
                        }
                    } finally {
                        extraction.delete()
                    }
                } else {
                    val bitmap = decodeImageBitmap(
                        uri,
                        maxLongSidePixels = PDF_IMAGE_MAX_LONG_SIDE_PIXELS,
                        maxPixels = PDF_IMAGE_MAX_PIXELS
                    ) ?: error("Image engine could not decode this input")

                    try {
                        throwIfConversionCancelled()
                        addBitmapPageToPdf(pdfDocument, bitmap, input, pageNumber)
                        pageNumber += 1
                        throwIfConversionCancelled()
                    } finally {
                        bitmap.recycle()
                    }
                }
            }

            if (pageNumber == 1) error("Image engine could not decode this input")
            throwIfConversionCancelled()
            outputFile.outputStream().use { output ->
                pdfDocument.writeTo(output)
                output.flush()
            }
            throwIfConversionCancelled()
            updateImageProgress(0.95f)
        } finally {
            pdfDocument.close()
        }
    }

    private fun writeSingleBitmapPdf(
        bitmap: Bitmap,
        input: ConversionTaskInput,
        outputFile: File
    ) {
        val pdfDocument = PdfDocument()
        try {
            addBitmapPageToPdf(pdfDocument, bitmap, input, pageNumber = 1)
            throwIfConversionCancelled()
            outputFile.outputStream().use { output ->
                pdfDocument.writeTo(output)
                output.flush()
            }
            throwIfConversionCancelled()
        } finally {
            pdfDocument.close()
        }
    }

    private fun addBitmapPageToPdf(
        pdfDocument: PdfDocument,
        bitmap: Bitmap,
        input: ConversionTaskInput,
        pageNumber: Int
    ) {
        val pageSize = pdfPageSizeFor(bitmap, input.pdfOptions.imagePageMode)
        val pageInfo = PdfDocument.PageInfo.Builder(
            pageSize.width,
            pageSize.height,
            pageNumber
        ).create()
        val page = pdfDocument.startPage(pageInfo)
        try {
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(
                bitmap,
                null,
                centeredFitRect(
                    sourceWidth = bitmap.width,
                    sourceHeight = bitmap.height,
                    targetWidth = pageSize.width,
                    targetHeight = pageSize.height
                ),
                PDF_BITMAP_PAINT
            )
        } finally {
            pdfDocument.finishPage(page)
        }
    }

    private fun pdfPageSizeFor(
        bitmap: Bitmap,
        pageMode: PdfImagePageMode
    ): PdfPageSize {
        if (pageMode == PdfImagePageMode.A4Fit) {
            return if (bitmap.width >= bitmap.height) {
                PdfPageSize(width = PDF_A4_LONG_EDGE_PT, height = PDF_A4_SHORT_EDGE_PT)
            } else {
                PdfPageSize(width = PDF_A4_SHORT_EDGE_PT, height = PDF_A4_LONG_EDGE_PT)
            }
        }

        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        return if (bitmap.width >= bitmap.height) {
            PdfPageSize(
                width = PDF_A4_LONG_EDGE_PT,
                height = (PDF_A4_LONG_EDGE_PT / aspectRatio).roundToInt().coerceAtLeast(1)
            )
        } else {
            PdfPageSize(
                width = (PDF_A4_LONG_EDGE_PT * aspectRatio).roundToInt().coerceAtLeast(1),
                height = PDF_A4_LONG_EDGE_PT
            )
        }
    }

    private fun centeredFitRect(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): RectF {
        val scale = minOf(
            targetWidth.toFloat() / sourceWidth.toFloat(),
            targetHeight.toFloat() / sourceHeight.toFloat()
        )
        val width = sourceWidth * scale
        val height = sourceHeight * scale
        val left = (targetWidth - width) / 2f
        val top = (targetHeight - height) / 2f
        return RectF(left, top, left + width, top + height)
    }

    private fun decodeImageBitmap(
        uri: Uri,
        maxLongSidePixels: Int?,
        maxPixels: Long = MAX_IMAGE_DECODE_PIXELS
    ): Bitmap? {
        decodeIcoBitmap(uri, maxLongSidePixels, maxPixels)?.let { bitmap ->
            return bitmap
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeImageBitmapWithImageDecoder(uri, maxLongSidePixels, maxPixels)?.let { bitmap ->
                return scaleBitmapIfNeeded(bitmap, maxLongSidePixels)
            }
        }
        decodeImageBitmapWithFileDescriptor(uri, maxLongSidePixels, maxPixels)?.let { bitmap ->
            return bitmap
        }
        return decodeImageBitmapWithBitmapFactory(uri, maxLongSidePixels, maxPixels)
    }

    private fun decodeIcoBitmap(
        uri: Uri,
        maxLongSidePixels: Int?,
        maxPixels: Long
    ): Bitmap? {
        val entries = readIcoDirectory(uri) ?: return null
        val selectedEntry = entries
            .filter { it.imageSize in 1..ICO_MAX_PAYLOAD_BYTES }
            .maxWithOrNull(
                compareBy<IcoDirectoryEntry> { it.pixelArea }
                    .thenBy { it.bitCount }
                    .thenBy { it.imageSize }
            ) ?: error("Image engine could not decode this ICO input")

        val payload = readIcoPayload(uri, selectedEntry)
            ?: error("Image engine could not decode this ICO input")
        if (!payload.hasPngSignature()) {
            Log.w(TAG, "ICO input uses an unsupported non-PNG icon payload")
            error("Image engine only supports PNG-in-ICO input")
        }

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(payload, 0, payload.size, bounds)

        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "ICO PNG payload bounds failed width=$width height=$height")
            error("Image engine could not decode this ICO input")
        }

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = imageSampleSize(width, height, maxLongSidePixels, maxPixels)
        }
        val decoded = BitmapFactory.decodeByteArray(payload, 0, payload.size, options) ?: run {
            Log.w(TAG, "ICO PNG payload decode returned null")
            error("Image engine could not decode this ICO input")
        }
        return scaleBitmapIfNeeded(decoded, maxLongSidePixels)
    }

    private fun readIcoDirectory(uri: Uri): List<IcoDirectoryEntry>? {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val header = input.readExactByteArrayOrNull(ICO_HEADER_BYTES) ?: return@use null
                val reserved = header.readLittleEndianUnsignedShort(0)
                val type = header.readLittleEndianUnsignedShort(2)
                val count = header.readLittleEndianUnsignedShort(4)
                if (reserved != 0 || type != ICO_TYPE_ICON) return@use null
                if (count <= 0 || count > ICO_MAX_IMAGE_COUNT) {
                    Log.w(TAG, "ICO directory has unsupported image count=$count")
                    return@use null
                }

                val directoryEndOffset = ICO_HEADER_BYTES + ICO_DIRECTORY_ENTRY_BYTES * count
                val entries = mutableListOf<IcoDirectoryEntry>()
                for (entryIndex in 0 until count) {
                    val entry = input.readExactByteArrayOrNull(ICO_DIRECTORY_ENTRY_BYTES)
                        ?: return@use null
                    val imageSize = entry.readLittleEndianUnsignedInt(8)
                    val imageOffset = entry.readLittleEndianUnsignedInt(12)
                    if (
                        imageSize > Int.MAX_VALUE.toLong() ||
                        imageOffset > Int.MAX_VALUE.toLong() ||
                        imageOffset < directoryEndOffset.toLong()
                    ) {
                        Log.w(TAG, "Skipping invalid ICO directory entry index=$entryIndex")
                        continue
                    }
                    entries += IcoDirectoryEntry(
                        width = entry.icoDirectorySize(0),
                        height = entry.icoDirectorySize(1),
                        planes = entry.readLittleEndianUnsignedShort(4),
                        bitCount = entry.readLittleEndianUnsignedShort(6),
                        imageSize = imageSize.toInt(),
                        imageOffset = imageOffset.toInt()
                    )
                }

                entries.takeIf { it.isNotEmpty() }
            }
        }.onFailure { exception ->
            Log.w(TAG, "ICO directory read failed", exception)
        }.getOrNull()
    }

    private fun readIcoPayload(uri: Uri, entry: IcoDirectoryEntry): ByteArray? {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                if (!input.skipFully(entry.imageOffset.toLong())) return@use null
                input.readExactByteArrayOrNull(entry.imageSize)
            }
        }.onFailure { exception ->
            Log.w(TAG, "ICO payload read failed", exception)
        }.getOrNull()
    }

    private fun InputStream.readExactByteArrayOrNull(byteCount: Int): ByteArray? {
        val bytes = ByteArray(byteCount)
        var offset = 0
        while (offset < byteCount) {
            val read = read(bytes, offset, byteCount - offset)
            if (read < 0) return null
            offset += read
        }
        return bytes
    }

    private fun InputStream.skipFully(byteCount: Long): Boolean {
        var remaining = byteCount
        val buffer = ByteArray(ICO_SKIP_BUFFER_BYTES)
        while (remaining > 0L) {
            val skipped = skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }

            val readSize = minOf(buffer.size.toLong(), remaining).toInt()
            val read = read(buffer, 0, readSize)
            if (read < 0) return false
            remaining -= read.toLong()
        }
        return true
    }

    private fun ByteArray.icoDirectorySize(offset: Int): Int {
        val value = this[offset].toInt() and 0xff
        return if (value == 0) ICO_MAX_DIRECTORY_SIZE else value
    }

    private fun ByteArray.readLittleEndianUnsignedShort(offset: Int): Int {
        return (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun ByteArray.readLittleEndianUnsignedInt(offset: Int): Long {
        return (this[offset].toLong() and 0xffL) or
            ((this[offset + 1].toLong() and 0xffL) shl 8) or
            ((this[offset + 2].toLong() and 0xffL) shl 16) or
            ((this[offset + 3].toLong() and 0xffL) shl 24)
    }

    private fun ByteArray.hasPngSignature(): Boolean {
        if (size < PNG_SIGNATURE.size) return false
        return PNG_SIGNATURE.indices.all { index -> this[index] == PNG_SIGNATURE[index] }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeImageBitmapWithImageDecoder(
        uri: Uri,
        maxLongSidePixels: Int?,
        maxPixels: Long
    ): Bitmap? {
        return runCatching {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                if (info.size.width > 0 && info.size.height > 0) {
                    val targetSize = imageDecodeSizeFor(
                        info.size.width,
                        info.size.height,
                        maxLongSidePixels,
                        maxPixels
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
        maxLongSidePixels: Int?,
        maxPixels: Long
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
            inSampleSize = imageSampleSize(width, height, maxLongSidePixels, maxPixels)
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
        maxLongSidePixels: Int?,
        maxPixels: Long
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
            inSampleSize = imageSampleSize(width, height, maxLongSidePixels, maxPixels)
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
        maxLongSidePixels: Int?,
        maxPixels: Long = MAX_IMAGE_DECODE_PIXELS
    ): Int {
        var sampleSize = 1
        while (
            imagePixelsAtSample(width, height, sampleSize) > maxPixels ||
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
        maxLongSidePixels: Int?,
        maxPixels: Long = MAX_IMAGE_DECODE_PIXELS
    ): ImageDecodeSize {
        val sampleSize = imageSampleSize(width, height, maxLongSidePixels, maxPixels)
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
        val outputNeedsOpaqueBackground =
            extension.equals("jpg", ignoreCase = true) ||
                extension.equals("jpeg", ignoreCase = true) ||
                extension.equals("jfif", ignoreCase = true) ||
                extension.equals("jpe", ignoreCase = true)
        val shouldFlatten = flattenTransparency ||
            (outputNeedsOpaqueBackground && bitmap.hasAlpha())
        if (!shouldFlatten) return bitmap

        val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return flattened
    }

    @Suppress("DEPRECATION")
    private fun imageCompressFormatFor(
        extension: String,
        useWebpLossless: Boolean = false
    ): Bitmap.CompressFormat? {
        return when (extension.lowercase(Locale.US)) {
            "jpg", "jpeg", "jfif", "jpe" -> Bitmap.CompressFormat.JPEG
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (useWebpLossless) {
                        Bitmap.CompressFormat.WEBP_LOSSLESS
                    } else {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    }
                } else {
                    Bitmap.CompressFormat.WEBP
                }
            }
            else -> null
        }
    }

    private fun imageQualityFor(
        extension: String,
        requestedQuality: Int,
        useWebpLossless: Boolean = false
    ): Int {
        return if (extension.equals("png", ignoreCase = true) || useWebpLossless) {
            100
        } else {
            requestedQuality.coerceIn(1, 100)
        }
    }

    private fun shouldUseWebpLossless(
        outputProfile: OutputProfile,
        imageOptions: ImageExportOptions
    ): Boolean {
        return outputProfile.extension.equals("webp", ignoreCase = true) &&
            imageOptions.webpLossless &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
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

    private fun throwIfConversionCancelled() {
        if (ConversionTaskStore.isCancelled() || Thread.currentThread().isInterrupted) {
            throw CancellationException()
        }
    }

    private fun progressForIndexedWork(
        index: Int,
        count: Int,
        start: Float,
        end: Float
    ): Float {
        if (count <= 0) return start
        val completed = (index + 1).toFloat() / count.toFloat()
        return start + (end - start) * completed
    }

    private fun imageFailureMessageFor(exception: Throwable): String {
        return exception.message?.takeIf {
            it.startsWith("Image engine") || it.startsWith("Compatibility engine")
        }
            ?: "Image conversion failed"
    }

    private fun pdfFailureMessageFor(exception: Throwable): String {
        return when {
            exception is InvalidPasswordException -> "PDF password was incorrect or unsupported"
            exception is SecurityException ->
                exception.message?.takeIf { it.contains("Password-protected") }
                    ?: "Password-protected or unsupported PDF security"
            exception.message == "PDF has no pages" -> "PDF has no pages"
            exception.message == "Not enough cache space for this PDF" ->
                "Not enough cache space for this PDF"
            exception.message == "Select at least two PDFs to merge" ->
                "Select at least two PDFs to merge"
            exception.message == "PDF has no selectable text; OCR is not included" ->
                "PDF has no selectable text; OCR is not included"
            exception.message == "Could not open PDF" -> "Input file could not be opened"
            else -> exception.message?.takeIf {
                it.startsWith("Image engine") || it.startsWith("PDF")
            } ?: "PDF conversion failed"
        }
    }

    private fun pdfDocumentFailureMessageFor(
        exception: Throwable,
        outputProfile: OutputProfile
    ): String {
        val message = pdfFailureMessageFor(exception)
        if (message != "PDF conversion failed") return message
        return when (outputProfile.extension.lowercase(Locale.US)) {
            "pdf" -> "PDF merge failed"
            "txt" -> "PDF text extraction failed"
            else -> message
        }
    }

    private fun officeFailureMessageFor(exception: Throwable): String {
        val message = exception.message.orEmpty()
        return when {
            exception is Office2PdfUnsupportedAbiException ->
                "Office converter is only available on arm64-v8a devices"
            exception is Office2PdfUnavailableException ->
                "Office converter could not start on this device"
            exception is UnsatisfiedLinkError ->
                "Office converter could not start on this device"
            message == "Unsupported Office document" ->
                "Unsupported Office document"
            message == "Input file is empty" ->
                "Input file is empty"
            message == "Input file could not be opened" ->
                "Input file could not be opened"
            message == "Office file is too large for this experimental converter" ->
                "Office file is too large for this experimental converter"
            message == "Office engine did not return a PDF" ->
                "Office conversion failed"
            message.contains("unsupported Office format", ignoreCase = true) ->
                "Unsupported Office document"
            message.startsWith("Office", ignoreCase = true) ->
                message
            else -> "Office conversion failed"
        }
    }

    private fun readOfficeInputBytes(input: ConversionTaskInput): ByteArray {
        val sourceSize = queryOpenableSize(input.inputUri)
        sourceSize?.let { sizeBytes ->
            if (sizeBytes > OFFICE_MAX_INPUT_BYTES) {
                error("Office file is too large for this experimental converter")
            }
        }

        val initialCapacity = sourceSize
            ?.coerceAtMost(OFFICE_MAX_INPUT_BYTES)
            ?.toInt()
            ?: COPY_BUFFER_SIZE
        val output = ByteArrayOutputStream(initialCapacity.coerceAtLeast(0))

        contentResolver.openInputStream(input.inputUri)?.use { inputStream ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            var totalBytes = 0L
            while (true) {
                throwIfConversionCancelled()
                val read = inputStream.read(buffer)
                if (read == -1) break
                totalBytes += read.toLong()
                if (totalBytes > OFFICE_MAX_INPUT_BYTES) {
                    error("Office file is too large for this experimental converter")
                }
                output.write(buffer, 0, read)
            }
        } ?: error("Input file could not be opened")

        if (output.size() == 0) error("Input file is empty")
        return output.toByteArray()
    }

    private fun officeInputExtensionFor(input: ConversionTaskInput): String? {
        val extension = input.extension.lowercase(Locale.US)
        if (extension in OFFICE_INPUT_EXTENSIONS) return extension
        val mimeType = input.mimeType.orEmpty().lowercase(Locale.US)
        return OFFICE_MIME_TYPES[mimeType]
    }

    private fun looksLikePdf(bytes: ByteArray): Boolean {
        return bytes.size >= PDF_HEADER.size &&
            PDF_HEADER.indices.all { index -> bytes[index] == PDF_HEADER[index] }
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
                failCurrentTask(compatibilityStartupFailureMessageFor(exception))
            }
        }
    }

    private suspend fun runFfmpegCompatibilityExport(
        input: ConversionTaskInput,
        tempFile: File
    ): FfmpegRunResult = withContext(Dispatchers.IO) {
        val ffmpegLoadFailure = ensureFfmpegKitReady()
        if (ffmpegLoadFailure != null) {
            return@withContext FfmpegRunResult(
                success = false,
                cancelled = false,
                message = compatibilityStartupFailureMessageFor(ffmpegLoadFailure)
            )
        }

        ffmpegMissingEncoderMessageFor(input)?.let { message ->
            return@withContext FfmpegRunResult(
                success = false,
                cancelled = false,
                message = message
            )
        }

        val durationMs = ffmpegProgressDurationMsFor(input)
        val logTail = mutableListOf<String>()

        val inputSource = openFfmpegInputSource(input.inputUri)
            ?: return@withContext FfmpegRunResult(
                success = false,
                cancelled = false,
                message = "Compatibility engine could not open SAF input"
            )

        try {
            if (isVideoGifOutput(input)) {
                return@withContext runFfmpegVideoGifExport(
                    input = input,
                    inputSource = inputSource,
                    tempFile = tempFile,
                    durationMs = durationMs,
                    logTail = logTail
                )
            }
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

    private suspend fun runFfmpegVideoGifExport(
        input: ConversionTaskInput,
        inputSource: FfmpegInputSource,
        tempFile: File,
        durationMs: Long?,
        logTail: MutableList<String>
    ): FfmpegRunResult {
        return executeFfmpeg(
            input = input,
            arguments = ffmpegVideoGifArgumentsFor(
                input = input,
                inputPath = inputSource.path,
                outputFile = tempFile
            ),
            durationMs = durationMs,
            logTail = logTail,
            inputSourceLabel = inputSource.label
        )
    }

    private suspend fun extractGifFrames(
        input: ConversionTaskInput,
        uri: Uri
    ): GifFrameExtraction {
        val ffmpegLoadFailure = ensureFfmpegKitReady()
        if (ffmpegLoadFailure != null) {
            error(compatibilityStartupFailureMessageFor(ffmpegLoadFailure))
        }

        val frameSize = readGifLogicalScreenSize(uri)
            ?: error("Image engine could not decode this input")
        validateGifFrameSize(frameSize, GIF_FRAME_MAX_PIXELS)

        val frameDirectory = createGifFrameTempDirectory(input)
        val inputSource = openFfmpegInputSource(uri)
        if (inputSource == null) {
            frameDirectory.deleteRecursively()
            error("Compatibility engine could not open SAF input")
        }

        val logTail = mutableListOf<String>()
        try {
            val rawFrameFile = File(frameDirectory, GIF_RAW_FRAME_FILE_NAME)
            val arguments = listOf(
                "-hide_banner",
                "-nostdin",
                "-y",
                "-i",
                inputSource.path,
                "-map",
                "0:v:0",
                "-an",
                "-sn",
                "-dn",
                "-fps_mode",
                "passthrough",
                "-pix_fmt",
                "rgba",
                "-c:v",
                "rawvideo",
                "-f",
                "rawvideo",
                rawFrameFile.absolutePath
            )
            val result = executeFfmpeg(
                input = input,
                arguments = arguments,
                durationMs = null,
                logTail = logTail,
                inputSourceLabel = inputSource.label
            )
            if (result.cancelled || ConversionTaskStore.isCancelled()) {
                throw CancellationException()
            }
            if (!result.success) {
                Log.e(
                    TAG,
                    "GIF frame extraction failed displayName=${input.displayName} " +
                        "outputTail=${result.outputTail.orEmpty()}"
                )
                error("Image engine could not split GIF frames")
            }

            val frameByteCount = gifRawFrameByteCount(frameSize)
            val rawLength = rawFrameFile.length()
            if (rawLength <= 0L || rawLength % frameByteCount != 0L) {
                Log.e(TAG, "GIF frame extraction produced no frames displayName=${input.displayName}")
                error("Image engine could not split GIF frames")
            }
            val frameCountLong = rawLength / frameByteCount
            if (frameCountLong <= 0L || frameCountLong > Int.MAX_VALUE) {
                Log.e(TAG, "GIF frame extraction produced no frames displayName=${input.displayName}")
                error("Image engine could not split GIF frames")
            }
            val frameCount = frameCountLong.toInt()
            return GifFrameExtraction(
                directory = frameDirectory,
                rawFrameFile = rawFrameFile,
                width = frameSize.width,
                height = frameSize.height,
                frameCount = frameCount,
                frameByteCount = frameByteCount.toInt()
            )
        } catch (throwable: Throwable) {
            frameDirectory.deleteRecursively()
            throw throwable
        } finally {
            inputSource.close()
        }
    }

    private fun readGifLogicalScreenSize(uri: Uri): ImageDecodeSize? {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val header = input.readExactByteArrayOrNull(GIF_HEADER_BYTES) ?: return@use null
                val signature = String(header, 0, GIF_SIGNATURE_BYTES, Charsets.US_ASCII)
                if (signature != "GIF87a" && signature != "GIF89a") return@use null
                val width = header.readLittleEndianUnsignedShort(GIF_WIDTH_OFFSET)
                val height = header.readLittleEndianUnsignedShort(GIF_HEIGHT_OFFSET)
                if (width <= 0 || height <= 0) null else ImageDecodeSize(width, height)
            }
        }.onFailure { exception ->
            Log.w(TAG, "GIF header read failed", exception)
        }.getOrNull()
    }

    private fun validateGifFrameSize(size: ImageDecodeSize, maxPixels: Long) {
        val pixels = size.width.toLong() * size.height.toLong()
        if (pixels <= 0L || pixels > maxPixels) {
            Log.w(TAG, "GIF frame size is too large width=${size.width} height=${size.height}")
            error("Image engine could not decode this input")
        }
        gifRawFrameByteCount(size)
    }

    private fun gifRawFrameByteCount(size: ImageDecodeSize): Long {
        val byteCount = size.width.toLong() * size.height.toLong() * RGBA_BYTES_PER_PIXEL
        if (byteCount <= 0L || byteCount > Int.MAX_VALUE) {
            error("Image engine could not decode this input")
        }
        return byteCount
    }

    private fun createGifFrameTempDirectory(input: ConversionTaskInput): File {
        val cacheRoot = externalCacheDir ?: cacheDir
        val tempRoot = File(cacheRoot, GIF_FRAME_TEMP_DIRECTORY).apply { mkdirs() }
        return File(tempRoot, "${input.fileId}_${System.nanoTime()}").apply {
            if (exists()) deleteRecursively()
            if (!mkdirs()) error("Image engine could not split GIF frames")
        }
    }

    private fun forEachGifFrameBitmap(
        extraction: GifFrameExtraction,
        maxPixels: Long,
        onFrame: (Int, Bitmap) -> Unit
    ) {
        validateGifFrameSize(ImageDecodeSize(extraction.width, extraction.height), maxPixels)
        val frameBytes = ByteArray(extraction.frameByteCount)
        extraction.rawFrameFile.inputStream().use { input ->
            for (frameIndex in 0 until extraction.frameCount) {
                throwIfConversionCancelled()
                if (!input.readExactByteArray(frameBytes)) {
                    error("Image engine could not split GIF frames")
                }
                val bitmap = bitmapFromRgbaFrameBytes(
                    frameBytes,
                    extraction.width,
                    extraction.height
                )
                try {
                    onFrame(frameIndex, bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun InputStream.readExactByteArray(buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun bitmapFromRgbaFrameBytes(
        frameBytes: ByteArray,
        width: Int,
        height: Int
    ): Bitmap {
        val pixels = IntArray(width * height)
        var byteIndex = 0
        for (pixelIndex in pixels.indices) {
            val red = frameBytes[byteIndex].toInt() and 0xff
            val green = frameBytes[byteIndex + 1].toInt() and 0xff
            val blue = frameBytes[byteIndex + 2].toInt() and 0xff
            val alpha = frameBytes[byteIndex + 3].toInt() and 0xff
            pixels[pixelIndex] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
            byteIndex += RGBA_BYTES_PER_PIXEL
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
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
            ConversionMediaCategory.Video -> ffmpegVideoArgumentsFor(input, inputPath, outputFile)
            ConversionMediaCategory.Audio -> buildList {
                val audioProfile = ffmpegAudioProfileFor(input)
                    ?: error("Unsupported audio target ${input.targetFormat}")
                add("-hide_banner")
                add("-nostdin")
                add("-y")
                add("-i")
                add(inputPath)
                add("-map")
                add("0:a:0")
                add("-vn")
                add("-sn")
                add("-dn")
                add("-c:a")
                add(audioProfile.codec)
                if (audioProfile.supportsBitrate) {
                    input.audioOptions.audioBitrate?.let { bitrate ->
                        add("-b:a")
                        add(bitrate.toString())
                    }
                }
                if (audioProfile.supportsSampleRate) {
                    input.audioOptions.sampleRateHz?.let { sampleRateHz ->
                        add("-ar")
                        add(sampleRateHz.toString())
                    }
                }
                if (audioProfile.supportsChannelCount) {
                    input.audioOptions.channelCount?.let { channelCount ->
                        add("-ac")
                        add(channelCount.toString())
                    }
                }
                if (audioProfile.useFastStart) {
                    add("-movflags")
                    add("+faststart")
                }
                add("-f")
                add(audioProfile.format)
                add(outputFile.absolutePath)
            }
            ConversionMediaCategory.Image -> error("Compatibility engine is not connected for images")
            ConversionMediaCategory.Pdf -> error("Compatibility engine is not connected for PDFs")
            ConversionMediaCategory.Document -> error("Compatibility engine is not connected for documents")
        }
    }

    private fun ffmpegVideoArgumentsFor(
        input: ConversionTaskInput,
        inputPath: String,
        outputFile: File
    ): List<String> {
        val videoProfile = ffmpegVideoProfileFor(input)
            ?: error("Unsupported video target ${input.targetFormat}")
        return buildList {
            add("-hide_banner")
            add("-nostdin")
            add("-y")
            add("-i")
            add(inputPath)
            add("-ignore_unknown")
            add("-map")
            add("0:v:0")
            add("-map")
            add("0:a:0?")
            add("-sn")
            add("-dn")
            add("-c:v")
            add(videoProfile.videoCodec)
            add("-pix_fmt")
            add(videoProfile.pixelFormat)
            add("-preset")
            add(videoProfile.preset)
            input.videoOptions.videoBitrate?.let { bitrate ->
                add("-b:v")
                add(bitrate.toString())
            } ?: run {
                add("-crf")
                add(videoProfile.crf)
            }
            ffmpegVideoFilterFor(input)?.let { filter ->
                add("-vf")
                add(filter)
            }
            input.videoOptions.maxFrameRate
                ?.takeIf { it > 0 }
                ?.let { maxFrameRate ->
                    add("-fpsmax")
                    add(maxFrameRate.toString())
                }
            if (videoProfile.videoTag != null) {
                add("-tag:v")
                add(videoProfile.videoTag)
            }
            add("-c:a")
            add(FFMPEG_AAC_ENCODER)
            if (videoProfile.useFastStart) {
                add("-movflags")
                add("+faststart")
            }
            add("-f")
            add(videoProfile.format)
            add(outputFile.absolutePath)
        }
    }

    private fun ffmpegVideoGifArgumentsFor(
        input: ConversionTaskInput,
        inputPath: String,
        outputFile: File
    ): List<String> {
        return buildList {
            add("-hide_banner")
            add("-nostdin")
            add("-y")
            add("-t")
            add(FFMPEG_VIDEO_GIF_MAX_DURATION_SECONDS)
            add("-i")
            add(inputPath)
            add("-an")
            add("-sn")
            add("-dn")
            add("-filter_complex")
            add(
                "[0:v:0]${ffmpegVideoGifFilterChainFor(input)},split[gifsrc][palette_src];" +
                    "[palette_src]palettegen=max_colors=256[palette];" +
                    "[gifsrc][palette]paletteuse=dither=sierra2_4a[gif]"
            )
            add("-map")
            add("[gif]")
            add("-frames:v")
            add(FFMPEG_VIDEO_GIF_MAX_FRAMES.toString())
            add("-loop")
            add("0")
            add("-f")
            add("gif")
            add(outputFile.absolutePath)
        }
    }

    private fun ffmpegVideoGifFilterChainFor(input: ConversionTaskInput): String {
        return buildList {
            add("fps=$FFMPEG_VIDEO_GIF_FRAME_RATE")
            ffmpegVideoGifScaleFilterFor(input)?.let { add(it) }
        }.joinToString(separator = ",")
    }

    private fun ffmpegVideoGifScaleFilterFor(input: ConversionTaskInput): String? {
        val targetShortSide = input.videoOptions.maxShortSidePixels
            ?.takeIf { it > 0 }
            ?: return null
        val sourceSize = readVideoSize(input.inputUri)
        if (sourceSize != null) {
            if (sourceSize.shortSide <= targetShortSide) return null
            val scaledSize = scaledVideoSizeFor(sourceSize, targetShortSide)
            return "scale=${scaledSize.width}:${scaledSize.height}:flags=lanczos"
        }
        Log.w(
            TAG,
            "Video size metadata unavailable; using FFmpeg dynamic GIF scale filter " +
                "targetShortSide=$targetShortSide displayName=${input.displayName}"
        )
        return "scale=w='if(gte(iw\\,ih)\\,-2\\,min(iw\\,$targetShortSide))':" +
            "h='if(gte(iw\\,ih)\\,min(ih\\,$targetShortSide)\\,-2)':flags=lanczos"
    }

    private fun ffmpegVideoProfileFor(input: ConversionTaskInput): FfmpegVideoProfile? {
        val targetExtension = videoTargetExtensionFor(input.targetFormat) ?: return null
        val videoCodec = when (input.videoOptions.videoMimeType) {
            VideoExportOptions.VIDEO_MIME_TYPE_H265 -> FFMPEG_VIDEO_ENCODER_H265
            else -> FFMPEG_VIDEO_ENCODER_H264
        }
        return when (targetExtension) {
            "mp4" -> FfmpegVideoProfile(
                videoCodec = videoCodec,
                format = "mp4",
                useFastStart = true,
                videoTag = if (videoCodec == FFMPEG_VIDEO_ENCODER_H265) "hvc1" else null,
                crf = defaultVideoCrfFor(videoCodec)
            )
            "mov" -> FfmpegVideoProfile(
                videoCodec = videoCodec,
                format = "mov",
                useFastStart = true,
                videoTag = if (videoCodec == FFMPEG_VIDEO_ENCODER_H265) "hvc1" else null,
                crf = defaultVideoCrfFor(videoCodec)
            )
            "mkv" -> FfmpegVideoProfile(
                videoCodec = videoCodec,
                format = "matroska",
                crf = defaultVideoCrfFor(videoCodec)
            )
            else -> null
        }
    }

    private fun defaultVideoCrfFor(videoCodec: String): String {
        return if (videoCodec == FFMPEG_VIDEO_ENCODER_H265) {
            FFMPEG_DEFAULT_CRF_H265
        } else {
            FFMPEG_DEFAULT_CRF_H264
        }
    }

    private fun ffmpegVideoFilterFor(input: ConversionTaskInput): String? {
        val targetShortSide = input.videoOptions.maxShortSidePixels
            ?.takeIf { it > 0 }
            ?: return null
        val sourceSize = readVideoSize(input.inputUri)
        if (sourceSize != null) {
            if (sourceSize.shortSide <= targetShortSide) return null
            val scaledSize = scaledVideoSizeFor(sourceSize, targetShortSide)
            return "scale=${scaledSize.width}:${scaledSize.height}"
        }
        Log.w(
            TAG,
            "Video size metadata unavailable; using FFmpeg dynamic scale filter " +
                "targetShortSide=$targetShortSide displayName=${input.displayName}"
        )
        return "scale=w='if(gte(iw\\,ih)\\,-2\\,min(iw\\,$targetShortSide))':" +
            "h='if(gte(iw\\,ih)\\,min(ih\\,$targetShortSide)\\,-2)'"
    }

    private fun scaledVideoSizeFor(sourceSize: VideoSize, targetShortSide: Int): VideoSize {
        return if (sourceSize.width >= sourceSize.height) {
            VideoSize(
                width = evenVideoDimension(
                    sourceSize.width.toDouble() *
                        targetShortSide.toDouble() /
                        sourceSize.height.toDouble()
                ),
                height = evenVideoDimension(targetShortSide.toDouble())
            )
        } else {
            VideoSize(
                width = evenVideoDimension(targetShortSide.toDouble()),
                height = evenVideoDimension(
                    sourceSize.height.toDouble() *
                        targetShortSide.toDouble() /
                        sourceSize.width.toDouble()
                )
            )
        }
    }

    private fun evenVideoDimension(value: Double): Int {
        val rounded = value.roundToInt().coerceAtLeast(2)
        return if (rounded % 2 == 0) rounded else (rounded - 1).coerceAtLeast(2)
    }

    private fun ffmpegAudioProfileFor(input: ConversionTaskInput): FfmpegAudioProfile? {
        val targetExtension = audioTargetExtensionFor(input.targetFormat) ?: return null
        if (targetExtension == "m4a" && shouldCopyM4aAudioInCompatibilityEngine(input)) {
            return FfmpegAudioProfile(
                codec = "copy",
                format = "ipod",
                useFastStart = true,
                supportsBitrate = false,
                supportsSampleRate = false,
                supportsChannelCount = false
            )
        }

        return when (targetExtension) {
            "mp3" -> FfmpegAudioProfile(
                codec = "libmp3lame",
                format = "mp3",
                requiredEncoder = "libmp3lame"
            )
            "m4a" -> FfmpegAudioProfile(
                codec = "aac",
                format = "ipod",
                useFastStart = true
            )
            "wav" -> FfmpegAudioProfile(
                codec = "pcm_s16le",
                format = "wav",
                supportsBitrate = false
            )
            "flac" -> FfmpegAudioProfile(
                codec = "flac",
                format = "flac",
                supportsBitrate = false
            )
            "wma" -> FfmpegAudioProfile(
                codec = "wmav2",
                format = "asf"
            )
            else -> null
        }
    }

    private fun shouldCopyM4aAudioInCompatibilityEngine(input: ConversionTaskInput): Boolean {
        return audioTargetExtensionFor(input.targetFormat) == "m4a" && isLikelyVideoInput(input)
    }

    private suspend fun executeFfmpeg(
        input: ConversionTaskInput,
        arguments: List<String>,
        durationMs: Long?,
        logTail: MutableList<String>,
        inputSourceLabel: String,
        progressStart: Float = 0f,
        progressEnd: Float = FFMPEG_MAX_PROGRESS_BEFORE_SAVE
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
                    val returnCode = session.getReturnCode()
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
                        updateCompatibilityProgress(
                            scaledFfmpegProgress(progress, progressStart, progressEnd)
                        )
                    }
                }
            },
            { statistics ->
                val duration = durationMs
                if (duration != null && duration > 0L) {
                    val progress = statistics.time.toFloat() / duration.toFloat()
                    updateCompatibilityProgress(
                        scaledFfmpegProgress(progress, progressStart, progressEnd)
                    )
                }
            }
        )
        activeFfmpegSession = session
        continuation.invokeOnCancellation {
            runCatching {
                FFmpegKit.cancel(session.getSessionId())
            }.onFailure { exception ->
                Log.w(TAG, "Could not cancel FFmpeg session", exception)
            }
        }
    }

    private fun scaledFfmpegProgress(
        progress: Float,
        progressStart: Float,
        progressEnd: Float
    ): Float {
        val normalizedProgress = progress.coerceIn(0f, 1f)
        return progressStart + (progressEnd - progressStart) * normalizedProgress
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

    private fun ffmpegMissingEncoderMessageFor(input: ConversionTaskInput): String? {
        val requiredEncoders = when (input.category) {
            ConversionMediaCategory.Video -> {
                if (isVideoGifOutput(input)) {
                    listOf(FFMPEG_GIF_ENCODER)
                } else {
                    val profile = ffmpegVideoProfileFor(input) ?: return null
                    listOf(profile.videoCodec, FFMPEG_AAC_ENCODER)
                }
            }
            ConversionMediaCategory.Audio -> {
                val profile = ffmpegAudioProfileFor(input) ?: return null
                listOfNotNull(profile.requiredEncoder)
            }
            ConversionMediaCategory.Image,
            ConversionMediaCategory.Pdf,
            ConversionMediaCategory.Document -> return null
        }
        val missingEncoder = requiredEncoders.firstOrNull { encoder ->
            ffmpegEncoderAvailable(encoder) == false
        } ?: return null

        Log.e(
            TAG,
            "FFmpeg compatibility package is missing encoder=$missingEncoder " +
                "target=${input.targetFormat} displayName=${input.displayName}"
        )
        return compatibilityMissingEncoderMessageFor(input, missingEncoder)
    }

    private fun ffmpegEncoderAvailable(encoder: String): Boolean? {
        ffmpegEncoderAvailability[encoder]?.let { return it }

        val output = runCatching {
            val session = FFmpegKit.executeWithArguments(
                arrayOf("-hide_banner", "-encoders")
            )
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                session.getAllLogsAsString(FFMPEG_ENCODER_PROBE_TIMEOUT_MS).orEmpty()
            } else {
                ""
            }
        }.onFailure { exception ->
            Log.w(TAG, "Could not probe FFmpeg encoder list", exception)
        }.getOrNull() ?: return null
        if (output.isBlank()) return null

        val available = ffmpegEncoderOutputContains(output, encoder)
        ffmpegEncoderAvailability[encoder] = available
        return available
    }

    private fun ffmpegEncoderOutputContains(output: String, encoder: String): Boolean {
        val lines = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        return lines.indices.any { index ->
            val line = lines[index]
            val tokens = line.split(WHITESPACE_REGEX, limit = 3)
            when {
                tokens.size >= 2 &&
                    FFMPEG_ENCODER_FLAGS_REGEX.matches(tokens[0]) &&
                    tokens[1] == encoder -> true
                tokens.size == 1 &&
                    tokens[0] == encoder &&
                    index > 0 &&
                    FFMPEG_ENCODER_FLAGS_REGEX.matches(lines[index - 1]) -> true
                else -> false
            }
        }
    }

    private fun compatibilityMissingEncoderMessageFor(
        input: ConversionTaskInput,
        encoder: String
    ): String {
        return when {
            encoder == "libmp3lame" &&
                audioTargetExtensionFor(input.targetFormat) == "mp3" ->
                "Compatibility engine needs an MP3-capable FFmpeg package"
            encoder == FFMPEG_VIDEO_ENCODER_H264 ->
                "Compatibility engine needs an H.264-capable FFmpeg package"
            encoder == FFMPEG_VIDEO_ENCODER_H265 ->
                "Compatibility engine needs an H.265-capable FFmpeg package"
            encoder == FFMPEG_AAC_ENCODER ->
                "Compatibility engine needs an AAC-capable FFmpeg package"
            encoder == FFMPEG_GIF_ENCODER &&
                isVideoGifOutput(input) ->
                "Compatibility engine needs a GIF-capable FFmpeg package"
            input.category == ConversionMediaCategory.Video ->
                "Compatibility engine cannot encode this video format yet"
            else ->
                "Compatibility engine cannot encode this audio format yet"
        }
    }

    private fun compatibilityFailureMessageFor(
        input: ConversionTaskInput,
        outputTail: String = ""
    ): String {
        val normalizedTail = outputTail.lowercase(Locale.US)
        if (
            input.category == ConversionMediaCategory.Audio &&
            shouldCopyM4aAudioInCompatibilityEngine(input) &&
            (
                normalizedTail.contains("codec not currently supported in container") ||
                    normalizedTail.contains("could not find tag for codec")
            )
        ) {
            return "Compatibility engine needs an AAC audio stream for M4A copy"
        }
        if (
            input.category == ConversionMediaCategory.Audio &&
            audioTargetExtensionFor(input.targetFormat) == "mp3" &&
            normalizedTail.contains("libmp3lame")
        ) {
            return "Compatibility engine needs an MP3-capable FFmpeg package"
        }
        if (
            input.category == ConversionMediaCategory.Audio &&
            (
                normalizedTail.contains("unknown encoder") ||
                    normalizedTail.contains("encoder not found") ||
                    normalizedTail.contains("invalid encoder")
            )
        ) {
            return "Compatibility engine cannot encode this audio format yet"
        }
        if (
            input.category == ConversionMediaCategory.Audio &&
            (
                normalizedTail.contains("codec not currently supported in container") ||
                    normalizedTail.contains("could not find tag for codec") ||
                    normalizedTail.contains("invalid argument")
            )
        ) {
            return "Compatibility engine could not write this audio container"
        }
        if (
            isVideoGifOutput(input) &&
            (
                normalizedTail.contains("unknown encoder") ||
                    normalizedTail.contains("encoder not found") ||
                    normalizedTail.contains("invalid encoder")
            )
        ) {
            return "Compatibility engine needs a GIF-capable FFmpeg package"
        }
        if (isVideoGifOutput(input)) {
            return "Compatibility engine could not create this GIF"
        }
        if (
            input.category == ConversionMediaCategory.Video &&
            (
                normalizedTail.contains("unknown encoder") ||
                    normalizedTail.contains("encoder not found") ||
                    normalizedTail.contains("invalid encoder")
            )
        ) {
            return "Compatibility engine cannot encode this video format yet"
        }
        if (
            input.category == ConversionMediaCategory.Video &&
            (
                normalizedTail.contains("codec not currently supported in container") ||
                    normalizedTail.contains("could not find tag for codec") ||
                    normalizedTail.contains("invalid argument")
            )
        ) {
            return "Compatibility engine could not write this video container"
        }
        return when (input.category) {
            ConversionMediaCategory.Video -> when (videoTargetExtensionFor(input.targetFormat)) {
                "mkv" -> "Compatibility engine could not transcode this file to MKV"
                "mov" -> "Compatibility engine could not transcode this file to MOV"
                "gif" -> "Compatibility engine could not create this GIF"
                else -> "Compatibility engine could not transcode this file to MP4"
            }
            ConversionMediaCategory.Audio ->
                "Compatibility engine could not convert this audio"
            ConversionMediaCategory.Image ->
                "Compatibility engine is not connected for images"
            ConversionMediaCategory.Pdf ->
                "Compatibility engine is not connected for PDFs"
            ConversionMediaCategory.Document ->
                "Compatibility engine is not connected for documents"
        }
    }

    private fun ensureFfmpegKitReady(): Throwable? {
        if (ffmpegKitReady) return null
        ffmpegKitLoadFailure?.let { return it }

        return runCatching {
            FFmpegKitConfig.enableRedirection()
        }.fold(
            onSuccess = {
                ffmpegKitReady = true
                null
            },
            onFailure = { exception ->
                ffmpegKitLoadFailure = exception
                Log.e(TAG, "FFmpegKit could not start", exception)
                exception
            }
        )
    }

    private fun compatibilityStartupFailureMessageFor(exception: Throwable): String {
        return if (isFfmpegKitStartupFailure(exception)) {
            "Compatibility engine could not start on this device"
        } else {
            "Compatibility engine failed before export"
        }
    }

    private fun isFfmpegKitStartupFailure(exception: Throwable): Boolean {
        var current: Throwable? = exception
        while (current != null) {
            if (
                current is UnsatisfiedLinkError ||
                current is ExceptionInInitializerError ||
                current is NoClassDefFoundError ||
                current.message?.contains("FFmpegKit failed to start", ignoreCase = true) == true
            ) {
                return true
            }
            current = current.cause
        }
        return false
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

    private fun ffmpegProgressDurationMsFor(input: ConversionTaskInput): Long? {
        val durationMs = readDurationMs(input.inputUri)
        return if (isVideoGifOutput(input)) {
            durationMs?.coerceAtMost(FFMPEG_VIDEO_GIF_MAX_DURATION_MS)
        } else {
            durationMs
        }
    }

    private fun shouldUseCompatibilityEngine(input: ConversionTaskInput): Boolean {
        return when (input.category) {
            ConversionMediaCategory.Video ->
                isLikelyVideoInput(input) &&
                    (
                        videoTargetExtensionFor(input.targetFormat) in COMPATIBILITY_VIDEO_TARGETS ||
                            !isLikelyMp4VideoInput(input)
                        )
            ConversionMediaCategory.Audio ->
                audioTargetExtensionFor(input.targetFormat) != "m4a" ||
                    isLikelyWmaAudioInput(input) ||
                    (isLikelyVideoInput(input) && !isLikelyMp4VideoInput(input))
            ConversionMediaCategory.Image -> false
            ConversionMediaCategory.Pdf -> false
            ConversionMediaCategory.Document -> false
        }
    }

    private fun isLikelyVideoInput(input: ConversionTaskInput): Boolean {
        val mimeType = input.mimeType.orEmpty().lowercase(Locale.US)
        val extension = input.extension.lowercase(Locale.US)
        return mimeType.startsWith("video/") || extension in VIDEO_INPUT_EXTENSIONS
    }

    private fun isVideoGifOutput(input: ConversionTaskInput): Boolean {
        return input.category == ConversionMediaCategory.Video &&
            videoTargetExtensionFor(input.targetFormat) == "gif"
    }

    private fun isLikelyMp4VideoInput(input: ConversionTaskInput): Boolean {
        val mimeType = input.mimeType.orEmpty().lowercase(Locale.US)
        val extension = input.extension.lowercase(Locale.US)
        return extension == "mp4" || mimeType == MIME_TYPE_MP4
    }

    private fun isLikelyWmaAudioInput(input: ConversionTaskInput): Boolean {
        val mimeType = input.mimeType.orEmpty().lowercase(Locale.US)
        val extension = input.extension.lowercase(Locale.US)
        return extension in WMA_AUDIO_INPUT_EXTENSIONS ||
            mimeType.contains("x-ms-wma") ||
            mimeType.contains("x-ms-asf")
    }

    private fun isLikelyGifInputUri(uri: Uri): Boolean {
        val mimeType = runCatching {
            contentResolver.getType(uri).orEmpty().lowercase(Locale.US)
        }.getOrDefault("")
        if (mimeType == MIME_TYPE_GIF) return true

        val displayName = displayNameForUri(uri).orEmpty().lowercase(Locale.US)
        return displayName.endsWith(".gif")
    }

    private fun displayNameForUri(uri: Uri): String? {
        if (uri.scheme == URI_SCHEME_FILE) {
            return uri.lastPathSegment
        }
        return runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private val ConversionTaskInput.extension: String
        get() = displayName.substringAfterLast('.', missingDelimiterValue = "")

    private fun cancelActiveFfmpegSession() {
        val sessionId = activeFfmpegSession?.getSessionId()
        activeFfmpegSession = null
        if (sessionId != null) {
            runCatching {
                FFmpegKit.cancel(sessionId)
            }.onFailure { exception ->
                Log.w(TAG, "Could not cancel active FFmpeg session", exception)
            }
        }
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

    private fun aggregateOutputInfo(
        outputProfile: OutputProfile,
        itemCount: Int,
        outputSizeBytes: Long
    ): FileBasicInfo {
        return FileBasicInfo(
            formatLabel = outputProfile.extension.uppercase(Locale.US),
            sizeBytes = outputSizeBytes.takeIf { it > 0L },
            itemCount = itemCount.takeIf { it > 1 }
        )
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
                val outputDisplayName = outputNameFor(input, outputProfile.extension)
                val tempFileSizeBytes = tempFile.length().takeIf { it >= 0L }
                val createdOutputUri = createOutput(
                    input,
                    outputDisplayName,
                    outputProfile
                )
                outputUri = createdOutputUri
                copyFileToOutput(tempFile, createdOutputUri)
                finalizeOutput(input, createdOutputUri, outputProfile.mimeType)
                val outputInfo = FileBasicInfoReader.read(
                    context = this,
                    uri = createdOutputUri,
                    displayName = outputDisplayName,
                    mimeType = outputProfile.mimeType,
                    fallbackSizeBytes = tempFileSizeBytes,
                    formatOverride = outputProfile.extension.uppercase(Locale.US)
                )
                tempFile.delete()
                handler.post {
                    if (ConversionTaskStore.isCancelled()) {
                        deleteOutputQuietly(createdOutputUri)
                        cancelRun()
                        return@post
                    }
                    activeTempFile = null
                    ConversionTaskStore.markCompleted(
                        index = taskIndex,
                        outputUri = createdOutputUri,
                        outputUris = listOf(createdOutputUri),
                        outputDirectoryUri = outputDirectoryUriFor(input, outputProfile),
                        outputMimeType = outputProfile.mimeType,
                        outputInfo = outputInfo
                    )
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

    private fun saveCompletedExportFiles(
        input: ConversionTaskInput,
        tempFiles: List<File>,
        outputProfile: OutputProfile
    ) {
        stopProgressPolling()
        activeTransformer = null
        ConversionTaskStore.markSaving(taskIndex)
        updateNotification("Saving", (ConversionTaskStore.aggregateProgress() * 100).toInt())

        copyThread = Thread {
            val outputUris = mutableListOf<Uri>()
            var outputSizeBytes = 0L
            try {
                tempFiles.forEachIndexed { index, tempFile ->
                    if (Thread.currentThread().isInterrupted || ConversionTaskStore.isCancelled()) {
                        throw InterruptedException()
                    }
                    outputSizeBytes += tempFile.length().coerceAtLeast(0L)
                    val createdOutputUri = createOutput(
                        input,
                        outputNameForPage(
                            input = input,
                            extension = outputProfile.extension,
                            pageIndex = index,
                            pageCount = tempFiles.size
                        ),
                        outputProfile
                    )
                    outputUris.add(createdOutputUri)
                    copyFileToOutput(tempFile, createdOutputUri)
                    finalizeOutput(input, createdOutputUri, outputProfile.mimeType)
                    tempFile.delete()
                }
                handler.post {
                    if (ConversionTaskStore.isCancelled()) {
                        outputUris.forEach { deleteOutputQuietly(it) }
                        cancelRun()
                        return@post
                    }
                    activeTempFile = null
                    ConversionTaskStore.markCompleted(
                        index = taskIndex,
                        outputUri = outputUris.firstOrNull(),
                        outputUris = outputUris.toList(),
                        outputDirectoryUri = outputDirectoryUriFor(input, outputProfile),
                        outputMimeType = outputProfile.mimeType,
                        outputInfo = aggregateOutputInfo(outputProfile, outputUris.size, outputSizeBytes)
                    )
                    taskIndex += 1
                    processNextTask()
                }
            } catch (exception: InterruptedException) {
                outputUris.forEach { deleteOutputQuietly(it) }
                tempFiles.forEach { it.delete() }
                handler.post { cancelRun() }
            } catch (exception: Throwable) {
                Log.w(TAG, "Could not save PDF image export", exception)
                outputUris.forEach { deleteOutputQuietly(it) }
                tempFiles.forEach { it.delete() }
                handler.post {
                    activeTempFile = null
                    failCurrentTask("Could not save output file")
                }
            }
        }.also { it.start() }
    }

    private fun saveCompletedExportFilesInFolder(
        input: ConversionTaskInput,
        tempFiles: List<File>,
        outputProfile: OutputProfile,
        folderName: String
    ) {
        stopProgressPolling()
        activeTransformer = null
        ConversionTaskStore.markSaving(taskIndex)
        updateNotification("Saving", (ConversionTaskStore.aggregateProgress() * 100).toInt())

        copyThread = Thread {
            val outputUris = mutableListOf<Uri>()
            var outputFolder: OutputFolder? = null
            var outputSizeBytes = 0L
            try {
                val folder = createOutputFolder(input, folderName, outputProfile)
                outputFolder = folder
                tempFiles.forEachIndexed { index, tempFile ->
                    if (Thread.currentThread().isInterrupted || ConversionTaskStore.isCancelled()) {
                        throw InterruptedException()
                    }
                    outputSizeBytes += tempFile.length().coerceAtLeast(0L)
                    val createdOutputUri = createOutputInFolder(
                        folder = folder,
                        displayName = outputNameForFrame(
                            input = input,
                            extension = outputProfile.extension,
                            frameIndex = index,
                            frameCount = tempFiles.size
                        ),
                        outputProfile = outputProfile
                    )
                    outputUris.add(createdOutputUri)
                    copyFileToOutput(tempFile, createdOutputUri)
                    finalizeOutput(input, createdOutputUri, outputProfile.mimeType)
                    tempFile.delete()
                }
                handler.post {
                    if (ConversionTaskStore.isCancelled()) {
                        outputUris.forEach { deleteOutputQuietly(it) }
                        deleteOutputFolderQuietly(outputFolder)
                        cancelRun()
                        return@post
                    }
                    activeTempFile = null
                    ConversionTaskStore.markCompleted(
                        index = taskIndex,
                        outputUri = outputUris.firstOrNull(),
                        outputUris = outputUris.toList(),
                        outputDirectoryUri = outputFolderOpenUri(outputFolder)
                            ?: outputDirectoryUriFor(input, outputProfile),
                        outputMimeType = outputProfile.mimeType,
                        outputInfo = aggregateOutputInfo(outputProfile, outputUris.size, outputSizeBytes)
                    )
                    taskIndex += 1
                    processNextTask()
                }
            } catch (exception: InterruptedException) {
                outputUris.forEach { deleteOutputQuietly(it) }
                deleteOutputFolderQuietly(outputFolder)
                tempFiles.forEach { it.delete() }
                handler.post { cancelRun() }
            } catch (exception: Throwable) {
                Log.w(TAG, "Could not save GIF frame export", exception)
                outputUris.forEach { deleteOutputQuietly(it) }
                deleteOutputFolderQuietly(outputFolder)
                tempFiles.forEach { it.delete() }
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

    private fun createTempFileForPdfPage(
        input: ConversionTaskInput,
        extension: String,
        pageIndex: Int
    ): File {
        val cacheRoot = externalCacheDir ?: cacheDir
        val tempDirectory = File(cacheRoot, "exports").apply { mkdirs() }
        return File(tempDirectory, "${input.fileId}_page_${pageIndex + 1}.$extension").apply {
            if (exists()) delete()
        }
    }

    private fun createTempFileForGifFrameOutput(
        input: ConversionTaskInput,
        extension: String,
        frameIndex: Int
    ): File {
        val cacheRoot = externalCacheDir ?: cacheDir
        val tempDirectory = File(cacheRoot, "exports").apply { mkdirs() }
        return File(tempDirectory, "${input.fileId}_frame_${frameIndex + 1}.$extension").apply {
            if (exists()) delete()
        }
    }

    private fun createOutput(
        input: ConversionTaskInput,
        displayName: String,
        outputProfile: OutputProfile
    ): Uri {
        return when (val destination = input.outputDestination) {
            OutputDestination.DefaultPublicDirectory -> createDefaultOutput(
                displayName,
                outputProfile
            )
            is OutputDestination.CustomDirectory -> createOutputDocument(
                destination.uri,
                displayName,
                outputProfile.mimeType
            )
        }
    }

    private fun createOutputFolder(
        input: ConversionTaskInput,
        folderName: String,
        outputProfile: OutputProfile
    ): OutputFolder {
        return when (val destination = input.outputDestination) {
            OutputDestination.DefaultPublicDirectory -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    val publicDirectory = Environment.getExternalStoragePublicDirectory(
                        defaultPublicDirectoryFor(outputProfile)
                    )
                    val folder = File(File(publicDirectory, DEFAULT_OUTPUT_DIRECTORY), folderName)
                    if (!folder.exists() && !folder.mkdirs()) {
                        error("Could not create output directory")
                    }
                    OutputFolder(fileDirectory = folder)
                } else {
                    OutputFolder(defaultRelativeSubdirectory = folderName)
                }
            }
            is OutputDestination.CustomDirectory -> {
                val folderUri = createOutputDirectoryDocument(destination.uri, folderName)
                OutputFolder(documentDirectoryUri = folderUri)
            }
        }
    }

    private fun outputDirectoryUriFor(
        input: ConversionTaskInput,
        outputProfile: OutputProfile
    ): Uri? {
        return when (val destination = input.outputDestination) {
            OutputDestination.DefaultPublicDirectory -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    null
                } else {
                    val publicDirectory = Environment.getExternalStoragePublicDirectory(
                        defaultPublicDirectoryFor(outputProfile)
                    )
                    Uri.fromFile(File(publicDirectory, DEFAULT_OUTPUT_DIRECTORY))
                }
            }
            is OutputDestination.CustomDirectory -> documentUriForTree(destination.uri)
        }
    }

    private fun outputFolderOpenUri(folder: OutputFolder?): Uri? {
        if (folder == null) return null
        folder.documentDirectoryUri?.let { return it }
        folder.fileDirectory?.let { return Uri.fromFile(it) }
        return null
    }

    private fun documentUriForTree(treeUri: Uri): Uri? {
        return runCatching {
            val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
            DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        }.getOrNull()
    }

    private fun createOutputInFolder(
        folder: OutputFolder,
        displayName: String,
        outputProfile: OutputProfile
    ): Uri {
        folder.fileDirectory?.let { directory ->
            return Uri.fromFile(File(directory, displayName))
        }
        folder.documentDirectoryUri?.let { directoryUri ->
            return DocumentsContract.createDocument(
                contentResolver,
                directoryUri,
                outputProfile.mimeType,
                displayName
            ) ?: error("Could not create output file")
        }
        folder.defaultRelativeSubdirectory?.let { subdirectory ->
            return createDefaultOutput(displayName, outputProfile, subdirectory)
        }
        error("Could not create output directory")
    }

    private fun createDefaultOutput(
        displayName: String,
        outputProfile: OutputProfile,
        relativeSubdirectory: String? = null
    ): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return createLegacyDefaultOutput(outputProfile, displayName, relativeSubdirectory)
        }

        val relativePath = buildString {
            append(defaultPublicDirectoryFor(outputProfile))
            append("/")
            append(DEFAULT_OUTPUT_DIRECTORY)
            if (!relativeSubdirectory.isNullOrBlank()) {
                append("/")
                append(relativeSubdirectory)
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, outputProfile.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return contentResolver.insert(defaultCollectionFor(outputProfile), values)
            ?: error("Could not create output file")
    }

    @Suppress("DEPRECATION")
    private fun createLegacyDefaultOutput(
        outputProfile: OutputProfile,
        displayName: String,
        relativeSubdirectory: String? = null
    ): Uri {
        val publicDirectory = Environment.getExternalStoragePublicDirectory(
            defaultPublicDirectoryFor(outputProfile)
        )
        val outputDirectory = if (relativeSubdirectory.isNullOrBlank()) {
            File(publicDirectory, DEFAULT_OUTPUT_DIRECTORY)
        } else {
            File(File(publicDirectory, DEFAULT_OUTPUT_DIRECTORY), relativeSubdirectory)
        }
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            error("Could not create output directory")
        }
        return Uri.fromFile(File(outputDirectory, displayName))
    }

    private fun defaultCollectionFor(outputProfile: OutputProfile): Uri {
        return when (outputProfile.kind) {
            OutputMediaKind.Audio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            OutputMediaKind.Image -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            OutputMediaKind.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            OutputMediaKind.Document -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
    }

    private fun defaultPublicDirectoryFor(outputProfile: OutputProfile): String {
        return when (outputProfile.kind) {
            OutputMediaKind.Audio -> Environment.DIRECTORY_MUSIC
            OutputMediaKind.Document -> Environment.DIRECTORY_DOCUMENTS
            OutputMediaKind.Image -> Environment.DIRECTORY_PICTURES
            OutputMediaKind.Video -> Environment.DIRECTORY_MOVIES
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

    private fun createOutputDirectoryDocument(
        outputDirectoryUri: Uri,
        displayName: String
    ): Uri {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(outputDirectoryUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(
            outputDirectoryUri,
            treeDocumentId
        )
        return DocumentsContract.createDocument(
            contentResolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            displayName
        ) ?: error("Could not create output directory")
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

    private fun deleteOutputFolderQuietly(folder: OutputFolder?) {
        if (folder == null) return
        folder.fileDirectory?.let { directory ->
            runCatching { directory.deleteRecursively() }
        }
        folder.documentDirectoryUri?.let { uri ->
            runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }
        }
    }

    private fun outputProfileFor(input: ConversionTaskInput): OutputProfile? {
        return when (input.category) {
            ConversionMediaCategory.Video -> {
                when (videoTargetExtensionFor(input.targetFormat)) {
                    "mp4" -> OutputProfile(extension = "mp4", mimeType = MIME_TYPE_MP4, kind = OutputMediaKind.Video)
                    "mkv" -> OutputProfile(extension = "mkv", mimeType = MIME_TYPE_MKV, kind = OutputMediaKind.Video)
                    "mov" -> OutputProfile(extension = "mov", mimeType = MIME_TYPE_MOV, kind = OutputMediaKind.Video)
                    "gif" -> OutputProfile(extension = "gif", mimeType = MIME_TYPE_GIF, kind = OutputMediaKind.Image)
                    else -> null
                }
            }
            ConversionMediaCategory.Audio -> {
                when (audioTargetExtensionFor(input.targetFormat)) {
                    "mp3" -> OutputProfile(extension = "mp3", mimeType = MIME_TYPE_MP3, kind = OutputMediaKind.Audio)
                    "m4a" -> OutputProfile(extension = "m4a", mimeType = MIME_TYPE_M4A, kind = OutputMediaKind.Audio)
                    "wav" -> OutputProfile(extension = "wav", mimeType = MIME_TYPE_WAV, kind = OutputMediaKind.Audio)
                    "flac" -> OutputProfile(extension = "flac", mimeType = MIME_TYPE_FLAC, kind = OutputMediaKind.Audio)
                    "wma" -> OutputProfile(extension = "wma", mimeType = MIME_TYPE_WMA, kind = OutputMediaKind.Audio)
                    else -> null
                }
            }
            ConversionMediaCategory.Image -> {
                when {
                    input.targetFormat.equals("JPG", ignoreCase = true) ||
                        input.targetFormat.equals("JPEG", ignoreCase = true) ->
                        OutputProfile(extension = "jpg", mimeType = MIME_TYPE_JPEG, kind = OutputMediaKind.Image)
                    input.targetFormat.equals("JFIF", ignoreCase = true) ->
                        OutputProfile(extension = "jfif", mimeType = MIME_TYPE_JPEG, kind = OutputMediaKind.Image)
                    input.targetFormat.equals("PNG", ignoreCase = true) ->
                        OutputProfile(extension = "png", mimeType = MIME_TYPE_PNG, kind = OutputMediaKind.Image)
                    input.targetFormat.equals("WEBP", ignoreCase = true) ->
                        OutputProfile(extension = "webp", mimeType = MIME_TYPE_WEBP, kind = OutputMediaKind.Image)
                    input.targetFormat.equals("ICO", ignoreCase = true) ->
                        OutputProfile(extension = "ico", mimeType = MIME_TYPE_ICO, kind = OutputMediaKind.Image)
                    input.targetFormat.equals("PDF", ignoreCase = true) ->
                        OutputProfile(extension = "pdf", mimeType = MIME_TYPE_PDF, kind = OutputMediaKind.Document)
                    else -> null
                }
            }
            ConversionMediaCategory.Pdf -> {
                when {
                    input.targetFormat.equals("JPG", ignoreCase = true) ||
                        input.targetFormat.equals("JPEG", ignoreCase = true) ->
                        OutputProfile(extension = "jpg", mimeType = MIME_TYPE_JPEG, kind = OutputMediaKind.Image)
                    input.targetFormat.equals("PNG", ignoreCase = true) ->
                        OutputProfile(extension = "png", mimeType = MIME_TYPE_PNG, kind = OutputMediaKind.Image)
                    input.targetFormat.equals("WEBP", ignoreCase = true) ->
                        OutputProfile(extension = "webp", mimeType = MIME_TYPE_WEBP, kind = OutputMediaKind.Image)
                    input.targetFormat.equals("PDF", ignoreCase = true) ->
                        OutputProfile(extension = "pdf", mimeType = MIME_TYPE_PDF, kind = OutputMediaKind.Document)
                    input.targetFormat.equals("TXT", ignoreCase = true) ->
                        OutputProfile(extension = "txt", mimeType = MIME_TYPE_TEXT, kind = OutputMediaKind.Document)
                    else -> null
                }
            }
            ConversionMediaCategory.Document -> {
                if (input.targetFormat.equals("PDF", ignoreCase = true)) {
                    OutputProfile(extension = "pdf", mimeType = MIME_TYPE_PDF, kind = OutputMediaKind.Document)
                } else {
                    null
                }
            }
        }
    }

    private fun audioTargetExtensionFor(targetFormat: String): String? {
        val normalized = targetFormat.lowercase(Locale.US)
        return when {
            normalized.contains("mp3") -> "mp3"
            normalized.contains("m4a") || normalized.contains("aac") -> "m4a"
            normalized.contains("wav") -> "wav"
            normalized.contains("flac") -> "flac"
            normalized.contains("wma") -> "wma"
            else -> null
        }
    }

    private fun videoTargetExtensionFor(targetFormat: String): String? {
        val normalized = targetFormat.lowercase(Locale.US)
        return when {
            normalized.contains("mp4") -> "mp4"
            normalized.contains("mkv") -> "mkv"
            normalized.contains("mov") -> "mov"
            normalized.contains("gif") -> "gif"
            else -> null
        }
    }

    private data class OutputProfile(
        val extension: String,
        val mimeType: String,
        val kind: OutputMediaKind
    )

    private sealed interface ImageExportResult {
        data class SingleFile(val file: File) : ImageExportResult

        data class FolderFiles(
            val files: List<File>,
            val outputProfile: OutputProfile,
            val folderName: String
        ) : ImageExportResult
    }

    private fun ImageExportResult.deleteTempFiles() {
        when (this) {
            is ImageExportResult.SingleFile -> file.delete()
            is ImageExportResult.FolderFiles -> files.forEach { it.delete() }
        }
    }

    private data class OutputFolder(
        val defaultRelativeSubdirectory: String? = null,
        val fileDirectory: File? = null,
        val documentDirectoryUri: Uri? = null
    )

    private enum class OutputMediaKind {
        Audio,
        Document,
        Image,
        Video
    }

    private data class FfmpegVideoProfile(
        val videoCodec: String,
        val format: String,
        val useFastStart: Boolean = false,
        val pixelFormat: String = "yuv420p",
        val preset: String = "veryfast",
        val crf: String,
        val videoTag: String? = null
    )

    private data class FfmpegAudioProfile(
        val codec: String,
        val format: String,
        val useFastStart: Boolean = false,
        val supportsBitrate: Boolean = true,
        val supportsSampleRate: Boolean = true,
        val supportsChannelCount: Boolean = true,
        val requiredEncoder: String? = null
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

    private data class GifFrameExtraction(
        val directory: File,
        val rawFrameFile: File,
        val width: Int,
        val height: Int,
        val frameCount: Int,
        val frameByteCount: Int
    ) {
        fun delete() {
            directory.deleteRecursively()
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

    private data class PdfPageSize(
        val width: Int,
        val height: Int
    )

    private data class PdfBitmapSize(
        val width: Int,
        val height: Int
    )

    private data class IcoPngImage(
        val size: Int,
        val pngBytes: ByteArray
    )

    private data class IcoDirectoryEntry(
        val width: Int,
        val height: Int,
        val planes: Int,
        val bitCount: Int,
        val imageSize: Int,
        val imageOffset: Int
    ) {
        val pixelArea: Int = width * height
    }

    private data class PdfRenderProfile(
        val dpi: Float,
        val maxLongSidePixels: Int,
        val maxPixels: Long
    )

    private data class PdfRendererSource(
        val renderer: PdfRenderer,
        val cacheFile: File? = null
    ) {
        fun close() {
            runCatching { renderer.close() }
            cacheFile?.delete()
        }
    }

    private data class PdfBoxCachedInputs(
        val directory: File,
        val files: List<File>
    ) {
        fun delete() {
            directory.deleteRecursively()
        }
    }

    private fun ConversionTaskInput.pdfPasswordAt(index: Int): String? {
        return pdfPasswords.getOrNull(index)
    }

    private fun pageNumberForTextOutput(pageIndex: Int): String {
        return (pageIndex + 1).toString().padStart(3, '0')
    }

    private fun outputNameFor(input: ConversionTaskInput, extension: String): String {
        val baseName = sanitizedBaseNameFor(input)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val shortId = input.fileId.take(8)
        return "${baseName}_converted_${timestamp}_$shortId.$extension"
    }

    private fun outputNameForPage(
        input: ConversionTaskInput,
        extension: String,
        pageIndex: Int,
        pageCount: Int
    ): String {
        val baseName = sanitizedBaseNameFor(input)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val shortId = input.fileId.take(8)
        val width = pageCount.toString().length.coerceAtLeast(3)
        val pageNumber = (pageIndex + 1).toString().padStart(width, '0')
        return "${baseName}_page_${pageNumber}_${timestamp}_$shortId.$extension"
    }

    private fun outputFolderNameForFrames(input: ConversionTaskInput): String {
        val baseName = sanitizedBaseNameFor(input)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val shortId = input.fileId.take(8)
        return "${baseName}_frames_${timestamp}_$shortId"
    }

    private fun outputNameForFrame(
        input: ConversionTaskInput,
        extension: String,
        frameIndex: Int,
        frameCount: Int
    ): String {
        val baseName = sanitizedBaseNameFor(input)
        val width = frameCount.toString().length.coerceAtLeast(3)
        val frameNumber = (frameIndex + 1).toString().padStart(width, '0')
        return "${baseName}_frame_${frameNumber}.$extension"
    }

    private fun sanitizedBaseNameFor(input: ConversionTaskInput): String {
        return input.displayName
            .substringBeforeLast('.', input.displayName)
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "ZenConverter" }
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
        private const val PDF_IMAGE_MAX_LONG_SIDE_PIXELS = 4096
        private const val PDF_IMAGE_MAX_PIXELS = 16_000_000L
        private const val PDF_A4_SHORT_EDGE_PT = 595
        private const val PDF_A4_LONG_EDGE_PT = 842
        private const val PDF_POINTS_PER_INCH = 72f
        private const val PDF_PASSWORD_EXTENSION = 13
        private const val PDF_CACHE_HEADROOM_BYTES = 16L * 1024L * 1024L
        private const val PDF_UNKNOWN_CACHE_MIN_FREE_BYTES = 256L * 1024L * 1024L
        private const val OFFICE_MAX_INPUT_BYTES = 64L * 1024L * 1024L
        private const val FFMPEG_MAX_PROGRESS_BEFORE_SAVE = 0.98f
        private const val FFMPEG_LOG_DRAIN_TIMEOUT_MS = 1_000
        private const val FFMPEG_ENCODER_PROBE_TIMEOUT_MS = 2_000
        private const val FFMPEG_LOG_TAIL_LINES = 16
        private const val FFMPEG_LOG_LINE_LIMIT = 600
        private const val FFMPEG_VIDEO_ENCODER_H264 = "libx264"
        private const val FFMPEG_VIDEO_ENCODER_H265 = "libx265"
        private const val FFMPEG_GIF_ENCODER = "gif"
        private const val FFMPEG_AAC_ENCODER = "aac"
        private const val FFMPEG_DEFAULT_CRF_H264 = "23"
        private const val FFMPEG_DEFAULT_CRF_H265 = "28"
        private const val FFMPEG_VIDEO_GIF_MAX_DURATION_MS = 30_000L
        private const val FFMPEG_VIDEO_GIF_MAX_DURATION_SECONDS = "30"
        private const val FFMPEG_VIDEO_GIF_FRAME_RATE = 30
        private const val FFMPEG_VIDEO_GIF_MAX_FRAMES = 900
        private const val MIME_TYPE_MP4 = "video/mp4"
        private const val MIME_TYPE_MKV = "video/x-matroska"
        private const val MIME_TYPE_MOV = "video/quicktime"
        private const val MIME_TYPE_MP3 = "audio/mpeg"
        private const val MIME_TYPE_M4A = "audio/mp4"
        private const val MIME_TYPE_WAV = "audio/wav"
        private const val MIME_TYPE_FLAC = "audio/flac"
        private const val MIME_TYPE_WMA = "audio/x-ms-wma"
        private const val MIME_TYPE_JPEG = "image/jpeg"
        private const val MIME_TYPE_PNG = "image/png"
        private const val MIME_TYPE_WEBP = "image/webp"
        private const val MIME_TYPE_GIF = "image/gif"
        private const val MIME_TYPE_ICO = "image/vnd.microsoft.icon"
        private const val MIME_TYPE_PDF = "application/pdf"
        private const val MIME_TYPE_TEXT = "text/plain"
        private const val MIME_TYPE_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        private const val MIME_TYPE_PPTX =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        private const val MIME_TYPE_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        private const val DEFAULT_OUTPUT_DIRECTORY = "ZenConverter"
        private const val URI_SCHEME_FILE = "file"
        private const val MIN_MIXABLE_CHANNEL_COUNT = 1
        private const val MAX_MIXABLE_CHANNEL_COUNT = 6
        private const val ICO_TYPE_ICON = 1
        private const val ICO_HEADER_BYTES = 6
        private const val ICO_DIRECTORY_ENTRY_BYTES = 16
        private const val ICO_MAX_DIRECTORY_SIZE = 256
        private const val ICO_MAX_IMAGE_COUNT = 64
        private const val ICO_MAX_PAYLOAD_BYTES = 64 * 1024 * 1024
        private const val ICO_SKIP_BUFFER_BYTES = 8 * 1024
        private const val ICO_BITS_PER_PIXEL = 32
        private const val GIF_FRAME_TEMP_DIRECTORY = "gif-frames"
        private const val GIF_RAW_FRAME_FILE_NAME = "frames.rgba"
        private const val GIF_HEADER_BYTES = 10
        private const val GIF_SIGNATURE_BYTES = 6
        private const val GIF_WIDTH_OFFSET = 6
        private const val GIF_HEIGHT_OFFSET = 8
        private const val GIF_FRAME_MAX_PIXELS = 16_000_000L
        private const val RGBA_BYTES_PER_PIXEL = 4
        private const val ACTION_START = "org.zenconverter.app.conversion.START"
        private const val ACTION_CANCEL = "org.zenconverter.app.conversion.CANCEL"
        private val ICO_IMAGE_SIZES = listOf(16, 32, 48, 64, 128, 256)
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50.toByte(),
            0x4e.toByte(),
            0x47.toByte(),
            0x0d.toByte(),
            0x0a.toByte(),
            0x1a.toByte(),
            0x0a.toByte()
        )
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
        private val WMA_AUDIO_INPUT_EXTENSIONS = setOf("wma", "asf")
        private val COMPATIBILITY_VIDEO_TARGETS = setOf("mkv", "mov", "gif")
        private val OFFICE_INPUT_EXTENSIONS = setOf("docx", "pptx", "xlsx")
        private val OFFICE_MIME_TYPES = mapOf(
            MIME_TYPE_DOCX to "docx",
            MIME_TYPE_PPTX to "pptx",
            MIME_TYPE_XLSX to "xlsx"
        )
        private val PDF_HEADER = "%PDF-".encodeToByteArray()
        private val PDF_BITMAP_PAINT = Paint(
            Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG
        )
        private val ICON_BITMAP_PAINT = Paint(
            Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG
        )
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val FFMPEG_ENCODER_FLAGS_REGEX = Regex("[VAS][A-Z.]{5}")
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
