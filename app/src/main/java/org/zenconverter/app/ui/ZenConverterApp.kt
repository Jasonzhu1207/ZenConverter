package org.zenconverter.app.ui

import org.zenconverter.app.conversion.TargetId

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import org.zenconverter.app.ui.theme.ZenAnimations
import org.zenconverter.app.ui.theme.bounceClick
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image as BrandImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
import org.zenconverter.app.conversion.ImageSuperResolutionMode
import org.zenconverter.app.conversion.MediaTrimRange
import org.zenconverter.app.conversion.PdfCompressionPreset
import org.zenconverter.app.conversion.PdfExportOptions
import org.zenconverter.app.conversion.PdfImagePageMode
import org.zenconverter.app.conversion.PdfRenderQuality
import org.zenconverter.app.conversion.PdfSecurityOptions
import org.zenconverter.app.conversion.VideoAdvancedOptions
import org.zenconverter.app.conversion.VideoAspectRatioMode
import org.zenconverter.app.conversion.VideoCompressionMode
import org.zenconverter.app.conversion.VideoExportOptions
import org.zenconverter.app.conversion.VideoFrameInterpolationMode
import org.zenconverter.app.conversion.VideoMirrorMode
import org.zenconverter.app.conversion.VideoRotationMode
import org.zenconverter.app.conversion.ContactSheetGrid
import org.zenconverter.app.conversion.VideoContactSheetOptions
import org.zenconverter.app.BuildConfig
import org.zenconverter.app.metadata.MetadataBackupInfo
import org.zenconverter.app.metadata.MetadataInspection
import org.zenconverter.app.metadata.MetadataMessageKey
import org.zenconverter.app.metadata.MetadataStatusMessage
import org.zenconverter.app.metadata.MetadataTargetKind
import org.zenconverter.app.metadata.MetadataToolState
import org.zenconverter.app.model.EsrganModelManager
import org.zenconverter.app.model.EsrganModelSpec
import org.zenconverter.app.model.EsrganModelUiState
import org.zenconverter.app.model.RifeModelManager
import org.zenconverter.app.model.RifeModelSpec
import org.zenconverter.app.model.RifeModelUiState
import org.zenconverter.app.office.OfficeFontManager
import org.zenconverter.app.office.OfficeFontSpec
import org.zenconverter.app.office.OfficeFontUiState
import org.zenconverter.app.settings.AppPreferences
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
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.zenconverter.app.i18n.toLocalizedDoubleOrNull
import org.zenconverter.app.i18n.LocalizedText
import org.zenconverter.app.i18n.localizedFailure
import org.zenconverter.app.i18n.AppLanguages
import org.zenconverter.app.i18n.LanguageOption
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

data class TargetFormat(
    val id: TargetId,
    val extension: String,
    val modeHint: String
) {
    val key: String get() = id.key
}

data class ExternalImportTarget(
    val category: FileCategory,
    val targetFormat: TargetFormat
)

enum class FileCategory(
    val mimeTypes: List<String>,
    val formats: List<TargetFormat>
) {
    Video(
        mimeTypes = listOf("video/*"),
        formats = listOf(
            TargetFormat(TargetId.Mp4, "mp4", "Re-encode"),
            TargetFormat(TargetId.Mkv, "mkv", "Re-encode"),
            TargetFormat(TargetId.Mov, "mov", "Re-encode"),
            TargetFormat(TargetId.Gif, "gif", "30s GIF"),
            TargetFormat(TargetId.ContactSheetJpg, "contact_sheet_jpg", "Summary Sheet"),
            TargetFormat(TargetId.ContactSheetPng, "contact_sheet_png", "Summary Sheet")
        )
    ),
    Audio(
        mimeTypes = listOf("audio/*", "video/*"),
        formats = listOf(
            TargetFormat(TargetId.M4a, "m4a", "Re-encode"),
            TargetFormat(TargetId.Mp3, "mp3", "Re-encode"),
            TargetFormat(TargetId.Wav, "wav", "Re-encode"),
            TargetFormat(TargetId.Flac, "flac", "Re-encode"),
            TargetFormat(TargetId.Wma, "wma", "Re-encode"),
            TargetFormat(TargetId.Opus, "opus", "Re-encode")
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
            TargetFormat(TargetId.Jpg, "jpg", "Batch"),
            TargetFormat(TargetId.Jfif, "jfif", "JPEG"),
            TargetFormat(TargetId.Png, "png", "Supports transparency"),
            TargetFormat(TargetId.Webp, "webp", "Supports transparency"),
            TargetFormat(TargetId.Ico, "ico", "Icon"),
            TargetFormat(TargetId.Pdf, "pdf", "PDF")
        )
    ),
    Pdf(
        mimeTypes = listOf("application/pdf"),
        formats = listOf(
            TargetFormat(TargetId.Png, "png", "Page rasterization"),
            TargetFormat(TargetId.Jpg, "jpg", "Page rasterization"),
            TargetFormat(TargetId.Webp, "webp", "Page rasterization"),
            TargetFormat(TargetId.Pdf, "pdf", "Merge PDFs"),
            TargetFormat(TargetId.PdfCompress, "pdf", "Reduce file size"),
            TargetFormat(TargetId.Txt, "txt", "Text layer"),
            TargetFormat(TargetId.Md, "md", "Markdown"),
            TargetFormat(TargetId.PdfEncrypt, "pdf", "Password protect"),
            TargetFormat(TargetId.PdfDecrypt, "pdf", "Remove password")
        )
    ),
    Document(
        mimeTypes = listOf(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ),
        formats = listOf(
            TargetFormat(TargetId.Pdf, "pdf", "Office to PDF"),
            TargetFormat(TargetId.Txt, "txt", "Text layer"),
            TargetFormat(TargetId.Md, "md", "Markdown")
        )
    ),
    Font(
        mimeTypes = listOf(
            "font/ttf",
            "font/otf",
            "font/woff",
            "font/woff2",
            "application/x-font-ttf",
            "application/x-font-opentype",
            "application/font-sfnt",
            "application/font-woff",
            "application/font-woff2",
            "application/x-font-woff"
        ),
        formats = listOf(
            TargetFormat(TargetId.Woff2, "woff2", "Web font"),
            TargetFormat(TargetId.Woff, "woff", "Web font"),
            TargetFormat(TargetId.Sfnt, "ttf", "Uncompressed")
        )
    ),
    Subtitle(
        mimeTypes = listOf(
            "application/x-subrip",
            "text/vtt",
            "text/x-ssa",
            "text/x-ass"
        ),
        formats = listOf(
            TargetFormat(TargetId.Srt, "srt", "Subtitle"),
            TargetFormat(TargetId.Vtt, "vtt", "Subtitle"),
            TargetFormat(TargetId.Lrc, "lrc", "Lyrics"),
            TargetFormat(TargetId.Ass, "ass", "Styled subtitle")
        )
    )
}

enum class PdfMergeType {
    Images,
    Pdfs
}

data class PdfMergeGroup(
    val id: String,
    val type: PdfMergeType,
    val memberFileIds: List<String>,
    val pdfOptions: PdfExportOptions = PdfExportOptions()
)

data class VideoMergeGroup(
    val id: String,
    val memberFileIds: List<String>,
    val targetFormat: String = "MP4",
    val videoOptions: VideoExportOptions = VideoExportOptions(),
    val audioOptions: AudioExportOptions = AudioExportOptions()
)

data class QueuedFile(
    val id: String,
    val uri: Uri,
    val inputUris: List<Uri>,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String?,
    val category: FileCategory,
    val sourceCategory: FileCategory = category,
    val targetOptions: List<ExternalImportTarget> = emptyList(),
    val targetFormat: String,
    val videoOptions: VideoExportOptions = VideoExportOptions(),
    val audioOptions: AudioExportOptions = AudioExportOptions(),
    val imageOptions: ImageExportOptions = ImageExportOptions(quality = 85),
    val pdfOptions: PdfExportOptions = PdfExportOptions(),
    val inputInfo: FileBasicInfo? = null,
    val gifFrameMode: GifFrameExportMode = GifFrameExportMode.FirstFrame,
    val pdfSecurityOptions: PdfSecurityOptions = PdfSecurityOptions(),
    val pdfPasswords: List<String?> = emptyList(),
    val contactSheetOptions: VideoContactSheetOptions = VideoContactSheetOptions()
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
    val message: LocalizedText,
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
        val detail: LocalizedText?
    ) : UpdateUiState
}

