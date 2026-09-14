package org.zenconverter.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
            .padding(14.dp),
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
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = purposeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = formatBytes(spec.totalSizeBytes, texts),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column {
            Text(
                text = stringResource(R.string.display_esrgan_model_download_section_1_s_real_esrgan, texts.modelSource),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            TextButton(onClick = { openExternalLink(context, spec.sourceUrl, texts.linkUnavailable) }) {
                Text(texts.openLink)
            }
        }

        when (state) {
            EsrganModelUiState.NotDownloaded -> {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(texts.modelDownloadAction)
                }
            }
            is EsrganModelUiState.Downloading -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
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
                }
            }
            EsrganModelUiState.Downloaded -> {
                AdaptiveActions {
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
                            fontWeight = FontWeight.Medium,
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
                        text = state.message?.resolve(LocalContext.current) ?: texts.downloadFailed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(texts.modelDownloadAction)
                    }
                }
            }
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
            .padding(14.dp),
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
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = texts.rifeModelPurpose,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = formatBytes(spec.totalSizeBytes, texts),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column {
            Text(
                text = stringResource(R.string.display_rife_model_download_section_1_s_rife, texts.modelSource),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            TextButton(onClick = { openExternalLink(context, spec.sourceUrl, texts.linkUnavailable) }) {
                Text(texts.openLink)
            }
        }

        when (state) {
            RifeModelUiState.NotDownloaded -> {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(texts.modelDownloadAction)
                }
            }
            is RifeModelUiState.Downloading -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
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
                }
            }
            RifeModelUiState.Downloaded -> {
                AdaptiveActions {
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
                            fontWeight = FontWeight.Medium,
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
                        text = state.message?.resolve(LocalContext.current) ?: texts.downloadFailed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(texts.modelDownloadAction)
                    }
                }
            }
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
            .padding(14.dp),
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
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = texts.taskMessage(spec.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = formatBytes(spec.sizeBytes, texts),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column {
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(texts.modelDownloadAction)
                }
            }
            is OfficeFontUiState.Downloading -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
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
                }
            }
            OfficeFontUiState.Downloaded -> {
                AdaptiveActions {
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
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onDelete) {
                            AppIcon(
                                icon = Icons.Rounded.DeleteOutline,
                                contentDescription = texts.remove,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        TextButton(onClick = onDownload) {
                            Text(texts.modelRedownload)
                        }
                    }
                }
            }
            is OfficeFontUiState.Failed -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = state.message?.resolve(LocalContext.current) ?: texts.downloadFailed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(texts.modelDownloadAction)
                    }
                }
            }
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
            modifier = Modifier.padding(14.dp),
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
