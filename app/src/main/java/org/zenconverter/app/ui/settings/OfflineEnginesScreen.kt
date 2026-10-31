package org.zenconverter.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.zenconverter.app.R
import org.zenconverter.app.office.OfficeFontManager
import org.zenconverter.app.office.OfficeFontSpec
import org.zenconverter.app.office.OfficeFontUiState
import org.zenconverter.app.model.EsrganModelManager
import org.zenconverter.app.model.EsrganModelSpec
import org.zenconverter.app.model.EsrganModelUiState
import org.zenconverter.app.model.RifeModelManager
import org.zenconverter.app.model.RifeModelSpec
import org.zenconverter.app.model.RifeModelUiState
import org.zenconverter.app.ui.AppIcon
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.formatBytes
import org.zenconverter.app.ui.openExternalLink

@Composable
internal fun OfflineEnginesScreen(
    texts: UiText,
    esrganModelStates: Map<String, EsrganModelUiState>,
    rifeModelStates: Map<String, RifeModelUiState>,
    officeFontStates: Map<String, OfficeFontUiState>,
    onDownloadEsrganModel: (EsrganModelSpec) -> Unit,
    onCancelEsrganModelDownload: (EsrganModelSpec) -> Unit,
    onDownloadRifeModel: (RifeModelSpec) -> Unit,
    onCancelRifeModelDownload: (RifeModelSpec) -> Unit,
    onDownloadOfficeFont: (OfficeFontSpec) -> Unit,
    onCancelOfficeFontDownload: (OfficeFontSpec) -> Unit,
    onDeleteOfficeFont: (OfficeFontSpec) -> Unit,
    onBack: () -> Unit,

    modifier: Modifier = Modifier
) {
    SettingsPage(texts.settingsOfflineEngines, texts.settingsBack, onBack, modifier = modifier) {
        // Group 1: Real-ESRGAN Super-Resolution
        item {
            SettingsGroupHeader(title = texts.superResolution)
        }
        item {
            InsetGroupCard {
                EsrganModelManager.ALL_MODELS.forEachIndexed { index, spec ->
                    EsrganModelItem(
                        texts = texts,
                        spec = spec,
                        state = esrganModelStates[spec.id] ?: EsrganModelUiState.NotDownloaded,
                        onDownload = { onDownloadEsrganModel(spec) },
                        onCancel = { onCancelEsrganModelDownload(spec) },
                        showDivider = index < EsrganModelManager.ALL_MODELS.lastIndex,
                    )
                }
            }
        }

        // Group 2: RIFE Video Frame Interpolation
        item {
            SettingsGroupHeader(title = texts.videoFrameInterpolation)
        }
        item {
            InsetGroupCard {
                RifeModelManager.ALL_MODELS.forEachIndexed { index, spec ->
                    RifeModelItem(
                        texts = texts,
                        spec = spec,
                        state = rifeModelStates[spec.id] ?: RifeModelUiState.NotDownloaded,
                        onDownload = { onDownloadRifeModel(spec) },
                        onCancel = { onCancelRifeModelDownload(spec) },
                        showDivider = index < RifeModelManager.ALL_MODELS.lastIndex,
                    )
                }
            }
        }

        // Group 3: Office CJK Fonts
        item {
            SettingsGroupHeader(title = texts.officeFontTitle)
        }
        item {
            // System fonts status badge card
            SystemFontBanner(texts = texts)
            Spacer(modifier = Modifier.height(10.dp))
            InsetGroupCard {
                OfficeFontManager.ALL_FONTS.forEachIndexed { index, spec ->
                    OfficeFontItem(
                        texts = texts,
                        spec = spec,
                        state = officeFontStates[spec.id] ?: OfficeFontUiState.NotDownloaded,
                        onDownload = { onDownloadOfficeFont(spec) },
                        onCancel = { onCancelOfficeFontDownload(spec) },
                        onDelete = { onDeleteOfficeFont(spec) },
                        showDivider = index < OfficeFontManager.ALL_FONTS.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun EsrganModelItem(
    texts: UiText,
    spec: EsrganModelSpec,
    state: EsrganModelUiState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    showDivider: Boolean,
) {
    val context = LocalContext.current
    val purposeText = if (spec.id == EsrganModelManager.MODEL_ANIME.id) {
        texts.modelPurposeAnime
    } else {
        texts.modelPurpose
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = spec.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "· ${formatBytes(spec.totalSizeBytes, texts)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { openExternalLink(context, spec.sourceUrl, texts.linkUnavailable) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        AppIcon(
                            icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = texts.openLink,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    text = purposeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when (state) {
                EsrganModelUiState.NotDownloaded -> {
                    Button(
                        onClick = onDownload,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 32.dp, max = 34.dp)
                    ) {
                        Text(texts.modelDownloadAction, style = MaterialTheme.typography.labelMedium)
                    }
                }
                is EsrganModelUiState.Downloading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp
                        )
                        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                            AppIcon(
                                icon = Icons.Rounded.Close,
                                contentDescription = texts.cancelDownload,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                EsrganModelUiState.Downloaded -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AppIcon(
                                icon = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = texts.modelDownloaded,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                            AppIcon(
                                icon = Icons.Rounded.Refresh,
                                contentDescription = texts.modelRedownload,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                is EsrganModelUiState.Failed -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = state.message?.resolve(context) ?: texts.downloadFailed,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 30.dp, max = 32.dp)
                        ) {
                            Text(texts.modelDownloadAction, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (state is EsrganModelUiState.Downloading) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).height(3.dp)
            )
        }
    }

    if (showDivider) {
        val dividerColor = settingsDividerColor()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(0.5.dp)
                .background(dividerColor)
        )
    }
}

@Composable
private fun RifeModelItem(
    texts: UiText,
    spec: RifeModelSpec,
    state: RifeModelUiState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    showDivider: Boolean,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = spec.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "· ${formatBytes(spec.totalSizeBytes, texts)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { openExternalLink(context, spec.sourceUrl, texts.linkUnavailable) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        AppIcon(
                            icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = texts.openLink,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    text = texts.rifeModelPurpose,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when (state) {
                RifeModelUiState.NotDownloaded -> {
                    Button(
                        onClick = onDownload,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 32.dp, max = 34.dp)
                    ) {
                        Text(texts.modelDownloadAction, style = MaterialTheme.typography.labelMedium)
                    }
                }
                is RifeModelUiState.Downloading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp
                        )
                        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                            AppIcon(
                                icon = Icons.Rounded.Close,
                                contentDescription = texts.cancelDownload,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                RifeModelUiState.Downloaded -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AppIcon(
                                icon = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = texts.modelDownloaded,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                            AppIcon(
                                icon = Icons.Rounded.Refresh,
                                contentDescription = texts.modelRedownload,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                is RifeModelUiState.Failed -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = state.message?.resolve(context) ?: texts.downloadFailed,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 30.dp, max = 32.dp)
                        ) {
                            Text(texts.modelDownloadAction, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (state is RifeModelUiState.Downloading) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).height(3.dp)
            )
        }
    }

    if (showDivider) {
        val dividerColor = settingsDividerColor()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(0.5.dp)
                .background(dividerColor)
        )
    }
}

@Composable
private fun OfficeFontItem(
    texts: UiText,
    spec: OfficeFontSpec,
    state: OfficeFontUiState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    showDivider: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = texts.taskMessage(spec.displayName),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "· ${formatBytes(spec.sizeBytes, texts)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = texts.taskMessage(spec.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when (state) {
                OfficeFontUiState.NotDownloaded -> {
                    Button(
                        onClick = onDownload,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 32.dp, max = 34.dp)
                    ) {
                        Text(texts.modelDownloadAction, style = MaterialTheme.typography.labelMedium)
                    }
                }
                is OfficeFontUiState.Downloading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp
                        )
                        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                            AppIcon(
                                icon = Icons.Rounded.Close,
                                contentDescription = texts.cancelDownload,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                OfficeFontUiState.Downloaded -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AppIcon(
                                icon = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = texts.modelDownloaded,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                            AppIcon(
                                icon = Icons.Rounded.DeleteOutline,
                                contentDescription = texts.remove,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                            AppIcon(
                                icon = Icons.Rounded.Refresh,
                                contentDescription = texts.modelRedownload,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                is OfficeFontUiState.Failed -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = state.message?.resolve(LocalContext.current) ?: texts.downloadFailed,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 30.dp, max = 32.dp)
                        ) {
                            Text(texts.modelDownloadAction, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (state is OfficeFontUiState.Downloading) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).height(3.dp)
            )
        }
    }

    if (showDivider) {
        val dividerColor = settingsDividerColor()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(0.5.dp)
                .background(dividerColor)
        )
    }
}

@Composable
private fun SystemFontBanner(
    texts: UiText,
) {
    val bgColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                icon = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = texts.officeFontSystemReady,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = texts.officeFontSystemNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
