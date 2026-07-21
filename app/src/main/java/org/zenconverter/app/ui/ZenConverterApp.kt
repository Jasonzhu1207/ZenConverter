package org.zenconverter.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image as BrandImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.zenconverter.app.conversion.AudioAdvancedOptions
import org.zenconverter.app.conversion.AudioEchoMode
import org.zenconverter.app.conversion.AudioExportOptions
import org.zenconverter.app.conversion.AudioNoiseReductionMode
import org.zenconverter.app.conversion.AudioVolumeMode
import org.zenconverter.app.conversion.FileBasicInfo
import org.zenconverter.app.conversion.GifFrameExportMode
import org.zenconverter.app.conversion.ImageExportOptions
import org.zenconverter.app.conversion.PdfExportOptions
import org.zenconverter.app.conversion.PdfImagePageMode
import org.zenconverter.app.conversion.PdfRenderQuality
import org.zenconverter.app.conversion.PdfSecurityOptions
import org.zenconverter.app.conversion.VideoAdvancedOptions
import org.zenconverter.app.conversion.VideoAspectRatioMode
import org.zenconverter.app.conversion.VideoExportOptions
import org.zenconverter.app.conversion.VideoMirrorMode
import org.zenconverter.app.conversion.VideoRotationMode
import org.zenconverter.app.updates.ApkInstaller
import org.zenconverter.app.updates.ApkOpenResult
import org.zenconverter.app.updates.ApkUpdateDownloader
import org.zenconverter.app.updates.DownloadProgress
import org.zenconverter.app.updates.DownloadedUpdate
import org.zenconverter.app.updates.GitHubUpdateChecker
import org.zenconverter.app.updates.InstalledAppVersion
import org.zenconverter.app.updates.UpdateChannel
import org.zenconverter.app.updates.UpdateCheckResult
import org.zenconverter.app.updates.UpdateFailureReason
import org.zenconverter.app.updates.UpdateRelease
import org.zenconverter.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

data class TargetFormat(
    val label: String,
    val extension: String,
    val modeHint: String
)

enum class FileCategory(
    val mimeTypes: List<String>,
    val formats: List<TargetFormat>
) {
    Video(
        mimeTypes = listOf("video/*"),
        formats = listOf(
            TargetFormat("MP4", "mp4", "Re-encode"),
            TargetFormat("MKV", "mkv", "Re-encode"),
            TargetFormat("MOV", "mov", "Re-encode"),
            TargetFormat("GIF", "gif", "30s GIF")
        )
    ),
    Audio(
        mimeTypes = listOf("audio/*", "video/*"),
        formats = listOf(
            TargetFormat("M4A (AAC)", "m4a", "Re-encode"),
            TargetFormat("MP3", "mp3", "Re-encode"),
            TargetFormat("WAV", "wav", "Re-encode"),
            TargetFormat("FLAC", "flac", "Re-encode"),
            TargetFormat("WMA", "wma", "Re-encode")
        )
    ),
    Image(
        mimeTypes = listOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/heic",
            "image/heif",
            "image/vnd.microsoft.icon",
            "image/x-icon",
            "image/ico"
        ),
        formats = listOf(
            TargetFormat("JPG", "jpg", "Batch"),
            TargetFormat("JFIF", "jfif", "JPEG"),
            TargetFormat("PNG", "png", "Supports transparency"),
            TargetFormat("WEBP", "webp", "Supports transparency"),
            TargetFormat("ICO", "ico", "Icon"),
            TargetFormat("PDF", "pdf", "PDF")
        )
    ),
    Pdf(
        mimeTypes = listOf("application/pdf"),
        formats = listOf(
            TargetFormat("PNG", "png", "Page rasterization"),
            TargetFormat("JPG", "jpg", "Page rasterization"),
            TargetFormat("WEBP", "webp", "Page rasterization"),
            TargetFormat("PDF", "pdf", "Merge PDFs"),
            TargetFormat("TXT", "txt", "Text layer"),
            TargetFormat("MD", "md", "Markdown"),
            TargetFormat("Encrypt PDF", "pdf", "Password protect"),
            TargetFormat("Decrypt PDF", "pdf", "Remove password")
        )
    ),
    Document(
        mimeTypes = listOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ),
        formats = listOf(
            TargetFormat("PDF", "pdf", "Office to PDF"),
            TargetFormat("TXT", "txt", "Text layer"),
            TargetFormat("MD", "md", "Markdown")
        )
    )
}

data class QueuedFile(
    val id: String,
    val uri: Uri,
    val inputUris: List<Uri>,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String?,
    val category: FileCategory,
    val targetFormat: String,
    val inputInfo: FileBasicInfo? = null,
    val gifFrameMode: GifFrameExportMode = GifFrameExportMode.FirstFrame,
    val pdfSecurityOptions: PdfSecurityOptions = PdfSecurityOptions(),
    val pdfPasswords: List<String?> = emptyList()
)

data class ImagePdfMergePrompt(
    val fileCount: Int
)

data class GifFrameModePrompt(
    val gifCount: Int
)

data class GifPdfFramePrompt(
    val gifCount: Int
)

data class PdfPasswordPrompt(
    val displayName: String
)

data class PdfOutputPasswordPrompt(
    val fileCount: Int
)

data class OutputDirectory(
    val uri: Uri,
    val label: String,
    val persistablePermissionSaved: Boolean
)

enum class OutputLocationMode {
    Default,
    Custom
}

data class TaskProgress(
    val fileId: String,
    val status: TaskProgressStatus,
    val progress: Float,
    val message: String,
    val outputUri: Uri? = null,
    val outputUris: List<Uri> = emptyList(),
    val outputDirectoryUri: Uri? = null,
    val outputMimeType: String? = null,
    val outputInfo: FileBasicInfo? = null
)

enum class TaskProgressStatus {
    Queued,
    Running,
    Completed,
    Cancelled,
    Failed
}

private enum class SupportTargetType {
    Link,
    Wallet
}

private data class SupportTarget(
    val title: String,
    val value: String,
    val type: SupportTargetType
)

private sealed interface UpdateUiState {
    object Idle : UpdateUiState
    object Checking : UpdateUiState
    data class Available(val release: UpdateRelease) : UpdateUiState
    data class UpToDate(val latest: UpdateRelease) : UpdateUiState
    data class Failed(
        val reason: UpdateFailureReason,
        val detail: String?
    ) : UpdateUiState
}

private sealed interface UpdateDownloadUiState {
    object Idle : UpdateDownloadUiState
    data class Downloading(val progress: DownloadProgress) : UpdateDownloadUiState
    data class Completed(val downloadedUpdate: DownloadedUpdate) : UpdateDownloadUiState
    data class Failed(val message: String?) : UpdateDownloadUiState
}

private data class VideoAdvancedUiState(
    val expanded: Boolean,
    val reverse: Boolean,
    val fadeIn: String,
    val fadeOut: String,
    val mirror: String,
    val rotation: String,
    val aspectRatio: String
)

private data class AudioAdvancedUiState(
    val expanded: Boolean,
    val reverse: Boolean,
    val fadeIn: String,
    val fadeOut: String,
    val volume: String,
    val echo: String,
    val noiseReduction: String
)

private const val ZENCONVERTER_REPOSITORY_URL = "https://github.com/Jasonzhu1207/ZenConverter"
private const val AFDIAN_URL = "https://afdian.com/a/Jason1207"
private const val USDT_TRC20_ADDRESS = "TL88m9Wfdy4dAGhkLQ5jn9g8kZBTkRKrwf"
private const val BTC_ADDRESS = "bc1p4s8e4pgwse4336vtwuqrxs58jdwkyaqcxtg0txg2xjzxpls07zjsjamy77"
private const val ETH_ERC20_ADDRESS = "0x53a2d13bf808AC104cB09C722f01Ad68AFc9Da1F"

private val supportTargets = listOf(
    SupportTarget("Afdian", AFDIAN_URL, SupportTargetType.Link),
    SupportTarget("USDT (TRC-20)", USDT_TRC20_ADDRESS, SupportTargetType.Wallet),
    SupportTarget("Bitcoin (BTC)", BTC_ADDRESS, SupportTargetType.Wallet),
    SupportTarget("Ethereum (ETH / ERC-20)", ETH_ERC20_ADDRESS, SupportTargetType.Wallet)
)

private enum class AccentColorOption(
    val englishLabel: String,
    val color: Color,
    val contentColor: Color
) {
    Charcoal("Charcoal", Color(0xFF111111), Color.White),
    DeepNavy("Deep Navy", Color(0xFF36454F), Color.White),
    ForestGreen("Forest Green", Color(0xFF2D4A2B), Color.White),
    SteelBlue("Steel Blue", Color(0xFF4A6FA5), Color.White),
    DustyRose("Dusty Rose", Color(0xFFD4A5A5), Color(0xFF111111)),
    Mustard("Mustard", Color(0xFFF4A900), Color(0xFF111111)),
    BurntOrange("Burnt Orange", Color(0xFFE76F51), Color(0xFF111111)),
    ElectricBlue("Electric Blue", Color(0xFF0066FF), Color.White),
    FernGreen("Fern Green", Color(0xFF4A7C59), Color.White),
    DeepPurple("Deep Purple", Color(0xFF2B1E3E), Color.White)
}

private enum class LanguageOption {
    System,
    English,
    SimplifiedChinese,
    TraditionalChinese
}

private enum class ResolvedLanguage {
    English,
    SimplifiedChinese,
    TraditionalChinese
}

private const val VIDEO_RESOLUTION_ORIGINAL = "Original"
private const val VIDEO_RESOLUTION_2160P = "2160p"
private const val VIDEO_RESOLUTION_1440P = "1440p"
private const val VIDEO_RESOLUTION_1080P = "1080p"
private const val VIDEO_RESOLUTION_720P = "720p"
private const val VIDEO_RESOLUTION_480P = "480p"
private val VIDEO_RESOLUTION_OPTIONS = listOf(
    VIDEO_RESOLUTION_ORIGINAL,
    VIDEO_RESOLUTION_2160P,
    VIDEO_RESOLUTION_1440P,
    VIDEO_RESOLUTION_1080P,
    VIDEO_RESOLUTION_720P,
    VIDEO_RESOLUTION_480P
)
private val VIDEO_GIF_RESOLUTION_OPTIONS = listOf(
    VIDEO_RESOLUTION_480P,
    VIDEO_RESOLUTION_720P,
    VIDEO_RESOLUTION_ORIGINAL
)

private const val VIDEO_BITRATE_AUTO = "Auto bitrate"
private const val VIDEO_BITRATE_LOW = "Low bitrate"
private const val VIDEO_BITRATE_MEDIUM = "Medium bitrate"
private const val VIDEO_BITRATE_HIGH = "High bitrate"
private const val VIDEO_BITRATE_VERY_HIGH = "Very high bitrate"
private const val VIDEO_BITRATE_ULTRA = "Ultra bitrate"
private val VIDEO_BITRATE_OPTIONS = listOf(
    VIDEO_BITRATE_AUTO,
    VIDEO_BITRATE_LOW,
    VIDEO_BITRATE_MEDIUM,
    VIDEO_BITRATE_HIGH,
    VIDEO_BITRATE_VERY_HIGH,
    VIDEO_BITRATE_ULTRA
)

private const val VIDEO_CODEC_H264 = "H.264"
private const val VIDEO_CODEC_H265 = "H.265"

private const val VIDEO_FRAME_RATE_ORIGINAL = "Original"
private const val VIDEO_FRAME_RATE_25 = "Frame rate 25"
private const val VIDEO_FRAME_RATE_30 = "Frame rate 30"
private const val VIDEO_FRAME_RATE_60 = "Frame rate 60"
private val VIDEO_FRAME_RATE_OPTIONS = listOf(
    VIDEO_FRAME_RATE_ORIGINAL,
    VIDEO_FRAME_RATE_25,
    VIDEO_FRAME_RATE_30,
    VIDEO_FRAME_RATE_60
)

private const val AUDIO_BITRATE_AUTO = "Auto audio bitrate"
private const val AUDIO_BITRATE_RECOMMENDED = "Recommended audio bitrate"
private const val AUDIO_BITRATE_HIGH = "High audio bitrate"
private const val AUDIO_BITRATE_COMPACT = "Compact audio bitrate"
private const val AUDIO_BITRATE_VOICE = "Voice audio bitrate"
private val AUDIO_BITRATE_OPTIONS = listOf(
    AUDIO_BITRATE_AUTO,
    AUDIO_BITRATE_RECOMMENDED,
    AUDIO_BITRATE_HIGH,
    AUDIO_BITRATE_COMPACT,
    AUDIO_BITRATE_VOICE
)

private const val AUDIO_SAMPLE_RATE_ORIGINAL = "Original"
private const val AUDIO_SAMPLE_RATE_RECOMMENDED = "Recommended sample rate"
private const val AUDIO_SAMPLE_RATE_44100 = "44.1 kHz"
private const val AUDIO_SAMPLE_RATE_32000 = "32 kHz"
private val AUDIO_SAMPLE_RATE_OPTIONS = listOf(
    AUDIO_SAMPLE_RATE_ORIGINAL,
    AUDIO_SAMPLE_RATE_RECOMMENDED,
    AUDIO_SAMPLE_RATE_44100,
    AUDIO_SAMPLE_RATE_32000
)

private const val AUDIO_CHANNELS_ORIGINAL = "Original"
private const val AUDIO_CHANNELS_STEREO = "Stereo"
private const val AUDIO_CHANNELS_MONO = "Mono"
private val AUDIO_CHANNEL_OPTIONS = listOf(
    AUDIO_CHANNELS_ORIGINAL,
    AUDIO_CHANNELS_STEREO,
    AUDIO_CHANNELS_MONO
)

private const val ADVANCED_FADE_OFF = "Off"
private const val ADVANCED_FADE_HALF_SECOND = "0.5s"
private const val ADVANCED_FADE_ONE_SECOND = "1s"
private const val ADVANCED_FADE_TWO_SECONDS = "2s"
private val ADVANCED_FADE_OPTIONS = listOf(
    ADVANCED_FADE_OFF,
    ADVANCED_FADE_HALF_SECOND,
    ADVANCED_FADE_ONE_SECOND,
    ADVANCED_FADE_TWO_SECONDS
)

private const val VIDEO_MIRROR_OFF = "Mirror off"
private const val VIDEO_MIRROR_HORIZONTAL = "Horizontal"
private const val VIDEO_MIRROR_VERTICAL = "Vertical"
private const val VIDEO_MIRROR_BOTH = "Both"
private val VIDEO_MIRROR_OPTIONS = listOf(
    VIDEO_MIRROR_OFF,
    VIDEO_MIRROR_HORIZONTAL,
    VIDEO_MIRROR_VERTICAL,
    VIDEO_MIRROR_BOTH
)

private const val VIDEO_ROTATION_NONE = "No rotation"
private const val VIDEO_ROTATION_90_CW = "90 CW"
private const val VIDEO_ROTATION_90_CCW = "90 CCW"
private const val VIDEO_ROTATION_180 = "180"
private val VIDEO_ROTATION_OPTIONS = listOf(
    VIDEO_ROTATION_NONE,
    VIDEO_ROTATION_90_CW,
    VIDEO_ROTATION_90_CCW,
    VIDEO_ROTATION_180
)

private const val VIDEO_ASPECT_KEEP = "Keep aspect"
private const val VIDEO_ASPECT_FIT_16_9 = "Fit 16:9"
private const val VIDEO_ASPECT_FIT_9_16 = "Fit 9:16"
private const val VIDEO_ASPECT_FIT_1_1 = "Fit 1:1"
private const val VIDEO_ASPECT_CROP_16_9 = "Crop 16:9"
private const val VIDEO_ASPECT_CROP_9_16 = "Crop 9:16"
private const val VIDEO_ASPECT_CROP_1_1 = "Crop 1:1"
private val VIDEO_ASPECT_OPTIONS = listOf(
    VIDEO_ASPECT_KEEP,
    VIDEO_ASPECT_FIT_16_9,
    VIDEO_ASPECT_FIT_9_16,
    VIDEO_ASPECT_FIT_1_1,
    VIDEO_ASPECT_CROP_16_9,
    VIDEO_ASPECT_CROP_9_16,
    VIDEO_ASPECT_CROP_1_1
)

private const val AUDIO_VOLUME_MUTE = "Mute"
private const val AUDIO_VOLUME_50 = "50%"
private const val AUDIO_VOLUME_100 = "100%"
private const val AUDIO_VOLUME_150 = "150%"
private const val AUDIO_VOLUME_200 = "200%"
private val AUDIO_VOLUME_OPTIONS = listOf(
    AUDIO_VOLUME_100,
    AUDIO_VOLUME_50,
    AUDIO_VOLUME_150,
    AUDIO_VOLUME_200,
    AUDIO_VOLUME_MUTE
)

private const val AUDIO_ECHO_OFF = "Echo off"
private const val AUDIO_ECHO_LIGHT = "Light echo"
private const val AUDIO_ECHO_ROOM = "Room echo"
private val AUDIO_ECHO_OPTIONS = listOf(
    AUDIO_ECHO_OFF,
    AUDIO_ECHO_LIGHT,
    AUDIO_ECHO_ROOM
)

private const val AUDIO_DENOISE_OFF = "Noise reduction off"
private const val AUDIO_DENOISE_LIGHT = "Light denoise"
private const val AUDIO_DENOISE_STANDARD = "Standard denoise"
private val AUDIO_DENOISE_OPTIONS = listOf(
    AUDIO_DENOISE_OFF,
    AUDIO_DENOISE_LIGHT,
    AUDIO_DENOISE_STANDARD
)

private const val IMAGE_QUALITY_ORIGINAL = "Original"
private const val IMAGE_QUALITY_LOSSLESS = "Lossless"
private const val IMAGE_QUALITY_HIGH = "High"
private const val IMAGE_QUALITY_BALANCED = "Balanced"
private const val IMAGE_QUALITY_SMALL = "Small"
private val IMAGE_QUALITY_OPTIONS = listOf(
    IMAGE_QUALITY_ORIGINAL,
    IMAGE_QUALITY_HIGH,
    IMAGE_QUALITY_BALANCED,
    IMAGE_QUALITY_SMALL
)

private const val PDF_PAGE_MODE_A4_FIT = "A4 fit"
private const val PDF_PAGE_MODE_ORIGINAL_RATIO = "Original ratio"
private val PDF_PAGE_MODE_OPTIONS = listOf(
    PDF_PAGE_MODE_A4_FIT,
    PDF_PAGE_MODE_ORIGINAL_RATIO
)

private const val PDF_RENDER_QUALITY_LOW = "Low resolution"
private const val PDF_RENDER_QUALITY_BALANCED = "Balanced"
private const val PDF_RENDER_QUALITY_HIGH = "High detail"
private val PDF_RENDER_QUALITY_OPTIONS = listOf(
    PDF_RENDER_QUALITY_BALANCED,
    PDF_RENDER_QUALITY_LOW,
    PDF_RENDER_QUALITY_HIGH
)

