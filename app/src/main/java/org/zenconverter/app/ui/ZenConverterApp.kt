package org.zenconverter.app.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image as BrandImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.zenconverter.app.conversion.AudioExportOptions
import org.zenconverter.app.conversion.ImageExportOptions
import org.zenconverter.app.conversion.VideoExportOptions
import org.zenconverter.app.R
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
            TargetFormat("MP4", "mp4", "Auto engine")
        )
    ),
    Audio(
        mimeTypes = listOf("audio/*", "video/*"),
        formats = listOf(
            TargetFormat("M4A (AAC)", "m4a", "Auto engine"),
            TargetFormat("MP3", "mp3", "Compatibility"),
            TargetFormat("WAV", "wav", "Compatibility"),
            TargetFormat("FLAC", "flac", "Compatibility"),
            TargetFormat("WMA", "wma", "Compatibility")
        )
    ),
    Image(
        mimeTypes = listOf("image/jpeg", "image/png", "image/webp"),
        formats = listOf(
            TargetFormat("JPG", "jpg", "Batch"),
            TargetFormat("PNG", "png", "Supports transparency"),
            TargetFormat("WEBP", "webp", "Supports transparency")
        )
    )
}

data class QueuedFile(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
    val mimeType: String?,
    val category: FileCategory,
    val targetFormat: String
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
    val message: String
)

enum class TaskProgressStatus {
    Queued,
    Running,
    Completed,
    Cancelled,
    Failed
}

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

