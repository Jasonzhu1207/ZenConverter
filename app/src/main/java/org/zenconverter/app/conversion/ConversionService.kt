package org.zenconverter.app.conversion
import org.zenconverter.app.R
import org.zenconverter.app.i18n.LocalizedText
import org.zenconverter.app.i18n.localizedText
import org.zenconverter.app.i18n.LocalizedFailure
import org.zenconverter.app.i18n.localizedFailure
import org.zenconverter.app.i18n.AppLanguages
import android.content.res.Configuration
import kotlinx.coroutines.flow.drop


import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.media.MediaExtractor
import android.media.MediaFormat
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
import androidx.annotation.RequiresApi
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.zenconverter.app.MainActivity
import org.zenconverter.app.office.Office2PdfNative
import org.zenconverter.app.office.Office2PdfUnavailableException
import org.zenconverter.app.office.Office2PdfUnsupportedAbiException
import org.zenconverter.app.font.FontFlavor
import org.zenconverter.app.font.FontFormat
import org.zenconverter.app.font.FontFormatDetector
import org.zenconverter.app.font.Woff2Native
import org.zenconverter.app.font.Woff2UnavailableException
import org.zenconverter.app.font.Woff2UnsupportedAbiException
import org.zenconverter.app.font.WoffCodec
import org.zenconverter.app.subtitle.LrcCodec
import org.zenconverter.app.subtitle.SrtCodec
import org.zenconverter.app.subtitle.SubtitleFormat
import org.zenconverter.app.subtitle.SubtitleTextDecoder
import org.zenconverter.app.model.EsrganModelManager
import org.zenconverter.app.model.RealEsrganUpscaler
import org.zenconverter.app.model.RifeModelManager
import org.zenconverter.app.model.RifeInterpolator
import org.zenconverter.app.conversion.VideoFrameInterpolationMode
import org.zenconverter.app.settings.AppPreferences
import java.io.ByteArrayInputStream
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

