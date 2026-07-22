package org.zenconverter.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
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
import org.zenconverter.app.metadata.MetadataInspection
import org.zenconverter.app.metadata.MetadataMessageKey
import org.zenconverter.app.metadata.MetadataOperationException
import org.zenconverter.app.metadata.MetadataPrivacyManager
import org.zenconverter.app.metadata.MetadataStatusMessage
import org.zenconverter.app.metadata.MetadataTargetKind
import org.zenconverter.app.metadata.MetadataToolState
import org.zenconverter.app.ui.ExternalImportChoice
import org.zenconverter.app.ui.ExternalImportFile
import org.zenconverter.app.ui.ExternalImportPrompt
import org.zenconverter.app.ui.ExternalImportTarget
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val externalImportPrompt = mutableStateOf<ExternalImportPrompt?>(null)
    private val metadataToolState = mutableStateOf<MetadataToolState>(MetadataToolState.Empty)
    private val pendingPdfSelections = ArrayDeque<PendingPdfSelection>()
    private val pendingExternalImportGroups = ArrayDeque<PendingExternalImportGroup>()
    private var pendingImagePdfSelection: PendingImagePdfSelection? = null
    private var pendingGifFrameSelection: PendingGifFrameSelection? = null
    private var pendingGifPdfFrameSelection: PendingGifPdfFrameSelection? = null
    private var pendingPdfOutputPasswordSelection: PendingPdfOutputPasswordSelection? = null
    private var pendingMetadataTargetKind: MetadataTargetKind? = null
    private var pendingMetadataMediaWriteGrant: PendingMetadataMediaWriteGrant? = null
    private var pendingMetadataReadPermissionRetry: PendingMetadataMediaWriteGrant? = null
    private var pendingExternalImportItems: List<PendingExternalImportItem> = emptyList()
    private var activePdfPasswordSelection: PendingPdfSelection? = null
    private var pendingSelection: PendingSelection? = null
    private var pendingVideoOptions: VideoExportOptions = VideoExportOptions()
    private var pendingAudioOptions: AudioExportOptions = AudioExportOptions()
    private var pendingImageOptions: ImageExportOptions = ImageExportOptions()
    private var pendingPdfOptions: PdfExportOptions = PdfExportOptions()
    private var pdfProbeRunning = false
    private var pdfBoxReady = false
    private var pdfSelectionGeneration = 0
    private var externalImportGeneration = 0
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

    private val openMetadataDocument = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val kind = pendingMetadataTargetKind ?: return@registerForActivityResult
        pendingMetadataTargetKind = null
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult

        val canWrite = persistMetadataFilePermission(
            uri = uri,
            requestWrite = kind == MetadataTargetKind.Image
        )
        inspectMetadataSelection(uri, kind, canWrite)
    }

    private val requestMetadataMediaReadPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingMetadataReadPermissionRetry ?: return@registerForActivityResult
        pendingMetadataReadPermissionRetry = null
        val ready = metadataToolState.value as? MetadataToolState.Ready ?: return@registerForActivityResult
        if (ready.inspection.uri != pending.uri) return@registerForActivityResult
        if (granted) {
            when (pending.operation) {
                MetadataWriteOperation.Clean -> cleanSelectedMetadata(allowMediaWriteRequest = true)
                MetadataWriteOperation.Restore -> {
                    val backupId = pending.backupId ?: return@registerForActivityResult
                    restoreSelectedMetadata(
                        backupId = backupId,
                        allowMediaWriteRequest = true
                    )
                }
            }
        } else {
            metadataToolState.value = ready.copy(
                busy = false,
                message = MetadataStatusMessage(MetadataMessageKey.WritePermissionNeeded)
            )
        }
    }

    private val requestMetadataMediaWrite = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pending = pendingMetadataMediaWriteGrant ?: return@registerForActivityResult
        pendingMetadataMediaWriteGrant = null
        if (result.resultCode == Activity.RESULT_OK) {
            val ready = metadataToolState.value as? MetadataToolState.Ready ?: return@registerForActivityResult
            if (ready.inspection.uri != pending.uri) return@registerForActivityResult
            when (pending.operation) {
                MetadataWriteOperation.Clean -> cleanSelectedMetadata(allowMediaWriteRequest = false)
                MetadataWriteOperation.Restore -> {
                    val backupId = pending.backupId ?: return@registerForActivityResult
                    restoreSelectedMetadata(
                        backupId = backupId,
                        allowMediaWriteRequest = false
                    )
                }
            }
        } else {
            val ready = metadataToolState.value as? MetadataToolState.Ready ?: return@registerForActivityResult
            metadataToolState.value = ready.copy(
                busy = false,
                message = MetadataStatusMessage(MetadataMessageKey.WritePermissionNeeded)
            )
        }
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
                    pendingExternalImportItems = emptyList()
                    pendingExternalImportGroups.clear()
                    pdfSelectionGeneration += 1
                    externalImportGeneration += 1
                    imagePdfMergePrompt.value = null
                    gifFrameModePrompt.value = null
                    gifPdfFramePrompt.value = null
                    pdfPasswordPrompt.value = null
                    pdfOutputPasswordPrompt.value = null
                    externalImportPrompt.value = null
                    ConversionTaskStore.clear()
                },
                conversionTasks = ConversionTaskStore.tasks.map { it.toUiProgress() },
                conversionSummary = ConversionTaskStore.summaryMessage.value,
                isConversionRunning = ConversionTaskStore.isRunning.value,
                metadataToolState = metadataToolState.value,
                imagePdfMergePrompt = imagePdfMergePrompt.value,
                gifFrameModePrompt = gifFrameModePrompt.value,
                gifPdfFramePrompt = gifPdfFramePrompt.value,
                pdfPasswordPrompt = pdfPasswordPrompt.value,
                pdfOutputPasswordPrompt = pdfOutputPasswordPrompt.value,
                externalImportPrompt = externalImportPrompt.value,
                onPickMetadataImage = {
                    openMetadataPicker(MetadataTargetKind.Image)
                },
                onPickMetadataVideo = {
                    openMetadataPicker(MetadataTargetKind.Video)
                },
                onCleanMetadata = {
                    cleanSelectedMetadata()
                },
                onRestoreMetadata = { backupId ->
                    restoreSelectedMetadata(backupId)
                },
                onChooseSinglePdf = {
                    pendingImagePdfSelection?.let { selection ->
                        queuedFiles.add(selection.toSinglePdfQueuedFile())
                    }
                    pendingImagePdfSelection = null
                    imagePdfMergePrompt.value = null
                    processPendingExternalImportGroups()
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
                    processPendingExternalImportGroups()
                },
                onDismissImagePdfPrompt = {
                    pendingImagePdfSelection = null
                    imagePdfMergePrompt.value = null
                    processPendingExternalImportGroups()
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
                    processPendingExternalImportGroups()
                },
                onChooseGifSplitFrames = {
                    val selection = pendingGifFrameSelection
                    pendingGifFrameSelection = null
                    gifFrameModePrompt.value = null
                    if (selection != null) {
                        enqueueGifSplitDocuments(selection.request, selection.documents)
                    }
                    processPendingExternalImportGroups()
                },
                onDismissGifFramePrompt = {
                    pendingGifFrameSelection = null
                    gifFrameModePrompt.value = null
                    processPendingExternalImportGroups()
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
                    processPendingExternalImportGroups()
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
                    processPendingExternalImportGroups()
                },
                onDismissGifPdfFramePrompt = {
                    pendingGifPdfFrameSelection = null
                    gifPdfFramePrompt.value = null
                    processPendingExternalImportGroups()
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
                    processPendingExternalImportGroups()
                },
                onCancelPdfOutputPassword = {
                    pendingPdfOutputPasswordSelection = null
                    pdfOutputPasswordPrompt.value = null
                    ConversionTaskStore.showMessage("PDF encryption was skipped")
                    processPendingExternalImportGroups()
                },
                onConfirmExternalImport = { choices ->
                    enqueueExternalImportChoices(choices)
                },
                onDismissExternalImport = {
                    pendingExternalImportItems = emptyList()
                    externalImportPrompt.value = null
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
        handleExternalIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
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

    private fun handleExternalIntent(intent: Intent?) {
        if (intent == null || intent.action !in EXTERNAL_IMPORT_ACTIONS) return
        val externalUris = externalUrisFromIntent(intent)
        if (externalUris.isEmpty()) return

        val generation = ++externalImportGeneration
        pendingExternalImportGroups.clear()
        pendingExternalImportItems = emptyList()
        externalImportPrompt.value = null
        setIntent(Intent(Intent.ACTION_MAIN))

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                buildExternalImportItems(externalUris)
            }
            if (generation != externalImportGeneration) return@launch
            if (items.isEmpty()) {
                ConversionTaskStore.showMessage("No shared files were found")
                return@launch
            }
            pendingExternalImportItems = items
            externalImportPrompt.value = ExternalImportPrompt(
                files = items.map { it.promptFile }
            )
        }
    }

    private fun externalUrisFromIntent(intent: Intent): List<ExternalImportUri> {
        val results = linkedMapOf<String, ExternalImportUri>()

        fun addUri(uri: Uri?, typeHint: String?) {
            if (uri == null) return
            results.putIfAbsent(uri.toString(), ExternalImportUri(uri, typeHint))
        }

        when (intent.action) {
            Intent.ACTION_VIEW -> addUri(intent.data, intent.type)
            Intent.ACTION_SEND -> addUri(intent.streamUri(), intent.type)
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.streamUris().forEach { uri -> addUri(uri, intent.type) }
            }
        }
        intent.clipData?.let { clipData ->
            for (index in 0 until clipData.itemCount) {
                addUri(clipData.getItemAt(index).uri, intent.type)
            }
        }

        return results.values.toList()
    }

    private fun buildExternalImportItems(
        externalUris: List<ExternalImportUri>
    ): List<PendingExternalImportItem> {
        val prepared = externalUris.map { externalUri ->
            persistInputFilePermission(externalUri.uri)
            val document = selectedDocumentForExternalUri(externalUri)
            val extension = extensionFor(document.displayName)
            val category = detectExternalImportCategory(document.mimeType, extension)
            PreparedExternalImportDocument(
                document = document,
                extension = extension,
                detectedCategory = category
            )
        }
        val pdfCount = prepared.count { it.detectedCategory == FileCategory.Pdf }

        return prepared.map { candidate ->
            val targets = externalTargetsFor(candidate.detectedCategory, pdfCount)
            val defaultTarget = defaultExternalTargetFor(
                category = candidate.detectedCategory,
                extension = candidate.extension,
                targets = targets
            )
            val id = UUID.randomUUID().toString()
            PendingExternalImportItem(
                id = id,
                document = candidate.document,
                promptFile = ExternalImportFile(
                    id = id,
                    displayName = candidate.document.displayName,
                    sizeBytes = candidate.document.sizeBytes,
                    mimeType = candidate.document.mimeType,
                    inputInfo = candidate.document.inputInfo,
                    detectedCategory = candidate.detectedCategory,
                    targets = targets,
                    defaultTarget = defaultTarget
                )
            )
        }
    }

    private fun selectedDocumentForExternalUri(externalUri: ExternalImportUri): SelectedDocument {
        val metadata = queryOpenableMetadata(externalUri.uri)
        val mimeType = externalMimeTypeFor(externalUri)
        return SelectedDocument(
            uri = externalUri.uri,
            displayName = metadata.displayName,
            sizeBytes = metadata.sizeBytes,
            mimeType = mimeType,
            inputInfo = FileBasicInfoReader.read(
                context = this,
                uri = externalUri.uri,
                displayName = metadata.displayName,
                mimeType = mimeType,
                fallbackSizeBytes = metadata.sizeBytes
            )
        )
    }

    private fun externalMimeTypeFor(externalUri: ExternalImportUri): String? {
        val resolverMimeType = runCatching {
            contentResolver.getType(externalUri.uri)
        }.getOrNull()
        return listOf(resolverMimeType, externalUri.typeHint)
            .firstOrNull { type ->
                !type.isNullOrBlank() && type != MIME_TYPE_ANY
            }
    }

    private fun detectExternalImportCategory(
        mimeType: String?,
        extension: String
    ): FileCategory? {
        val normalizedMimeType = mimeType.orEmpty().lowercase(Locale.US)
        return when {
            normalizedMimeType.startsWith("video/") ||
                extension in VIDEO_INPUT_EXTENSIONS -> FileCategory.Video
            normalizedMimeType.startsWith("audio/") ||
                extension in AUDIO_INPUT_EXTENSIONS -> FileCategory.Audio
            normalizedMimeType == MIME_TYPE_PDF ||
                extension == "pdf" -> FileCategory.Pdf
            normalizedMimeType in OFFICE_MIME_TYPES ||
                extension in OFFICE_INPUT_EXTENSIONS -> FileCategory.Document
            normalizedMimeType in IMAGE_MIME_TYPES ||
                extension in IMAGE_INPUT_EXTENSIONS -> FileCategory.Image
            else -> null
        }
    }

    private fun externalTargetsFor(
        category: FileCategory?,
        pdfCount: Int
    ): List<ExternalImportTarget> {
        return when (category) {
            FileCategory.Video -> FileCategory.Video.formats.map {
                ExternalImportTarget(FileCategory.Video, it)
            } + FileCategory.Audio.formats.map {
                ExternalImportTarget(FileCategory.Audio, it)
            }
            FileCategory.Audio -> FileCategory.Audio.formats.map {
                ExternalImportTarget(FileCategory.Audio, it)
            }
            FileCategory.Image -> FileCategory.Image.formats.map {
                ExternalImportTarget(FileCategory.Image, it)
            }
            FileCategory.Pdf -> FileCategory.Pdf.formats
                .filter { pdfCount > 1 || !it.label.equals("PDF", ignoreCase = true) }
                .map { ExternalImportTarget(FileCategory.Pdf, it) }
            FileCategory.Document -> FileCategory.Document.formats.map {
                ExternalImportTarget(FileCategory.Document, it)
            }
            null -> emptyList()
        }
    }

    private fun defaultExternalTargetFor(
        category: FileCategory?,
        extension: String,
        targets: List<ExternalImportTarget>
    ): ExternalImportTarget? {
        val preferredLabel = when (category) {
            FileCategory.Video -> "MP4"
            FileCategory.Audio -> "M4A (AAC)"
            FileCategory.Image -> if (extension in JPEG_INPUT_EXTENSIONS) "PNG" else "JPG"
            FileCategory.Pdf -> "PNG"
            FileCategory.Document -> "PDF"
            null -> null
        }
        return targets.firstOrNull { target ->
            target.targetFormat.label.equals(preferredLabel, ignoreCase = true) &&
                target.category == category
        } ?: targets.firstOrNull()
    }

    private fun enqueueExternalImportChoices(choices: List<ExternalImportChoice>) {
        val pendingById = pendingExternalImportItems.associateBy { it.id }
        pendingExternalImportItems = emptyList()
        externalImportPrompt.value = null
        pendingExternalImportGroups.clear()

        val grouped = linkedMapOf<PendingSelection, MutableList<SelectedDocument>>()
        choices.forEach { choice ->
            val pendingItem = pendingById[choice.fileId] ?: return@forEach
            val request = PendingSelection(
                category = choice.target.category,
                targetFormat = choice.target.targetFormat
            )
            grouped.getOrPut(request) { mutableListOf() }.add(pendingItem.document)
        }

        if (grouped.isEmpty()) {
            ConversionTaskStore.showMessage("No external files were added")
            return
        }

        grouped.forEach { (request, documents) ->
            pendingExternalImportGroups.add(
                PendingExternalImportGroup(
                    request = request,
                    documents = documents
                )
            )
        }
        processPendingExternalImportGroups()
    }

    private fun processPendingExternalImportGroups() {
        if (hasBlockingPromptOrProbe()) return
        val group = pendingExternalImportGroups.poll() ?: return
        enqueuePickedDocuments(group.request, group.documents)
        if (!hasBlockingPromptOrProbe()) {
            processPendingExternalImportGroups()
        }
    }

    private fun hasBlockingPromptOrProbe(): Boolean {
        return externalImportPrompt.value != null ||
            imagePdfMergePrompt.value != null ||
            gifFrameModePrompt.value != null ||
            gifPdfFramePrompt.value != null ||
            pdfPasswordPrompt.value != null ||
            pdfOutputPasswordPrompt.value != null ||
            activePdfPasswordSelection != null ||
            pendingPdfSelections.isNotEmpty() ||
            pdfProbeRunning
    }

    private fun needsLegacyWritePermission(): Boolean {
        return outputLocationMode.value == OutputLocationMode.Default &&
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
    }

    private fun openMetadataPicker(kind: MetadataTargetKind) {
        pendingMetadataTargetKind = kind
        val mimeTypes = when (kind) {
            MetadataTargetKind.Image -> arrayOf(
                "image/jpeg",
                "image/jpg",
                "image/jfif",
                "image/png",
                "image/webp",
                "image/heic",
                "image/heif"
            )
            MetadataTargetKind.Video -> arrayOf("video/*")
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (kind == MetadataTargetKind.Image) "image/*" else "video/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            if (kind == MetadataTargetKind.Image) {
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        openMetadataDocument.launch(intent)
    }

    private fun inspectMetadataSelection(
        uri: Uri,
        kind: MetadataTargetKind,
        canWrite: Boolean,
        message: MetadataStatusMessage? = null
    ) {
        metadataToolState.value = MetadataToolState.Loading
        lifecycleScope.launch {
            val nextState = withContext(Dispatchers.IO) {
                runCatching {
                    val metadata = queryOpenableMetadata(uri)
                    val mimeType = contentResolver.getType(uri)
                    MetadataToolState.Ready(
                        inspection = MetadataPrivacyManager.inspect(
                            context = this@MainActivity,
                            uri = uri,
                            displayName = metadata.displayName,
                            sizeBytes = metadata.sizeBytes,
                            mimeType = mimeType,
                            kind = kind,
                            canWrite = canWrite
                        ),
                        message = message
                    )
                }.getOrElse { throwable ->
                    MetadataToolState.Error(metadataStatusFor(throwable, MetadataMessageKey.CouldNotRead))
                }
            }
            metadataToolState.value = nextState
        }
    }

    private fun cleanSelectedMetadata(allowMediaWriteRequest: Boolean = true) {
        val ready = metadataToolState.value as? MetadataToolState.Ready ?: return
        val inspection = ready.inspection
        metadataToolState.value = ready.copy(busy = true, message = null)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    MetadataPrivacyManager.requireJpegWriteAccess(this@MainActivity, inspection)
                    MetadataPrivacyManager.cleanJpegInPlace(this@MainActivity, inspection)
                }
            }
            result.onSuccess { message ->
                inspectMetadataSelection(
                    uri = inspection.uri,
                    kind = inspection.kind,
                    canWrite = inspection.canWrite,
                    message = message
                )
            }.onFailure { throwable ->
                if (
                    allowMediaWriteRequest &&
                    requestMetadataMediaWritePermission(
                        throwable = throwable,
                        inspection = inspection,
                        operation = MetadataWriteOperation.Clean
                    )
                ) {
                    metadataToolState.value = ready.copy(busy = false, message = null)
                    return@onFailure
                }
                metadataToolState.value = ready.copy(
                    busy = false,
                    message = metadataStatusFor(throwable, MetadataMessageKey.CouldNotWrite)
                )
            }
        }
    }

    private fun restoreSelectedMetadata(
        backupId: String,
        allowMediaWriteRequest: Boolean = true
    ) {
        val ready = metadataToolState.value as? MetadataToolState.Ready ?: return
        val inspection = ready.inspection
        metadataToolState.value = ready.copy(busy = true, message = null)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    MetadataPrivacyManager.requireJpegWriteAccess(this@MainActivity, inspection)
                    MetadataPrivacyManager.restoreJpegMetadataInPlace(
                        context = this@MainActivity,
                        inspection = inspection,
                        backupId = backupId
                    )
                }
            }
            result.onSuccess { message ->
                inspectMetadataSelection(
                    uri = inspection.uri,
                    kind = inspection.kind,
                    canWrite = inspection.canWrite,
                    message = message
                )
            }.onFailure { throwable ->
                if (
                    allowMediaWriteRequest &&
                    requestMetadataMediaWritePermission(
                        throwable = throwable,
                        inspection = inspection,
                        operation = MetadataWriteOperation.Restore,
                        backupId = backupId
                    )
                ) {
                    metadataToolState.value = ready.copy(busy = false, message = null)
                    return@onFailure
                }
                metadataToolState.value = ready.copy(
                    busy = false,
                    message = metadataStatusFor(throwable, MetadataMessageKey.CouldNotWrite)
                )
            }
        }
    }

    private fun requestMetadataMediaWritePermission(
        throwable: Throwable,
        inspection: MetadataInspection,
        operation: MetadataWriteOperation,
        backupId: String? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        if (!MetadataPrivacyManager.isMediaWritePermissionFailure(throwable)) return false
        val writeUri = MetadataPrivacyManager.mediaStoreUriForWriteRequest(this, inspection) ?: return false
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                MediaStore.createWriteRequest(contentResolver, listOf(writeUri))
            }.getOrElse { exception ->
                Log.w(TAG, "Could not create media write request for $writeUri", exception)
                return false
            }
        } else {
            MetadataPrivacyManager.recoverableSecurityExceptionFor(throwable)
                ?.userAction
                ?.actionIntent
                ?: run {
                    if (requestAndroid10MetadataReadPermission(inspection, operation, backupId)) {
                        return true
                    }
                    Log.w(TAG, "Media write failure was not recoverable on Android 10 for $writeUri", throwable)
                    return false
                }
        }
        pendingMetadataMediaWriteGrant = PendingMetadataMediaWriteGrant(
            uri = inspection.uri,
            operation = operation,
            backupId = backupId
        )
        requestMetadataMediaWrite.launch(
            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        )
        return true
    }

    private fun requestAndroid10MetadataReadPermission(
        inspection: MetadataInspection,
        operation: MetadataWriteOperation,
        backupId: String?
    ): Boolean {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) return false
        if (
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        pendingMetadataReadPermissionRetry = PendingMetadataMediaWriteGrant(
            uri = inspection.uri,
            operation = operation,
            backupId = backupId
        )
        requestMetadataMediaReadPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        return true
    }

    private fun persistMetadataFilePermission(
        uri: Uri,
        requestWrite: Boolean
    ): Boolean {
        val readFlag = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val writeFlag = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return if (requestWrite) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, readFlag or writeFlag)
                true
            }.getOrElse {
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, readFlag)
                }
                false
            }
        } else {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, readFlag)
            }
            false
        }
    }

    private fun metadataStatusFor(
        throwable: Throwable,
        fallback: MetadataMessageKey
    ): MetadataStatusMessage {
        return (throwable as? MetadataOperationException)?.status
            ?: MetadataStatusMessage(fallback)
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

        val fallbackName = uri.path
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "Selected file"

        return OpenableMetadata(
            displayName = displayName?.takeIf { it.isNotBlank() } ?: fallbackName,
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
        val selection = pendingPdfSelections.poll() ?: run {
            processPendingExternalImportGroups()
            return
        }
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

private data class ExternalImportUri(
    val uri: Uri,
    val typeHint: String?
)

private data class PreparedExternalImportDocument(
    val document: SelectedDocument,
    val extension: String,
    val detectedCategory: FileCategory?
)

private data class PendingExternalImportItem(
    val id: String,
    val document: SelectedDocument,
    val promptFile: ExternalImportFile
)

private data class PendingExternalImportGroup(
    val request: PendingSelection,
    val documents: List<SelectedDocument>
)

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

private enum class MetadataWriteOperation {
    Clean,
    Restore
}

private data class PendingMetadataMediaWriteGrant(
    val uri: Uri,
    val operation: MetadataWriteOperation,
    val backupId: String? = null
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

private fun extensionFor(displayName: String): String {
    return displayName.substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.length in 1..12 }
        ?.lowercase(Locale.US)
        .orEmpty()
}

private fun Intent.streamUri(): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    }
}

