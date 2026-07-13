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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import org.zenconverter.app.conversion.AudioExportOptions
import org.zenconverter.app.conversion.ConversionMediaCategory
import org.zenconverter.app.conversion.ConversionService
import org.zenconverter.app.conversion.ConversionTaskInput
import org.zenconverter.app.conversion.ConversionTaskState
import org.zenconverter.app.conversion.ConversionTaskStatus
import org.zenconverter.app.conversion.ConversionTaskStore
import org.zenconverter.app.conversion.ImageExportOptions
import org.zenconverter.app.conversion.OutputDestination
import org.zenconverter.app.conversion.PdfExportOptions
import org.zenconverter.app.conversion.VideoExportOptions
import org.zenconverter.app.conversion.VideoEncoderSupport
import org.zenconverter.app.ui.FileCategory
import org.zenconverter.app.ui.ImagePdfMergePrompt
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
    private val pdfPasswordPrompt = mutableStateOf<PdfPasswordPrompt?>(null)
    private val pendingPdfSelections = ArrayDeque<PendingPdfSelection>()
    private var pendingImagePdfSelection: PendingImagePdfSelection? = null
    private var activePdfPasswordSelection: PendingPdfSelection? = null
    private var pendingSelection: PendingSelection? = null
    private var pendingVideoOptions: VideoExportOptions = VideoExportOptions()
    private var pendingAudioOptions: AudioExportOptions = AudioExportOptions()
    private var pendingImageOptions: ImageExportOptions = ImageExportOptions()
    private var pendingPdfOptions: PdfExportOptions = PdfExportOptions()
    private var pdfProbeRunning = false
    private var pdfSelectionGeneration = 0
    private val supportedVideoMimeTypes by lazy { VideoEncoderSupport.supportedMimeTypes() }

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
            SelectedDocument(
                uri = uri,
                displayName = metadata.displayName,
                sizeBytes = metadata.sizeBytes,
                mimeType = contentResolver.getType(uri)
            )
        }

        when {
            request.category == FileCategory.Image &&
                request.targetFormat.extension.equals("pdf", ignoreCase = true) &&
                documents.size > 1 -> {
                imagePdfMergePrompt.value = ImagePdfMergePrompt(fileCount = documents.size)
                pendingImagePdfSelection = PendingImagePdfSelection(request, documents)
            }
            request.category == FileCategory.Pdf -> {
                enqueuePdfDocumentsWithProbe(request, documents)
            }
            else -> {
                queuedFiles.addAll(documents.map { it.toQueuedFile(request) })
            }
        }
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
                    pdfSelectionGeneration += 1
                    imagePdfMergePrompt.value = null
                    pdfPasswordPrompt.value = null
                    ConversionTaskStore.clear()
                },
                conversionTasks = ConversionTaskStore.tasks.map { it.toUiProgress() },
                conversionSummary = ConversionTaskStore.summaryMessage.value,
                isConversionRunning = ConversionTaskStore.isRunning.value,
                imagePdfMergePrompt = imagePdfMergePrompt.value,
                pdfPasswordPrompt = pdfPasswordPrompt.value,
                onChooseSinglePdf = {
                    pendingImagePdfSelection?.let { selection ->
                        queuedFiles.add(selection.toSinglePdfQueuedFile())
                    }
                    pendingImagePdfSelection = null
                    imagePdfMergePrompt.value = null
                },
                onChooseOnePdfPerImage = {
                    pendingImagePdfSelection?.let { selection ->
                        queuedFiles.addAll(
                            selection.documents.map { document ->
                                document.toQueuedFile(selection.request)
                            }
                        )
                    }
                    pendingImagePdfSelection = null
                    imagePdfMergePrompt.value = null
                },
                onDismissImagePdfPrompt = {
                    pendingImagePdfSelection = null
                    imagePdfMergePrompt.value = null
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
                    pdfPasswordPrompt.value = null
                    activePdfPasswordSelection = null
                    ConversionTaskStore.showMessage("Password-protected PDF was skipped")
                    processNextPendingPdfSelection()
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
            ConversionTaskStore.showMessage("Only connected video, audio, image, and PDF targets can run")
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
                    pdfPassword = file.pdfPassword
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

    private fun describeTreeUri(uri: Uri): String {
        val segment = uri.lastPathSegment ?: return "Selected folder"
        val label = segment.substringAfter(':', segment).ifBlank { "Device storage" }
        return label
    }

    private fun enqueuePdfDocumentsWithProbe(
        request: PendingSelection,
        documents: List<SelectedDocument>
    ) {
        pendingPdfSelections.addAll(
            documents.map { PendingPdfSelection(request, it, pdfSelectionGeneration) }
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
            val result = probePdf(selection.document.uri, password = null)
            runOnUiThread {
                pdfProbeRunning = false
                if (selection.generation != pdfSelectionGeneration) {
                    processNextPendingPdfSelection()
                    return@runOnUiThread
                }
                when (result) {
                    PdfProbeResult.Opened -> {
                        queuedFiles.add(selection.document.toQueuedFile(selection.request))
                        processNextPendingPdfSelection()
                    }
                    PdfProbeResult.PasswordRequired -> {
                        if (supportsPdfPassword()) {
                            activePdfPasswordSelection = selection
                            pdfPasswordPrompt.value = PdfPasswordPrompt(selection.document.displayName)
                        } else {
                            ConversionTaskStore.showMessage(
                                "Password-protected PDFs need Android 15 or PDF extension 13"
                            )
                            processNextPendingPdfSelection()
                        }
                    }
                    is PdfProbeResult.Failed -> {
                        ConversionTaskStore.showMessage(result.message)
                        processNextPendingPdfSelection()
                    }
                }
            }
        }.start()
    }

    private fun retryPdfWithPassword(selection: PendingPdfSelection, password: String) {
        if (password.isBlank()) {
            ConversionTaskStore.showMessage("PDF password was empty")
            processNextPendingPdfSelection()
            return
        }
        pdfProbeRunning = true
        Thread {
            val result = probePdf(selection.document.uri, password)
            runOnUiThread {
                pdfProbeRunning = false
                if (selection.generation != pdfSelectionGeneration) {
                    processNextPendingPdfSelection()
                    return@runOnUiThread
                }
                when (result) {
                    PdfProbeResult.Opened -> {
                        queuedFiles.add(
                            selection.document.toQueuedFile(
                                request = selection.request,
                                pdfPassword = password
                            )
                        )
                    }
                    PdfProbeResult.PasswordRequired ->
                        ConversionTaskStore.showMessage("PDF password was incorrect or unsupported")
                    is PdfProbeResult.Failed ->
                        ConversionTaskStore.showMessage(result.message)
                }
                processNextPendingPdfSelection()
            }
        }.start()
    }

    private fun probePdf(uri: Uri, password: String?): PdfProbeResult {
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

    private fun clearQueuedPdfPasswords() {
        for (index in queuedFiles.indices) {
            val file = queuedFiles[index]
            if (file.pdfPassword != null) {
                queuedFiles[index] = file.copy(pdfPassword = null)
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
    val targetFormat: TargetFormat
)

private data class SelectedDocument(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String?
)

private data class PendingImagePdfSelection(
    val request: PendingSelection,
    val documents: List<SelectedDocument>
)

private data class PendingPdfSelection(
    val request: PendingSelection,
    val document: SelectedDocument,
    val generation: Int
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
        pdfPassword = pdfPassword
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
        targetFormat = request.targetFormat.label
    )
}

private fun QueuedFile.hasConnectedNativeTarget(): Boolean {
    return when (category) {
        FileCategory.Video -> targetFormat.equals("MP4", ignoreCase = true)
        FileCategory.Audio -> audioTargetExtensionFor(targetFormat) in CONNECTED_AUDIO_TARGETS
        FileCategory.Image -> targetFormat.equals("JPG", ignoreCase = true) ||
            targetFormat.equals("PNG", ignoreCase = true) ||
            targetFormat.equals("WEBP", ignoreCase = true) ||
            targetFormat.equals("PDF", ignoreCase = true)
        FileCategory.Pdf -> targetFormat.equals("JPG", ignoreCase = true) ||
            targetFormat.equals("PNG", ignoreCase = true) ||
            targetFormat.equals("WEBP", ignoreCase = true)
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

private fun FileCategory.toConversionCategory(): ConversionMediaCategory {
    return when (this) {
        FileCategory.Video -> ConversionMediaCategory.Video
        FileCategory.Audio -> ConversionMediaCategory.Audio
        FileCategory.Image -> ConversionMediaCategory.Image
        FileCategory.Pdf -> ConversionMediaCategory.Pdf
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
        message = message
    )
}

private val CONNECTED_AUDIO_TARGETS = setOf("mp3", "m4a", "wav", "flac", "wma")