class ConversionService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var taskIndex = 0
    private var notificationActive = false
    private var notificationTitle: LocalizedText = localizedText(R.string.message_preparing)
    private var notificationProgress = 0
    private var activeFfmpegSession: FFmpegSession? = null
    private var activeTempFile: File? = null
    private var copyThread: Thread? = null
    private var customOutputFallbackOccurred = false
    private var ffmpegKitReady = false
    private var ffmpegKitLoadFailure: Throwable? = null
    private var pdfBoxReady = false
    private val ffmpegEncoderAvailability = mutableMapOf<String, Boolean>()
    private val ffmpegFilterAvailability = mutableMapOf<String, Boolean>()
    private var ffmpegSubtitleFeatureLists: Map<String, Set<String>>? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        serviceScope.launch {
            AppLanguages.revision.drop(1).collect {
                refreshNotificationLanguage()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshNotificationLanguage()
    }

    private fun refreshNotificationLanguage() {
        if (!notificationActive) return
        ensureNotificationChannel()
        updateNotification(notificationTitle, notificationProgress)
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
                notificationActive = true
                // mediaProcessing only exists on Android 15 (API 35)+. On Android 14
                // (API 34) it is unrecognized and would crash with
                // "Starting FGS with type unknown", so fall back to dataSync there.
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> startForeground(
                        NOTIFICATION_ID,
                        buildNotification(localizedText(R.string.message_preparing), 0),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                    )
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> startForeground(
                        NOTIFICATION_ID,
                        buildNotification(localizedText(R.string.message_preparing), 0),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                    else -> startForeground(NOTIFICATION_ID, buildNotification(localizedText(R.string.message_preparing), 0))
                }
                taskIndex = 0
                customOutputFallbackOccurred = false
                processNextTask()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
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
            failCurrentTask(localizedText(R.string.ui_failed))
            return
        }

        val outputProfile = outputProfileFor(input)
        if (outputProfile == null) {
            failCurrentTask(localizedText(R.string.message_only_connected_video_audio_image_pdf_document_and_font_targets_can_run))
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
                        input.category == ConversionMediaCategory.Font -> "Font"
                        input.category == ConversionMediaCategory.Subtitle -> "Subtitle"
                        useCompatibilityEngine -> "Compatibility"
                        else -> "Unrouted"
                    }
                }"
        )

        val tempFile = createTempFileFor(input, outputProfile.extension)
        activeTempFile = tempFile
        ConversionTaskStore.markRunning(taskIndex)
        updateNotification(localizedText(R.string.task_processing), (ConversionTaskStore.aggregateProgress() * 100).toInt())

        if (input.category == ConversionMediaCategory.Image) {
            startImageExport(input, tempFile, outputProfile)
            return
        }

        if (input.category == ConversionMediaCategory.Pdf) {
            if (
                outputProfile.extension.equals("pdf", ignoreCase = true) ||
                outputProfile.extension.equals("txt", ignoreCase = true) ||
                outputProfile.extension.equals("md", ignoreCase = true)
            ) {
                startPdfDocumentExport(input, tempFile, outputProfile)
            } else {
                startPdfImageExport(input, tempFile, outputProfile)
            }
            return
        }

        if (input.category == ConversionMediaCategory.Document) {
            startOfficeDocumentExport(input, tempFile, outputProfile)
            return
        }

        if (input.category == ConversionMediaCategory.Font) {
            startFontExport(input, tempFile, outputProfile)
            return
        }

        if (input.category == ConversionMediaCategory.Subtitle) {
            startSubtitleExport(input, tempFile)
            return
        }

        if (input.category == ConversionMediaCategory.Video && isVideoContactSheetOutput(input)) {
            startVideoContactSheetExport(input, tempFile, outputProfile)
            return
        }

        if (useCompatibilityEngine) {
            startCompatibilityExport(input, tempFile)
            return
        }

        tempFile.delete()
        activeTempFile = null
        failCurrentTask(localizedText(R.string.message_only_connected_video_audio_image_pdf_document_font_and_subtitle_targets_can_run))
    }

    private fun startVideoContactSheetExport(
        input: ConversionTaskInput,
        tempFile: File,
        outputProfile: OutputProfile
    ) {
        serviceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    writeVideoContactSheet(input, tempFile, outputProfile)
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
                Log.w(TAG, "Could not create video contact sheet", exception)
                failCurrentTask(exception.localizedFailure(R.string.message_video_contact_sheet_failed))
            }
        }
    }

    private fun writeVideoContactSheet(
        input: ConversionTaskInput,
        tempFile: File,
        outputProfile: OutputProfile
    ) {
        val displayLocale = AppLanguages.localizedContext(this).resources.configuration.locales[0]
        val pfd = runCatching { contentResolver.openFileDescriptor(input.inputUri, "r") }.getOrNull()
        val retriever = MediaMetadataRetriever()
        try {
            runCatching {
                retriever.setDataSource(this, input.inputUri)
            }.onFailure {
                if (pfd != null) {
                    retriever.setDataSource(pfd.fileDescriptor)
                }
            }

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: input.inputInfo?.durationMs
                ?: 0L
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                ?: input.inputInfo?.width
                ?: 1920
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                ?: input.inputInfo?.height
                ?: 1080
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val isRotated = rotation == 90 || rotation == 270
            val displayWidth = if (isRotated) rawHeight else rawWidth
            val displayHeight = if (isRotated) rawWidth else rawHeight
            val captureFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
                ?: input.inputInfo?.frameRate
            val totalBitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                ?: input.inputInfo?.bitrateBitsPerSecond

            var videoCodecName = localizedText(R.string.contact_sheet_unknown_codec).resolve(this)
            var videoProfile = ""
            var videoPixFmt = ""
            var videoBitrateKbps: Long? = null
            var audioCodecName = ""
            var audioSampleRate = ""
            var audioChannels = ""
            var audioBitrateKbps: Long? = null

            val ffmpegFailure = ensureFfmpegKitReady()
            if (ffmpegFailure == null) {
                val inputSource = runCatching { openFfmpegInputSource(input.inputUri) }.getOrNull()
                if (inputSource != null) {
                    try {
                        val mediaInfo = runCatching {
                            FFprobeKit.getMediaInformation(inputSource.path, 2000)?.getMediaInformation()
                        }.getOrNull()
                        if (mediaInfo != null) {
                            val streams = mediaInfo.getStreams().orEmpty()
                            val vStream = streams.firstOrNull { it.getType().equals("video", ignoreCase = true) }
                            if (vStream != null) {
                                videoCodecName = vStream.getCodec().orEmpty().ifBlank { videoCodecName }
                                videoProfile = vStream.getStringProperty("profile").orEmpty()
                                videoPixFmt = vStream.getFormat().orEmpty()
                                videoBitrateKbps = vStream.getBitrate()?.toLongOrNull()?.let { it / 1000 }
                            }
                            val aStream = streams.firstOrNull { it.getType().equals("audio", ignoreCase = true) }
                            if (aStream != null) {
                                audioCodecName = aStream.getCodec().orEmpty()
                                audioSampleRate = aStream.getSampleRate().orEmpty().let { rate ->
                                    rate.toIntOrNull()?.let { String.format(displayLocale, "%.1f kHz", it / 1000.0) } ?: rate
                                }
                                val chCount = aStream.getNumberProperty("channels") ?: 2L
                                val chLayout = aStream.getChannelLayout().orEmpty()
                                val channelCount = LocalizedText.Quantity(
                                    R.plurals.contact_sheet_audio_channel_count, chCount.toInt(), listOf(chCount)
                                )
                                audioChannels = if (chLayout.isNotBlank()) {
                                    localizedText(R.string.format_detail_parenthesized, channelCount, chLayout).resolve(this)
                                } else channelCount.resolve(this)
                                audioBitrateKbps = aStream.getBitrate()?.toLongOrNull()?.let { it / 1000 }
                            }
                        }
                    } finally {
                        inputSource.close()
                    }
                }
            }

            if (audioCodecName.isBlank()) {
                val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                if (hasAudio != null) {
                    audioCodecName = localizedText(R.string.contact_sheet_audio_stream).resolve(this)
                }
            }

            val startSeconds = input.videoOptions.trimRange.startSeconds ?: 0.0
            val endSeconds = input.videoOptions.trimRange.endSeconds
            val effectiveStartMs = (startSeconds * 1000).toLong().coerceAtLeast(0L)
            val effectiveEndMs = if (endSeconds != null && endSeconds > startSeconds) {
                (endSeconds * 1000).toLong().coerceAtMost(durationMs)
            } else {
                durationMs
            }
            val effectiveDurationMs = (effectiveEndMs - effectiveStartMs).coerceAtLeast(1000L)

            val grid = input.contactSheetOptions.grid
            val rows = grid.rows
            val cols = grid.cols
            val frameCount = rows * cols

            val sheetWidth = 2048
            val margin = 20
            val gap = 12
            val availableWidth = sheetWidth - (margin * 2) - ((cols - 1) * gap)
            val cellWidth = availableWidth / cols
            val cellHeight = (cellWidth * (displayHeight.toFloat() / displayWidth)).toInt().coerceAtLeast(80)

            val includeHeader = input.contactSheetOptions.includeHeader
            val headerHeight = if (includeHeader) 160 else 0
            val sheetHeight = headerHeight + (margin * 2) + (rows * cellHeight) + ((rows - 1) * gap)

            val stepMs = effectiveDurationMs / (frameCount + 1)
            val frameBitmaps = mutableListOf<Pair<Bitmap, Long>>()

            for (i in 0 until frameCount) {
                if (ConversionTaskStore.isCancelled()) {
                    frameBitmaps.forEach { it.first.recycle() }
                    return
                }
                val timeMs = effectiveStartMs + (i + 1) * stepMs
                val timeUs = timeMs * 1000L

                var bmp: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    runCatching {
                        retriever.getScaledFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            cellWidth,
                            cellHeight
                        )
                    }.getOrNull()
                } else null

                if (bmp == null) {
                    bmp = runCatching {
                        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }.getOrNull()
                    if (bmp != null) {
                        val scaled = Bitmap.createScaledBitmap(bmp, cellWidth, cellHeight, true)
                        if (scaled != bmp) bmp.recycle()
                        bmp = scaled
                    }
                }

                if (bmp != null && rotation != 0) {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                    if (rotated != bmp) bmp.recycle()
                    bmp = rotated
                }

                if (bmp != null) {
                    frameBitmaps.add(bmp to timeMs)
                }

                ConversionTaskStore.updateProgress(taskIndex, 0.05f + 0.80f * ((i + 1).toFloat() / frameCount))
            }

            val sheetBitmap = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheetBitmap)
            canvas.drawColor(Color.parseColor("#16181D"))

            if (includeHeader) {
                val headerRect = RectF(0f, 0f, sheetWidth.toFloat(), headerHeight.toFloat())
                val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#1C1F26")
                }
                canvas.drawRect(headerRect, headerBgPaint)

                val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#2B303C")
                    strokeWidth = 2f
                }
                canvas.drawLine(0f, headerHeight.toFloat(), sheetWidth.toFloat(), headerHeight.toFloat(), dividerPaint)

                val headerPaddingX = margin.toFloat()
                val headerPaddingY = 24f
                val labelTextSize = 22f

                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#94A3B8")
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textSize = labelTextSize
                }
                val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#F8FAFC")
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    textSize = labelTextSize
                }
                val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#38BDF8")
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textSize = labelTextSize
                }
                val separatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#475569")
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    textSize = labelTextSize
                }

                fun drawSegments(segments: List<Pair<String, Paint>>, x: Float, y: Float) {
                    var curX = x
                    for ((text, paint) in segments) {
                        canvas.drawText(text, curX, y, paint)
                        curX += paint.measureText(text)
                    }
                }

                val lineLeading = 30f
                var curY = headerPaddingY + labelTextSize

                drawSegments(listOf(
                    localizedText(R.string.contact_sheet_file).resolve(this@ConversionService) to labelPaint,
                    input.displayName to valuePaint
                ), headerPaddingX, curY)
                curY += lineLeading

                val fileSizeStr = formatFileSize(input.inputInfo?.sizeBytes ?: pfd?.statSize ?: 0L)
                val durationStr = formatDuration(durationMs)
                drawSegments(listOf(
                    localizedText(R.string.contact_sheet_size).resolve(this@ConversionService) to labelPaint,
                    fileSizeStr to valuePaint,
                    "  |  " to separatorPaint,
                    localizedText(R.string.contact_sheet_duration).resolve(this@ConversionService) to labelPaint,
                    durationStr to accentPaint
                ), headerPaddingX, curY)
                curY += lineLeading

                val codecLabel = if (videoProfile.isNotBlank()) "$videoCodecName ($videoProfile)" else videoCodecName
                val fpsStr = if (captureFps != null && captureFps > 0) String.format(displayLocale, "%.2f fps", captureFps) else String.format(displayLocale, "%.2f fps", 30.0)
                val vBitrateStr = videoBitrateKbps?.let { String.format(displayLocale, "%d kbps", it) }
                    ?: totalBitrateBps?.let { String.format(displayLocale, "%.2f Mbps", it / 1_000_000.0) }
                    ?: localizedText(R.string.text_option_value_auto).resolve(this)
                val resStr = String.format(displayLocale, "%dx%d", displayWidth, displayHeight)
                val line3Segments = mutableListOf(
                    localizedText(R.string.contact_sheet_video).resolve(this@ConversionService) to labelPaint,
                    codecLabel to valuePaint,
                    "  |  " to separatorPaint,
                    localizedText(R.string.contact_sheet_resolution).resolve(this@ConversionService) to labelPaint,
                    resStr to accentPaint,
                    "  |  " to separatorPaint,
                    localizedText(R.string.contact_sheet_frame_rate).resolve(this@ConversionService) to labelPaint,
                    fpsStr to valuePaint
                )
                if (videoPixFmt.isNotBlank()) {
                    line3Segments.add("  |  " to separatorPaint)
                    line3Segments.add(localizedText(R.string.contact_sheet_bit_depth).resolve(this@ConversionService) to labelPaint)
                    line3Segments.add(videoPixFmt to valuePaint)
                }
                line3Segments.add("  |  " to separatorPaint)
                line3Segments.add(localizedText(R.string.contact_sheet_bitrate).resolve(this@ConversionService) to labelPaint)
                line3Segments.add(vBitrateStr to valuePaint)
                drawSegments(line3Segments, headerPaddingX, curY)
                curY += lineLeading

                if (audioCodecName.isNotBlank()) {
                    val line4Segments = mutableListOf(
                        localizedText(R.string.contact_sheet_audio).resolve(this@ConversionService) to labelPaint,
                        audioCodecName to valuePaint
                    )
                    if (audioSampleRate.isNotBlank()) {
                        line4Segments.add("  |  " to separatorPaint)
                        line4Segments.add(localizedText(R.string.contact_sheet_sample_rate).resolve(this@ConversionService) to labelPaint)
                        line4Segments.add(audioSampleRate to valuePaint)
                    }
                    if (audioChannels.isNotBlank()) {
                        line4Segments.add("  |  " to separatorPaint)
                        line4Segments.add(localizedText(R.string.contact_sheet_channels).resolve(this@ConversionService) to labelPaint)
                        line4Segments.add(audioChannels to valuePaint)
                    }
                    if (audioBitrateKbps != null) {
                        line4Segments.add("  |  " to separatorPaint)
                        line4Segments.add(localizedText(R.string.contact_sheet_bitrate).resolve(this@ConversionService) to labelPaint)
                        line4Segments.add(String.format(displayLocale, "%d kbps", audioBitrateKbps) to valuePaint)
                    }
                    drawSegments(line4Segments, headerPaddingX, curY)
                }

                val wmText = localizedText(R.string.contact_sheet_generated_by, getString(R.string.project_identifier)).resolve(this)
                val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#94A3B8")
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    textSize = 20f
                }
                val wmTextWidth = wmPaint.measureText(wmText)
                val wmMetrics = wmPaint.fontMetrics
                val wmTextHeight = wmMetrics.descent - wmMetrics.ascent

                val logoBitmap = runCatching {
                    BitmapFactory.decodeResource(resources, R.drawable.zenconverter)
                }.getOrNull()
                val logoSize = 28
                val scaledLogo = if (logoBitmap != null) {
                    Bitmap.createScaledBitmap(logoBitmap, logoSize, logoSize, true)
                } else null

                val pillPadX = 16f
                val pillPadY = 10f
                val logoSpacing = if (scaledLogo != null) 10f else 0f
                val logoW = if (scaledLogo != null) logoSize.toFloat() else 0f
                val pillWidth = pillPadX * 2 + logoW + logoSpacing + wmTextWidth
                val pillHeight = (wmTextHeight + pillPadY * 2).coerceAtLeast(logoSize + pillPadY * 2)

                val pillRight = (sheetWidth - margin).toFloat()
                val pillCenterY = headerHeight / 2f
                val pillTop = pillCenterY - pillHeight / 2f
                val pillBottom = pillCenterY + pillHeight / 2f
                val pillLeft = pillRight - pillWidth

                val pillRect = RectF(pillLeft, pillTop, pillRight, pillBottom)
                val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#262B36")
                }
                val pillRadius = pillHeight / 2f
                canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillBgPaint)

                val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#384152")
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                }
                canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillBorderPaint)

                var curPillX = pillLeft + pillPadX
                if (scaledLogo != null) {
                    val logoTop = pillCenterY - logoSize / 2f
                    val logoPath = Path().apply {
                        addRoundRect(RectF(curPillX, logoTop, curPillX + logoSize, logoTop + logoSize), 6f, 6f, Path.Direction.CW)
                    }
                    canvas.save()
                    canvas.clipPath(logoPath)
                    canvas.drawBitmap(scaledLogo, curPillX, logoTop, null)
                    canvas.restore()
                    curPillX += logoSize + logoSpacing
                }
                val textY = pillCenterY + (wmTextHeight / 2f) - wmMetrics.descent
                canvas.drawText(wmText, curPillX, textY, wmPaint)
            }

            val includeTimestamp = input.contactSheetOptions.includeTimestamp
            val tsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 20f
            }
            val tsMetrics = tsPaint.fontMetrics
            val tsTextHeight = tsMetrics.descent - tsMetrics.ascent
            val tsBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 0, 0, 0)
            }

            for (idx in frameBitmaps.indices) {
                val (frameBmp, timeMs) = frameBitmaps[idx]
                val row = idx / cols
                val col = idx % cols
                val cellLeft = (margin + col * (cellWidth + gap)).toFloat()
                val cellTop = (headerHeight + margin + row * (cellHeight + gap)).toFloat()
                val cellRect = RectF(cellLeft, cellTop, cellLeft + cellWidth, cellTop + cellHeight)

                val cellPath = Path().apply {
                    addRoundRect(cellRect, 8f, 8f, Path.Direction.CW)
                }
                canvas.save()
                canvas.clipPath(cellPath)
                canvas.drawBitmap(frameBmp, null, cellRect, null)
                canvas.restore()

                if (includeTimestamp) {
                    val timeStr = formatTimestamp(timeMs)
                    val tsTextWidth = tsPaint.measureText(timeStr)
                    val tsPadX = 10f
                    val tsPadY = 6f
                    val tsWidth = tsTextWidth + tsPadX * 2
                    val tsHeight = tsTextHeight + tsPadY * 2
                    val tsRight = cellRect.right - 10f
                    val tsBottom = cellRect.bottom - 10f
                    val tsLeft = tsRight - tsWidth
                    val tsTop = tsBottom - tsHeight

                    val tsBadgeRect = RectF(tsLeft, tsTop, tsRight, tsBottom)
                    canvas.drawRoundRect(tsBadgeRect, 6f, 6f, tsBgPaint)
                    canvas.drawText(timeStr, tsLeft + tsPadX, tsBottom - tsPadY - tsMetrics.descent, tsPaint)
                }

                frameBmp.recycle()
            }

            tempFile.outputStream().use { outStream ->
                if (outputProfile.extension.equals("png", ignoreCase = true)) {
                    sheetBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                } else {
                    sheetBitmap.compress(Bitmap.CompressFormat.JPEG, 92, outStream)
                }
            }
            sheetBitmap.recycle()
        } finally {
            runCatching { retriever.release() }
            runCatching { pfd?.close() }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        val displayLocale = AppLanguages.localizedContext(this).resources.configuration.locales[0]
        if (bytes <= 0) return String.format(displayLocale, "%d B", 0)
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(displayLocale, "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun formatDuration(durationMs: Long): String {
        val displayLocale = AppLanguages.localizedContext(this).resources.configuration.locales[0]
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(displayLocale, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(displayLocale, "%02d:%02d", minutes, seconds)
        }
    }

    private fun formatTimestamp(timestampMs: Long): String {
        val displayLocale = AppLanguages.localizedContext(this).resources.configuration.locales[0]
        val totalSeconds = (timestampMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val hundredths = ((timestampMs % 1000) / 10).coerceIn(0, 99)
        return if (hours > 0) {
            String.format(displayLocale, "%02d:%02d:%02d.%02d", hours, minutes, seconds, hundredths)
        } else {
            String.format(displayLocale, "%02d:%02d.%02d", minutes, seconds, hundredths)
        }
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
                failCurrentTask(pdfDocumentFailureMessageFor(exception, input, outputProfile))
            }
        }
    }

    private fun startOfficeDocumentExport(
        input: ConversionTaskInput,
        tempFile: File,
        outputProfile: OutputProfile
    ) {
        serviceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    writeOfficeDocumentExport(input, tempFile, outputProfile)
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
        outputFile: File,
        outputProfile: OutputProfile
    ) {
        val pdfBytes = convertOfficeInputToPdfBytes(input)
        when (outputProfile.extension.lowercase(Locale.US)) {
            "pdf" -> {
                outputFile.outputStream().use { output ->
                    output.write(pdfBytes)
                    output.flush()
                }
                throwIfConversionCancelled()
                updateImageProgress(0.95f)
            }
            "txt" -> writePdfTextFileFromBytes(
                pdfBytes = pdfBytes,
                outputFile = outputFile,
                title = markdownTitleFor(input),
                format = TextDocumentFormat.PlainText
            )
            "md" -> writePdfTextFileFromBytes(
                pdfBytes = pdfBytes,
                outputFile = outputFile,
                title = markdownTitleFor(input),
                format = TextDocumentFormat.Markdown
            )
            else -> throw LocalizedFailure(localizedText(R.string.text_task_message_office_conversion_failed))
        }
    }

    private fun convertOfficeInputToPdfBytes(input: ConversionTaskInput): ByteArray {
        val extension = officeInputExtensionFor(input)
            ?: throw LocalizedFailure(localizedText(R.string.text_task_message_unsupported_office_document))

        throwIfConversionCancelled()
        val inputBytes = readOfficeInputBytes(input)
        updateImageProgress(0.25f)

        throwIfConversionCancelled()
        val pdfBytes = Office2PdfNative.convert(this, inputBytes, extension)
        throwIfConversionCancelled()

        if (!looksLikePdf(pdfBytes)) {
            throw LocalizedFailure(localizedText(R.string.message_office_engine_did_not_return_a_pdf))
        }
        throwIfConversionCancelled()
        updateImageProgress(0.55f)
        return pdfBytes
    }

    private fun startFontExport(
        input: ConversionTaskInput,
        tempFile: File,
        outputProfile: OutputProfile
    ) {
        serviceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    writeFontExport(input, tempFile, outputProfile)
                }
            }.onSuccess { result ->
                if (ConversionTaskStore.isCancelled()) {
                    tempFile.delete()
                    cancelRun()
                    return@onSuccess
                }
                ConversionTaskStore.updateProgress(taskIndex, PROGRESS_BEFORE_SAVE)
                saveCompletedFontExport(input, tempFile, result)
            }.onFailure { exception ->
                tempFile.delete()
                if (exception is CancellationException) {
                    return@onFailure
                }
                Log.w(TAG, "Could not run font export", exception)
                failCurrentTask(fontFailureMessageFor(exception))
            }
        }
    }

    private data class FontExportResult(
        val extension: String,
        val mimeType: String
    )

    private fun writeFontExport(
        input: ConversionTaskInput,
        outputFile: File,
        outputProfile: OutputProfile
    ): FontExportResult {
        throwIfConversionCancelled()
        val inputBytes = readFontInputBytes(input)
        updateImageProgress(0.25f)

        throwIfConversionCancelled()
        val sourceFormat = FontFormatDetector.detect(inputBytes)
            ?: throw LocalizedFailure(localizedText(R.string.message_unsupported_font_file))

        val targetExtension = outputProfile.extension.lowercase(Locale.US)
        val outputBytes = when (targetExtension) {
            "woff2" -> {
                if (sourceFormat != FontFormat.Sfnt) {
                    throw LocalizedFailure(localizedText(R.string.message_woff2_output_requires_a_ttf_or_otf_input))
                }
                Woff2Native.compressSfnt(inputBytes)
            }
            "woff" -> {
                if (sourceFormat != FontFormat.Sfnt) {
                    throw LocalizedFailure(localizedText(R.string.message_woff_output_requires_a_ttf_or_otf_input))
                }
                WoffCodec.encode(inputBytes)
            }
            "ttf" -> when (sourceFormat) {
                FontFormat.Woff2 -> Woff2Native.decompressToSfnt(inputBytes)
                FontFormat.Woff -> WoffCodec.decode(inputBytes)
                FontFormat.Sfnt -> throw LocalizedFailure(localizedText(R.string.message_input_is_already_an_uncompressed_font))
            }
            else -> throw LocalizedFailure(localizedText(R.string.message_unsupported_font_output_format))
        }

        throwIfConversionCancelled()
        val resolvedExtension = if (targetExtension == "ttf") {
            FontFlavor.extensionForSfnt(outputBytes)
        } else {
            targetExtension
        }

        outputFile.outputStream().use { output ->
            output.write(outputBytes)
            output.flush()
        }
        throwIfConversionCancelled()
        updateImageProgress(0.95f)
        return FontExportResult(
            extension = resolvedExtension,
            mimeType = fontMimeTypeFor(resolvedExtension)
        )
    }

    private fun readFontInputBytes(input: ConversionTaskInput): ByteArray {
        val sourceSize = queryOpenableSize(input.inputUri)
        sourceSize?.let { sizeBytes ->
            if (sizeBytes > FONT_MAX_INPUT_BYTES) {
                throw LocalizedFailure(localizedText(R.string.message_font_file_is_too_large))
            }
        }

        val initialCapacity = sourceSize
            ?.coerceAtMost(FONT_MAX_INPUT_BYTES)
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
                if (totalBytes > FONT_MAX_INPUT_BYTES) {
                    throw LocalizedFailure(localizedText(R.string.message_font_file_is_too_large))
                }
                output.write(buffer, 0, read)
            }
        } ?: throw LocalizedFailure(localizedText(R.string.text_task_message_input_file_could_not_be_opened))

        if (output.size() == 0) throw LocalizedFailure(localizedText(R.string.text_task_message_input_file_is_empty))
        return output.toByteArray()
    }

    private fun fontMimeTypeFor(extension: String): String {
        return when (extension.lowercase(Locale.US)) {
            "ttf" -> MIME_TYPE_TTF
            "otf" -> MIME_TYPE_OTF
            "woff" -> MIME_TYPE_WOFF
            "woff2" -> MIME_TYPE_WOFF2
            else -> "application/octet-stream"
        }
    }

    private fun fontFailureMessageFor(exception: Throwable): LocalizedText {
        return when (exception) {
            is Woff2UnsupportedAbiException -> localizedText(R.string.message_font_converter_is_only_available_on_arm64_v8a_devices)
            is Woff2UnavailableException, is UnsatisfiedLinkError -> localizedText(R.string.message_font_converter_could_not_start_on_this_device)
            else -> exception.localizedFailure(R.string.message_font_conversion_failed)
        }
    }

    private fun saveCompletedFontExport(
        input: ConversionTaskInput,
        tempFile: File,
        result: FontExportResult
    ) {
        ConversionTaskStore.markSaving(taskIndex)
        updateNotification(localizedText(R.string.task_saving), (ConversionTaskStore.aggregateProgress() * 100).toInt())

        copyThread = Thread {
            var outputUri: Uri? = null
            try {
                val outputProfile = OutputProfile(
                    extension = result.extension,
                    mimeType = result.mimeType,
                    kind = OutputMediaKind.Font
                )
                val outputDisplayName = outputNameFor(input, result.extension)
                val tempFileSizeBytes = tempFile.length().takeIf { it >= 0L }
                val createdOutput = createOutput(input, outputDisplayName, outputProfile)
                val createdOutputUri = createdOutput.uri
                outputUri = createdOutputUri
                copyFileToOutput(tempFile, createdOutputUri)
                finalizeOutput(createdOutput.isDefaultPublicDestination, createdOutputUri, outputProfile.mimeType)
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
                        outputDirectoryUri = createdOutput.outputDirectoryUri,
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
                Log.w(TAG, "Could not save font export", exception)
                deleteOutputQuietly(outputUri)
                tempFile.delete()
                handler.post {
                    activeTempFile = null
                    failCurrentTask(localizedText(R.string.text_task_message_could_not_save_output_file))
                }
            }
        }.also { it.start() }
    }

    private fun writePdfDocumentExport(
        input: ConversionTaskInput,
        outputFile: File,
        outputProfile: OutputProfile
    ) {
        if (TargetId.fromKey(input.targetFormat) == TargetId.PdfCompress) {
            writeCompressedPdf(input, outputFile)
            return
        }

        when (input.pdfSecurityOptions.mode) {
            PdfSecurityMode.Encrypt -> {
                writePdfSecurityFile(input, outputFile, encrypt = true)
                return
            }
            PdfSecurityMode.Decrypt -> {
                writePdfSecurityFile(input, outputFile, encrypt = false)
                return
            }
            PdfSecurityMode.None -> Unit
        }

        when (outputProfile.extension.lowercase(Locale.US)) {
            "pdf" -> writeMergedPdf(input, outputFile)
            "txt" -> writePdfTextFile(input, outputFile, TextDocumentFormat.PlainText)
            "md" -> writePdfTextFile(input, outputFile, TextDocumentFormat.Markdown)
            else -> throw LocalizedFailure(localizedText(R.string.text_task_message_pdf_conversion_failed))
        }
    }

    private fun writeCompressedPdf(
        input: ConversionTaskInput,
        outputFile: File
    ) {
        val inputUris = input.inputUris.ifEmpty { listOf(input.inputUri) }
        if (inputUris.size != 1) throw LocalizedFailure(localizedText(R.string.message_select_one_pdf_to_compress))

        throwIfConversionCancelled()
        ensurePdfBoxReady()
        val cachedInputs = cachePdfInputsForPdfBox(input, inputUris)
        var document: PDDocument? = null

        try {
            document = loadPdfBoxDocument(cachedInputs.files.first(), input.pdfPasswordAt(0))
            val pageCount = document.numberOfPages
            if (pageCount <= 0) throw LocalizedFailure(localizedText(R.string.text_task_message_pdf_has_no_pages))
            throwIfConversionCancelled()

            val preset = input.pdfOptions.compressionPreset
            val (jpegQuality, maxDimension) = when (preset) {
                PdfCompressionPreset.HighQuality -> Pair(0.85f, 2160)
                PdfCompressionPreset.Balanced -> Pair(0.70f, 1440)
                PdfCompressionPreset.SmallFile -> Pair(0.50f, 1080)
            }

            val visitedCosObjects = mutableSetOf<COSBase>()
            for (pageIndex in 0 until pageCount) {
                throwIfConversionCancelled()
                val page = document.getPage(pageIndex)
                page.cosObject?.removeItem(COSName.THUMB)
                val resources = page.resources
                if (resources != null) {
                    compressPdfResources(
                        document = document,
                        resources = resources,
                        jpegQuality = jpegQuality,
                        maxDimension = maxDimension,
                        visited = visitedCosObjects
                    )
                }
                updateImageProgress(
                    progressForIndexedWork(pageIndex, pageCount, 0.08f, 0.90f)
                )
            }

            throwIfConversionCancelled()
            document.save(outputFile)
            throwIfConversionCancelled()
            updateImageProgress(0.95f)
        } catch (throwable: Throwable) {
            outputFile.delete()
            throw throwable
        } finally {
            runCatching { document?.close() }
            cachedInputs.delete()
        }
    }

    private fun compressPdfResources(
        document: PDDocument,
        resources: PDResources,
        jpegQuality: Float,
        maxDimension: Int,
        visited: MutableSet<COSBase>
    ) {
        val xObjectNames = resources.xObjectNames ?: return
        for (name in xObjectNames) {
            throwIfConversionCancelled()
            val cosDict = resources.cosObject
            val cosObject = cosDict?.getDictionaryObject(name)
            if (cosObject != null && !visited.add(cosObject)) {
                continue
            }
            if (resources.isImageXObject(name)) {
                val xObject = runCatching { resources.getXObject(name) }.getOrNull()
                if (xObject is PDImageXObject) {
                    compressPdfImageXObject(
                        document = document,
                        resources = resources,
                        name = name,
                        imageXObject = xObject,
                        jpegQuality = jpegQuality,
                        maxDimension = maxDimension
                    )
                }
            } else {
                val xObject = runCatching { resources.getXObject(name) }.getOrNull()
                if (xObject is PDFormXObject) {
                    val formResources = xObject.resources
                    if (formResources != null) {
                        compressPdfResources(
                            document = document,
                            resources = formResources,
                            jpegQuality = jpegQuality,
                            maxDimension = maxDimension,
                            visited = visited
                        )
                    }
                }
            }
        }
    }

    private fun compressPdfImageXObject(
        document: PDDocument,
        resources: PDResources,
        name: COSName,
        imageXObject: PDImageXObject,
        jpegQuality: Float,
        maxDimension: Int
    ) {
        val width = imageXObject.width
        val height = imageXObject.height
        if (width <= 64 && height <= 64) return
        if (imageXObject.isStencil) return

        val originalBitmap = runCatching { imageXObject.image }.getOrNull() ?: return
        try {
            val (scaledBitmap, needsRecycle) = downscalePdfBitmapIfNeeded(originalBitmap, maxDimension)
            try {
                val newImageXObject = JPEGFactory.createFromImage(
                    document,
                    scaledBitmap,
                    jpegQuality
                )
                resources.put(name, newImageXObject)
            } finally {
                if (needsRecycle) {
                    scaledBitmap.recycle()
                }
            }
        } finally {
            originalBitmap.recycle()
        }
    }

    private fun downscalePdfBitmapIfNeeded(
        source: Bitmap,
        maxDimension: Int
    ): Pair<Bitmap, Boolean> {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) {
            return Pair(source, false)
        }
        val scale = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        return Pair(scaled, scaled !== source)
    }

    private fun writeMergedPdf(
        input: ConversionTaskInput,
        outputFile: File
    ) {
        val inputUris = input.inputUris.ifEmpty { listOf(input.inputUri) }
        if (inputUris.size < 2) throw LocalizedFailure(localizedText(R.string.text_task_message_select_at_least_two_pdfs_to_merge))

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
                    if (source.numberOfPages <= 0) throw LocalizedFailure(localizedText(R.string.text_task_message_pdf_has_no_pages))
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
        outputFile: File,
        format: TextDocumentFormat
    ) {
        val inputUris = input.inputUris.ifEmpty { listOf(input.inputUri) }
        if (inputUris.size != 1) throw LocalizedFailure(localizedText(R.string.text_task_message_pdf_text_extraction_failed))

        throwIfConversionCancelled()
        ensurePdfBoxReady()
        val cachedInputs = cachePdfInputsForPdfBox(input, inputUris)
        var document: PDDocument? = null

        try {
            document = loadPdfBoxDocument(cachedInputs.files.first(), input.pdfPasswordAt(0))
            writeExtractedPdfTextFile(
                document = document,
                outputFile = outputFile,
                title = markdownTitleFor(input),
                format = format,
                progressStart = 0.08f,
                progressEnd = 0.92f
            )
        } catch (throwable: Throwable) {
            outputFile.delete()
            throw throwable
        } finally {
            runCatching { document?.close() }
            cachedInputs.delete()
        }
    }

    private fun writePdfTextFileFromBytes(
        pdfBytes: ByteArray,
        outputFile: File,
        title: String,
        format: TextDocumentFormat
    ) {
        throwIfConversionCancelled()
        ensurePdfBoxReady()
        try {
            ByteArrayInputStream(pdfBytes).use { input ->
                PDDocument.load(input, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                    writeExtractedPdfTextFile(
                        document = document,
                        outputFile = outputFile,
                        title = title,
                        format = format,
                        progressStart = 0.58f,
                        progressEnd = 0.95f
                    )
                }
            }
        } catch (throwable: Throwable) {
            outputFile.delete()
            throw throwable
        }
    }

    private fun writeExtractedPdfTextFile(
        document: PDDocument,
        outputFile: File,
        title: String,
        format: TextDocumentFormat,
        progressStart: Float,
        progressEnd: Float
    ) {
        val pageCount = document.numberOfPages
        if (pageCount <= 0) throw LocalizedFailure(localizedText(R.string.text_task_message_pdf_has_no_pages))

        val stripper = PDFTextStripper()
        var hasSelectableText = false

        outputFile.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
            if (format == TextDocumentFormat.Markdown) {
                writer.write("# $title")
                writer.newLine()
                writer.newLine()
            }

            for (pageIndex in 0 until pageCount) {
                throwIfConversionCancelled()
                stripper.startPage = pageIndex + 1
                stripper.endPage = pageIndex + 1
                val pageText = stripper.getText(document).trimEnd()
                throwIfConversionCancelled()

                if (pageText.isNotBlank()) {
                    hasSelectableText = true
                }

                when (format) {
                    TextDocumentFormat.PlainText -> {
                        writer.write("--- Page ${pageNumberForTextOutput(pageIndex)} ---")
                        writer.newLine()
                    }
                    TextDocumentFormat.Markdown -> {
                        writer.write("## Page ${pageIndex + 1}")
                        writer.newLine()
                        writer.newLine()
                    }
                }
                if (pageText.isNotEmpty()) {
                    writer.write(pageText)
                    writer.newLine()
                }
                writer.newLine()
                writer.flush()
                throwIfConversionCancelled()

                updateImageProgress(progressForIndexedWork(pageIndex, pageCount, progressStart, progressEnd))
            }
        }

        throwIfConversionCancelled()
        if (!hasSelectableText) {
            throw LocalizedFailure(localizedText(R.string.text_task_message_pdf_has_no_selectable_text_ocr_is_not_included))
        }
        updateImageProgress(0.95f)
    }

    private fun writePdfSecurityFile(
        input: ConversionTaskInput,
        outputFile: File,
        encrypt: Boolean
    ) {
        val inputUris = input.inputUris.ifEmpty { listOf(input.inputUri) }
        if (inputUris.size != 1) error(if (encrypt) localizedText(R.string.text_task_message_pdf_encryption_failed) else localizedText(R.string.text_task_message_pdf_decryption_failed))

        throwIfConversionCancelled()
        ensurePdfBoxReady()
        val cachedInputs = cachePdfInputsForPdfBox(input, inputUris)
        var document: PDDocument? = null

        try {
            document = loadPdfBoxDocument(cachedInputs.files.first(), input.pdfPasswordAt(0))
            if (document.numberOfPages <= 0) throw LocalizedFailure(localizedText(R.string.text_task_message_pdf_has_no_pages))
            throwIfConversionCancelled()

            if (encrypt) {
                val password = input.pdfSecurityOptions.outputPassword
                    ?.takeIf { it.isNotBlank() }
                    ?: throw LocalizedFailure(localizedText(R.string.text_task_message_pdf_password_was_empty))
                val policy = StandardProtectionPolicy(
                    password,
                    password,
                    AccessPermission()
                ).apply {
                    setEncryptionKeyLength(PDF_ENCRYPTION_KEY_LENGTH_BITS)
                    setPreferAES(true)
                }
                document.protect(policy)
            } else {
                document.setAllSecurityToBeRemoved(true)
            }

            throwIfConversionCancelled()
            document.save(outputFile)
            throwIfConversionCancelled()
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
            if (pageCount <= 0) throw LocalizedFailure(localizedText(R.string.text_task_message_pdf_has_no_pages))

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
                File(uri.path ?: throw LocalizedFailure(localizedText(R.string.message_could_not_open_pdf))),
                ParcelFileDescriptor.MODE_READ_ONLY
            )
        } else {
            contentResolver.openFileDescriptor(uri, "r")
        } ?: throw LocalizedFailure(localizedText(R.string.message_could_not_open_pdf))

        return try {
            if (password != null) {
                if (!supportsPdfPassword()) {
                    throw LocalizedFailure(localizedText(R.string.text_task_message))
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
                throw LocalizedFailure(localizedText(R.string.text_task_message_not_enough_cache_space_for_this_pdf))
            }
        } else if (cacheDirectory.usableSpace < PDF_UNKNOWN_CACHE_MIN_FREE_BYTES) {
            throw LocalizedFailure(localizedText(R.string.text_task_message_not_enough_cache_space_for_this_pdf))
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
            } ?: throw LocalizedFailure(localizedText(R.string.message_could_not_open_pdf))
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
                        throw LocalizedFailure(localizedText(R.string.text_task_message_not_enough_cache_space_for_this_pdf))
                    }
                } else if (cacheDirectory.usableSpace < PDF_UNKNOWN_CACHE_MIN_FREE_BYTES) {
                    throw LocalizedFailure(localizedText(R.string.text_task_message_not_enough_cache_space_for_this_pdf))
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
            } ?: throw LocalizedFailure(localizedText(R.string.message_could_not_open_pdf))
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
        ) ?: throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_decode_this_input))
        throwIfConversionCancelled()
        if (input.imageOptions.superResolution == ImageSuperResolutionMode.Off) {
            updateImageProgress(0.55f)
        } else {
            updateImageProgress(0.05f)
        }

        val workingBitmap = applySuperResolutionIfNeeded(
            decodedBitmap,
            input.imageOptions.superResolution
        )
        throwIfConversionCancelled()

        if (outputProfile.extension.equals("ico", ignoreCase = true)) {
            try {
                writeIcoImageFile(workingBitmap, outputFile)
                throwIfConversionCancelled()
                updateImageProgress(0.95f)
            } finally {
                if (workingBitmap !== decodedBitmap) {
                    workingBitmap.recycle()
                }
                decodedBitmap.recycle()
            }
            return
        }

        val bitmapForOutput = bitmapForImageOutput(
            workingBitmap,
            outputProfile.extension,
            flattenTransparency = false
        )
        val useWebpLossless = shouldUseWebpLossless(outputProfile, input.imageOptions)
        val compressFormat = imageCompressFormatFor(outputProfile.extension, useWebpLossless)
            ?: throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_write_this_output))
        val requestedQuality = if (
            input.imageOptions.superResolution == ImageSuperResolutionMode.Off
        ) {
            input.imageOptions.quality
        } else {
            100
        }
        val quality = imageQualityFor(
            outputProfile.extension,
            requestedQuality,
            useWebpLossless
        )

        try {
            throwIfConversionCancelled()
            outputFile.outputStream().use { output ->
                if (!bitmapForOutput.compress(compressFormat, quality, output)) {
                    throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_write_this_output))
                }
                output.flush()
            }
            throwIfConversionCancelled()
            updateImageProgress(0.95f)
        } finally {
            if (bitmapForOutput !== workingBitmap) {
                bitmapForOutput.recycle()
            }
            if (workingBitmap !== decodedBitmap) {
                workingBitmap.recycle()
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
            ?: throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_write_this_output))
        val quality = imageQualityFor(outputProfile.extension, requestedQuality, useWebpLossless)

        try {
            throwIfConversionCancelled()
            outputFile.outputStream().use { output ->
                if (!bitmapForOutput.compress(compressFormat, quality, output)) {
                    throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_write_this_output))
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
                    throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_write_this_output))
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
        if (inputUris.isEmpty()) throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_decode_this_input))

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
                    ) ?: throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_decode_this_input))

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

            if (pageNumber == 1) throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_decode_this_input))
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
            ) ?: throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_decode_this_ico_input))

        val payload = readIcoPayload(uri, selectedEntry)
            ?: throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_decode_this_ico_input))
        if (!payload.hasPngSignature()) {
            Log.w(TAG, "ICO input uses an unsupported non-PNG icon payload")
            throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_only_supports_png_in_ico_input))
        }

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(payload, 0, payload.size, bounds)

        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "ICO PNG payload bounds failed width=$width height=$height")
            throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_decode_this_ico_input))
        }

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = imageSampleSize(width, height, maxLongSidePixels, maxPixels)
        }
        val decoded = BitmapFactory.decodeByteArray(payload, 0, payload.size, options) ?: run {
            Log.w(TAG, "ICO PNG payload decode returned null")
            throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_decode_this_ico_input))
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

    private fun applySuperResolutionIfNeeded(
        bitmap: Bitmap,
        mode: ImageSuperResolutionMode
    ): Bitmap {
        if (mode == ImageSuperResolutionMode.Off) return bitmap

        val targetWidth = bitmap.width.toLong() * mode.scale
        val targetHeight = bitmap.height.toLong() * mode.scale
        if (
            targetWidth > Int.MAX_VALUE ||
            targetHeight > Int.MAX_VALUE ||
            targetWidth * targetHeight > superResolutionMaxPixels()
        ) {
            throw LocalizedFailure(localizedText(R.string.text_task_message_image_is_too_large_to_super_resolve_at_this_scale_try_a_smaller_scale))
        }

        throwIfConversionCancelled()

        if (mode == ImageSuperResolutionMode.RealEsrganAnime4x) {
            return applyRealEsrganSuperResolution(bitmap, EsrganModelManager.MODEL_ANIME)
        }
        if (mode == ImageSuperResolutionMode.RealEsrgan4x) {
            return applyRealEsrganSuperResolution(bitmap, EsrganModelManager.MODEL_X4PLUS)
        }

        return try {
            Bitmap.createScaledBitmap(bitmap, targetWidth.toInt(), targetHeight.toInt(), true)
        } catch (oom: OutOfMemoryError) {
            throw LocalizedFailure(localizedText(R.string.text_task_message_not_enough_memory_to_super_resolve_this_image))
        }
    }

    private fun applyRealEsrganSuperResolution(
        bitmap: Bitmap,
        spec: org.zenconverter.app.model.EsrganModelSpec
    ): Bitmap {
        if (!EsrganModelManager.isDownloaded(this, spec)) {
            throw LocalizedFailure(localizedText(R.string.message_image_engine_could_not_load_the_1_s_model_download_it_in_settings, spec.displayName))
        }

        val inferenceBitmap = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        return try {
            RealEsrganUpscaler.upscale(
                source = inferenceBitmap,
                paramPath = EsrganModelManager.paramFile(this, spec).absolutePath,
                binPath = EsrganModelManager.binFile(this, spec).absolutePath,
                onTileProgress = { progress ->
                    updateImageProgress(0.05f + 0.90f * progress)
                },
                isCancelled = {
                    ConversionTaskStore.isCancelled() || Thread.currentThread().isInterrupted
                }
            )
        } catch (oom: OutOfMemoryError) {
            throw LocalizedFailure(localizedText(R.string.text_task_message_not_enough_memory_to_super_resolve_this_image))
        } finally {
            if (inferenceBitmap !== bitmap) {
                inferenceBitmap.recycle()
            }
        }
    }

    private fun superResolutionMaxPixels(): Long {
        val totalRamBytes = runCatching {
            val activityManager = getSystemService(ActivityManager::class.java)
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem
        }.getOrDefault(0L)

        val ramGiB = totalRamBytes / (1024L * 1024L * 1024L)
        val budgetPixels = ramGiB * SUPER_RESOLUTION_PIXELS_PER_GIB
        return budgetPixels.coerceIn(SUPER_RESOLUTION_MIN_PIXELS, SUPER_RESOLUTION_MAX_PIXELS)
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
                localizedText(R.string.task_processing),
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

    private fun imageFailureMessageFor(exception: Throwable): LocalizedText {
        return exception.localizedFailure(R.string.text_task_message_image_conversion_failed)
    }

    private fun pdfFailureMessageFor(exception: Throwable): LocalizedText {
        return when (exception) {
            is InvalidPasswordException -> localizedText(R.string.text_task_message_pdf_password_was_incorrect_or_unsupported)
            is SecurityException -> localizedText(R.string.text_task_message_password_protected_or_unsupported_pdf_security)
            else -> exception.localizedFailure(R.string.text_task_message_pdf_conversion_failed)
        }
    }

    private fun pdfDocumentFailureMessageFor(
        exception: Throwable,
        input: ConversionTaskInput,
        outputProfile: OutputProfile
    ): LocalizedText {
        if (exception is LocalizedFailure) return exception.description
        if (exception is InvalidPasswordException || exception is SecurityException) {
            return pdfFailureMessageFor(exception)
        }
        if (TargetId.fromKey(input.targetFormat) == TargetId.PdfCompress) {
            return localizedText(R.string.message_pdf_compression_failed)
        }
        when (input.pdfSecurityOptions.mode) {
            PdfSecurityMode.Encrypt -> return localizedText(R.string.text_task_message_pdf_encryption_failed)
            PdfSecurityMode.Decrypt -> return localizedText(R.string.text_task_message_pdf_decryption_failed)
            PdfSecurityMode.None -> Unit
        }
        return when (outputProfile.extension.lowercase(Locale.US)) {
            "pdf" -> localizedText(R.string.text_task_message_pdf_merge_failed)
            "txt" -> localizedText(R.string.text_task_message_pdf_text_extraction_failed)
            "md" -> localizedText(R.string.text_task_message_pdf_markdown_export_failed)
            else -> pdfFailureMessageFor(exception)
        }
    }

    private fun officeFailureMessageFor(exception: Throwable): LocalizedText {
        return when (exception) {
            is Office2PdfUnsupportedAbiException -> localizedText(R.string.text_task_message_office_converter_is_only_available_on_arm64_v8a_devices)
            is Office2PdfUnavailableException, is UnsatisfiedLinkError -> localizedText(R.string.text_task_message_office_converter_could_not_start_on_this_device)
            else -> exception.localizedFailure(R.string.text_task_message_office_conversion_failed)
        }
    }

    private fun readOfficeInputBytes(input: ConversionTaskInput): ByteArray {
        val sourceSize = queryOpenableSize(input.inputUri)
        sourceSize?.let { sizeBytes ->
            if (sizeBytes > OFFICE_MAX_INPUT_BYTES) {
                throw LocalizedFailure(localizedText(R.string.text_task_message_office_file_is_too_large_for_this_experimental_converter))
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
                    throw LocalizedFailure(localizedText(R.string.text_task_message_office_file_is_too_large_for_this_experimental_converter))
                }
                output.write(buffer, 0, read)
            }
        } ?: throw LocalizedFailure(localizedText(R.string.text_task_message_input_file_could_not_be_opened))

        if (output.size() == 0) throw LocalizedFailure(localizedText(R.string.text_task_message_input_file_is_empty))
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

    private fun startSubtitleExport(
        input: ConversionTaskInput,
        tempFile: File
    ) {
        serviceScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    runSubtitleExport(input, tempFile)
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
                Log.w(TAG, "Could not run subtitle export", exception)
                failCurrentTask(subtitleFailureMessageFor(exception))
            }
        }
    }

    private suspend fun runSubtitleExport(
        input: ConversionTaskInput,
        tempFile: File
    ) {
        val sourceFormat = SubtitleFormat.fromExtension(input.extension)
            ?: throw LocalizedFailure(localizedText(R.string.text_task_message_unsupported_subtitle_format))
        val targetFormat = subtitleTargetFormatFor(input.targetFormat)
            ?: throw LocalizedFailure(localizedText(R.string.text_task_message_unsupported_subtitle_format))
        if (sourceFormat == targetFormat) throw LocalizedFailure(localizedText(R.string.text_task_message_unsupported_subtitle_format))

        throwIfConversionCancelled()
        updateImageProgress(0.05f)

        val lrcInputBridge = sourceFormat == SubtitleFormat.LRC
        val lrcOutputBridge = targetFormat == SubtitleFormat.LRC

        if (lrcInputBridge) {
            val srtText = parseLrcInputToSrt(input)
            updateImageProgress(0.5f)
            throwIfConversionCancelled()
            if (targetFormat == SubtitleFormat.SRT) {
                writeUtf8File(tempFile, srtText)
                updateImageProgress(0.95f)
                return
            }
            val srtTemp = createSubtitleInterchangeFile(input, SubtitleFormat.SRT)
            try {
                writeUtf8File(srtTemp, srtText)
                runSubtitleFfmpeg(
                    input = input,
                    inputPath = srtTemp.absolutePath,
                    sourceFormat = SubtitleFormat.SRT,
                    targetFormat = targetFormat,
                    outputFile = tempFile
                )
            } finally {
                srtTemp.delete()
            }
            updateImageProgress(0.95f)
            return
        }

        if (lrcOutputBridge) {
            val srtText = if (sourceFormat == SubtitleFormat.SRT) {
                readSubtitleInputText(input)
            } else {
                val srtTemp = createSubtitleInterchangeFile(input, SubtitleFormat.SRT)
                try {
                    val inputSource = openFfmpegInputSource(input.inputUri)
                        ?: throw LocalizedFailure(localizedText(R.string.text_task_message_compatibility_engine_could_not_open_saf_input))
                    runSubtitleFfmpeg(
                        input = input,
                        inputPath = inputSource.path,
                        sourceFormat = sourceFormat,
                        targetFormat = SubtitleFormat.SRT,
                        outputFile = srtTemp,
                        inputSource = inputSource
                    )
                    srtTemp.readText(Charsets.UTF_8)
                } finally {
                    srtTemp.delete()
                }
            }
            updateImageProgress(0.6f)
            throwIfConversionCancelled()
            val document = SrtCodec.parse(srtText)
            writeUtf8File(tempFile, LrcCodec.write(document))
            updateImageProgress(0.95f)
            return
        }

        val inputSource = openFfmpegInputSource(input.inputUri)
            ?: throw LocalizedFailure(localizedText(R.string.text_task_message_compatibility_engine_could_not_open_saf_input))
        runSubtitleFfmpeg(
            input = input,
            inputPath = inputSource.path,
            sourceFormat = sourceFormat,
            targetFormat = targetFormat,
            outputFile = tempFile,
            inputSource = inputSource
        )
        updateImageProgress(0.95f)
    }

    private suspend fun runSubtitleFfmpeg(
        input: ConversionTaskInput,
        inputPath: String,
        sourceFormat: SubtitleFormat,
        targetFormat: SubtitleFormat,
        outputFile: File,
        inputSource: FfmpegInputSource? = null
    ): FfmpegRunResult {
        try {
            val loadFailure = ensureFfmpegKitReady()
            if (loadFailure != null) {
                error(compatibilityStartupFailureMessageFor(loadFailure))
            }
            subtitleMissingFfmpegSupportMessage(sourceFormat, targetFormat)?.let { message ->
                error(message)
            }
            val arguments = subtitleFfmpegArgumentsFor(
                sourceFormat = sourceFormat,
                targetFormat = targetFormat,
                inputPath = inputPath,
                outputFile = outputFile
            )
            val logTail = mutableListOf<String>()
            val result = executeFfmpeg(
                input = input,
                arguments = arguments,
                durationMs = null,
                logTail = logTail,
                inputSourceLabel = inputSource?.label ?: "file"
            )
            if (result.cancelled) throw CancellationException()
            if (!result.success) {
                error(result.message ?: subtitleFailureMessageFor(IllegalStateException()))
            }
            return result
        } finally {
            inputSource?.close()
        }
    }

    private fun subtitleTargetFormatFor(targetFormat: String): SubtitleFormat? {
        return when (targetFormat.uppercase(Locale.US)) {
            "SRT" -> SubtitleFormat.SRT
            "VTT" -> SubtitleFormat.VTT
            "ASS" -> SubtitleFormat.ASS
            "LRC" -> SubtitleFormat.LRC
            else -> null
        }
    }

    private fun parseLrcInputToSrt(input: ConversionTaskInput): String {
        val text = readSubtitleInputText(input)
        throwIfConversionCancelled()
        val document = LrcCodec.parse(text)
        throwIfConversionCancelled()
        return SrtCodec.write(document)
    }

    private fun readSubtitleInputText(input: ConversionTaskInput): String {
        val bytes = readSubtitleInputBytes(input)
        throwIfConversionCancelled()
        return SubtitleTextDecoder.decode(bytes)
    }

    private fun readSubtitleInputBytes(input: ConversionTaskInput): ByteArray {
        val sourceSize = queryOpenableSize(input.inputUri)
        sourceSize?.let { sizeBytes ->
            if (sizeBytes > SUBTITLE_MAX_INPUT_BYTES) {
                throw LocalizedFailure(localizedText(R.string.text_task_message_subtitle_file_is_too_large))
            }
        }

        val initialCapacity = sourceSize
            ?.coerceAtMost(SUBTITLE_MAX_INPUT_BYTES)
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
                if (totalBytes > SUBTITLE_MAX_INPUT_BYTES) {
                    throw LocalizedFailure(localizedText(R.string.text_task_message_subtitle_file_is_too_large))
                }
                output.write(buffer, 0, read)
            }
        } ?: throw LocalizedFailure(localizedText(R.string.text_task_message_input_file_could_not_be_opened))

        if (output.size() == 0) throw LocalizedFailure(localizedText(R.string.text_task_message_subtitle_file_is_empty))
        return output.toByteArray()
    }

    private fun writeUtf8File(file: File, text: String) {
        file.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(text)
            writer.flush()
        }
    }

    private fun createSubtitleInterchangeFile(
        input: ConversionTaskInput,
        format: SubtitleFormat
    ): File {
        val cacheRoot = externalCacheDir ?: cacheDir
        val tempDirectory = File(cacheRoot, SUBTITLE_INTERCHANGE_DIRECTORY).apply { mkdirs() }
        return File(tempDirectory, "${input.fileId}_${System.nanoTime()}.${format.extension}").apply {
            if (exists()) delete()
        }
    }

    private fun subtitleFfmpegArgumentsFor(
        sourceFormat: SubtitleFormat,
        targetFormat: SubtitleFormat,
        inputPath: String,
        outputFile: File
    ): List<String> {
        return listOf(
            "-hide_banner",
            "-nostdin",
            "-y",
            "-f",
            subtitleDemuxerFor(sourceFormat),
            "-i",
            inputPath,
            "-map",
            "0:s:0",
            "-c:s",
            subtitleCodecFor(targetFormat),
            "-f",
            subtitleMuxerFor(targetFormat),
            outputFile.absolutePath
        )
    }

    private fun subtitleDemuxerFor(format: SubtitleFormat): String {
        return when (format) {
            SubtitleFormat.SRT -> "srt"
            SubtitleFormat.VTT -> "webvtt"
            SubtitleFormat.ASS -> "ass"
            SubtitleFormat.LRC -> throw LocalizedFailure(localizedText(R.string.text_task_message_unsupported_subtitle_format))
        }
    }

    private fun subtitleMuxerFor(format: SubtitleFormat): String {
        return when (format) {
            SubtitleFormat.SRT -> "srt"
            SubtitleFormat.VTT -> "webvtt"
            SubtitleFormat.ASS -> "ass"
            SubtitleFormat.LRC -> throw LocalizedFailure(localizedText(R.string.text_task_message_unsupported_subtitle_format))
        }
    }

    private fun subtitleCodecFor(format: SubtitleFormat): String {
        return when (format) {
            SubtitleFormat.SRT -> "subrip"
            SubtitleFormat.VTT -> "webvtt"
            SubtitleFormat.ASS -> "ass"
            SubtitleFormat.LRC -> throw LocalizedFailure(localizedText(R.string.text_task_message_unsupported_subtitle_format))
        }
    }

    private fun subtitleMissingFfmpegSupportMessage(
        sourceFormat: SubtitleFormat,
        targetFormat: SubtitleFormat
    ): LocalizedText? {
        val missing = listOf(
            subtitleDemuxerFor(sourceFormat),
            subtitleMuxerFor(targetFormat),
            subtitleCodecFor(targetFormat)
        ).distinct().firstOrNull { token ->
            ffmpegSubtitleFeatureAvailable(token) == false
        } ?: return null

        Log.e(
            TAG,
            "FFmpeg compatibility package is missing subtitle feature=$missing " +
                "target=${targetFormat.extension}"
        )
        return localizedText(R.string.text_task_message_compatibility_engine_needs_subtitle_support)
    }

    private fun ffmpegSubtitleFeatureAvailable(token: String): Boolean? {
        val lists = ffmpegSubtitleFeatureLists ?: loadFfmpegSubtitleFeatureLists()
        if (lists == null) return null
        return lists.values.any { set -> token in set }
    }

    private fun loadFfmpegSubtitleFeatureLists(): Map<String, Set<String>>? {
        val demuxers = probeFfmpegFeatureList("-demuxers")
        val muxers = probeFfmpegFeatureList("-muxers")
        val codecs = probeFfmpegFeatureList("-codecs")
        if (demuxers == null || muxers == null || codecs == null) return null

        val lists = mapOf(
            "demuxer" to demuxers,
            "muxer" to muxers,
            "codec" to codecs
        )
        ffmpegSubtitleFeatureLists = lists
        return lists
    }

    private fun probeFfmpegFeatureList(flag: String): Set<String>? {
        val output = runCatching {
            val session = FFmpegKit.executeWithArguments(
                arrayOf("-hide_banner", flag)
            )
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                session.getAllLogsAsString(FFMPEG_ENCODER_PROBE_TIMEOUT_MS).orEmpty()
            } else {
                ""
            }
        }.onFailure { exception ->
            Log.w(TAG, "Could not probe FFmpeg feature list $flag", exception)
        }.getOrNull() ?: return null
        if (output.isBlank()) return null

        return output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                line.split(WHITESPACE_REGEX).getOrNull(1)
            }
            .toSet()
    }

    private fun subtitleFailureMessageFor(exception: Throwable): LocalizedText {
        return exception.localizedFailure(R.string.text_task_message_subtitle_conversion_failed)
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
                    result.segmentTempFiles.forEach { it.delete() }
                    cancelRun()
                    return@onSuccess
                }
                if (result.success) {
                    if (result.segmentTempFiles.isNotEmpty()) {
                        tempFile.delete()
                        val outputProfile = outputProfileFor(input) ?: throw LocalizedFailure(localizedText(R.string.message_unsupported_output_format))
                        saveCompletedExportFilesInFolder(
                            input = input,
                            tempFiles = result.segmentTempFiles,
                            outputProfile = outputProfile,
                            folderName = outputFolderNameForSplit(input),
                            nameGenerator = { index, count ->
                                outputNameForPart(input, outputProfile.extension, index, count)
                            }
                        )
                    } else {
                        saveCompletedExport(input, tempFile)
                    }
                } else {
                    Log.e(
                        TAG,
                        "Compatibility export returned failure category=${input.category} " +
                            "target=${input.targetFormat} displayName=${input.displayName} " +
                            "message=${result.message} outputTail=${result.outputTail.orEmpty()}"
                    )
                    tempFile.delete()
                    result.segmentTempFiles.forEach { it.delete() }
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

        val logTail = mutableListOf<String>()
        if (input.category == ConversionMediaCategory.Video && input.inputUris.size > 1) {
            return@withContext runFfmpegVideoMergeExport(
                input = input,
                tempFile = tempFile,
                logTail = logTail
            )
        }

        val sourceDurationMs = readDurationMs(input.inputUri)
            ?: run {
                val probeSource = openFfmpegInputSource(input.inputUri)
                    ?: return@withContext FfmpegRunResult(
                        success = false,
                        cancelled = false,
                        message = localizedText(R.string.text_task_message_compatibility_engine_could_not_open_saf_input)
                    )
                try {
                    readFfmpegDurationMs(probeSource.path)
                } finally {
                    probeSource.close()
                }
            }

        val trimWindow = ffmpegTrimWindowFor(input, sourceDurationMs)
        if (trimWindow.errorMessage != null) {
            return@withContext FfmpegRunResult(
                success = false,
                cancelled = false,
                message = trimWindow.errorMessage
            )
        }
        val effectiveDurationMs = ffmpegProgressDurationMsFor(input, sourceDurationMs, trimWindow)
        ffmpegMissingAdvancedMetadataMessageFor(input, effectiveDurationMs)?.let { message ->
            return@withContext FfmpegRunResult(
                success = false,
                cancelled = false,
                message = message
            )
        }
        ffmpegUnsupportedAdvancedSelectionMessageFor(input, effectiveDurationMs)?.let { message ->
            return@withContext FfmpegRunResult(
                success = false,
                cancelled = false,
                message = message
            )
        }
        ffmpegMissingFilterMessageFor(input)?.let { message ->
            return@withContext FfmpegRunResult(
                success = false,
                cancelled = false,
                message = message
            )
        }
        if (input.category == ConversionMediaCategory.Video &&
            input.videoOptions.frameInterpolation == VideoFrameInterpolationMode.Rife2x
        ) {
            return@withContext runFfmpegVideoInterpolationExport(
                input = input,
                tempFile = tempFile,
                effectiveDurationMs = effectiveDurationMs,
                trimWindow = trimWindow,
                logTail = logTail
            )
        }
        if (isVideoGifOutput(input)) {
            val inputSource = openFfmpegInputSource(input.inputUri)
                ?: return@withContext FfmpegRunResult(
                    success = false,
                    cancelled = false,
                    message = localizedText(R.string.text_task_message_compatibility_engine_could_not_open_saf_input)
                )
            return@withContext try {
                runFfmpegVideoGifExport(
                    input = input,
                    inputSource = inputSource,
                    tempFile = tempFile,
                    durationMs = effectiveDurationMs,
                    trimWindow = trimWindow,
                    logTail = logTail
                )
            } finally {
                inputSource.close()
            }
        }
        if (trimWindow.isMultiSegment) {
            val outputProfile = outputProfileFor(input)
                ?: return@withContext FfmpegRunResult(
                    success = false,
                    cancelled = false,
                    message = localizedText(R.string.message_unsupported_output_format)
                )
            val segmentTempFiles = mutableListOf<File>()
            val segmentCount = trimWindow.segments.size
            for (i in 0 until segmentCount) {
                if (ConversionTaskStore.isCancelled()) {
                    segmentTempFiles.forEach { it.delete() }
                    return@withContext FfmpegRunResult(success = false, cancelled = true)
                }
                val segment = trimWindow.segments[i]
                val segTrimWindow = FfmpegTrimWindow(segments = listOf(segment))
                val partTempFile = File(
                    tempFile.parentFile ?: (externalCacheDir ?: cacheDir),
                    "${input.fileId}_part_${i + 1}_${System.nanoTime()}.${outputProfile.extension}"
                )
                val segInputSource = openFfmpegInputSource(input.inputUri)
                    ?: run {
                        segmentTempFiles.forEach { it.delete() }
                        return@withContext FfmpegRunResult(
                            success = false,
                            cancelled = false,
                            message = localizedText(R.string.text_task_message_compatibility_engine_could_not_open_saf_input)
                        )
                    }
                val partResult = try {
                    val partArguments = ffmpegArgumentsFor(
                        input = input,
                        inputPath = segInputSource.path,
                        outputFile = partTempFile,
                        durationMs = segment.effectiveDurationMs,
                        trimWindow = segTrimWindow
                    )
                    val segStartProgress = (i.toFloat() / segmentCount.toFloat()) * FFMPEG_MAX_PROGRESS_BEFORE_SAVE
                    val segEndProgress = ((i + 1).toFloat() / segmentCount.toFloat()) * FFMPEG_MAX_PROGRESS_BEFORE_SAVE
                    executeFfmpeg(
                        input = input,
                        arguments = partArguments,
                        durationMs = segment.effectiveDurationMs,
                        logTail = logTail,
                        inputSourceLabel = segInputSource.label,
                        progressStart = segStartProgress,
                        progressEnd = segEndProgress
                    )
                } finally {
                    segInputSource.close()
                }
                if (partResult.cancelled || ConversionTaskStore.isCancelled()) {
                    partTempFile.delete()
                    segmentTempFiles.forEach { it.delete() }
                    return@withContext FfmpegRunResult(success = false, cancelled = true)
                }
                if (!partResult.success) {
                    partTempFile.delete()
                    segmentTempFiles.forEach { it.delete() }
                    return@withContext partResult
                }
                segmentTempFiles.add(partTempFile)
            }
            return@withContext FfmpegRunResult(
                success = true,
                cancelled = false,
                segmentTempFiles = segmentTempFiles
            )
        }
        val inputSource = openFfmpegInputSource(input.inputUri)
            ?: return@withContext FfmpegRunResult(
                success = false,
                cancelled = false,
                message = localizedText(R.string.text_task_message_compatibility_engine_could_not_open_saf_input)
            )
        try {
            val arguments = ffmpegArgumentsFor(
                input = input,
                inputPath = inputSource.path,
                outputFile = tempFile,
                durationMs = effectiveDurationMs,
                trimWindow = trimWindow
            )
            executeFfmpeg(input, arguments, effectiveDurationMs, logTail, inputSource.label)
        } finally {
            inputSource.close()
        }
    }

    private suspend fun runFfmpegVideoInterpolationExport(
        input: ConversionTaskInput,
        tempFile: File,
        effectiveDurationMs: Long?,
        trimWindow: FfmpegTrimWindow,
        logTail: MutableList<String>
    ): FfmpegRunResult = withContext(Dispatchers.IO) {
        if (!RifeModelManager.isDownloaded(this@ConversionService)) {
            return@withContext FfmpegRunResult(
                success = false,
                cancelled = false,
                message = localizedText(R.string.message_ai_rife)
            )
        }

        val workDir = File(externalCacheDir ?: cacheDir, "rife_${System.currentTimeMillis()}").apply { mkdirs() }
        val framesInDir = File(workDir, "in").apply { mkdirs() }
        val framesOutDir = File(workDir, "out").apply { mkdirs() }
        val tempAudioFile = File(workDir, "audio.m4a")

        try {
            val probeFps = probeVideoFrameRate(input.inputUri, input.inputInfo?.frameRate)
            val targetFps = (probeFps * 2.0f).coerceIn(24.0f, 120.0f)

            val hasAudio = probeHasAudio(input.inputUri)
            if (hasAudio) {
                val audioInputSource = openFfmpegInputSource(input.inputUri)
                if (audioInputSource != null) {
                    try {
                        val audioArgs = mutableListOf(
                            "-hide_banner",
                            "-nostdin",
                            "-y"
                        )
                        audioArgs.addFfmpegTrimInputOptions(trimWindow)
                        audioArgs.addAll(listOf(
                            "-i", audioInputSource.path,
                            "-map", "0:a:0",
                            "-vn", "-sn", "-dn",
                            "-c:a", "copy",
                            tempAudioFile.absolutePath
                        ))
                        executeFfmpeg(
                            input = input,
                            arguments = audioArgs,
                            durationMs = effectiveDurationMs,
                            logTail = logTail,
                            inputSourceLabel = "extract-audio",
                            progressStart = 0.0f,
                            progressEnd = 0.05f
                        )
                    } finally {
                        audioInputSource.close()
                    }
                }
            }

            if (ConversionTaskStore.isCancelled()) {
                return@withContext FfmpegRunResult(success = false, cancelled = true)
            }

            val extractInputSource = openFfmpegInputSource(input.inputUri)
                ?: return@withContext FfmpegRunResult(
                    success = false,
                    cancelled = false,
                    message = localizedText(R.string.text_task_message_compatibility_engine_could_not_open_saf_input)
                )

            val extractResult = try {
                val framePatternIn = File(framesInDir, "f_%06d.jpg").absolutePath
                val extractArgs = mutableListOf(
                    "-hide_banner",
                    "-nostdin",
                    "-y"
                )
                extractArgs.addFfmpegTrimInputOptions(trimWindow)
                extractArgs.addAll(listOf(
                    "-i", extractInputSource.path,
                    "-map", "0:v:0",
                    "-an", "-sn", "-dn",
                    "-c:v", "mjpeg",
                    "-q:v", "2",
                    framePatternIn
                ))

                executeFfmpeg(
                    input = input,
                    arguments = extractArgs,
                    durationMs = effectiveDurationMs,
                    logTail = logTail,
                    inputSourceLabel = "extract-frames",
                    progressStart = 0.05f,
                    progressEnd = 0.20f
                )
            } finally {
                extractInputSource.close()
            }

            if (!extractResult.success || extractResult.cancelled || ConversionTaskStore.isCancelled()) {
                return@withContext extractResult
            }

            val inFrames = framesInDir.listFiles { file -> file.extension.equals("jpg", ignoreCase = true) || file.extension.equals("jpeg", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?: emptyList()

            if (inFrames.isEmpty()) {
                return@withContext FfmpegRunResult(
                    success = false,
                    cancelled = false,
                    message = localizedText(R.string.message_run_ffmpeg_video_interpolation_export)
                )
            }

            val paramPath = RifeModelManager.paramFile(this@ConversionService).absolutePath
            val binPath = RifeModelManager.binFile(this@ConversionService).absolutePath
            val totalInFrames = inFrames.size
            var outFrameIdx = 1

            RifeInterpolator.initSession(paramPath, binPath)
            try {
                for (i in 0 until totalInFrames) {
                    if (ConversionTaskStore.isCancelled()) {
                        return@withContext FfmpegRunResult(success = false, cancelled = true)
                    }

                    val currentFrameFile = inFrames[i]
                    val currentBitmap = BitmapFactory.decodeFile(currentFrameFile.absolutePath)
                        ?: continue

                    val curOutFile = File(framesOutDir, String.format(Locale.US, "f_%06d.jpg", outFrameIdx++))
                    curOutFile.outputStream().use { fos ->
                        currentBitmap.compress(Bitmap.CompressFormat.JPEG, 98, fos)
                    }

                    if (i < totalInFrames - 1) {
                        val nextFrameFile = inFrames[i + 1]
                        val nextBitmap = BitmapFactory.decodeFile(nextFrameFile.absolutePath)
                        if (nextBitmap != null) {
                            val interpolated = RifeInterpolator.interpolate(
                                frame0 = currentBitmap,
                                frame1 = nextBitmap,
                                paramPath = paramPath,
                                binPath = binPath,
                                isCancelled = { ConversionTaskStore.isCancelled() }
                            )
                            val interOutFile = File(framesOutDir, String.format(Locale.US, "f_%06d.jpg", outFrameIdx++))
                            interOutFile.outputStream().use { fos ->
                                interpolated.compress(Bitmap.CompressFormat.JPEG, 98, fos)
                            }
                            interpolated.recycle()
                            nextBitmap.recycle()
                        }
                    }
                    currentBitmap.recycle()

                    val interpolateProgress = 0.20f + 0.60f * ((i + 1).toFloat() / totalInFrames.toFloat())
                    updateCompatibilityProgress(interpolateProgress)
                }
            } finally {
                RifeInterpolator.releaseSession()
            }

            if (ConversionTaskStore.isCancelled()) {
                return@withContext FfmpegRunResult(success = false, cancelled = true)
            }

            val framePatternOut = File(framesOutDir, "f_%06d.jpg").absolutePath
            val encodeArgs = mutableListOf(
                "-hide_banner",
                "-nostdin",
                "-y",
                "-framerate", String.format(Locale.US, "%.3f", targetFps),
                "-i", framePatternOut
            )
            if (tempAudioFile.exists() && tempAudioFile.length() > 0) {
                encodeArgs.addAll(listOf("-i", tempAudioFile.absolutePath, "-c:a", "aac", "-b:a", "192k"))
            }
            encodeArgs.addAll(listOf(
                "-c:v", "libx264",
                "-crf", "18",
                "-preset", "medium",
                "-pix_fmt", "yuv420p",
                tempFile.absolutePath
            ))

            val encodeResult = executeFfmpeg(
                input = input,
                arguments = encodeArgs,
                durationMs = effectiveDurationMs,
                logTail = logTail,
                inputSourceLabel = "encode-interpolated",
                progressStart = 0.80f,
                progressEnd = 0.95f
            )

            return@withContext encodeResult
        } finally {
            workDir.deleteRecursively()
        }
    }

    private suspend fun runFfmpegVideoMergeExport(
        input: ConversionTaskInput,
        tempFile: File,
        logTail: MutableList<String>
    ): FfmpegRunResult {
        val inputUris = input.inputUris
        if (inputUris.size < 2) {
            return FfmpegRunResult(
                success = false,
                cancelled = false,
                message = localizedText(R.string.message_at_least_two_videos_are_required_for_merge)
            )
        }

        val videoProfile = ffmpegVideoProfileFor(input)
            ?: return FfmpegRunResult(
                success = false,
                cancelled = false,
                message = localizedText(R.string.message_unsupported_video_target_1_s, input.targetFormat)
            )

        val inputSources = mutableListOf<FfmpegInputSource>()
        val inputDurations = mutableListOf<Long>()
        val inputAudioFlags = mutableListOf<Boolean>()

        try {
            for (uri in inputUris) {
                if (ConversionTaskStore.isCancelled()) {
                    return FfmpegRunResult(success = false, cancelled = true)
                }
                val src = openFfmpegInputSource(uri)
                    ?: return FfmpegRunResult(
                        success = false,
                        cancelled = false,
                        message = localizedText(R.string.text_task_message_compatibility_engine_could_not_open_saf_input)
                    )
                inputSources.add(src)
                val duration = readDurationMs(uri)
                    ?: readFfmpegDurationMs(src.path)
                    ?: 0L
                inputDurations.add(duration)
                inputAudioFlags.add(probeHasAudio(uri))
            }

            val totalDurationMs = inputDurations.sum().takeIf { it > 0L }
            val videoAudioOptions = ffmpegVideoAudioOptionsFor(
                compressionMode = input.videoOptions.compressionMode,
                manualAudioOptions = input.audioOptions
            )
            val includeAudio = videoAudioOptions.advanced.volume != AudioVolumeMode.Mute

            // Determine output video dimensions
            val firstUri = inputUris.first()
            val rawSourceSize = input.inputInfo?.let {
                if (it.width != null && it.height != null && it.width > 0 && it.height > 0) {
                    VideoSize(it.width, it.height)
                } else null
            } ?: readVideoSize(firstUri) ?: VideoSize(1920, 1080)

            val targetShortSide = input.videoOptions.maxShortSidePixels?.takeIf { it > 0 }
            val baseSize = if (targetShortSide != null && rawSourceSize.shortSide > targetShortSide) {
                scaledVideoSizeFor(rawSourceSize, targetShortSide)
            } else {
                rawSourceSize
            }
            val targetW = (baseSize.width / 2) * 2
            val targetH = (baseSize.height / 2) * 2

            // Build filter_complex
            val filterComplex = buildString {
                for (i in inputSources.indices) {
                    // Video scaling and padding with SAR=1
                    append("[$i:v:0]scale=w=$targetW:h=$targetH:force_original_aspect_ratio=decrease,")
                    append("pad=w=$targetW:h=$targetH:x=(ow-iw)/2:y=(oh-ih)/2:color=black,setsar=1[v$i];")

                    if (includeAudio) {
                        if (inputAudioFlags[i]) {
                            append("[$i:a:0]aformat=sample_fmts=fltp:sample_rates=44100:channel_layouts=stereo[a$i];")
                        } else {
                            val durSec = (inputDurations[i].toDouble() / 1000.0).coerceAtLeast(0.1)
                            append("aevalsrc=0:d=$durSec:s=44100:c=stereo[a$i];")
                        }
                    }
                }

                if (includeAudio) {
                    for (i in inputSources.indices) {
                        append("[v$i][a$i]")
                    }
                    append("concat=n=${inputSources.size}:v=1:a=1[outv][outa]")
                } else {
                    for (i in inputSources.indices) {
                        append("[v$i]")
                    }
                    append("concat=n=${inputSources.size}:v=1:a=0[outv]")
                }
            }

            val arguments = buildList {
                add("-hide_banner")
                add("-nostdin")
                add("-y")
                inputSources.forEach { src ->
                    add("-i")
                    add(src.path)
                }
                add("-filter_complex")
                add(filterComplex)
                add("-map")
                add("[outv]")
                if (includeAudio) {
                    add("-map")
                    add("[outa]")
                }
                add("-sn")
                add("-dn")
                if (!includeAudio) {
                    add("-an")
                }
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
                input.videoOptions.maxFrameRate
                    ?.takeIf { it > 0 }
                    ?.let { maxFps ->
                        add("-fpsmax")
                        add(maxFps.toString())
                    }
                if (videoProfile.videoTag != null) {
                    add("-tag:v")
                    add(videoProfile.videoTag)
                }
                if (includeAudio) {
                    add("-c:a")
                    add(FFMPEG_AAC_ENCODER)
                    if (videoAudioOptions.audioBitrate != null) {
                        add("-b:a")
                        add(videoAudioOptions.audioBitrate.toString())
                    }
                }
                if (videoProfile.useFastStart) {
                    add("-movflags")
                    add("+faststart")
                }
                add("-f")
                add(videoProfile.format)
                add(tempFile.absolutePath)
            }

            return executeFfmpeg(
                input = input,
                arguments = arguments,
                durationMs = totalDurationMs,
                logTail = logTail,
                inputSourceLabel = "${inputSources.size} inputs"
            )
        } finally {
            inputSources.forEach { it.close() }
        }
    }

    private fun probeHasAudio(uri: Uri): Boolean {
        val retriever = MediaMetadataRetriever()
        return runCatching {
            retriever.setDataSource(this, uri)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            hasAudio.equals("yes", ignoreCase = true) || hasAudio == "true" || hasAudio == "1"
        }.getOrDefault(false).also {
            runCatching { retriever.release() }
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
        trimWindow: FfmpegTrimWindow,
        logTail: MutableList<String>
    ): FfmpegRunResult {
        return executeFfmpeg(
            input = input,
            arguments = ffmpegVideoGifArgumentsFor(
                input = input,
                inputPath = inputSource.path,
                outputFile = tempFile,
                trimWindow = trimWindow
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
            ?: throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_decode_this_input))
        validateGifFrameSize(frameSize, GIF_FRAME_MAX_PIXELS)

        val frameDirectory = createGifFrameTempDirectory(input)
        val inputSource = openFfmpegInputSource(uri)
        if (inputSource == null) {
            frameDirectory.deleteRecursively()
            throw LocalizedFailure(localizedText(R.string.text_task_message_compatibility_engine_could_not_open_saf_input))
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
                throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_split_gif_frames))
            }

            val frameByteCount = gifRawFrameByteCount(frameSize)
            val rawLength = rawFrameFile.length()
            if (rawLength <= 0L || rawLength % frameByteCount != 0L) {
                Log.e(TAG, "GIF frame extraction produced no frames displayName=${input.displayName}")
                throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_split_gif_frames))
            }
            val frameCountLong = rawLength / frameByteCount
            if (frameCountLong <= 0L || frameCountLong > Int.MAX_VALUE) {
                Log.e(TAG, "GIF frame extraction produced no frames displayName=${input.displayName}")
                throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_split_gif_frames))
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
            throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_decode_this_input))
        }
        gifRawFrameByteCount(size)
    }

    private fun gifRawFrameByteCount(size: ImageDecodeSize): Long {
        val byteCount = size.width.toLong() * size.height.toLong() * RGBA_BYTES_PER_PIXEL
        if (byteCount <= 0L || byteCount > Int.MAX_VALUE) {
            throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_decode_this_input))
        }
        return byteCount
    }

    private fun createGifFrameTempDirectory(input: ConversionTaskInput): File {
        val cacheRoot = externalCacheDir ?: cacheDir
        val tempRoot = File(cacheRoot, GIF_FRAME_TEMP_DIRECTORY).apply { mkdirs() }
        return File(tempRoot, "${input.fileId}_${System.nanoTime()}").apply {
            if (exists()) deleteRecursively()
            if (!mkdirs()) throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_split_gif_frames))
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
                    throw LocalizedFailure(localizedText(R.string.text_task_message_image_engine_could_not_split_gif_frames))
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
        outputFile: File,
        durationMs: Long?,
        trimWindow: FfmpegTrimWindow
    ): List<String> {
        return when (input.category) {
            ConversionMediaCategory.Video -> ffmpegVideoArgumentsFor(
                input,
                inputPath,
                outputFile,
                durationMs,
                trimWindow
            )
            ConversionMediaCategory.Audio -> buildList {
                val audioProfile = ffmpegAudioProfileFor(input)
                    ?: throw LocalizedFailure(localizedText(R.string.message_unsupported_audio_target_1_s, input.targetFormat))
                add("-hide_banner")
                add("-nostdin")
                add("-y")
                addFfmpegTrimInputOptions(trimWindow)
                add("-i")
                add(inputPath)
                add("-map")
                add("0:a:0")
                add("-vn")
                add("-sn")
                add("-dn")
                add("-c:a")
                add(audioProfile.codec)
                addFfmpegAudioOptions(input.audioOptions, audioProfile, durationMs)
                if (audioProfile.useFastStart) {
                    add("-movflags")
                    add("+faststart")
                }
                add("-f")
                add(audioProfile.format)
                add(outputFile.absolutePath)
            }
            ConversionMediaCategory.Image -> throw LocalizedFailure(localizedText(R.string.ui_failed))
            ConversionMediaCategory.Pdf -> throw LocalizedFailure(localizedText(R.string.message_compatibility_engine_is_not_connected_for_pdfs))
            ConversionMediaCategory.Document -> throw LocalizedFailure(localizedText(R.string.message_compatibility_engine_is_not_connected_for_documents))
            ConversionMediaCategory.Font -> throw LocalizedFailure(localizedText(R.string.message_compatibility_engine_is_not_connected_for_fonts))
            ConversionMediaCategory.Subtitle -> throw LocalizedFailure(localizedText(R.string.ui_failed))
        }
    }

    private fun ffmpegVideoArgumentsFor(
        input: ConversionTaskInput,
        inputPath: String,
        outputFile: File,
        durationMs: Long?,
        trimWindow: FfmpegTrimWindow
    ): List<String> {
        val videoProfile = ffmpegVideoProfileFor(input)
            ?: throw LocalizedFailure(localizedText(R.string.message_unsupported_video_target_1_s, input.targetFormat))
        val videoAudioOptions = ffmpegVideoAudioOptionsFor(
            compressionMode = input.videoOptions.compressionMode,
            manualAudioOptions = input.audioOptions
        )
        val includeAudio = videoAudioOptions.advanced.volume != AudioVolumeMode.Mute
        return buildList {
            add("-hide_banner")
            add("-nostdin")
            add("-y")
            addFfmpegTrimInputOptions(trimWindow)
            add("-i")
            add(inputPath)
            add("-ignore_unknown")
            add("-map")
            add("0:v:0")
            if (includeAudio) {
                add("-map")
                add("0:a:0?")
            }
            add("-sn")
            add("-dn")
            if (!includeAudio) {
                add("-an")
            }
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
            ffmpegVideoFilterFor(input, durationMs)?.let { filter ->
                add("-vf")
                add(filter)
            }
            val isInterpolationActive =
                input.videoOptions.frameInterpolation != VideoFrameInterpolationMode.Off
            if (!isInterpolationActive) {
                input.videoOptions.maxFrameRate
                    ?.takeIf { it > 0 }
                    ?.let { maxFrameRate ->
                        add("-fpsmax")
                        add(maxFrameRate.toString())
                    }
            }
            if (videoProfile.videoTag != null) {
                add("-tag:v")
                add(videoProfile.videoTag)
            }
            if (includeAudio) {
                add("-c:a")
                add(FFMPEG_AAC_ENCODER)
                addFfmpegAudioOptions(
                    audioOptions = videoAudioOptions,
                    audioProfile = ffmpegAacAudioProfile(),
                    durationMs = durationMs,
                    forceReverse = input.videoOptions.compressionMode == VideoCompressionMode.Standard &&
                        !isInterpolationActive &&
                        input.videoOptions.advanced.reverse
                )
            }
            if (videoProfile.useFastStart) {
                add("-movflags")
                add("+faststart")
            }
            add("-f")
            add(videoProfile.format)
            add(outputFile.absolutePath)
        }
    }

    private fun ffmpegVideoAudioOptionsFor(
        compressionMode: VideoCompressionMode,
        manualAudioOptions: AudioExportOptions
    ): AudioExportOptions {
        return when (compressionMode) {
            VideoCompressionMode.Standard -> manualAudioOptions
            VideoCompressionMode.VisualLossless -> AudioExportOptions(audioBitrate = 192_000)
            VideoCompressionMode.BalancedShrink -> AudioExportOptions(audioBitrate = 160_000)
            VideoCompressionMode.SmallFile -> AudioExportOptions(audioBitrate = 128_000)
        }
    }

    private fun MutableList<String>.addFfmpegAudioOptions(
        audioOptions: AudioExportOptions,
        audioProfile: FfmpegAudioProfile,
        durationMs: Long?,
        forceReverse: Boolean = false
    ) {
        ffmpegAudioFilterFor(audioOptions, durationMs, forceReverse)?.let { filter ->
            add("-af")
            add(filter)
        }
        if (audioProfile.supportsBitrate) {
            audioOptions.audioBitrate?.let { bitrate ->
                add("-b:a")
                add(bitrate.toString())
            }
        }
        if (audioProfile.supportsSampleRate) {
            audioOptions.sampleRateHz?.let { sampleRateHz ->
                if (audioProfile.codec == FFMPEG_OPUS_ENCODER) {
                    if (sampleRateHz in OPUS_SUPPORTED_SAMPLE_RATES) {
                        add("-ar")
                        add(sampleRateHz.toString())
                    } else {
                        Log.w(
                            TAG,
                            "Ignoring unsupported sample rate $sampleRateHz for Opus; libopus will auto-resample"
                        )
                    }
                } else {
                    add("-ar")
                    add(sampleRateHz.toString())
                }
            }
        }
        if (audioProfile.supportsChannelCount) {
            audioOptions.channelCount?.let { channelCount ->
                add("-ac")
                add(channelCount.toString())
            }
        }
    }

    private fun MutableList<String>.addFfmpegTrimInputOptions(
        trimWindow: FfmpegTrimWindow,
        durationLimitMs: Long? = trimWindow.durationLimitMs
    ) {
        if (trimWindow.startSeconds > 0.0) {
            add("-ss")
            add(ffmpegSeconds(trimWindow.startSeconds))
        }
        durationLimitMs?.takeIf { it > 0L }?.let { durationMs ->
            add("-t")
            add(ffmpegSeconds(durationMs.toDouble() / 1000.0))
        }
    }

    private fun ffmpegAacAudioProfile(): FfmpegAudioProfile {
        return FfmpegAudioProfile(
            codec = FFMPEG_AAC_ENCODER,
            format = "ipod",
            useFastStart = true,
            requiredEncoder = FFMPEG_AAC_ENCODER
        )
    }

    private fun ffmpegVideoGifArgumentsFor(
        input: ConversionTaskInput,
        inputPath: String,
        outputFile: File,
        trimWindow: FfmpegTrimWindow
    ): List<String> {
        return buildList {
            add("-hide_banner")
            add("-nostdin")
            add("-y")
            addFfmpegTrimInputOptions(
                trimWindow,
                durationLimitMs = trimWindow.effectiveDurationMs
                    ?.coerceAtMost(FFMPEG_VIDEO_GIF_MAX_DURATION_MS)
                    ?: FFMPEG_VIDEO_GIF_MAX_DURATION_MS
            )
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
                preset = videoPresetFor(input.videoOptions.compressionMode),
                crf = videoCrfFor(videoCodec, input.videoOptions.compressionMode)
            )
            "mov" -> FfmpegVideoProfile(
                videoCodec = videoCodec,
                format = "mov",
                useFastStart = true,
                videoTag = if (videoCodec == FFMPEG_VIDEO_ENCODER_H265) "hvc1" else null,
                preset = videoPresetFor(input.videoOptions.compressionMode),
                crf = videoCrfFor(videoCodec, input.videoOptions.compressionMode)
            )
            "mkv" -> FfmpegVideoProfile(
                videoCodec = videoCodec,
                format = "matroska",
                preset = videoPresetFor(input.videoOptions.compressionMode),
                crf = videoCrfFor(videoCodec, input.videoOptions.compressionMode)
            )
            else -> null
        }
    }

    private fun videoPresetFor(compressionMode: VideoCompressionMode): String {
        return when (compressionMode) {
            VideoCompressionMode.Standard -> FFMPEG_STANDARD_VIDEO_PRESET
            VideoCompressionMode.VisualLossless,
            VideoCompressionMode.BalancedShrink,
            VideoCompressionMode.SmallFile -> FFMPEG_PRESET_COMPRESSION_MEDIUM
        }
    }

    private fun videoCrfFor(videoCodec: String, compressionMode: VideoCompressionMode): String {
        return when (compressionMode) {
            VideoCompressionMode.Standard -> defaultVideoCrfFor(videoCodec)
            VideoCompressionMode.VisualLossless -> {
                if (videoCodec == FFMPEG_VIDEO_ENCODER_H265) {
                    FFMPEG_VISUAL_LOSSLESS_CRF_H265
                } else {
                    FFMPEG_VISUAL_LOSSLESS_CRF_H264
                }
            }
            VideoCompressionMode.BalancedShrink -> {
                if (videoCodec == FFMPEG_VIDEO_ENCODER_H265) {
                    FFMPEG_BALANCED_SHRINK_CRF_H265
                } else {
                    FFMPEG_BALANCED_SHRINK_CRF_H264
                }
            }
            VideoCompressionMode.SmallFile -> {
                if (videoCodec == FFMPEG_VIDEO_ENCODER_H265) {
                    FFMPEG_SMALL_FILE_CRF_H265
                } else {
                    FFMPEG_SMALL_FILE_CRF_H264
                }
            }
        }
    }

    private fun defaultVideoCrfFor(videoCodec: String): String {
        return if (videoCodec == FFMPEG_VIDEO_ENCODER_H265) {
            FFMPEG_DEFAULT_CRF_H265
        } else {
            FFMPEG_DEFAULT_CRF_H264
        }
    }

    private fun ffmpegVideoFilterFor(
        input: ConversionTaskInput,
        durationMs: Long?
    ): String? {
        val isOpticalFlowActive = input.category == ConversionMediaCategory.Video &&
            !isVideoGifOutput(input) &&
            input.videoOptions.frameInterpolation == VideoFrameInterpolationMode.OpticalFlow2x
        val advanced = if (input.videoOptions.compressionMode == VideoCompressionMode.Standard && !isOpticalFlowActive) {
            input.videoOptions.advanced
        } else {
            VideoAdvancedOptions()
        }
        val filters = buildList {
            if (isOpticalFlowActive) {
                val effectiveShortSide = input.videoOptions.maxShortSidePixels?.takeIf { it > 0 }
                    ?: run {
                        val sourceSize = input.inputInfo?.let { info ->
                            val w = info.width ?: 0
                            val h = info.height ?: 0
                            if (w > 0 && h > 0) VideoSize(w, h) else null
                        } ?: readVideoSize(input.inputUri)
                        if (sourceSize == null || sourceSize.shortSide > 1080) 1080 else null
                    }
                ffmpegVideoScaleFilterFor(effectiveShortSide)?.let { add(it) }
                val sourceFps = probeVideoFrameRate(input.inputUri, input.inputInfo?.frameRate)
                val targetFps = (sourceFps * 2.0f).coerceIn(24.0f, 120.0f)
                val targetFpsFormatted = if (targetFps % 1.0f == 0.0f) {
                    targetFps.toInt().toString()
                } else {
                    String.format(Locale.US, "%.3f", targetFps).trimEnd('0').trimEnd('.')
                }
                add("minterpolate=fps=$targetFpsFormatted:mi_mode=mci:mc_mode=obmc:me_mode=bilat:me=epzs:mb_size=16:search_param=32:vsbmc=0:scd=fdiff:scd_threshold=10")
            } else {
                addAll(ffmpegVideoRotationFiltersFor(advanced.rotation))
                addAll(ffmpegVideoMirrorFiltersFor(advanced.mirror))
                ffmpegVideoAspectFilterFor(advanced.aspectRatio)?.let { add(it) }
                ffmpegVideoScaleFilterFor(input.videoOptions.maxShortSidePixels)?.let { add(it) }
                if (advanced.reverse) add("reverse")
                advanced.fadeInSeconds?.let { seconds ->
                    add("fade=t=in:st=0:d=${ffmpegSeconds(seconds.toDouble())}")
                }
                advanced.fadeOutSeconds?.let { seconds ->
                    durationMs?.let {
                        add(
                            "fade=t=out:st=${ffmpegFadeOutStartSeconds(it, seconds)}:" +
                                "d=${ffmpegSeconds(seconds.toDouble())}"
                        )
                    }
                }
            }
        }
        return filters.takeIf { it.isNotEmpty() }?.joinToString(separator = ",")
    }

    private fun ffmpegVideoRotationFiltersFor(rotation: VideoRotationMode): List<String> {
        return when (rotation) {
            VideoRotationMode.Clockwise90 -> listOf("transpose=clock")
            VideoRotationMode.CounterClockwise90 -> listOf("transpose=cclock")
            VideoRotationMode.Rotate180 -> listOf("transpose=clock", "transpose=clock")
            VideoRotationMode.None -> emptyList()
        }
    }

    private fun ffmpegVideoMirrorFiltersFor(mirror: VideoMirrorMode): List<String> {
        return when (mirror) {
            VideoMirrorMode.Horizontal -> listOf("hflip")
            VideoMirrorMode.Vertical -> listOf("vflip")
            VideoMirrorMode.Both -> listOf("hflip", "vflip")
            VideoMirrorMode.Off -> emptyList()
        }
    }

    private fun ffmpegVideoAspectFilterFor(aspectRatio: VideoAspectRatioMode): String? {
        val ratio = when (aspectRatio) {
            VideoAspectRatioMode.Fit16By9,
            VideoAspectRatioMode.Crop16By9 -> "(16/9)"
            VideoAspectRatioMode.Fit9By16,
            VideoAspectRatioMode.Crop9By16 -> "(9/16)"
            VideoAspectRatioMode.Fit1By1,
            VideoAspectRatioMode.Crop1By1 -> "1"
            VideoAspectRatioMode.Keep -> return null
        }
        return when (aspectRatio) {
            VideoAspectRatioMode.Fit16By9,
            VideoAspectRatioMode.Fit9By16,
            VideoAspectRatioMode.Fit1By1 ->
                "pad=w='if(gt(iw/ih\\,$ratio)\\,iw\\,ceil(ih*$ratio/2)*2)':" +
                    "h='if(gt(iw/ih\\,$ratio)\\,ceil(iw/($ratio)/2)*2\\,ih)':" +
                    "x=(ow-iw)/2:y=(oh-ih)/2:color=black"
            VideoAspectRatioMode.Crop16By9,
            VideoAspectRatioMode.Crop9By16,
            VideoAspectRatioMode.Crop1By1 ->
                "crop=w='if(gt(iw/ih\\,$ratio)\\,floor(ih*$ratio/2)*2\\,iw)':" +
                    "h='if(gt(iw/ih\\,$ratio)\\,ih\\,floor(iw/($ratio)/2)*2)':" +
                    "x=(iw-ow)/2:y=(ih-oh)/2"
            VideoAspectRatioMode.Keep -> null
        }
    }

    private fun ffmpegVideoScaleFilterFor(targetShortSide: Int?): String? {
        val shortSide = targetShortSide?.takeIf { it > 0 } ?: return null
        return "scale=w='if(gte(iw\\,ih)\\,-2\\,min(iw\\,$shortSide))':" +
            "h='if(gte(iw\\,ih)\\,min(ih\\,$shortSide)\\,-2)':flags=lanczos"
    }

    private fun ffmpegAudioFilterFor(
        audioOptions: AudioExportOptions,
        durationMs: Long?,
        forceReverse: Boolean = false
    ): String? {
        val advanced = audioOptions.advanced
        val filters = buildList {
            if (forceReverse || advanced.reverse) add("areverse")
            ffmpegAudioDenoiseFilterFor(advanced.noiseReduction)?.let { add(it) }
            when (advanced.volume) {
                AudioVolumeMode.Mute -> add("volume=0")
                AudioVolumeMode.Half -> add("volume=0.5")
                AudioVolumeMode.OneAndHalf -> add("volume=1.5")
                AudioVolumeMode.Double -> add("volume=2.0")
                AudioVolumeMode.Original -> Unit
            }
            when (advanced.echo) {
                AudioEchoMode.Light -> add("aecho=0.8:0.88:60:0.35")
                AudioEchoMode.Room -> add("aecho=0.8:0.9:80|160:0.35|0.25")
                AudioEchoMode.Off -> Unit
            }
            advanced.fadeInSeconds?.let { seconds ->
                add("afade=t=in:st=0:d=${ffmpegSeconds(seconds.toDouble())}")
            }
            advanced.fadeOutSeconds?.let { seconds ->
                durationMs?.let {
                    add(
                        "afade=t=out:st=${ffmpegFadeOutStartSeconds(it, seconds)}:" +
                            "d=${ffmpegSeconds(seconds.toDouble())}"
                    )
                }
            }
        }
        return filters.takeIf { it.isNotEmpty() }?.joinToString(separator = ",")
    }

    private fun ffmpegAudioDenoiseFilterFor(mode: AudioNoiseReductionMode): String? {
        return when (mode) {
            AudioNoiseReductionMode.Light -> "afftdn=nr=6"
            AudioNoiseReductionMode.Standard -> "afftdn=nr=12"
            AudioNoiseReductionMode.Off -> null
        }
    }

    private fun ffmpegFadeOutStartSeconds(durationMs: Long, fadeSeconds: Float): String {
        val startSeconds = (durationMs.toDouble() / 1000.0 - fadeSeconds.toDouble())
            .coerceAtLeast(0.0)
        return ffmpegSeconds(startSeconds)
    }

    private fun ffmpegSeconds(seconds: Double): String {
        return String.format(Locale.US, "%.3f", seconds)
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
        return when (targetExtension) {
            "mp3" -> FfmpegAudioProfile(
                codec = FFMPEG_MP3_ENCODER,
                format = "mp3",
                requiredEncoder = FFMPEG_MP3_ENCODER
            )
            "m4a" -> ffmpegAacAudioProfile()
            "wav" -> FfmpegAudioProfile(
                codec = FFMPEG_WAV_ENCODER,
                format = "wav",
                supportsBitrate = false,
                requiredEncoder = FFMPEG_WAV_ENCODER
            )
            "flac" -> FfmpegAudioProfile(
                codec = FFMPEG_FLAC_ENCODER,
                format = "flac",
                supportsBitrate = false,
                requiredEncoder = FFMPEG_FLAC_ENCODER
            )
            "wma" -> FfmpegAudioProfile(
                codec = FFMPEG_WMA_ENCODER,
                format = "asf",
                requiredEncoder = FFMPEG_WMA_ENCODER
            )
            "opus" -> FfmpegAudioProfile(
                codec = FFMPEG_OPUS_ENCODER,
                format = "opus",
                requiredEncoder = FFMPEG_OPUS_ENCODER
            )
            else -> null
        }
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
                localizedText(R.string.task_processing),
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

    private fun ffmpegMissingAdvancedMetadataMessageFor(
        input: ConversionTaskInput,
        durationMs: Long?
    ): LocalizedText? {
        if (durationMs != null) return null
        val presetCompressionActive =
            input.category == ConversionMediaCategory.Video &&
                !isVideoGifOutput(input) &&
                input.videoOptions.compressionMode != VideoCompressionMode.Standard
        val videoAdvanced = if (presetCompressionActive) {
            VideoAdvancedOptions()
        } else {
            input.videoOptions.advanced
        }
        val audioAdvanced = if (presetCompressionActive) {
            ffmpegVideoAudioOptionsFor(input.videoOptions.compressionMode, input.audioOptions).advanced
        } else {
            input.audioOptions.advanced
        }
        val videoReverseApplies =
            input.category == ConversionMediaCategory.Video &&
                !isVideoGifOutput(input) &&
                videoAdvanced.reverse
        val audioFadeOutApplies =
            audioAdvanced.volume != AudioVolumeMode.Mute &&
                audioAdvanced.fadeOutSeconds != null
        val needsDuration = when (input.category) {
            ConversionMediaCategory.Video ->
                !isVideoGifOutput(input) &&
                    (videoAdvanced.fadeOutSeconds != null || audioFadeOutApplies)
            ConversionMediaCategory.Audio ->
                audioAdvanced.fadeOutSeconds != null
            ConversionMediaCategory.Image,
            ConversionMediaCategory.Pdf,
            ConversionMediaCategory.Document,
            ConversionMediaCategory.Font,
            ConversionMediaCategory.Subtitle -> false
        }
        return when {
            videoReverseApplies ->
                localizedText(R.string.text_task_message_compatibility_engine_needs_duration_metadata_for_reverse_playback)
            needsDuration ->
                localizedText(R.string.text_task_message_compatibility_engine_needs_duration_metadata_for_fade_out)
            else -> null
        }
    }

    private fun ffmpegUnsupportedAdvancedSelectionMessageFor(
        input: ConversionTaskInput,
        durationMs: Long?
    ): LocalizedText? {
        if (
            input.category == ConversionMediaCategory.Video &&
            !isVideoGifOutput(input) &&
            input.videoOptions.compressionMode == VideoCompressionMode.Standard &&
            input.videoOptions.advanced.reverse
        ) {
            val reverseBudgetMessage = ffmpegUnsafeVideoReverseMessageFor(input, durationMs)
            if (reverseBudgetMessage != null) return reverseBudgetMessage
        }
        return null
    }

    private fun ffmpegUnsafeVideoReverseMessageFor(
        input: ConversionTaskInput,
        durationMs: Long?
    ): LocalizedText? {
        if (durationMs == null) {
            return localizedText(R.string.text_task_message_compatibility_engine_needs_duration_metadata_for_reverse_playback)
        }
        if (
            durationMs > FFMPEG_VIDEO_REVERSE_MAX_DURATION_MS
        ) {
            return localizedText(R.string.text_task_message_reverse_video_supports_files_up_to_60_seconds)
        }
        val reverseBufferSize = reverseBufferVideoSizeFor(input)
            ?: return localizedText(R.string.text_task_message_reverse_video_needs_readable_video_size_metadata)
        val frameRate = input.inputInfo
            ?.frameRate
            ?.takeIf { it > 0f }
            ?.toDouble()
            ?.coerceIn(1.0, FFMPEG_VIDEO_REVERSE_MAX_FPS_ESTIMATE)
            ?: FFMPEG_VIDEO_REVERSE_DEFAULT_FPS_ESTIMATE
        val frameCount = (durationMs.toDouble() / 1000.0 * frameRate)
            .toLong()
            .coerceAtLeast(1L)
        val estimatedBufferBytes =
            reverseBufferSize.width.toLong() *
                reverseBufferSize.height.toLong() *
                FFMPEG_VIDEO_REVERSE_BYTES_PER_PIXEL *
                frameCount
        return if (estimatedBufferBytes > FFMPEG_VIDEO_REVERSE_MAX_BUFFER_BYTES) {
            Log.w(
                TAG,
                "Video reverse rejected by memory budget displayName=${input.displayName} " +
                    "width=${reverseBufferSize.width} height=${reverseBufferSize.height} " +
                    "durationMs=$durationMs frameRate=$frameRate " +
                    "estimatedBufferBytes=$estimatedBufferBytes"
            )
            localizedText(R.string.text_task_message_reverse_video_only_supports_very_short_low_resolution_clips)
        } else {
            null
        }
    }

    private fun reverseBufferVideoSizeFor(input: ConversionTaskInput): VideoSize? {
        val infoWidth = input.inputInfo?.width
        val infoHeight = input.inputInfo?.height
        val sourceSize = if (
            infoWidth != null &&
            infoHeight != null &&
            infoWidth > 0 &&
            infoHeight > 0
        ) {
            VideoSize(infoWidth, infoHeight)
        } else {
            readVideoSize(input.inputUri)
        } ?: return null
        val targetShortSide = input.videoOptions.maxShortSidePixels
            ?.takeIf { it > 0 }
            ?: return sourceSize
        return if (sourceSize.shortSide > targetShortSide) {
            scaledVideoSizeFor(sourceSize, targetShortSide)
        } else {
            sourceSize
        }
    }

    private fun ffmpegMissingFilterMessageFor(input: ConversionTaskInput): LocalizedText? {
        val missingFilter = requiredFfmpegFiltersFor(input).firstOrNull { filter ->
            ffmpegFilterAvailable(filter) == false
        } ?: return null
        Log.e(
            TAG,
            "FFmpeg compatibility package is missing filter=$missingFilter " +
                "target=${input.targetFormat} displayName=${input.displayName}"
        )
        return compatibilityMissingFilterMessageFor(missingFilter)
    }

    private fun compatibilityMissingFilterMessageFor(filter: String): LocalizedText {
        return when (filter) {
            "reverse",
            "areverse" -> localizedText(R.string.text_task_message_compatibility_engine_needs_reverse_filters)
            "afftdn" -> localizedText(R.string.text_task_message_compatibility_engine_needs_the_audio_denoise_filter)
            else -> localizedText(R.string.text_task_message_compatibility_engine_is_missing_an_advanced_filter)
        }
    }

    private fun requiredFfmpegFiltersFor(input: ConversionTaskInput): Set<String> {
        return buildSet {
            val presetCompressionActive =
                input.category == ConversionMediaCategory.Video &&
                    !isVideoGifOutput(input) &&
                    input.videoOptions.compressionMode != VideoCompressionMode.Standard
            val interpolationActive =
                input.category == ConversionMediaCategory.Video &&
                    !isVideoGifOutput(input) &&
                    input.videoOptions.frameInterpolation == VideoFrameInterpolationMode.OpticalFlow2x
            if (input.category == ConversionMediaCategory.Video && !isVideoGifOutput(input)) {
                if (interpolationActive) {
                    add("minterpolate")
                }
                val videoAdvanced = if (presetCompressionActive || interpolationActive) {
                    VideoAdvancedOptions()
                } else {
                    input.videoOptions.advanced
                }
                if (videoAdvanced.reverse) add("reverse")
                if (
                    videoAdvanced.rotation == VideoRotationMode.Clockwise90 ||
                    videoAdvanced.rotation == VideoRotationMode.CounterClockwise90 ||
                    videoAdvanced.rotation == VideoRotationMode.Rotate180
                ) {
                    add("transpose")
                }
                when (videoAdvanced.mirror) {
                    VideoMirrorMode.Horizontal -> add("hflip")
                    VideoMirrorMode.Vertical -> add("vflip")
                    VideoMirrorMode.Both -> {
                        add("hflip")
                        add("vflip")
                    }
                    VideoMirrorMode.Off -> Unit
                }
                when (videoAdvanced.aspectRatio) {
                    VideoAspectRatioMode.Fit16By9,
                    VideoAspectRatioMode.Fit9By16,
                    VideoAspectRatioMode.Fit1By1 -> add("pad")
                    VideoAspectRatioMode.Crop16By9,
                    VideoAspectRatioMode.Crop9By16,
                    VideoAspectRatioMode.Crop1By1 -> add("crop")
                    VideoAspectRatioMode.Keep -> Unit
                }
                if (
                    videoAdvanced.fadeInSeconds != null ||
                    videoAdvanced.fadeOutSeconds != null
                ) {
                    add("fade")
                }
            }

            val audioOptions = if (presetCompressionActive) {
                ffmpegVideoAudioOptionsFor(input.videoOptions.compressionMode, input.audioOptions)
            } else {
                input.audioOptions
            }
            val audioAdvanced = audioOptions.advanced
            val audioFiltersApply = when (input.category) {
                ConversionMediaCategory.Audio -> true
                ConversionMediaCategory.Video ->
                    !isVideoGifOutput(input) &&
                        audioAdvanced.volume != AudioVolumeMode.Mute
                ConversionMediaCategory.Image,
                ConversionMediaCategory.Pdf,
                ConversionMediaCategory.Document,
                ConversionMediaCategory.Font,
                ConversionMediaCategory.Subtitle -> false
            }
            if (audioFiltersApply) {
                if (
                    audioAdvanced.reverse ||
                    (
                        input.category == ConversionMediaCategory.Video &&
                            input.videoOptions.compressionMode == VideoCompressionMode.Standard &&
                            !interpolationActive &&
                            input.videoOptions.advanced.reverse
                        )
                ) {
                    add("areverse")
                }
                if (audioAdvanced.noiseReduction != AudioNoiseReductionMode.Off) add("afftdn")
                when (audioAdvanced.volume) {
                    AudioVolumeMode.Half,
                    AudioVolumeMode.OneAndHalf,
                    AudioVolumeMode.Double -> add("volume")
                    AudioVolumeMode.Mute -> {
                        if (input.category == ConversionMediaCategory.Audio) add("volume")
                    }
                    AudioVolumeMode.Original -> Unit
                }
                if (audioAdvanced.echo != AudioEchoMode.Off) add("aecho")
                if (
                    audioAdvanced.fadeInSeconds != null ||
                    audioAdvanced.fadeOutSeconds != null
                ) {
                    add("afade")
                }
            }
        }
    }

    private fun ffmpegMissingEncoderMessageFor(input: ConversionTaskInput): LocalizedText? {
        val requiredEncoders = when (input.category) {
            ConversionMediaCategory.Video -> {
                if (isVideoGifOutput(input)) {
                    listOf(FFMPEG_GIF_ENCODER)
                } else {
                    val profile = ffmpegVideoProfileFor(input) ?: return null
                    val audioOptions = ffmpegVideoAudioOptionsFor(
                        compressionMode = input.videoOptions.compressionMode,
                        manualAudioOptions = input.audioOptions
                    )
                    buildList {
                        add(profile.videoCodec)
                        if (audioOptions.advanced.volume != AudioVolumeMode.Mute) {
                            add(FFMPEG_AAC_ENCODER)
                        }
                    }
                }
            }
            ConversionMediaCategory.Audio -> {
                val profile = ffmpegAudioProfileFor(input) ?: return null
                listOfNotNull(profile.requiredEncoder)
            }
            ConversionMediaCategory.Image,
            ConversionMediaCategory.Pdf,
            ConversionMediaCategory.Document,
            ConversionMediaCategory.Font,
            ConversionMediaCategory.Subtitle -> return null
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

    private fun ffmpegFilterAvailable(filter: String): Boolean? {
        ffmpegFilterAvailability[filter]?.let { return it }

        val output = runCatching {
            val session = FFmpegKit.executeWithArguments(
                arrayOf("-hide_banner", "-filters")
            )
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                session.getAllLogsAsString(FFMPEG_ENCODER_PROBE_TIMEOUT_MS).orEmpty()
            } else {
                ""
            }
        }.onFailure { exception ->
            Log.w(TAG, "Could not probe FFmpeg filter list", exception)
        }.getOrNull() ?: return null
        if (output.isBlank()) return null

        val available = ffmpegFilterOutputContains(output, filter)
        ffmpegFilterAvailability[filter] = available
        return available
    }

    private fun ffmpegFilterOutputContains(output: String, filter: String): Boolean {
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .any { line ->
                val tokens = line.split(WHITESPACE_REGEX, limit = 3)
                tokens.size >= 2 && tokens[1] == filter
            }
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
    ): LocalizedText {
        return when {
            encoder == FFMPEG_MP3_ENCODER &&
                audioTargetExtensionFor(input.targetFormat) == "mp3" ->
                localizedText(R.string.text_task_message_compatibility_engine_needs_an_mp3_capable_ffmpeg_package)
            encoder == FFMPEG_VIDEO_ENCODER_H264 ->
                localizedText(R.string.text_task_message_compatibility_engine_needs_an_h_264_capable_ffmpeg_package)
            encoder == FFMPEG_VIDEO_ENCODER_H265 ->
                localizedText(R.string.text_task_message_compatibility_engine_needs_an_h_265_capable_ffmpeg_package)
            encoder == FFMPEG_AAC_ENCODER ->
                localizedText(R.string.text_task_message_compatibility_engine_needs_an_aac_capable_ffmpeg_package)
            encoder == FFMPEG_WAV_ENCODER ->
                localizedText(R.string.text_task_message_compatibility_engine_needs_a_pcm_wav_capable_ffmpeg_package)
            encoder == FFMPEG_FLAC_ENCODER ->
                localizedText(R.string.text_task_message_compatibility_engine_needs_a_flac_capable_ffmpeg_package)
            encoder == FFMPEG_WMA_ENCODER ->
                localizedText(R.string.text_task_message_compatibility_engine_needs_a_wma_capable_ffmpeg_package)
            encoder == FFMPEG_OPUS_ENCODER ->
                localizedText(R.string.text_task_message_compatibility_engine_needs_an_opus_capable_ffmpeg_package)
            encoder == FFMPEG_GIF_ENCODER &&
                isVideoGifOutput(input) ->
                localizedText(R.string.text_task_message_compatibility_engine_needs_a_gif_capable_ffmpeg_package)
            input.category == ConversionMediaCategory.Video ->
                localizedText(R.string.text_task_message_compatibility_engine_cannot_encode_this_video_format_yet)
            else ->
                localizedText(R.string.text_task_message_compatibility_engine_cannot_encode_this_audio_format_yet)
        }
    }

    private fun compatibilityFailureMessageFor(
        input: ConversionTaskInput,
        outputTail: String = ""
    ): LocalizedText {
        val normalizedTail = outputTail.lowercase(Locale.US)
        if (
            input.category == ConversionMediaCategory.Audio &&
            audioTargetExtensionFor(input.targetFormat) == "mp3" &&
            normalizedTail.contains(FFMPEG_MP3_ENCODER)
        ) {
            return localizedText(R.string.text_task_message_compatibility_engine_needs_an_mp3_capable_ffmpeg_package)
        }
        if (
            input.category == ConversionMediaCategory.Audio &&
            (
                normalizedTail.contains("unknown encoder") ||
                    normalizedTail.contains("encoder not found") ||
                    normalizedTail.contains("invalid encoder")
            )
        ) {
            return localizedText(R.string.text_task_message_compatibility_engine_cannot_encode_this_audio_format_yet)
        }
        if (
            input.category == ConversionMediaCategory.Audio &&
            (
                normalizedTail.contains("specified sample rate") ||
                    normalizedTail.contains("sample rate is not supported") ||
                    normalizedTail.contains("sample rate not supported")
            )
        ) {
            return localizedText(R.string.text_task_message_selected_sample_rate_is_not_supported_by_this_audio_format)
        }
        if (
            input.category == ConversionMediaCategory.Audio &&
            (
                normalizedTail.contains("codec not currently supported in container") ||
                    normalizedTail.contains("could not find tag for codec") ||
                    normalizedTail.contains("invalid argument")
            )
        ) {
            return localizedText(R.string.text_task_message_compatibility_engine_could_not_write_this_audio_container)
        }
        if (
            isVideoGifOutput(input) &&
            (
                normalizedTail.contains("unknown encoder") ||
                    normalizedTail.contains("encoder not found") ||
                    normalizedTail.contains("invalid encoder")
            )
        ) {
            return localizedText(R.string.text_task_message_compatibility_engine_needs_a_gif_capable_ffmpeg_package)
        }
        if (isVideoGifOutput(input)) {
            return localizedText(R.string.text_task_message_compatibility_engine_could_not_create_this_gif)
        }
        if (
            input.category == ConversionMediaCategory.Video &&
            (
                normalizedTail.contains("image size is too small") ||
                    normalizedTail.contains("width not divisible by") ||
                    normalizedTail.contains("height not divisible by")
            )
        ) {
            return localizedText(R.string.text_task_message_advanced_video_settings_produced_an_unsupported_frame_size)
        }
        if (
            input.category == ConversionMediaCategory.Video &&
            (
                normalizedTail.contains("unknown encoder") ||
                    normalizedTail.contains("encoder not found") ||
                    normalizedTail.contains("invalid encoder")
            )
        ) {
            return localizedText(R.string.text_task_message_compatibility_engine_cannot_encode_this_video_format_yet)
        }
        if (
            input.category == ConversionMediaCategory.Video &&
            (
                normalizedTail.contains("codec not currently supported in container") ||
                    normalizedTail.contains("could not find tag for codec") ||
                    normalizedTail.contains("invalid argument")
            )
        ) {
            return localizedText(R.string.text_task_message_compatibility_engine_could_not_write_this_video_container)
        }
        return when (input.category) {
            ConversionMediaCategory.Video -> when (videoTargetExtensionFor(input.targetFormat)) {
                "mkv" -> localizedText(R.string.text_task_message_compatibility_engine_could_not_transcode_this_file_to_mkv)
                "mov" -> localizedText(R.string.text_task_message_compatibility_engine_could_not_transcode_this_file_to_mov)
                "gif" -> localizedText(R.string.text_task_message_compatibility_engine_could_not_create_this_gif)
                else -> localizedText(R.string.text_task_message_compatibility_engine_could_not_transcode_this_file_to_mp4)
            }
            ConversionMediaCategory.Audio ->
                localizedText(R.string.text_task_message_compatibility_engine_could_not_convert_this_audio)
            ConversionMediaCategory.Image ->
                localizedText(R.string.ui_failed)
            ConversionMediaCategory.Pdf ->
                localizedText(R.string.message_compatibility_engine_is_not_connected_for_pdfs)
            ConversionMediaCategory.Document ->
                localizedText(R.string.message_compatibility_engine_is_not_connected_for_documents)
            ConversionMediaCategory.Font ->
                localizedText(R.string.message_compatibility_engine_is_not_connected_for_fonts)
            ConversionMediaCategory.Subtitle ->
                localizedText(R.string.text_task_message_compatibility_engine_could_not_convert_this_subtitle)
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

    private fun compatibilityStartupFailureMessageFor(exception: Throwable): LocalizedText {
        return if (isFfmpegKitStartupFailure(exception)) {
            localizedText(R.string.message_compatibility_engine_could_not_start_on_this_device)
        } else {
            localizedText(R.string.text_task_message_compatibility_engine_failed_before_export)
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

    private fun ffmpegSourceDurationMsFor(
        input: ConversionTaskInput,
        inputPath: String
    ): Long? {
        return readDurationMs(input.inputUri) ?: readFfmpegDurationMs(inputPath)
    }

    private fun ffmpegProgressDurationMsFor(
        input: ConversionTaskInput,
        sourceDurationMs: Long?,
        trimWindow: FfmpegTrimWindow
    ): Long? {
        val durationMs = trimWindow.effectiveDurationMs ?: sourceDurationMs
        return if (isVideoGifOutput(input)) {
            durationMs?.coerceAtMost(FFMPEG_VIDEO_GIF_MAX_DURATION_MS)
        } else {
            durationMs
        }
    }

    private fun ffmpegTrimWindowFor(
        input: ConversionTaskInput,
        sourceDurationMs: Long?
    ): FfmpegTrimWindow {
        val trimRange = ffmpegTrimRangeFor(input)
        if (!trimRange.isEnabled) return FfmpegTrimWindow()

        val sourceDuration = sourceDurationMs
            ?: return FfmpegTrimWindow(errorMessage = localizedText(R.string.text_task_message_trimming_needs_readable_media_duration))
        val startSeconds = trimRange.startSeconds ?: 0.0
        val endSeconds = trimRange.endSeconds
        if (startSeconds < 0.0) {
            return FfmpegTrimWindow(errorMessage = localizedText(R.string.text_task_message_trim_start_must_be_zero_or_greater))
        }
        val startMs = trimSecondsToMs(startSeconds)
            ?: return FfmpegTrimWindow(errorMessage = localizedText(R.string.ui_trim_range_too_large))
        if (startMs >= sourceDuration) {
            return FfmpegTrimWindow(errorMessage = localizedText(R.string.ui_trim_start_before_duration))
        }
        val effectiveDurationMs = if (endSeconds != null) {
            val endMs = trimSecondsToMs(endSeconds)
                ?: return FfmpegTrimWindow(errorMessage = localizedText(R.string.ui_trim_range_too_large))
            when {
                endMs <= startMs ->
                    return FfmpegTrimWindow(errorMessage = localizedText(R.string.ui_trim_end_after_start))
                endMs > sourceDuration ->
                    return FfmpegTrimWindow(errorMessage = localizedText(R.string.ui_trim_end_within_duration))
            }
            endMs - startMs
        } else {
            sourceDuration - startMs
        }

        if (trimRange.splitPoints.isNotEmpty()) {
            val effectiveEndSec = endSeconds ?: (sourceDuration.toDouble() / 1_000.0)
            val cutPoints = mutableListOf<Double>()
            cutPoints.add(startSeconds)
            cutPoints.addAll(trimRange.splitPoints)
            cutPoints.add(effectiveEndSec)
            for (i in 0 until cutPoints.size - 1) {
                val p1 = cutPoints[i]
                val p2 = cutPoints[i + 1]
                if (p2 <= p1) {
                    return FfmpegTrimWindow(errorMessage = localizedText(R.string.message_split_points_must_be_in_strictly_increasing_order))
                }
                if (p2 * 1_000.0 > sourceDuration) {
                    return FfmpegTrimWindow(errorMessage = localizedText(R.string.message_split_points_must_not_exceed_media_duration))
                }
            }
            val segments = mutableListOf<FfmpegTrimSegment>()
            for (i in 0 until cutPoints.size - 1) {
                val p1 = cutPoints[i]
                val p2 = cutPoints[i + 1]
                val segEndMs = trimSecondsToMs(p2) ?: return FfmpegTrimWindow(errorMessage = localizedText(R.string.ui_trim_range_too_large))
                val segStartMs = trimSecondsToMs(p1) ?: return FfmpegTrimWindow(errorMessage = localizedText(R.string.ui_trim_range_too_large))
                val segDurationMs = segEndMs - segStartMs
                if (segDurationMs <= 0L) {
                    return FfmpegTrimWindow(errorMessage = localizedText(R.string.message_split_points_must_be_distinct))
                }
                segments.add(
                    FfmpegTrimSegment(
                        startSeconds = p1,
                        durationLimitMs = segDurationMs,
                        effectiveDurationMs = segDurationMs
                    )
                )
            }
            return FfmpegTrimWindow(segments = segments)
        }

        return FfmpegTrimWindow(
            segments = listOf(
                FfmpegTrimSegment(
                    startSeconds = startSeconds,
                    durationLimitMs = endSeconds?.let { effectiveDurationMs },
                    effectiveDurationMs = effectiveDurationMs
                )
            )
        )
    }

    private fun ffmpegTrimRangeFor(input: ConversionTaskInput): MediaTrimRange {
        return when (input.category) {
            ConversionMediaCategory.Video -> input.videoOptions.trimRange
            ConversionMediaCategory.Audio -> input.audioOptions.trimRange
            ConversionMediaCategory.Image,
            ConversionMediaCategory.Pdf,
            ConversionMediaCategory.Document,
            ConversionMediaCategory.Font,
            ConversionMediaCategory.Subtitle -> MediaTrimRange()
        }
    }

    private fun trimSecondsToMs(seconds: Double): Long? {
        if (!seconds.isFinite() || seconds < 0.0) return null
        return runCatching { Math.round(seconds * 1_000.0) }.getOrNull()
    }

    private fun readFfmpegDurationMs(inputPath: String): Long? {
        val durationMs = runCatching {
            FFprobeKit.getMediaInformation(
                inputPath,
                FFMPEG_MEDIA_INFORMATION_PROBE_TIMEOUT_MS
            ).getMediaInformation()
                ?.getDuration()
                ?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.times(1_000.0)
                ?.toLong()
                ?.takeIf { it > 0L }
        }.onFailure { exception ->
            Log.d(TAG, "FFprobe could not read media duration", exception)
        }.getOrNull()

        if (durationMs != null) {
            Log.d(TAG, "FFprobe provided media durationMs=$durationMs")
        }
        return durationMs
    }

    private fun isVideoContactSheetOutput(input: ConversionTaskInput): Boolean =
        input.category == ConversionMediaCategory.Video && TargetId.fromKey(input.targetFormat)?.isContactSheet == true


    private fun shouldUseCompatibilityEngine(input: ConversionTaskInput): Boolean {
        return when (input.category) {
            ConversionMediaCategory.Video -> !isVideoContactSheetOutput(input)
            ConversionMediaCategory.Audio -> true
            ConversionMediaCategory.Image -> false
            ConversionMediaCategory.Pdf -> false
            ConversionMediaCategory.Document -> false
            ConversionMediaCategory.Font -> false
            ConversionMediaCategory.Subtitle -> false
        }
    }

    private fun isVideoGifOutput(input: ConversionTaskInput): Boolean {
        return input.category == ConversionMediaCategory.Video &&
            videoTargetExtensionFor(input.targetFormat) == "gif"
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

    private fun probeVideoFrameRate(
        uri: Uri,
        fallbackCaptureFps: Float? = null
    ): Float {
        val extractorFps = runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(this@ConversionService, uri, null)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/")) {
                        val fps = runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }.getOrNull()
                            ?: runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrNull()
                        if (fps != null && fps in 1.0f..240.0f) {
                            return@runCatching fps
                        }
                        extractor.selectTrack(i)
                        val t0 = extractor.sampleTime
                        if (t0 >= 0L && extractor.advance()) {
                            val t1 = extractor.sampleTime
                            val dtUs = t1 - t0
                            if (dtUs in 4_000..100_000) {
                                val calculatedFps = 1_000_000.0f / dtUs.toFloat()
                                if (calculatedFps in 1.0f..240.0f) {
                                    return@runCatching calculatedFps
                                }
                            }
                        }
                    }
                }
                null
            } finally {
                runCatching { extractor.release() }
            }
        }.getOrNull()
        if (extractorFps != null && extractorFps in 1.0f..240.0f) {
            Log.d(TAG, "MediaExtractor detected frameRate=$extractorFps from $uri")
            return extractorFps
        }

        if (fallbackCaptureFps != null && fallbackCaptureFps in 1.0f..240.0f) {
            Log.d(TAG, "Using cached capture frameRate=$fallbackCaptureFps for $uri")
            return fallbackCaptureFps
        }

        val retrieverFps = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this@ConversionService, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toFloatOrNull()
                    ?.takeIf { it in 1.0f..240.0f }
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
        if (retrieverFps != null && retrieverFps in 1.0f..240.0f) {
            Log.d(TAG, "MediaMetadataRetriever detected frameRate=$retrieverFps from $uri")
            return retrieverFps
        }

        return 30.0f
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
        ConversionTaskStore.markSaving(taskIndex)
        updateNotification(localizedText(R.string.task_saving), (ConversionTaskStore.aggregateProgress() * 100).toInt())

        copyThread = Thread {
            var outputUri: Uri? = null
            try {
                val outputProfile = outputProfileFor(input)
                    ?: throw LocalizedFailure(localizedText(R.string.message_unsupported_output_format))
                val outputDisplayName = outputNameFor(input, outputProfile.extension)
                val tempFileSizeBytes = tempFile.length().takeIf { it >= 0L }
                val createdOutput = createOutput(
                    input,
                    outputDisplayName,
                    outputProfile
                )
                val createdOutputUri = createdOutput.uri
                outputUri = createdOutputUri
                copyFileToOutput(tempFile, createdOutputUri)
                finalizeOutput(createdOutput.isDefaultPublicDestination, createdOutputUri, outputProfile.mimeType)
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
                        outputDirectoryUri = createdOutput.outputDirectoryUri,
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
                    failCurrentTask(localizedText(R.string.text_task_message_could_not_save_output_file))
                }
            }
        }.also { it.start() }
    }

    private fun saveCompletedExportFiles(
        input: ConversionTaskInput,
        tempFiles: List<File>,
        outputProfile: OutputProfile
    ) {
        ConversionTaskStore.markSaving(taskIndex)
        updateNotification(localizedText(R.string.task_saving), (ConversionTaskStore.aggregateProgress() * 100).toInt())

        copyThread = Thread {
            val outputUris = mutableListOf<Uri>()
            var outputDirectoryUri: Uri? = null
            var outputSizeBytes = 0L
            try {
                tempFiles.forEachIndexed { index, tempFile ->
                    if (Thread.currentThread().isInterrupted || ConversionTaskStore.isCancelled()) {
                        throw InterruptedException()
                    }
                    outputSizeBytes += tempFile.length().coerceAtLeast(0L)
                    val createdOutput = createOutput(
                        input,
                        outputNameForPage(
                            input = input,
                            extension = outputProfile.extension,
                            pageIndex = index,
                            pageCount = tempFiles.size
                        ),
                        outputProfile
                    )
                    val createdOutputUri = createdOutput.uri
                    outputDirectoryUri = createdOutput.outputDirectoryUri
                    outputUris.add(createdOutputUri)
                    copyFileToOutput(tempFile, createdOutputUri)
                    finalizeOutput(createdOutput.isDefaultPublicDestination, createdOutputUri, outputProfile.mimeType)
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
                        outputDirectoryUri = outputDirectoryUri,
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
                    failCurrentTask(localizedText(R.string.text_task_message_could_not_save_output_file))
                }
            }
        }.also { it.start() }
    }

    private fun saveCompletedExportFilesInFolder(
        input: ConversionTaskInput,
        tempFiles: List<File>,
        outputProfile: OutputProfile,
        folderName: String,
        nameGenerator: (Int, Int) -> String = { index, count ->
            outputNameForFrame(input, outputProfile.extension, index, count)
        }
    ) {
        ConversionTaskStore.markSaving(taskIndex)
        updateNotification(localizedText(R.string.task_saving), (ConversionTaskStore.aggregateProgress() * 100).toInt())

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
                        displayName = nameGenerator(index, tempFiles.size),
                        outputProfile = outputProfile
                    )
                    outputUris.add(createdOutputUri)
                    copyFileToOutput(tempFile, createdOutputUri)
                    finalizeOutput(folder.isDefaultPublicDestination, createdOutputUri, outputProfile.mimeType)
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
                            ?: defaultOutputDirectoryUriFor(outputProfile),
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
                    failCurrentTask(localizedText(R.string.text_task_message_could_not_save_output_file))
                }
            }
        }.also { it.start() }
    }

    private fun failCurrentTask(message: LocalizedText) {
        val input = ConversionTaskStore.inputAt(taskIndex)
        Log.e(
            TAG,
            "Task failed index=$taskIndex message=$message " +
                "category=${input?.category} target=${input?.targetFormat} " +
                "displayName=${input?.displayName} mimeType=${input?.mimeType}"
        )
        activeFfmpegSession = null
        activeTempFile?.delete()
        activeTempFile = null
        ConversionTaskStore.markFailed(taskIndex, message)
        updateNotification(message, (ConversionTaskStore.aggregateProgress() * 100).toInt())
        taskIndex += 1
        handler.post { processNextTask() }
    }

    private fun finishRun() {
        activeFfmpegSession = null
        activeTempFile = null
        if (customOutputFallbackOccurred && ConversionTaskStore.tasks.none { it.status == ConversionTaskStatus.Failed }) {
            ConversionTaskStore.markRunFinished(localizedText(R.string.text_task_message_custom_output_folder_was_unavailable_saved_to_default_directory))
        } else {
            ConversionTaskStore.markRunFinished()
        }
        updateNotification(
            ConversionTaskStore.summaryMessage.value ?: localizedText(R.string.ui_flow_complete),
            (ConversionTaskStore.aggregateProgress() * 100).toInt()
        )
        detachForeground()
        stopSelf()
    }

    private fun cancelRun() {
        handler.removeCallbacksAndMessages(null)
        cancelActiveFfmpegSession()
        copyThread?.interrupt()
        activeTempFile?.delete()
        activeTempFile = null
        ConversionTaskStore.cancelAll()
        updateNotification(localizedText(R.string.ui_cancelled), (ConversionTaskStore.aggregateProgress() * 100).toInt())
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
    ): CreatedOutput {
        return when (val destination = input.outputDestination) {
            OutputDestination.DefaultPublicDirectory -> CreatedOutput(
                uri = createDefaultOutput(displayName, outputProfile),
                outputDirectoryUri = defaultOutputDirectoryUriFor(outputProfile),
                isDefaultPublicDestination = true
            )
            is OutputDestination.CustomDirectory -> {
                try {
                    val uri = createOutputDocument(
                        destination.uri,
                        displayName,
                        outputProfile.mimeType
                    )
                    CreatedOutput(
                        uri = uri,
                        outputDirectoryUri = documentUriForTree(destination.uri),
                        isDefaultPublicDestination = false
                    )
                } catch (throwable: Throwable) {
                    Log.w(TAG, "Custom output directory unavailable, falling back to default output", throwable)
                    handleCustomOutputDirectoryUnavailable()
                    CreatedOutput(
                        uri = createDefaultOutput(displayName, outputProfile),
                        outputDirectoryUri = defaultOutputDirectoryUriFor(outputProfile),
                        isDefaultPublicDestination = true
                    )
                }
            }
        }
    }

    private fun handleCustomOutputDirectoryUnavailable() {
        customOutputFallbackOccurred = true
        AppPreferences.clearOutputDirectory(this)
        AppPreferences.setUsesCustomOutput(this, false)
        ConversionTaskStore.showMessage(localizedText(R.string.text_task_message_custom_output_folder_was_unavailable_saved_to_default_directory))
    }

    private fun createOutputFolder(
        input: ConversionTaskInput,
        folderName: String,
        outputProfile: OutputProfile
    ): OutputFolder {
        return when (val destination = input.outputDestination) {
            OutputDestination.DefaultPublicDirectory -> {
                createDefaultOutputFolder(outputProfile, folderName)
            }
            is OutputDestination.CustomDirectory -> {
                try {
                    val folderUri = createOutputDirectoryDocument(destination.uri, folderName)
                    OutputFolder(
                        documentDirectoryUri = folderUri,
                        isDefaultPublicDestination = false
                    )
                } catch (throwable: Throwable) {
                    Log.w(TAG, "Custom output directory unavailable for folder export, falling back to default output", throwable)
                    handleCustomOutputDirectoryUnavailable()
                    createDefaultOutputFolder(outputProfile, folderName)
                }
            }
        }
    }

    private fun createDefaultOutputFolder(
        outputProfile: OutputProfile,
        folderName: String
    ): OutputFolder {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val publicDirectory = Environment.getExternalStoragePublicDirectory(
                defaultPublicDirectoryFor(outputProfile)
            )
            val folder = File(File(publicDirectory, DEFAULT_OUTPUT_DIRECTORY), folderName)
            if (!folder.exists() && !folder.mkdirs()) {
                throw LocalizedFailure(localizedText(R.string.message_could_not_create_output_directory))
            }
            OutputFolder(
                fileDirectory = folder,
                isDefaultPublicDestination = true
            )
        } else {
            OutputFolder(
                defaultRelativeSubdirectory = folderName,
                isDefaultPublicDestination = true
            )
        }
    }

    private fun defaultOutputDirectoryUriFor(outputProfile: OutputProfile): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            null
        } else {
            val publicDirectory = Environment.getExternalStoragePublicDirectory(
                defaultPublicDirectoryFor(outputProfile)
            )
            Uri.fromFile(File(publicDirectory, DEFAULT_OUTPUT_DIRECTORY))
        }
    }

    private fun outputDirectoryUriFor(
        input: ConversionTaskInput,
        outputProfile: OutputProfile
    ): Uri? {
        return when (val destination = input.outputDestination) {
            OutputDestination.DefaultPublicDirectory -> defaultOutputDirectoryUriFor(outputProfile)
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
            ) ?: throw LocalizedFailure(localizedText(R.string.message_could_not_create_output_file))
        }
        folder.defaultRelativeSubdirectory?.let { subdirectory ->
            return createDefaultOutput(displayName, outputProfile, subdirectory)
        }
        throw LocalizedFailure(localizedText(R.string.message_could_not_create_output_directory))
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
            ?: throw LocalizedFailure(localizedText(R.string.message_could_not_create_output_file))
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
            throw LocalizedFailure(localizedText(R.string.message_could_not_create_output_directory))
        }
        return Uri.fromFile(File(outputDirectory, displayName))
    }

    private fun defaultCollectionFor(outputProfile: OutputProfile): Uri {
        return when (outputProfile.kind) {
            OutputMediaKind.Audio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            OutputMediaKind.Image -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            OutputMediaKind.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            OutputMediaKind.Document -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            OutputMediaKind.Font -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            OutputMediaKind.Subtitle -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
    }

    private fun defaultPublicDirectoryFor(outputProfile: OutputProfile): String {
        return when (outputProfile.kind) {
            OutputMediaKind.Audio -> Environment.DIRECTORY_MUSIC
            OutputMediaKind.Document -> Environment.DIRECTORY_DOCUMENTS
            OutputMediaKind.Font -> Environment.DIRECTORY_DOCUMENTS
            OutputMediaKind.Subtitle -> Environment.DIRECTORY_DOCUMENTS
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
        ) ?: throw LocalizedFailure(localizedText(R.string.message_could_not_create_output_file))
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
        ) ?: throw LocalizedFailure(localizedText(R.string.message_could_not_create_output_directory))
    }

    private fun copyFileToOutput(source: File, outputUri: Uri) {
        if (outputUri.scheme == URI_SCHEME_FILE) {
            val path = outputUri.path ?: throw LocalizedFailure(localizedText(R.string.message_could_not_open_output_file))
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
        } ?: throw LocalizedFailure(localizedText(R.string.message_could_not_open_output_file))
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
        isDefaultPublicDestination: Boolean,
        outputUri: Uri,
        mimeType: String
    ) {
        if (!isDefaultPublicDestination) return

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
                    "jpg" -> OutputProfile(extension = "jpg", mimeType = MIME_TYPE_JPEG, kind = OutputMediaKind.Image)
                    "png" -> OutputProfile(extension = "png", mimeType = MIME_TYPE_PNG, kind = OutputMediaKind.Image)
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
                    "opus" -> OutputProfile(extension = "opus", mimeType = MIME_TYPE_OPUS, kind = OutputMediaKind.Audio)
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
                    input.targetFormat.equals("MD", ignoreCase = true) ->
                        OutputProfile(extension = "md", mimeType = MIME_TYPE_MARKDOWN, kind = OutputMediaKind.Document)
                    TargetId.fromKey(input.targetFormat) == TargetId.PdfEncrypt ||
                        TargetId.fromKey(input.targetFormat) == TargetId.PdfDecrypt ||
                        TargetId.fromKey(input.targetFormat) == TargetId.PdfCompress ->
                        OutputProfile(extension = "pdf", mimeType = MIME_TYPE_PDF, kind = OutputMediaKind.Document)
                    else -> null
                }
            }
            ConversionMediaCategory.Document -> {
                when {
                    input.targetFormat.equals("PDF", ignoreCase = true) ->
                        OutputProfile(extension = "pdf", mimeType = MIME_TYPE_PDF, kind = OutputMediaKind.Document)
                    input.targetFormat.equals("TXT", ignoreCase = true) ->
                        OutputProfile(extension = "txt", mimeType = MIME_TYPE_TEXT, kind = OutputMediaKind.Document)
                    input.targetFormat.equals("MD", ignoreCase = true) ->
                        OutputProfile(extension = "md", mimeType = MIME_TYPE_MARKDOWN, kind = OutputMediaKind.Document)
                    else -> null
                }
            }
            ConversionMediaCategory.Font -> {
                when {
                    input.targetFormat.equals("WOFF2", ignoreCase = true) ->
                        OutputProfile(extension = "woff2", mimeType = MIME_TYPE_WOFF2, kind = OutputMediaKind.Font)
                    input.targetFormat.equals("WOFF", ignoreCase = true) ->
                        OutputProfile(extension = "woff", mimeType = MIME_TYPE_WOFF, kind = OutputMediaKind.Font)
                    input.targetFormat.equals("TTF/OTF", ignoreCase = true) ->
                        OutputProfile(extension = "ttf", mimeType = MIME_TYPE_TTF, kind = OutputMediaKind.Font)
                    else -> null
                }
            }
            ConversionMediaCategory.Subtitle -> {
                when (input.targetFormat.uppercase(Locale.US)) {
                    "SRT" -> OutputProfile(extension = "srt", mimeType = MIME_TYPE_SRT, kind = OutputMediaKind.Subtitle)
                    "VTT" -> OutputProfile(extension = "vtt", mimeType = MIME_TYPE_VTT, kind = OutputMediaKind.Subtitle)
                    "ASS" -> OutputProfile(extension = "ass", mimeType = MIME_TYPE_ASS, kind = OutputMediaKind.Subtitle)
                    "LRC" -> OutputProfile(extension = "lrc", mimeType = MIME_TYPE_LRC, kind = OutputMediaKind.Subtitle)
                    else -> null
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
            normalized.contains("opus") -> "opus"
            else -> null
        }
    }

    private fun videoTargetExtensionFor(targetFormat: String): String? {
        val normalized = targetFormat.lowercase(Locale.US)
        return when {
            normalized == "contact_sheet_png" -> "png"
            normalized == "contact_sheet_jpg" -> "jpg"
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

    private data class CreatedOutput(
        val uri: Uri,
        val outputDirectoryUri: Uri?,
        val isDefaultPublicDestination: Boolean
    )

    private data class OutputFolder(
        val defaultRelativeSubdirectory: String? = null,
        val fileDirectory: File? = null,
        val documentDirectoryUri: Uri? = null,
        val isDefaultPublicDestination: Boolean = false
    )

    private enum class OutputMediaKind {
        Audio,
        Document,
        Font,
        Image,
        Subtitle,
        Video
    }

    private enum class TextDocumentFormat {
        PlainText,
        Markdown
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
        val message: LocalizedText? = null,
        val outputTail: String? = null,
        val segmentTempFiles: List<File> = emptyList()
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

    private data class FfmpegTrimSegment(
        val startSeconds: Double = 0.0,
        val durationLimitMs: Long? = null,
        val effectiveDurationMs: Long? = null
    )

    private data class FfmpegTrimWindow(
        val segments: List<FfmpegTrimSegment> = listOf(FfmpegTrimSegment()),
        val errorMessage: LocalizedText? = null
    ) {
        val startSeconds: Double get() = segments.firstOrNull()?.startSeconds ?: 0.0
        val durationLimitMs: Long? get() = segments.firstOrNull()?.durationLimitMs
        val effectiveDurationMs: Long? get() = segments.firstOrNull()?.effectiveDurationMs
        val isMultiSegment: Boolean get() = segments.size > 1
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

    private fun markdownTitleFor(input: ConversionTaskInput): String {
        return sanitizedBaseNameFor(input)
            .replace('_', ' ')
            .ifBlank { "ZenConverter" }
    }

    private fun outputNameFor(input: ConversionTaskInput, extension: String): String {
        val baseName = sanitizedBaseNameFor(input)
        if (input.inputUris.size > 1) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val shortId = input.fileId.take(8)
            return "${baseName}_merged_${timestamp}_$shortId.$extension"
        }
        if (isVideoContactSheetOutput(input)) {
            return "${baseName}_summary.$extension"
        }
        val inputExtension = input.extension.trim().lowercase(Locale.US)
        val targetExtension = extension.trim().lowercase(Locale.US)
        val isSameFormat = inputExtension.isNotEmpty() && inputExtension == targetExtension
        return if (isSameFormat) {
            "$baseName (1).$extension"
        } else {
            "$baseName.$extension"
        }
    }

    private fun outputNameForPage(
        input: ConversionTaskInput,
        extension: String,
        pageIndex: Int,
        pageCount: Int
    ): String {
        val baseName = sanitizedBaseNameFor(input)
        val width = pageCount.toString().length.coerceAtLeast(3)
        val pageNumber = (pageIndex + 1).toString().padStart(width, '0')
        return "${baseName}_page_${pageNumber}.$extension"
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

    private fun outputFolderNameForSplit(input: ConversionTaskInput): String {
        val baseName = sanitizedBaseNameFor(input)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val shortId = input.fileId.take(8)
        return "${baseName}_split_${timestamp}_$shortId"
    }

    private fun outputNameForPart(
        input: ConversionTaskInput,
        extension: String,
        partIndex: Int,
        partCount: Int
    ): String {
        val baseName = sanitizedBaseNameFor(input)
        val width = partCount.toString().length.coerceAtLeast(2)
        val partNumber = (partIndex + 1).toString().padStart(width, '0')
        return "${baseName}_part_${partNumber}.$extension"
    }

    private fun sanitizedBaseNameFor(input: ConversionTaskInput): String {
        return input.displayName
            .substringBeforeLast('.', input.displayName)
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
            .ifBlank { "ZenConverter" }
    }

    private fun updateNotification(title: LocalizedText, progress: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, progress.coerceIn(0, 100)))
    }

    private fun buildNotification(title: LocalizedText, progress: Int): Notification {
        notificationTitle = title
        notificationProgress = progress
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
            .setContentTitle(getString(R.string.app_name))
            .setContentText(title.resolve(this))
            .setContentIntent(openPendingIntent)
            .setOngoing(ConversionTaskStore.isRunning.value)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .addAction(R.drawable.ic_stat_zenconverter, localizedText(R.string.notification_cancel).resolve(this), cancelPendingIntent)
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
            localizedText(R.string.notification_channel_name).resolve(this),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = localizedText(R.string.notification_channel_description).resolve(this@ConversionService)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ConversionService"
        private const val CHANNEL_ID = "conversion_progress"
        private const val NOTIFICATION_ID = 1001
        private const val COPY_BUFFER_SIZE = 1024 * 1024
        private const val PROGRESS_BEFORE_SAVE = 0.98f
        private const val MAX_IMAGE_DECODE_PIXELS = 64_000_000L
        private const val SUPER_RESOLUTION_MIN_PIXELS = 64_000_000L
        private const val SUPER_RESOLUTION_MAX_PIXELS = 512_000_000L
        private const val SUPER_RESOLUTION_PIXELS_PER_GIB = 32_000_000L
        private const val PDF_IMAGE_MAX_LONG_SIDE_PIXELS = 4096
        private const val PDF_IMAGE_MAX_PIXELS = 16_000_000L
        private const val PDF_A4_SHORT_EDGE_PT = 595
        private const val PDF_A4_LONG_EDGE_PT = 842
        private const val PDF_POINTS_PER_INCH = 72f
        private const val PDF_PASSWORD_EXTENSION = 13
        private const val PDF_CACHE_HEADROOM_BYTES = 16L * 1024L * 1024L
        private const val PDF_UNKNOWN_CACHE_MIN_FREE_BYTES = 256L * 1024L * 1024L
        private const val OFFICE_MAX_INPUT_BYTES = 64L * 1024L * 1024L
        private const val FONT_MAX_INPUT_BYTES = 32L * 1024L * 1024L
        private const val SUBTITLE_MAX_INPUT_BYTES = 8L * 1024L * 1024L
        private const val SUBTITLE_INTERCHANGE_DIRECTORY = "subtitle-interchange"
        private const val FFMPEG_MAX_PROGRESS_BEFORE_SAVE = 0.98f
        private const val FFMPEG_LOG_DRAIN_TIMEOUT_MS = 1_000
        private const val FFMPEG_ENCODER_PROBE_TIMEOUT_MS = 2_000
        private const val FFMPEG_MEDIA_INFORMATION_PROBE_TIMEOUT_MS = 3_000
        private const val FFMPEG_LOG_TAIL_LINES = 16
        private const val FFMPEG_LOG_LINE_LIMIT = 600
        private const val FFMPEG_VIDEO_ENCODER_H264 = "libx264"
        private const val FFMPEG_VIDEO_ENCODER_H265 = "libx265"
        private const val FFMPEG_GIF_ENCODER = "gif"
        private const val FFMPEG_AAC_ENCODER = "aac"
        private const val FFMPEG_MP3_ENCODER = "libmp3lame"
        private const val FFMPEG_WAV_ENCODER = "pcm_s16le"
        private const val FFMPEG_FLAC_ENCODER = "flac"
        private const val FFMPEG_WMA_ENCODER = "wmav2"
        private const val FFMPEG_OPUS_ENCODER = "libopus"
        private val OPUS_SUPPORTED_SAMPLE_RATES = setOf(48_000, 24_000, 16_000, 12_000, 8_000)
        private const val FFMPEG_DEFAULT_CRF_H264 = "23"
        private const val FFMPEG_DEFAULT_CRF_H265 = "28"
        private const val FFMPEG_VISUAL_LOSSLESS_CRF_H264 = "18"
        private const val FFMPEG_VISUAL_LOSSLESS_CRF_H265 = "20"
        private const val FFMPEG_BALANCED_SHRINK_CRF_H264 = "21"
        private const val FFMPEG_BALANCED_SHRINK_CRF_H265 = "24"
        private const val FFMPEG_SMALL_FILE_CRF_H264 = "24"
        private const val FFMPEG_SMALL_FILE_CRF_H265 = "28"
        private const val FFMPEG_STANDARD_VIDEO_PRESET = "veryfast"
        private const val FFMPEG_PRESET_COMPRESSION_MEDIUM = "medium"
        private const val FFMPEG_VIDEO_REVERSE_MAX_DURATION_MS = 60_000L
        private const val FFMPEG_VIDEO_REVERSE_MAX_BUFFER_BYTES = 256L * 1024L * 1024L
        private const val FFMPEG_VIDEO_REVERSE_BYTES_PER_PIXEL = 4L
        private const val FFMPEG_VIDEO_REVERSE_DEFAULT_FPS_ESTIMATE = 60.0
        private const val FFMPEG_VIDEO_REVERSE_MAX_FPS_ESTIMATE = 120.0
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
        private const val MIME_TYPE_OPUS = "audio/opus"
        private const val MIME_TYPE_JPEG = "image/jpeg"
        private const val MIME_TYPE_PNG = "image/png"
        private const val MIME_TYPE_WEBP = "image/webp"
        private const val MIME_TYPE_GIF = "image/gif"
        private const val MIME_TYPE_ICO = "image/vnd.microsoft.icon"
        private const val MIME_TYPE_PDF = "application/pdf"
        private const val MIME_TYPE_TEXT = "text/plain"
        private const val MIME_TYPE_MARKDOWN = "text/markdown"
        private const val MIME_TYPE_TTF = "font/ttf"
        private const val MIME_TYPE_OTF = "font/otf"
        private const val MIME_TYPE_WOFF = "font/woff"
        private const val MIME_TYPE_WOFF2 = "font/woff2"
        private const val MIME_TYPE_SRT = "application/x-subrip"
        private const val MIME_TYPE_VTT = "text/vtt"
        private const val MIME_TYPE_ASS = "text/x-ssa"
        private const val MIME_TYPE_LRC = "text/x-lrc"
        private const val PDF_ENCRYPTION_KEY_LENGTH_BITS = 128
        private const val MIME_TYPE_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        private const val MIME_TYPE_PPTX =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        private const val MIME_TYPE_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        private const val DEFAULT_OUTPUT_DIRECTORY = "ZenConverter"
        private const val URI_SCHEME_FILE = "file"
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