private fun Intent.streamUris(): List<Uri> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
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
private val EXTERNAL_IMPORT_ACTIONS = setOf(
    Intent.ACTION_VIEW,
    Intent.ACTION_SEND,
    Intent.ACTION_SEND_MULTIPLE
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
    "ts",
    "mts",
    "m2ts",
    "mpg",
    "mpeg",
    "vob",
    "flv",
    "ogv"
)
private val AUDIO_INPUT_EXTENSIONS = setOf(
    "mp3",
    "m4a",
    "aac",
    "wav",
    "flac",
    "wma",
    "ogg"
)
private val JPEG_INPUT_EXTENSIONS = setOf("jpg", "jpeg", "jfif", "jpe")
private val IMAGE_INPUT_EXTENSIONS = JPEG_INPUT_EXTENSIONS + setOf(
    "png",
    "webp",
    "gif",
    "heic",
    "heif",
    "ico"
)
private val IMAGE_MIME_TYPES = setOf(
    "image/jpeg",
    "image/jpg",
    "image/jfif",
    "image/png",
    "image/webp",
    "image/gif",
    "image/heic",
    "image/heif",
    "image/vnd.microsoft.icon",
    "image/x-icon",
    "image/ico"
)
private val OFFICE_INPUT_EXTENSIONS = setOf("docx", "pptx", "xlsx")
private val OFFICE_MIME_TYPES = setOf(
    MIME_TYPE_DOCX,
    MIME_TYPE_PPTX,
    MIME_TYPE_XLSX
)
private const val MIME_TYPE_ANY = "*/*"
private const val MIME_TYPE_PDF = "application/pdf"
private const val MIME_TYPE_DOCX =
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
private const val MIME_TYPE_PPTX =
    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
private const val MIME_TYPE_XLSX =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