@Composable
fun ZenConverterApp(
    queuedFiles: List<QueuedFile>,
    supportedVideoMimeTypes: Set<String>,
    outputLocationMode: OutputLocationMode,
    outputDirectory: OutputDirectory?,
    conversionTasks: List<TaskProgress>,
    conversionSummary: String?,
    isConversionRunning: Boolean,
    onOutputLocationModeChange: (OutputLocationMode) -> Unit,
    onPickFiles: (FileCategory, TargetFormat) -> Unit,
    onPickOutputDirectory: () -> Unit,
    onRemoveFile: (String) -> Unit,
    onClearQueue: () -> Unit,
    imagePdfMergePrompt: ImagePdfMergePrompt?,
    gifFrameModePrompt: GifFrameModePrompt?,
    gifPdfFramePrompt: GifPdfFramePrompt?,
    pdfPasswordPrompt: PdfPasswordPrompt?,
    pdfOutputPasswordPrompt: PdfOutputPasswordPrompt?,
    onChooseSinglePdf: () -> Unit,
    onChooseOnePdfPerImage: () -> Unit,
    onDismissImagePdfPrompt: () -> Unit,
    onChooseGifFirstFrame: () -> Unit,
    onChooseGifSplitFrames: () -> Unit,
    onDismissGifFramePrompt: () -> Unit,
    onChooseGifFramesSinglePdf: () -> Unit,
    onChooseGifFramePdfFiles: () -> Unit,
    onDismissGifPdfFramePrompt: () -> Unit,
    onSubmitPdfPassword: (String) -> Unit,
    onCancelPdfPassword: () -> Unit,
    onSubmitPdfOutputPassword: (String) -> Unit,
    onCancelPdfOutputPassword: () -> Unit,
    onStartConversion: (VideoExportOptions, AudioExportOptions, ImageExportOptions, PdfExportOptions) -> Unit,
    onCancelConversion: () -> Unit
) {
    var accent by remember { mutableStateOf(AccentColorOption.Charcoal) }
    var languageOption by remember { mutableStateOf(LanguageOption.System) }
    val texts = uiTextFor(resolveLanguage(languageOption))
    val rootView = LocalView.current

    DisposableEffect(rootView, isConversionRunning) {
        val previousKeepScreenOn = rootView.keepScreenOn
        rootView.keepScreenOn = isConversionRunning || previousKeepScreenOn
        onDispose {
            rootView.keepScreenOn = previousKeepScreenOn
        }
    }

    MaterialTheme(colorScheme = zenConverterColorScheme(accent)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ZenConverterContent(
                accent = accent,
                languageOption = languageOption,
                texts = texts,
                queuedFiles = queuedFiles,
                supportedVideoMimeTypes = supportedVideoMimeTypes,
                outputLocationMode = outputLocationMode,
                outputDirectory = outputDirectory,
                conversionTasks = conversionTasks,
                conversionSummary = conversionSummary,
                isConversionRunning = isConversionRunning,
                onAccentSelected = { accent = it },
                onLanguageSelected = { languageOption = it },
                onOutputLocationModeChange = onOutputLocationModeChange,
                onPickFiles = onPickFiles,
                onPickOutputDirectory = onPickOutputDirectory,
                onRemoveFile = onRemoveFile,
                onClearQueue = onClearQueue,
                onStartConversion = onStartConversion,
                onCancelConversion = onCancelConversion
            )
            imagePdfMergePrompt?.let { prompt ->
                ImagePdfMergeDialog(
                    texts = texts,
                    prompt = prompt,
                    onSinglePdf = onChooseSinglePdf,
                    onOnePdfPerImage = onChooseOnePdfPerImage,
                    onDismiss = onDismissImagePdfPrompt
                )
            }
            gifFrameModePrompt?.let { prompt ->
                GifFrameModeDialog(
                    texts = texts,
                    prompt = prompt,
                    onFirstFrame = onChooseGifFirstFrame,
                    onSplitFrames = onChooseGifSplitFrames,
                    onDismiss = onDismissGifFramePrompt
                )
            }
            gifPdfFramePrompt?.let { prompt ->
                GifPdfFrameDialog(
                    texts = texts,
                    prompt = prompt,
                    onSinglePdf = onChooseGifFramesSinglePdf,
                    onPdfFiles = onChooseGifFramePdfFiles,
                    onDismiss = onDismissGifPdfFramePrompt
                )
            }
            pdfPasswordPrompt?.let { prompt ->
                PdfPasswordDialog(
                    texts = texts,
                    prompt = prompt,
                    onSubmit = onSubmitPdfPassword,
                    onCancel = onCancelPdfPassword
                )
            }
            pdfOutputPasswordPrompt?.let { prompt ->
                PdfOutputPasswordDialog(
                    texts = texts,
                    prompt = prompt,
                    onSubmit = onSubmitPdfOutputPassword,
                    onCancel = onCancelPdfOutputPassword
                )
            }
        }
    }
}