private const val IMAGE_QUALITY_ORIGINAL = "Original"
private const val IMAGE_QUALITY_HIGH = "High"
private const val IMAGE_QUALITY_BALANCED = "Balanced"
private const val IMAGE_QUALITY_SMALL = "Small"
private val IMAGE_QUALITY_OPTIONS = listOf(
    IMAGE_QUALITY_ORIGINAL,
    IMAGE_QUALITY_HIGH,
    IMAGE_QUALITY_BALANCED,
    IMAGE_QUALITY_SMALL
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
    onStartConversion: (VideoExportOptions, AudioExportOptions, ImageExportOptions) -> Unit,
    onCancelConversion: () -> Unit
) {
    var accent by remember { mutableStateOf(AccentColorOption.Charcoal) }
    var languageOption by remember { mutableStateOf(LanguageOption.System) }
    val texts = uiTextFor(resolveLanguage(languageOption))

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
    onStartConversion: (VideoExportOptions, AudioExportOptions, ImageExportOptions) -> Unit,
    onCancelConversion: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var activeCategory by remember { mutableStateOf(FileCategory.Video) }
    var videoTarget by remember { mutableStateOf(FileCategory.Video.formats.first()) }
    var audioTarget by remember { mutableStateOf(FileCategory.Audio.formats.first()) }
    var imageTarget by remember { mutableStateOf(FileCategory.Image.formats.first()) }
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
    var imageQuality by remember { mutableStateOf(IMAGE_QUALITY_BALANCED) }

    fun targetFor(category: FileCategory): TargetFormat = when (category) {
        FileCategory.Video -> videoTarget
        FileCategory.Audio -> audioTarget
        FileCategory.Image -> imageTarget
    }

    fun setTarget(category: FileCategory, targetFormat: TargetFormat) {
        when (category) {
            FileCategory.Video -> videoTarget = targetFormat
            FileCategory.Audio -> audioTarget = targetFormat
            FileCategory.Image -> imageTarget = targetFormat
        }
    }

    fun currentVideoOptions(): VideoExportOptions {
        return VideoExportOptions(
            maxShortSidePixels = videoResolutionToShortSide(videoResolution),
            videoBitrate = videoBitrateToBits(videoBitrate),
            videoMimeType = videoCodecToMimeType(videoCodec),
            maxFrameRate = videoFrameRateToCap(videoFrameRate)
        )
    }

    fun currentAudioOptions(): AudioExportOptions {
        return AudioExportOptions(
            audioBitrate = audioBitrateToBits(audioBitrate),
            sampleRateHz = audioSampleRateToHz(audioSampleRate),
            channelCount = audioChannelsToCount(audioChannels)
        )
    }

    fun currentImageOptions(): ImageExportOptions {
        return ImageExportOptions(
            quality = imageQualityToPercent(imageQuality)
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
                Header(
                    texts = texts,
                    showSettings = showSettings,
                    onToggleSettings = {
                        openMenuId = null
                        showSettings = !showSettings
                    }
                )
            }

            item(key = "settings") {
                AnimatedVisibility(
                    visible = showSettings,
                    enter = fadeIn() + expandVertically(
                        animationSpec = spring(stiffness = 420f)
                    ),
                    exit = fadeOut() + shrinkVertically(
                        animationSpec = spring(stiffness = 520f)
                    )
                ) {
                    SettingsPanel(
                        texts = texts,
                        selectedAccent = accent,
                        selectedLanguage = languageOption,
                        onAccentSelected = onAccentSelected,
                        onLanguageSelected = onLanguageSelected
                    )
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
                    audioBitrate = audioBitrate,
                    audioSampleRate = audioSampleRate,
                    audioChannels = audioChannels,
                    audioTarget = audioTarget,
                    imageTarget = imageTarget,
                    imageQuality = imageQuality,
                    openMenuId = openMenuId,
                    onOpenMenuChange = { openMenuId = it },
                    onVideoResolutionChange = { videoResolution = it },
                    onVideoBitrateChange = { videoBitrate = it },
                    onVideoCodecChange = { videoCodec = it },
                    onVideoFrameRateChange = { videoFrameRate = it },
                    onAudioBitrateChange = { audioBitrate = it },
                    onAudioSampleRateChange = { audioSampleRate = it },
                    onAudioChannelsChange = { audioChannels = it },
                    onImageQualityChange = { imageQuality = it }
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
                            currentImageOptions()
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
                    StatusLine(text = texts.taskMessage(message))
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
}

@Composable
private fun Header(
    texts: UiText,
    showSettings: Boolean,
    onToggleSettings: () -> Unit
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
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "ZenConverter",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = texts.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        IconButton(
            onClick = onToggleSettings,
            modifier = Modifier
                .size(44.dp)
                .border(1.dp, Color(0xFFE7E7E7), CircleShape)
        ) {
            AppIcon(
                icon = if (showSettings) Icons.Rounded.Close else Icons.Rounded.Settings,
                contentDescription = if (showSettings) texts.closeSettings else texts.openSettings,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
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
                        maxLines = 1,
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
    audioBitrate: String,
    audioSampleRate: String,
    audioChannels: String,
    audioTarget: TargetFormat,
    imageTarget: TargetFormat,
    imageQuality: String,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onVideoResolutionChange: (String) -> Unit,
    onVideoBitrateChange: (String) -> Unit,
    onVideoCodecChange: (String) -> Unit,
    onVideoFrameRateChange: (String) -> Unit,
    onAudioBitrateChange: (String) -> Unit,
    onAudioSampleRateChange: (String) -> Unit,
    onAudioChannelsChange: (String) -> Unit,
    onImageQualityChange: (String) -> Unit
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

        when (category) {
            FileCategory.Video -> VideoOptions(
                texts = texts,
                resolution = videoResolution,
                bitrate = videoBitrate,
                codec = videoCodec,
                codecOptions = videoCodecOptions,
                frameRate = videoFrameRate,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onResolutionChange = onVideoResolutionChange,
                onBitrateChange = onVideoBitrateChange,
                onCodecChange = onVideoCodecChange,
                onFrameRateChange = onVideoFrameRateChange
            )
            FileCategory.Audio -> AudioOptions(
                texts = texts,
                bitrate = audioBitrate,
                sampleRate = audioSampleRate,
                channels = audioChannels,
                targetFormat = audioTarget,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onBitrateChange = onAudioBitrateChange,
                onSampleRateChange = onAudioSampleRateChange,
                onChannelsChange = onAudioChannelsChange
            )
            FileCategory.Image -> ImageOptions(
                texts = texts,
                targetFormat = imageTarget,
                quality = imageQuality,
                openMenuId = openMenuId,
                onOpenMenuChange = onOpenMenuChange,
                onQualityChange = onImageQualityChange
            )
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
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onResolutionChange: (String) -> Unit,
    onBitrateChange: (String) -> Unit,
    onCodecChange: (String) -> Unit,
    onFrameRateChange: (String) -> Unit
) {
    OptionGrid {
        OptionDropdown(
            "video-size",
            texts.resolution,
            resolution,
            VIDEO_RESOLUTION_OPTIONS,
            texts,
            openMenuId,
            onOpenMenuChange,
            onResolutionChange
        )
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
    }
}

@Composable
private fun AudioOptions(
    texts: UiText,
    bitrate: String,
    sampleRate: String,
    channels: String,
    targetFormat: TargetFormat,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onBitrateChange: (String) -> Unit,
    onSampleRateChange: (String) -> Unit,
    onChannelsChange: (String) -> Unit
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
    }
}

@Composable
private fun ImageOptions(
    texts: UiText,
    targetFormat: TargetFormat,
    quality: String,
    openMenuId: String?,
    onOpenMenuChange: (String?) -> Unit,
    onQualityChange: (String) -> Unit
) {
    if (targetFormat.extension.equals("png", ignoreCase = true)) {
        Text(
            text = texts.optionValue("Lossless output"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    OptionGrid {
        OptionDropdown("image-quality", texts.quality, quality, IMAGE_QUALITY_OPTIONS, texts, openMenuId, onOpenMenuChange, onQualityChange)
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
                        maxLines = 1,
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
            Text(texts.cancel)
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
    texts: UiText,
    file: QueuedFile,
    progress: TaskProgress?,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFEAEAEA), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        file.mimeType ?: texts.unknownType,
                        formatBytes(file.sizeBytes, texts)
                    ).joinToString(" / "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onRemove,
                enabled = canRemove
            ) {
                AppIcon(
                    icon = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(texts.remove)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallTag(texts.categoryLabel(file.category))
            SmallTag(texts.toFormat(file.targetFormat))
            SmallTag(texts.progressLabel(progress))
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
            text = "$label: ${selected.label}",
            expanded = expanded
        )
        InlineDropdownPanel(
            expanded = expanded
        ) {
            options.forEach { option ->
                DropdownOption(
                    text = "${option.label} / ${texts.engineHint(option.modeHint)}",
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
private fun StatusLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    )
}

@Composable
private fun QuietPanel(
    borderColor: Color = Color(0xFFE7E7E7),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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

private fun audioSupportsBitrateOption(targetFormat: TargetFormat): Boolean {
    return targetFormat.extension.lowercase(Locale.US) !in AUDIO_LOSSLESS_OUTPUT_EXTENSIONS
}

private fun imageQualityToPercent(value: String): Int {
    return when (value) {
        IMAGE_QUALITY_ORIGINAL -> 100
        IMAGE_QUALITY_HIGH -> 95
        IMAGE_QUALITY_BALANCED -> 85
        IMAGE_QUALITY_SMALL -> 60
        else -> 85
    }
}

private val AUDIO_LOSSLESS_OUTPUT_EXTENSIONS = setOf("wav", "flac")

private data class UiText(
    val tagline: String,
    val openSettings: String,
    val closeSettings: String,
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
    val videoEncoderUnsupported: String,
    val folderPermissionSaved: String,
    val folderSelectedForSession: String,
    val start: String,
    val cancel: String,
    val queue: String,
    val selectedSuffix: String,
    val emptyQueue: String,
    val unknownType: String,
    val unknownSize: String,
    val remove: String,
    val waiting: String,
    val processing: String,
    val flowComplete: String,
    val flowCompleteNoFiles: String,
    val cancelled: String,
    val failed: String,
    val quality: String,
    val resolution: String,
    val bitrate: String,
    val codec: String,
    val frameRate: String,
    val sampleRate: String,
    val channels: String,
    val outputMode: String,
    val uiPresetSuffix: String,
    val toPrefix: String
) {
    fun selectedCount(count: Int): String = "$count $selectedSuffix"

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
            "Default output needs storage permission on this Android version" -> storagePermissionRequired
            "Selected video encoder is not supported on this device" -> videoEncoderUnsupported
            "Native engine timed out before writing output" -> when (this) {
                englishText -> "Native engine timed out before writing output; this file may need compatibility mode"
                simplifiedChineseText -> "原生引擎写出前超时，这个文件可能需要兼容模式"
                else -> "原生引擎寫出前逾時，這個檔案可能需要相容模式"
            }
            "Native engine cannot decode this input on this device" -> when (this) {
                englishText -> "Native engine cannot decode this input on this device"
                simplifiedChineseText -> "当前设备的原生引擎无法解码这个输入"
                else -> "目前裝置的原生引擎無法解碼這個輸入"
            }
            "Native engine failed while decoding this input" -> when (this) {
                englishText -> "Native engine failed while decoding this input"
                simplifiedChineseText -> "原生引擎解码这个输入时失败"
                else -> "原生引擎解碼這個輸入時失敗"
            }
            "Native engine cannot encode the selected output" -> when (this) {
                englishText -> "Native engine cannot encode the selected output"
                simplifiedChineseText -> "原生引擎无法编码所选输出"
                else -> "原生引擎無法編碼所選輸出"
            }
            "Native engine could not write this MP4/M4A output" -> when (this) {
                englishText -> "Native engine could not write this MP4/M4A output"
                simplifiedChineseText -> "原生引擎无法写出这个 MP4/M4A 输出"
                else -> "原生引擎無法寫出這個 MP4/M4A 輸出"
            }
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
            "Could not start native export" -> when (this) {
                englishText -> "Could not start native export"
                simplifiedChineseText -> "无法启动原生导出"
                else -> "無法啟動原生匯出"
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
            "Compatibility engine could not remux this file to MP4" -> when (this) {
                englishText -> "Compatibility engine could not remux this file to MP4"
                simplifiedChineseText -> "兼容引擎无法把这个文件重封装为 MP4"
                else -> "相容引擎無法把這個檔案重封裝為 MP4"
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
            "Compatibility engine needs an MP3-capable FFmpeg package" -> when (this) {
                englishText -> "Compatibility engine needs an MP3-capable FFmpeg package"
                simplifiedChineseText -> "当前兼容包不包含 MP3 编码器"
                else -> "目前相容包不包含 MP3 編碼器"
            }
            "Compatibility engine could not write this audio container" -> when (this) {
                englishText -> "Compatibility engine could not write this audio container"
                simplifiedChineseText -> "兼容引擎无法写出这个音频容器"
                else -> "相容引擎無法寫出這個音訊容器"
            }
            "Compatibility engine needs an AAC audio stream for M4A copy" -> when (this) {
                englishText -> "Compatibility engine needs an AAC audio stream for M4A copy"
                simplifiedChineseText -> "这个兼容包暂时只能拷贝已有 AAC 音轨到 M4A"
                else -> "這個相容包暫時只能拷貝既有 AAC 音軌到 M4A"
            }
            "Compatibility engine cannot copy this codec into MP4 yet" -> when (this) {
                englishText -> "Compatibility engine cannot copy this codec into MP4 yet"
                simplifiedChineseText -> "这个编码暂时不能直接封装进 MP4"
                else -> "這個編碼暫時不能直接封裝進 MP4"
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
            "Hardware" -> when (this) {
                englishText -> "Hardware accelerated"
                simplifiedChineseText -> "硬件加速"
                else -> "硬體加速"
            }
            "Compatibility" -> when (this) {
                englishText -> "Multi-format"
                simplifiedChineseText -> "多格式兼容"
                else -> "多格式相容"
            }
            "Auto engine" -> when (this) {
                englishText -> "Native or compatibility"
                simplifiedChineseText -> "原生或兼容引擎"
                else -> "原生或相容引擎"
            }
            "PDF" -> "PDF"
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
            "Auto bitrate" -> when (this) {
                englishText -> "Auto (recommended)"
                simplifiedChineseText -> "自动（推荐）"
                else -> "自動（推薦）"
            }
            "Auto audio bitrate" -> when (this) {
                englishText -> "Auto (keep if possible)"
                simplifiedChineseText -> "自动（尽量保留）"
                else -> "自動（盡量保留）"
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
    openSettings = "Open settings",
    closeSettings = "Close settings",
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
    defaultOutputNote = "Videos save to Movies/ZenConverter; audio to Music/ZenConverter; images to Pictures/ZenConverter",
    customOutputLocation = "Custom folder",
    storagePermissionRequired = "Allow storage permission or choose a folder",
    videoEncoderUnsupported = "This device does not support the selected video encoder",
    folderPermissionSaved = "Folder permission saved",
    folderSelectedForSession = "Folder selected for this session",
    start = "Start",
    cancel = "Cancel",
    queue = "Conversion tasks",
    selectedSuffix = "selected",
    emptyQueue = "No tasks yet",
    unknownType = "Unknown type",
    unknownSize = "Unknown size",
    remove = "Remove",
    waiting = "Waiting",
    processing = "Processing",
    flowComplete = "Flow complete",
    flowCompleteNoFiles = "Flow checked, no files created",
    cancelled = "Cancelled",
    failed = "Failed",
    quality = "Quality",
    resolution = "Resolution",
    bitrate = "Bitrate",
    codec = "Codec",
    frameRate = "Frame rate",
    sampleRate = "Sample rate",
    channels = "Channels",
    outputMode = "Output",
    uiPresetSuffix = "",
    toPrefix = "to"
)

private val simplifiedChineseText = UiText(
    tagline = "本机转换，文件不上云",
    openSettings = "打开设置",
    closeSettings = "关闭设置",
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
    defaultOutputNote = "视频保存到 Movies/ZenConverter；音频到 Music/ZenConverter；图片到 Pictures/ZenConverter",
    customOutputLocation = "自定义文件夹",
    storagePermissionRequired = "请允许存储权限，或改选自定义文件夹",
    videoEncoderUnsupported = "当前设备不支持所选视频编码",
    folderPermissionSaved = "文件夹权限已保存",
    folderSelectedForSession = "本次已选择文件夹",
    start = "开始",
    cancel = "取消",
    queue = "转换任务",
    selectedSuffix = "个已选",
    emptyQueue = "暂无任务，先在上方选择文件",
    unknownType = "未知类型",
    unknownSize = "未知大小",
    remove = "移除",
    waiting = "等待中",
    processing = "处理中",
    flowComplete = "流程完成",
    flowCompleteNoFiles = "流程已跑通，暂未生成文件",
    cancelled = "已取消",
    failed = "失败",
    quality = "质量",
    resolution = "分辨率",
    bitrate = "码率",
    codec = "编码",
    frameRate = "帧率",
    sampleRate = "采样率",
    channels = "声道",
    outputMode = "输出方式",
    uiPresetSuffix = "",
    toPrefix = "转为"
)

private val traditionalChineseText = UiText(
    tagline = "本機轉換，檔案不上雲",
    openSettings = "開啟設定",
    closeSettings = "關閉設定",
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
    defaultOutputNote = "影片儲存到 Movies/ZenConverter；音訊到 Music/ZenConverter；圖片到 Pictures/ZenConverter",
    customOutputLocation = "自訂資料夾",
    storagePermissionRequired = "請允許儲存權限，或改選自訂資料夾",
    videoEncoderUnsupported = "目前裝置不支援所選影片編碼",
    folderPermissionSaved = "資料夾權限已儲存",
    folderSelectedForSession = "本次已選擇資料夾",
    start = "開始",
    cancel = "取消",
    queue = "轉換任務",
    selectedSuffix = "個已選",
    emptyQueue = "暫無任務，先在上方選擇檔案",
    unknownType = "未知類型",
    unknownSize = "未知大小",
    remove = "移除",
    waiting = "等待中",
    processing = "處理中",
    flowComplete = "流程完成",
    flowCompleteNoFiles = "流程已跑通，暫未產生檔案",
    cancelled = "已取消",
    failed = "失敗",
    quality = "品質",
    resolution = "解析度",
    bitrate = "位元率",
    codec = "編碼",
    frameRate = "幀率",
    sampleRate = "取樣率",
    channels = "聲道",
    outputMode = "輸出方式",
    uiPresetSuffix = "",
    toPrefix = "轉為"
)
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
