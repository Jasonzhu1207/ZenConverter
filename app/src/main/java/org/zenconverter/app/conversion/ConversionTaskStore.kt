package org.zenconverter.app.conversion

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

data class ConversionTaskInput(
    val fileId: String,
    val inputUri: Uri,
    val inputUris: List<Uri>,
    val displayName: String,
    val mimeType: String?,
    val category: ConversionMediaCategory,
    val targetFormat: String,
    val outputDestination: OutputDestination,
    val videoOptions: VideoExportOptions,
    val audioOptions: AudioExportOptions,
    val imageOptions: ImageExportOptions,
    val pdfOptions: PdfExportOptions,
    val inputInfo: FileBasicInfo? = null,
    val gifFrameMode: GifFrameExportMode = GifFrameExportMode.FirstFrame,
    val pdfPasswords: List<String?> = emptyList()
)

sealed interface OutputDestination {
    object DefaultPublicDirectory : OutputDestination
    data class CustomDirectory(val uri: Uri) : OutputDestination
}

enum class ConversionMediaCategory {
    Video,
    Audio,
    Image,
    Pdf,
    Document
}

data class VideoExportOptions(
    val maxShortSidePixels: Int? = null,
    val videoBitrate: Int? = null,
    val videoMimeType: String = VIDEO_MIME_TYPE_H264,
    val maxFrameRate: Int? = null,
    val advanced: VideoAdvancedOptions = VideoAdvancedOptions()
) {
    companion object {
        const val VIDEO_MIME_TYPE_H264 = "video/avc"
        const val VIDEO_MIME_TYPE_H265 = "video/hevc"
    }
}

data class AudioExportOptions(
    val audioBitrate: Int? = null,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
    val advanced: AudioAdvancedOptions = AudioAdvancedOptions()
)

data class VideoAdvancedOptions(
    val fadeInSeconds: Float? = null,
    val fadeOutSeconds: Float? = null,
    val mirror: VideoMirrorMode = VideoMirrorMode.Off,
    val rotation: VideoRotationMode = VideoRotationMode.None,
    val aspectRatio: VideoAspectRatioMode = VideoAspectRatioMode.Keep
) {
    val hasEnabledEffects: Boolean
        get() = fadeInSeconds != null ||
            fadeOutSeconds != null ||
            mirror != VideoMirrorMode.Off ||
            rotation != VideoRotationMode.None ||
            aspectRatio != VideoAspectRatioMode.Keep
}

data class AudioAdvancedOptions(
    val fadeInSeconds: Float? = null,
    val fadeOutSeconds: Float? = null,
    val volume: AudioVolumeMode = AudioVolumeMode.Original,
    val echo: AudioEchoMode = AudioEchoMode.Off
) {
    val hasEnabledEffects: Boolean
        get() = fadeInSeconds != null ||
            fadeOutSeconds != null ||
            volume != AudioVolumeMode.Original ||
            echo != AudioEchoMode.Off
}

enum class VideoMirrorMode {
    Off,
    Horizontal,
    Vertical,
    Both
}

enum class VideoRotationMode {
    None,
    Clockwise90,
    CounterClockwise90,
    Rotate180
}

enum class VideoAspectRatioMode {
    Keep,
    Fit16By9,
    Fit9By16,
    Fit1By1,
    Crop16By9,
    Crop9By16,
    Crop1By1
}

enum class AudioVolumeMode {
    Original,
    Mute,
    Half,
    OneAndHalf,
    Double
}

enum class AudioEchoMode {
    Off,
    Light,
    Room
}

data class ImageExportOptions(
    val quality: Int = 90,
    val webpLossless: Boolean = false
)

data class PdfExportOptions(
    val imagePageMode: PdfImagePageMode = PdfImagePageMode.A4Fit,
    val renderQuality: PdfRenderQuality = PdfRenderQuality.Balanced
)

enum class PdfImagePageMode {
    A4Fit,
    OriginalRatio
}

enum class PdfRenderQuality {
    LowResolution,
    Balanced,
    HighDetail
}

enum class GifFrameExportMode {
    FirstFrame,
    FramesAsImages,
    FramesAsSinglePdf,
    FramesAsPdfFiles
}

data class ConversionTaskState(
    val fileId: String,
    val displayName: String,
    val targetFormat: String,
    val status: ConversionTaskStatus,
    val progress: Float,
    val message: String,
    val outputUri: Uri? = null,
    val outputUris: List<Uri> = emptyList(),
    val outputDirectoryUri: Uri? = null,
    val outputMimeType: String? = null,
    val outputInfo: FileBasicInfo? = null
)

enum class ConversionTaskStatus {
    Queued,
    Running,
    Completed,
    Cancelled,
    Failed
}

