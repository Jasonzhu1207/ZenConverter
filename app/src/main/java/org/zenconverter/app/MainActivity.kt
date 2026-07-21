package org.zenconverter.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.zenconverter.app.conversion.AudioExportOptions
import org.zenconverter.app.conversion.ConversionMediaCategory
import org.zenconverter.app.conversion.ConversionService
import org.zenconverter.app.conversion.ConversionTaskInput
import org.zenconverter.app.conversion.ConversionTaskState
import org.zenconverter.app.conversion.ConversionTaskStatus
import org.zenconverter.app.conversion.ConversionTaskStore
import org.zenconverter.app.conversion.FileBasicInfo
import org.zenconverter.app.conversion.FileBasicInfoReader
import org.zenconverter.app.conversion.GifFrameExportMode
import org.zenconverter.app.conversion.ImageExportOptions
import org.zenconverter.app.conversion.OutputDestination
import org.zenconverter.app.conversion.PdfExportOptions
import org.zenconverter.app.conversion.PdfSecurityMode
import org.zenconverter.app.conversion.PdfSecurityOptions
import org.zenconverter.app.conversion.VideoExportOptions
import org.zenconverter.app.ui.FileCategory
import org.zenconverter.app.ui.GifFrameModePrompt
import org.zenconverter.app.ui.GifPdfFramePrompt
import org.zenconverter.app.ui.ImagePdfMergePrompt
import org.zenconverter.app.ui.PdfOutputPasswordPrompt
import org.zenconverter.app.ui.PdfPasswordPrompt
import org.zenconverter.app.ui.ZenConverterApp
import org.zenconverter.app.ui.OutputDirectory
import org.zenconverter.app.ui.OutputLocationMode
import org.zenconverter.app.ui.QueuedFile
import org.zenconverter.app.ui.TaskProgress
import org.zenconverter.app.ui.TaskProgressStatus
import org.zenconverter.app.ui.TargetFormat
import java.io.IOException
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val queuedFiles = mutableStateListOf<QueuedFile>()
    private val outputDirectory = mutableStateOf<OutputDirectory?>(null)
    private val outputLocationMode = mutableStateOf(OutputLocationMode.Default)
    private val imagePdfMergePrompt = mutableStateOf<ImagePdfMergePrompt?>(null)
    private val gifFrameModePrompt = mutableStateOf<GifFrameModePrompt?>(null)
    private val gifPdfFramePrompt = mutableStateOf<GifPdfFramePrompt?>(null)
    private val pdfPasswordPrompt = mutableStateOf<PdfPasswordPrompt?>(null)
    private val pdfOutputPasswordPrompt = mutableStateOf<PdfOutputPasswordPrompt?>(null)
    private val pendingPdfSelections = ArrayDeque<PendingPdfSelection>()
    private var pendingImagePdfSelection: PendingImagePdfSelection? = null
    private var pendingGifFrameSelection: PendingGifFrameSelection? = null
    private var pendingGifPdfFrameSelection: PendingGifPdfFrameSelection? = null
    private var pendingPdfOutputPasswordSelection: PendingPdfOutputPasswordSelection? = null
    private var activePdfPasswordSelection: PendingPdfSelection? = null
    private var pendingSelection: PendingSelection? = null
    private var pendingVideoOptions: VideoExportOptions = VideoExportOptions()
    private var pendingAudioOptions: AudioExportOptions = AudioExportOptions()
    private var pendingImageOptions: ImageExportOptions = ImageExportOptions()
    private var pendingPdfOptions: PdfExportOptions = PdfExportOptions()
    private var pdfProbeRunning = false
    private var pdfBoxReady = false
    private var pdfSelectionGeneration = 0
    private val supportedVideoMimeTypes = setOf(
        VideoExportOptions.VIDEO_MIME_TYPE_H264,
        VideoExportOptions.VIDEO_MIME_TYPE_H265
    )

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        requestLegacyWritePermissionThenStart()
    }

    private val requestLegacyWritePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startConversion()
        } else {
            ConversionTaskStore.showMessage("Default output needs storage permission on this Android version")
        }
    }

    private val openDocuments = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val request = pendingSelection ?: return@registerForActivityResult
        pendingSelection = null

        val documents = uris.map { uri ->
            persistInputFilePermission(uri)
            val metadata = queryOpenableMetadata(uri)
            val mimeType = contentResolver.getType(uri)
            SelectedDocument(
                uri = uri,
                displayName = metadata.displayName,
                sizeBytes = metadata.sizeBytes,
                mimeType = mimeType,
                inputInfo = FileBasicInfoReader.read(
                    context = this,
                    uri = uri,
                    displayName = metadata.displayName,
                    mimeType = mimeType,
                    fallbackSizeBytes = metadata.sizeBytes
                )
            )
        }

        enqueuePickedDocuments(request, documents)
    }

    private val openOutputDirectory = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        val persisted = persistOutputDirectoryPermission(uri)
        outputDirectory.value = OutputDirectory(
            uri = uri,
            label = describeTreeUri(uri),
            persistablePermissionSaved = persisted
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        setContent {
            ZenConverterApp(
                queuedFiles = queuedFiles,
                supportedVideoMimeTypes = supportedVideoMimeTypes,
                outputLocationMode = outputLocationMode.value,
                outputDirectory = outputDirectory.value,
                onOutputLocationModeChange = { mode ->
                    outputLocationMode.value = mode
                },
                onPickFiles = { category, targetFormat ->
                    pendingSelection = PendingSelection(category, targetFormat)
                    openDocuments.launch(category.mimeTypes.toTypedArray())
                },
                onPickOutputDirectory = {
                    openOutputDirectory.launch(null)
                },
                onRemoveFile = { fileId ->
                    queuedFiles.removeAll { it.id == fileId }
                },
                onClearQueue = {
                    queuedFiles.clear()
                    pendingPdfSelections.clear()
                    pendingImagePdfSelection = null
                    activePdfPasswordSelection = null
                    pendingGifFrameSelection = null
                    pendingGifPdfFrameSelection = null
                    pendingPdfOutputPasswordSelection = null
                    pdfSelectionGeneration += 1
                    imagePdfMergePrompt.value = null
                    gifFrameModePrompt.value = null
                    gifPdfFramePrompt.value = null
                    pdfPasswordPrompt.value = null
                    pdfOutputPasswordPrompt.value = null
                    ConversionTaskStore.clear()
                },
                conversionTasks = ConversionTaskStore.tasks.map { it.toUiProgress() },
                conversionSummary = ConversionTaskStore.summaryMessage.value,
                isConversionRunning = ConversionTaskStore.isRunning.value,
                imagePdfMergePrompt = imagePdfMergePrompt.value,
                gifFrameModePrompt = gifFrameModePrompt.value,
                gifPdfFramePrompt = gifPdfFramePrompt.value,
                pdfPasswordPrompt = pdfPasswordPrompt.value,
                pdfOutputPasswordPrompt = pdfOutputPasswordPrompt.value,
                onChooseSinglePdf = {
                    pendingImagePdfSelection?.let { selection ->
                        queuedFiles.add(selection.toSinglePdfQueuedFile())
                    }
                    pendingImagePdfSelection = null
                    imagePdfMergePrompt.value = null
                },
                onChooseOnePdfPerImage = {
                    pendingImagePdfSelection?.let { selection ->
                        if (selection.gifFrameMode == GifFrameExportMode.FramesAsSinglePdf) {
                            promptGifPdfFrameMode(selection.request, selection.documents)
                        } else {
                            enqueueDocumentsOnePerImage(
                                selection.request,
                                selection.documents,
                                GifFrameExportMode.FirstFrame
                            )
                        }
                    }
                    pendingImagePdfSelection = null
                    imagePdfMergePrompt.value = null
                },
                onDismissImagePdfPrompt = {
                    pendingImagePdfSelection = null
                    imagePdfMergePrompt.value = null
                },
                onChooseGifFirstFrame = {
                    val selection = pendingGifFrameSelection
                    pendingGifFrameSelection = null
                    gifFrameModePrompt.value = null
                    if (selection != null) {
                        enqueuePickedDocuments(
                            selection.request,
                            selection.documents,
                            GifFrameExportMode.FirstFrame,
                            allowGifPrompt = false
                        )
                    }
                },
                onChooseGifSplitFrames = {
                    val selection = pendingGifFrameSelection
                    pendingGifFrameSelection = null
                    gifFrameModePrompt.value = null
                    if (selection != null) {
                        enqueueGifSplitDocuments(selection.request, selection.documents)
                    }
                },
                onDismissGifFramePrompt = {
                    pendingGifFrameSelection = null
                    gifFrameModePrompt.value = null
                },
                onChooseGifFramesSinglePdf = {
                    val selection = pendingGifPdfFrameSelection
                    pendingGifPdfFrameSelection = null
                    gifPdfFramePrompt.value = null
                    if (selection != null) {
                        enqueueDocumentsOnePerImage(
                            selection.request,
                            selection.documents,
                            GifFrameExportMode.FramesAsSinglePdf
                        )
                    }
                },
                onChooseGifFramePdfFiles = {
                    val selection = pendingGifPdfFrameSelection
                    pendingGifPdfFrameSelection = null
                    gifPdfFramePrompt.value = null
                    if (selection != null) {
                        enqueueDocumentsOnePerImage(
                            selection.request,
                            selection.documents,
                            GifFrameExportMode.FramesAsPdfFiles
                        )
                    }
                },
                onDismissGifPdfFramePrompt = {
                    pendingGifPdfFrameSelection = null
                    gifPdfFramePrompt.value = null
                },
                onSubmitPdfPassword = { password ->
                    val prompt = activePdfPasswordSelection
                    pdfPasswordPrompt.value = null
                    activePdfPasswordSelection = null
                    if (prompt != null) {
                        retryPdfWithPassword(prompt, password)
                    } else {
                        processNextPendingPdfSelection()
                    }
                },
                onCancelPdfPassword = {
                    val selection = activePdfPasswordSelection
                    pdfPasswordPrompt.value = null
                    activePdfPasswordSelection = null
                    ConversionTaskStore.showMessage("Password-protected PDF was skipped")
                    if (selection != null) {
                        finishPendingPdfSelection(selection)
                    } else {
                        processNextPendingPdfSelection()
                    }
                },
                onSubmitPdfOutputPassword = { password ->
                    val selection = pendingPdfOutputPasswordSelection
                    pendingPdfOutputPasswordSelection = null
                    pdfOutputPasswordPrompt.value = null
                    if (selection != null) {
                        if (password.isBlank()) {
                            ConversionTaskStore.showMessage("PDF password was empty")
                        } else {
                            enqueuePdfDocumentsWithProbe(
                                request = selection.request.copy(
                                    pdfSecurityOptions = PdfSecurityOptions(
                                        mode = PdfSecurityMode.Encrypt,
                                        outputPassword = password
                                    )
                                ),
                                documents = selection.documents
                            )
                        }
                    }
                },
                onCancelPdfOutputPassword = {
                    pendingPdfOutputPasswordSelection = null
                    pdfOutputPasswordPrompt.value = null
                    ConversionTaskStore.showMessage("PDF encryption was skipped")
                },
                onStartConversion = { videoOptions, audioOptions, imageOptions, pdfOptions ->
                    pendingVideoOptions = videoOptions
                    pendingAudioOptions = audioOptions
                    pendingImageOptions = imageOptions
                    pendingPdfOptions = pdfOptions
                    requestNotificationPermissionThenStart()
                },
                onCancelConversion = {
                    ConversionService.cancel(this)
                }
            )
        }
    }

    private fun requestNotificationPermissionThenStart() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        requestLegacyWritePermissionThenStart()
    }

    private fun requestLegacyWritePermissionThenStart() {
        if (
            needsLegacyWritePermission() &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            requestLegacyWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        startConversion()
    }

    private fun startConversion() {
        if (queuedFiles.isEmpty()) return

        val outputDestination = when (outputLocationMode.value) {
            OutputLocationMode.Default -> OutputDestination.DefaultPublicDirectory
            OutputLocationMode.Custom -> {
                val outputUri = outputDirectory.value?.uri
                if (outputUri == null) {
                    ConversionTaskStore.showMessage("Choose output folder first")
                    return
                }
                OutputDestination.CustomDirectory(outputUri)
            }
        }

        if (needsLegacyWritePermission()) {
            ConversionTaskStore.showMessage("Default output needs storage permission on this Android version")
            return
        }

        if (queuedFiles.any { !it.hasConnectedNativeTarget() }) {
            ConversionTaskStore.showMessage("Only connected video, audio, image, PDF, and document targets can run")
            return
        }

        if (outputLocationMode.value == OutputLocationMode.Custom && outputDirectory.value == null) {
            ConversionTaskStore.showMessage("Choose output folder first")
            return
        }

        ConversionTaskStore.prepareRun(
            queuedFiles.map { file ->
                ConversionTaskInput(
                    fileId = file.id,
                    inputUri = file.uri,
                    inputUris = file.inputUris,
                    displayName = file.displayName,
                    mimeType = file.mimeType,
                    category = file.category.toConversionCategory(),
                    targetFormat = file.targetFormat,
                    outputDestination = outputDestination,
                    videoOptions = pendingVideoOptions,
                    audioOptions = pendingAudioOptions,
                    imageOptions = pendingImageOptions,
                    pdfOptions = pendingPdfOptions,
                    pdfSecurityOptions = file.pdfSecurityOptions,
                    inputInfo = file.inputInfo,
                    gifFrameMode = file.gifFrameMode,
                    pdfPasswords = file.pdfPasswords
                )
            }
        )
        ConversionService.start(this)
        clearQueuedPdfPasswords()
    }

    private fun needsLegacyWritePermission(): Boolean {
        return outputLocationMode.value == OutputLocationMode.Default &&
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
    }

    private fun queryOpenableMetadata(uri: Uri): OpenableMetadata {
        var displayName: String? = null
        var sizeBytes: Long? = null

        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex)
                }

                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    val rawSize = cursor.getLong(sizeIndex)
                    if (rawSize >= 0) sizeBytes = rawSize
                }
            }
        }

        return OpenableMetadata(
            displayName = displayName?.takeIf { it.isNotBlank() } ?: "Selected file",
            sizeBytes = sizeBytes
        )
    }

    private fun persistOutputDirectoryPermission(uri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        return runCatching {
            contentResolver.takePersistableUriPermission(uri, flags)
            true
        }.getOrDefault(false)
    }

    private fun persistInputFilePermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun enqueuePickedDocuments(
        request: PendingSelection,
        documents: List<SelectedDocument>,
        gifFrameMode: GifFrameExportMode = GifFrameExportMode.FirstFrame,
        allowGifPrompt: Boolean = true
    ) {
        if (documents.isEmpty()) return
        if (
            allowGifPrompt &&
            request.category == FileCategory.Image &&
            documents.any { it.isGifInput() }
        ) {
            pendingGifFrameSelection = PendingGifFrameSelection(request, documents)
            gifFrameModePrompt.value = GifFrameModePrompt(
                gifCount = documents.count { it.isGifInput() }
            )
            return
        }

        when {
            request.category == FileCategory.Image &&
                request.targetFormat.extension.equals("pdf", ignoreCase = true) &&
                documents.size > 1 -> {
                imagePdfMergePrompt.value = ImagePdfMergePrompt(fileCount = documents.size)
                pendingImagePdfSelection = PendingImagePdfSelection(
                    request = request,
                    documents = documents,
                    gifFrameMode = gifFrameMode
                )
            }
            request.category == FileCategory.Pdf -> {
                if (request.isPdfEncryptTarget()) {
                    pendingPdfOutputPasswordSelection = PendingPdfOutputPasswordSelection(
                        request = request,
                        documents = documents
                    )
                    pdfOutputPasswordPrompt.value = PdfOutputPasswordPrompt(fileCount = documents.size)
                } else {
                    enqueuePdfDocumentsWithProbe(
                        request = request.withPdfTargetSecurityOptions(),
                        documents = documents
                    )
                }
            }
            else -> {
                enqueueDocumentsOnePerImage(request, documents, gifFrameMode)
            }
        }
    }

    private fun enqueueGifSplitDocuments(
        request: PendingSelection,
        documents: List<SelectedDocument>
    ) {
        if (request.targetFormat.extension.equals("pdf", ignoreCase = true)) {
            if (documents.size > 1) {
                imagePdfMergePrompt.value = ImagePdfMergePrompt(fileCount = documents.size)
                pendingImagePdfSelection = PendingImagePdfSelection(
                    request = request,
                    documents = documents,
                    gifFrameMode = GifFrameExportMode.FramesAsSinglePdf
                )
            } else {
                promptGifPdfFrameMode(request, documents)
            }
            return
        }

        enqueueDocumentsOnePerImage(
            request = request,
            documents = documents,
            gifFrameMode = GifFrameExportMode.FramesAsImages
        )
    }

    private fun enqueueDocumentsOnePerImage(
        request: PendingSelection,
        documents: List<SelectedDocument>,
        gifFrameMode: GifFrameExportMode
    ) {
        queuedFiles.addAll(
            documents.map { document ->
                document.toQueuedFile(
                    request = request,
                    gifFrameMode = if (document.isGifInput()) {
                        gifFrameMode
                    } else {
                        GifFrameExportMode.FirstFrame
                    }
                )
            }
        )
    }

    private fun promptGifPdfFrameMode(
        request: PendingSelection,
        documents: List<SelectedDocument>
    ) {
        val gifCount = documents.count { it.isGifInput() }
        if (gifCount == 0) {
            enqueueDocumentsOnePerImage(request, documents, GifFrameExportMode.FirstFrame)
            return
        }
        pendingGifPdfFrameSelection = PendingGifPdfFrameSelection(request, documents)
        gifPdfFramePrompt.value = GifPdfFramePrompt(gifCount = gifCount)
    }

    private fun describeTreeUri(uri: Uri): String {
        val segment = uri.lastPathSegment ?: return "Selected folder"
        val label = segment.substringAfter(':', segment).ifBlank { "Device storage" }
        return label
    }

    private fun enqueuePdfDocumentsWithProbe(
        request: PendingSelection,
        documents: List<SelectedDocument>
    ) {
        if (request.isPdfMergeTarget() && documents.size < 2) {
            ConversionTaskStore.showMessage("Select at least two PDFs to merge")
            return
        }

        val batch = if (request.isPdfMergeTarget()) {
            PendingPdfBatch(
                request = request,
                documents = documents,
                generation = pdfSelectionGeneration
            )
        } else {
            null
        }
        pendingPdfSelections.addAll(
            documents.map { document ->
                PendingPdfSelection(
                    request = request,
                    document = document,
                    generation = pdfSelectionGeneration,
                    batch = batch
                )
            }
        )
        processNextPendingPdfSelection()
    }

    private fun processNextPendingPdfSelection() {
        if (pdfProbeRunning || pdfPasswordPrompt.value != null) return
        val selection = pendingPdfSelections.poll() ?: return
        if (selection.generation != pdfSelectionGeneration) {
            processNextPendingPdfSelection()
            return
        }
        pdfProbeRunning = true
        Thread {
            val result = probePdf(selection, password = null)
            runOnUiThread {
                pdfProbeRunning = false
                if (selection.generation != pdfSelectionGeneration) {
                    processNextPendingPdfSelection()
                    return@runOnUiThread
                }
                when (result) {
                    PdfProbeResult.Opened -> {
                        recordOpenedPdfSelection(selection, pdfPassword = null)
                        finishPendingPdfSelection(selection)
                    }
                    PdfProbeResult.PasswordRequired -> {
                        if (selection.request.usesPdfBoxTarget() || supportsPdfPassword()) {
                            activePdfPasswordSelection = selection
                            pdfPasswordPrompt.value = PdfPasswordPrompt(selection.document.displayName)
                        } else {
                            ConversionTaskStore.showMessage(
                                "Password-protected PDFs need Android 15 or PDF extension 13"
                            )
                            finishPendingPdfSelection(selection)
                        }
                    }
                    is PdfProbeResult.Failed -> {
                        ConversionTaskStore.showMessage(result.message)
                        finishPendingPdfSelection(selection)
                    }
                }
            }
        }.start()
    }

    private fun retryPdfWithPassword(selection: PendingPdfSelection, password: String) {
        if (password.isBlank()) {
            ConversionTaskStore.showMessage("PDF password was empty")
            finishPendingPdfSelection(selection)
            return
        }
        pdfProbeRunning = true
        Thread {
            val result = probePdf(selection, password)
            runOnUiThread {
                pdfProbeRunning = false
                if (selection.generation != pdfSelectionGeneration) {
                    processNextPendingPdfSelection()
                    return@runOnUiThread
                }
                when (result) {
                    PdfProbeResult.Opened -> {
                        recordOpenedPdfSelection(selection, pdfPassword = password)
                    }
                    PdfProbeResult.PasswordRequired ->
                        ConversionTaskStore.showMessage("PDF password was incorrect or unsupported")
                    is PdfProbeResult.Failed ->
                        ConversionTaskStore.showMessage(result.message)
                }
                finishPendingPdfSelection(selection)
            }
        }.start()
    }

    private fun recordOpenedPdfSelection(selection: PendingPdfSelection, pdfPassword: String?) {
        val batch = selection.batch
        if (batch == null) {
            queuedFiles.add(
                selection.document.toQueuedFile(
                    request = selection.request,
                    pdfPassword = pdfPassword
                )
            )
            return
        }

        batch.openedDocuments.add(selection.document)
        batch.pdfPasswords.add(pdfPassword)
    }

    private fun finishPendingPdfSelection(selection: PendingPdfSelection) {
        selection.batch?.let { batch ->
            batch.processedCount += 1
            if (
                batch.processedCount == batch.documents.size &&
                batch.generation == pdfSelectionGeneration
            ) {
                if (batch.openedDocuments.size >= 2) {
                    queuedFiles.add(batch.toMergedPdfQueuedFile())
                } else {
                    ConversionTaskStore.showMessage("Select at least two PDFs to merge")
                }
            }
        }
        processNextPendingPdfSelection()
    }

    private fun probePdf(selection: PendingPdfSelection, password: String?): PdfProbeResult {
        if (selection.request.usesPdfBoxTarget()) {
            return probePdfWithPdfBox(selection.document.uri, password)
        }
        return probePdfWithRenderer(selection.document.uri, password)
    }

    private fun probePdfWithRenderer(uri: Uri, password: String?): PdfProbeResult {
        return try {
            val renderer = openPdfRenderer(uri, password)
            renderer.close()
            PdfProbeResult.Opened
        } catch (exception: SecurityException) {
            PdfProbeResult.PasswordRequired
        } catch (exception: IllegalArgumentException) {
            PdfProbeResult.Opened
        } catch (exception: IOException) {
            PdfProbeResult.Failed("Could not open this PDF")
        } catch (exception: Throwable) {
            Log.w(TAG, "PDF probe failed", exception)
            PdfProbeResult.Failed("Could not read this PDF")
        }
    }

    private fun probePdfWithPdfBox(uri: Uri, password: String?): PdfProbeResult {
        return try {
            ensurePdfBoxReady()
            val memoryUsage = MemoryUsageSetting.setupTempFileOnly()
            contentResolver.openInputStream(uri)?.use { input ->
                val document = if (password == null) {
                    PDDocument.load(input, memoryUsage)
                } else {
                    PDDocument.load(input, password, memoryUsage)
                }
                document.use {
                    if (document.numberOfPages <= 0) {
                        PdfProbeResult.Failed("PDF has no pages")
                    } else {
                        PdfProbeResult.Opened
                    }
                }
            } ?: PdfProbeResult.Failed("Could not open this PDF")
        } catch (exception: InvalidPasswordException) {
            PdfProbeResult.PasswordRequired
        } catch (exception: IOException) {
            PdfProbeResult.Failed("Could not open this PDF")
        } catch (exception: Throwable) {
            Log.w(TAG, "PDFBox probe failed", exception)
            PdfProbeResult.Failed("Could not read this PDF")
        }
    }

    private fun openPdfRenderer(uri: Uri, password: String?): PdfRenderer {
        val descriptor = contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("Could not open PDF")
        return try {
            if (password != null && supportsPdfPassword()) {
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

    private fun clearQueuedPdfPasswords() {
        for (index in queuedFiles.indices) {
            val file = queuedFiles[index]
            if (
                file.pdfPasswords.any { it != null } ||
                file.pdfSecurityOptions.outputPassword != null
            ) {
                queuedFiles[index] = file.copy(
                    pdfPasswords = emptyList(),
                    pdfSecurityOptions = file.pdfSecurityOptions.copy(outputPassword = null)
                )
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PDF_PASSWORD_EXTENSION = 13
    }
}

private data class PendingSelection(
    val category: FileCategory,
    val targetFormat: TargetFormat,
    val pdfSecurityOptions: PdfSecurityOptions = PdfSecurityOptions()
)

private data class SelectedDocument(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String?,
    val inputInfo: FileBasicInfo?
)

private data class PendingImagePdfSelection(
    val request: PendingSelection,
    val documents: List<SelectedDocument>,
    val gifFrameMode: GifFrameExportMode = GifFrameExportMode.FirstFrame
)

private data class PendingGifFrameSelection(
    val request: PendingSelection,
    val documents: List<SelectedDocument>
)

private data class PendingGifPdfFrameSelection(
    val request: PendingSelection,
    val documents: List<SelectedDocument>
)

private data class PendingPdfOutputPasswordSelection(
    val request: PendingSelection,
    val documents: List<SelectedDocument>
)

private data class PendingPdfSelection(
    val request: PendingSelection,
    val document: SelectedDocument,
    val generation: Int,
    val batch: PendingPdfBatch? = null
)

private data class PendingPdfBatch(
    val request: PendingSelection,
    val documents: List<SelectedDocument>,
    val generation: Int,
    val openedDocuments: MutableList<SelectedDocument> = mutableListOf(),
    val pdfPasswords: MutableList<String?> = mutableListOf(),
    var processedCount: Int = 0
)

private data class OpenableMetadata(
    val displayName: String,
    val sizeBytes: Long?
)

private sealed interface PdfProbeResult {
    object Opened : PdfProbeResult
    object PasswordRequired : PdfProbeResult
    data class Failed(val message: String) : PdfProbeResult
}

private fun SelectedDocument.toQueuedFile(
    request: PendingSelection,
    gifFrameMode: GifFrameExportMode = GifFrameExportMode.FirstFrame,
    pdfPassword: String? = null
): QueuedFile {
    return QueuedFile(
        id = UUID.randomUUID().toString(),
        uri = uri,
        inputUris = listOf(uri),
        displayName = displayName,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        category = request.category,
        targetFormat = request.targetFormat.label,
        inputInfo = inputInfo,
        gifFrameMode = gifFrameMode,
        pdfSecurityOptions = request.pdfSecurityOptions,
        pdfPasswords = listOf(pdfPassword)
    )
}

private fun PendingPdfBatch.toMergedPdfQueuedFile(): QueuedFile {
    val first = openedDocuments.first()
    val totalSize = openedDocuments.mapNotNull { it.sizeBytes }
        .takeIf { it.size == openedDocuments.size }
        ?.sum()
    return QueuedFile(
        id = UUID.randomUUID().toString(),
        uri = first.uri,
        inputUris = openedDocuments.map { it.uri },
        displayName = "${openedDocuments.size} PDFs",
        sizeBytes = totalSize,
        mimeType = MIME_TYPE_PDF,
        category = request.category,
        targetFormat = request.targetFormat.label,
        inputInfo = FileBasicInfoReader.aggregate(
            documents = openedDocuments.map { it.inputInfo },
            fallbackSizeBytes = totalSize,
            formatLabel = "PDF"
        ),
        pdfSecurityOptions = request.pdfSecurityOptions,
        pdfPasswords = pdfPasswords.toList()
    )
}

private fun PendingImagePdfSelection.toSinglePdfQueuedFile(): QueuedFile {
    val first = documents.first()
    val totalSize = documents.mapNotNull { it.sizeBytes }.takeIf { it.size == documents.size }
        ?.sum()
    return QueuedFile(
        id = UUID.randomUUID().toString(),
        uri = first.uri,
        inputUris = documents.map { it.uri },
        displayName = "${documents.size} images",
        sizeBytes = totalSize,
        mimeType = "image/*",
        category = request.category,
        targetFormat = request.targetFormat.label,
        inputInfo = FileBasicInfoReader.aggregate(
            documents = documents.map { it.inputInfo },
            fallbackSizeBytes = totalSize
        ),
        gifFrameMode = gifFrameMode
    )
}

private fun SelectedDocument.isGifInput(): Boolean {
    val normalizedMimeType = mimeType.orEmpty().lowercase(Locale.US)
    return normalizedMimeType == "image/gif" ||
        displayName.lowercase(Locale.US).endsWith(".gif")
}

private fun QueuedFile.hasConnectedNativeTarget(): Boolean {
    return when (category) {
        FileCategory.Video -> targetFormat.equals("MP4", ignoreCase = true) ||
            targetFormat.equals("MKV", ignoreCase = true) ||
            targetFormat.equals("MOV", ignoreCase = true) ||
            targetFormat.equals("GIF", ignoreCase = true)
        FileCategory.Audio -> audioTargetExtensionFor(targetFormat) in CONNECTED_AUDIO_TARGETS
        FileCategory.Image -> targetFormat.equals("JPG", ignoreCase = true) ||
            targetFormat.equals("JFIF", ignoreCase = true) ||
            targetFormat.equals("PNG", ignoreCase = true) ||
            targetFormat.equals("WEBP", ignoreCase = true) ||
            targetFormat.equals("ICO", ignoreCase = true) ||
            targetFormat.equals("PDF", ignoreCase = true)
        FileCategory.Pdf -> targetFormat.equals("JPG", ignoreCase = true) ||
            targetFormat.equals("PNG", ignoreCase = true) ||
            targetFormat.equals("WEBP", ignoreCase = true) ||
            targetFormat.equals("PDF", ignoreCase = true) ||
            targetFormat.equals("TXT", ignoreCase = true) ||
            targetFormat.equals("MD", ignoreCase = true) ||
            targetFormat.equals("Encrypt PDF", ignoreCase = true) ||
            targetFormat.equals("Decrypt PDF", ignoreCase = true)
        FileCategory.Document -> targetFormat.equals("PDF", ignoreCase = true) ||
            targetFormat.equals("TXT", ignoreCase = true) ||
            targetFormat.equals("MD", ignoreCase = true)
    }
}

private fun PendingSelection.isPdfMergeTarget(): Boolean {
    return category == FileCategory.Pdf &&
        targetFormat.label.equals("PDF", ignoreCase = true)
}

private fun PendingSelection.isPdfEncryptTarget(): Boolean {
    return category == FileCategory.Pdf &&
        targetFormat.label.equals("Encrypt PDF", ignoreCase = true)
}

private fun PendingSelection.isPdfDecryptTarget(): Boolean {
    return category == FileCategory.Pdf &&
        targetFormat.label.equals("Decrypt PDF", ignoreCase = true)
}

private fun PendingSelection.withPdfTargetSecurityOptions(): PendingSelection {
    if (pdfSecurityOptions.mode != PdfSecurityMode.None) return this
    return if (isPdfDecryptTarget()) {
        copy(pdfSecurityOptions = PdfSecurityOptions(mode = PdfSecurityMode.Decrypt))
    } else {
        this
    }
}

private fun PendingSelection.usesPdfBoxTarget(): Boolean {
    return category == FileCategory.Pdf &&
        (
            isPdfMergeTarget() ||
                isPdfEncryptTarget() ||
                isPdfDecryptTarget() ||
                pdfSecurityOptions.mode != PdfSecurityMode.None ||
                targetFormat.extension.equals("txt", ignoreCase = true) ||
                targetFormat.extension.equals("md", ignoreCase = true)
            )
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

private fun FileCategory.toConversionCategory(): ConversionMediaCategory {
    return when (this) {
        FileCategory.Video -> ConversionMediaCategory.Video
        FileCategory.Audio -> ConversionMediaCategory.Audio
        FileCategory.Image -> ConversionMediaCategory.Image
        FileCategory.Pdf -> ConversionMediaCategory.Pdf
        FileCategory.Document -> ConversionMediaCategory.Document
    }
}

private fun ConversionTaskState.toUiProgress(): TaskProgress {
    return TaskProgress(
        fileId = fileId,
        status = when (status) {
            ConversionTaskStatus.Queued -> TaskProgressStatus.Queued
            ConversionTaskStatus.Running -> TaskProgressStatus.Running
            ConversionTaskStatus.Completed -> TaskProgressStatus.Completed
            ConversionTaskStatus.Cancelled -> TaskProgressStatus.Cancelled
            ConversionTaskStatus.Failed -> TaskProgressStatus.Failed
        },
        progress = progress,
        message = message,
        outputUri = outputUri,
        outputUris = outputUris,
        outputDirectoryUri = outputDirectoryUri,
        outputMimeType = outputMimeType,
        outputInfo = outputInfo
    )
}

private val CONNECTED_AUDIO_TARGETS = setOf("mp3", "m4a", "wav", "flac", "wma")
private const val MIME_TYPE_PDF = "application/pdf"