@Composable
private fun ZenConverterContent(
    accent: AccentColorOption,
    languageOption: LanguageOption,
    texts: UiText,
    queuedFiles: List<QueuedFile>,
    supportedVideoMimeTypes: Set<String>,
    outputLocationMode: OutputLocationMode,
    outputDirectory: OutputDirectory?,
    conversionTasks: List<TaskProgress>,
    conversionSummary: String?,
    isConversionRunning: Boolean,
    onAccentSelected: (AccentColorOption) -> Unit,
    onLanguageSelected: (LanguageOption) -> Unit,
    onOutputLocationModeChange: (OutputLocationMode) -> Unit,
    onPickFiles: (FileCategory, TargetFormat) -> Unit,
    onPickOutputDirectory: () -> Unit,
    onRemoveFile: (String) -> Unit,
    onClearQueue: () -> Unit,
    onStartConversion: (VideoExportOptions, AudioExportOptions, ImageExportOptions, PdfExportOptions) -> Unit,
    onCancelConversion: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }
    var activeCategory by remember { mutableStateOf(FileCategory.Video) }
    var videoTarget by remember { mutableStateOf(FileCategory.Video.formats.first()) }
    var audioTarget by remember { mutableStateOf(FileCategory.Audio.formats.first()) }
    var imageTarget by remember { mutableStateOf(FileCategory.Image.formats.first()) }
    var pdfTarget by remember { mutableStateOf(FileCategory.Pdf.formats.first()) }
    var documentTarget by remember { mutableStateOf(FileCategory.Document.formats.first()) }
    var queueMessage by remember { mutableStateOf<String?>(null) }
    var openMenuId by remember { mutableStateOf<String?>(null) }
    var videoResolution by remember { mutableStateOf(VIDEO_RESOLUTION_ORIGINAL) }
    var videoBitrate by remember { mutableStateOf(VIDEO_BITRATE_AUTO) }
    var videoCodec by remember(supportedVideoMimeTypes) {
        mutableStateOf(defaultVideoCodecFor(supportedVideoMimeTypes))
    }
    var videoFrameRate by remember { mutableStateOf(VIDEO_FRAME_RATE_ORIGINAL) }
    var audioBitrate by remember { mutableStateOf(AUDIO_BITRATE_AUTO) }
    var audioSampleRate by remember { mutableStateOf(AUDIO_SAMPLE_RATE_ORIGINAL) }
    var audioChannels by remember { mutableStateOf(AUDIO_CHANNELS_ORIGINAL) }
    var videoAdvancedExpanded by remember { mutableStateOf(false) }
    var videoReverse by remember { mutableStateOf(false) }
    var videoFadeIn by remember { mutableStateOf(ADVANCED_FADE_OFF) }
    var videoFadeOut by remember { mutableStateOf(ADVANCED_FADE_OFF) }
    var videoMirror by remember { mutableStateOf(VIDEO_MIRROR_OFF) }
    var videoRotation by remember { mutableStateOf(VIDEO_ROTATION_NONE) }
    var videoAspectRatio by remember { mutableStateOf(VIDEO_ASPECT_KEEP) }
    var audioAdvancedExpanded by remember { mutableStateOf(false) }
    var audioReverse by remember { mutableStateOf(false) }
    var audioFadeIn by remember { mutableStateOf(ADVANCED_FADE_OFF) }
    var audioFadeOut by remember { mutableStateOf(ADVANCED_FADE_OFF) }
    var audioVolume by remember { mutableStateOf(AUDIO_VOLUME_100) }
    var audioEcho by remember { mutableStateOf(AUDIO_ECHO_OFF) }
    var audioNoiseReduction by remember { mutableStateOf(AUDIO_DENOISE_OFF) }
    var imageQuality by remember { mutableStateOf(IMAGE_QUALITY_BALANCED) }
    var pdfPageMode by remember { mutableStateOf(PDF_PAGE_MODE_A4_FIT) }
    var pdfRenderQuality by remember { mutableStateOf(PDF_RENDER_QUALITY_BALANCED) }

    fun targetFor(category: FileCategory): TargetFormat = when (category) {
        FileCategory.Video -> videoTarget
        FileCategory.Audio -> audioTarget
        FileCategory.Image -> imageTarget
        FileCategory.Pdf -> pdfTarget
        FileCategory.Document -> documentTarget
    }

    fun setTarget(category: FileCategory, targetFormat: TargetFormat) {
        when (category) {
            FileCategory.Video -> {
                videoTarget = targetFormat
                if (targetFormat.extension.equals("gif", ignoreCase = true)) {
                    videoResolution = VIDEO_RESOLUTION_480P
                }
            }
            FileCategory.Audio -> audioTarget = targetFormat
            FileCategory.Image -> {
                imageTarget = targetFormat
                if (
                    imageQuality == IMAGE_QUALITY_LOSSLESS &&
                    !supportsWebpLosslessQuality(targetFormat)
                ) {
                    imageQuality = IMAGE_QUALITY_BALANCED
                }
            }
            FileCategory.Pdf -> pdfTarget = targetFormat
            FileCategory.Document -> documentTarget = targetFormat
        }
    }

    fun currentVideoOptions(): VideoExportOptions {
        if (videoTarget.extension.equals("gif", ignoreCase = true)) {
            return VideoExportOptions(
                maxShortSidePixels = videoResolutionToShortSide(videoResolution),
                videoBitrate = null,
                videoMimeType = VideoExportOptions.VIDEO_MIME_TYPE_H264,
                maxFrameRate = 30
            )
        }
        return VideoExportOptions(
            maxShortSidePixels = videoResolutionToShortSide(videoResolution),
            videoBitrate = videoBitrateToBits(videoBitrate),
            videoMimeType = videoCodecToMimeType(videoCodec),
            maxFrameRate = videoFrameRateToCap(videoFrameRate),
            advanced = VideoAdvancedOptions(
                reverse = videoReverse,
                fadeInSeconds = fadeDurationSeconds(videoFadeIn),
                fadeOutSeconds = fadeDurationSeconds(videoFadeOut),
                mirror = videoMirrorModeFor(videoMirror),
                rotation = videoRotationModeFor(videoRotation),
                aspectRatio = videoAspectRatioModeFor(videoAspectRatio)
            )
        )
    }

    fun currentAudioOptions(): AudioExportOptions {
        return AudioExportOptions(
            audioBitrate = audioBitrateToBits(audioBitrate),
            sampleRateHz = audioSampleRateToHz(audioSampleRate),
            channelCount = audioChannelsToCount(audioChannels),
            advanced = AudioAdvancedOptions(
                reverse = audioReverse,
                fadeInSeconds = fadeDurationSeconds(audioFadeIn),
                fadeOutSeconds = fadeDurationSeconds(audioFadeOut),
                volume = audioVolumeModeFor(audioVolume),
                echo = audioEchoModeFor(audioEcho),
                noiseReduction = audioNoiseReductionModeFor(audioNoiseReduction)
            )
        )
    }

    fun currentImageOptions(): ImageExportOptions {
        return ImageExportOptions(
            quality = imageQualityToPercent(imageQuality),
            webpLossless = supportsWebpLosslessQuality(imageTarget) &&
                imageQuality == IMAGE_QUALITY_LOSSLESS
        )
    }

    fun currentPdfOptions(): PdfExportOptions {
        return PdfExportOptions(
            imagePageMode = pdfPageModeToOption(pdfPageMode),
            renderQuality = pdfRenderQualityToOption(pdfRenderQuality)
        )
    }

    NoOverscroll {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { contentPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "header") {
                    Column {
                        Header(
                            texts = texts,
                            showSettings = showSettings,
                            showAbout = showAbout,
                            onToggleSettings = {
                                openMenuId = null
                                showAbout = false
                                showSettings = !showSettings
                            },
                            onToggleAbout = {
                                openMenuId = null
                                showSettings = false
                                showAbout = !showAbout
                            }
                        )
                        AnimatedVisibility(
                            visible = showSettings,
                            enter = fadeIn() + expandVertically(
                                animationSpec = spring(stiffness = 420f)
                            ),
                            exit = fadeOut() + shrinkVertically(
                                animationSpec = spring(stiffness = 520f)
                            )
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                SettingsPanel(
                                    texts = texts,
                                    selectedAccent = accent,
                                    selectedLanguage = languageOption,
                                    onAccentSelected = onAccentSelected,
                                    onLanguageSelected = onLanguageSelected
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = showAbout,
                            enter = fadeIn() + expandVertically(
                                animationSpec = spring(stiffness = 420f)
                            ),
                            exit = fadeOut() + shrinkVertically(
                                animationSpec = spring(stiffness = 520f)
                            )
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                AboutPanel(
                                    texts = texts,
                                    onShowSupport = { showSupport = true }
                                )
                            }
                        }
                    }
                }

                item(key = "conversion-lanes") {
                    ConversionLanes(
                        texts = texts,
                        activeCategory = activeCategory,
                        targetFor = ::targetFor,
                        onTargetChange = ::setTarget,
                        onActivate = { activeCategory = it },
                        openMenuId = openMenuId,
                        onOpenMenuChange = { openMenuId = it },
                        onPickFiles = { category, target ->
                            activeCategory = category
                            openMenuId = null
                            queueMessage = null
                            onPickFiles(category, target)
                        }
                    )
                }

                item(key = "encoding-panel") {
                    EncodingPanel(
                        texts = texts,
                        category = activeCategory,
                        videoResolution = videoResolution,
                        videoBitrate = videoBitrate,
                        videoCodec = videoCodec,
                        videoCodecOptions = videoCodecOptionsFor(supportedVideoMimeTypes),
                        videoFrameRate = videoFrameRate,
                        videoTarget = videoTarget,
                        audioBitrate = audioBitrate,
                        audioSampleRate = audioSampleRate,
                        audioChannels = audioChannels,
                        videoAdvanced = VideoAdvancedUiState(
                            expanded = videoAdvancedExpanded,
                            reverse = videoReverse,
                            fadeIn = videoFadeIn,
                            fadeOut = videoFadeOut,
                            mirror = videoMirror,
                            rotation = videoRotation,
                            aspectRatio = videoAspectRatio
                        ),
                        audioAdvanced = AudioAdvancedUiState(
                            expanded = audioAdvancedExpanded,
                            reverse = audioReverse,
                            fadeIn = audioFadeIn,
                            fadeOut = audioFadeOut,
                            volume = audioVolume,
                            echo = audioEcho,
                            noiseReduction = audioNoiseReduction
                        ),
                        audioTarget = audioTarget,
                        imageTarget = imageTarget,
                        pdfTarget = pdfTarget,
                        documentTarget = documentTarget,
                        imageQuality = imageQuality,
                        pdfPageMode = pdfPageMode,
                        pdfRenderQuality = pdfRenderQuality,
                        openMenuId = openMenuId,
                        onOpenMenuChange = { openMenuId = it },
                        onVideoResolutionChange = { videoResolution = it },
                        onVideoBitrateChange = { videoBitrate = it },
                        onVideoCodecChange = { videoCodec = it },
                        onVideoFrameRateChange = { videoFrameRate = it },
                        onAudioBitrateChange = { audioBitrate = it },
                        onAudioSampleRateChange = { audioSampleRate = it },
                        onAudioChannelsChange = { audioChannels = it },
                        onVideoAdvancedExpandedChange = { videoAdvancedExpanded = it },
                        onVideoReverseChange = { videoReverse = it },
                        onVideoFadeInChange = { videoFadeIn = it },
                        onVideoFadeOutChange = { videoFadeOut = it },
                        onVideoMirrorChange = { videoMirror = it },
                        onVideoRotationChange = { videoRotation = it },
                        onVideoAspectRatioChange = { videoAspectRatio = it },
                        onAudioAdvancedExpandedChange = { audioAdvancedExpanded = it },
                        onAudioReverseChange = { audioReverse = it },
                        onAudioFadeInChange = { audioFadeIn = it },
                        onAudioFadeOutChange = { audioFadeOut = it },
                        onAudioVolumeChange = { audioVolume = it },
                        onAudioEchoChange = { audioEcho = it },
                        onAudioNoiseReductionChange = { audioNoiseReduction = it },
                        onImageQualityChange = { imageQuality = it },
                        onPdfPageModeChange = { pdfPageMode = it },
                        onPdfRenderQualityChange = { pdfRenderQuality = it }
                    )
                }

                item(key = "output-panel") {
                    OutputPanel(
                        texts = texts,
                        outputLocationMode = outputLocationMode,
                        outputDirectory = outputDirectory,
                        onOutputLocationModeChange = onOutputLocationModeChange,
                        onPickOutputDirectory = onPickOutputDirectory
                    )
                }

                item(key = "queue-actions") {
                    QueueActions(
                        texts = texts,
                        hasFiles = queuedFiles.isNotEmpty(),
                        isRunning = isConversionRunning,
                        onStart = {
                            openMenuId = null
                            queueMessage = null
                            onStartConversion(
                                currentVideoOptions(),
                                currentAudioOptions(),
                                currentImageOptions(),
                                currentPdfOptions()
                            )
                        },
                        onCancel = {
                            openMenuId = null
                            queueMessage = null
                            if (isConversionRunning) {
                                onCancelConversion()
                            } else {
                                onClearQueue()
                            }
                        }
                    )
                }

                (conversionSummary ?: queueMessage)?.let { message ->
                    item(key = "status-line") {
                        StatusLine(text = texts.summaryMessage(message))
                    }
                }

                item(key = "file-queue") {
                    FileQueue(
                        texts = texts,
                        files = queuedFiles,
                        taskProgress = conversionTasks.associateBy { it.fileId },
                        canRemove = !isConversionRunning,
                        onRemoveFile = onRemoveFile
                    )
                }
            }
        }

        if (showSupport) {
            SupportDialog(
                texts = texts,
                onDismiss = { showSupport = false }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoOverscroll(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalOverscrollConfiguration provides null,
        content = content
    )
}

@Composable
private fun ImagePdfMergeDialog(
    texts: UiText,
    prompt: ImagePdfMergePrompt,
    onSinglePdf: () -> Unit,
    onOnePdfPerImage: () -> Unit,
    onDismiss: () -> Unit
) {
    ZenPromptDialog(
        title = texts.imagePdfPromptTitle,
        message = texts.imagePdfPromptMessage(prompt.fileCount),
        icon = Icons.Rounded.PictureAsPdf,
        confirmText = texts.optionValue("Single PDF"),
        dismissText = texts.optionValue("One PDF per image"),
        onConfirm = onSinglePdf,
        onDismissAction = onOnePdfPerImage,
        onDismissRequest = onDismiss
    )
}

@Composable
private fun GifFrameModeDialog(
    texts: UiText,
    prompt: GifFrameModePrompt,
    onFirstFrame: () -> Unit,
    onSplitFrames: () -> Unit,
    onDismiss: () -> Unit
) {
    ZenPromptDialog(
        title = texts.gifFramePromptTitle,
        message = texts.gifFramePromptMessage(prompt.gifCount),
        icon = Icons.Rounded.Image,
        confirmText = texts.optionValue("Split frames"),
        dismissText = texts.optionValue("First frame"),
        onConfirm = onSplitFrames,
        onDismissAction = onFirstFrame,
        onDismissRequest = onDismiss
    )
}

@Composable
private fun GifPdfFrameDialog(
    texts: UiText,
    prompt: GifPdfFramePrompt,
    onSinglePdf: () -> Unit,
    onPdfFiles: () -> Unit,
    onDismiss: () -> Unit
) {
    ZenPromptDialog(
        title = texts.gifPdfFramePromptTitle,
        message = texts.gifPdfFramePromptMessage(prompt.gifCount),
        icon = Icons.Rounded.PictureAsPdf,
        confirmText = texts.optionValue("All frames in one PDF"),
        dismissText = texts.optionValue("One PDF per frame"),
        onConfirm = onSinglePdf,
        onDismissAction = onPdfFiles,
        onDismissRequest = onDismiss
    )
}

@Composable
private fun PdfPasswordDialog(
    texts: UiText,
    prompt: PdfPasswordPrompt,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember(prompt.displayName) { mutableStateOf("") }
    ZenPromptFrame(onDismissRequest = onCancel) {
        SectionTitle(
            icon = Icons.Rounded.PictureAsPdf,
            title = texts.pdfPasswordTitle
        )
        Text(
            text = texts.pdfPasswordMessage(prompt.displayName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            singleLine = true,
            label = { Text(texts.password) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        ZenPromptActions(
            confirmText = texts.choose,
            dismissText = texts.skip,
            onConfirm = { onSubmit(password) },
            onDismissAction = onCancel
        )
    }
}

@Composable
private fun PdfOutputPasswordDialog(
    texts: UiText,
    prompt: PdfOutputPasswordPrompt,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var password by remember(prompt.fileCount) { mutableStateOf("") }
    ZenPromptFrame(onDismissRequest = onCancel) {
        SectionTitle(
            icon = Icons.Rounded.PictureAsPdf,
            title = texts.pdfOutputPasswordTitle
        )
        Text(
            text = texts.pdfOutputPasswordMessage(prompt.fileCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            singleLine = true,
            label = { Text(texts.password) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        ZenPromptActions(
            confirmText = texts.choose,
            dismissText = texts.cancel,
            onConfirm = { onSubmit(password) },
            onDismissAction = onCancel
        )
    }
}

@Composable
private fun ZenPromptDialog(
    title: String,
    message: String,
    icon: ImageVector,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismissAction: () -> Unit,
    onDismissRequest: () -> Unit
) {
    ZenPromptFrame(onDismissRequest = onDismissRequest) {
        SectionTitle(icon = icon, title = title)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ZenPromptActions(
            confirmText = confirmText,
            dismissText = dismissText,
            onConfirm = onConfirm,
            onDismissAction = onDismissAction
        )
    }
}

@Composable
private fun ZenPromptFrame(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Color(0xFFE5E5E5))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ZenPromptActions(
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismissAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onDismissAction,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(dismissText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Button(
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(confirmText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Header(
    texts: UiText,
    showSettings: Boolean,
    showAbout: Boolean,
    onToggleSettings: () -> Unit,
    onToggleAbout: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandImage(
                painter = painterResource(id = R.drawable.zenconverter),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "ZenConverter",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = texts.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HeaderIconButton(
                onClick = onToggleAbout,
                icon = if (showAbout) Icons.Rounded.Close else Icons.Rounded.ErrorOutline,
                contentDescription = if (showAbout) texts.closeAbout else texts.openAbout
            )
            HeaderIconButton(
                onClick = onToggleSettings,
                icon = if (showSettings) Icons.Rounded.Close else Icons.Rounded.Settings,
                contentDescription = if (showSettings) texts.closeSettings else texts.openSettings
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, Color(0xFFE7E7E7), CircleShape)
            .clickable(
                onClickLabel = contentDescription,
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        AppIcon(
            icon = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun SettingsPanel(
    texts: UiText,
    selectedAccent: AccentColorOption,
    selectedLanguage: LanguageOption,
    onAccentSelected: (AccentColorOption) -> Unit,
    onLanguageSelected: (LanguageOption) -> Unit
) {
    QuietPanel {
        SectionTitle(
            icon = Icons.Rounded.Palette,
            title = texts.accentColor
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AccentColorOption.entries.forEach { option ->
                AccentSwatch(
                    texts = texts,
                    option = option,
                    selected = option == selectedAccent,
                    onSelected = { onAccentSelected(option) }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        SectionTitle(
            icon = Icons.Rounded.Language,
            title = texts.language
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LanguageOption.entries.forEach { option ->
                val selected = option == selectedLanguage
                if (selected) {
                    Button(
                        onClick = { onLanguageSelected(option) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(texts.languageLabel(option))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onLanguageSelected(option) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(texts.languageLabel(option))
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutPanel(
    texts: UiText,
    onShowSupport: () -> Unit
) {
    val context = LocalContext.current
    val installedVersion = remember(context) { installedAppVersion(context) }

    QuietPanel {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BrandImage(
                painter = painterResource(id = R.drawable.zenconverter),
                contentDescription = texts.appLogo,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFFE4E4E4), RoundedCornerShape(20.dp))
            )
            Text(
                text = "ZenConverter",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            SmallTag("${texts.appVersion} ${installedVersion.versionName}")
            SmallTag(texts.appLicense)
            Text(
                text = texts.aboutDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedButton(
            onClick = { openExternalLink(context, ZENCONVERTER_REPOSITORY_URL, texts.linkUnavailable) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) {
            AppIcon(
                icon = Icons.Rounded.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = texts.githubRepository,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            AppIcon(
                icon = Icons.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        UpdatePanel(
            texts = texts,
            installedVersion = installedVersion
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onShowSupport,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF151515),
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            AppIcon(
                icon = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = Color(0xFFFFD6C2),
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = texts.supportDevelopment,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun UpdatePanel(
    texts: UiText,
    installedVersion: InstalledAppVersion
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var channel by remember { mutableStateOf(UpdateChannel.Stable) }
    var updateState by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var downloadState by remember {
        mutableStateOf<UpdateDownloadUiState>(UpdateDownloadUiState.Idle)
    }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    val isChecking = updateState is UpdateUiState.Checking
    val isDownloading = downloadState is UpdateDownloadUiState.Downloading
    val isBusy = isChecking || isDownloading
    val availableRelease = (updateState as? UpdateUiState.Available)?.release

    fun resetForChannel(nextChannel: UpdateChannel) {
        if (channel == nextChannel || isBusy) return
        channel = nextChannel
        updateState = UpdateUiState.Idle
        downloadState = UpdateDownloadUiState.Idle
    }

    fun checkUpdates() {
        if (isBusy) return
        updateState = UpdateUiState.Checking
        downloadState = UpdateDownloadUiState.Idle
        scope.launch {
            when (val result = GitHubUpdateChecker.check(channel, installedVersion)) {
                is UpdateCheckResult.Available -> {
                    updateState = UpdateUiState.Available(result.release)
                }
                is UpdateCheckResult.UpToDate -> {
                    updateState = UpdateUiState.UpToDate(result.latest)
                }
                is UpdateCheckResult.Failed -> {
                    updateState = UpdateUiState.Failed(result.reason, result.detail)
                }
            }
        }
    }

    fun startAppDownload(release: UpdateRelease) {
        if (isDownloading) return
        downloadJob?.cancel()
        downloadState = UpdateDownloadUiState.Downloading(
            DownloadProgress(bytesDownloaded = 0L, totalBytes = release.sizeBytes)
        )
        downloadJob = scope.launch {
            try {
                val downloadedUpdate = ApkUpdateDownloader.download(context, release) { progress ->
                    downloadState = UpdateDownloadUiState.Downloading(progress)
                }
                downloadState = UpdateDownloadUiState.Completed(downloadedUpdate)
            } catch (_: CancellationException) {
                downloadState = UpdateDownloadUiState.Idle
                Toast.makeText(context, texts.downloadCancelled, Toast.LENGTH_SHORT).show()
            } catch (exception: Throwable) {
                downloadState = UpdateDownloadUiState.Failed(exception.message)
            } finally {
                downloadJob = null
            }
        }
    }

    fun openDownloadedApk(downloadedUpdate: DownloadedUpdate) {
        when (ApkInstaller.openDownloadedApk(context, downloadedUpdate.file)) {
            ApkOpenResult.Started -> Unit
            ApkOpenResult.PermissionSettingsOpened -> {
                Toast.makeText(context, texts.installPermissionRequired, Toast.LENGTH_LONG).show()
            }
            ApkOpenResult.Failed -> {
                Toast.makeText(context, texts.apkOpenFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UpdateChannelButton(
                text = texts.updateChannelLabel(UpdateChannel.Stable),
                selected = channel == UpdateChannel.Stable,
                enabled = !isBusy,
                onClick = { resetForChannel(UpdateChannel.Stable) },
                modifier = Modifier.weight(1f)
            )
            UpdateChannelButton(
                text = texts.updateChannelLabel(UpdateChannel.Preview),
                selected = channel == UpdateChannel.Preview,
                enabled = !isBusy,
                onClick = { resetForChannel(UpdateChannel.Preview) },
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedButton(
            onClick = ::checkUpdates,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) {
            AppIcon(
                icon = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isChecking) texts.checkingUpdates else texts.checkUpdates,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        when (val state = updateState) {
            UpdateUiState.Idle -> Unit
            UpdateUiState.Checking -> {
                StatusLine(text = texts.checkingUpdates)
            }
            is UpdateUiState.Available -> {
                StatusLine(text = texts.updateAvailableMessage(state.release))
                Text(
                    text = texts.releaseDetail(state.release),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            is UpdateUiState.UpToDate -> {
                StatusLine(text = texts.currentIsLatest(state.latest.channel))
            }
            is UpdateUiState.Failed -> {
                StatusLine(
                    text = texts.updateFailureMessage(state.reason, state.detail),
                    isError = true
                )
            }
        }

        availableRelease?.let { release ->
            UpdateDownloadActions(
                texts = texts,
                downloadState = downloadState,
                onAppDownload = { startAppDownload(release) },
                onBrowserDownload = {
                    openExternalLink(context, release.downloadUrl, texts.linkUnavailable)
                },
                onCancelDownload = { downloadJob?.cancel() },
                onOpenDownloadedApk = ::openDownloadedApk
            )
        }
    }
}

@Composable
private fun UpdateChannelButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 42.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.heightIn(min = 42.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun UpdateDownloadActions(
    texts: UiText,
    downloadState: UpdateDownloadUiState,
    onAppDownload: () -> Unit,
    onBrowserDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenDownloadedApk: (DownloadedUpdate) -> Unit
) {
    when (downloadState) {
        UpdateDownloadUiState.Idle -> {
            UpdateDownloadButtons(
                texts = texts,
                onAppDownload = onAppDownload,
                onBrowserDownload = onBrowserDownload
            )
        }
        is UpdateDownloadUiState.Downloading -> {
            UpdateDownloadProgress(
                texts = texts,
                progress = downloadState.progress,
                onCancelDownload = onCancelDownload
            )
        }
        is UpdateDownloadUiState.Completed -> {
            StatusLine(text = texts.downloadComplete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onOpenDownloadedApk(downloadState.downloadedUpdate) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = texts.openDownloadedApk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onBrowserDownload,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = texts.browserDownload,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        is UpdateDownloadUiState.Failed -> {
            StatusLine(
                text = texts.downloadFailureMessage(downloadState.message),
                isError = true
            )
            UpdateDownloadButtons(
                texts = texts,
                onAppDownload = onAppDownload,
                onBrowserDownload = onBrowserDownload
            )
        }
    }
}

@Composable
private fun UpdateDownloadButtons(
    texts: UiText,
    onAppDownload: () -> Unit,
    onBrowserDownload: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onAppDownload,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = texts.appDownload,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = onBrowserDownload,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = texts.browserDownload,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun UpdateDownloadProgress(
    texts: UiText,
    progress: DownloadProgress,
    onCancelDownload: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val fraction = progress.fraction
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = fraction,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = texts.downloadProgressMessage(progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onCancelDownload) {
                Text(texts.cancelDownload)
            }
        }
    }
}

@Composable
private fun SupportDialog(
    texts: UiText,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Color(0xFFE5E5E5))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SectionTitle(
                    icon = Icons.Rounded.Favorite,
                    title = texts.sponsorTitle
                )
                Text(
                    text = texts.sponsorIntro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    supportTargets.forEach { target ->
                        SupportTargetCard(
                            texts = texts,
                            target = target
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportTargetCard(
    texts: UiText,
    target: SupportTarget
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Color(0xFFE5E5E5))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = target.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            QrCodeView(
                value = target.value,
                contentDescription = texts.qrCodeFor(target.title)
            )
            when (target.type) {
                SupportTargetType.Link -> {
                    Text(
                        text = target.value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(
                        onClick = { openExternalLink(context, target.value, texts.linkUnavailable) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF151515),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        AppIcon(
                            icon = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(texts.openLink)
                    }
                }
                SupportTargetType.Wallet -> {
                    CopyableValueBox(
                        texts = texts,
                        label = target.title,
                        value = target.value
                    )
                }
            }
        }
    }
}

@Composable
private fun QrCodeView(
    value: String,
    contentDescription: String
) {
    val qrCode = remember(value) { QrCode.encode(value) }
    val quietZone = 4

    Canvas(
        modifier = Modifier
            .size(120.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE3E3E3), RoundedCornerShape(8.dp))
            .padding(8.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        drawRect(Color.White)
        val totalModules = qrCode.size + quietZone * 2
        val moduleSize = minOf(size.width, size.height) / totalModules
        val originX = (size.width - moduleSize * totalModules) / 2f
        val originY = (size.height - moduleSize * totalModules) / 2f
        for (y in 0 until qrCode.size) {
            for (x in 0 until qrCode.size) {
                if (qrCode.isDark(x, y)) {
                    drawRect(
                        color = Color(0xFF111111),
                        topLeft = Offset(
                            originX + (x + quietZone) * moduleSize,
                            originY + (y + quietZone) * moduleSize
                        ),
                        size = Size(moduleSize, moduleSize)
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyableValueBox(
    texts: UiText,
    label: String,
    value: String
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF6F7F8))
            .border(1.dp, Color(0xFFE2E4E8), RoundedCornerShape(8.dp))
            .clickable(
                onClickLabel = texts.copy,
                role = Role.Button,
                onClick = {
                    copyToClipboard(context, label, value, texts.copied)
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        AppIcon(
            icon = Icons.Rounded.ContentCopy,
            contentDescription = texts.copy,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AccentSwatch(
    texts: UiText,
    option: AccentColorOption,
    selected: Boolean,
    onSelected: () -> Unit
) {
    val label = texts.accentLabel(option)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(option.color)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) Color(0xFF111111) else Color(0xFFE2E2E2),
                    shape = CircleShape
                )
                .clickable(
                    onClickLabel = label,
                    role = Role.Button,
                    onClick = onSelected
                )
                .semantics {
                    contentDescription = label
                    role = Role.Button
                    this.selected = selected
                }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

@Composable
private fun ConversionLanes(
    texts: UiText,
    activeCategory: FileCategory,
    targetFor: (FileCategory) -> TargetFormat,
    onTargetChange: (FileCategory, TargetFormat) -> Unit,
    onActivate: (FileCategory) -> Unit,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onPickFiles: (FileCategory, TargetFormat) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = texts.chooseConversion,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        FileCategory.entries.forEach { category ->
            ConversionLane(
                texts = texts,
                category = category,
                selected = category == activeCategory,
                targetFormat = targetFor(category),
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onTargetChange = {
                    onActivate(category)
                    onTargetChange(category, it)
                },
                onActivate = { onActivate(category) },
                onPickFiles = { onPickFiles(category, targetFor(category)) }
            )
        }
    }
}

@Composable
private fun ConversionLane(
    texts: UiText,
    category: FileCategory,
    selected: Boolean,
    targetFormat: TargetFormat,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onTargetChange: (TargetFormat) -> Unit,
    onActivate: () -> Unit,
    onPickFiles: () -> Unit
) {
    QuietPanel(
        borderColor = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFE7E7E7)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    icon = category.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = texts.categoryLabel(category),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = texts.categoryPurpose(category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            EngineHint(texts.engineHint(targetFormat.modeHint))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                FormatDropdown(
                    label = texts.target,
                    selected = targetFormat,
                    options = category.formats,
                    texts = texts,
                    expanded = openMenuId == "format-${category.name}",
                    onExpandedChange = { expanded ->
                        onActivate()
                        onOpenMenuChange(if (expanded) "format-${category.name}" else null)
                    },
                    onSelected = onTargetChange
                )
            }
            Button(
                onClick = onPickFiles,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                AppIcon(
                    icon = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(texts.select)
            }
        }
    }
}

@Composable
private fun EncodingPanel(
    texts: UiText,
    category: FileCategory,
    videoResolution: String,
    videoBitrate: String,
    videoCodec: String,
    videoCodecOptions: List<String>,
    videoFrameRate: String,
    videoTarget: TargetFormat,
    audioBitrate: String,
    audioSampleRate: String,
    audioChannels: String,
    videoAdvanced: VideoAdvancedUiState,
    audioAdvanced: AudioAdvancedUiState,
    audioTarget: TargetFormat,
    imageTarget: TargetFormat,
    pdfTarget: TargetFormat,
    documentTarget: TargetFormat,
    imageQuality: String,
    pdfPageMode: String,
    pdfRenderQuality: String,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onVideoResolutionChange: (String) -> Unit,
    onVideoBitrateChange: (String) -> Unit,
    onVideoCodecChange: (String) -> Unit,
    onVideoFrameRateChange: (String) -> Unit,
    onAudioBitrateChange: (String) -> Unit,
    onAudioSampleRateChange: (String) -> Unit,
    onAudioChannelsChange: (String) -> Unit,
    onVideoAdvancedExpandedChange: (Boolean) -> Unit,
    onVideoReverseChange: (Boolean) -> Unit,
    onVideoFadeInChange: (String) -> Unit,
    onVideoFadeOutChange: (String) -> Unit,
    onVideoMirrorChange: (String) -> Unit,
    onVideoRotationChange: (String) -> Unit,
    onVideoAspectRatioChange: (String) -> Unit,
    onAudioAdvancedExpandedChange: (Boolean) -> Unit,
    onAudioReverseChange: (Boolean) -> Unit,
    onAudioFadeInChange: (String) -> Unit,
    onAudioFadeOutChange: (String) -> Unit,
    onAudioVolumeChange: (String) -> Unit,
    onAudioEchoChange: (String) -> Unit,
    onAudioNoiseReductionChange: (String) -> Unit,
    onImageQualityChange: (String) -> Unit,
    onPdfPageModeChange: (String) -> Unit,
    onPdfRenderQualityChange: (String) -> Unit
) {
    QuietPanel {
        Text(
            text = texts.optionsTitle(category),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = texts.presetNote(category),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        AnimatedContent(
            targetState = category,
            label = "EncodingPanelCategory"
        ) { targetCategory ->
            when (targetCategory) {
            FileCategory.Video -> VideoOptions(
                texts = texts,
                resolution = videoResolution,
                bitrate = videoBitrate,
                codec = videoCodec,
                codecOptions = videoCodecOptions,
                frameRate = videoFrameRate,
                audioBitrate = audioBitrate,
                audioSampleRate = audioSampleRate,
                audioChannels = audioChannels,
                videoAdvanced = videoAdvanced,
                audioAdvanced = audioAdvanced,
                targetFormat = videoTarget,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onResolutionChange = onVideoResolutionChange,
                onBitrateChange = onVideoBitrateChange,
                onCodecChange = onVideoCodecChange,
                onFrameRateChange = onVideoFrameRateChange,
                onAudioBitrateChange = onAudioBitrateChange,
                onAudioSampleRateChange = onAudioSampleRateChange,
                onAudioChannelsChange = onAudioChannelsChange,
                onVideoAdvancedExpandedChange = onVideoAdvancedExpandedChange,
                onVideoReverseChange = onVideoReverseChange,
                onVideoFadeInChange = onVideoFadeInChange,
                onVideoFadeOutChange = onVideoFadeOutChange,
                onVideoMirrorChange = onVideoMirrorChange,
                onVideoRotationChange = onVideoRotationChange,
                onVideoAspectRatioChange = onVideoAspectRatioChange,
                onAudioAdvancedExpandedChange = onAudioAdvancedExpandedChange,
                onAudioReverseChange = onAudioReverseChange,
                onAudioFadeInChange = onAudioFadeInChange,
                onAudioFadeOutChange = onAudioFadeOutChange,
                onAudioVolumeChange = onAudioVolumeChange,
                onAudioEchoChange = onAudioEchoChange,
                onAudioNoiseReductionChange = onAudioNoiseReductionChange
            )
            FileCategory.Audio -> AudioOptions(
                texts = texts,
                bitrate = audioBitrate,
                sampleRate = audioSampleRate,
                channels = audioChannels,
                advanced = audioAdvanced,
                targetFormat = audioTarget,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onBitrateChange = onAudioBitrateChange,
                onSampleRateChange = onAudioSampleRateChange,
                onChannelsChange = onAudioChannelsChange,
                onAdvancedExpandedChange = onAudioAdvancedExpandedChange,
                onReverseChange = onAudioReverseChange,
                onFadeInChange = onAudioFadeInChange,
                onFadeOutChange = onAudioFadeOutChange,
                onVolumeChange = onAudioVolumeChange,
                onEchoChange = onAudioEchoChange,
                onNoiseReductionChange = onAudioNoiseReductionChange
            )
            FileCategory.Image -> ImageOptions(
                texts = texts,
                targetFormat = imageTarget,
                quality = imageQuality,
                pdfPageMode = pdfPageMode,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onQualityChange = onImageQualityChange,
                onPdfPageModeChange = onPdfPageModeChange
            )
            FileCategory.Pdf -> PdfOptions(
                texts = texts,
                targetFormat = pdfTarget,
                renderQuality = pdfRenderQuality,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onRenderQualityChange = onPdfRenderQualityChange
            )
            FileCategory.Document -> Text(
                text = texts.optionValue(documentTarget.modeHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    }
}

@Composable
private fun VideoOptions(
    texts: UiText,
    resolution: String,
    bitrate: String,
    codec: String,
    codecOptions: List<String>,
    frameRate: String,
    audioBitrate: String,
    audioSampleRate: String,
    audioChannels: String,
    videoAdvanced: VideoAdvancedUiState,
    audioAdvanced: AudioAdvancedUiState,
    targetFormat: TargetFormat,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onResolutionChange: (String) -> Unit,
    onBitrateChange: (String) -> Unit,
    onCodecChange: (String) -> Unit,
    onFrameRateChange: (String) -> Unit,
    onAudioBitrateChange: (String) -> Unit,
    onAudioSampleRateChange: (String) -> Unit,
    onAudioChannelsChange: (String) -> Unit,
    onVideoAdvancedExpandedChange: (Boolean) -> Unit,
    onVideoReverseChange: (Boolean) -> Unit,
    onVideoFadeInChange: (String) -> Unit,
    onVideoFadeOutChange: (String) -> Unit,
    onVideoMirrorChange: (String) -> Unit,
    onVideoRotationChange: (String) -> Unit,
    onVideoAspectRatioChange: (String) -> Unit,
    onAudioAdvancedExpandedChange: (Boolean) -> Unit,
    onAudioReverseChange: (Boolean) -> Unit,
    onAudioFadeInChange: (String) -> Unit,
    onAudioFadeOutChange: (String) -> Unit,
    onAudioVolumeChange: (String) -> Unit,
    onAudioEchoChange: (String) -> Unit,
    onAudioNoiseReductionChange: (String) -> Unit
) {
    val isGifTarget = targetFormat.extension.equals("gif", ignoreCase = true)
    OptionGrid {
        OptionDropdown(
            "video-size",
            texts.resolution,
            resolution,
            if (isGifTarget) VIDEO_GIF_RESOLUTION_OPTIONS else VIDEO_RESOLUTION_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onResolutionChange
        )
        if (!isGifTarget) {
            OptionDropdown(
                "video-bitrate",
                texts.bitrate,
                bitrate,
                VIDEO_BITRATE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                onBitrateChange
            )
            OptionDropdown(
                "video-codec",
                texts.codec,
                codec,
                codecOptions,
                texts,
                openMenuId,
                onOpenMenuChange,
                onCodecChange
            )
            OptionDropdown(
                "video-frame-rate",
                texts.frameRate,
                frameRate,
                VIDEO_FRAME_RATE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                onFrameRateChange
            )
            OptionDropdown(
                "video-audio-bitrate",
                texts.audioBitrateLabel(),
                audioBitrate,
                AUDIO_BITRATE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                onAudioBitrateChange
            )
            OptionDropdown(
                "video-audio-sample-rate",
                texts.sampleRate,
                audioSampleRate,
                AUDIO_SAMPLE_RATE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                onAudioSampleRateChange
            )
            OptionDropdown(
                "video-audio-channels",
                texts.channels,
                audioChannels,
                AUDIO_CHANNEL_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                onAudioChannelsChange
            )
            VideoAdvancedOptionsPanel(
                texts = texts,
                state = videoAdvanced,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onExpandedChange = onVideoAdvancedExpandedChange,
                onReverseChange = onVideoReverseChange,
                onFadeInChange = onVideoFadeInChange,
                onFadeOutChange = onVideoFadeOutChange,
                onMirrorChange = onVideoMirrorChange,
                onRotationChange = onVideoRotationChange,
                onAspectRatioChange = onVideoAspectRatioChange
            )
            AudioAdvancedOptionsPanel(
                texts = texts,
                state = audioAdvanced,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onExpandedChange = onAudioAdvancedExpandedChange,
                onReverseChange = onAudioReverseChange,
                onFadeInChange = onAudioFadeInChange,
                onFadeOutChange = onAudioFadeOutChange,
                onVolumeChange = onAudioVolumeChange,
                onEchoChange = onAudioEchoChange,
                onNoiseReductionChange = onAudioNoiseReductionChange
            )
        }
    }
}

@Composable
private fun AudioOptions(
    texts: UiText,
    bitrate: String,
    sampleRate: String,
    channels: String,
    advanced: AudioAdvancedUiState,
    targetFormat: TargetFormat,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onBitrateChange: (String) -> Unit,
    onSampleRateChange: (String) -> Unit,
    onChannelsChange: (String) -> Unit,
    onAdvancedExpandedChange: (Boolean) -> Unit,
    onReverseChange: (Boolean) -> Unit,
    onFadeInChange: (String) -> Unit,
    onFadeOutChange: (String) -> Unit,
    onVolumeChange: (String) -> Unit,
    onEchoChange: (String) -> Unit,
    onNoiseReductionChange: (String) -> Unit
) {
    OptionGrid {
        if (audioSupportsBitrateOption(targetFormat)) {
            OptionDropdown(
                "audio-bitrate",
                texts.bitrate,
                bitrate,
                AUDIO_BITRATE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                onBitrateChange
            )
        } else {
            Text(
                text = texts.optionValue("Lossless output"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OptionDropdown(
            "audio-sample-rate",
            texts.sampleRate,
            sampleRate,
            AUDIO_SAMPLE_RATE_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onSampleRateChange
        )
        OptionDropdown(
            "audio-channels",
            texts.channels,
            channels,
            AUDIO_CHANNEL_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onChannelsChange
        )
        AudioAdvancedOptionsPanel(
            texts = texts,
            state = advanced,
            openMenuId = openMenuId,
            onOpenMenuChange = onOpenMenuChange,
            onExpandedChange = onAdvancedExpandedChange,
            onReverseChange = onReverseChange,
            onFadeInChange = onFadeInChange,
            onFadeOutChange = onFadeOutChange,
            onVolumeChange = onVolumeChange,
            onEchoChange = onEchoChange,
            onNoiseReductionChange = onNoiseReductionChange
        )
    }
}

@Composable
private fun VideoAdvancedOptionsPanel(
    texts: UiText,
    state: VideoAdvancedUiState,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onReverseChange: (Boolean) -> Unit,
    onFadeInChange: (String) -> Unit,
    onFadeOutChange: (String) -> Unit,
    onMirrorChange: (String) -> Unit,
    onRotationChange: (String) -> Unit,
    onAspectRatioChange: (String) -> Unit
) {
    AdvancedOptionsPanel(
        title = texts.videoAdvancedTitle(),
        note = texts.videoAdvancedNote(),
        expanded = state.expanded,
        onExpandedChange = onExpandedChange
    ) {
        AdvancedSwitchRow(
            label = texts.reverseLabel(),
            checked = state.reverse,
            onCheckedChange = onReverseChange
        )
        OptionDropdown(
            "video-advanced-fade-in",
            texts.fadeInLabel(),
            state.fadeIn,
            ADVANCED_FADE_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onFadeInChange
        )
        OptionDropdown(
            "video-advanced-fade-out",
            texts.fadeOutLabel(),
            state.fadeOut,
            ADVANCED_FADE_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onFadeOutChange
        )
        OptionDropdown(
            "video-advanced-mirror",
            texts.mirrorLabel(),
            state.mirror,
            VIDEO_MIRROR_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onMirrorChange
        )
        OptionDropdown(
            "video-advanced-rotation",
            texts.rotationLabel(),
            state.rotation,
            VIDEO_ROTATION_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onRotationChange
        )
        OptionDropdown(
            "video-advanced-aspect",
            texts.aspectRatioLabel(),
            state.aspectRatio,
            VIDEO_ASPECT_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onAspectRatioChange
        )
    }
}

@Composable
private fun AudioAdvancedOptionsPanel(
    texts: UiText,
    state: AudioAdvancedUiState,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onReverseChange: (Boolean) -> Unit,
    onFadeInChange: (String) -> Unit,
    onFadeOutChange: (String) -> Unit,
    onVolumeChange: (String) -> Unit,
    onEchoChange: (String) -> Unit,
    onNoiseReductionChange: (String) -> Unit
) {
    AdvancedOptionsPanel(
        title = texts.audioAdvancedTitle(),
        note = texts.audioAdvancedNote(),
        expanded = state.expanded,
        onExpandedChange = onExpandedChange
    ) {
        AdvancedSwitchRow(
            label = texts.reverseLabel(),
            checked = state.reverse,
            onCheckedChange = onReverseChange
        )
        OptionDropdown(
            "audio-advanced-fade-in",
            texts.fadeInLabel(),
            state.fadeIn,
            ADVANCED_FADE_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onFadeInChange
        )
        OptionDropdown(
            "audio-advanced-fade-out",
            texts.fadeOutLabel(),
            state.fadeOut,
            ADVANCED_FADE_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onFadeOutChange
        )
        OptionDropdown(
            "audio-advanced-volume",
            texts.volumeLabel(),
            state.volume,
            AUDIO_VOLUME_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onVolumeChange
        )
        OptionDropdown(
            "audio-advanced-echo",
            texts.echoLabel(),
            state.echo,
            AUDIO_ECHO_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onEchoChange
        )
        OptionDropdown(
            "audio-advanced-denoise",
            texts.noiseReductionLabel(),
            state.noiseReduction,
            AUDIO_DENOISE_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onNoiseReductionChange
        )
    }
}

@Composable
private fun AdvancedSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = if (checked) 0.22f else 0.08f),
                RoundedCornerShape(8.dp)
            )
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

@Composable
private fun AdvancedOptionsPanel(
    title: String,
    note: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.045f), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AppIcon(
                icon = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = 520f)),
            exit = fadeOut() + shrinkVertically(animationSpec = spring(stiffness = 620f))
        ) {
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
private fun ImageOptions(
    texts: UiText,
    targetFormat: TargetFormat,
    quality: String,
    pdfPageMode: String,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onQualityChange: (String) -> Unit,
    onPdfPageModeChange: (String) -> Unit
) {
    if (targetFormat.extension.equals("pdf", ignoreCase = true)) {
        OptionGrid {
            OptionDropdown(
                "image-pdf-page-mode",
                texts.pageSize,
                pdfPageMode,
                PDF_PAGE_MODE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                onPdfPageModeChange
            )
        }
        return
    }

    if (
        targetFormat.extension.equals("png", ignoreCase = true) ||
        targetFormat.extension.equals("ico", ignoreCase = true)
    ) {
        Text(
            text = texts.optionValue("Lossless output"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    OptionGrid {
        val qualityOptions = imageQualityOptionsFor(targetFormat)
        val selectedQuality = if (quality in qualityOptions) quality else IMAGE_QUALITY_BALANCED
        OptionDropdown(
            "image-quality",
            texts.quality,
            selectedQuality,
            qualityOptions,
            texts,
            openMenuId,
            onOpenMenuChange,
            onQualityChange
        )
    }
}

@Composable
private fun PdfOptions(
    texts: UiText,
    targetFormat: TargetFormat,
    renderQuality: String,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onRenderQualityChange: (String) -> Unit
) {
    if (
        targetFormat.extension.equals("pdf", ignoreCase = true) ||
        targetFormat.extension.equals("txt", ignoreCase = true) ||
        targetFormat.extension.equals("md", ignoreCase = true)
    ) {
        Text(
            text = texts.optionValue(targetFormat.modeHint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    OptionGrid {
        OptionDropdown(
            "pdf-render-quality",
            texts.renderQuality,
            renderQuality,
            PDF_RENDER_QUALITY_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onRenderQualityChange
        )
    }
}

@Composable
private fun OptionGrid(content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun OutputPanel(
    texts: UiText,
    outputLocationMode: OutputLocationMode,
    outputDirectory: OutputDirectory?,
    onOutputLocationModeChange: (OutputLocationMode) -> Unit,
    onPickOutputDirectory: () -> Unit
) {
    QuietPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    icon = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(23.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = texts.output,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = when (outputLocationMode) {
                            OutputLocationMode.Default -> texts.defaultOutputNote
                            OutputLocationMode.Custom ->
                                outputDirectory?.label ?: texts.chooseFolderBeforeConversion
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (outputLocationMode == OutputLocationMode.Default) {
                        Text(
                            text = texts.defaultOutputLocation,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (outputDirectory != null) {
                        Text(
                            text = if (outputDirectory.persistablePermissionSaved) {
                                texts.folderPermissionSaved
                            } else {
                                texts.folderSelectedForSession
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (outputLocationMode == OutputLocationMode.Default) {
                    Button(onClick = { onOutputLocationModeChange(OutputLocationMode.Default) }) {
                        Text(texts.defaultOutputLocation)
                    }
                    OutlinedButton(
                        onClick = {
                            onOutputLocationModeChange(OutputLocationMode.Custom)
                            if (outputDirectory == null) onPickOutputDirectory()
                        }
                    ) {
                        Text(texts.customOutputLocation)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onOutputLocationModeChange(OutputLocationMode.Default) }
                    ) {
                        Text(texts.defaultOutputLocation)
                    }
                    Button(onClick = onPickOutputDirectory) {
                        AppIcon(
                            icon = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(texts.chooseDirectory)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueActions(
    texts: UiText,
    hasFiles: Boolean,
    isRunning: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onCancel,
            enabled = hasFiles || isRunning,
            modifier = Modifier.weight(1f)
        ) {
            AppIcon(
                icon = Icons.Rounded.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(texts.cancelOrClearTasks)
        }
        Button(
            onClick = onStart,
            enabled = hasFiles && !isRunning,
            modifier = Modifier.weight(1f)
        ) {
            AppIcon(
                icon = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(texts.start)
        }
    }
}

@Composable
private fun FileQueue(
    texts: UiText,
    files: List<QueuedFile>,
    taskProgress: Map<String, TaskProgress>,
    canRemove: Boolean,
    onRemoveFile: (String) -> Unit
) {
    QuietPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = texts.queue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = texts.selectedCount(files.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (files.isEmpty()) {
            Text(
                text = texts.emptyQueue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.id }) { file ->
                    FileRow(
                        modifier = Modifier.animateItem(),
                        texts = texts,
                        file = file,
                        progress = taskProgress[file.id],
                        canRemove = canRemove,
                        onRemove = { onRemoveFile(file.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    modifier: Modifier = Modifier,
    texts: UiText,
    file: QueuedFile,
    progress: TaskProgress?,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val completedProgress = progress?.takeIf {
        it.status == TaskProgressStatus.Completed && it.outputUriList().isNotEmpty()
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .border(1.dp, Color(0xFFEAEAEA), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileInfoLine(
                        info = file.inputInfo,
                        texts = texts,
                        fallbackType = file.mimeType ?: texts.unknownType,
                        fallbackSizeBytes = file.sizeBytes
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (completedProgress != null) {
                    CompactTaskActionButton(
                        icon = Icons.Rounded.Share,
                        contentDescription = texts.shareOutput,
                        onClick = { shareOutput(context, completedProgress, texts) }
                    )
                    CompactTaskActionButton(
                        icon = Icons.Rounded.FolderOpen,
                        contentDescription = texts.openOutputLocation,
                        onClick = { openOutputLocation(context, completedProgress, texts) }
                    )
                }
                CompactTaskActionButton(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = texts.remove,
                    enabled = canRemove,
                    onClick = onRemove
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallTag(texts.categoryLabel(file.category))
            SmallTag(texts.toFormat(texts.optionValue(file.targetFormat)))
            SmallTag(texts.progressLabel(progress))
        }
        if (progress?.status == TaskProgressStatus.Completed && progress.outputInfo != null) {
            ResultInfoLine(
                text = formatResultInfoLine(file, progress.outputInfo, texts)
            )
        }
        if (progress?.status == TaskProgressStatus.Failed) {
            Text(
                text = texts.taskMessage(progress.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FormatDropdown(
    label: String,
    selected: TargetFormat,
    options: List<TargetFormat>,
    texts: UiText,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (TargetFormat) -> Unit
) {
    Column(horizontalAlignment = Alignment.End) {
        PillMenuButton(
            onClick = { onExpandedChange(!expanded) },
            text = "$label: ${texts.optionValue(selected.label)}",
            expanded = expanded
        )
        InlineDropdownPanel(
            expanded = expanded
        ) {
            options.forEach { option ->
                DropdownOption(
                    text = "${texts.optionValue(option.label)} / ${texts.engineHint(option.modeHint)}",
                    selected = option == selected,
                    onClick = {
                        onExpandedChange(false)
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun OptionDropdown(
    menuId: String,
    label: String,
    selected: String,
    options: List<String>,
    texts: UiText,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onSelected: (String) -> Unit
) {
    val expanded = openMenuId == menuId

    Column(horizontalAlignment = Alignment.End) {
        PillMenuButton(
            onClick = { onOpenMenuChange(if (expanded) null else menuId) },
            text = "$label: ${texts.optionValue(selected)}",
            expanded = expanded
        )
        InlineDropdownPanel(
            expanded = expanded
        ) {
            options.forEach { option ->
                DropdownOption(
                    text = texts.optionValue(option),
                    selected = option == selected,
                    onClick = {
                        onOpenMenuChange(null)
                        onSelected(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(
    icon: ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(
            icon = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(19.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PillMenuButton(
    onClick: () -> Unit,
    text: String,
    expanded: Boolean
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "dropdownArrow"
    )
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(100.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (expanded) Color(0xFFF3F3F3) else Color.White,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            1.dp,
            if (expanded) Color(0xFFD8D8D8) else Color(0xFFE2E2E2)
        )
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .rotate(rotation)
        )
    }
}

@Composable
private fun InlineDropdownPanel(
    expanded: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn(animationSpec = spring(stiffness = 520f)) + expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = spring(stiffness = 520f)
        ),
        exit = fadeOut(animationSpec = spring(stiffness = 620f)) + shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = spring(stiffness = 620f)
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Color(0xFFE4E4E4))
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content
            )
        }
    }
}

@Composable
private fun DropdownOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            AppIcon(
                icon = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun EngineHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
private fun SmallTag(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(Color(0xFFF5F5F5), RoundedCornerShape(100.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

@Composable
private fun ResultInfoLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    )
}

@Composable
private fun StatusLine(
    text: String,
    isError: Boolean = false
) {
    val color = if (isError) Color(0xFFB3261E) else MaterialTheme.colorScheme.primary
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    )
}

@Composable
private fun QuietPanel(
    borderColor: Color = Color(0xFFE7E7E7),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
private fun AppIcon(
    icon: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
    )
}

private fun FileCategory.icon(): ImageVector {
    return when (this) {
        FileCategory.Video -> Icons.Rounded.Videocam
        FileCategory.Audio -> Icons.Rounded.AudioFile
        FileCategory.Image -> Icons.Rounded.Image
        FileCategory.Pdf -> Icons.Rounded.PictureAsPdf
        FileCategory.Document -> Icons.Rounded.Description
    }
}

private fun zenConverterColorScheme(accent: AccentColorOption): ColorScheme {
    return lightColorScheme(
        primary = accent.color,
        onPrimary = accent.contentColor,
        secondary = accent.color,
        onSecondary = accent.contentColor,
        background = Color.White,
        onBackground = Color(0xFF111111),
        surface = Color.White,
        onSurface = Color(0xFF111111),
        surfaceVariant = Color(0xFFF6F6F6),
        onSurfaceVariant = Color(0xFF666666),
        outline = Color(0xFFD8D8D8)
    )
}

private fun resolveLanguage(option: LanguageOption): ResolvedLanguage {
    if (option == LanguageOption.English) return ResolvedLanguage.English
    if (option == LanguageOption.SimplifiedChinese) return ResolvedLanguage.SimplifiedChinese
    if (option == LanguageOption.TraditionalChinese) return ResolvedLanguage.TraditionalChinese

    val locale = Locale.getDefault()
    if (locale.language.equals("zh", ignoreCase = true)) {
        val country = locale.country.uppercase(Locale.US)
        val script = locale.script
        return if (
            script.equals("Hant", ignoreCase = true) ||
            country == "TW" ||
            country == "HK" ||
            country == "MO"
        ) {
            ResolvedLanguage.TraditionalChinese
        } else {
            ResolvedLanguage.SimplifiedChinese
        }
    }
    return ResolvedLanguage.English
}

private fun uiTextFor(language: ResolvedLanguage): UiText {
    return when (language) {
        ResolvedLanguage.English -> englishText
        ResolvedLanguage.SimplifiedChinese -> simplifiedChineseText
        ResolvedLanguage.TraditionalChinese -> traditionalChineseText
    }
}

private fun videoResolutionToShortSide(value: String): Int? {
    return when (value) {
        VIDEO_RESOLUTION_2160P -> 2160
        VIDEO_RESOLUTION_1440P -> 1440
        VIDEO_RESOLUTION_1080P -> 1080
        VIDEO_RESOLUTION_720P -> 720
        VIDEO_RESOLUTION_480P -> 480
        else -> null
    }
}

private fun videoBitrateToBits(value: String): Int? {
    return when (value) {
        VIDEO_BITRATE_LOW -> 1_000_000
        VIDEO_BITRATE_MEDIUM -> 2_500_000
        VIDEO_BITRATE_HIGH -> 5_000_000
        VIDEO_BITRATE_VERY_HIGH -> 8_000_000
        VIDEO_BITRATE_ULTRA -> 16_000_000
        else -> null
    }
}

private fun videoCodecToMimeType(value: String): String {
    return when (value) {
        VIDEO_CODEC_H265 -> VideoExportOptions.VIDEO_MIME_TYPE_H265
        else -> VideoExportOptions.VIDEO_MIME_TYPE_H264
    }
}

@Suppress("UNUSED_PARAMETER")
private fun defaultVideoCodecFor(supportedVideoMimeTypes: Set<String>): String {
    return VIDEO_CODEC_H264
}

private fun videoFrameRateToCap(value: String): Int? {
    return when (value) {
        VIDEO_FRAME_RATE_25 -> 25
        VIDEO_FRAME_RATE_30 -> 30
        VIDEO_FRAME_RATE_60 -> 60
        else -> null
    }
}

private fun videoCodecOptionsFor(supportedVideoMimeTypes: Set<String>): List<String> {
    return buildList {
        add(VIDEO_CODEC_H264)
        if (VideoExportOptions.VIDEO_MIME_TYPE_H265 in supportedVideoMimeTypes) {
            add(VIDEO_CODEC_H265)
        }
    }
}

private fun audioBitrateToBits(value: String): Int? {
    return when (value) {
        AUDIO_BITRATE_RECOMMENDED -> 192_000
        AUDIO_BITRATE_HIGH -> 256_000
        AUDIO_BITRATE_COMPACT -> 128_000
        AUDIO_BITRATE_VOICE -> 96_000
        else -> null
    }
}

private fun audioSampleRateToHz(value: String): Int? {
    return when (value) {
        AUDIO_SAMPLE_RATE_RECOMMENDED -> 48_000
        AUDIO_SAMPLE_RATE_44100 -> 44_100
        AUDIO_SAMPLE_RATE_32000 -> 32_000
        else -> null
    }
}

private fun audioChannelsToCount(value: String): Int? {
    return when (value) {
        AUDIO_CHANNELS_STEREO -> 2
        AUDIO_CHANNELS_MONO -> 1
        else -> null
    }
}

private fun fadeDurationSeconds(value: String): Float? {
    return when (value) {
        ADVANCED_FADE_HALF_SECOND -> 0.5f
        ADVANCED_FADE_ONE_SECOND -> 1f
        ADVANCED_FADE_TWO_SECONDS -> 2f
        else -> null
    }
}

private fun videoMirrorModeFor(value: String): VideoMirrorMode {
    return when (value) {
        VIDEO_MIRROR_HORIZONTAL -> VideoMirrorMode.Horizontal
        VIDEO_MIRROR_VERTICAL -> VideoMirrorMode.Vertical
        VIDEO_MIRROR_BOTH -> VideoMirrorMode.Both
        else -> VideoMirrorMode.Off
    }
}

private fun videoRotationModeFor(value: String): VideoRotationMode {
    return when (value) {
        VIDEO_ROTATION_90_CW -> VideoRotationMode.Clockwise90
        VIDEO_ROTATION_90_CCW -> VideoRotationMode.CounterClockwise90
        VIDEO_ROTATION_180 -> VideoRotationMode.Rotate180
        else -> VideoRotationMode.None
    }
}

private fun videoAspectRatioModeFor(value: String): VideoAspectRatioMode {
    return when (value) {
        VIDEO_ASPECT_FIT_16_9 -> VideoAspectRatioMode.Fit16By9
        VIDEO_ASPECT_FIT_9_16 -> VideoAspectRatioMode.Fit9By16
        VIDEO_ASPECT_FIT_1_1 -> VideoAspectRatioMode.Fit1By1
        VIDEO_ASPECT_CROP_16_9 -> VideoAspectRatioMode.Crop16By9
        VIDEO_ASPECT_CROP_9_16 -> VideoAspectRatioMode.Crop9By16
        VIDEO_ASPECT_CROP_1_1 -> VideoAspectRatioMode.Crop1By1
        else -> VideoAspectRatioMode.Keep
    }
}

private fun audioVolumeModeFor(value: String): AudioVolumeMode {
    return when (value) {
        AUDIO_VOLUME_MUTE -> AudioVolumeMode.Mute
        AUDIO_VOLUME_50 -> AudioVolumeMode.Half
        AUDIO_VOLUME_150 -> AudioVolumeMode.OneAndHalf
        AUDIO_VOLUME_200 -> AudioVolumeMode.Double
        else -> AudioVolumeMode.Original
    }
}

private fun audioEchoModeFor(value: String): AudioEchoMode {
    return when (value) {
        AUDIO_ECHO_LIGHT -> AudioEchoMode.Light
        AUDIO_ECHO_ROOM -> AudioEchoMode.Room
        else -> AudioEchoMode.Off
    }
}

private fun audioNoiseReductionModeFor(value: String): AudioNoiseReductionMode {
    return when (value) {
        AUDIO_DENOISE_LIGHT -> AudioNoiseReductionMode.Light
        AUDIO_DENOISE_STANDARD -> AudioNoiseReductionMode.Standard
        else -> AudioNoiseReductionMode.Off
    }
}

private fun audioSupportsBitrateOption(targetFormat: TargetFormat): Boolean {
    return targetFormat.extension.lowercase(Locale.US) !in AUDIO_LOSSLESS_OUTPUT_EXTENSIONS
}

private fun imageQualityOptionsFor(targetFormat: TargetFormat): List<String> {
    return if (supportsWebpLosslessQuality(targetFormat)) {
        listOf(
            IMAGE_QUALITY_LOSSLESS,
            IMAGE_QUALITY_ORIGINAL,
            IMAGE_QUALITY_HIGH,
            IMAGE_QUALITY_BALANCED,
            IMAGE_QUALITY_SMALL
        )
    } else {
        IMAGE_QUALITY_OPTIONS
    }
}

@Composable
private fun CompactTaskActionButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(34.dp)
    ) {
        AppIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(18.dp)
        )
    }
}

private fun supportsWebpLosslessQuality(targetFormat: TargetFormat): Boolean {
    return targetFormat.extension.equals("webp", ignoreCase = true) &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
}

private fun imageQualityToPercent(value: String): Int {
    return when (value) {
        IMAGE_QUALITY_LOSSLESS -> 100
        IMAGE_QUALITY_ORIGINAL -> 100
        IMAGE_QUALITY_HIGH -> 95
        IMAGE_QUALITY_BALANCED -> 85
        IMAGE_QUALITY_SMALL -> 60
        else -> 85
    }
}

private fun pdfPageModeToOption(value: String): PdfImagePageMode {
    return when (value) {
        PDF_PAGE_MODE_ORIGINAL_RATIO -> PdfImagePageMode.OriginalRatio
        else -> PdfImagePageMode.A4Fit
    }
}

private fun pdfRenderQualityToOption(value: String): PdfRenderQuality {
    return when (value) {
        PDF_RENDER_QUALITY_LOW -> PdfRenderQuality.LowResolution
        PDF_RENDER_QUALITY_HIGH -> PdfRenderQuality.HighDetail
        else -> PdfRenderQuality.Balanced
    }
}

private val AUDIO_LOSSLESS_OUTPUT_EXTENSIONS = setOf("wav", "flac")

private data class UiText(
    val tagline: String,
    val openAbout: String,
    val closeAbout: String,
    val openSettings: String,
    val closeSettings: String,
    val appLogo: String,
    val appVersion: String,
    val appLicense: String,
    val aboutDescription: String,
    val githubRepository: String,
    val checkUpdates: String,
    val stableUpdateChannel: String,
    val previewUpdateChannel: String,
    val checkingUpdates: String,
    val appDownload: String,
    val browserDownload: String,
    val downloadComplete: String,
    val openDownloadedApk: String,
    val downloadFailed: String,
    val cancelDownload: String,
    val downloadCancelled: String,
    val installPermissionRequired: String,
    val apkOpenFailed: String,
    val supportDevelopment: String,
    val sponsorTitle: String,
    val sponsorIntro: String,
    val openLink: String,
    val copy: String,
    val copied: String,
    val linkUnavailable: String,
    val accentColor: String,
    val language: String,
    val chooseConversion: String,
    val target: String,
    val select: String,
    val output: String,
    val choose: String,
    val chooseDirectory: String,
    val chooseFolderBeforeConversion: String,
    val defaultOutputLocation: String,
    val defaultOutputNote: String,
    val customOutputLocation: String,
    val storagePermissionRequired: String,
    val folderPermissionSaved: String,
    val folderSelectedForSession: String,
    val start: String,
    val cancel: String,
    val cancelOrClearTasks: String,
    val queue: String,
    val selectedSuffix: String,
    val emptyQueue: String,
    val unknownType: String,
    val unknownSize: String,
    val remove: String,
    val shareOutput: String,
    val openOutputLocation: String,
    val outputUnavailable: String,
    val shareOutputFailed: String,
    val openOutputFailed: String,
    val waiting: String,
    val processing: String,
    val flowComplete: String,
    val flowCompleteNoFiles: String,
    val cancelled: String,
    val failed: String,
    val quality: String,
    val pageSize: String,
    val renderQuality: String,
    val resolution: String,
    val bitrate: String,
    val codec: String,
    val frameRate: String,
    val sampleRate: String,
    val channels: String,
    val outputMode: String,
    val password: String,
    val skip: String,
    val imagePdfPromptTitle: String,
    val gifFramePromptTitle: String,
    val gifPdfFramePromptTitle: String,
    val pdfPasswordTitle: String,
    val pdfOutputPasswordTitle: String,
    val uiPresetSuffix: String,
    val toPrefix: String
) {
    fun selectedCount(count: Int): String = "$count $selectedSuffix"

    fun fileCountLabel(count: Int): String {
        return when (this) {
            englishText -> if (count == 1) "1 file" else "$count files"
            simplifiedChineseText -> "$count 个文件"
            else -> "$count 個檔案"
        }
    }

    fun pageCountLabel(count: Int): String {
        return when (this) {
            englishText -> if (count == 1) "1 page" else "$count pages"
            simplifiedChineseText -> "$count 页"
            else -> "$count 頁"
        }
    }

    fun frameRateLabel(value: String): String {
        return when (this) {
            englishText -> "Frame rate $value"
            simplifiedChineseText -> "帧率 $value"
            else -> "幀率 $value"
        }
    }

    fun bitrateLabel(value: String): String {
        return when (this) {
            englishText -> "Overall bitrate $value"
            simplifiedChineseText -> "总码率 $value"
            else -> "總碼率 $value"
        }
    }

    fun audioBitrateLabel(): String {
        return when (this) {
            englishText -> "Audio bitrate"
            simplifiedChineseText -> "音频码率"
            else -> "音訊位元率"
        }
    }

    fun videoAdvancedTitle(): String {
        return when (this) {
            englishText -> "Advanced video"
            simplifiedChineseText -> "视频高级处理"
            else -> "影片進階處理"
        }
    }

    fun videoAdvancedNote(): String {
        return when (this) {
            englishText -> "Short reverse, fade, mirror, rotate, and frame shape"
            simplifiedChineseText -> "短视频倒放、淡入淡出、镜像、旋转、画幅"
            else -> "短影片倒放、淡入淡出、鏡像、旋轉、畫幅"
        }
    }

    fun audioAdvancedTitle(): String {
        return when (this) {
            englishText -> "Advanced audio"
            simplifiedChineseText -> "音频高级处理"
            else -> "音訊進階處理"
        }
    }

    fun audioAdvancedNote(): String {
        return when (this) {
            englishText -> "Reverse, denoise, fade, volume, mute, and echo"
            simplifiedChineseText -> "倒放、降噪、淡入淡出、音量、静音、回音"
            else -> "倒放、降噪、淡入淡出、音量、靜音、回音"
        }
    }

    fun reverseLabel(): String {
        return when (this) {
            englishText -> "Reverse playback"
            simplifiedChineseText -> "倒放"
            else -> "倒放"
        }
    }

    fun fadeInLabel(): String {
        return when (this) {
            englishText -> "Fade in"
            simplifiedChineseText -> "淡入"
            else -> "淡入"
        }
    }

    fun fadeOutLabel(): String {
        return when (this) {
            englishText -> "Fade out"
            simplifiedChineseText -> "淡出"
            else -> "淡出"
        }
    }

    fun mirrorLabel(): String {
        return when (this) {
            englishText -> "Mirror"
            simplifiedChineseText -> "镜像"
            else -> "鏡像"
        }
    }

    fun rotationLabel(): String {
        return when (this) {
            englishText -> "Rotate"
            simplifiedChineseText -> "旋转"
            else -> "旋轉"
        }
    }

    fun aspectRatioLabel(): String {
        return when (this) {
            englishText -> "Frame"
            simplifiedChineseText -> "画幅"
            else -> "畫幅"
        }
    }

    fun volumeLabel(): String {
        return when (this) {
            englishText -> "Volume"
            simplifiedChineseText -> "音量"
            else -> "音量"
        }
    }

    fun echoLabel(): String {
        return when (this) {
            englishText -> "Echo"
            simplifiedChineseText -> "回音"
            else -> "回音"
        }
    }

    fun noiseReductionLabel(): String {
        return when (this) {
            englishText -> "Noise reduction"
            simplifiedChineseText -> "声音降噪"
            else -> "聲音降噪"
        }
    }

    fun qrCodeFor(value: String): String {
        return when (this) {
            englishText -> "QR code for $value"
            simplifiedChineseText -> "$value 的二维码"
            else -> "$value 的 QR Code"
        }
    }

    fun imagePdfPromptMessage(count: Int): String {
        return when (this) {
            englishText -> "Create one PDF from $count selected images, or one PDF per image?"
            simplifiedChineseText -> "已选择 $count 张图片。要合并成一个 PDF，还是每张图片各生成一个 PDF？"
            else -> "已選擇 $count 張圖片。要合併成一個 PDF，還是每張圖片各生成一個 PDF？"
        }
    }

    fun gifFramePromptMessage(count: Int): String {
        return when (this) {
            englishText -> {
                val label = if (count == 1) "GIF" else "$count GIFs"
                "Convert only the first frame, or split $label into numbered frame files?"
            }
            simplifiedChineseText ->
                "已选择 $count 个 GIF。只转换首帧，还是拆成按顺序编号的帧文件？"
            else ->
                "已選擇 $count 個 GIF。只轉換首幀，還是拆成按順序編號的幀檔案？"
        }
    }

    fun gifPdfFramePromptMessage(count: Int): String {
        return when (this) {
            englishText -> {
                val label = if (count == 1) "this GIF" else "each GIF"
                "For split GIF output, put all frames from $label into one PDF, or create one PDF per frame?"
            }
            simplifiedChineseText ->
                "GIF 拆帧输出到 PDF 时，全部帧放进一个 PDF，还是每帧生成一个 PDF？"
            else ->
                "GIF 拆幀輸出到 PDF 時，全部幀放進一個 PDF，還是每幀產生一個 PDF？"
        }
    }

    fun pdfPasswordMessage(fileName: String): String {
        return when (this) {
            englishText -> "$fileName is password-protected. Enter the password to add it."
            simplifiedChineseText -> "$fileName 受密码保护。输入密码后再加入队列。"
            else -> "$fileName 受密碼保護。輸入密碼後再加入佇列。"
        }
    }

    fun pdfOutputPasswordMessage(fileCount: Int): String {
        return when (this) {
            englishText -> {
                val label = if (fileCount == 1) "this PDF" else "$fileCount PDFs"
                "Set the password required to open $label after export."
            }
            simplifiedChineseText ->
                "设置导出后打开这 $fileCount 个 PDF 所需的密码。"
            else ->
                "設定匯出後開啟這 $fileCount 個 PDF 所需的密碼。"
        }
    }

    fun summaryMessage(value: String): String {
        return when (value) {
            "Processing", "Compatibility processing" -> runningKeepAwakeMessage(processing)
            "Saving" -> runningKeepAwakeMessage(
                when (this) {
                    englishText -> "Saving"
                    simplifiedChineseText -> "保存中"
                    else -> "儲存中"
                }
            )
            else -> taskMessage(value)
        }
    }

    private fun runningKeepAwakeMessage(action: String): String {
        return when (this) {
            englishText -> "$action · Keeping the screen on; keep ZenConverter in the foreground"
            simplifiedChineseText -> "$action · 已保持屏幕常亮，请让 ZenConverter 留在前台"
            else -> "$action · 已保持螢幕常亮，請讓 ZenConverter 留在前台"
        }
    }

    fun taskMessage(value: String): String {
        return when (value) {
            "Processing", "Saving", "Compatibility processing" -> processing
            "Flow complete" -> flowComplete
            "Flow complete, no files created" -> flowCompleteNoFiles
            "Conversion complete" -> flowComplete
            "Conversion failed" -> failed
            "Choose output folder first" -> chooseFolderBeforeConversion
            "Only MP4 video is connected" -> failed
            "Only video MP4 and audio M4A are connected" -> failed
            "Only video MP4, audio M4A, and JPG/PNG/WEBP images are connected" -> failed
            "Only video MP4, audio MP3/M4A/WAV/FLAC/WMA, and JPG/PNG/WEBP images are connected" -> failed
            "Only connected video, audio, image, and PDF targets can run" -> failed
            "Only connected video, audio, image, PDF, and document targets can run" -> failed
            "Password-protected PDFs need Android 15 or PDF extension 13" -> when (this) {
                englishText -> "Password-protected PDFs need Android 15 or PDF extension 13"
                simplifiedChineseText -> "受密码保护的 PDF 需要 Android 15 或 PDF 扩展 13"
                else -> "受密碼保護的 PDF 需要 Android 15 或 PDF 擴充 13"
            }
            "Password-protected PDF was skipped" -> when (this) {
                englishText -> "Password-protected PDF was skipped"
                simplifiedChineseText -> "已跳过受密码保护的 PDF"
                else -> "已略過受密碼保護的 PDF"
            }
            "PDF encryption was skipped" -> when (this) {
                englishText -> "PDF encryption was skipped"
                simplifiedChineseText -> "已跳过 PDF 加密"
                else -> "已略過 PDF 加密"
            }
            "PDF password was empty" -> when (this) {
                englishText -> "PDF password was empty"
                simplifiedChineseText -> "PDF 密码不能为空"
                else -> "PDF 密碼不能為空"
            }
            "PDF password was incorrect or unsupported" -> when (this) {
                englishText -> "PDF password was incorrect or unsupported"
                simplifiedChineseText -> "PDF 密码不正确，或该保护方式不受支持"
                else -> "PDF 密碼不正確，或該保護方式不受支援"
            }
            "Password-protected or unsupported PDF security" -> when (this) {
                englishText -> "Password-protected or unsupported PDF security"
                simplifiedChineseText -> "PDF 受密码保护，或使用了不受支持的安全方式"
                else -> "PDF 受密碼保護，或使用了不受支援的安全方式"
            }
            "PDF has no pages" -> when (this) {
                englishText -> "PDF has no pages"
                simplifiedChineseText -> "PDF 没有可渲染页面"
                else -> "PDF 沒有可渲染頁面"
            }
            "Not enough cache space for this PDF" -> when (this) {
                englishText -> "Not enough cache space for this PDF"
                simplifiedChineseText -> "缓存空间不足，无法处理这个 PDF"
                else -> "快取空間不足，無法處理這個 PDF"
            }
            "Select at least two PDFs to merge" -> when (this) {
                englishText -> "Select at least two PDFs to merge"
                simplifiedChineseText -> "请选择至少两个 PDF 来合并"
                else -> "請選擇至少兩個 PDF 來合併"
            }
            "PDF has no selectable text; OCR is not included" -> when (this) {
                englishText -> "PDF has no selectable text; OCR is not included"
                simplifiedChineseText -> "PDF 没有可选择文本；当前不包含 OCR"
                else -> "PDF 沒有可選取文字；目前不包含 OCR"
            }
            "PDF merge failed" -> when (this) {
                englishText -> "PDF merge failed"
                simplifiedChineseText -> "PDF 合并失败"
                else -> "PDF 合併失敗"
            }
            "PDF text extraction failed" -> when (this) {
                englishText -> "PDF text extraction failed"
                simplifiedChineseText -> "PDF 文本提取失败"
                else -> "PDF 文字提取失敗"
            }
            "PDF markdown export failed" -> when (this) {
                englishText -> "PDF markdown export failed"
                simplifiedChineseText -> "PDF Markdown 导出失败"
                else -> "PDF Markdown 匯出失敗"
            }
            "PDF encryption failed" -> when (this) {
                englishText -> "PDF encryption failed"
                simplifiedChineseText -> "PDF 加密失败"
                else -> "PDF 加密失敗"
            }
            "PDF decryption failed" -> when (this) {
                englishText -> "PDF decryption failed"
                simplifiedChineseText -> "PDF 解密失败"
                else -> "PDF 解密失敗"
            }
            "PDF conversion failed" -> when (this) {
                englishText -> "PDF conversion failed"
                simplifiedChineseText -> "PDF 转换失败"
                else -> "PDF 轉換失敗"
            }
            "Unsupported Office document" -> when (this) {
                englishText -> "Unsupported Office document"
                simplifiedChineseText -> "不支持这个 Office 文档"
                else -> "不支援這個 Office 文件"
            }
            "Input file is empty" -> when (this) {
                englishText -> "Input file is empty"
                simplifiedChineseText -> "输入文件为空"
                else -> "輸入檔案為空"
            }
            "Office converter is only available on arm64-v8a devices" -> when (this) {
                englishText -> "Office converter is only available on arm64-v8a devices"
                simplifiedChineseText -> "Office 转 PDF 暂时仅支持 arm64-v8a 设备"
                else -> "Office 轉 PDF 暫時僅支援 arm64-v8a 裝置"
            }
            "Office converter could not start on this device" -> when (this) {
                englishText -> "Office converter could not start on this device"
                simplifiedChineseText -> "Office 转 PDF 引擎无法在此设备启动"
                else -> "Office 轉 PDF 引擎無法在此裝置啟動"
            }
            "Office file is too large for this experimental converter" -> when (this) {
                englishText -> "Office file is too large for this experimental converter"
                simplifiedChineseText -> "Office 文件过大，超过当前实验转换上限"
                else -> "Office 檔案過大，超過目前實驗轉換上限"
            }
            "Office conversion failed" -> when (this) {
                englishText -> "Office conversion failed"
                simplifiedChineseText -> "Office 转 PDF 失败"
                else -> "Office 轉 PDF 失敗"
            }
            "Could not open this PDF" -> when (this) {
                englishText -> "Could not open this PDF"
                simplifiedChineseText -> "无法打开这个 PDF"
                else -> "無法開啟這個 PDF"
            }
            "Could not read this PDF" -> when (this) {
                englishText -> "Could not read this PDF"
                simplifiedChineseText -> "无法读取这个 PDF"
                else -> "無法讀取這個 PDF"
            }
            "Default output needs storage permission on this Android version" -> storagePermissionRequired
            "Input file could not be opened" -> when (this) {
                englishText -> "Input file could not be opened"
                simplifiedChineseText -> "无法打开输入文件"
                else -> "無法開啟輸入檔案"
            }
            "Input file permission was lost" -> when (this) {
                englishText -> "Input file permission was lost"
                simplifiedChineseText -> "输入文件权限已失效"
                else -> "輸入檔案權限已失效"
            }
            "Could not save output file" -> when (this) {
                englishText -> "Could not save output file"
                simplifiedChineseText -> "无法保存输出文件"
                else -> "無法儲存輸出檔案"
            }
            "Compatibility engine failed before export" -> when (this) {
                englishText -> "Compatibility engine failed before export"
                simplifiedChineseText -> "兼容引擎启动导出前失败"
                else -> "相容引擎啟動匯出前失敗"
            }
            "Compatibility engine could not open SAF input" -> when (this) {
                englishText -> "Compatibility engine could not open SAF input"
                simplifiedChineseText -> "兼容引擎无法打开 SAF 输入文件"
                else -> "相容引擎無法開啟 SAF 輸入檔案"
            }
            "Compatibility engine could not transcode this file to MP4" -> when (this) {
                englishText -> "Compatibility engine could not transcode this file to MP4"
                simplifiedChineseText -> "兼容引擎无法把这个文件转码为 MP4"
                else -> "相容引擎無法把這個檔案轉碼為 MP4"
            }
            "Compatibility engine could not transcode this file to MKV" -> when (this) {
                englishText -> "Compatibility engine could not transcode this file to MKV"
                simplifiedChineseText -> "兼容引擎无法把这个文件转码为 MKV"
                else -> "相容引擎無法把這個檔案轉碼為 MKV"
            }
            "Compatibility engine could not transcode this file to MOV" -> when (this) {
                englishText -> "Compatibility engine could not transcode this file to MOV"
                simplifiedChineseText -> "兼容引擎无法把这个文件转码为 MOV"
                else -> "相容引擎無法把這個檔案轉碼為 MOV"
            }
            "Compatibility engine could not extract AAC M4A audio" -> when (this) {
                englishText -> "Compatibility engine could not extract AAC M4A audio"
                simplifiedChineseText -> "兼容引擎无法提取 AAC/M4A 音频"
                else -> "相容引擎無法提取 AAC/M4A 音訊"
            }
            "Compatibility engine could not convert this audio" -> when (this) {
                englishText -> "Compatibility engine could not convert this audio"
                simplifiedChineseText -> "兼容引擎无法转换这段音频"
                else -> "相容引擎無法轉換這段音訊"
            }
            "Compatibility engine cannot encode this audio format yet" -> when (this) {
                englishText -> "Compatibility engine cannot encode this audio format yet"
                simplifiedChineseText -> "当前兼容包暂时不能编码这个音频格式"
                else -> "目前相容包暫時不能編碼這個音訊格式"
            }
            "Compatibility engine cannot encode this video format yet" -> when (this) {
                englishText -> "Compatibility engine cannot encode this video format yet"
                simplifiedChineseText -> "当前兼容包暂时不能编码这个视频格式"
                else -> "目前相容包暫時不能編碼這個影片格式"
            }
            "Compatibility engine needs an MP3-capable FFmpeg package" -> when (this) {
                englishText -> "Compatibility engine needs an MP3-capable FFmpeg package"
                simplifiedChineseText -> "当前兼容包不包含 MP3 编码器"
                else -> "目前相容包不包含 MP3 編碼器"
            }
            "Compatibility engine needs an H.264-capable FFmpeg package" -> when (this) {
                englishText -> "Compatibility engine needs an H.264-capable FFmpeg package"
                simplifiedChineseText -> "当前兼容包不包含 H.264 编码器"
                else -> "目前相容包不包含 H.264 編碼器"
            }
            "Compatibility engine needs an H.265-capable FFmpeg package" -> when (this) {
                englishText -> "Compatibility engine needs an H.265-capable FFmpeg package"
                simplifiedChineseText -> "当前兼容包不包含 H.265 编码器"
                else -> "目前相容包不包含 H.265 編碼器"
            }
            "Compatibility engine needs an AAC-capable FFmpeg package" -> when (this) {
                englishText -> "Compatibility engine needs an AAC-capable FFmpeg package"
                simplifiedChineseText -> "当前兼容包不包含 AAC 编码器"
                else -> "目前相容包不包含 AAC 編碼器"
            }
            "Compatibility engine needs a PCM WAV-capable FFmpeg package" -> when (this) {
                englishText -> "Compatibility engine needs a PCM WAV-capable FFmpeg package"
                simplifiedChineseText -> "当前兼容包不包含 PCM WAV 编码器"
                else -> "目前相容包不包含 PCM WAV 編碼器"
            }
            "Compatibility engine needs a FLAC-capable FFmpeg package" -> when (this) {
                englishText -> "Compatibility engine needs a FLAC-capable FFmpeg package"
                simplifiedChineseText -> "当前兼容包不包含 FLAC 编码器"
                else -> "目前相容包不包含 FLAC 編碼器"
            }
            "Compatibility engine needs a WMA-capable FFmpeg package" -> when (this) {
                englishText -> "Compatibility engine needs a WMA-capable FFmpeg package"
                simplifiedChineseText -> "当前兼容包不包含 WMA 编码器"
                else -> "目前相容包不包含 WMA 編碼器"
            }
            "Compatibility engine needs a GIF-capable FFmpeg package" -> when (this) {
                englishText -> "Compatibility engine needs a GIF-capable FFmpeg package"
                simplifiedChineseText -> "当前兼容包不包含 GIF 编码器"
                else -> "目前相容包不包含 GIF 編碼器"
            }
            "Compatibility engine needs duration metadata for fade out" -> when (this) {
                englishText -> "Compatibility engine needs duration metadata for fade out"
                simplifiedChineseText -> "淡出需要读取文件时长"
                else -> "淡出需要讀取檔案時長"
            }
            "Compatibility engine needs duration metadata for reverse playback" -> when (this) {
                englishText -> "Compatibility engine needs duration metadata for reverse playback"
                simplifiedChineseText -> "倒放需要读取文件时长"
                else -> "倒放需要讀取檔案時長"
            }
            "Compatibility engine supports reverse video up to 60 seconds" -> when (this) {
                englishText -> "Reverse video supports files up to 60 seconds"
                simplifiedChineseText -> "视频倒放暂时只支持 60 秒以内"
                else -> "影片倒放暫時只支援 60 秒以內"
            }
            "Compatibility engine needs video size metadata for reverse playback" -> when (this) {
                englishText -> "Reverse video needs readable video size metadata"
                simplifiedChineseText -> "视频倒放需要读取画面尺寸"
                else -> "影片倒放需要讀取畫面尺寸"
            }
            "Reverse video is only safe for very short low-resolution clips" -> when (this) {
                englishText -> "Reverse video only supports very short low-resolution clips"
                simplifiedChineseText -> "视频倒放只适合很短的低分辨率片段"
                else -> "影片倒放只適合很短的低解析度片段"
            }
            "Compatibility engine needs reverse filters" -> when (this) {
                englishText -> "Compatibility engine needs reverse filters"
                simplifiedChineseText -> "当前兼容包缺少倒放滤镜"
                else -> "目前相容包缺少倒放濾鏡"
            }
            "Compatibility engine needs the audio denoise filter" -> when (this) {
                englishText -> "Compatibility engine needs the audio denoise filter"
                simplifiedChineseText -> "当前兼容包缺少声音降噪滤镜"
                else -> "目前相容包缺少聲音降噪濾鏡"
            }
            "Advanced video settings produced an unsupported frame size" -> when (this) {
                englishText -> "Advanced video settings produced an unsupported frame size"
                simplifiedChineseText -> "高级画面设置生成了不支持的尺寸"
                else -> "進階畫面設定產生了不支援的尺寸"
            }
            "Compatibility engine is missing an advanced filter" -> when (this) {
                englishText -> "Compatibility engine is missing an advanced filter"
                simplifiedChineseText -> "当前兼容包缺少所选高级处理滤镜"
                else -> "目前相容包缺少所選進階處理濾鏡"
            }
            "Compatibility engine could not create this GIF" -> when (this) {
                englishText -> "Compatibility engine could not create this GIF"
                simplifiedChineseText -> "兼容引擎无法生成这个 GIF"
                else -> "相容引擎無法產生這個 GIF"
            }
            "Compatibility engine could not write this audio container" -> when (this) {
                englishText -> "Compatibility engine could not write this audio container"
                simplifiedChineseText -> "兼容引擎无法写出这个音频容器"
                else -> "相容引擎無法寫出這個音訊容器"
            }
            "Compatibility engine could not write this video container" -> when (this) {
                englishText -> "Compatibility engine could not write this video container"
                simplifiedChineseText -> "兼容引擎无法写出这个视频容器"
                else -> "相容引擎無法寫出這個影片容器"
            }
            "Compatibility engine is not connected for images" -> failed
            "Image conversion failed" -> when (this) {
                englishText -> "Image conversion failed"
                simplifiedChineseText -> "图片转换失败"
                else -> "圖片轉換失敗"
            }
            "Image engine could not decode this input" -> when (this) {
                englishText -> "Image engine could not decode this input"
                simplifiedChineseText -> "无法解码这张图片"
                else -> "無法解碼這張圖片"
            }
            "Image engine could not split GIF frames" -> when (this) {
                englishText -> "Image engine could not split GIF frames"
                simplifiedChineseText -> "无法拆分这个 GIF 的帧"
                else -> "無法拆分這個 GIF 的幀"
            }
            "Image engine could not decode this ICO input" -> when (this) {
                englishText -> "Image engine could not decode this ICO input"
                simplifiedChineseText -> "无法解码这个 ICO 文件"
                else -> "無法解碼這個 ICO 檔案"
            }
            "Image engine only supports PNG-in-ICO input" -> when (this) {
                englishText -> "Image engine only supports PNG-in-ICO input"
                simplifiedChineseText -> "当前只支持 PNG-in-ICO 输入"
                else -> "目前只支援 PNG-in-ICO 輸入"
            }
            "Image engine could not write this output" -> when (this) {
                englishText -> "Image engine could not write this output"
                simplifiedChineseText -> "无法写出这个图片格式"
                else -> "無法寫出這個圖片格式"
            }
            "Cancelled" -> cancelled
            "Queued" -> waiting
            else -> value
        }
    }

    fun progressLabel(progress: TaskProgress?): String {
        if (progress == null) return waiting
        return when (progress.status) {
            TaskProgressStatus.Queued -> waiting
            TaskProgressStatus.Running -> "${processing} ${(progress.progress * 100).toInt()}%"
            TaskProgressStatus.Completed -> flowComplete
            TaskProgressStatus.Cancelled -> cancelled
            TaskProgressStatus.Failed -> failed
        }
    }

    fun categoryLabel(category: FileCategory): String {
        return when (category) {
            FileCategory.Video -> optionValue("Video")
            FileCategory.Audio -> optionValue("Audio")
            FileCategory.Image -> optionValue("Image")
            FileCategory.Pdf -> optionValue("PDF")
            FileCategory.Document -> optionValue("Document")
        }
    }

    fun categoryPurpose(category: FileCategory): String {
        return when (category) {
            FileCategory.Video -> when (this) {
                englishText -> "Convert or compress video"
                simplifiedChineseText -> "换格式，压缩体积"
                else -> "換格式，壓縮體積"
            }
            FileCategory.Audio -> when (this) {
                englishText -> "Convert or extract audio"
                simplifiedChineseText -> "换格式，提取声音"
                else -> "換格式，提取聲音"
            }
            FileCategory.Image -> when (this) {
                englishText -> "Convert image formats"
                simplifiedChineseText -> "转换图片格式"
                else -> "轉換圖片格式"
            }
            FileCategory.Pdf -> when (this) {
                englishText -> "Render, extract, merge, or secure"
                simplifiedChineseText -> "渲染、提取、合并、加密"
                else -> "渲染、提取、合併、加密"
            }
            FileCategory.Document -> when (this) {
                englishText -> "Convert Office files"
                simplifiedChineseText -> "转换 Office 文档"
                else -> "轉換 Office 文件"
            }
        }
    }

    fun optionsTitle(category: FileCategory): String {
        return when (this) {
            englishText -> "${categoryLabel(category)} options"
            simplifiedChineseText -> "${categoryLabel(category)}选项"
            else -> "${categoryLabel(category)}選項"
        }
    }

    fun presetNote(category: FileCategory): String {
        return when (category) {
            FileCategory.Video -> when (this) {
                englishText -> "Tune quality and size"
                simplifiedChineseText -> "调画质，控体积"
                else -> "調畫質，控體積"
            }
            FileCategory.Audio -> when (this) {
                englishText -> "Tune sound and compatibility"
                simplifiedChineseText -> "调音质和兼容性"
                else -> "調音質和相容性"
            }
            FileCategory.Image -> when (this) {
                englishText -> "Set quality when available"
                simplifiedChineseText -> "可用时调整输出质量"
                else -> "可用時調整輸出品質"
            }
            FileCategory.Pdf -> when (this) {
                englishText -> "PDF pages, text, merge, and password tools"
                simplifiedChineseText -> "PDF 页面、文本、合并和密码工具"
                else -> "PDF 頁面、文字、合併和密碼工具"
            }
            FileCategory.Document -> when (this) {
                englishText -> "Experimental DOCX, PPTX, and XLSX path"
                simplifiedChineseText -> "DOCX、PPTX、XLSX 实验转换"
                else -> "DOCX、PPTX、XLSX 實驗轉換"
            }
        }
    }

    fun toFormat(format: String): String = "$toPrefix $format"

    fun engineHint(value: String): String = optionValue(value)

    fun accentLabel(option: AccentColorOption): String = optionValue(option.englishLabel)

    fun languageLabel(option: LanguageOption): String {
        return when (option) {
            LanguageOption.System -> when (this) {
                englishText -> "System"
                simplifiedChineseText -> "跟随系统"
                else -> "跟隨系統"
            }
            LanguageOption.English -> "English"
            LanguageOption.SimplifiedChinese -> "简体中文"
            LanguageOption.TraditionalChinese -> "繁體中文"
        }
    }

    fun updateChannelLabel(channel: UpdateChannel): String {
        return when (channel) {
            UpdateChannel.Stable -> stableUpdateChannel
            UpdateChannel.Preview -> previewUpdateChannel
        }
    }

    fun currentIsLatest(channel: UpdateChannel): String {
        return when (this) {
            englishText -> "No ${updateChannelLabel(channel).lowercase(Locale.US)} update found"
            simplifiedChineseText -> "当前已是${updateChannelLabel(channel)}最新版本"
            else -> "目前已是${updateChannelLabel(channel)}最新版本"
        }
    }

    fun updateAvailableMessage(release: UpdateRelease): String {
        return when (this) {
            englishText -> "Update ${release.versionName} is available"
            simplifiedChineseText -> "发现新版本 ${release.versionName}"
            else -> "發現新版本 ${release.versionName}"
        }
    }

    fun releaseDetail(release: UpdateRelease): String {
        return when (this) {
            englishText -> "${release.assetName} · ${formatBytes(release.sizeBytes, this)}"
            simplifiedChineseText -> "${release.assetName} · ${formatBytes(release.sizeBytes, this)}"
            else -> "${release.assetName} · ${formatBytes(release.sizeBytes, this)}"
        }
    }

    fun downloadProgressMessage(progress: DownloadProgress): String {
        val downloaded = formatBytes(progress.bytesDownloaded, this)
        val total = progress.totalBytes
        return if (total == null) {
            downloaded
        } else {
            "$downloaded / ${formatBytes(total, this)}"
        }
    }

    fun downloadFailureMessage(detail: String?): String {
        return if (detail.isNullOrBlank()) {
            downloadFailed
        } else {
            "$downloadFailed: $detail"
        }
    }

    fun updateFailureMessage(reason: UpdateFailureReason, detail: String?): String {
        val base = when (reason) {
            UpdateFailureReason.Network -> when (this) {
                englishText -> "Could not connect to GitHub"
                simplifiedChineseText -> "无法连接 GitHub"
                else -> "無法連接 GitHub"
            }
            UpdateFailureReason.NoRelease -> when (this) {
                englishText -> "No release found for this channel"
                simplifiedChineseText -> "这个通道还没有发布版本"
                else -> "這個通道尚未發布版本"
            }
            UpdateFailureReason.NoApkAsset -> when (this) {
                englishText -> "The release has no Android APK"
                simplifiedChineseText -> "这个发布里没有 Android APK"
                else -> "這個發布裡沒有 Android APK"
            }
            UpdateFailureReason.MissingVersionMetadata -> when (this) {
                englishText -> "The release is missing Android version metadata"
                simplifiedChineseText -> "这个发布缺少 Android 版本信息"
                else -> "這個發布缺少 Android 版本資訊"
            }
            UpdateFailureReason.InvalidResponse -> when (this) {
                englishText -> "GitHub returned an unreadable response"
                simplifiedChineseText -> "GitHub 返回的内容无法识别"
                else -> "GitHub 返回的內容無法識別"
            }
        }
        return if (detail.isNullOrBlank()) base else "$base: $detail"
    }

    fun optionValue(value: String): String {
        return when (value) {
            "Video" -> when (this) {
                englishText -> "Video"
                simplifiedChineseText -> "视频"
                else -> "影片"
            }
            "Audio" -> when (this) {
                englishText -> "Audio"
                simplifiedChineseText -> "音频"
                else -> "音訊"
            }
            "Image" -> when (this) {
                englishText -> "Image"
                simplifiedChineseText -> "图片"
                else -> "圖片"
            }
            "Document" -> when (this) {
                englishText -> "Document"
                simplifiedChineseText -> "文档"
                else -> "文件"
            }
            "Compatibility" -> when (this) {
                englishText -> "Multi-format"
                simplifiedChineseText -> "多格式兼容"
                else -> "多格式相容"
            }
            "Re-encode" -> when (this) {
                englishText -> "Re-encode"
                simplifiedChineseText -> "重新编码"
                else -> "重新編碼"
            }
            "30s GIF" -> when (this) {
                englishText -> "Up to 30s GIF"
                simplifiedChineseText -> "上限 30 秒 GIF"
                else -> "上限 30 秒 GIF"
            }
            "Auto engine" -> when (this) {
                englishText -> "Native or compatibility"
                simplifiedChineseText -> "原生或兼容引擎"
                else -> "原生或相容引擎"
            }
            "PDF" -> "PDF"
            "TXT" -> "TXT"
            "MD" -> "MD"
            "Page rasterization" -> when (this) {
                englishText -> "Page rasterization"
                simplifiedChineseText -> "页面栅格化"
                else -> "頁面柵格化"
            }
            "Merge PDFs" -> when (this) {
                englishText -> "Merge PDFs"
                simplifiedChineseText -> "合并 PDF"
                else -> "合併 PDF"
            }
            "Text layer" -> when (this) {
                englishText -> "Text layer"
                simplifiedChineseText -> "文本层提取"
                else -> "文字層提取"
            }
            "Markdown" -> when (this) {
                englishText -> "Markdown"
                simplifiedChineseText -> "Markdown"
                else -> "Markdown"
            }
            "Encrypt PDF" -> when (this) {
                englishText -> "Encrypt PDF"
                simplifiedChineseText -> "加密 PDF"
                else -> "加密 PDF"
            }
            "Decrypt PDF" -> when (this) {
                englishText -> "Decrypt PDF"
                simplifiedChineseText -> "解密 PDF"
                else -> "解密 PDF"
            }
            "Password protect" -> when (this) {
                englishText -> "Password protect"
                simplifiedChineseText -> "密码保护"
                else -> "密碼保護"
            }
            "Remove password" -> when (this) {
                englishText -> "Remove password"
                simplifiedChineseText -> "移除密码"
                else -> "移除密碼"
            }
            "Office to PDF" -> when (this) {
                englishText -> "Office to PDF"
                simplifiedChineseText -> "Office 转 PDF"
                else -> "Office 轉 PDF"
            }
            "Batch" -> when (this) {
                englishText -> "Batch processing"
                simplifiedChineseText -> "批量处理"
                else -> "批次處理"
            }
            "Supports transparency" -> when (this) {
                englishText -> "Supports transparency"
                simplifiedChineseText -> "支持透明度"
                else -> "支援透明度"
            }
            "Lossless output" -> when (this) {
                englishText -> "Lossless output"
                simplifiedChineseText -> "无损输出"
                else -> "無損輸出"
            }
            "High" -> when (this) {
                englishText -> "High"
                simplifiedChineseText -> "高质量"
                else -> "高品質"
            }
            "Balanced" -> when (this) {
                englishText -> "Balanced"
                simplifiedChineseText -> "均衡"
                else -> "均衡"
            }
            "Small" -> when (this) {
                englishText -> "Small"
                simplifiedChineseText -> "小体积"
                else -> "小體積"
            }
            "Original" -> when (this) {
                englishText -> "Original"
                simplifiedChineseText -> "原始"
                else -> "原始"
            }
            ADVANCED_FADE_OFF -> when (this) {
                englishText -> "Off"
                simplifiedChineseText -> "关闭"
                else -> "關閉"
            }
            ADVANCED_FADE_HALF_SECOND,
            ADVANCED_FADE_ONE_SECOND,
            ADVANCED_FADE_TWO_SECONDS,
            AUDIO_VOLUME_50,
            AUDIO_VOLUME_100,
            AUDIO_VOLUME_150,
            AUDIO_VOLUME_200 -> value
            VIDEO_MIRROR_OFF -> when (this) {
                englishText -> "Off"
                simplifiedChineseText -> "关闭"
                else -> "關閉"
            }
            VIDEO_MIRROR_HORIZONTAL -> when (this) {
                englishText -> "Horizontal"
                simplifiedChineseText -> "左右镜像"
                else -> "左右鏡像"
            }
            VIDEO_MIRROR_VERTICAL -> when (this) {
                englishText -> "Vertical"
                simplifiedChineseText -> "上下镜像"
                else -> "上下鏡像"
            }
            VIDEO_MIRROR_BOTH -> when (this) {
                englishText -> "Both"
                simplifiedChineseText -> "上下左右"
                else -> "上下左右"
            }
            VIDEO_ROTATION_NONE -> when (this) {
                englishText -> "None"
                simplifiedChineseText -> "不旋转"
                else -> "不旋轉"
            }
            VIDEO_ROTATION_90_CW -> when (this) {
                englishText -> "90° clockwise"
                simplifiedChineseText -> "顺时针 90°"
                else -> "順時針 90°"
            }
            VIDEO_ROTATION_90_CCW -> when (this) {
                englishText -> "90° counterclockwise"
                simplifiedChineseText -> "逆时针 90°"
                else -> "逆時針 90°"
            }
            VIDEO_ROTATION_180 -> when (this) {
                englishText -> "180°"
                simplifiedChineseText -> "180°"
                else -> "180°"
            }
            VIDEO_ASPECT_KEEP -> when (this) {
                englishText -> "Keep"
                simplifiedChineseText -> "保持"
                else -> "保持"
            }
            VIDEO_ASPECT_FIT_16_9 -> when (this) {
                englishText -> "Fit 16:9"
                simplifiedChineseText -> "适配 16:9"
                else -> "適配 16:9"
            }
            VIDEO_ASPECT_FIT_9_16 -> when (this) {
                englishText -> "Fit 9:16"
                simplifiedChineseText -> "适配 9:16"
                else -> "適配 9:16"
            }
            VIDEO_ASPECT_FIT_1_1 -> when (this) {
                englishText -> "Fit 1:1"
                simplifiedChineseText -> "适配 1:1"
                else -> "適配 1:1"
            }
            VIDEO_ASPECT_CROP_16_9 -> when (this) {
                englishText -> "Crop 16:9"
                simplifiedChineseText -> "裁剪 16:9"
                else -> "裁剪 16:9"
            }
            VIDEO_ASPECT_CROP_9_16 -> when (this) {
                englishText -> "Crop 9:16"
                simplifiedChineseText -> "裁剪 9:16"
                else -> "裁剪 9:16"
            }
            VIDEO_ASPECT_CROP_1_1 -> when (this) {
                englishText -> "Crop 1:1"
                simplifiedChineseText -> "裁剪 1:1"
                else -> "裁剪 1:1"
            }
            AUDIO_VOLUME_MUTE -> when (this) {
                englishText -> "Mute"
                simplifiedChineseText -> "静音"
                else -> "靜音"
            }
            AUDIO_ECHO_OFF -> when (this) {
                englishText -> "Off"
                simplifiedChineseText -> "关闭"
                else -> "關閉"
            }
            AUDIO_ECHO_LIGHT -> when (this) {
                englishText -> "Light"
                simplifiedChineseText -> "轻微"
                else -> "輕微"
            }
            AUDIO_ECHO_ROOM -> when (this) {
                englishText -> "Room"
                simplifiedChineseText -> "房间"
                else -> "房間"
            }
            AUDIO_DENOISE_OFF -> when (this) {
                englishText -> "Off"
                simplifiedChineseText -> "关闭"
                else -> "關閉"
            }
            AUDIO_DENOISE_LIGHT -> when (this) {
                englishText -> "Light"
                simplifiedChineseText -> "轻度"
                else -> "輕度"
            }
            AUDIO_DENOISE_STANDARD -> when (this) {
                englishText -> "Standard"
                simplifiedChineseText -> "标准"
                else -> "標準"
            }
            "Auto bitrate" -> when (this) {
                englishText -> "Auto (recommended)"
                simplifiedChineseText -> "自动（推荐）"
                else -> "自動（推薦）"
            }
            "Auto audio bitrate" -> when (this) {
                englishText -> "Auto (encoder default)"
                simplifiedChineseText -> "自动（编码器默认）"
                else -> "自動（編碼器預設）"
            }
            "Recommended audio bitrate" -> when (this) {
                englishText -> "Recommended (192 kbps)"
                simplifiedChineseText -> "推荐（192 kbps）"
                else -> "建議（192 kbps）"
            }
            "High audio bitrate" -> when (this) {
                englishText -> "High (256 kbps)"
                simplifiedChineseText -> "高（256 kbps）"
                else -> "高（256 kbps）"
            }
            "Compact audio bitrate" -> when (this) {
                englishText -> "Compact (128 kbps)"
                simplifiedChineseText -> "小体积（128 kbps）"
                else -> "小體積（128 kbps）"
            }
            "Voice audio bitrate" -> when (this) {
                englishText -> "Voice (96 kbps)"
                simplifiedChineseText -> "语音（96 kbps）"
                else -> "語音（96 kbps）"
            }
            "Recommended sample rate" -> when (this) {
                englishText -> "Recommended (48 kHz)"
                simplifiedChineseText -> "推荐（48 kHz）"
                else -> "建議（48 kHz）"
            }
            "Low bitrate" -> when (this) {
                englishText -> "Low (1 Mbps)"
                simplifiedChineseText -> "低（1 Mbps）"
                else -> "低（1 Mbps）"
            }
            "Medium bitrate" -> when (this) {
                englishText -> "Medium (2.5 Mbps)"
                simplifiedChineseText -> "中（2.5 Mbps）"
                else -> "中（2.5 Mbps）"
            }
            "High bitrate" -> when (this) {
                englishText -> "High (5 Mbps)"
                simplifiedChineseText -> "高（5 Mbps）"
                else -> "高（5 Mbps）"
            }
            "Very high bitrate" -> when (this) {
                englishText -> "Very high (8 Mbps)"
                simplifiedChineseText -> "极高（8 Mbps）"
                else -> "極高（8 Mbps）"
            }
            "Ultra bitrate" -> when (this) {
                englishText -> "Ultra (16 Mbps)"
                simplifiedChineseText -> "超高（16 Mbps）"
                else -> "超高（16 Mbps）"
            }
            "H.264", "H.265" -> value
            "Frame rate 25" -> when (this) {
                englishText -> "Max 25 fps"
                simplifiedChineseText -> "最高 25fps"
                else -> "最高 25fps"
            }
            "Frame rate 30" -> when (this) {
                englishText -> "Max 30 fps"
                simplifiedChineseText -> "最高 30fps"
                else -> "最高 30fps"
            }
            "Frame rate 60" -> when (this) {
                englishText -> "Max 60 fps"
                simplifiedChineseText -> "最高 60fps"
                else -> "最高 60fps"
            }
            "Auto" -> when (this) {
                englishText -> "Auto"
                simplifiedChineseText -> "自动"
                else -> "自動"
            }
            "Keep original picture" -> when (this) {
                englishText -> "Keep original picture"
                simplifiedChineseText -> "保持原画"
                else -> "保留原畫"
            }
            "Smart allocation" -> when (this) {
                englishText -> "Smart allocation"
                simplifiedChineseText -> "智能分配"
                else -> "智慧分配"
            }
            "Medium" -> when (this) {
                englishText -> "Medium"
                simplifiedChineseText -> "中等"
                else -> "中等"
            }
            "Low" -> when (this) {
                englishText -> "Low"
                simplifiedChineseText -> "低"
                else -> "低"
            }
            "Stereo" -> when (this) {
                englishText -> "Stereo"
                simplifiedChineseText -> "立体声"
                else -> "立體聲"
            }
            "Mono" -> when (this) {
                englishText -> "Mono"
                simplifiedChineseText -> "单声道"
                else -> "單聲道"
            }
            "Keep if possible" -> when (this) {
                englishText -> "Keep if possible"
                simplifiedChineseText -> "尽量保留"
                else -> "盡量保留"
            }
            "Flatten" -> when (this) {
                englishText -> "Flatten"
                simplifiedChineseText -> "平铺背景"
                else -> "平鋪背景"
            }
            "One file per input" -> when (this) {
                englishText -> "One file per input"
                simplifiedChineseText -> "每个输入单独输出"
                else -> "每個輸入單獨輸出"
            }
            "Single PDF" -> when (this) {
                englishText -> "Single PDF"
                simplifiedChineseText -> "合并为 PDF"
                else -> "合併為 PDF"
            }
            "One PDF per image" -> when (this) {
                englishText -> "One PDF per image"
                simplifiedChineseText -> "每张图一个 PDF"
                else -> "每張圖一個 PDF"
            }
            "First frame" -> when (this) {
                englishText -> "First frame"
                simplifiedChineseText -> "只转首帧"
                else -> "只轉首幀"
            }
            "Split frames" -> when (this) {
                englishText -> "Split frames"
                simplifiedChineseText -> "拆帧输出"
                else -> "拆幀輸出"
            }
            "All frames in one PDF" -> when (this) {
                englishText -> "All frames in one PDF"
                simplifiedChineseText -> "全部帧进一个 PDF"
                else -> "全部幀進一個 PDF"
            }
            "One PDF per frame" -> when (this) {
                englishText -> "One PDF per frame"
                simplifiedChineseText -> "一帧一个 PDF"
                else -> "一幀一個 PDF"
            }
            "A4 fit" -> when (this) {
                englishText -> "A4 fit"
                simplifiedChineseText -> "适配 A4"
                else -> "適配 A4"
            }
            "Original ratio" -> when (this) {
                englishText -> "Original ratio"
                simplifiedChineseText -> "原图比例"
                else -> "原圖比例"
            }
            "Low resolution" -> when (this) {
                englishText -> "Low resolution"
                simplifiedChineseText -> "低分辨率"
                else -> "低解析度"
            }
            "High detail" -> when (this) {
                englishText -> "High detail"
                simplifiedChineseText -> "高清细节"
                else -> "高清細節"
            }
            "Charcoal" -> when (this) {
                englishText -> "Charcoal"
                simplifiedChineseText -> "炭黑"
                else -> "炭黑"
            }
            "Deep Navy" -> when (this) {
                englishText -> "Deep Navy"
                simplifiedChineseText -> "深海蓝"
                else -> "深海藍"
            }
            "Forest Green" -> when (this) {
                englishText -> "Forest Green"
                simplifiedChineseText -> "森林绿"
                else -> "森林綠"
            }
            "Steel Blue" -> when (this) {
                englishText -> "Steel Blue"
                simplifiedChineseText -> "钢蓝"
                else -> "鋼藍"
            }
            "Dusty Rose" -> when (this) {
                englishText -> "Dusty Rose"
                simplifiedChineseText -> "灰玫瑰"
                else -> "灰玫瑰"
            }
            "Mustard" -> when (this) {
                englishText -> "Mustard"
                simplifiedChineseText -> "芥末"
                else -> "芥末"
            }
            "Burnt Orange" -> when (this) {
                englishText -> "Burnt Orange"
                simplifiedChineseText -> "暖橙"
                else -> "暖橙"
            }
            "Electric Blue" -> when (this) {
                englishText -> "Electric Blue"
                simplifiedChineseText -> "电蓝"
                else -> "電藍"
            }
            "Fern Green" -> when (this) {
                englishText -> "Fern Green"
                simplifiedChineseText -> "蕨绿"
                else -> "蕨綠"
            }
            "Deep Purple" -> when (this) {
                englishText -> "Deep Purple"
                simplifiedChineseText -> "深紫"
                else -> "深紫"
            }
            else -> value
        }
    }
}

private val englishText = UiText(
    tagline = "Files stay on this device",
    openAbout = "Open about",
    closeAbout = "Close about",
    openSettings = "Open settings",
    closeSettings = "Close settings",
    appLogo = "ZenConverter logo",
    appVersion = "Version",
    appLicense = "AGPL-3.0-or-later",
    aboutDescription = "A local all-in-one format converter for Android. Works offline, with no ads or fees.",
    githubRepository = "GitHub repository",
    checkUpdates = "Check for updates",
    stableUpdateChannel = "Stable",
    previewUpdateChannel = "Preview",
    checkingUpdates = "Checking GitHub...",
    appDownload = "Download in app",
    browserDownload = "Browser download",
    downloadComplete = "Download complete",
    openDownloadedApk = "Open APK",
    downloadFailed = "Download failed",
    cancelDownload = "Cancel download",
    downloadCancelled = "Download cancelled",
    installPermissionRequired = "Allow APK installs, then open the APK again",
    apkOpenFailed = "Could not open APK",
    supportDevelopment = "Sponsor development",
    sponsorTitle = "Sponsor ZenConverter",
    sponsorIntro = "Sponsor to help ZenConverter stay maintained, open-source, free, and ad-free.",
    openLink = "Open link",
    copy = "Copy",
    copied = "Copied",
    linkUnavailable = "No app can open this link",
    accentColor = "Accent color",
    language = "Language",
    chooseConversion = "Choose conversion",
    target = "Target",
    select = "Select",
    output = "Save location",
    choose = "Choose",
    chooseDirectory = "Choose folder",
    chooseFolderBeforeConversion = "Choose where to save results",
    defaultOutputLocation = "Default folder",
    defaultOutputNote = "System folders / ZenConverter",
    customOutputLocation = "Custom folder",
    storagePermissionRequired = "Allow storage permission or choose a folder",
    folderPermissionSaved = "Folder permission saved",
    folderSelectedForSession = "Folder selected for this session",
    start = "Start",
    cancel = "Cancel",
    cancelOrClearTasks = "Cancel / Clear tasks",
    queue = "Conversion tasks",
    selectedSuffix = "selected",
    emptyQueue = "No tasks yet",
    unknownType = "Unknown type",
    unknownSize = "Unknown size",
    remove = "Remove",
    shareOutput = "Share output",
    openOutputLocation = "Open output location",
    outputUnavailable = "Output file is unavailable",
    shareOutputFailed = "No app can share this output",
    openOutputFailed = "No app can open this output",
    waiting = "Waiting",
    processing = "Processing",
    flowComplete = "Flow complete",
    flowCompleteNoFiles = "Flow checked, no files created",
    cancelled = "Cancelled",
    failed = "Failed",
    quality = "Quality",
    pageSize = "Page size",
    renderQuality = "Render quality",
    resolution = "Resolution",
    bitrate = "Bitrate",
    codec = "Codec",
    frameRate = "Frame rate",
    sampleRate = "Sample rate",
    channels = "Channels",
    outputMode = "Output",
    password = "Password",
    skip = "Skip",
    imagePdfPromptTitle = "Image to PDF",
    gifFramePromptTitle = "GIF frames",
    gifPdfFramePromptTitle = "GIF frames to PDF",
    pdfPasswordTitle = "PDF password",
    pdfOutputPasswordTitle = "Set PDF password",
    uiPresetSuffix = "",
    toPrefix = "to"
)

private val simplifiedChineseText = UiText(
    tagline = "本机转换，文件不上云",
    openAbout = "打开关于",
    closeAbout = "关闭关于",
    openSettings = "打开设置",
    closeSettings = "关闭设置",
    appLogo = "ZenConverter 标志",
    appVersion = "版本",
    appLicense = "AGPL-3.0-or-later",
    aboutDescription = "面向 Android 的本地综合格式转换工具，不联网，无广告、不收费",
    githubRepository = "GitHub 仓库",
    checkUpdates = "检查更新",
    stableUpdateChannel = "正式版",
    previewUpdateChannel = "预览版",
    checkingUpdates = "正在检查 GitHub...",
    appDownload = "App 内下载",
    browserDownload = "浏览器下载",
    downloadComplete = "下载完成",
    openDownloadedApk = "打开安装包",
    downloadFailed = "下载失败",
    cancelDownload = "取消下载",
    downloadCancelled = "下载已取消",
    installPermissionRequired = "允许安装 APK 后，再次打开安装包",
    apkOpenFailed = "无法打开安装包",
    supportDevelopment = "赞助开发",
    sponsorTitle = "赞助 ZenConverter",
    sponsorIntro = "赞助以支持 ZenConverter 始终保持维护，坚持开源免费无广告",
    openLink = "打开链接",
    copy = "复制",
    copied = "已复制",
    linkUnavailable = "没有可打开此链接的应用",
    accentColor = "重点色",
    language = "语言",
    chooseConversion = "选择转换",
    target = "目标",
    select = "选择",
    output = "保存位置",
    choose = "选择",
    chooseDirectory = "选择目录",
    chooseFolderBeforeConversion = "选择处理后文件的保存位置",
    defaultOutputLocation = "默认文件夹",
    defaultOutputNote = "系统文件夹 / ZenConverter",
    customOutputLocation = "自定义文件夹",
    storagePermissionRequired = "请允许存储权限，或改选自定义文件夹",
    folderPermissionSaved = "文件夹权限已保存",
    folderSelectedForSession = "本次已选择文件夹",
    start = "开始",
    cancel = "取消",
    cancelOrClearTasks = "取消/清空任务列表",
    queue = "转换任务",
    selectedSuffix = "个已选",
    emptyQueue = "暂无任务，先在上方选择文件",
    unknownType = "未知类型",
    unknownSize = "未知大小",
    remove = "移除",
    shareOutput = "分享输出",
    openOutputLocation = "打开输出位置",
    outputUnavailable = "输出文件不可用",
    shareOutputFailed = "没有可分享此输出的应用",
    openOutputFailed = "没有可打开此输出的应用",
    waiting = "等待中",
    processing = "处理中",
    flowComplete = "流程完成",
    flowCompleteNoFiles = "流程已跑通，暂未生成文件",
    cancelled = "已取消",
    failed = "失败",
    quality = "质量",
    pageSize = "页面尺寸",
    renderQuality = "渲染质量",
    resolution = "分辨率",
    bitrate = "码率",
    codec = "编码",
    frameRate = "帧率",
    sampleRate = "采样率",
    channels = "声道",
    outputMode = "输出方式",
    password = "密码",
    skip = "跳过",
    imagePdfPromptTitle = "图片转 PDF",
    gifFramePromptTitle = "GIF 拆帧",
    gifPdfFramePromptTitle = "GIF 拆帧转 PDF",
    pdfPasswordTitle = "PDF 密码",
    pdfOutputPasswordTitle = "设置 PDF 密码",
    uiPresetSuffix = "",
    toPrefix = "转为"
)

private val traditionalChineseText = UiText(
    tagline = "本機轉換，檔案不上雲",
    openAbout = "開啟關於",
    closeAbout = "關閉關於",
    openSettings = "開啟設定",
    closeSettings = "關閉設定",
    appLogo = "ZenConverter 標誌",
    appVersion = "版本",
    appLicense = "AGPL-3.0-or-later",
    aboutDescription = "面向 Android 的本地綜合格式轉換工具，不聯網，無廣告、不收費",
    githubRepository = "GitHub 倉庫",
    checkUpdates = "檢查更新",
    stableUpdateChannel = "正式版",
    previewUpdateChannel = "預覽版",
    checkingUpdates = "正在檢查 GitHub...",
    appDownload = "App 內下載",
    browserDownload = "瀏覽器下載",
    downloadComplete = "下載完成",
    openDownloadedApk = "開啟安裝包",
    downloadFailed = "下載失敗",
    cancelDownload = "取消下載",
    downloadCancelled = "下載已取消",
    installPermissionRequired = "允許安裝 APK 後，再次開啟安裝包",
    apkOpenFailed = "無法開啟安裝包",
    supportDevelopment = "贊助開發",
    sponsorTitle = "贊助 ZenConverter",
    sponsorIntro = "贊助以支持 ZenConverter 始終保持維護，堅持開源免費無廣告",
    openLink = "開啟連結",
    copy = "複製",
    copied = "已複製",
    linkUnavailable = "沒有可開啟此連結的應用",
    accentColor = "重點色",
    language = "語言",
    chooseConversion = "選擇轉換",
    target = "目標",
    select = "選擇",
    output = "儲存位置",
    choose = "選擇",
    chooseDirectory = "選擇資料夾",
    chooseFolderBeforeConversion = "選擇處理後檔案的儲存位置",
    defaultOutputLocation = "預設資料夾",
    defaultOutputNote = "系統資料夾 / ZenConverter",
    customOutputLocation = "自訂資料夾",
    storagePermissionRequired = "請允許儲存權限，或改選自訂資料夾",
    folderPermissionSaved = "資料夾權限已儲存",
    folderSelectedForSession = "本次已選擇資料夾",
    start = "開始",
    cancel = "取消",
    cancelOrClearTasks = "取消／清空任務列表",
    queue = "轉換任務",
    selectedSuffix = "個已選",
    emptyQueue = "暫無任務，先在上方選擇檔案",
    unknownType = "未知類型",
    unknownSize = "未知大小",
    remove = "移除",
    shareOutput = "分享輸出",
    openOutputLocation = "開啟輸出位置",
    outputUnavailable = "輸出檔案不可用",
    shareOutputFailed = "沒有可分享此輸出的應用",
    openOutputFailed = "沒有可開啟此輸出的應用",
    waiting = "等待中",
    processing = "處理中",
    flowComplete = "流程完成",
    flowCompleteNoFiles = "流程已跑通，暫未產生檔案",
    cancelled = "已取消",
    failed = "失敗",
    quality = "品質",
    pageSize = "頁面尺寸",
    renderQuality = "渲染品質",
    resolution = "解析度",
    bitrate = "位元率",
    codec = "編碼",
    frameRate = "幀率",
    sampleRate = "取樣率",
    channels = "聲道",
    outputMode = "輸出方式",
    password = "密碼",
    skip = "略過",
    imagePdfPromptTitle = "圖片轉 PDF",
    gifFramePromptTitle = "GIF 拆幀",
    gifPdfFramePromptTitle = "GIF 拆幀轉 PDF",
    pdfPasswordTitle = "PDF 密碼",
    pdfOutputPasswordTitle = "設定 PDF 密碼",
    uiPresetSuffix = "",
    toPrefix = "轉為"
)

private fun installedAppVersion(context: Context): InstalledAppVersion {
    return runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        InstalledAppVersion(
            versionName = packageInfo.versionName ?: "0.1.0",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        )
    }.getOrDefault(InstalledAppVersion(versionName = "0.1.0", versionCode = 1_000_001L))
}

private fun openExternalLink(
    context: Context,
    url: String,
    failureMessage: String
) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
    }
}

private fun shareOutput(
    context: Context,
    progress: TaskProgress,
    texts: UiText
) {
    val outputUris = progress.outputUriList()
    if (outputUris.isEmpty()) {
        Toast.makeText(context, texts.outputUnavailable, Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(
        if (outputUris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
    ).apply {
        type = progress.outputMimeType ?: MIME_TYPE_ANY
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = clipDataForUris(context, outputUris, texts.shareOutput)
        if (outputUris.size == 1) {
            putExtra(Intent.EXTRA_STREAM, outputUris.first())
        } else {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf<Uri>().apply { addAll(outputUris) })
        }
    }
    val chooser = Intent.createChooser(intent, texts.shareOutput).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(chooser)
    } catch (_: Throwable) {
        Toast.makeText(context, texts.shareOutputFailed, Toast.LENGTH_SHORT).show()
    }
}

private fun openOutputLocation(
    context: Context,
    progress: TaskProgress,
    texts: UiText
) {
    val outputUri = progress.outputUri ?: progress.outputUriList().firstOrNull()
    val locationUri = progress.outputDirectoryUri
    if (
        locationUri != null &&
        tryOpenOutputUri(
            context = context,
            uri = locationUri,
            mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
            title = texts.openOutputLocation
        )
    ) {
        return
    }
    if (outputUri == null) {
        Toast.makeText(context, texts.outputUnavailable, Toast.LENGTH_SHORT).show()
        return
    }
    if (
        !tryOpenOutputUri(
            context = context,
            uri = outputUri,
            mimeType = progress.outputMimeType ?: MIME_TYPE_ANY,
            title = texts.openOutputLocation
        )
    ) {
        Toast.makeText(context, texts.openOutputFailed, Toast.LENGTH_SHORT).show()
    }
}

private fun tryOpenOutputUri(
    context: Context,
    uri: Uri,
    mimeType: String,
    title: String
): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, title, uri)
    }
    val chooser = Intent.createChooser(intent, title).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching {
        context.startActivity(chooser)
        true
    }.getOrDefault(false)
}

private fun clipDataForUris(
    context: Context,
    uris: List<Uri>,
    label: String
): ClipData {
    val clipData = ClipData.newUri(context.contentResolver, label, uris.first())
    uris.drop(1).forEach { uri ->
        clipData.addItem(ClipData.Item(uri))
    }
    return clipData
}

private fun TaskProgress.outputUriList(): List<Uri> {
    if (outputUris.isNotEmpty()) return outputUris
    return outputUri?.let { listOf(it) }.orEmpty()
}

private fun copyToClipboard(
    context: Context,
    label: String,
    value: String,
    copiedMessage: String
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
}

private const val MIME_TYPE_ANY = "*/*"

private fun formatBytes(sizeBytes: Long?, texts: UiText): String {
    if (sizeBytes == null) return texts.unknownSize
    if (sizeBytes < 1024) return "$sizeBytes B"

    val units = listOf("KB", "MB", "GB", "TB")
    var value = sizeBytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}

private fun formatFileInfoLine(
    info: FileBasicInfo?,
    texts: UiText,
    fallbackType: String,
    fallbackSizeBytes: Long?
): String {
    val parts = fileInfoParts(info, texts)
    if (parts.isNotEmpty()) return parts.joinToString(" · ")
    return listOf(fallbackType, formatBytes(fallbackSizeBytes, texts)).joinToString(" · ")
}

private fun formatResultInfoLine(
    file: QueuedFile,
    outputInfo: FileBasicInfo,
    texts: UiText
): String {
    val inputInfo = file.inputInfo
    val formatChange = formatChangeLabel(inputInfo, outputInfo)
    val sizeChange = formatSizeChangeLabel(inputInfo?.sizeBytes ?: file.sizeBytes, outputInfo.sizeBytes, texts)
    return fileInfoParts(
        info = outputInfo,
        texts = texts,
        formatOverride = formatChange,
        sizeOverride = sizeChange
    ).joinToString(" · ")
}

private fun fileInfoParts(
    info: FileBasicInfo?,
    texts: UiText,
    formatOverride: String? = null,
    sizeOverride: String? = null
): List<String> {
    if (info == null) return emptyList()
    return buildList {
        val formatPart = formatOverride ?: info.formatLabel?.takeIf { it.isNotBlank() }
        formatPart?.let { add(it) }
        info.itemCount?.let { add(texts.fileCountLabel(it)) }
        info.durationMs?.let { add(formatDurationMs(it, texts)) }
        if (info.width != null && info.height != null) {
            add("${info.width}x${info.height}")
        }
        info.frameRate?.let { add(texts.frameRateLabel(formatFrameRate(it))) }
        info.bitrateBitsPerSecond?.let { add(texts.bitrateLabel(formatBitrate(it))) }
        info.pageCount?.let { add(texts.pageCountLabel(it)) }
        val sizePart = sizeOverride ?: info.sizeBytes?.let { formatBytes(it, texts) }
        sizePart?.let { add(it) }
    }
}

private fun formatChangeLabel(
    inputInfo: FileBasicInfo?,
    outputInfo: FileBasicInfo
): String? {
    val inputFormat = inputInfo?.formatLabel?.takeIf { it.isNotBlank() }
    val outputFormat = outputInfo.formatLabel?.takeIf { it.isNotBlank() }
    return when {
        inputFormat != null && outputFormat != null -> "$inputFormat -> $outputFormat"
        outputFormat != null -> outputFormat
        else -> null
    }
}

private fun formatSizeChangeLabel(
    inputSizeBytes: Long?,
    outputSizeBytes: Long?,
    texts: UiText
): String? {
    if (outputSizeBytes == null) return null
    val outputSize = formatBytes(outputSizeBytes, texts)
    val inputSize = inputSizeBytes?.takeIf { it > 0L } ?: return outputSize
    val percent = ((outputSizeBytes - inputSize).toDouble() / inputSize.toDouble()) * 100.0
    return "$outputSize (${String.format(Locale.US, "%+.0f%%", percent)})"
}

private fun formatDurationMs(durationMs: Long, texts: UiText): String {
    if (durationMs < 60_000L) {
        return if (texts === englishText) {
            String.format(Locale.US, "%.1fs", durationMs.toDouble() / 1000.0)
        } else {
            String.format(Locale.US, "%.1f秒", durationMs.toDouble() / 1000.0)
        }
    }
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3600L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private fun formatFrameRate(frameRate: Float): String {
    return if (frameRate >= 10f) {
        String.format(Locale.US, "%.0f fps", frameRate)
    } else {
        String.format(Locale.US, "%.1f fps", frameRate)
    }
}

private fun formatBitrate(bitsPerSecond: Long): String {
    return if (bitsPerSecond >= 1_000_000L) {
        String.format(Locale.US, "%.1f Mbps", bitsPerSecond.toDouble() / 1_000_000.0)
    } else {
        String.format(Locale.US, "%.0f kbps", bitsPerSecond.toDouble() / 1_000.0)
    }
}