object ConversionTaskStore {
    val tasks = mutableStateListOf<ConversionTaskState>()
    val summaryMessage = mutableStateOf<String?>(null)
    val isRunning = mutableStateOf(false)

    private val inputs = mutableListOf<ConversionTaskInput>()
    private var cancelled = false

    fun prepareRun(nextInputs: List<ConversionTaskInput>) {
        cancelled = false
        inputs.clear()
        inputs.addAll(nextInputs)
        isRunning.value = nextInputs.isNotEmpty()
        summaryMessage.value = if (nextInputs.isEmpty()) null else "Processing"
        tasks.clear()
        tasks.addAll(
            nextInputs.map { input ->
                ConversionTaskState(
                    fileId = input.fileId,
                    displayName = input.displayName,
                    targetFormat = input.targetFormat,
                    status = ConversionTaskStatus.Queued,
                    progress = 0f,
                    message = "Queued"
                )
            }
        )
    }

    fun showMessage(message: String) {
        summaryMessage.value = message
    }

    fun taskCount(): Int = tasks.size

    fun inputAt(index: Int): ConversionTaskInput? = inputs.getOrNull(index)

    fun isCancelled(): Boolean = cancelled

    fun markRunning(index: Int) {
        updateTask(index) { task ->
            task.copy(
                status = ConversionTaskStatus.Running,
                progress = 0f,
                message = "Processing"
            )
        }
        summaryMessage.value = "Processing"
        isRunning.value = true
    }

    fun markSaving(index: Int) {
        updateTask(index) { task ->
            task.copy(
                status = ConversionTaskStatus.Running,
                progress = task.progress.coerceAtLeast(0.98f),
                message = "Saving"
            )
        }
        summaryMessage.value = "Saving"
        isRunning.value = true
    }

    fun updateProgress(index: Int, progress: Float) {
        updateTask(index) { task ->
            task.copy(
                status = ConversionTaskStatus.Running,
                progress = progress.coerceIn(0f, 1f),
                message = "Processing"
            )
        }
    }

    fun markCompleted(
        index: Int,
        outputUri: Uri? = null,
        outputUris: List<Uri> = outputUri?.let { listOf(it) }.orEmpty(),
        outputDirectoryUri: Uri? = null,
        outputMimeType: String? = null,
        outputInfo: FileBasicInfo? = null
    ) {
        updateTask(index) { task ->
            task.copy(
                status = ConversionTaskStatus.Completed,
                progress = 1f,
                message = "Conversion complete",
                outputUri = outputUri,
                outputUris = outputUris,
                outputDirectoryUri = outputDirectoryUri,
                outputMimeType = outputMimeType,
                outputInfo = outputInfo
            )
        }
    }

    fun markFailed(index: Int, message: String) {
        updateTask(index) { task ->
            task.copy(
                status = ConversionTaskStatus.Failed,
                message = message
            )
        }
        summaryMessage.value = message
    }

    fun markRunFinished() {
        isRunning.value = false
        summaryMessage.value = tasks.lastOrNull { it.status == ConversionTaskStatus.Failed }
            ?.message
            ?: "Conversion complete"
        clearSensitiveInputs()
    }

    fun cancelAll() {
        cancelled = true
        isRunning.value = false
        summaryMessage.value = "Cancelled"
        for (index in tasks.indices) {
            val task = tasks[index]
            if (task.status == ConversionTaskStatus.Queued || task.status == ConversionTaskStatus.Running) {
                tasks[index] = task.copy(
                    status = ConversionTaskStatus.Cancelled,
                    message = "Cancelled"
                )
            }
        }
        clearSensitiveInputs()
    }

    fun failRunning(message: String) {
        isRunning.value = false
        summaryMessage.value = message
        val index = tasks.indexOfFirst { it.status == ConversionTaskStatus.Running }
        if (index >= 0) {
            tasks[index] = tasks[index].copy(
                status = ConversionTaskStatus.Failed,
                message = message
            )
        }
        clearSensitiveInputs()
    }

    fun clear() {
        cancelled = false
        isRunning.value = false
        summaryMessage.value = null
        inputs.clear()
        tasks.clear()
    }

    fun aggregateProgress(): Float {
        if (tasks.isEmpty()) return 0f
        return tasks.sumOf { it.progress.toDouble() }.toFloat() / tasks.size
    }

    private fun updateTask(
        index: Int,
        transform: (ConversionTaskState) -> ConversionTaskState
    ) {
        if (index !in tasks.indices) return
        tasks[index] = transform(tasks[index])
    }

    private fun clearSensitiveInputs() {
        for (index in inputs.indices) {
            val input = inputs[index]
            if (input.pdfPasswords.any { it != null }) {
                inputs[index] = input.copy(pdfPasswords = emptyList())
            }
        }
    }
}