private sealed interface UpdateDownloadUiState {
    object Idle : UpdateDownloadUiState
    data class Downloading(val progress: DownloadProgress) : UpdateDownloadUiState
    data class Completed(val downloadedUpdate: DownloadedUpdate) : UpdateDownloadUiState
    data class Failed(val message: LocalizedText?) : UpdateDownloadUiState
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

private val HomeHorizontalPadding = 20.dp
private val HomeTopPadding = 16.dp
private val HomeBottomPadding = 16.dp
private val HeaderContentGap = 12.dp
private val HeaderActionSpacing = 6.dp
private val HeaderButtonSize = 44.dp
private val HeaderAddSlotWidth = 50.dp
private val EmptyPanelEntryHeight = 360.dp
private val EmptyHeroButtonOffsetY = (-36).dp
private val EmptyHeroTextOffsetY = 82.dp

private val supportTargets = listOf(
    SupportTarget("Afdian", AFDIAN_URL, SupportTargetType.Link),
    SupportTarget("USDT (TRC-20)", USDT_TRC20_ADDRESS, SupportTargetType.Wallet),
    SupportTarget("Bitcoin (BTC)", BTC_ADDRESS, SupportTargetType.Wallet),
    SupportTarget("Ethereum (ETH / ERC-20)", ETH_ERC20_ADDRESS, SupportTargetType.Wallet)
)

internal enum class AccentColorOption(
    val englishLabel: String,
    val lightColor: Color,
    val lightContentColor: Color,
    val darkColor: Color,
    val darkContentColor: Color
) {
    Dynamic("Material You", Color(0xFF6750A4), Color.White, Color(0xFFD0BCFF), Color(0xFF381E72)),
    Charcoal("Charcoal", Color(0xFF111111), Color.White, Color(0xFFE5E7EB), Color(0xFF111111)),
    DeepNavy("Deep Navy", Color(0xFF36454F), Color.White, Color(0xFF93C5FD), Color(0xFF0F172A)),
    ForestGreen("Forest Green", Color(0xFF2D4A2B), Color.White, Color(0xFF86EFAC), Color(0xFF052E16)),
    SteelBlue("Steel Blue", Color(0xFF4A6FA5), Color.White, Color(0xFF60A5FA), Color(0xFF0F172A)),
    DustyRose("Dusty Rose", Color(0xFFD4A5A5), Color(0xFF111111), Color(0xFFFDA4AF), Color(0xFF4C0519)),
    Mustard("Mustard", Color(0xFFF4A900), Color(0xFF111111), Color(0xFFFCD34D), Color(0xFF451A03)),
    BurntOrange("Burnt Orange", Color(0xFFE76F51), Color(0xFF111111), Color(0xFFFB923C), Color(0xFF431407)),
    ElectricBlue("Electric Blue", Color(0xFF0066FF), Color.White, Color(0xFF38BDF8), Color(0xFF082F49)),
    FernGreen("Fern Green", Color(0xFF4A7C59), Color.White, Color(0xFF34D399), Color(0xFF022C22)),
    DeepPurple("Deep Purple", Color(0xFF2B1E3E), Color.White, Color(0xFFC084FC), Color(0xFF3B0764));

    val color: Color get() = lightColor
    val contentColor: Color get() = lightContentColor

    fun color(isDark: Boolean): Color = if (isDark) darkColor else lightColor
    fun contentColor(isDark: Boolean): Color = if (isDark) darkContentColor else lightContentColor
}

internal enum class ThemeModeOption {
    System,
    Light,
    Dark
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

internal const val VIDEO_COMPRESSION_STANDARD = "Video compression standard"
internal const val VIDEO_COMPRESSION_VISUAL_LOSSLESS = "Video compression visual lossless"
internal const val VIDEO_COMPRESSION_BALANCED = "Video compression balanced"
internal const val VIDEO_COMPRESSION_SMALL = "Video compression small"
private val VIDEO_COMPRESSION_OPTIONS = listOf(
    VIDEO_COMPRESSION_STANDARD,
    VIDEO_COMPRESSION_VISUAL_LOSSLESS,
    VIDEO_COMPRESSION_BALANCED,
    VIDEO_COMPRESSION_SMALL
)

internal const val VIDEO_INTERPOLATION_OFF = "Video interpolation off"
internal const val VIDEO_INTERPOLATION_OPTICAL_FLOW_2X = "Video interpolation optical flow 2x"
internal const val VIDEO_INTERPOLATION_RIFE_2X = "Video interpolation rife 2x"
private val VIDEO_INTERPOLATION_OPTIONS = listOf(
    VIDEO_INTERPOLATION_OFF,
    VIDEO_INTERPOLATION_OPTICAL_FLOW_2X,
    VIDEO_INTERPOLATION_RIFE_2X
)

private const val CONTACT_SHEET_GRID_3X4 = "3 × 4 (12)"
private const val CONTACT_SHEET_GRID_3X3 = "3 × 3 (9)"
private const val CONTACT_SHEET_GRID_4X4 = "4 × 4 (16)"
private const val CONTACT_SHEET_GRID_5X5 = "5 × 5 (25)"
private val CONTACT_SHEET_GRID_OPTIONS = listOf(
    CONTACT_SHEET_GRID_3X4,
    CONTACT_SHEET_GRID_3X3,
    CONTACT_SHEET_GRID_4X4,
    CONTACT_SHEET_GRID_5X5
)

private fun contactSheetGridFor(labelKey: String): ContactSheetGrid {
    return when (labelKey) {
        CONTACT_SHEET_GRID_3X3 -> ContactSheetGrid.Grid3x3
        CONTACT_SHEET_GRID_4X4 -> ContactSheetGrid.Grid4x4
        CONTACT_SHEET_GRID_5X5 -> ContactSheetGrid.Grid5x5
        else -> ContactSheetGrid.Grid3x4
    }
}

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
private const val AUDIO_SAMPLE_RATE_24000 = "24 kHz"
private const val AUDIO_SAMPLE_RATE_16000 = "16 kHz"
private const val AUDIO_SAMPLE_RATE_8000 = "8 kHz"
private val AUDIO_SAMPLE_RATE_OPTIONS = listOf(
    AUDIO_SAMPLE_RATE_ORIGINAL,
    AUDIO_SAMPLE_RATE_RECOMMENDED,
    AUDIO_SAMPLE_RATE_44100,
    AUDIO_SAMPLE_RATE_32000
)
private val OPUS_SUPPORTED_SAMPLE_RATES = setOf(48_000, 24_000, 16_000, 12_000, 8_000)
private val OPUS_AUDIO_SAMPLE_RATE_OPTIONS = listOf(
    AUDIO_SAMPLE_RATE_ORIGINAL,
    AUDIO_SAMPLE_RATE_RECOMMENDED,
    AUDIO_SAMPLE_RATE_24000,
    AUDIO_SAMPLE_RATE_16000,
    AUDIO_SAMPLE_RATE_8000
)

private fun isOpusTarget(targetFormat: TargetFormat): Boolean {
    return targetFormat.extension.equals("opus", ignoreCase = true)
}

private fun audioSampleRateOptionsFor(targetFormat: TargetFormat): List<String> {
    return if (isOpusTarget(targetFormat)) {
        OPUS_AUDIO_SAMPLE_RATE_OPTIONS
    } else {
        AUDIO_SAMPLE_RATE_OPTIONS
    }
}

private const val AUDIO_CHANNELS_ORIGINAL = "Original"
private const val AUDIO_CHANNELS_STEREO = "Stereo"
private const val AUDIO_CHANNELS_MONO = "Mono"
private val AUDIO_CHANNEL_OPTIONS = listOf(
    AUDIO_CHANNELS_ORIGINAL,
    AUDIO_CHANNELS_STEREO,
    AUDIO_CHANNELS_MONO
)

internal const val ADVANCED_FADE_OFF = "Off"
internal const val ADVANCED_FADE_HALF_SECOND = "0.5s"
internal const val ADVANCED_FADE_ONE_SECOND = "1s"
internal const val ADVANCED_FADE_TWO_SECONDS = "2s"
private val ADVANCED_FADE_OPTIONS = listOf(
    ADVANCED_FADE_OFF,
    ADVANCED_FADE_HALF_SECOND,
    ADVANCED_FADE_ONE_SECOND,
    ADVANCED_FADE_TWO_SECONDS
)

internal const val VIDEO_MIRROR_OFF = "Mirror off"
internal const val VIDEO_MIRROR_HORIZONTAL = "Horizontal"
internal const val VIDEO_MIRROR_VERTICAL = "Vertical"
internal const val VIDEO_MIRROR_BOTH = "Both"
private val VIDEO_MIRROR_OPTIONS = listOf(
    VIDEO_MIRROR_OFF,
    VIDEO_MIRROR_HORIZONTAL,
    VIDEO_MIRROR_VERTICAL,
    VIDEO_MIRROR_BOTH
)

internal const val VIDEO_ROTATION_NONE = "No rotation"
internal const val VIDEO_ROTATION_90_CW = "90 CW"
internal const val VIDEO_ROTATION_90_CCW = "90 CCW"
internal const val VIDEO_ROTATION_180 = "180"
private val VIDEO_ROTATION_OPTIONS = listOf(
    VIDEO_ROTATION_NONE,
    VIDEO_ROTATION_90_CW,
    VIDEO_ROTATION_90_CCW,
    VIDEO_ROTATION_180
)

internal const val VIDEO_ASPECT_KEEP = "Keep aspect"
internal const val VIDEO_ASPECT_FIT_16_9 = "Fit 16:9"
internal const val VIDEO_ASPECT_FIT_9_16 = "Fit 9:16"
internal const val VIDEO_ASPECT_FIT_1_1 = "Fit 1:1"
internal const val VIDEO_ASPECT_CROP_16_9 = "Crop 16:9"
internal const val VIDEO_ASPECT_CROP_9_16 = "Crop 9:16"
internal const val VIDEO_ASPECT_CROP_1_1 = "Crop 1:1"
private val VIDEO_ASPECT_OPTIONS = listOf(
    VIDEO_ASPECT_KEEP,
    VIDEO_ASPECT_FIT_16_9,
    VIDEO_ASPECT_FIT_9_16,
    VIDEO_ASPECT_FIT_1_1,
    VIDEO_ASPECT_CROP_16_9,
    VIDEO_ASPECT_CROP_9_16,
    VIDEO_ASPECT_CROP_1_1
)

internal const val AUDIO_VOLUME_MUTE = "Mute"
internal const val AUDIO_VOLUME_50 = "50%"
internal const val AUDIO_VOLUME_100 = "100%"
internal const val AUDIO_VOLUME_150 = "150%"
internal const val AUDIO_VOLUME_200 = "200%"
private val AUDIO_VOLUME_OPTIONS = listOf(
    AUDIO_VOLUME_100,
    AUDIO_VOLUME_50,
    AUDIO_VOLUME_150,
    AUDIO_VOLUME_200,
    AUDIO_VOLUME_MUTE
)

internal const val AUDIO_ECHO_OFF = "Echo off"
internal const val AUDIO_ECHO_LIGHT = "Light echo"
internal const val AUDIO_ECHO_ROOM = "Room echo"
private val AUDIO_ECHO_OPTIONS = listOf(
    AUDIO_ECHO_OFF,
    AUDIO_ECHO_LIGHT,
    AUDIO_ECHO_ROOM
)

internal const val AUDIO_DENOISE_OFF = "Noise reduction off"
internal const val AUDIO_DENOISE_LIGHT = "Light denoise"
internal const val AUDIO_DENOISE_STANDARD = "Standard denoise"
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
internal const val BATCH_MIXED_OPTION = "Mixed"
private val IMAGE_QUALITY_OPTIONS = listOf(
    IMAGE_QUALITY_ORIGINAL,
    IMAGE_QUALITY_HIGH,
    IMAGE_QUALITY_BALANCED,
    IMAGE_QUALITY_SMALL
)
internal const val IMAGE_SUPER_RESOLUTION_OFF = "Super resolution off"
internal const val IMAGE_SUPER_RESOLUTION_2X = "Bilinear 2×"
internal const val IMAGE_SUPER_RESOLUTION_3X = "Bilinear 3×"
internal const val IMAGE_SUPER_RESOLUTION_4X = "Bilinear 4×"
internal const val IMAGE_SUPER_RESOLUTION_AI_ANIME = "Real-ESRGAN Anime 4× (AI)"
internal const val IMAGE_SUPER_RESOLUTION_AI = "Real-ESRGAN 4× (AI)"
private val IMAGE_SUPER_RESOLUTION_OPTIONS = listOf(
    IMAGE_SUPER_RESOLUTION_OFF,
    IMAGE_SUPER_RESOLUTION_2X,
    IMAGE_SUPER_RESOLUTION_3X,
    IMAGE_SUPER_RESOLUTION_4X,
    IMAGE_SUPER_RESOLUTION_AI_ANIME,
    IMAGE_SUPER_RESOLUTION_AI
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

private const val PDF_COMPRESSION_PRESET_HIGH = "High quality"
private const val PDF_COMPRESSION_PRESET_BALANCED = "Balanced"
private const val PDF_COMPRESSION_PRESET_SMALL = "Small file"
private val PDF_COMPRESSION_PRESET_OPTIONS = listOf(
    PDF_COMPRESSION_PRESET_BALANCED,
    PDF_COMPRESSION_PRESET_HIGH,
    PDF_COMPRESSION_PRESET_SMALL
)

private const val GIF_FRAME_FIRST = "First frame"
private const val GIF_FRAME_IMAGES = "Split frames"
private const val GIF_FRAMES_SINGLE_PDF = "All frames in one PDF"
private const val GIF_FRAMES_PDF_FILES = "One PDF per frame"
private val GIF_IMAGE_FRAME_MODE_OPTIONS = listOf(
    GIF_FRAME_FIRST,
    GIF_FRAME_IMAGES
)
private val GIF_PDF_FRAME_MODE_OPTIONS = listOf(
    GIF_FRAME_FIRST,
    GIF_FRAMES_SINGLE_PDF,
    GIF_FRAMES_PDF_FILES
)

@Composable
fun ZenConverterApp(
    queuedFiles: List<QueuedFile>,
    pdfMergeGroups: List<PdfMergeGroup>,
    videoMergeGroups: List<VideoMergeGroup> = emptyList(),
    supportedVideoMimeTypes: Set<String>,
    outputLocationMode: OutputLocationMode,
    outputDirectory: OutputDirectory?,
    conversionTasks: List<TaskProgress>,
    conversionSummary: LocalizedText?,
    isConversionRunning: Boolean,
    metadataToolState: MetadataToolState,
    esrganModelStates: Map<String, EsrganModelUiState>,
    onOutputLocationModeChange: (OutputLocationMode) -> Unit,
    onPickFiles: () -> Unit,
    onPickAlbumImages: () -> Unit,
    onPickAlbumVideos: () -> Unit,
    onPickFolder: () -> Unit,
    onUpdateQueuedFile: (QueuedFile) -> Unit,
    onUpdateQueuedFiles: (List<QueuedFile>) -> Unit,
    onCreatePdfMergeGroup: (PdfMergeType) -> Unit,
    onUpdatePdfMergeGroup: (PdfMergeGroup) -> Unit,
    onRemovePdfMergeGroup: (String) -> Unit,
    onAddFileToPdfMergeGroup: (String, String) -> Unit,
    onRemoveFileFromPdfMergeGroup: (String, String) -> Unit,
    onCreateVideoMergeGroup: () -> Unit = {},
    onUpdateVideoMergeGroup: (VideoMergeGroup) -> Unit = {},
    onRemoveVideoMergeGroup: (String) -> Unit = {},
    onAddFileToVideoMergeGroup: (String, String) -> Unit = { _, _ -> },
    onRemoveFileFromVideoMergeGroup: (String, String) -> Unit = { _, _ -> },
    onPickOutputDirectory: () -> Unit,
    onRemoveFile: (String) -> Unit,
    onClearQueue: () -> Unit,
    onPickMetadataImage: () -> Unit,
    onPickMetadataVideo: () -> Unit,
    onCleanMetadata: () -> Unit,
    onRestoreMetadata: (String) -> Unit,
    onDownloadEsrganModel: (EsrganModelSpec) -> Unit,
    onCancelEsrganModelDownload: (EsrganModelSpec) -> Unit,
    rifeModelStates: Map<String, RifeModelUiState> = emptyMap(),
    onDownloadRifeModel: (RifeModelSpec) -> Unit = {},
    onCancelRifeModelDownload: (RifeModelSpec) -> Unit = {},
    officeFontStates: Map<String, OfficeFontUiState> = emptyMap(),
    onDownloadOfficeFont: (OfficeFontSpec) -> Unit = {},
    onCancelOfficeFontDownload: (OfficeFontSpec) -> Unit = {},
    onDeleteOfficeFont: (OfficeFontSpec) -> Unit = {},
    pdfPasswordPrompt: PdfPasswordPrompt?,
    pdfOutputPasswordPrompt: PdfOutputPasswordPrompt?,
    onSubmitPdfPassword: (String) -> Unit,
    onCancelPdfPassword: () -> Unit,
    onSubmitPdfOutputPassword: (String) -> Unit,
    onCancelPdfOutputPassword: () -> Unit,
    onStartConversion: () -> Unit,
    onCancelConversion: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val languageRevision by AppLanguages.revision.collectAsStateWithLifecycle()
    var accent by remember(context) {
        mutableStateOf(accentColorFromPreference(AppPreferences.accentColor(context)))
    }
    var themeModeOption by remember(context) {
        mutableStateOf(themeModeFromPreference(AppPreferences.themeMode(context)))
    }
    var isOledDark by remember(context) {
        mutableStateOf(AppPreferences.isOledDarkMode(context))
    }
    val languageOption = AppLanguages.selectedOption(context, configuration)
    val resourceContext = remember(context, configuration, languageRevision) { AppLanguages.localizedContext(context) }
    val texts = remember(resourceContext) { UiText(resourceContext) }
    val rootView = LocalView.current
    val isSystemDark = isSystemInDarkTheme()

    val isDark = when (themeModeOption) {
        ThemeModeOption.System -> isSystemDark
        ThemeModeOption.Light -> false
        ThemeModeOption.Dark -> true
    }
    val isOled = isDark && isOledDark

    DisposableEffect(rootView, isConversionRunning) {
        val previousKeepScreenOn = rootView.keepScreenOn
        rootView.keepScreenOn = isConversionRunning || previousKeepScreenOn
        onDispose {
            rootView.keepScreenOn = previousKeepScreenOn
        }
    }

    if (!rootView.isInEditMode) {
        DisposableEffect(rootView, isDark) {
            val window = (rootView.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, rootView)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
            onDispose {}
        }
    }

    CompositionLocalProvider(
        LocalContext provides resourceContext,
        LocalConfiguration provides resourceContext.resources.configuration,
        LocalLayoutDirection provides if (resourceContext.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) {
            androidx.compose.ui.unit.LayoutDirection.Rtl
        } else {
            androidx.compose.ui.unit.LayoutDirection.Ltr
        }
    ) {
        MaterialTheme(colorScheme = zenConverterColorScheme(context, accent, isDark, isOled)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                ZenConverterContent(
                    accent = accent,
                    themeModeOption = themeModeOption,
                    isOledDark = isOledDark,
                    languageOption = languageOption,
                    texts = texts,
                    queuedFiles = queuedFiles,
                    pdfMergeGroups = pdfMergeGroups,
                    videoMergeGroups = videoMergeGroups,
                    supportedVideoMimeTypes = supportedVideoMimeTypes,
                    outputLocationMode = outputLocationMode,
                    outputDirectory = outputDirectory,
                    conversionTasks = conversionTasks,
                    conversionSummary = conversionSummary,
                    isConversionRunning = isConversionRunning,
                    metadataToolState = metadataToolState,
                    esrganModelStates = esrganModelStates,
                    rifeModelStates = rifeModelStates,
                    officeFontStates = officeFontStates,
                    onAccentSelected = {
                        accent = it
                        AppPreferences.setAccentColor(context, it.name)
                    },
                    onThemeModeSelected = {
                        themeModeOption = it
                        AppPreferences.setThemeMode(context, it.name)
                    },
                    onOledDarkChange = { enabled ->
                        isOledDark = enabled
                        AppPreferences.setOledDarkMode(context, enabled)
                    },
                    onLanguageSelected = {
                        AppLanguages.select(it)
                    },
                    onOutputLocationModeChange = onOutputLocationModeChange,
                    onPickFiles = onPickFiles,
                    onPickAlbumImages = onPickAlbumImages,
                    onPickAlbumVideos = onPickAlbumVideos,
                    onPickFolder = onPickFolder,
                    onUpdateQueuedFile = onUpdateQueuedFile,
                    onUpdateQueuedFiles = onUpdateQueuedFiles,
                    onCreatePdfMergeGroup = onCreatePdfMergeGroup,
                    onUpdatePdfMergeGroup = onUpdatePdfMergeGroup,
                    onRemovePdfMergeGroup = onRemovePdfMergeGroup,
                    onAddFileToPdfMergeGroup = onAddFileToPdfMergeGroup,
                    onRemoveFileFromPdfMergeGroup = onRemoveFileFromPdfMergeGroup,
                    onCreateVideoMergeGroup = onCreateVideoMergeGroup,
                    onUpdateVideoMergeGroup = onUpdateVideoMergeGroup,
                    onRemoveVideoMergeGroup = onRemoveVideoMergeGroup,
                    onAddFileToVideoMergeGroup = onAddFileToVideoMergeGroup,
                    onRemoveFileFromVideoMergeGroup = onRemoveFileFromVideoMergeGroup,
                    onPickOutputDirectory = onPickOutputDirectory,
                    onRemoveFile = onRemoveFile,
                    onClearQueue = onClearQueue,
                    onPickMetadataImage = onPickMetadataImage,
                    onPickMetadataVideo = onPickMetadataVideo,
                    onCleanMetadata = onCleanMetadata,
                    onRestoreMetadata = onRestoreMetadata,
                    onDownloadEsrganModel = onDownloadEsrganModel,
                    onCancelEsrganModelDownload = onCancelEsrganModelDownload,
                    onDownloadRifeModel = onDownloadRifeModel,
                    onCancelRifeModelDownload = onCancelRifeModelDownload,
                    onDownloadOfficeFont = onDownloadOfficeFont,
                    onCancelOfficeFontDownload = onCancelOfficeFontDownload,
                    onDeleteOfficeFont = onDeleteOfficeFont,
                    onStartConversion = onStartConversion,
                    onCancelConversion = onCancelConversion
                )
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
}

@Composable
private fun ZenConverterContent(
    accent: AccentColorOption,
    themeModeOption: ThemeModeOption,
    isOledDark: Boolean,
    languageOption: LanguageOption,
    texts: UiText,
    queuedFiles: List<QueuedFile>,
    pdfMergeGroups: List<PdfMergeGroup>,
    videoMergeGroups: List<VideoMergeGroup> = emptyList(),
    supportedVideoMimeTypes: Set<String>,
    outputLocationMode: OutputLocationMode,
    outputDirectory: OutputDirectory?,
    conversionTasks: List<TaskProgress>,
    conversionSummary: LocalizedText?,
    isConversionRunning: Boolean,
    metadataToolState: MetadataToolState,
    esrganModelStates: Map<String, EsrganModelUiState>,
    rifeModelStates: Map<String, RifeModelUiState> = emptyMap(),
    officeFontStates: Map<String, OfficeFontUiState>,
    onAccentSelected: (AccentColorOption) -> Unit,
    onThemeModeSelected: (ThemeModeOption) -> Unit,
    onOledDarkChange: (Boolean) -> Unit,
    onLanguageSelected: (LanguageOption) -> Unit,
    onOutputLocationModeChange: (OutputLocationMode) -> Unit,
    onPickFiles: () -> Unit,
    onPickAlbumImages: () -> Unit,
    onPickAlbumVideos: () -> Unit,
    onPickFolder: () -> Unit,
    onUpdateQueuedFile: (QueuedFile) -> Unit,
    onUpdateQueuedFiles: (List<QueuedFile>) -> Unit,
    onCreatePdfMergeGroup: (PdfMergeType) -> Unit,
    onUpdatePdfMergeGroup: (PdfMergeGroup) -> Unit,
    onRemovePdfMergeGroup: (String) -> Unit,
    onAddFileToPdfMergeGroup: (String, String) -> Unit,
    onRemoveFileFromPdfMergeGroup: (String, String) -> Unit,
    onCreateVideoMergeGroup: () -> Unit = {},
    onUpdateVideoMergeGroup: (VideoMergeGroup) -> Unit = {},
    onRemoveVideoMergeGroup: (String) -> Unit = {},
    onAddFileToVideoMergeGroup: (String, String) -> Unit = { _, _ -> },
    onRemoveFileFromVideoMergeGroup: (String, String) -> Unit = { _, _ -> },
    onPickOutputDirectory: () -> Unit,
    onRemoveFile: (String) -> Unit,
    onClearQueue: () -> Unit,
    onPickMetadataImage: () -> Unit,
    onPickMetadataVideo: () -> Unit,
    onCleanMetadata: () -> Unit,
    onRestoreMetadata: (String) -> Unit,
    onDownloadEsrganModel: (EsrganModelSpec) -> Unit,
    onCancelEsrganModelDownload: (EsrganModelSpec) -> Unit,
    onDownloadRifeModel: (RifeModelSpec) -> Unit = {},
    onCancelRifeModelDownload: (RifeModelSpec) -> Unit = {},
    onDownloadOfficeFont: (OfficeFontSpec) -> Unit,
    onCancelOfficeFontDownload: (OfficeFontSpec) -> Unit,
    onDeleteOfficeFont: (OfficeFontSpec) -> Unit,
    onStartConversion: () -> Unit,
    onCancelConversion: () -> Unit
) {
    var activeHeaderPanel by rememberSaveable { mutableStateOf<HeaderPanel?>(null) }
    var showSupport by remember { mutableStateOf(false) }
    var showPrivacyPolicy by rememberSaveable { mutableStateOf(false) }
    var showHelpScreen by rememberSaveable { mutableStateOf(false) }
    var showImportSourceSheet by rememberSaveable { mutableStateOf(false) }
    var showAlbumSourceDialog by rememberSaveable { mutableStateOf(false) }
    var queueMessage by remember { mutableStateOf<LocalizedText?>(null) }
    var openMenuId by remember { mutableStateOf<String?>(null) }
    var expandedFileId by remember { mutableStateOf<String?>(null) }
    val homeListState = rememberLazyListState()

    val taskProgressById = conversionTasks.associateBy { it.fileId }
    val queueIds = queuedFiles.map { it.id }
    val pdfGroupedIds = pdfMergeGroups.flatMap { it.memberFileIds }.toSet()
    val videoGroupedIds = videoMergeGroups.flatMap { it.memberFileIds }.toSet()
    val groupedFileIds = pdfGroupedIds + videoGroupedIds
    val availableAiModels = buildSet {
        if (esrganModelStates[EsrganModelManager.MODEL_ANIME.id] is EsrganModelUiState.Downloaded) {
            add(IMAGE_SUPER_RESOLUTION_AI_ANIME)
        }
        if (esrganModelStates[EsrganModelManager.MODEL_X4PLUS.id] is EsrganModelUiState.Downloaded) {
            add(IMAGE_SUPER_RESOLUTION_AI)
        }
    }
    val isRifeModelDownloaded = rifeModelStates[RifeModelManager.MODEL_RIFE.id] is RifeModelUiState.Downloaded

    LaunchedEffect(queueIds) {
        // 默认收起；只清理已不存在的展开项，不在添加文件时自动展开。
        if (queueIds.isEmpty()) {
            expandedFileId = null
        } else if (expandedFileId != null && expandedFileId !in queueIds) {
            expandedFileId = null
        }
    }

    val statusMessage = conversionSummary ?: queueMessage
    var headerHeightPx by remember { mutableStateOf(0) }
    var renderedHeaderPanel by remember { mutableStateOf<HeaderPanel?>(null) }
    val headerPanelVisibleState = remember { MutableTransitionState(false) }

    LaunchedEffect(activeHeaderPanel) {
        if (activeHeaderPanel != null) {
            renderedHeaderPanel = activeHeaderPanel
            homeListState.scrollToItem(0)
        }
        headerPanelVisibleState.targetState = activeHeaderPanel != null
    }

    BackHandler(enabled = showPrivacyPolicy || showHelpScreen) {
        showPrivacyPolicy = false
        showHelpScreen = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NoOverscroll {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing
            ) { contentPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .consumeWindowInsets(contentPadding)
            ) {
                val density = LocalDensity.current
                val headerHeight = with(density) { headerHeightPx.toDp() }
                val headerAvailableWidth = headerContentWidth(maxWidth)
                val listTopPadding = HomeTopPadding + headerHeight + HeaderContentGap
                val hasFiles = queuedFiles.isNotEmpty()
                val keepPanelSlot = activeHeaderPanel != null ||
                    headerPanelVisibleState.currentState ||
                    headerPanelVisibleState.targetState
                val availableEmptyEntryHeight = run {
                    val available = maxHeight - listTopPadding - HomeBottomPadding
                    if (available > 280.dp) available else 280.dp
                }
                val emptyEntryHeightTarget = if (!hasFiles && activeHeaderPanel != null) {
                    EmptyPanelEntryHeight
                } else {
                    availableEmptyEntryHeight
                }
                val emptyLayoutSpring = if (activeHeaderPanel != null) {
                    ZenAnimations.PanelEnterDpSpring
                } else {
                    ZenAnimations.PanelExitDpSpring
                }
                val emptyEntryHeight by animateDpAsState(
                    targetValue = emptyEntryHeightTarget,
                    animationSpec = emptyLayoutSpring,
                    label = "emptyEntryHeight"
                )

                val morphProgress by animateFloatAsState(
                    targetValue = if (hasFiles) 1f else 0f,
                    animationSpec = ZenAnimations.HeroMorphSpring,
                    label = "heroMorphProgress"
                )
                val showEmptyStateItem = queuedFiles.isEmpty() || morphProgress < 1f
                val emptyPanelGap by animateDpAsState(
                    targetValue = if (activeHeaderPanel != null) HeaderContentGap else 0.dp,
                    animationSpec = emptyLayoutSpring,
                    label = "emptyPanelGap"
                )

                LazyColumn(
                    state = homeListState,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = HomeHorizontalPadding,
                        top = listTopPadding,
                        end = HomeHorizontalPadding,
                        bottom = HomeBottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (keepPanelSlot || showEmptyStateItem) {
                        item(key = "top-content") {
                            Column {
                                AnimatedVisibility(
                                    visibleState = headerPanelVisibleState,
                                    enter = ZenAnimations.PanelEnter,
                                    exit = ZenAnimations.PanelExit
                                ) {
                                    AnimatedContent(
                                        targetState = renderedHeaderPanel,
                                        transitionSpec = {
                                            (
                                                slideInVertically(
                                                    animationSpec = tween(
                                                        durationMillis = ZenAnimations.PanelSwitchDuration,
                                                        easing = ZenAnimations.StrongEaseInOut
                                                    ),
                                                    initialOffsetY = { fullHeight -> fullHeight / 12 }
                                                ) + fadeIn(
                                                    animationSpec = tween(
                                                        durationMillis = ZenAnimations.ContentFadeDuration,
                                                        easing = ZenAnimations.StrongEaseOut
                                                    )
                                                )
                                            ) togetherWith (
                                                slideOutVertically(
                                                    animationSpec = tween(
                                                        durationMillis = ZenAnimations.PanelSwitchDuration,
                                                        easing = ZenAnimations.StrongEaseInOut
                                                    ),
                                                    targetOffsetY = { fullHeight -> -fullHeight / 12 }
                                                ) + fadeOut(
                                                    animationSpec = tween(
                                                        durationMillis = ZenAnimations.ContentFadeOutDuration,
                                                        easing = ZenAnimations.StrongEaseOut
                                                    )
                                                )
                                            ) using SizeTransform(
                                                clip = false,
                                                sizeAnimationSpec = { _, _ ->
                                                    tween(
                                                        durationMillis = ZenAnimations.PanelSwitchDuration,
                                                        easing = ZenAnimations.StrongEaseInOut
                                                    )
                                                }
                                            )
                                        },
                                        label = "HeaderPanelSwitch"
                                    ) { panel ->
                                        when (panel) {
                                            HeaderPanel.Settings -> SettingsPanel(
                                                texts = texts,
                                                selectedAccent = accent,
                                                selectedThemeMode = themeModeOption,
                                                isOledDark = isOledDark,
                                                selectedLanguage = languageOption,
                                                outputLocationMode = outputLocationMode,
                                                outputDirectory = outputDirectory,
                                                esrganModelStates = esrganModelStates,
                                                rifeModelStates = rifeModelStates,
                                                officeFontStates = officeFontStates,
                                                onAccentSelected = onAccentSelected,
                                                onThemeModeSelected = onThemeModeSelected,
                                                onOledDarkChange = onOledDarkChange,
                                                onLanguageSelected = onLanguageSelected,
                                                onOutputLocationModeChange = onOutputLocationModeChange,
                                                onPickOutputDirectory = onPickOutputDirectory,
                                                onDownloadEsrganModel = onDownloadEsrganModel,
                                                onCancelEsrganModelDownload = onCancelEsrganModelDownload,
                                                onDownloadRifeModel = onDownloadRifeModel,
                                                onCancelRifeModelDownload = onCancelRifeModelDownload,
                                                onDownloadOfficeFont = onDownloadOfficeFont,
                                                onCancelOfficeFontDownload = onCancelOfficeFontDownload,
                                                onDeleteOfficeFont = onDeleteOfficeFont
                                            )
                                            HeaderPanel.About -> AboutPanel(
                                                texts = texts,
                                                onShowPrivacyPolicy = { showPrivacyPolicy = true },
                                                onShowHelp = { showHelpScreen = true },
                                                onShowSupport = { showSupport = true }
                                            )
                                            HeaderPanel.MetadataSecurity -> MetadataSecurityPanel(
                                                texts = texts,
                                                state = metadataToolState,
                                                onPickImage = onPickMetadataImage,
                                                onPickVideo = onPickMetadataVideo,
                                                onClean = onCleanMetadata,
                                                onRestore = onRestoreMetadata
                                            )
                                            null -> Unit
                                        }
                                    }
                                }

                                if (showEmptyStateItem) {
                                    Spacer(modifier = Modifier.height(emptyPanelGap))
                                    EmptyAddState(
                                        texts = texts,
                                        height = emptyEntryHeight,
                                        showButton = queuedFiles.isEmpty(),
                                        onPickFiles = {
                                            openMenuId = null
                                            queueMessage = null
                                            showImportSourceSheet = true
                                        },
                                        modifier = Modifier.graphicsLayer {
                                            alpha = 1f - morphProgress
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (queuedFiles.isNotEmpty() && !isConversionRunning) {
                        item(key = "batch-settings") {
                            BatchSettingsPanel(
                                texts = texts,
                                files = queuedFiles,
                                supportedVideoMimeTypes = supportedVideoMimeTypes,
                                availableAiModels = availableAiModels,
                                isRifeModelDownloaded = isRifeModelDownloaded,
                                openMenuId = openMenuId,
                                onOpenMenuChange = { openMenuId = it },
                                onUpdateFiles = onUpdateQueuedFiles
                            )
                        }
                    }
                    if (queuedFiles.isNotEmpty()) {
                        item(key = "pdf-merge-groups") {
                            PdfMergeGroupsPanel(
                                texts = texts,
                                files = queuedFiles,
                                groups = pdfMergeGroups,
                                taskProgress = taskProgressById,
                                canEdit = !isConversionRunning,
                                openMenuId = openMenuId,
                                onOpenMenuChange = { openMenuId = it },
                                onCreateGroup = onCreatePdfMergeGroup,
                                onUpdateGroup = onUpdatePdfMergeGroup,
                                onRemoveGroup = onRemovePdfMergeGroup,
                                onAddFileToGroup = onAddFileToPdfMergeGroup,
                                onRemoveFileFromGroup = onRemoveFileFromPdfMergeGroup
                            )
                        }
                        item(key = "video-merge-groups") {
                            VideoMergeGroupsPanel(
                                texts = texts,
                                files = queuedFiles,
                                groups = videoMergeGroups,
                                taskProgress = taskProgressById,
                                canEdit = !isConversionRunning,
                                openMenuId = openMenuId,
                                onOpenMenuChange = { openMenuId = it },
                                onCreateGroup = onCreateVideoMergeGroup,
                                onUpdateGroup = onUpdateVideoMergeGroup,
                                onRemoveGroup = onRemoveVideoMergeGroup,
                                onAddFileToGroup = onAddFileToVideoMergeGroup,
                                onRemoveFileFromGroup = onRemoveFileFromVideoMergeGroup
                            )
                        }
                    }

                    if (queuedFiles.isNotEmpty()) {
                        item(key = "queue-actions") {
                            QueueActions(
                                texts = texts,
                                isRunning = isConversionRunning,
                                onStart = {
                                    openMenuId = null
                                    queueMessage = null
                                    onStartConversion()
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

                        item(key = "queue-header") {
                            QueueHeader(
                                texts = texts,
                                fileCount = queuedFiles.size
                            )
                        }
                    }

                    statusMessage?.let { message ->
                        item(key = "status-line") {
                            StatusLine(text = texts.summaryMessage(message))
                        }
                    }

                    if (queuedFiles.isNotEmpty()) {
                        val pdfGroupByFileId = pdfMergeGroups.flatMap { group ->
                            group.memberFileIds.map { id -> id to group }
                        }.toMap()
                        val videoGroupByFileId = videoMergeGroups.flatMap { group ->
                            group.memberFileIds.map { id -> id to group }
                        }.toMap()
                        items(queuedFiles, key = { it.id }) { file ->
                            val effectiveProgress = taskProgressById[file.id]
                                ?: pdfGroupByFileId[file.id]?.let { taskProgressById[it.id] }
                                ?: videoGroupByFileId[file.id]?.let { taskProgressById[it.id] }
                            FileRow(
                                modifier = Modifier.animateItem(),
                                texts = texts,
                                file = file,
                                progress = effectiveProgress,
                                canEdit = !isConversionRunning,
                                supportedVideoMimeTypes = supportedVideoMimeTypes,
                                availableAiModels = availableAiModels,
                                isRifeModelDownloaded = isRifeModelDownloaded,
                                openMenuId = openMenuId,
                                optionsExpanded = expandedFileId == file.id,
                                groupedInPdfMerge = file.id in pdfGroupedIds,
                                groupedInVideoMerge = file.id in videoGroupedIds,
                                onOpenMenuChange = { openMenuId = it },
                                onUpdateFile = onUpdateQueuedFile,
                                onOptionsExpandedChange = { expanded ->
                                    expandedFileId = if (expanded) file.id else null
                                    if (!expanded) openMenuId = null
                                },
                                onRemove = { onRemoveFile(file.id) }
                            )
                        }
                    }
                }

                // Header stays outside the LazyColumn so it remains pinned while content scrolls.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(
                            start = HomeHorizontalPadding,
                            top = HomeTopPadding,
                            end = HomeHorizontalPadding,
                            bottom = HeaderContentGap
                        )
                ) {
                    Header(
                        texts = texts,
                        activeHeaderPanel = activeHeaderPanel,
                        hasFiles = hasFiles,
                        onTogglePanel = { panel ->
                            openMenuId = null
                            activeHeaderPanel = if (activeHeaderPanel == panel) null else panel
                        },
                        modifier = Modifier.onSizeChanged { size ->
                            if (headerHeightPx != size.height) {
                                headerHeightPx = size.height
                            }
                        }
                    )
                }

                val rightGroupWidth = headerActionsWidth(
                    availableWidth = headerAvailableWidth,
                    hasFiles = hasFiles
                )

                val currentHeroSize = ZenAnimations.HeroCenterSize +
                    (ZenAnimations.HeroHeaderSize - ZenAnimations.HeroCenterSize) * morphProgress

                val heroX = androidx.compose.ui.unit.lerp(
                    (maxWidth - currentHeroSize.dp) / 2,
                    HomeHorizontalPadding + headerAvailableWidth - rightGroupWidth - HeaderAddSlotWidth,
                    morphProgress
                )

                val emptyCenterY = listTopPadding +
                    (emptyEntryHeight - currentHeroSize.dp) / 2 - 36.dp
                val heroY = androidx.compose.ui.unit.lerp(
                    emptyCenterY,
                    HomeTopPadding,
                    morphProgress
                )

                if (hasFiles || morphProgress > 0f) {
                    HeroAddButton(
                        morphProgress = morphProgress,
                        texts = texts,
                        enabled = !isConversionRunning,
                        onPickFiles = {
                            openMenuId = null
                            queueMessage = null
                            showImportSourceSheet = true
                        },
                        modifier = Modifier.offset(x = heroX, y = heroY)
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

        if (showImportSourceSheet) {
            ImportSourceSheet(
                texts = texts,
                onDismiss = { showImportSourceSheet = false },
                onPickAlbum = {
                    showImportSourceSheet = false
                    showAlbumSourceDialog = true
                },
                onPickFolder = {
                    showImportSourceSheet = false
                    onPickFolder()
                },
                onPickFiles = {
                    showImportSourceSheet = false
                    onPickFiles()
                }
            )
        }

        if (showAlbumSourceDialog) {
            AlbumSourceDialog(
                texts = texts,
                onDismiss = { showAlbumSourceDialog = false },
                onPickImages = {
                    showAlbumSourceDialog = false
                    onPickAlbumImages()
                },
                onPickVideos = {
                    showAlbumSourceDialog = false
                    onPickAlbumVideos()
                }
            )
        }
        }

        AnimatedVisibility(
            visible = showPrivacyPolicy,
            modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(
                animationSpec = tween(
                    durationMillis = ZenAnimations.PageEnterDuration,
                    easing = ZenAnimations.StrongEaseOut
                ),
                initialOffsetY = { fullHeight -> fullHeight / 12 }
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = ZenAnimations.PageEnterDuration,
                    easing = ZenAnimations.StrongEaseOut
                )
            ),
            exit = slideOutVertically(
                animationSpec = tween(
                    durationMillis = ZenAnimations.PageExitDuration,
                    easing = ZenAnimations.StrongEaseOut
                ),
                targetOffsetY = { fullHeight -> fullHeight / 12 }
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = ZenAnimations.PageExitDuration,
                    easing = ZenAnimations.StrongEaseOut
                )
            )
        ) {
            PrivacyPolicyScreen(
                policy = texts.privacyPolicy,
                linkUnavailable = texts.linkUnavailable,
                onBack = { showPrivacyPolicy = false }
            )
        }

        AnimatedVisibility(
            visible = showHelpScreen,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(animationSpec = tween(ZenAnimations.PageEnterDuration)) +
                slideInVertically(animationSpec = tween(ZenAnimations.PageEnterDuration), initialOffsetY = { it / 12 }),
            exit = fadeOut(animationSpec = tween(ZenAnimations.PageExitDuration)) +
                slideOutVertically(animationSpec = tween(ZenAnimations.PageExitDuration), targetOffsetY = { it / 12 })
        ) {
            HelpScreen(
                copy = texts.helpGuide,
                onBack = { showHelpScreen = false }
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
private fun CenteredFlowRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        val horizontalSpacingPx = horizontalSpacing.roundToPx()
        val verticalSpacingPx = verticalSpacing.roundToPx()
        val maxWidth = constraints.maxWidth
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        }
        val rows = mutableListOf<MutableList<Placeable>>()
        val rowWidths = mutableListOf<Int>()
        val rowHeights = mutableListOf<Int>()
        var currentRow = mutableListOf<Placeable>()
        var currentWidth = 0
        var currentHeight = 0

        fun commitRow() {
            if (currentRow.isEmpty()) return
            rows.add(currentRow)
            rowWidths.add(currentWidth)
            rowHeights.add(currentHeight)
            currentRow = mutableListOf()
            currentWidth = 0
            currentHeight = 0
        }

        placeables.forEach { placeable ->
            val nextWidth = if (currentRow.isEmpty()) {
                placeable.width
            } else {
                currentWidth + horizontalSpacingPx + placeable.width
            }
            if (currentRow.isNotEmpty() && nextWidth > maxWidth) {
                commitRow()
            }
            currentWidth = if (currentRow.isEmpty()) {
                placeable.width
            } else {
                currentWidth + horizontalSpacingPx + placeable.width
            }
            currentHeight = maxOf(currentHeight, placeable.height)
            currentRow.add(placeable)
        }
        commitRow()

        val contentHeight = rowHeights.sum() +
            verticalSpacingPx * (rows.size - 1).coerceAtLeast(0)
        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            rowWidths.maxOrNull() ?: 0
        }
        val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        val layoutWidth = width.coerceIn(constraints.minWidth, constraints.maxWidth)

        layout(width = layoutWidth, height = height) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                val rowWidth = rowWidths[rowIndex]
                var x = ((layoutWidth - rowWidth) / 2).coerceAtLeast(0)
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + horizontalSpacingPx
                }
                y += rowHeights[rowIndex] + verticalSpacingPx
            }
        }
    }
}

@Composable
private fun ExternalImportTargetChip(
    texts: UiText,
    target: ExternalImportTarget,
    selected: Boolean,
    onSelected: () -> Unit
) {
    val label = texts.externalImportTargetLabel(target)
    if (selected) {
        Button(
            onClick = onSelected,
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onSelected,
            shape = RoundedCornerShape(100.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
        val dialogScale by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(ZenAnimations.DialogScaleDuration),
            label = "dialogScale"
        )
        val dialogAlpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(ZenAnimations.DialogScaleDuration),
            label = "dialogAlpha"
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .heightIn(max = 520.dp)
                .graphicsLayer {
                    scaleX = ZenAnimations.DialogScaleFrom +
                        (1f - ZenAnimations.DialogScaleFrom) * dialogScale
                    scaleY = scaleX
                    alpha = dialogAlpha
                },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
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
    activeHeaderPanel: HeaderPanel?,
    hasFiles: Boolean,
    onTogglePanel: (HeaderPanel) -> Unit,
    modifier: Modifier = Modifier
) {
    val showMetadataSecurity = activeHeaderPanel == HeaderPanel.MetadataSecurity
    val showAbout = activeHeaderPanel == HeaderPanel.About
    val showSettings = activeHeaderPanel == HeaderPanel.Settings
    val headerActions = listOf(
        HeaderAction(
            icon = if (showMetadataSecurity) Icons.Rounded.Close else Icons.Rounded.Security,
            label = if (showMetadataSecurity) texts.closeMetadataSecurity else texts.openMetadataSecurity,
            active = showMetadataSecurity,
            onClick = { onTogglePanel(HeaderPanel.MetadataSecurity) }
        ),
        HeaderAction(
            icon = if (showAbout) Icons.Rounded.Close else Icons.Rounded.ErrorOutline,
            label = if (showAbout) texts.closeAbout else texts.openAbout,
            active = showAbout,
            onClick = { onTogglePanel(HeaderPanel.About) }
        ),
        HeaderAction(
            icon = if (showSettings) Icons.Rounded.Close else Icons.Rounded.Settings,
            label = if (showSettings) texts.closeSettings else texts.openSettings,
            active = showSettings,
            onClick = { onTogglePanel(HeaderPanel.Settings) }
        )
    )

    val spacerWidth by animateDpAsState(
        targetValue = if (hasFiles) HeaderAddSlotWidth else 0.dp,
        animationSpec = ZenAnimations.HeroMorphDpSpring,
        label = "headerSpacerWidth"
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        val actionMode = headerActionMode(availableWidth, hasFiles)
        val secondaryActions = headerActions.take(2)
        val settingsAction = headerActions.last()

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
                        text = stringResource(R.string.app_name),
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(spacerWidth))
                
                when {
                    actionMode == HeaderActionMode.AllInline -> {
                        HeaderActions(actions = headerActions)
                    }
                    actionMode == HeaderActionMode.SettingsInline -> {
                        HeaderActions(actions = listOf(settingsAction))
                        Spacer(modifier = Modifier.width(HeaderActionSpacing))
                        HeaderOverflowActions(
                            actions = secondaryActions,
                            texts = texts
                        )
                    }
                    else -> {
                        HeaderOverflowActions(
                            actions = headerActions,
                            texts = texts
                        )
                    }
                }
            }
        }
    }
}

private data class HeaderAction(
    val icon: ImageVector,
    val label: String,
    val active: Boolean,
    val onClick: () -> Unit
)

private enum class HeaderActionMode {
    AllInline,
    SettingsInline,
    OverflowOnly
}

private enum class HeaderPanel {
    Settings,
    About,
    MetadataSecurity
}

private fun headerContentWidth(containerWidth: Dp): Dp {
    val horizontalPadding = HomeHorizontalPadding + HomeHorizontalPadding
    return if (containerWidth > horizontalPadding) {
        containerWidth - horizontalPadding
    } else {
        0.dp
    }
}

private fun headerActionMode(
    availableWidth: Dp,
    hasFiles: Boolean
): HeaderActionMode {
    val allInlineWidth = if (hasFiles) 430.dp else 380.dp
    val settingsInlineWidth = if (hasFiles) 340.dp else 300.dp
    return when {
        availableWidth >= allInlineWidth -> HeaderActionMode.AllInline
        availableWidth >= settingsInlineWidth -> HeaderActionMode.SettingsInline
        else -> HeaderActionMode.OverflowOnly
    }
}

private fun headerActionsWidth(
    availableWidth: Dp,
    hasFiles: Boolean
): Dp {
    return when (headerActionMode(availableWidth, hasFiles)) {
        HeaderActionMode.AllInline ->
            HeaderButtonSize + HeaderActionSpacing + HeaderButtonSize +
                HeaderActionSpacing + HeaderButtonSize
        HeaderActionMode.SettingsInline ->
            HeaderButtonSize + HeaderActionSpacing + HeaderButtonSize
        HeaderActionMode.OverflowOnly -> HeaderButtonSize
    }
}

@Composable
private fun HeaderOverflowActions(
    actions: List<HeaderAction>,
    texts: UiText
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        HeaderIconButton(
            onClick = { expanded = true },
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = texts.moreHeaderActions,
            active = actions.any { action -> action.active }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(8.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                    leadingIcon = {
                        AppIcon(
                            icon = action.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun HeaderActions(actions: List<HeaderAction>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(HeaderActionSpacing)
    ) {
        actions.forEach { action ->
            HeaderIconButton(
                onClick = action.onClick,
                icon = action.icon,
                contentDescription = action.label,
                active = action.active
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    active: Boolean
) {
    val bgColor by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(ZenAnimations.ContentFadeDuration),
        label = "headerBtnBg"
    )
    Box(
        modifier = Modifier
            .size(HeaderButtonSize)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = bgColor * 0.08f),
                CircleShape
            )
            .border(
                1.dp,
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                CircleShape
            )
            .bounceClick(onClick = onClick, scaleDown = 0.90f)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = icon,
            animationSpec = tween(ZenAnimations.ContentFadeDuration),
            label = "headerIconCrossfade"
        ) { targetIcon ->
            AppIcon(
                icon = targetIcon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    texts: UiText,
    selectedAccent: AccentColorOption,
    selectedThemeMode: ThemeModeOption,
    isOledDark: Boolean,
    selectedLanguage: LanguageOption,
    outputLocationMode: OutputLocationMode,
    outputDirectory: OutputDirectory?,
    esrganModelStates: Map<String, EsrganModelUiState>,
    rifeModelStates: Map<String, RifeModelUiState>,
    officeFontStates: Map<String, OfficeFontUiState>,
    onAccentSelected: (AccentColorOption) -> Unit,
    onThemeModeSelected: (ThemeModeOption) -> Unit,
    onOledDarkChange: (Boolean) -> Unit,
    onLanguageSelected: (LanguageOption) -> Unit,
    onOutputLocationModeChange: (OutputLocationMode) -> Unit,
    onPickOutputDirectory: () -> Unit,
    onDownloadEsrganModel: (EsrganModelSpec) -> Unit,
    onCancelEsrganModelDownload: (EsrganModelSpec) -> Unit,
    onDownloadRifeModel: (RifeModelSpec) -> Unit,
    onCancelRifeModelDownload: (RifeModelSpec) -> Unit,
    onDownloadOfficeFont: (OfficeFontSpec) -> Unit,
    onCancelOfficeFontDownload: (OfficeFontSpec) -> Unit,
    onDeleteOfficeFont: (OfficeFontSpec) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    QuietPanel {
        SectionTitle(
            icon = Icons.Rounded.FolderOpen,
            title = texts.output
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutputLocationSection(
            texts = texts,
            outputLocationMode = outputLocationMode,
            outputDirectory = outputDirectory,
            onOutputLocationModeChange = onOutputLocationModeChange,
            onPickOutputDirectory = onPickOutputDirectory
        )

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(
            icon = Icons.Rounded.Palette,
            title = texts.accentColor
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val availableAccents = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AccentColorOption.entries
                } else {
                    AccentColorOption.entries.filter { it != AccentColorOption.Dynamic }
                }
            }
            availableAccents.forEach { option ->
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
            icon = Icons.Rounded.DarkMode,
            title = texts.themeMode
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeModeOption.entries.forEach { option ->
                val selected = option == selectedThemeMode
                val optionIcon = when (option) {
                    ThemeModeOption.System -> Icons.Rounded.BrightnessAuto
                    ThemeModeOption.Light -> Icons.Rounded.LightMode
                    ThemeModeOption.Dark -> Icons.Rounded.DarkMode
                }
                if (selected) {
                    Button(
                        onClick = { onThemeModeSelected(option) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = optionIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(texts.themeModeLabel(option))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onThemeModeSelected(option) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = optionIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(texts.themeModeLabel(option))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectedThemeMode != ThemeModeOption.Light,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                AdvancedSwitchRow(
                    label = texts.usePureBlackTheme,
                    checked = isOledDark,
                    onCheckedChange = onOledDarkChange
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
            AppLanguages.options(LocalContext.current).forEach { option ->
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

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(
            icon = Icons.Rounded.Download,
            title = texts.modelDownload
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EsrganModelManager.ALL_MODELS.forEach { spec ->
                EsrganModelDownloadSection(
                    texts = texts,
                    spec = spec,
                    state = esrganModelStates[spec.id] ?: EsrganModelUiState.NotDownloaded,
                    onDownload = { onDownloadEsrganModel(spec) },
                    onCancel = { onCancelEsrganModelDownload(spec) }
                )
            }
            RifeModelManager.ALL_MODELS.forEach { spec ->
                RifeModelDownloadSection(
                    texts = texts,
                    spec = spec,
                    state = rifeModelStates[spec.id] ?: RifeModelUiState.NotDownloaded,
                    onDownload = { onDownloadRifeModel(spec) },
                    onCancel = { onCancelRifeModelDownload(spec) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(
            icon = Icons.Rounded.FontDownload,
            title = texts.officeFontTitle
        )
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = if (isDark) Color(0xFF064E3B).copy(alpha = 0.35f) else Color(0xFFF0FDF4),
            border = BorderStroke(1.dp, if (isDark) Color(0xFF059669).copy(alpha = 0.45f) else Color(0xFFDCFCE7))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    icon = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = texts.officeFontSystemReady,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color(0xFF86EFAC) else Color(0xFF15803D)
                    )
                    Text(
                        text = texts.officeFontSystemNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color(0xFF4ADE80) else Color(0xFF166534)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = texts.officeFontEnhancementNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OfficeFontManager.ALL_FONTS.forEach { spec ->
                OfficeFontDownloadSection(
                    texts = texts,
                    spec = spec,
                    state = officeFontStates[spec.id] ?: OfficeFontUiState.NotDownloaded,
                    onDownload = { onDownloadOfficeFont(spec) },
                    onCancel = { onCancelOfficeFontDownload(spec) },
                    onDelete = { onDeleteOfficeFont(spec) }
                )
            }
        }
    }
}

@Composable
private fun EsrganModelDownloadSection(
    texts: UiText,
    spec: EsrganModelSpec,
    state: EsrganModelUiState,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val purposeText = if (spec.id == EsrganModelManager.MODEL_ANIME.id) {
        texts.modelPurposeAnime
    } else {
        texts.modelPurpose
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spec.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = purposeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatBytes(spec.totalSizeBytes, texts),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.display_esrgan_model_download_section_1_s_real_esrgan, texts.modelSource),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = texts.openLink,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        openExternalLink(context, spec.sourceUrl, texts.linkUnavailable)
                    }
                )
            }

            when (state) {
                EsrganModelUiState.NotDownloaded -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(texts.modelDownloadAction)
                    }
                }
                is EsrganModelUiState.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = state.progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.display_esrgan_model_download_section_1_s, (state.progress * 100).toInt()),
                                style = MaterialTheme.typography.labelMedium
                            )
                            TextButton(onClick = onCancel) {
                                Text(texts.cancelDownload)
                            }
                        }
                        Text(
                            text = texts.modelDownloadNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                EsrganModelUiState.Downloaded -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(
                                icon = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = texts.modelDownloaded,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(onClick = onDownload) {
                            Text(texts.modelRedownload)
                        }
                    }
                }
                is EsrganModelUiState.Failed -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = state.message?.resolve(LocalContext.current)
                                ?: texts.downloadFailed,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(texts.modelDownloadAction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RifeModelDownloadSection(
    texts: UiText,
    spec: RifeModelSpec,
    state: RifeModelUiState,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spec.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = texts.rifeModelPurpose,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatBytes(spec.totalSizeBytes, texts),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.display_rife_model_download_section_1_s_rife, texts.modelSource),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = texts.openLink,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        openExternalLink(context, spec.sourceUrl, texts.linkUnavailable)
                    }
                )
            }

            when (state) {
                RifeModelUiState.NotDownloaded -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(texts.modelDownloadAction)
                    }
                }
                is RifeModelUiState.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = state.progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.display_esrgan_model_download_section_1_s, (state.progress * 100).toInt()),
                                style = MaterialTheme.typography.labelMedium
                            )
                            TextButton(onClick = onCancel) {
                                Text(texts.cancelDownload)
                            }
                        }
                        Text(
                            text = texts.modelDownloadNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                RifeModelUiState.Downloaded -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(
                                icon = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = texts.modelDownloaded,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(onClick = onDownload) {
                            Text(texts.modelRedownload)
                        }
                    }
                }
                is RifeModelUiState.Failed -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = state.message?.resolve(LocalContext.current)
                                ?: texts.downloadFailed,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(texts.modelDownloadAction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfficeFontDownloadSection(
    texts: UiText,
    spec: OfficeFontSpec,
    state: OfficeFontUiState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = texts.taskMessage(spec.displayName),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = texts.taskMessage(spec.description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatBytes(spec.sizeBytes, texts),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.display_office_font_download_section_1_s_google_noto_cjk_sil_ofl_1_1, texts.officeFontSource),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (state) {
                OfficeFontUiState.NotDownloaded -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(texts.modelDownloadAction)
                    }
                }
                is OfficeFontUiState.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = state.progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.display_esrgan_model_download_section_1_s, (state.progress * 100).toInt()),
                                style = MaterialTheme.typography.labelMedium
                            )
                            TextButton(onClick = onCancel) {
                                Text(texts.cancelDownload)
                            }
                        }
                        Text(
                            text = texts.modelDownloadNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OfficeFontUiState.Downloaded -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(
                                icon = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = texts.modelDownloaded,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row {
                            TextButton(onClick = onDownload) {
                                Text(texts.modelRedownload)
                            }
                        }
                    }
                }
                is OfficeFontUiState.Failed -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = state.message?.resolve(LocalContext.current)
                                ?: texts.downloadFailed,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(texts.modelDownloadAction)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutPanel(
    texts: UiText,
    onShowPrivacyPolicy: () -> Unit,
    onShowHelp: () -> Unit,
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
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            )
            Text(
                text = stringResource(R.string.app_name),
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onShowHelp,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
            ) {
                AppIcon(
                    icon = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = texts.helpGuide.help, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onShowPrivacyPolicy,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
            ) {
                AppIcon(
                    icon = Icons.Rounded.PrivacyTip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = texts.privacyPolicy.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (BuildConfig.ENABLE_GITHUB_UPDATES) {
            Spacer(modifier = Modifier.height(10.dp))

            UpdatePanel(
                texts = texts,
                installedVersion = installedVersion
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onShowSupport,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
        ) {
            AppIcon(
                icon = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
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
private fun HelpScreen(
    copy: HelpGuideCopy,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    AppIcon(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = copy.back,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = copy.help,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth()) {
                    Text(
                        text = copy.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(copy.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                GuideFlow(copy)
            }
            items(
                listOf(
                    GuideCardData(Icons.Rounded.Videocam, copy.videoTitle, copy.videoBody, copy.videoFormats),
                    GuideCardData(Icons.Rounded.AudioFile, copy.audioTitle, copy.audioBody, copy.audioFormats),
                    GuideCardData(Icons.Rounded.Image, copy.imageTitle, copy.imageBody, copy.imageFormats),
                    GuideCardData(Icons.Rounded.Description, copy.documentTitle, copy.documentBody, copy.documentFormats),
                    GuideCardData(Icons.Rounded.FontDownload, copy.fontTitle, copy.fontBody, copy.fontFormats),
                    GuideCardData(Icons.Rounded.Subtitles, copy.subtitleTitle, copy.subtitleBody, copy.subtitleFormats)
                )
            ) { card ->
                GuideCard(card)
            }
        }
    }
}

private data class GuideCardData(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val formats: String
)

@Composable
private fun GuideFlow(copy: HelpGuideCopy) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(12.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        GuideFlowStep(Icons.Rounded.FolderOpen, copy.flowInput)
        AppIcon(Icons.Rounded.PlayArrow, null, MaterialTheme.colorScheme.primary, Modifier.size(18.dp))
        GuideFlowStep(Icons.Rounded.Settings, copy.flowProcess)
        AppIcon(Icons.Rounded.PlayArrow, null, MaterialTheme.colorScheme.primary, Modifier.size(18.dp))
        GuideFlowStep(Icons.Rounded.Check, copy.flowOutput)
    }
}

@Composable
private fun GuideFlowStep(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 64.dp, max = 110.dp)) {
        Box(modifier = Modifier.size(38.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            AppIcon(icon, null, MaterialTheme.colorScheme.onPrimary, Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
    }
}

@Composable
private fun GuideCard(card: GuideCardData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                AppIcon(card.icon, null, MaterialTheme.colorScheme.primary, Modifier.size(23.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(card.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(card.formats, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PrivacyPolicyScreen(
    policy: PrivacyPolicyText,
    linkUnavailable: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    AppIcon(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = policy.back,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = policy.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = 36.dp
                ),
                verticalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                item(key = "privacy-introduction") {
                    PrivacyPolicyContent {
                        Text(
                            text = policy.updated,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcon(
                                    icon = Icons.Rounded.PrivacyTip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = policy.intro,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                items(
                    items = policy.sections,
                    key = { section -> section.title }
                ) { section ->
                    PrivacyPolicyContent {
                        PrivacyPolicySectionContent(section)
                    }
                }

                item(key = "privacy-project-page") {
                    PrivacyPolicyContent {
                        OutlinedButton(
                            onClick = {
                                openExternalLink(
                                    context,
                                    ZENCONVERTER_REPOSITORY_URL,
                                    linkUnavailable
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = policy.projectPage,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AppIcon(
                                icon = Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

            }

        }
    }
}

@Composable
private fun PrivacyPolicyContent(
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(),
            content = content
        )
    }
}

@Composable
private fun PrivacyPolicySectionContent(section: PrivacyPolicySection) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    section.paragraphs.forEachIndexed { index, paragraph ->
        if (index > 0) {
            Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
            text = paragraph,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetadataSecurityPanel(
    texts: UiText,
    state: MetadataToolState,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onClean: () -> Unit,
    onRestore: (String) -> Unit
) {
    QuietPanel {
        SectionTitle(
            icon = Icons.Rounded.Security,
            title = texts.metadataSecurityTitle
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = texts.metadataSecurityNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onPickImage,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                AppIcon(
                    icon = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(texts.pickMetadataImage, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onPickVideo,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                AppIcon(
                    icon = Icons.Rounded.Videocam,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(texts.pickMetadataVideo, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        StatusLine(text = texts.metadataBackupNote)

        Spacer(modifier = Modifier.height(12.dp))
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(animationSpec = tween(ZenAnimations.ContentFadeDuration)) togetherWith
                fadeOut(animationSpec = tween(ZenAnimations.ContentFadeOutDuration)) using
                SizeTransform(clip = false)
            },
            label = "MetadataState"
        ) { targetState ->
            Column(modifier = Modifier.fillMaxWidth()) {
                when (targetState) {
                    MetadataToolState.Empty -> {
                        Text(
                            text = texts.metadataEmpty,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MetadataToolState.Loading -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = texts.processing,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is MetadataToolState.Error -> {
                        StatusLine(
                            text = texts.metadataMessage(targetState.message),
                            isError = true
                        )
                    }
                    is MetadataToolState.Ready -> {
                        MetadataInspectionCard(
                            texts = texts,
                            state = targetState,
                            onClean = onClean,
                            onRestore = onRestore
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataInspectionCard(
    texts: UiText,
    state: MetadataToolState.Ready,
    onClean: () -> Unit,
    onRestore: (String) -> Unit
) {
    val inspection = state.inspection
    var showDetails by remember(inspection.uri, state.message) { mutableStateOf(false) }
    var showRestoreChoices by remember(inspection.uri, inspection.backups) { mutableStateOf(false) }
    val metadataNotice = state.message ?: when {
        inspection.kind == MetadataTargetKind.Image && !inspection.editable ->
            MetadataStatusMessage(inspection.unsupportedMessage ?: MetadataMessageKey.UnsupportedImageFormat)
        inspection.kind == MetadataTargetKind.Image && !inspection.canWrite ->
            MetadataStatusMessage(MetadataMessageKey.WritePermissionNeeded)
        inspection.kind == MetadataTargetKind.Image && !inspection.hasRemovableMetadata ->
            MetadataStatusMessage(MetadataMessageKey.NoRemovableMetadata)
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
            ) {
                Text(
                    text = inspection.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = metadataPrimaryInfoLine(inspection, texts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SmallTag(texts.metadataKindLabel(inspection.kind))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallTag(texts.metadataSupportLabel(inspection))
            if (inspection.kind == MetadataTargetKind.Image) {
                SmallTag(texts.yesNoLabel(inspection.hasGps, texts.metadataGps))
                if (inspection.editable) {
                    SmallTag(texts.metadataBackupCountLabel(inspection.backups.size))
                }
            }
        }

        MetadataCompactRows(
            rows = metadataCompactRows(inspection, texts)
        )

        metadataNotice?.let { message ->
            StatusLine(
                text = texts.metadataMessage(message),
                isError = message.key !in setOf(
                    MetadataMessageKey.Cleaned,
                    MetadataMessageKey.Restored,
                    MetadataMessageKey.NoRemovableMetadata
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { showDetails = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(texts.metadataDetails, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (inspection.kind == MetadataTargetKind.Image) {
                Button(
                    onClick = onClean,
                    enabled = inspection.editable &&
                        inspection.hasRemovableMetadata &&
                        inspection.canWrite &&
                        !state.busy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (state.busy) texts.processing else texts.metadataCleanAndBackup,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (inspection.kind == MetadataTargetKind.Image) {
            OutlinedButton(
                onClick = {
                    if (inspection.backups.size == 1) {
                        onRestore(inspection.backups.first().id)
                    } else {
                        showRestoreChoices = true
                    }
                },
                enabled = inspection.editable &&
                    inspection.backups.isNotEmpty() &&
                    inspection.canWrite &&
                    !state.busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.045f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(texts.metadataRestore, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    if (showDetails) {
        MetadataDetailsDialog(
            texts = texts,
            inspection = inspection,
            onDismiss = { showDetails = false }
        )
    }
    if (showRestoreChoices) {
        MetadataRestoreDialog(
            texts = texts,
            backups = inspection.backups,
            onRestore = { backupId ->
                showRestoreChoices = false
                onRestore(backupId)
            },
            onDismiss = { showRestoreChoices = false }
        )
    }
}

@Composable
private fun MetadataCompactRows(rows: List<Pair<String, String>>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.38f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.62f)
                )
            }
        }
    }
}

@Composable
private fun MetadataDetailsDialog(
    texts: UiText,
    inspection: MetadataInspection,
    onDismiss: () -> Unit
) {
    ZenPromptFrame(onDismissRequest = onDismiss) {
        SectionTitle(
            icon = Icons.Rounded.Security,
            title = texts.metadataDetails
        )
        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MetadataCompactRows(rows = metadataDetailRows(inspection, texts))
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(texts.optionValue("Close"))
        }
    }
}

@Composable
private fun MetadataRestoreDialog(
    texts: UiText,
    backups: List<MetadataBackupInfo>,
    onRestore: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ZenPromptFrame(onDismissRequest = onDismiss) {
        SectionTitle(
            icon = Icons.Rounded.Security,
            title = texts.metadataRestoreTitle
        )
        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            backups.forEachIndexed { index, backup ->
                OutlinedButton(
                    onClick = { onRestore(backup.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (index == 0) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (index == 0) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.045f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = texts.metadataBackupLabel(backup, recommended = index == 0),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(texts.cancel)
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
                downloadState = UpdateDownloadUiState.Failed(exception.localizedFailure(R.string.ui_download_failed))
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
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                Text(
                    text = texts.sponsorNoBenefits,
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
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        AppIcon(
                            icon = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
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
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
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
    val context = LocalContext.current
    val label = texts.accentLabel(option)
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    val (swatchColor, swatchContentColor) = if (option == AccentColorOption.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val scheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        Pair(scheme.primary, scheme.onPrimary)
    } else {
        Pair(option.color(isDark), option.contentColor(isDark))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
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
        ) {
            if (option == AccentColorOption.Dynamic) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = swatchContentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
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
private fun EmptyAddState(
    texts: UiText,
    height: Dp,
    showButton: Boolean,
    onPickFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        if (showButton) {
            HeroAddButton(
                morphProgress = 0f,
                texts = texts,
                onPickFiles = onPickFiles,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = EmptyHeroButtonOffsetY)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = EmptyHeroTextOffsetY)
        ) {
            Text(
                text = texts.addFilesTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = texts.addFilesNote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.82f)
            )
        }
    }
}

@Composable
private fun HeroAddButton(
    morphProgress: Float,
    onPickFiles: () -> Unit,
    texts: UiText,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val size = ZenAnimations.HeroCenterSize + (ZenAnimations.HeroHeaderSize - ZenAnimations.HeroCenterSize) * morphProgress
    val iconSize = ZenAnimations.HeroCenterIconSize + (ZenAnimations.HeroHeaderIconSize - ZenAnimations.HeroCenterIconSize) * morphProgress

    val containerColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
        animationSpec = tween(ZenAnimations.ContentFadeDuration),
        label = "heroAddContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        animationSpec = tween(ZenAnimations.ContentFadeDuration),
        label = "heroAddBorder"
    )
    val iconTint by animateColorAsState(
        targetValue = if (enabled) MaterialTheme.colorScheme.onPrimary
                      else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
        animationSpec = tween(ZenAnimations.ContentFadeDuration),
        label = "heroAddIcon"
    )

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                color = containerColor,
                shape = CircleShape
            )
            .border(
                1.dp,
                borderColor,
                CircleShape
            )
            .bounceClick(
                onClick = onPickFiles,
                enabled = enabled,
                scaleDown = if (morphProgress < 0.5f) 0.94f else 0.90f
            )
            .semantics {
                contentDescription = texts.addFiles
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        AppIcon(
            icon = Icons.Rounded.Add,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize.dp)
        )
    }
}

@Composable
private fun AlbumSourceDialog(
    texts: UiText,
    onDismiss: () -> Unit,
    onPickImages: () -> Unit,
    onPickVideos: () -> Unit
) {
    ZenPromptFrame(onDismissRequest = onDismiss) {
        SectionTitle(
            icon = Icons.Rounded.Image,
            title = texts.importAlbumTitle
        )
        Text(
            text = texts.importAlbumDialogNote,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onPickImages,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(texts.importAlbumImagesTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(
                onClick = onPickVideos,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(texts.importAlbumVideosTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSourceSheet(
    texts: UiText,
    onDismiss: () -> Unit,
    onPickAlbum: () -> Unit,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = texts.importSourceTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            ImportSourceRow(
                icon = Icons.Rounded.Image,
                title = texts.importAlbumTitle,
                note = texts.importAlbumNote,
                onClick = onPickAlbum
            )
            ImportSourceRow(
                icon = Icons.Rounded.FolderOpen,
                title = texts.importFolderTitle,
                note = texts.importFolderNote,
                onClick = onPickFolder
            )
            ImportSourceRow(
                icon = Icons.Rounded.Description,
                title = texts.importFilesTitle,
                note = texts.importFilesNote,
                onClick = onPickFiles
            )
        }
    }
}

@Composable
private fun ImportSourceRow(
    icon: ImageVector,
    title: String,
    note: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .bounceClick(onClick = onClick, scaleDown = 0.97f)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                icon = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BatchSettingsPanel(
    texts: UiText,
    files: List<QueuedFile>,
    supportedVideoMimeTypes: Set<String>,
    availableAiModels: Set<String>,
    isRifeModelDownloaded: Boolean = false,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onUpdateFiles: (List<QueuedFile>) -> Unit
) {
    val groups = files
        .groupBy { it.sourceCategory }
        .filterValues { it.size > 1 }
    if (groups.isEmpty()) return

    var selectedCategory by remember(files.map { it.id to it.sourceCategory }) {
        mutableStateOf(groups.maxByOrNull { it.value.size }?.key)
    }
    val activeCategory = selectedCategory?.takeIf { it in groups.keys }
        ?: groups.maxByOrNull { it.value.size }?.key
        ?: return
    val activeCount = groups[activeCategory]?.size ?: 0
    val targets = targetsForSourceCategory(activeCategory)
    if (targets.isEmpty()) return

    val activeFiles = groups[activeCategory].orEmpty()
    val commonTarget = commonSelectedTargetFor(activeFiles)
    var batchOptionsExpanded by rememberSaveable { mutableStateOf(false) }

    QuietPanel(
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = texts.batchSettings,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = texts.batchSettingsNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SmallTag(texts.batchCount(activeCount))
        }

        Spacer(modifier = Modifier.height(10.dp))

        CenteredFlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalSpacing = 8.dp,
            verticalSpacing = 8.dp
        ) {
            groups.entries.sortedBy { it.key.ordinal }.forEach { (category, categoryFiles) ->
                BatchScopeChip(
                    label = stringResource(R.string.display_batch_settings_panel_1_s_2_s, texts.categoryLabel(category), categoryFiles.size),
                    selected = category == activeCategory,
                    onClick = {
                        selectedCategory = category
                        onOpenMenuChange(null)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = texts.target,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CenteredFlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalSpacing = 8.dp,
            verticalSpacing = 8.dp
        ) {
            targets.forEach { target ->
                ExternalImportTargetChip(
                    texts = texts,
                    target = target,
                    selected = commonTarget == target,
                    onSelected = {
                        val updates = files
                            .filter { it.sourceCategory == activeCategory }
                            .map { file ->
                                fileWithTarget(file, target, supportedVideoMimeTypes)
                            }
                        onOpenMenuChange(null)
                        onUpdateFiles(updates)
                    }
                )
            }
        }

        if (commonTarget == null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = texts.batchMixedTarget,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.height(10.dp))
            AdvancedOptionsPanel(
                title = texts.batchOptions,
                note = texts.batchOptionsNote,
                expanded = batchOptionsExpanded,
                onExpandedChange = { batchOptionsExpanded = it }
            ) {
                BatchTargetOptions(
                    texts = texts,
                    files = activeFiles,
                    category = activeCategory,
                    target = commonTarget,
                    supportedVideoMimeTypes = supportedVideoMimeTypes,
                    availableAiModels = availableAiModels,
                    isRifeModelDownloaded = isRifeModelDownloaded,
                    openMenuId = openMenuId,
                    onOpenMenuChange = onOpenMenuChange,
                    onUpdateFiles = onUpdateFiles
                )
            }
        }
    }
}

@Composable
private fun BatchScopeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun BatchTargetOptions(
    texts: UiText,
    files: List<QueuedFile>,
    category: FileCategory,
    target: ExternalImportTarget,
    supportedVideoMimeTypes: Set<String>,
    availableAiModels: Set<String>,
    isRifeModelDownloaded: Boolean = false,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onUpdateFiles: (List<QueuedFile>) -> Unit
) {
    AnimatedContent(
        targetState = "${category.name}-${target.targetFormat.key}",
        transitionSpec = {
            fadeIn(animationSpec = tween(ZenAnimations.ContentFadeDuration)) togetherWith
                fadeOut(animationSpec = tween(ZenAnimations.ContentFadeOutDuration)) using
                SizeTransform(clip = false)
        },
        label = "BatchTargetOptions"
    ) {
        when (category) {
            FileCategory.Video -> BatchVideoTargetOptions(
                texts = texts,
                files = files,
                target = target.targetFormat,
                supportedVideoMimeTypes = supportedVideoMimeTypes,
                isRifeModelDownloaded = isRifeModelDownloaded,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onUpdateFiles = onUpdateFiles
            )
            FileCategory.Audio -> BatchAudioTargetOptions(
                texts = texts,
                files = files,
                target = target.targetFormat,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onUpdateFiles = onUpdateFiles
            )
            FileCategory.Image -> BatchImageTargetOptions(
                texts = texts,
                files = files,
                target = target.targetFormat,
                availableAiModels = availableAiModels,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onUpdateFiles = onUpdateFiles
            )
            FileCategory.Pdf -> BatchPdfTargetOptions(
                texts = texts,
                files = files,
                target = target.targetFormat,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onUpdateFiles = onUpdateFiles
            )
            FileCategory.Document -> Text(
                text = texts.optionValue(target.targetFormat.modeHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FileCategory.Font -> Text(
                text = texts.optionValue(target.targetFormat.modeHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FileCategory.Subtitle -> Text(
                text = texts.optionValue(target.targetFormat.modeHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun <T> commonBatchLabel(
    files: List<T>,
    label: (T) -> String
): String {
    return files.map(label).distinct().singleOrNull() ?: BATCH_MIXED_OPTION
}

private fun QueuedFile.withBatchVideoCompressionMode(
    value: String,
    supportedVideoMimeTypes: Set<String>
): QueuedFile {
    val mode = videoCompressionModeFor(value)
    val presetActive = mode != VideoCompressionMode.Standard
    return copy(
        videoOptions = videoOptions.copy(
            compressionMode = mode,
            videoBitrate = if (presetActive) null else videoOptions.videoBitrate,
            videoMimeType = if (
                presetActive &&
                VideoExportOptions.VIDEO_MIME_TYPE_H265 in supportedVideoMimeTypes
            ) {
                VideoExportOptions.VIDEO_MIME_TYPE_H265
            } else {
                videoOptions.videoMimeType
            },
            maxShortSidePixels = videoCompressionShortSideFor(mode)
                ?: videoOptions.maxShortSidePixels,
            maxFrameRate = videoCompressionFrameRateCapFor(mode)
                ?: videoOptions.maxFrameRate,
            advanced = if (presetActive) VideoAdvancedOptions() else videoOptions.advanced
        )
    )
}

private fun QueuedFile.withBatchVideoInterpolationMode(
    value: String
): QueuedFile {
    val mode = videoInterpolationModeFor(value)
    val isInterpolationActive = mode != VideoFrameInterpolationMode.Off
    val isOpticalFlow = mode == VideoFrameInterpolationMode.OpticalFlow2x
    val sourceShortSide = inputInfo?.let {
        val w = it.width ?: 0
        val h = it.height ?: 0
        if (w > 0 && h > 0) minOf(w, h) else null
    }
    val currentRes = videoOptions.maxShortSidePixels
    val clampedRes = if (isOpticalFlow) {
        if (currentRes == null) {
            if (sourceShortSide != null && sourceShortSide > 1080) 1080 else null
        } else if (currentRes > 1080) {
            1080
        } else {
            currentRes
        }
    } else {
        currentRes
    }
    return copy(
        videoOptions = videoOptions.copy(
            frameInterpolation = mode,
            maxShortSidePixels = clampedRes,
            compressionMode = if (isInterpolationActive) VideoCompressionMode.Standard else videoOptions.compressionMode,
            advanced = if (isInterpolationActive) VideoAdvancedOptions() else videoOptions.advanced
        )
    )
}

@Composable
private fun BatchVideoTargetOptions(
    texts: UiText,
    files: List<QueuedFile>,
    target: TargetFormat,
    supportedVideoMimeTypes: Set<String>,
    isRifeModelDownloaded: Boolean = false,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onUpdateFiles: (List<QueuedFile>) -> Unit
) {
    val isContactSheetTarget = target.extension.startsWith("contact_sheet", ignoreCase = true)
    if (isContactSheetTarget) {
        val commonGrid = commonBatchLabel(files) { it.contactSheetOptions.grid.labelKey }
        val allIncludeHeader = files.all { it.contactSheetOptions.includeHeader }
        val allIncludeTimestamp = files.all { it.contactSheetOptions.includeTimestamp }
        OptionGrid {
            OptionDropdown(
                menuId = "batch-contact-sheet-grid",
                label = texts.contactSheetGridLabel,
                selected = commonGrid,
                options = CONTACT_SHEET_GRID_OPTIONS,
                texts = texts,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onSelected = { key ->
                    val grid = contactSheetGridFor(key)
                    onUpdateFiles(files.map { it.copy(contactSheetOptions = it.contactSheetOptions.copy(grid = grid)) })
                }
            )
            AdvancedSwitchRow(
                label = texts.contactSheetIncludeHeader,
                checked = allIncludeHeader,
                onCheckedChange = { checked ->
                    onUpdateFiles(files.map { it.copy(contactSheetOptions = it.contactSheetOptions.copy(includeHeader = checked)) })
                }
            )
            AdvancedSwitchRow(
                label = texts.contactSheetIncludeTimestamp,
                checked = allIncludeTimestamp,
                onCheckedChange = { checked ->
                    onUpdateFiles(files.map { it.copy(contactSheetOptions = it.contactSheetOptions.copy(includeTimestamp = checked)) })
                }
            )
        }
        return
    }

    val isGifTarget = target.extension.equals("gif", ignoreCase = true)
    val commonInterpolation = commonBatchLabel(files) {
        videoInterpolationLabelFor(it.videoOptions.frameInterpolation)
    }
    val isInterpolationActive = !isGifTarget && videoInterpolationModeFor(commonInterpolation) != VideoFrameInterpolationMode.Off
    val disabledInterpolationOptions = if (isRifeModelDownloaded) emptySet() else setOf(VIDEO_INTERPOLATION_RIFE_2X)
    val commonCompression = commonBatchLabel(files) {
        videoCompressionLabelFor(it.videoOptions.compressionMode)
    }
    val standardCompressionActive =
        videoCompressionModeFor(commonCompression) == VideoCompressionMode.Standard
    val mixedCompression = commonCompression == BATCH_MIXED_OPTION
    val presetCompressionActive = !isGifTarget && !isInterpolationActive && !standardCompressionActive && !mixedCompression

    OptionGrid {
        if (!isGifTarget) {
            OptionDropdown(
                menuId = "batch-video-frame-interpolation",
                label = texts.videoFrameInterpolation,
                selected = commonInterpolation,
                options = VIDEO_INTERPOLATION_OPTIONS,
                texts = texts,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                disabledOptions = disabledInterpolationOptions
            ) { value ->
                onOpenMenuChange(null)
                onUpdateFiles(
                    files.map { it.withBatchVideoInterpolationMode(value) }
                )
            }

            val showRifeHint = !isRifeModelDownloaded &&
                (commonInterpolation == VIDEO_INTERPOLATION_RIFE_2X || openMenuId == "batch-video-frame-interpolation")
            if (showRifeHint) {
                Text(
                    text = texts.rifeInterpolationHint(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isInterpolationActive) {
            val summaryText = if (commonInterpolation == VIDEO_INTERPOLATION_OPTICAL_FLOW_2X) {
                texts.videoInterpolationOpticalFlowSummary
            } else {
                texts.videoInterpolationSummary
            }
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }

        if (!isGifTarget && !isInterpolationActive) {
            OptionDropdown(
                "batch-video-compression-mode",
                texts.videoCompressionMode,
                commonCompression,
                VIDEO_COMPRESSION_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange
            ) { value ->
                onOpenMenuChange(null)
                onUpdateFiles(
                    files.map { it.withBatchVideoCompressionMode(value, supportedVideoMimeTypes) }
                )
            }
        }

        if ((!isInterpolationActive || commonInterpolation == VIDEO_INTERPOLATION_OPTICAL_FLOW_2X) && (isGifTarget || standardCompressionActive || mixedCompression)) {
            val anySourceExceeds1080 = files.any { file ->
                val w = file.inputInfo?.width ?: 0
                val h = file.inputInfo?.height ?: 0
                w > 0 && h > 0 && minOf(w, h) > 1080
            }
            val isBatchOpticalFlow = commonInterpolation == VIDEO_INTERPOLATION_OPTICAL_FLOW_2X
            val disabledBatchResolutionOptions = buildSet {
                if (isBatchOpticalFlow) {
                    add(VIDEO_RESOLUTION_2160P)
                    add(VIDEO_RESOLUTION_1440P)
                    if (anySourceExceeds1080) {
                        add(VIDEO_RESOLUTION_ORIGINAL)
                    }
                }
            }
            OptionDropdown(
                "batch-video-size",
                texts.resolution,
                commonBatchLabel(files) { videoResolutionLabelFor(it.videoOptions) },
                if (isGifTarget) VIDEO_GIF_RESOLUTION_OPTIONS else VIDEO_RESOLUTION_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                disabledOptions = disabledBatchResolutionOptions
            ) { value ->
                onOpenMenuChange(null)
                onUpdateFiles(
                    files.map { file ->
                        file.copy(
                            videoOptions = file.videoOptions.copy(
                                maxShortSidePixels = videoResolutionToShortSide(value)
                            )
                        )
                    }
                )
            }
        }

        if (presetCompressionActive) {
            Text(
                text = texts.compressionPresetSummary(commonCompression),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
        if (!isGifTarget && !isInterpolationActive && (standardCompressionActive || mixedCompression)) {
            OptionDropdown(
                "batch-video-bitrate",
                texts.bitrate,
                commonBatchLabel(files) { videoBitrateLabelFor(it.videoOptions.videoBitrate) },
                VIDEO_BITRATE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange
            ) { value ->
                onOpenMenuChange(null)
                onUpdateFiles(
                    files.map { file ->
                        file.copy(
                            videoOptions = file.videoOptions.copy(
                                videoBitrate = videoBitrateToBits(value)
                            )
                        )
                    }
                )
            }
            OptionDropdown(
                "batch-video-codec",
                texts.codec,
                commonBatchLabel(files) { videoCodecLabelFor(it.videoOptions.videoMimeType) },
                videoCodecOptionsFor(supportedVideoMimeTypes),
                texts,
                openMenuId,
                onOpenMenuChange
            ) { value ->
                onOpenMenuChange(null)
                onUpdateFiles(
                    files.map { file ->
                        file.copy(
                            videoOptions = file.videoOptions.copy(
                                videoMimeType = videoCodecToMimeType(value)
                            )
                        )
                    }
                )
            }
            OptionDropdown(
                "batch-video-frame-rate",
                texts.frameRate,
                commonBatchLabel(files) { videoFrameRateLabelFor(it.videoOptions.maxFrameRate) },
                VIDEO_FRAME_RATE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange
            ) { value ->
                onOpenMenuChange(null)
                onUpdateFiles(
                    files.map { file ->
                        file.copy(
                            videoOptions = file.videoOptions.copy(
                                maxFrameRate = videoFrameRateToCap(value)
                            )
                        )
                    }
                )
            }
            OptionDropdown(
                "batch-video-audio-bitrate",
                texts.audioBitrateLabel(),
                commonBatchLabel(files) { audioBitrateLabelFor(it.audioOptions.audioBitrate) },
                AUDIO_BITRATE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange
            ) { value ->
                onOpenMenuChange(null)
                onUpdateFiles(
                    files.map { file ->
                        file.copy(
                            audioOptions = file.audioOptions.copy(
                                audioBitrate = audioBitrateToBits(value)
                            )
                        )
                    }
                )
            }
            OptionDropdown(
                "batch-video-audio-sample-rate",
                texts.sampleRate,
                commonBatchLabel(files) { audioSampleRateLabelFor(it.audioOptions.sampleRateHz) },
                AUDIO_SAMPLE_RATE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange
            ) { value ->
                onOpenMenuChange(null)
                onUpdateFiles(
                    files.map { file ->
                        file.copy(
                            audioOptions = file.audioOptions.copy(
                                sampleRateHz = audioSampleRateToHz(value)
                            )
                        )
                    }
                )
            }
            OptionDropdown(
                "batch-video-audio-channels",
                texts.channels,
                commonBatchLabel(files) { audioChannelsLabelFor(it.audioOptions.channelCount) },
                AUDIO_CHANNEL_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange
            ) { value ->
                onOpenMenuChange(null)
                onUpdateFiles(
                    files.map { file ->
                        file.copy(
                            audioOptions = file.audioOptions.copy(
                                channelCount = audioChannelsToCount(value)
                            )
                        )
                    }
                )
            }
        }
    }
}
@Composable
private fun BatchAudioTargetOptions(
    texts: UiText,
    files: List<QueuedFile>,
    target: TargetFormat,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onUpdateFiles: (List<QueuedFile>) -> Unit
) {
    OptionGrid {
        if (audioSupportsBitrateOption(target)) {
            OptionDropdown(
                "batch-audio-bitrate",
                texts.bitrate,
                commonBatchLabel(files) { audioBitrateLabelFor(it.audioOptions.audioBitrate) },
                AUDIO_BITRATE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange
            ) { value ->
                onOpenMenuChange(null)
                onUpdateFiles(
                    files.map { file ->
                        file.copy(
                            audioOptions = file.audioOptions.copy(
                                audioBitrate = audioBitrateToBits(value)
                            )
                        )
                    }
                )
            }
        } else {
            Text(
                text = texts.optionValue("Lossless output"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OptionDropdown(
            "batch-audio-sample-rate",
            texts.sampleRate,
            commonBatchLabel(files) { audioSampleRateLabelFor(it.audioOptions.sampleRateHz) },
            audioSampleRateOptionsFor(target),
            texts,
            openMenuId,
            onOpenMenuChange
        ) { value ->
            onOpenMenuChange(null)
            onUpdateFiles(
                files.map { file ->
                    file.copy(
                        audioOptions = file.audioOptions.copy(
                            sampleRateHz = audioSampleRateToHz(value)
                        )
                    )
                }
            )
        }
        if (isOpusTarget(target)) {
            Text(
                text = texts.opusSampleRateHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OptionDropdown(
            "batch-audio-channels",
            texts.channels,
            commonBatchLabel(files) { audioChannelsLabelFor(it.audioOptions.channelCount) },
            AUDIO_CHANNEL_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange
        ) { value ->
            onOpenMenuChange(null)
            onUpdateFiles(
                files.map { file ->
                    file.copy(
                        audioOptions = file.audioOptions.copy(
                            channelCount = audioChannelsToCount(value)
                        )
                    )
                }
            )
        }
    }
}
@Composable
private fun BatchImageTargetOptions(
    texts: UiText,
    files: List<QueuedFile>,
    target: TargetFormat,
    availableAiModels: Set<String>,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onUpdateFiles: (List<QueuedFile>) -> Unit
) {
    ImageOptions(
        texts = texts,
        menuPrefix = "batch-image-",
        targetFormat = target,
        quality = commonBatchLabel(files) { imageQualityLabelFor(it.imageOptions, target) },
        pdfPageMode = commonBatchLabel(files) { pdfPageModeLabelFor(it.pdfOptions.imagePageMode) },
        superResolution = commonBatchLabel(files) {
            superResolutionLabelFor(it.imageOptions.superResolution)
        },
        availableAiModels = availableAiModels,
        openMenuId = openMenuId,
        onOpenMenuChange = onOpenMenuChange,
        onQualityChange = { value ->
            onOpenMenuChange(null)
            onUpdateFiles(
                files.map { file ->
                    file.copy(imageOptions = imageOptionsForQuality(value, target))
                }
            )
        },
        onPdfPageModeChange = { value ->
            onOpenMenuChange(null)
            onUpdateFiles(
                files.map { file ->
                    file.copy(
                        pdfOptions = file.pdfOptions.copy(
                            imagePageMode = pdfPageModeToOption(value)
                        )
                    )
                }
            )
        },
        onSuperResolutionChange = { value ->
            onOpenMenuChange(null)
            val mode = superResolutionModeFor(value)
            onUpdateFiles(
                files.map { file ->
                    file.copy(
                        imageOptions = file.imageOptions.copy(superResolution = mode),
                        gifFrameMode = if (
                            mode != ImageSuperResolutionMode.Off &&
                            file.isGifQueuedImage()
                        ) {
                            GifFrameExportMode.FirstFrame
                        } else {
                            file.gifFrameMode
                        }
                    )
                }
            )
        }
    )
}

@Composable
private fun BatchPdfTargetOptions(
    texts: UiText,
    files: List<QueuedFile>,
    target: TargetFormat,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onUpdateFiles: (List<QueuedFile>) -> Unit
) {
    PdfOptions(
        texts = texts,
        menuPrefix = "batch-pdf-",
        targetFormat = target,
        renderQuality = commonBatchLabel(files) { pdfRenderQualityLabelFor(it.pdfOptions.renderQuality) },
        compressionPreset = commonBatchLabel(files) { pdfCompressionPresetLabelFor(it.pdfOptions.compressionPreset) },
        openMenuId = openMenuId,
        onOpenMenuChange = onOpenMenuChange,
        onRenderQualityChange = { value ->
            onOpenMenuChange(null)
            onUpdateFiles(
                files.map { file ->
                    file.copy(
                        pdfOptions = file.pdfOptions.copy(
                            renderQuality = pdfRenderQualityToOption(value)
                        )
                    )
                }
            )
        },
        onCompressionPresetChange = { value ->
            onOpenMenuChange(null)
            onUpdateFiles(
                files.map { file ->
                    file.copy(
                        pdfOptions = file.pdfOptions.copy(
                            compressionPreset = pdfCompressionPresetToOption(value)
                        )
                    )
                }
            )
        }
    )
}

@Composable
private fun PdfMergeGroupsPanel(
    texts: UiText,
    files: List<QueuedFile>,
    groups: List<PdfMergeGroup>,
    taskProgress: Map<String, TaskProgress>,
    canEdit: Boolean,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onCreateGroup: (PdfMergeType) -> Unit,
    onUpdateGroup: (PdfMergeGroup) -> Unit,
    onRemoveGroup: (String) -> Unit,
    onAddFileToGroup: (String, String) -> Unit,
    onRemoveFileFromGroup: (String, String) -> Unit
) {
    val imageCandidates = mergeablePdfFilesFor(files, groups, PdfMergeType.Images)
    val pdfCandidates = mergeablePdfFilesFor(files, groups, PdfMergeType.Pdfs)
    val visibleGroups = groups.filter { group ->
        group.memberFileIds.count { id -> files.any { it.id == id } } >= 2
    }
    if (
        visibleGroups.isEmpty() &&
        (!canEdit || (imageCandidates.size < 2 && pdfCandidates.size < 2))
    ) return

    QuietPanel(
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = texts.pdfMergeTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = texts.pdfMergeNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (visibleGroups.isNotEmpty()) {
                SmallTag(texts.fileCountLabel(visibleGroups.size))
            }
        }

        if (canEdit && (imageCandidates.size >= 2 || pdfCandidates.size >= 2)) {
            Spacer(modifier = Modifier.height(10.dp))
            CenteredFlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalSpacing = 8.dp,
                verticalSpacing = 8.dp
            ) {
                if (imageCandidates.size >= 2) {
                    PdfMergeActionChip(
                        label = stringResource(R.string.display_pdf_merge_groups_panel_1_s_2_s, texts.createImagePdfMerge, texts.fileCountLabel(imageCandidates.size)),
                        onClick = {
                            onOpenMenuChange(null)
                            onCreateGroup(PdfMergeType.Images)
                        }
                    )
                }
                if (pdfCandidates.size >= 2) {
                    PdfMergeActionChip(
                        label = stringResource(R.string.display_pdf_merge_groups_panel_1_s_2_s, texts.createPdfMerge, texts.fileCountLabel(pdfCandidates.size)),
                        onClick = {
                            onOpenMenuChange(null)
                            onCreateGroup(PdfMergeType.Pdfs)
                        }
                    )
                }
            }
        }

        visibleGroups.forEach { group ->
            Spacer(modifier = Modifier.height(10.dp))
            PdfMergeGroupCard(
                texts = texts,
                files = files,
                groups = groups,
                group = group,
                progress = taskProgress[group.id],
                canEdit = canEdit,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onUpdateGroup = onUpdateGroup,
                onRemoveGroup = onRemoveGroup,
                onAddFileToGroup = onAddFileToGroup,
                onRemoveFileFromGroup = onRemoveFileFromGroup
            )
        }
    }
}

@Composable
private fun PdfMergeActionChip(
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            contentColor = MaterialTheme.colorScheme.primary
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PdfMergeGroupCard(
    texts: UiText,
    files: List<QueuedFile>,
    groups: List<PdfMergeGroup>,
    group: PdfMergeGroup,
    progress: TaskProgress?,
    canEdit: Boolean,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onUpdateGroup: (PdfMergeGroup) -> Unit,
    onRemoveGroup: (String) -> Unit,
    onAddFileToGroup: (String, String) -> Unit,
    onRemoveFileFromGroup: (String, String) -> Unit
) {
    val context = LocalContext.current
    val filesById = files.associateBy { it.id }
    val members = group.memberFileIds.mapNotNull { filesById[it] }
    val addableFiles = mergeablePdfFilesFor(files, groups, group.type)
    val completedProgress = progress?.takeIf {
        it.status == TaskProgressStatus.Completed && it.outputUriList().isNotEmpty()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.035f), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = texts.pdfMergeGroupTitle(group.type),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallTag(texts.fileCountLabel(members.size))
                    SmallTag("PDF")
                    SmallTag(texts.progressLabel(progress))
                }
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
                    contentDescription = texts.removeMergeGroup,
                    enabled = canEdit,
                    onClick = { onRemoveGroup(group.id) }
                )
            }
        }

        if (group.type == PdfMergeType.Images && canEdit) {
            OptionDropdown(
                "merge-${group.id}-page-mode",
                texts.pageSize,
                pdfPageModeLabelFor(group.pdfOptions.imagePageMode),
                PDF_PAGE_MODE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange
            ) { value ->
                onUpdateGroup(
                    group.copy(
                        pdfOptions = group.pdfOptions.copy(
                            imagePageMode = pdfPageModeToOption(value)
                        )
                    )
                )
            }
        }

        members.forEach { member ->
            PdfMergeMemberRow(
                texts = texts,
                file = member,
                canEdit = canEdit,
                onRemove = {
                    onOpenMenuChange(null)
                    onRemoveFileFromGroup(group.id, member.id)
                }
            )
        }

        if (addableFiles.isNotEmpty() && canEdit) {
            Text(
                text = texts.addToMerge,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CenteredFlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalSpacing = 8.dp,
                verticalSpacing = 8.dp
            ) {
                if (addableFiles.size > 6) {
                    PdfMergeActionChip(
                        label = stringResource(R.string.display_pdf_merge_groups_panel_1_s_2_s, texts.addToMerge, texts.fileCountLabel(addableFiles.size)),
                        onClick = {
                            onOpenMenuChange(null)
                            addableFiles.forEach { file ->
                                onAddFileToGroup(group.id, file.id)
                            }
                        }
                    )
                }
                addableFiles.take(6).forEach { file ->
                    BatchScopeChip(
                        label = compactMergeFileName(file.displayName),
                        selected = false,
                        onClick = {
                            onOpenMenuChange(null)
                            onAddFileToGroup(group.id, file.id)
                        }
                    )
                }
            }
        }
        if (progress?.status == TaskProgressStatus.Running) {
            LinearProgressIndicator(
                progress = progress.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
            )
        }
        if (progress?.status == TaskProgressStatus.Completed && progress.outputInfo != null) {
            ResultInfoLine(
                text = formatMergedResultInfoLine(progress.outputInfo, texts)
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
private fun PdfMergeMemberRow(
    texts: UiText,
    file: QueuedFile,
    canEdit: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = file.displayName,
                style = MaterialTheme.typography.bodySmall,
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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        CompactTaskActionButton(
            icon = Icons.Rounded.Close,
            contentDescription = texts.remove,
            enabled = canEdit,
            onClick = onRemove
        )
    }
}

private fun mergeableVideoFilesFor(
    files: List<QueuedFile>,
    groups: List<VideoMergeGroup>
): List<QueuedFile> {
    val groupedIds = groups.flatMap { it.memberFileIds }.toSet()
    return files.filter { file ->
        file.category == FileCategory.Video && file.id !in groupedIds
    }
}

@Composable
private fun VideoMergeGroupsPanel(
    texts: UiText,
    files: List<QueuedFile>,
    groups: List<VideoMergeGroup>,
    taskProgress: Map<String, TaskProgress>,
    canEdit: Boolean,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onCreateGroup: () -> Unit,
    onUpdateGroup: (VideoMergeGroup) -> Unit,
    onRemoveGroup: (String) -> Unit,
    onAddFileToGroup: (String, String) -> Unit,
    onRemoveFileFromGroup: (String, String) -> Unit
) {
    val candidates = mergeableVideoFilesFor(files, groups)
    val visibleGroups = groups.filter { group ->
        group.memberFileIds.count { id -> files.any { it.id == id } } >= 2
    }
    if (
        visibleGroups.isEmpty() &&
        (!canEdit || candidates.size < 2)
    ) return

    QuietPanel(
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = texts.videoMergeTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = texts.videoMergeNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (visibleGroups.isNotEmpty()) {
                SmallTag(texts.fileCountLabel(visibleGroups.size))
            }
        }

        if (canEdit && candidates.size >= 2) {
            Spacer(modifier = Modifier.height(10.dp))
            CenteredFlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalSpacing = 8.dp,
                verticalSpacing = 8.dp
            ) {
                VideoMergeActionChip(
                    label = stringResource(R.string.display_pdf_merge_groups_panel_1_s_2_s, texts.createVideoMerge, texts.fileCountLabel(candidates.size)),
                    onClick = {
                        onOpenMenuChange(null)
                        onCreateGroup()
                    }
                )
            }
        }

        visibleGroups.forEach { group ->
            Spacer(modifier = Modifier.height(10.dp))
            VideoMergeGroupCard(
                texts = texts,
                files = files,
                groups = groups,
                group = group,
                progress = taskProgress[group.id],
                canEdit = canEdit,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onUpdateGroup = onUpdateGroup,
                onRemoveGroup = onRemoveGroup,
                onAddFileToGroup = onAddFileToGroup,
                onRemoveFileFromGroup = onRemoveFileFromGroup
            )
        }
    }
}

@Composable
private fun VideoMergeActionChip(
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            contentColor = MaterialTheme.colorScheme.primary
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Videocam,
            contentDescription = null,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun VideoMergeGroupCard(
    texts: UiText,
    files: List<QueuedFile>,
    groups: List<VideoMergeGroup>,
    group: VideoMergeGroup,
    progress: TaskProgress?,
    canEdit: Boolean,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onUpdateGroup: (VideoMergeGroup) -> Unit,
    onRemoveGroup: (String) -> Unit,
    onAddFileToGroup: (String, String) -> Unit,
    onRemoveFileFromGroup: (String, String) -> Unit
) {
    val context = LocalContext.current
    val filesById = files.associateBy { it.id }
    val members = group.memberFileIds.mapNotNull { filesById[it] }
    val addableFiles = mergeableVideoFilesFor(files, groups)
    val completedProgress = progress?.takeIf {
        it.status == TaskProgressStatus.Completed && it.outputUriList().isNotEmpty()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.035f), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = texts.videoMergeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallTag(texts.fileCountLabel(members.size))
                    SmallTag(group.targetFormat)
                    SmallTag(texts.progressLabel(progress))
                }
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
                    contentDescription = texts.removeMergeGroup,
                    enabled = canEdit,
                    onClick = { onRemoveGroup(group.id) }
                )
            }
        }

        if (canEdit) {
            CenteredFlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalSpacing = 8.dp,
                verticalSpacing = 8.dp
            ) {
                OptionDropdown(
                    "video-merge-${group.id}-target",
                    texts.target,
                    group.targetFormat,
                    listOf("MP4", "MKV", "MOV"),
                    texts,
                    openMenuId,
                    onOpenMenuChange
                ) { value ->
                    onUpdateGroup(group.copy(targetFormat = value))
                }

                OptionDropdown(
                    "video-merge-${group.id}-preset",
                    texts.videoCompressionMode,
                    videoCompressionLabelFor(group.videoOptions.compressionMode),
                    VIDEO_COMPRESSION_OPTIONS,
                    texts,
                    openMenuId,
                    onOpenMenuChange
                ) { value ->
                    val mode = videoCompressionModeFor(value)
                    val presetActive = mode != VideoCompressionMode.Standard
                    onUpdateGroup(
                        group.copy(
                            videoOptions = group.videoOptions.copy(
                                compressionMode = mode,
                                videoBitrate = if (presetActive) null else group.videoOptions.videoBitrate,
                                maxShortSidePixels = videoCompressionShortSideFor(mode)
                                    ?: group.videoOptions.maxShortSidePixels,
                                maxFrameRate = videoCompressionFrameRateCapFor(mode)
                                    ?: group.videoOptions.maxFrameRate
                            )
                        )
                    )
                }

                if (group.videoOptions.compressionMode == VideoCompressionMode.Standard) {
                    OptionDropdown(
                        "video-merge-${group.id}-resolution",
                        texts.resolution,
                        videoResolutionLabelFor(group.videoOptions),
                        VIDEO_RESOLUTION_OPTIONS,
                        texts,
                        openMenuId,
                        onOpenMenuChange
                    ) { value ->
                        onUpdateGroup(
                            group.copy(
                                videoOptions = group.videoOptions.copy(
                                    maxShortSidePixels = videoResolutionToShortSide(value)
                                )
                            )
                        )
                    }

                    OptionDropdown(
                        "video-merge-${group.id}-codec",
                        texts.codec,
                        videoCodecLabelFor(group.videoOptions.videoMimeType),
                        listOf(VIDEO_CODEC_H264, VIDEO_CODEC_H265),
                        texts,
                        openMenuId,
                        onOpenMenuChange
                    ) { value ->
                        onUpdateGroup(
                            group.copy(
                                videoOptions = group.videoOptions.copy(
                                    videoMimeType = videoCodecToMimeType(value)
                                )
                            )
                        )
                    }
                }
            }
        }

        members.forEachIndexed { index, member ->
            VideoMergeMemberRow(
                texts = texts,
                index = index + 1,
                file = member,
                canEdit = canEdit,
                onRemove = {
                    onOpenMenuChange(null)
                    onRemoveFileFromGroup(group.id, member.id)
                }
            )
        }

        if (addableFiles.isNotEmpty() && canEdit) {
            Text(
                text = texts.addToMerge,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CenteredFlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalSpacing = 8.dp,
                verticalSpacing = 8.dp
            ) {
                if (addableFiles.size > 6) {
                    VideoMergeActionChip(
                        label = stringResource(R.string.display_pdf_merge_groups_panel_1_s_2_s, texts.addToMerge, texts.fileCountLabel(addableFiles.size)),
                        onClick = {
                            onOpenMenuChange(null)
                            addableFiles.forEach { file ->
                                onAddFileToGroup(group.id, file.id)
                            }
                        }
                    )
                }
                addableFiles.take(6).forEach { file ->
                    BatchScopeChip(
                        label = compactMergeFileName(file.displayName),
                        selected = false,
                        onClick = {
                            onOpenMenuChange(null)
                            onAddFileToGroup(group.id, file.id)
                        }
                    )
                }
            }
        }
        if (progress?.status == TaskProgressStatus.Running) {
            LinearProgressIndicator(
                progress = progress.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
            )
        }
        if (progress?.status == TaskProgressStatus.Completed && progress.outputInfo != null) {
            ResultInfoLine(
                text = formatMergedResultInfoLine(progress.outputInfo, texts)
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
private fun VideoMergeMemberRow(
    texts: UiText,
    index: Int,
    file: QueuedFile,
    canEdit: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodySmall,
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
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        CompactTaskActionButton(
            icon = Icons.Rounded.Close,
            contentDescription = texts.remove,
            enabled = canEdit,
            onClick = onRemove
        )
    }
}

@Composable
private fun QueueHeader(
    texts: UiText,
    fileCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = texts.queue,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = texts.selectedCount(fileCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VideoOptions(
    texts: UiText,
    menuPrefix: String = "",
    trimRange: MediaTrimRange,
    sourceDurationMs: Long?,
    resolution: String,
    compressionMode: String,
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
    trimInputMode: TrimInputMode,
    frameInterpolation: String = VIDEO_INTERPOLATION_OFF,
    isRifeModelDownloaded: Boolean = false,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onTrimInputModeChange: (TrimInputMode) -> Unit,
    onTrimStartSecondsChange: (Double?) -> Unit,
    onTrimEndSecondsChange: (Double?) -> Unit,
    onTrimRangeChange: (MediaTrimRange) -> Unit,
    onFrameInterpolationChange: (String) -> Unit = {},
    onResolutionChange: (String) -> Unit,
    onCompressionModeChange: (String) -> Unit,
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
    onAudioNoiseReductionChange: (String) -> Unit,
    contactSheetOptions: VideoContactSheetOptions = VideoContactSheetOptions(),
    onContactSheetOptionsChange: (VideoContactSheetOptions) -> Unit = {},
    sourceShortSide: Int? = null,
) {
    val isContactSheetTarget = targetFormat.id.isContactSheet
    if (isContactSheetTarget) {
        OptionGrid {
            MediaTrimOptions(
                texts = texts,
                trimRange = trimRange,
                sourceDurationMs = sourceDurationMs,
                inputMode = trimInputMode,
                onInputModeChange = onTrimInputModeChange,
                onStartSecondsChange = onTrimStartSecondsChange,
                onEndSecondsChange = onTrimEndSecondsChange,
                onTrimRangeChange = onTrimRangeChange
            )
            OptionDropdown(
                menuId = "${menuPrefix}contact-sheet-grid",
                label = texts.contactSheetGridLabel,
                selected = contactSheetOptions.grid.labelKey,
                options = CONTACT_SHEET_GRID_OPTIONS,
                texts = texts,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onSelected = { key ->
                    onContactSheetOptionsChange(
                        contactSheetOptions.copy(grid = contactSheetGridFor(key))
                    )
                }
            )
            AdvancedSwitchRow(
                label = texts.contactSheetIncludeHeader,
                checked = contactSheetOptions.includeHeader,
                onCheckedChange = { checked ->
                    onContactSheetOptionsChange(contactSheetOptions.copy(includeHeader = checked))
                }
            )
            AdvancedSwitchRow(
                label = texts.contactSheetIncludeTimestamp,
                checked = contactSheetOptions.includeTimestamp,
                onCheckedChange = { checked ->
                    onContactSheetOptionsChange(contactSheetOptions.copy(includeTimestamp = checked))
                }
            )
        }
        return
    }

    val isGifTarget = targetFormat.extension.equals("gif", ignoreCase = true)
    val isInterpolationActive = !isGifTarget && videoInterpolationModeFor(frameInterpolation) != VideoFrameInterpolationMode.Off
    val disabledInterpolationOptions = if (isRifeModelDownloaded) emptySet() else setOf(VIDEO_INTERPOLATION_RIFE_2X)
    val isOpticalFlow = !isGifTarget && frameInterpolation == VIDEO_INTERPOLATION_OPTICAL_FLOW_2X
    val disabledResolutionOptions = buildSet {
        if (isOpticalFlow) {
            add(VIDEO_RESOLUTION_2160P)
            add(VIDEO_RESOLUTION_1440P)
            if (sourceShortSide != null && sourceShortSide > 1080) {
                add(VIDEO_RESOLUTION_ORIGINAL)
            }
        }
    }
    val isStandardCompression = videoCompressionModeFor(compressionMode) == VideoCompressionMode.Standard
    val presetCompressionActive = !isGifTarget && !isInterpolationActive && !isStandardCompression
    OptionGrid {
        MediaTrimOptions(
            texts = texts,
            trimRange = trimRange,
            sourceDurationMs = sourceDurationMs,
            inputMode = trimInputMode,
            onInputModeChange = onTrimInputModeChange,
            onStartSecondsChange = onTrimStartSecondsChange,
            onEndSecondsChange = onTrimEndSecondsChange,
            onTrimRangeChange = onTrimRangeChange
        )
        if (!isGifTarget) {
            OptionDropdown(
                menuId = "${menuPrefix}video-frame-interpolation",
                label = texts.videoFrameInterpolation,
                selected = frameInterpolation,
                options = VIDEO_INTERPOLATION_OPTIONS,
                texts = texts,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                disabledOptions = disabledInterpolationOptions,
                onSelected = onFrameInterpolationChange
            )
            val showRifeHint = !isRifeModelDownloaded &&
                (frameInterpolation == VIDEO_INTERPOLATION_RIFE_2X || openMenuId == "${menuPrefix}video-frame-interpolation")
            if (showRifeHint) {
                Text(
                    text = texts.rifeInterpolationHint(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isInterpolationActive) {
            val summaryText = if (frameInterpolation == VIDEO_INTERPOLATION_OPTICAL_FLOW_2X) {
                texts.videoInterpolationOpticalFlowSummary
            } else {
                texts.videoInterpolationSummary
            }
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
        if (!isGifTarget && !isInterpolationActive) {
            OptionDropdown(
                "${menuPrefix}video-compression-mode",
                texts.videoCompressionMode,
                compressionMode,
                VIDEO_COMPRESSION_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                onCompressionModeChange
            )
        }
        if ((!isInterpolationActive || frameInterpolation == VIDEO_INTERPOLATION_OPTICAL_FLOW_2X) && (isGifTarget || isStandardCompression)) {
            OptionDropdown(
                "${menuPrefix}video-size",
                texts.resolution,
                resolution,
                if (isGifTarget) VIDEO_GIF_RESOLUTION_OPTIONS else VIDEO_RESOLUTION_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                disabledOptions = disabledResolutionOptions,
                onSelected = onResolutionChange
            )
        }
        if (presetCompressionActive) {
            Text(
                text = texts.compressionPresetSummary(compressionMode),
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
        if (!isGifTarget && !isInterpolationActive && isStandardCompression) {
            OptionDropdown(
                "${menuPrefix}video-bitrate",
                texts.bitrate,
                bitrate,
                VIDEO_BITRATE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                onBitrateChange
            )
            OptionDropdown(
                "${menuPrefix}video-codec",
                texts.codec,
                codec,
                codecOptions,
                texts,
                openMenuId,
                onOpenMenuChange,
                onCodecChange
            )
            OptionDropdown(
                "${menuPrefix}video-frame-rate",
                texts.frameRate,
                frameRate,
                VIDEO_FRAME_RATE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                onFrameRateChange
            )
            OptionDropdown(
                "${menuPrefix}video-audio-bitrate",
                texts.audioBitrateLabel(),
                audioBitrate,
                AUDIO_BITRATE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                onAudioBitrateChange
            )
            OptionDropdown(
                "${menuPrefix}video-audio-sample-rate",
                texts.sampleRate,
                audioSampleRate,
                AUDIO_SAMPLE_RATE_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                onAudioSampleRateChange
            )
            OptionDropdown(
                "${menuPrefix}video-audio-channels",
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
                menuPrefix = menuPrefix,
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
                menuPrefix = menuPrefix,
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
    menuPrefix: String = "",
    trimRange: MediaTrimRange,
    sourceDurationMs: Long?,
    bitrate: String,
    sampleRate: String,
    channels: String,
    advanced: AudioAdvancedUiState,
    targetFormat: TargetFormat,
    trimInputMode: TrimInputMode,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onTrimInputModeChange: (TrimInputMode) -> Unit,
    onTrimStartSecondsChange: (Double?) -> Unit,
    onTrimEndSecondsChange: (Double?) -> Unit,
    onTrimRangeChange: (MediaTrimRange) -> Unit,
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
        MediaTrimOptions(
            texts = texts,
            trimRange = trimRange,
            sourceDurationMs = sourceDurationMs,
            inputMode = trimInputMode,
            onInputModeChange = onTrimInputModeChange,
            onStartSecondsChange = onTrimStartSecondsChange,
            onEndSecondsChange = onTrimEndSecondsChange,
            onTrimRangeChange = onTrimRangeChange
        )
        if (audioSupportsBitrateOption(targetFormat)) {
            OptionDropdown(
                "${menuPrefix}audio-bitrate",
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
            "${menuPrefix}audio-sample-rate",
            texts.sampleRate,
            sampleRate,
            audioSampleRateOptionsFor(targetFormat),
            texts,
            openMenuId,
            onOpenMenuChange,
            onSampleRateChange
        )
        if (isOpusTarget(targetFormat)) {
            Text(
                text = texts.opusSampleRateHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OptionDropdown(
            "${menuPrefix}audio-channels",
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
            menuPrefix = menuPrefix,
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

private enum class TrimInputMode {
    Quick,
    Precise
}

@Composable
private fun MediaTrimOptions(
    texts: UiText,
    trimRange: MediaTrimRange,
    sourceDurationMs: Long?,
    inputMode: TrimInputMode,
    onInputModeChange: (TrimInputMode) -> Unit,
    onStartSecondsChange: (Double?) -> Unit,
    onEndSecondsChange: (Double?) -> Unit,
    onTrimRangeChange: (MediaTrimRange) -> Unit
) {
    val errorText = mediaTrimErrorText(trimRange, sourceDurationMs, texts)
    val durationSeconds = sourceDurationMs
        ?.takeIf { it > 0L }
        ?.let { it.toDouble() / 1_000.0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (errorText == null) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                },
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = texts.trimRange,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val durationText = sourceDurationMs?.let { formatDurationMs(it, texts) }
            val hint = if (trimRange.isSplitActive) {
                texts.trimDurationHint(durationText) + " · " + texts.trimSplitSegmentsHint(trimRange.segmentCount)
            } else {
                texts.trimDurationHint(durationText)
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TrimModeToggle(
                    texts = texts,
                    selected = inputMode,
                    onSelected = onInputModeChange
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(100.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                IconButton(
                    onClick = {
                        if (trimRange.splitPoints.isNotEmpty()) {
                            onTrimRangeChange(trimRange.copy(splitPoints = trimRange.splitPoints.dropLast(1)))
                        }
                    },
                    enabled = trimRange.splitPoints.isNotEmpty(),
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = texts.text(R.string.a11y_remove_split_point),
                        modifier = Modifier.size(16.dp),
                        tint = if (trimRange.splitPoints.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
                Text(
                    text = stringResource(R.string.display_media_trim_options_1_s, trimRange.splitPoints.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                IconButton(
                    onClick = {
                        val totalSec = durationSeconds ?: 100.0
                        val startSec = trimRange.startSeconds ?: 0.0
                        val endSec = trimRange.endSeconds ?: totalSec
                        val points = (listOf(startSec) + trimRange.splitPoints + listOf(endSec)).sorted()
                        var maxGap = 0.0
                        var insertPos = (startSec + endSec) / 2.0
                        for (i in 0 until points.size - 1) {
                            val gap = points[i + 1] - points[i]
                            if (gap > maxGap) {
                                maxGap = gap
                                insertPos = (points[i] + points[i + 1]) / 2.0
                            }
                        }
                        val rounded = (Math.round(insertPos * 10.0) / 10.0).coerceIn(startSec + 0.1, endSec - 0.1)
                        onTrimRangeChange(trimRange.copy(splitPoints = (trimRange.splitPoints + rounded).sorted()))
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = texts.text(R.string.a11y_add_split_point),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (inputMode == TrimInputMode.Quick) {
            TrimAndSplitSlider(
                trimRange = trimRange,
                sourceDurationMs = sourceDurationMs,
                texts = texts,
                onRangeChange = onTrimRangeChange
            )
        } else {
            PreciseTrimAndSplitFields(
                trimRange = trimRange,
                sourceDurationMs = sourceDurationMs,
                texts = texts,
                errorText = errorText,
                onStartSecondsChange = onStartSecondsChange,
                onEndSecondsChange = onEndSecondsChange,
                onTrimRangeChange = onTrimRangeChange
            )
        }

        errorText?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun TrimModeToggle(
    texts: UiText,
    selected: TrimInputMode,
    onSelected: (TrimInputMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(100.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        TrimModeChip(
            text = texts.trimQuick,
            selected = selected == TrimInputMode.Quick,
            onClick = { onSelected(TrimInputMode.Quick) },
            modifier = Modifier.weight(1f)
        )
        TrimModeChip(
            text = texts.trimPrecise,
            selected = selected == TrimInputMode.Precise,
            onClick = { onSelected(TrimInputMode.Precise) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TrimModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrimAndSplitSlider(
    trimRange: MediaTrimRange,
    sourceDurationMs: Long?,
    texts: UiText,
    onRangeChange: (MediaTrimRange) -> Unit
) {
    val durationSeconds = sourceDurationMs
        ?.takeIf { it > 0L }
        ?.let { it.toDouble() / 1_000.0 }
    val start = (trimRange.startSeconds ?: 0.0).toFloat()
    val fallbackEnd = maxOf(if (durationSeconds == null) 1f else 0f, start)
    val end = (trimRange.endSeconds ?: durationSeconds?.toFloat() ?: fallbackEnd).toFloat()
    val sliderMax = maxOf(
        durationSeconds?.toFloat() ?: 0f,
        start,
        end,
        if (durationSeconds == null) 1f else 0f
    ).coerceAtLeast(0.1f)
    val valueRange = 0f..sliderMax
    val currentStart = if (start <= end) start.coerceIn(valueRange) else end.coerceIn(valueRange)
    val currentEnd = if (start <= end) end.coerceIn(valueRange) else start.coerceIn(valueRange)
    val currentRange = currentStart..currentEnd

    var sliderWidthPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    val currentTrimRange by rememberUpdatedState(trimRange)
    val currentOnRangeChange by rememberUpdatedState(onRangeChange)
    val currentSliderMax by rememberUpdatedState(sliderMax)
    val currentDurationSeconds by rememberUpdatedState(durationSeconds)
    val currentSliderWidthPx by rememberUpdatedState(sliderWidthPx)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val sliderColors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp, bottom = 4.dp)
                .onSizeChanged { sliderWidthPx = it.width.toFloat() },
            contentAlignment = Alignment.CenterStart
        ) {
            RangeSlider(
                value = currentRange,
                onValueChange = { range ->
                    onRangeChange(
                        trimRange.copy(
                            startSeconds = range.start.toDouble(),
                            endSeconds = range.endInclusive.toDouble()
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                valueRange = valueRange,
                colors = sliderColors,
                startThumb = { SmallTrimThumb() },
                endThumb = { SmallTrimThumb() },
                track = { state ->
                    TrimAndSplitTrack(
                        state = state,
                        colors = sliderColors,
                        splitFractions = trimRange.splitPoints.map { (it.toFloat() / sliderMax).coerceIn(0f, 1f) }
                    )
                }
            )

            if (sliderWidthPx > 0f && trimRange.splitPoints.isNotEmpty()) {
                trimRange.splitPoints.forEachIndexed { index, pointVal ->
                    val frac = (pointVal.toFloat() / sliderMax).coerceIn(0f, 1f)
                    val xPosPx = frac * sliderWidthPx
                    val xDp = with(density) { xPosPx.toDp() } - 18.dp

                    Box(
                        modifier = Modifier
                            .offset(x = xDp, y = (-22).dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .pointerInput(index, trimRange.splitPoints.size) {
                                var dragAccumulatedSec = 0.0
                                detectDragGestures(
                                    onDragStart = {
                                        val range = currentTrimRange
                                        dragAccumulatedSec = range.splitPoints.getOrNull(index) ?: 0.0
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val sWidth = currentSliderWidthPx
                                        val sMax = currentSliderMax
                                        val range = currentTrimRange
                                        if (sWidth > 0f && sMax > 0f) {
                                            val deltaSec = (dragAmount.x / sWidth) * sMax
                                            val prevLimit = if (index == 0) (range.startSeconds ?: 0.0) + 0.1 else range.splitPoints[index - 1] + 0.1
                                            val nextLimit = if (index == range.splitPoints.size - 1) (range.endSeconds ?: currentDurationSeconds ?: sMax.toDouble()) - 0.1 else range.splitPoints[index + 1] - 0.1
                                            dragAccumulatedSec = (dragAccumulatedSec + deltaSec).coerceIn(prevLimit, maxOf(prevLimit, nextLimit))
                                            val updated = range.splitPoints.toMutableList()
                                            if (index < updated.size) {
                                                updated[index] = Math.round(dragAccumulatedSec * 100.0) / 100.0
                                                currentOnRangeChange(range.copy(splitPoints = updated))
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✂️",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.display_batch_settings_panel_1_s_2_s, texts.trimStartSeconds, formatTrimSeconds(currentStart.toDouble(), texts)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            trimRange.splitPoints.forEachIndexed { i, pt ->
                Text(
                    text = stringResource(R.string.display_trim_and_split_slider_1_s_2_s, i + 1, formatTrimSeconds(pt, texts)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = stringResource(R.string.display_batch_settings_panel_1_s_2_s, texts.trimEndSeconds, formatTrimSeconds(currentEnd.toDouble(), texts)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SmallTrimThumb() {
    Box(
        modifier = Modifier
            .size(16.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrimAndSplitTrack(
    state: RangeSliderState,
    colors: SliderColors,
    splitFractions: List<Float>
) {
    val trackHeight = 4.dp
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        val strokeWidth = trackHeight.toPx()
        val y = center.y
        val trackStart = Offset(0f, y)
        val trackEnd = Offset(size.width, y)
        drawLine(
            color = colors.inactiveTrackColor,
            start = trackStart,
            end = trackEnd,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        val range = state.valueRange
        val span = range.endInclusive - range.start
        if (span > 0f) {
            val activeStart = (state.activeRangeStart - range.start) / span * size.width
            val activeEnd = (state.activeRangeEnd - range.start) / span * size.width
            drawLine(
                color = colors.activeTrackColor,
                start = Offset(activeStart, y),
                end = Offset(activeEnd, y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
        splitFractions.forEach { frac ->
            val x = frac * size.width
            drawLine(
                color = colors.activeTrackColor,
                start = Offset(x, 2.dp.toPx()),
                end = Offset(x, size.height - 2.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                pathEffect = dashEffect
            )
        }
    }
}

@Composable
private fun PreciseTrimAndSplitFields(
    trimRange: MediaTrimRange,
    sourceDurationMs: Long?,
    texts: UiText,
    errorText: String?,
    onStartSecondsChange: (Double?) -> Unit,
    onEndSecondsChange: (Double?) -> Unit,
    onTrimRangeChange: (MediaTrimRange) -> Unit
) {
    val durationSeconds = sourceDurationMs?.takeIf { it > 0L }?.let { it.toDouble() / 1_000.0 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrimSecondsField(
                value = trimRange.startSeconds,
                label = texts.trimStartSeconds,
                isError = errorText != null,
                onValueChange = onStartSecondsChange,
                modifier = Modifier.weight(1f)
            )
            TrimSecondsField(
                value = trimRange.endSeconds,
                label = texts.trimEndSeconds,
                isError = errorText != null,
                onValueChange = onEndSecondsChange,
                modifier = Modifier.weight(1f)
            )
        }

        trimRange.splitPoints.forEachIndexed { index, pointSec ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrimSecondsField(
                    value = pointSec,
                    label = stringResource(R.string.display_precise_trim_and_split_fields_1_s_2_s, texts.trimSplitPoints, index + 1),
                    isError = errorText != null,
                    onValueChange = { newVal ->
                        val updated = trimRange.splitPoints.toMutableList()
                        if (newVal != null) {
                            updated[index] = newVal
                        }
                        onTrimRangeChange(trimRange.copy(splitPoints = updated))
                    },
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val updated = trimRange.splitPoints.toMutableList()
                        updated.removeAt(index)
                        onTrimRangeChange(trimRange.copy(splitPoints = updated))
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = texts.text(R.string.ui_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OutlinedButton(
            onClick = {
                val totalSec = durationSeconds ?: 100.0
                val startSec = trimRange.startSeconds ?: 0.0
                val endSec = trimRange.endSeconds ?: totalSec
                val points = (listOf(startSec) + trimRange.splitPoints + listOf(endSec)).sorted()
                var maxGap = 0.0
                var insertPos = (startSec + endSec) / 2.0
                for (i in 0 until points.size - 1) {
                    val gap = points[i + 1] - points[i]
                    if (gap > maxGap) {
                        maxGap = gap
                        insertPos = (points[i] + points[i + 1]) / 2.0
                    }
                }
                val rounded = (Math.round(insertPos * 10.0) / 10.0).coerceIn(startSec + 0.1, endSec - 0.1)
                onTrimRangeChange(trimRange.copy(splitPoints = (trimRange.splitPoints + rounded).sorted()))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = texts.trimAddSplitPoint)
        }
    }
}

@Composable
private fun TrimSecondsField(
    value: Double?,
    label: String,
    isError: Boolean,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = LocalConfiguration.current.locales[0]
    var text by remember {
        mutableStateOf(value?.let { formatTrimSecondsInput(it, locale) }.orEmpty())
    }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, locale) {
        if (!focused && (value == null || value.isFinite())) {
            text = value?.let { formatTrimSecondsInput(it, locale) }.orEmpty()
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { rawValue ->
            val cleaned = sanitizeTrimSecondsInput(rawValue)
            text = cleaned
            val parsed = cleaned.toLocalizedDoubleOrNull(locale)
            if (cleaned.isBlank() || parsed != null) onValueChange(parsed)
        },
        singleLine = true,
        label = { Text(label) },
        isError = isError || (text.isNotBlank() && text.toLocalizedDoubleOrNull(locale) == null),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            errorBorderColor = MaterialTheme.colorScheme.error,
            errorLabelColor = MaterialTheme.colorScheme.error,
            errorCursorColor = MaterialTheme.colorScheme.error,
            errorContainerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .defaultMinSize(minWidth = 0.dp)
            .onFocusChanged { focusState ->
                if (focused && !focusState.isFocused && (value == null || value.isFinite())) {
                    text = value?.let { formatTrimSecondsInput(it, locale) }.orEmpty()
                }
                focused = focusState.isFocused
            }
    )
}

private fun sanitizeTrimSecondsInput(rawValue: String): String {
    return rawValue.take(32)
}

private fun formatTrimSecondsInput(seconds: Double, locale: Locale): String {
    if (!seconds.isFinite()) return ""
    val millis = Math.round(seconds * 1_000.0)
    val normalized = millis.toDouble() / 1_000.0
    return java.text.NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = 3
    }.format(normalized)
}

private fun formatTrimSeconds(seconds: Double, texts: UiText): String {
    if (!seconds.isFinite() || seconds < 0.0) return "—"
    val millis = Math.round(seconds * 1_000.0)
    return formatDurationMs(millis, texts)
}

private fun mediaTrimErrorText(
    trimRange: MediaTrimRange,
    durationMs: Long?,
    texts: UiText
): String? {
    if (!trimRange.isEnabled) return null
    val startSeconds = trimRange.startSeconds ?: 0.0
    val endSeconds = trimRange.endSeconds
    val startMs = trimSecondsToMs(startSeconds) ?: return texts.trimRangeTooLarge
    if (endSeconds != null) {
        val endMs = trimSecondsToMs(endSeconds) ?: return texts.trimRangeTooLarge
        if (endMs <= startMs) return texts.trimEndAfterStart
        if (durationMs != null && endMs > durationMs) return texts.trimEndWithinDuration
    }
    if (durationMs != null && startMs >= durationMs) return texts.trimStartBeforeDuration
    if (trimRange.splitPoints.isNotEmpty()) {
        var lastSeconds = startSeconds
        val maxLimitSeconds = endSeconds ?: durationMs?.let { it.toDouble() / 1_000.0 }
        for (splitPoint in trimRange.splitPoints) {
            if (!splitPoint.isFinite() || splitPoint < 0.0) return texts.trimRangeTooLarge
            if (splitPoint <= lastSeconds) return texts.trimSplitPointsOrder
            if (maxLimitSeconds != null && splitPoint >= maxLimitSeconds) return texts.trimSplitPointsWithinDuration
            lastSeconds = splitPoint
        }
    }
    return null
}

private fun trimSecondsToMs(seconds: Double): Long? {
    if (!seconds.isFinite() || seconds < 0.0) return null
    return runCatching { Math.round(seconds * 1_000.0) }.getOrNull()
}

@Composable
private fun VideoAdvancedOptionsPanel(
    texts: UiText,
    state: VideoAdvancedUiState,
    menuPrefix: String = "",
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
            "${menuPrefix}video-advanced-fade-in",
            texts.fadeInLabel(),
            state.fadeIn,
            ADVANCED_FADE_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onFadeInChange
        )
        OptionDropdown(
            "${menuPrefix}video-advanced-fade-out",
            texts.fadeOutLabel(),
            state.fadeOut,
            ADVANCED_FADE_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onFadeOutChange
        )
        OptionDropdown(
            "${menuPrefix}video-advanced-mirror",
            texts.mirrorLabel(),
            state.mirror,
            VIDEO_MIRROR_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onMirrorChange
        )
        OptionDropdown(
            "${menuPrefix}video-advanced-rotation",
            texts.rotationLabel(),
            state.rotation,
            VIDEO_ROTATION_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onRotationChange
        )
        OptionDropdown(
            "${menuPrefix}video-advanced-aspect",
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
    menuPrefix: String = "",
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
            "${menuPrefix}audio-advanced-fade-in",
            texts.fadeInLabel(),
            state.fadeIn,
            ADVANCED_FADE_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onFadeInChange
        )
        OptionDropdown(
            "${menuPrefix}audio-advanced-fade-out",
            texts.fadeOutLabel(),
            state.fadeOut,
            ADVANCED_FADE_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onFadeOutChange
        )
        OptionDropdown(
            "${menuPrefix}audio-advanced-volume",
            texts.volumeLabel(),
            state.volume,
            AUDIO_VOLUME_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onVolumeChange
        )
        OptionDropdown(
            "${menuPrefix}audio-advanced-echo",
            texts.echoLabel(),
            state.echo,
            AUDIO_ECHO_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onEchoChange
        )
        OptionDropdown(
            "${menuPrefix}audio-advanced-denoise",
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
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
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
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
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
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = ZenAnimations.IconRotationSpring,
        label = "advancedArrow"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.045f), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .animateContentSize(
                animationSpec = spring(stiffness = ZenAnimations.DropdownEnterStiffness)
            )
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
                    .rotate(arrowRotation)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = ZenAnimations.DropdownEnter,
            exit = ZenAnimations.DropdownExit
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
    menuPrefix: String = "",
    targetFormat: TargetFormat,
    quality: String,
    pdfPageMode: String,
    superResolution: String,
    availableAiModels: Set<String>,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onQualityChange: (String) -> Unit,
    onPdfPageModeChange: (String) -> Unit,
    onSuperResolutionChange: (String) -> Unit
) {
    if (targetFormat.extension.equals("pdf", ignoreCase = true)) {
        OptionGrid {
            OptionDropdown(
                "${menuPrefix}image-pdf-page-mode",
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

    if (targetFormat.extension.equals("ico", ignoreCase = true)) {
        Text(
            text = texts.optionValue("Lossless output"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val isPng = targetFormat.extension.equals("png", ignoreCase = true)
    val superResolutionActive = superResolution != IMAGE_SUPER_RESOLUTION_OFF &&
        superResolution != BATCH_MIXED_OPTION
    val disabledOptions = setOf(IMAGE_SUPER_RESOLUTION_AI_ANIME, IMAGE_SUPER_RESOLUTION_AI) - availableAiModels

    OptionGrid {
        OptionDropdown(
            menuId = "${menuPrefix}image-super-resolution",
            label = texts.superResolution,
            selected = superResolution,
            options = IMAGE_SUPER_RESOLUTION_OPTIONS,
            texts = texts,
            openMenuId = openMenuId,
            onOpenMenuChange = onOpenMenuChange,
            disabledOptions = disabledOptions,
            onSelected = onSuperResolutionChange
        )

        if (availableAiModels.isEmpty()) {
            Text(
                text = texts.aiUpscaleHint(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (superResolutionActive) {
            val isAi = superResolution == IMAGE_SUPER_RESOLUTION_AI ||
                superResolution == IMAGE_SUPER_RESOLUTION_AI_ANIME
            Text(
                text = if (isAi) {
                    texts.aiSuperResolutionSummary()
                } else {
                    texts.superResolutionSummary()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else if (isPng) {
            Text(
                text = texts.optionValue("Lossless output"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val qualityOptions = imageQualityOptionsFor(targetFormat)
            val selectedQuality = if (quality in qualityOptions) quality else IMAGE_QUALITY_BALANCED
            OptionDropdown(
                "${menuPrefix}image-quality",
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
}

@Composable
private fun PdfOptions(
    texts: UiText,
    menuPrefix: String = "",
    targetFormat: TargetFormat,
    renderQuality: String,
    compressionPreset: String = "",
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onRenderQualityChange: (String) -> Unit = {},
    onCompressionPresetChange: (String) -> Unit = {}
) {
    if (targetFormat.id == TargetId.PdfCompress) {
        OptionGrid {
            OptionDropdown(
                "${menuPrefix}pdf-compression-preset",
                texts.compressionPreset,
                compressionPreset,
                PDF_COMPRESSION_PRESET_OPTIONS,
                texts,
                openMenuId,
                onOpenMenuChange,
                onCompressionPresetChange
            )
        }
        return
    }

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
            "${menuPrefix}pdf-render-quality",
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
private fun OutputLocationSection(
    texts: UiText,
    outputLocationMode: OutputLocationMode,
    outputDirectory: OutputDirectory?,
    onOutputLocationModeChange: (OutputLocationMode) -> Unit,
    onPickOutputDirectory: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                icon = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = when (outputLocationMode) {
                        OutputLocationMode.Default -> texts.defaultOutputNote
                        OutputLocationMode.Custom ->
                            outputDirectory?.let { directory ->
                                directory.label.ifBlank {
                                    texts.text(
                                        if (directory.uri.lastPathSegment == null) R.string.ui_selected_folder
                                        else R.string.ui_device_storage
                                    )
                                }
                            } ?: texts.chooseFolderBeforeConversion
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        outputLocationMode == OutputLocationMode.Default -> texts.defaultOutputLocation
                        outputDirectory?.persistablePermissionSaved == true -> texts.folderPermissionSaved
                        outputDirectory != null -> texts.folderSelectedForSession
                        else -> texts.chooseFolderBeforeConversion
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        CenteredFlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalSpacing = 8.dp,
            verticalSpacing = 8.dp
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

@Composable
private fun QueueActions(
    texts: UiText,
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
            enabled = !isRunning,
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
private fun FileRow(
    modifier: Modifier = Modifier,
    texts: UiText,
    file: QueuedFile,
    progress: TaskProgress?,
    canEdit: Boolean,
    supportedVideoMimeTypes: Set<String>,
    availableAiModels: Set<String>,
    isRifeModelDownloaded: Boolean = false,
    openMenuId: String?,
    optionsExpanded: Boolean,
    groupedInPdfMerge: Boolean,
    groupedInVideoMerge: Boolean = false,
    onOpenMenuChange: (String?) -> Unit,
    onUpdateFile: (QueuedFile) -> Unit,
    onOptionsExpandedChange: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val completedProgress = progress?.takeIf {
        it.status == TaskProgressStatus.Completed && it.outputUriList().isNotEmpty()
    }
    val selectedTarget = selectedTargetFor(file)
    var videoAdvancedExpanded by remember(file.id) { mutableStateOf(false) }
    var audioAdvancedExpanded by remember(file.id) { mutableStateOf(false) }
    var trimInputMode by remember(file.id) { mutableStateOf(TrimInputMode.Quick) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
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
                    enabled = canEdit,
                    onClick = onRemove
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallTag(texts.categoryLabel(file.sourceCategory))
                SmallTag(texts.toFormat(texts.optionValue(file.targetFormat)))
                SmallTag(texts.progressLabel(progress))
                if (groupedInPdfMerge) {
                    SmallTag(texts.pdfMergeMember)
                }
                if (groupedInVideoMerge) {
                    SmallTag(texts.videoMergeMember)
                }
            }
            if (canEdit) {
                OptionsToggleChip(
                    texts = texts,
                    expanded = optionsExpanded,
                    onClick = { onOptionsExpandedChange(!optionsExpanded) }
                )
            }
        }
        if (canEdit) {
            CenteredFlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalSpacing = 8.dp,
                verticalSpacing = 8.dp
            ) {
                targetsForQueuedFile(file).forEach { target ->
                    ExternalImportTargetChip(
                        texts = texts,
                        target = target,
                        selected = target == selectedTarget,
                        onSelected = {
                            onOpenMenuChange(null)
                            onUpdateFile(fileWithTarget(file, target, supportedVideoMimeTypes))
                        }
                    )
                }
            }
            AnimatedVisibility(
                visible = optionsExpanded,
                enter = ZenAnimations.DropdownEnter,
                exit = ZenAnimations.DropdownExit
            ) {
                QueuedFileOptionsPanel(
                    texts = texts,
                    file = file,
                    selectedTarget = selectedTarget,
                    supportedVideoMimeTypes = supportedVideoMimeTypes,
                    availableAiModels = availableAiModels,
                    isRifeModelDownloaded = isRifeModelDownloaded,
                    videoAdvancedExpanded = videoAdvancedExpanded,
                    audioAdvancedExpanded = audioAdvancedExpanded,
                    trimInputMode = trimInputMode,
                    openMenuId = openMenuId,
                    onOpenMenuChange = onOpenMenuChange,
                    onTrimInputModeChange = { trimInputMode = it },
                    onVideoAdvancedExpandedChange = { videoAdvancedExpanded = it },
                    onAudioAdvancedExpandedChange = { audioAdvancedExpanded = it },
                    onUpdateFile = onUpdateFile
                )
            }
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
private fun OptionsToggleChip(
    texts: UiText,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = ZenAnimations.IconRotationSpring,
        label = "OptionsToggleRotation"
    )
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 32.dp),
        shape = RoundedCornerShape(100.dp),
        border = BorderStroke(
            1.dp,
            if (expanded) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (expanded) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (expanded) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = texts.adjustOptions,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .rotate(rotation)
        )
    }
}

@Composable
private fun QueuedFileOptionsPanel(
    texts: UiText,
    file: QueuedFile,
    selectedTarget: ExternalImportTarget,
    supportedVideoMimeTypes: Set<String>,
    availableAiModels: Set<String>,
    isRifeModelDownloaded: Boolean = false,
    videoAdvancedExpanded: Boolean,
    audioAdvancedExpanded: Boolean,
    trimInputMode: TrimInputMode,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onTrimInputModeChange: (TrimInputMode) -> Unit,
    onVideoAdvancedExpandedChange: (Boolean) -> Unit,
    onAudioAdvancedExpandedChange: (Boolean) -> Unit,
    onUpdateFile: (QueuedFile) -> Unit
) {
    val menuPrefix = "file-${file.id}-"
    AnimatedContent(
        targetState = "${file.category.name}-${file.targetFormat}",
        transitionSpec = {
            fadeIn(animationSpec = tween(ZenAnimations.ContentFadeDuration)) togetherWith
                fadeOut(animationSpec = tween(ZenAnimations.ContentFadeOutDuration)) using
                SizeTransform(clip = false)
        },
        label = "QueuedFileOptions"
    ) {
        when (file.category) {
            FileCategory.Video -> VideoOptions(
                texts = texts,
                menuPrefix = menuPrefix,
                trimRange = file.videoOptions.trimRange,
                sourceDurationMs = file.inputInfo?.durationMs,
                resolution = videoResolutionLabelFor(file.videoOptions),
                compressionMode = videoCompressionLabelFor(file.videoOptions.compressionMode),
                bitrate = videoBitrateLabelFor(file.videoOptions.videoBitrate),
                codec = videoCodecLabelFor(file.videoOptions.videoMimeType),
                codecOptions = videoCodecOptionsFor(supportedVideoMimeTypes),
                frameRate = videoFrameRateLabelFor(file.videoOptions.maxFrameRate),
                audioBitrate = audioBitrateLabelFor(file.audioOptions.audioBitrate),
                audioSampleRate = audioSampleRateLabelFor(file.audioOptions.sampleRateHz),
                audioChannels = audioChannelsLabelFor(file.audioOptions.channelCount),
                videoAdvanced = videoAdvancedUiStateFor(file.videoOptions.advanced, videoAdvancedExpanded),
                audioAdvanced = audioAdvancedUiStateFor(file.audioOptions.advanced, audioAdvancedExpanded),
                targetFormat = selectedTarget.targetFormat,
                trimInputMode = trimInputMode,
                contactSheetOptions = file.contactSheetOptions,
                onContactSheetOptionsChange = { options ->
                    onUpdateFile(file.copy(contactSheetOptions = options))
                },
                sourceShortSide = file.inputInfo?.let {
                    val w = it.width ?: 0
                    val h = it.height ?: 0
                    if (w > 0 && h > 0) minOf(w, h) else null
                },
                frameInterpolation = videoInterpolationLabelFor(file.videoOptions.frameInterpolation),
                isRifeModelDownloaded = isRifeModelDownloaded,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onTrimInputModeChange = onTrimInputModeChange,
                onFrameInterpolationChange = { value ->
                    val mode = videoInterpolationModeFor(value)
                    val isInterpolationActive = mode != VideoFrameInterpolationMode.Off
                    val isOpticalFlow = mode == VideoFrameInterpolationMode.OpticalFlow2x
                    val sourceShort = file.inputInfo?.let {
                        val w = it.width ?: 0
                        val h = it.height ?: 0
                        if (w > 0 && h > 0) minOf(w, h) else null
                    }
                    val currentRes = file.videoOptions.maxShortSidePixels
                    val clampedRes = if (isOpticalFlow) {
                        if (currentRes == null) {
                            if (sourceShort != null && sourceShort > 1080) 1080 else null
                        } else if (currentRes > 1080) {
                            1080
                        } else {
                            currentRes
                        }
                    } else {
                        currentRes
                    }
                    onUpdateFile(
                        file.copy(
                            videoOptions = file.videoOptions.copy(
                                frameInterpolation = mode,
                                maxShortSidePixels = clampedRes,
                                compressionMode = if (isInterpolationActive) VideoCompressionMode.Standard else file.videoOptions.compressionMode,
                                advanced = if (isInterpolationActive) VideoAdvancedOptions() else file.videoOptions.advanced
                            )
                        )
                    )
                },
                onTrimStartSecondsChange = { value ->
                    onUpdateFile(
                        file.copy(
                            videoOptions = file.videoOptions.copy(
                                trimRange = file.videoOptions.trimRange.copy(startSeconds = value)
                            )
                        )
                    )
                },
                onTrimEndSecondsChange = { value ->
                    onUpdateFile(
                        file.copy(
                            videoOptions = file.videoOptions.copy(
                                trimRange = file.videoOptions.trimRange.copy(endSeconds = value)
                            )
                        )
                    )
                },
                onTrimRangeChange = { range ->
                    onUpdateFile(
                        file.copy(
                            videoOptions = file.videoOptions.copy(trimRange = range)
                        )
                    )
                },
                onResolutionChange = { value ->
                    onUpdateFile(
                        file.copy(
                            videoOptions = file.videoOptions.copy(
                                maxShortSidePixels = videoResolutionToShortSide(value)
                            )
                        )
                    )
                },
                onCompressionModeChange = { value ->
                    val mode = videoCompressionModeFor(value)
                    val presetActive = mode != VideoCompressionMode.Standard
                    onUpdateFile(
                        file.copy(
                            videoOptions = file.videoOptions.copy(
                                compressionMode = mode,
                                videoBitrate = if (presetActive) null else file.videoOptions.videoBitrate,
                                videoMimeType = if (
                                    presetActive &&
                                    VideoExportOptions.VIDEO_MIME_TYPE_H265 in supportedVideoMimeTypes
                                ) {
                                    VideoExportOptions.VIDEO_MIME_TYPE_H265
                                } else {
                                    file.videoOptions.videoMimeType
                                },
                                maxShortSidePixels = videoCompressionShortSideFor(mode)
                                    ?: file.videoOptions.maxShortSidePixels,
                                maxFrameRate = videoCompressionFrameRateCapFor(mode)
                                    ?: file.videoOptions.maxFrameRate,
                                advanced = if (presetActive) VideoAdvancedOptions() else file.videoOptions.advanced
                            )
                        )
                    )
                },
                onBitrateChange = { value ->
                    onUpdateFile(file.copy(videoOptions = file.videoOptions.copy(videoBitrate = videoBitrateToBits(value))))
                },
                onCodecChange = { value ->
                    onUpdateFile(file.copy(videoOptions = file.videoOptions.copy(videoMimeType = videoCodecToMimeType(value))))
                },
                onFrameRateChange = { value ->
                    onUpdateFile(file.copy(videoOptions = file.videoOptions.copy(maxFrameRate = videoFrameRateToCap(value))))
                },
                onAudioBitrateChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(audioBitrate = audioBitrateToBits(value))))
                },
                onAudioSampleRateChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(sampleRateHz = audioSampleRateToHz(value))))
                },
                onAudioChannelsChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(channelCount = audioChannelsToCount(value))))
                },
                onVideoAdvancedExpandedChange = onVideoAdvancedExpandedChange,
                onVideoReverseChange = { enabled ->
                    onUpdateFile(file.copy(videoOptions = file.videoOptions.copy(advanced = file.videoOptions.advanced.copy(reverse = enabled))))
                },
                onVideoFadeInChange = { value ->
                    onUpdateFile(file.copy(videoOptions = file.videoOptions.copy(advanced = file.videoOptions.advanced.copy(fadeInSeconds = fadeDurationSeconds(value)))))
                },
                onVideoFadeOutChange = { value ->
                    onUpdateFile(file.copy(videoOptions = file.videoOptions.copy(advanced = file.videoOptions.advanced.copy(fadeOutSeconds = fadeDurationSeconds(value)))))
                },
                onVideoMirrorChange = { value ->
                    onUpdateFile(file.copy(videoOptions = file.videoOptions.copy(advanced = file.videoOptions.advanced.copy(mirror = videoMirrorModeFor(value)))))
                },
                onVideoRotationChange = { value ->
                    onUpdateFile(file.copy(videoOptions = file.videoOptions.copy(advanced = file.videoOptions.advanced.copy(rotation = videoRotationModeFor(value)))))
                },
                onVideoAspectRatioChange = { value ->
                    onUpdateFile(file.copy(videoOptions = file.videoOptions.copy(advanced = file.videoOptions.advanced.copy(aspectRatio = videoAspectRatioModeFor(value)))))
                },
                onAudioAdvancedExpandedChange = onAudioAdvancedExpandedChange,
                onAudioReverseChange = { enabled ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(advanced = file.audioOptions.advanced.copy(reverse = enabled))))
                },
                onAudioFadeInChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(advanced = file.audioOptions.advanced.copy(fadeInSeconds = fadeDurationSeconds(value)))))
                },
                onAudioFadeOutChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(advanced = file.audioOptions.advanced.copy(fadeOutSeconds = fadeDurationSeconds(value)))))
                },
                onAudioVolumeChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(advanced = file.audioOptions.advanced.copy(volume = audioVolumeModeFor(value)))))
                },
                onAudioEchoChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(advanced = file.audioOptions.advanced.copy(echo = audioEchoModeFor(value)))))
                },
                onAudioNoiseReductionChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(advanced = file.audioOptions.advanced.copy(noiseReduction = audioNoiseReductionModeFor(value)))))
                }
            )
            FileCategory.Audio -> AudioOptions(
                texts = texts,
                menuPrefix = menuPrefix,
                trimRange = file.audioOptions.trimRange,
                sourceDurationMs = file.inputInfo?.durationMs,
                bitrate = audioBitrateLabelFor(file.audioOptions.audioBitrate),
                sampleRate = audioSampleRateLabelFor(file.audioOptions.sampleRateHz),
                channels = audioChannelsLabelFor(file.audioOptions.channelCount),
                advanced = audioAdvancedUiStateFor(file.audioOptions.advanced, audioAdvancedExpanded),
                targetFormat = selectedTarget.targetFormat,
                trimInputMode = trimInputMode,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onTrimInputModeChange = onTrimInputModeChange,
                onTrimStartSecondsChange = { value ->
                    onUpdateFile(
                        file.copy(
                            audioOptions = file.audioOptions.copy(
                                trimRange = file.audioOptions.trimRange.copy(startSeconds = value)
                            )
                        )
                    )
                },
                onTrimEndSecondsChange = { value ->
                    onUpdateFile(
                        file.copy(
                            audioOptions = file.audioOptions.copy(
                                trimRange = file.audioOptions.trimRange.copy(endSeconds = value)
                            )
                        )
                    )
                },
                onTrimRangeChange = { range ->
                    onUpdateFile(
                        file.copy(
                            audioOptions = file.audioOptions.copy(trimRange = range)
                        )
                    )
                },
                onBitrateChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(audioBitrate = audioBitrateToBits(value))))
                },
                onSampleRateChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(sampleRateHz = audioSampleRateToHz(value))))
                },
                onChannelsChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(channelCount = audioChannelsToCount(value))))
                },
                onAdvancedExpandedChange = onAudioAdvancedExpandedChange,
                onReverseChange = { enabled ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(advanced = file.audioOptions.advanced.copy(reverse = enabled))))
                },
                onFadeInChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(advanced = file.audioOptions.advanced.copy(fadeInSeconds = fadeDurationSeconds(value)))))
                },
                onFadeOutChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(advanced = file.audioOptions.advanced.copy(fadeOutSeconds = fadeDurationSeconds(value)))))
                },
                onVolumeChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(advanced = file.audioOptions.advanced.copy(volume = audioVolumeModeFor(value)))))
                },
                onEchoChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(advanced = file.audioOptions.advanced.copy(echo = audioEchoModeFor(value)))))
                },
                onNoiseReductionChange = { value ->
                    onUpdateFile(file.copy(audioOptions = file.audioOptions.copy(advanced = file.audioOptions.advanced.copy(noiseReduction = audioNoiseReductionModeFor(value)))))
                }
            )
            FileCategory.Image -> {
                ImageOptions(
                    texts = texts,
                    menuPrefix = menuPrefix,
                    targetFormat = selectedTarget.targetFormat,
                    quality = imageQualityLabelFor(file.imageOptions, selectedTarget.targetFormat),
                    pdfPageMode = pdfPageModeLabelFor(file.pdfOptions.imagePageMode),
                    superResolution = superResolutionLabelFor(file.imageOptions.superResolution),
                    availableAiModels = availableAiModels,
                    openMenuId = openMenuId,
                    onOpenMenuChange = onOpenMenuChange,
                    onQualityChange = { value ->
                        onUpdateFile(file.copy(imageOptions = imageOptionsForQuality(value, selectedTarget.targetFormat)))
                    },
                    onPdfPageModeChange = { value ->
                        onUpdateFile(file.copy(pdfOptions = file.pdfOptions.copy(imagePageMode = pdfPageModeToOption(value))))
                    },
                    onSuperResolutionChange = { value ->
                        val mode = superResolutionModeFor(value)
                        onUpdateFile(
                            file.copy(
                                imageOptions = file.imageOptions.copy(superResolution = mode),
                                gifFrameMode = if (
                                    mode != ImageSuperResolutionMode.Off &&
                                    file.isGifQueuedImage()
                                ) {
                                    GifFrameExportMode.FirstFrame
                                } else {
                                    file.gifFrameMode
                                }
                            )
                        )
                    }
                )
                if (
                    file.isGifQueuedImage() &&
                    file.imageOptions.superResolution == ImageSuperResolutionMode.Off
                ) {
                    GifImageOptions(
                        texts = texts,
                        file = file,
                        targetFormat = selectedTarget.targetFormat,
                        openMenuId = openMenuId,
                        onOpenMenuChange = onOpenMenuChange,
                        onUpdateFile = onUpdateFile
                    )
                }
            }
            FileCategory.Pdf -> PdfOptions(
                texts = texts,
                menuPrefix = menuPrefix,
                targetFormat = selectedTarget.targetFormat,
                renderQuality = pdfRenderQualityLabelFor(file.pdfOptions.renderQuality),
                compressionPreset = pdfCompressionPresetLabelFor(file.pdfOptions.compressionPreset),
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onRenderQualityChange = { value ->
                    onUpdateFile(file.copy(pdfOptions = file.pdfOptions.copy(renderQuality = pdfRenderQualityToOption(value))))
                },
                onCompressionPresetChange = { value ->
                    onUpdateFile(file.copy(pdfOptions = file.pdfOptions.copy(compressionPreset = pdfCompressionPresetToOption(value))))
                }
            )
            FileCategory.Document -> Text(
                text = texts.optionValue(selectedTarget.targetFormat.modeHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FileCategory.Font -> Text(
                text = texts.optionValue(selectedTarget.targetFormat.modeHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FileCategory.Subtitle -> Text(
                text = texts.optionValue(selectedTarget.targetFormat.modeHint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GifImageOptions(
    texts: UiText,
    file: QueuedFile,
    targetFormat: TargetFormat,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onUpdateFile: (QueuedFile) -> Unit
) {
    val frameModeOptions = if (targetFormat.extension.equals("pdf", ignoreCase = true)) {
        GIF_PDF_FRAME_MODE_OPTIONS
    } else {
        GIF_IMAGE_FRAME_MODE_OPTIONS
    }
    OptionDropdown(
        "file-${file.id}-gif-frame-mode",
        texts.gifFrameMode,
        gifFrameModeLabelFor(file.gifFrameMode, targetFormat),
        frameModeOptions,
        texts,
        openMenuId,
        onOpenMenuChange
    ) { value ->
        onUpdateFile(file.copy(gifFrameMode = gifFrameModeForLabel(value, targetFormat)))
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
    OptionDropdown(
        menuId = menuId,
        label = label,
        selected = selected,
        options = options,
        texts = texts,
        openMenuId = openMenuId,
        onOpenMenuChange = onOpenMenuChange,
        disabledOptions = emptySet(),
        onSelected = onSelected
    )
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
    disabledOptions: Set<String>,
    onSelected: (String) -> Unit
) {
    val expanded = openMenuId == menuId

    Column(horizontalAlignment = Alignment.End) {
        PillMenuButton(
            onClick = { onOpenMenuChange(if (expanded) null else menuId) },
            text = stringResource(R.string.display_option_dropdown_1_s_2_s, label, texts.optionValue(selected)),
            expanded = expanded
        )
        InlineDropdownPanel(
            expanded = expanded
        ) {
            options.forEach { option ->
                DropdownOption(
                    text = texts.optionValue(option),
                    selected = option == selected,
                    enabled = option !in disabledOptions,
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
        animationSpec = ZenAnimations.IconRotationSpring,
        label = "dropdownArrow"
    )
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(100.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (expanded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            1.dp,
            if (expanded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
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
        enter = fadeIn(animationSpec = spring(stiffness = ZenAnimations.DropdownEnterStiffness)) + expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = spring(stiffness = ZenAnimations.DropdownEnterStiffness)
        ),
        exit = fadeOut(animationSpec = spring(stiffness = ZenAnimations.DropdownExitStiffness)) + shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = spring(stiffness = ZenAnimations.DropdownExitStiffness)
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
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
    onClick: () -> Unit,
    enabled: Boolean = true
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
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            AppIcon(
                icon = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
        } else if (!enabled) {
            AppIcon(
                icon = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
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
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(100.dp))
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
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
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
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(stiffness = ZenAnimations.PanelEnterStiffness)
            ),
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

internal fun FileCategory.icon(): ImageVector {
    return when (this) {
        FileCategory.Video -> Icons.Rounded.Videocam
        FileCategory.Audio -> Icons.Rounded.AudioFile
        FileCategory.Image -> Icons.Rounded.Image
        FileCategory.Pdf -> Icons.Rounded.PictureAsPdf
        FileCategory.Document -> Icons.Rounded.Description
        FileCategory.Font -> Icons.Rounded.FontDownload
        FileCategory.Subtitle -> Icons.Rounded.Subtitles
    }
}

private fun zenConverterColorScheme(
    context: Context,
    accent: AccentColorOption,
    isDark: Boolean,
    isOled: Boolean
): ColorScheme {
    if (accent == AccentColorOption.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return if (!isDark) {
            dynamicLightColorScheme(context)
        } else if (isOled) {
            dynamicDarkColorScheme(context).copy(
                background = Color(0xFF000000),
                surface = Color(0xFF000000),
                surfaceVariant = Color(0xFF141414),
                surfaceContainerLowest = Color(0xFF000000),
                surfaceContainerLow = Color(0xFF0C0C0C),
                surfaceContainer = Color(0xFF121212),
                surfaceContainerHigh = Color(0xFF181818),
                surfaceContainerHighest = Color(0xFF202020),
                outline = Color(0xFF282828),
                outlineVariant = Color(0xFF1C1C1C),
                onBackground = Color(0xFFF5F5F5),
                onSurface = Color(0xFFF5F5F5),
                onSurfaceVariant = Color(0xFFA3A3A3)
            )
        } else {
            dynamicDarkColorScheme(context)
        }
    }

    val safeAccent = if (accent == AccentColorOption.Dynamic) AccentColorOption.Charcoal else accent
    val primary = safeAccent.color(isDark)
    val onPrimary = safeAccent.contentColor(isDark)

    return if (!isDark) {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = primary,
            onSecondary = onPrimary,
            background = Color.White,
            onBackground = Color(0xFF111111),
            surface = Color.White,
            onSurface = Color(0xFF111111),
            surfaceVariant = Color(0xFFF6F6F6),
            onSurfaceVariant = Color(0xFF666666),
            outline = Color(0xFFD8D8D8),
            outlineVariant = Color(0xFFEBEBEB),
            error = Color(0xFFB3261E),
            onError = Color.White
        )
    } else if (isOled) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = primary,
            onSecondary = onPrimary,
            background = Color(0xFF000000),
            onBackground = Color(0xFFF5F5F5),
            surface = Color(0xFF000000),
            onSurface = Color(0xFFF5F5F5),
            surfaceVariant = Color(0xFF141414),
            onSurfaceVariant = Color(0xFFA3A3A3),
            outline = Color(0xFF282828),
            outlineVariant = Color(0xFF1C1C1C),
            error = Color(0xFFCF6679),
            onError = Color(0xFF1E0005)
        )
    } else {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = primary,
            onSecondary = onPrimary,
            background = Color(0xFF16181D),
            onBackground = Color(0xFFF3F4F6),
            surface = Color(0xFF1E222A),
            onSurface = Color(0xFFF3F4F6),
            surfaceVariant = Color(0xFF262B35),
            onSurfaceVariant = Color(0xFF9CA3AF),
            outline = Color(0xFF374151),
            outlineVariant = Color(0xFF2B3240),
            error = Color(0xFFEF4444),
            onError = Color.White
        )
    }
}

private fun accentColorFromPreference(value: String?): AccentColorOption {
    if (value == "Dynamic" && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return AccentColorOption.Charcoal
    }
    return AccentColorOption.entries.firstOrNull { it.name == value }
        ?: AccentColorOption.Charcoal
}

private fun themeModeFromPreference(value: String?): ThemeModeOption {
    if (value == "OledDark") return ThemeModeOption.Dark
    return ThemeModeOption.entries.firstOrNull { it.name == value }
        ?: ThemeModeOption.System
}



private fun targetsForSourceCategory(category: FileCategory?): List<ExternalImportTarget> {
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
        FileCategory.Pdf -> FileCategory.Pdf.formats.map {
            ExternalImportTarget(FileCategory.Pdf, it)
        }
        FileCategory.Document -> FileCategory.Document.formats.map {
            ExternalImportTarget(FileCategory.Document, it)
        }
        FileCategory.Font -> FileCategory.Font.formats.map {
            ExternalImportTarget(FileCategory.Font, it)
        }
        FileCategory.Subtitle -> FileCategory.Subtitle.formats.map {
            ExternalImportTarget(FileCategory.Subtitle, it)
        }
        null -> emptyList()
    }
}

private fun targetsForQueuedFile(file: QueuedFile): List<ExternalImportTarget> {
    return file.targetOptions.ifEmpty { targetsForSourceCategory(file.sourceCategory) }
}

private fun mergeablePdfFilesFor(
    files: List<QueuedFile>,
    groups: List<PdfMergeGroup>,
    type: PdfMergeType
): List<QueuedFile> {
    val groupedIds = groups.flatMap { it.memberFileIds }.toSet()
    return files.filter { file ->
        file.id !in groupedIds && file.isMergeableFor(type)
    }
}

private fun QueuedFile.isMergeableFor(type: PdfMergeType): Boolean {
    return when (type) {
        PdfMergeType.Images -> category == FileCategory.Image &&
            targetFormat.equals("PDF", ignoreCase = true)
        PdfMergeType.Pdfs -> category == FileCategory.Pdf &&
            targetFormat.equals("PDF", ignoreCase = true) &&
            pdfSecurityOptions.mode == org.zenconverter.app.conversion.PdfSecurityMode.None
    }
}

private fun compactMergeFileName(name: String): String {
    return if (name.length <= 28) {
        name
    } else {
        "${name.take(13)}...${name.takeLast(10)}"
    }
}

private fun commonSelectedTargetFor(files: List<QueuedFile>): ExternalImportTarget? {
    return files
        .map { selectedTargetFor(it) }
        .distinctBy { "${it.category.name}:${it.targetFormat.key}" }
        .singleOrNull()
}

private fun selectedTargetFor(file: QueuedFile): ExternalImportTarget {
    return targetsForQueuedFile(file).firstOrNull { target ->
        target.category == file.category && target.targetFormat.id == TargetId.fromKey(file.targetFormat)
    } ?: ExternalImportTarget(
        category = file.category,
        targetFormat = file.category.formats.firstOrNull { it.id == TargetId.fromKey(file.targetFormat) }
            ?: file.category.formats.first()
    )
}

private fun fileWithTarget(
    file: QueuedFile,
    target: ExternalImportTarget,
    supportedVideoMimeTypes: Set<String>
): QueuedFile {
    val targetFormat = target.targetFormat
    val nextVideoOptions = videoOptionsForTarget(file.videoOptions, targetFormat, supportedVideoMimeTypes)
        .let { options ->
            if (
                target.category == FileCategory.Video &&
                file.category == FileCategory.Audio &&
                !options.trimRange.isEnabled &&
                file.audioOptions.trimRange.isEnabled
            ) {
                options.copy(trimRange = file.audioOptions.trimRange)
            } else {
                options
            }
        }
    val nextAudioOptions = audioOptionsForTarget(
        if (
            target.category == FileCategory.Audio &&
            file.category == FileCategory.Video &&
            !file.audioOptions.trimRange.isEnabled &&
            file.videoOptions.trimRange.isEnabled
        ) {
            file.audioOptions.copy(trimRange = file.videoOptions.trimRange)
        } else {
            file.audioOptions
        },
        targetFormat
    )
    val nextPdfSecurityOptions = when {
        target.category == FileCategory.Pdf &&
            targetFormat.id == TargetId.PdfEncrypt ->
            PdfSecurityOptions(mode = org.zenconverter.app.conversion.PdfSecurityMode.Encrypt)
        target.category == FileCategory.Pdf &&
            targetFormat.id == TargetId.PdfDecrypt ->
            PdfSecurityOptions(mode = org.zenconverter.app.conversion.PdfSecurityMode.Decrypt)
        else -> PdfSecurityOptions()
    }
    return file.copy(
        category = target.category,
        targetFormat = targetFormat.key,
        videoOptions = nextVideoOptions,
        audioOptions = nextAudioOptions,
        imageOptions = imageOptionsForTarget(file.imageOptions, targetFormat),
        pdfSecurityOptions = nextPdfSecurityOptions
    )
}

private fun videoOptionsForTarget(
    current: VideoExportOptions,
    targetFormat: TargetFormat,
    supportedVideoMimeTypes: Set<String>
): VideoExportOptions {
    if (targetFormat.extension.equals("gif", ignoreCase = true)) {
        return current.copy(
            maxShortSidePixels = 480,
            videoBitrate = null,
            videoMimeType = VideoExportOptions.VIDEO_MIME_TYPE_H264,
            maxFrameRate = 30,
            compressionMode = VideoCompressionMode.Standard,
            advanced = VideoAdvancedOptions()
        )
    }
    val codec = if (current.videoMimeType in supportedVideoMimeTypes) {
        current.videoMimeType
    } else {
        VideoExportOptions.VIDEO_MIME_TYPE_H264
    }
    return if (current.maxFrameRate == 30 && current.maxShortSidePixels == 480 &&
        current.videoBitrate == null && current.compressionMode == VideoCompressionMode.Standard
    ) {
        current.copy(videoMimeType = codec, maxShortSidePixels = null, maxFrameRate = null)
    } else {
        current.copy(videoMimeType = codec)
    }
}

private fun imageOptionsForTarget(
    current: ImageExportOptions,
    targetFormat: TargetFormat
): ImageExportOptions {
    val superResolutionReset = targetFormat.extension.equals("pdf", ignoreCase = true) ||
        targetFormat.extension.equals("ico", ignoreCase = true)
    return if (supportsWebpLosslessQuality(targetFormat)) {
        if (superResolutionReset) {
            current.copy(superResolution = ImageSuperResolutionMode.Off)
        } else {
            current
        }
    } else {
        current.copy(
            webpLossless = false,
            superResolution = if (superResolutionReset) ImageSuperResolutionMode.Off else current.superResolution
        )
    }
}

private fun audioOptionsForTarget(
    current: AudioExportOptions,
    targetFormat: TargetFormat
): AudioExportOptions {
    if (isOpusTarget(targetFormat)) {
        return if (current.sampleRateHz != null && current.sampleRateHz !in OPUS_SUPPORTED_SAMPLE_RATES) {
            current.copy(sampleRateHz = null)
        } else {
            current
        }
    }
    return current
}

private fun videoResolutionLabelFor(options: VideoExportOptions): String {
    return when (options.maxShortSidePixels) {
        2160 -> VIDEO_RESOLUTION_2160P
        1440 -> VIDEO_RESOLUTION_1440P
        1080 -> VIDEO_RESOLUTION_1080P
        720 -> VIDEO_RESOLUTION_720P
        480 -> VIDEO_RESOLUTION_480P
        else -> VIDEO_RESOLUTION_ORIGINAL
    }
}

private fun videoCompressionLabelFor(mode: VideoCompressionMode): String {
    return when (mode) {
        VideoCompressionMode.VisualLossless -> VIDEO_COMPRESSION_VISUAL_LOSSLESS
        VideoCompressionMode.BalancedShrink -> VIDEO_COMPRESSION_BALANCED
        VideoCompressionMode.SmallFile -> VIDEO_COMPRESSION_SMALL
        VideoCompressionMode.Standard -> VIDEO_COMPRESSION_STANDARD
    }
}

private fun videoInterpolationLabelFor(mode: VideoFrameInterpolationMode): String {
    return when (mode) {
        VideoFrameInterpolationMode.OpticalFlow2x -> VIDEO_INTERPOLATION_OPTICAL_FLOW_2X
        VideoFrameInterpolationMode.Rife2x -> VIDEO_INTERPOLATION_RIFE_2X
        VideoFrameInterpolationMode.Off -> VIDEO_INTERPOLATION_OFF
    }
}

private fun videoBitrateLabelFor(value: Int?): String {
    return when (value) {
        1_000_000 -> VIDEO_BITRATE_LOW
        2_500_000 -> VIDEO_BITRATE_MEDIUM
        5_000_000 -> VIDEO_BITRATE_HIGH
        8_000_000 -> VIDEO_BITRATE_VERY_HIGH
        16_000_000 -> VIDEO_BITRATE_ULTRA
        else -> VIDEO_BITRATE_AUTO
    }
}

private fun videoCodecLabelFor(value: String): String {
    return when (value) {
        VideoExportOptions.VIDEO_MIME_TYPE_H265 -> VIDEO_CODEC_H265
        else -> VIDEO_CODEC_H264
    }
}

private fun videoFrameRateLabelFor(value: Int?): String {
    return when (value) {
        25 -> VIDEO_FRAME_RATE_25
        30 -> VIDEO_FRAME_RATE_30
        60 -> VIDEO_FRAME_RATE_60
        else -> VIDEO_FRAME_RATE_ORIGINAL
    }
}

private fun audioBitrateLabelFor(value: Int?): String {
    return when (value) {
        192_000 -> AUDIO_BITRATE_RECOMMENDED
        256_000 -> AUDIO_BITRATE_HIGH
        128_000 -> AUDIO_BITRATE_COMPACT
        96_000 -> AUDIO_BITRATE_VOICE
        else -> AUDIO_BITRATE_AUTO
    }
}

private fun audioSampleRateLabelFor(value: Int?): String {
    return when (value) {
        48_000 -> AUDIO_SAMPLE_RATE_RECOMMENDED
        44_100 -> AUDIO_SAMPLE_RATE_44100
        32_000 -> AUDIO_SAMPLE_RATE_32000
        24_000 -> AUDIO_SAMPLE_RATE_24000
        16_000 -> AUDIO_SAMPLE_RATE_16000
        8_000 -> AUDIO_SAMPLE_RATE_8000
        else -> AUDIO_SAMPLE_RATE_ORIGINAL
    }
}

private fun audioChannelsLabelFor(value: Int?): String {
    return when (value) {
        2 -> AUDIO_CHANNELS_STEREO
        1 -> AUDIO_CHANNELS_MONO
        else -> AUDIO_CHANNELS_ORIGINAL
    }
}

private fun videoAdvancedUiStateFor(
    advanced: VideoAdvancedOptions,
    expanded: Boolean
): VideoAdvancedUiState {
    return VideoAdvancedUiState(
        expanded = expanded,
        reverse = advanced.reverse,
        fadeIn = fadeLabelFor(advanced.fadeInSeconds),
        fadeOut = fadeLabelFor(advanced.fadeOutSeconds),
        mirror = videoMirrorLabelFor(advanced.mirror),
        rotation = videoRotationLabelFor(advanced.rotation),
        aspectRatio = videoAspectRatioLabelFor(advanced.aspectRatio)
    )
}

private fun audioAdvancedUiStateFor(
    advanced: AudioAdvancedOptions,
    expanded: Boolean
): AudioAdvancedUiState {
    return AudioAdvancedUiState(
        expanded = expanded,
        reverse = advanced.reverse,
        fadeIn = fadeLabelFor(advanced.fadeInSeconds),
        fadeOut = fadeLabelFor(advanced.fadeOutSeconds),
        volume = audioVolumeLabelFor(advanced.volume),
        echo = audioEchoLabelFor(advanced.echo),
        noiseReduction = audioNoiseReductionLabelFor(advanced.noiseReduction)
    )
}

private fun fadeLabelFor(value: Float?): String {
    return when (value) {
        0.5f -> ADVANCED_FADE_HALF_SECOND
        1f -> ADVANCED_FADE_ONE_SECOND
        2f -> ADVANCED_FADE_TWO_SECONDS
        else -> ADVANCED_FADE_OFF
    }
}

private fun videoMirrorLabelFor(value: VideoMirrorMode): String {
    return when (value) {
        VideoMirrorMode.Horizontal -> VIDEO_MIRROR_HORIZONTAL
        VideoMirrorMode.Vertical -> VIDEO_MIRROR_VERTICAL
        VideoMirrorMode.Both -> VIDEO_MIRROR_BOTH
        VideoMirrorMode.Off -> VIDEO_MIRROR_OFF
    }
}

private fun videoRotationLabelFor(value: VideoRotationMode): String {
    return when (value) {
        VideoRotationMode.Clockwise90 -> VIDEO_ROTATION_90_CW
        VideoRotationMode.CounterClockwise90 -> VIDEO_ROTATION_90_CCW
        VideoRotationMode.Rotate180 -> VIDEO_ROTATION_180
        VideoRotationMode.None -> VIDEO_ROTATION_NONE
    }
}

private fun videoAspectRatioLabelFor(value: VideoAspectRatioMode): String {
    return when (value) {
        VideoAspectRatioMode.Fit16By9 -> VIDEO_ASPECT_FIT_16_9
        VideoAspectRatioMode.Fit9By16 -> VIDEO_ASPECT_FIT_9_16
        VideoAspectRatioMode.Fit1By1 -> VIDEO_ASPECT_FIT_1_1
        VideoAspectRatioMode.Crop16By9 -> VIDEO_ASPECT_CROP_16_9
        VideoAspectRatioMode.Crop9By16 -> VIDEO_ASPECT_CROP_9_16
        VideoAspectRatioMode.Crop1By1 -> VIDEO_ASPECT_CROP_1_1
        VideoAspectRatioMode.Keep -> VIDEO_ASPECT_KEEP
    }
}

private fun audioVolumeLabelFor(value: AudioVolumeMode): String {
    return when (value) {
        AudioVolumeMode.Mute -> AUDIO_VOLUME_MUTE
        AudioVolumeMode.Half -> AUDIO_VOLUME_50
        AudioVolumeMode.OneAndHalf -> AUDIO_VOLUME_150
        AudioVolumeMode.Double -> AUDIO_VOLUME_200
        AudioVolumeMode.Original -> AUDIO_VOLUME_100
    }
}

private fun audioEchoLabelFor(value: AudioEchoMode): String {
    return when (value) {
        AudioEchoMode.Light -> AUDIO_ECHO_LIGHT
        AudioEchoMode.Room -> AUDIO_ECHO_ROOM
        AudioEchoMode.Off -> AUDIO_ECHO_OFF
    }
}

private fun audioNoiseReductionLabelFor(value: AudioNoiseReductionMode): String {
    return when (value) {
        AudioNoiseReductionMode.Light -> AUDIO_DENOISE_LIGHT
        AudioNoiseReductionMode.Standard -> AUDIO_DENOISE_STANDARD
        AudioNoiseReductionMode.Off -> AUDIO_DENOISE_OFF
    }
}

private fun imageQualityLabelFor(
    options: ImageExportOptions,
    targetFormat: TargetFormat
): String {
    if (supportsWebpLosslessQuality(targetFormat) && options.webpLossless) {
        return IMAGE_QUALITY_LOSSLESS
    }
    return when {
        options.quality >= 100 -> IMAGE_QUALITY_ORIGINAL
        options.quality >= 95 -> IMAGE_QUALITY_HIGH
        options.quality <= 60 -> IMAGE_QUALITY_SMALL
        else -> IMAGE_QUALITY_BALANCED
    }
}

private fun imageOptionsForQuality(
    value: String,
    targetFormat: TargetFormat
): ImageExportOptions {
    return ImageExportOptions(
        quality = imageQualityToPercent(value),
        webpLossless = supportsWebpLosslessQuality(targetFormat) &&
            value == IMAGE_QUALITY_LOSSLESS
    )
}

private fun superResolutionLabelFor(mode: ImageSuperResolutionMode): String {
    return when (mode) {
        ImageSuperResolutionMode.Off -> IMAGE_SUPER_RESOLUTION_OFF
        ImageSuperResolutionMode.X2 -> IMAGE_SUPER_RESOLUTION_2X
        ImageSuperResolutionMode.X3 -> IMAGE_SUPER_RESOLUTION_3X
        ImageSuperResolutionMode.X4 -> IMAGE_SUPER_RESOLUTION_4X
        ImageSuperResolutionMode.RealEsrganAnime4x -> IMAGE_SUPER_RESOLUTION_AI_ANIME
        ImageSuperResolutionMode.RealEsrgan4x -> IMAGE_SUPER_RESOLUTION_AI
    }
}

private fun superResolutionModeFor(value: String): ImageSuperResolutionMode {
    return when (value) {
        IMAGE_SUPER_RESOLUTION_2X -> ImageSuperResolutionMode.X2
        IMAGE_SUPER_RESOLUTION_3X -> ImageSuperResolutionMode.X3
        IMAGE_SUPER_RESOLUTION_4X -> ImageSuperResolutionMode.X4
        IMAGE_SUPER_RESOLUTION_AI_ANIME -> ImageSuperResolutionMode.RealEsrganAnime4x
        IMAGE_SUPER_RESOLUTION_AI -> ImageSuperResolutionMode.RealEsrgan4x
        else -> ImageSuperResolutionMode.Off
    }
}

private fun pdfPageModeLabelFor(value: PdfImagePageMode): String {
    return when (value) {
        PdfImagePageMode.OriginalRatio -> PDF_PAGE_MODE_ORIGINAL_RATIO
        PdfImagePageMode.A4Fit -> PDF_PAGE_MODE_A4_FIT
    }
}

private fun pdfRenderQualityLabelFor(value: PdfRenderQuality): String {
    return when (value) {
        PdfRenderQuality.LowResolution -> PDF_RENDER_QUALITY_LOW
        PdfRenderQuality.HighDetail -> PDF_RENDER_QUALITY_HIGH
        PdfRenderQuality.Balanced -> PDF_RENDER_QUALITY_BALANCED
    }
}

private fun pdfCompressionPresetLabelFor(value: PdfCompressionPreset): String {
    return when (value) {
        PdfCompressionPreset.HighQuality -> PDF_COMPRESSION_PRESET_HIGH
        PdfCompressionPreset.SmallFile -> PDF_COMPRESSION_PRESET_SMALL
        PdfCompressionPreset.Balanced -> PDF_COMPRESSION_PRESET_BALANCED
    }
}

private fun gifFrameModeLabelFor(
    value: GifFrameExportMode,
    targetFormat: TargetFormat
): String {
    return if (targetFormat.extension.equals("pdf", ignoreCase = true)) {
        when (value) {
            GifFrameExportMode.FramesAsSinglePdf -> GIF_FRAMES_SINGLE_PDF
            GifFrameExportMode.FramesAsPdfFiles -> GIF_FRAMES_PDF_FILES
            else -> GIF_FRAME_FIRST
        }
    } else {
        when (value) {
            GifFrameExportMode.FramesAsImages -> GIF_FRAME_IMAGES
            else -> GIF_FRAME_FIRST
        }
    }
}

private fun gifFrameModeForLabel(
    value: String,
    targetFormat: TargetFormat
): GifFrameExportMode {
    return if (targetFormat.extension.equals("pdf", ignoreCase = true)) {
        when (value) {
            GIF_FRAMES_SINGLE_PDF -> GifFrameExportMode.FramesAsSinglePdf
            GIF_FRAMES_PDF_FILES -> GifFrameExportMode.FramesAsPdfFiles
            else -> GifFrameExportMode.FirstFrame
        }
    } else {
        when (value) {
            GIF_FRAME_IMAGES -> GifFrameExportMode.FramesAsImages
            else -> GifFrameExportMode.FirstFrame
        }
    }
}

private fun QueuedFile.isGifQueuedImage(): Boolean {
    val normalizedMimeType = mimeType.orEmpty().lowercase(Locale.US)
    return sourceCategory == FileCategory.Image &&
        (
            normalizedMimeType == "image/gif" ||
                displayName.lowercase(Locale.US).endsWith(".gif")
            )
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

private fun videoCompressionModeFor(value: String): VideoCompressionMode {
    return when (value) {
        VIDEO_COMPRESSION_VISUAL_LOSSLESS -> VideoCompressionMode.VisualLossless
        VIDEO_COMPRESSION_BALANCED -> VideoCompressionMode.BalancedShrink
        VIDEO_COMPRESSION_SMALL -> VideoCompressionMode.SmallFile
        else -> VideoCompressionMode.Standard
    }
}

private fun videoInterpolationModeFor(value: String): VideoFrameInterpolationMode {
    return when (value) {
        VIDEO_INTERPOLATION_OPTICAL_FLOW_2X -> VideoFrameInterpolationMode.OpticalFlow2x
        VIDEO_INTERPOLATION_RIFE_2X -> VideoFrameInterpolationMode.Rife2x
        else -> VideoFrameInterpolationMode.Off
    }
}

private fun videoCompressionShortSideFor(mode: VideoCompressionMode): Int? {
    return when (mode) {
        VideoCompressionMode.BalancedShrink -> 1080
        VideoCompressionMode.SmallFile -> 720
        VideoCompressionMode.Standard,
        VideoCompressionMode.VisualLossless -> null
    }
}

private fun videoCompressionFrameRateCapFor(mode: VideoCompressionMode): Int? {
    return when (mode) {
        VideoCompressionMode.SmallFile -> 30
        VideoCompressionMode.Standard,
        VideoCompressionMode.VisualLossless,
        VideoCompressionMode.BalancedShrink -> null
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
        AUDIO_SAMPLE_RATE_24000 -> 24_000
        AUDIO_SAMPLE_RATE_16000 -> 16_000
        AUDIO_SAMPLE_RATE_8000 -> 8_000
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

private fun pdfCompressionPresetToOption(value: String): PdfCompressionPreset {
    return when (value) {
        PDF_COMPRESSION_PRESET_HIGH -> PdfCompressionPreset.HighQuality
        PDF_COMPRESSION_PRESET_SMALL -> PdfCompressionPreset.SmallFile
        else -> PdfCompressionPreset.Balanced
    }
}

private val AUDIO_LOSSLESS_OUTPUT_EXTENSIONS = setOf("wav", "flac")



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

internal fun shareOutput(
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

internal fun openOutputLocation(
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

internal fun TaskProgress.outputUriList(): List<Uri> {
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

private fun metadataPrimaryInfoLine(
    inspection: MetadataInspection,
    texts: UiText
): String {
    return buildList {
        add(inspection.formatLabel)
        if (inspection.width != null && inspection.height != null) {
            add("${inspection.width}x${inspection.height}")
        }
        inspection.durationMs?.let { add(formatDurationMs(it, texts)) }
        add(formatBytes(inspection.sizeBytes, texts))
    }.joinToString(" · ")
}

private fun metadataCompactRows(
    inspection: MetadataInspection,
    texts: UiText
): List<Pair<String, String>> {
    return buildList {
        if (inspection.kind == MetadataTargetKind.Video) {
            inspection.durationMs?.let {
                add(texts.metadataLabel("Duration") to formatDurationMs(it, texts))
            }
            if (inspection.width != null && inspection.height != null) {
                add(texts.metadataLabel("Dimensions") to "${inspection.width}x${inspection.height}")
            }
            inspection.bitrateBitsPerSecond?.let {
                add(texts.metadataLabel("Overall bitrate") to formatBitrate(it, texts.locale))
            }
            inspection.frameRate?.let {
                add(texts.metadataLabel("Frame rate") to formatFrameRate(it, texts.locale))
            }
            return@buildList
        }

        add(texts.metadataLabel("GPS") to texts.yesNo(inspection.hasGps))
        inspection.capturedAt?.let {
            add(texts.metadataLabel("Captured") to it)
        }
        inspection.camera?.let {
            add(texts.metadataLabel("Camera") to it)
        }
        add(
            texts.metadataLabel("Removable") to
                "${inspection.removableSegmentCount} · ${formatBytes(inspection.removableBytes, texts)}"
        )
    }
}

private fun metadataDetailRows(
    inspection: MetadataInspection,
    texts: UiText
): List<Pair<String, String>> {
    return buildList {
        add(texts.metadataLabel("Format") to inspection.formatLabel)
        add(texts.metadataLabel("Size") to formatBytes(inspection.sizeBytes, texts))
        if (inspection.width != null && inspection.height != null) {
            add(texts.metadataLabel("Dimensions") to "${inspection.width}x${inspection.height}")
        }
        inspection.durationMs?.let {
            add(texts.metadataLabel("Duration") to formatDurationMs(it, texts))
        }
        inspection.frameRate?.let {
            add(texts.metadataLabel("Frame rate") to formatFrameRate(it, texts.locale))
        }
        inspection.bitrateBitsPerSecond?.let {
            add(texts.metadataLabel("Overall bitrate") to formatBitrate(it, texts.locale))
        }
        add(texts.metadataLabel("GPS") to texts.yesNo(inspection.hasGps))
        inspection.capturedAt?.let { add(texts.metadataLabel("Captured") to it) }
        inspection.camera?.let { add(texts.metadataLabel("Camera") to it) }
        inspection.software?.let { add(texts.metadataLabel("Software") to it) }
        add(texts.metadataLabel("Orientation") to texts.metadataOrientationLabel(inspection.orientation))
        inspection.description?.let { add(texts.metadataLabel("Description") to it) }
        inspection.artist?.let { add(texts.metadataLabel("Artist") to it) }
        inspection.copyright?.let { add(texts.metadataLabel("Copyright") to it) }
        if (inspection.kind == MetadataTargetKind.Image) {
            add(texts.metadataLabel("EXIF") to texts.yesNo(inspection.hasExif))
            add(texts.metadataLabel("XMP") to texts.yesNo(inspection.hasXmp))
            add(texts.metadataLabel("IPTC") to texts.yesNo(inspection.hasIptc))
            add(texts.metadataLabel("Comment") to texts.yesNo(inspection.hasComment))
            add(
                texts.metadataLabel("Removable") to
                    "${inspection.removableSegmentCount} · ${formatBytes(inspection.removableBytes, texts)}"
            )
            add(texts.metadataLabel("Backups") to texts.metadataBackupCountLabel(inspection.backups.size))
            inspection.coreHash?.take(16)?.let {
                add(texts.metadataLabel("Core hash") to "$it...")
            }
            inspection.unsupportedMessage?.let {
                add(texts.metadataLabel("Support") to texts.metadataMessage(MetadataStatusMessage(it)))
            }
        }
    }
}

internal fun formatBytes(sizeBytes: Long?, texts: UiText): String {
    if (sizeBytes == null) return texts.unknownSize
    if (sizeBytes < 1024) return String.format(texts.locale, "%d B", sizeBytes)

    val units = listOf("KB", "MB", "GB", "TB")
    var value = sizeBytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(texts.locale, "%.1f %s", value, units[unitIndex])
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
    val inputSizeBytes = inputInfo?.sizeBytes ?: file.sizeBytes
    val sizeChange = formatSizeChangeLabel(inputSizeBytes, outputInfo.sizeBytes, texts)
    val parts = fileInfoParts(
        info = outputInfo,
        texts = texts,
        formatOverride = formatChange,
        sizeOverride = sizeChange
    ).toMutableList()
    if (shouldShowVideoOutputLargerHint(file, inputSizeBytes, outputInfo.sizeBytes)) {
        parts.add(texts.outputLargerHint())
    }
    return parts.joinToString(" · ")
}

private fun formatMergedResultInfoLine(
    outputInfo: FileBasicInfo,
    texts: UiText
): String {
    val parts = fileInfoParts(
        info = outputInfo,
        texts = texts
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "PDF"
}

private fun shouldShowVideoOutputLargerHint(
    file: QueuedFile,
    inputSizeBytes: Long?,
    outputSizeBytes: Long?
): Boolean {
    val inputSize = inputSizeBytes?.takeIf { it > 0L } ?: return false
    val outputSize = outputSizeBytes?.takeIf { it > 0L } ?: return false
    return file.category == FileCategory.Video &&
        !file.targetFormat.equals("GIF", ignoreCase = true) &&
        outputSize > inputSize
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
        info.frameRate?.let { add(texts.frameRateLabel(formatFrameRate(it, texts.locale))) }
        info.bitrateBitsPerSecond?.let { add(texts.bitrateLabel(formatBitrate(it, texts.locale))) }
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
    return texts.text(R.string.format_detail_parenthesized, outputSize, String.format(texts.locale, "%+.0f%%", percent))
}

private fun formatDurationMs(durationMs: Long, texts: UiText): String {
    if (durationMs < 60_000L) {
        return texts.text(R.string.format_seconds, durationMs.toDouble() / 1000.0)
    }
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3600L
    return if (hours > 0L) {
        String.format(texts.locale, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(texts.locale, "%d:%02d", minutes, seconds)
    }
}

private fun formatFrameRate(frameRate: Float, locale: Locale): String {
    return if (frameRate >= 10f) {
        String.format(locale, "%.0f fps", frameRate)
    } else {
        String.format(locale, "%.1f fps", frameRate)
    }
}

private fun formatBitrate(bitsPerSecond: Long, locale: Locale): String {
    return if (bitsPerSecond >= 1_000_000L) {
        String.format(locale, "%.1f Mbps", bitsPerSecond.toDouble() / 1_000_000.0)
    } else {
        String.format(locale, "%.0f kbps", bitsPerSecond.toDouble() / 1_000.0)
    }
}
