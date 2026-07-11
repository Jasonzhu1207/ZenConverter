package org.zenconverter.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
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
import org.zenconverter.app.conversion.VideoExportOptions
import org.zenconverter.app.conversion.VideoEncoderSupport
import org.zenconverter.app.ui.FileCategory
import org.zenconverter.app.ui.ZenConverterApp
import org.zenconverter.app.ui.OutputDirectory
import org.zenconverter.app.ui.OutputLocationMode
import org.zenconverter.app.ui.QueuedFile
import org.zenconverter.app.ui.TaskProgress
import org.zenconverter.app.ui.TaskProgressStatus
import org.zenconverter.app.ui.TargetFormat
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val queuedFiles = mutableStateListOf<QueuedFile>()
    private val outputDirectory = mutableStateOf<OutputDirectory?>(null)
    private val outputLocationMode = mutableStateOf(OutputLocationMode.Default)
    private var pendingSelection: PendingSelection? = null
    private var pendingVideoOptions: VideoExportOptions = VideoExportOptions()
    private var pendingAudioOptions: AudioExportOptions = AudioExportOptions()
    private var pendingImageOptions: ImageExportOptions = ImageExportOptions()
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

        val nextFiles = uris.map { uri ->
            persistInputFilePermission(uri)
            val metadata = queryOpenableMetadata(uri)
            QueuedFile(
                id = UUID.randomUUID().toString(),
                uri = uri,
                displayName = metadata.displayName,
                sizeBytes = metadata.sizeBytes,
                mimeType = contentResolver.getType(uri),
                category = request.category,
                targetFormat = request.targetFormat.label
            )
        }
        queuedFiles.addAll(nextFiles)
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
                    ConversionTaskStore.clear()
                },
                conversionTasks = ConversionTaskStore.tasks.map { it.toUiProgress() },
                conversionSummary = ConversionTaskStore.summaryMessage.value,
                isConversionRunning = ConversionTaskStore.isRunning.value,
                onStartConversion = { videoOptions, audioOptions, imageOptions ->
                    pendingVideoOptions = videoOptions
                    pendingAudioOptions = audioOptions
                    pendingImageOptions = imageOptions
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
            ConversionTaskStore.showMessage("Only video MP4, audio MP3/M4A/WAV/FLAC/WMA, and JPG/PNG/WEBP images are connected")
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
                    displayName = file.displayName,
                    mimeType = file.mimeType,
                    category = file.category.toConversionCategory(),
                    targetFormat = file.targetFormat,
                    outputDestination = outputDestination,
                    videoOptions = pendingVideoOptions,
                    audioOptions = pendingAudioOptions,
                    imageOptions = pendingImageOptions
                )
            }
        )
        ConversionService.start(this)
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
}

private data class PendingSelection(
    val category: FileCategory,
    val targetFormat: TargetFormat
)

private data class OpenableMetadata(
    val displayName: String,
    val sizeBytes: Long?
)

private fun QueuedFile.hasConnectedNativeTarget(): Boolean {
    return when (category) {
        FileCategory.Video -> targetFormat.equals("MP4", ignoreCase = true)
        FileCategory.Audio -> audioTargetExtensionFor(targetFormat) in CONNECTED_AUDIO_TARGETS
        FileCategory.Image -> targetFormat.equals("JPG", ignoreCase = true) ||
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
