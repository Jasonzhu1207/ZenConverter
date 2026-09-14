package org.zenconverter.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.zenconverter.app.metadata.MetadataBackupInfo
import org.zenconverter.app.metadata.MetadataInspection
import org.zenconverter.app.metadata.MetadataMessageKey
import org.zenconverter.app.metadata.MetadataStatusMessage
import org.zenconverter.app.metadata.MetadataTargetKind
import org.zenconverter.app.metadata.MetadataToolState
import org.zenconverter.app.ui.AppIcon
import org.zenconverter.app.ui.SmallTag
import org.zenconverter.app.ui.StatusLine
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.ZenPromptFrame
import org.zenconverter.app.ui.metadataCompactRows
import org.zenconverter.app.ui.metadataDetailRows
import org.zenconverter.app.ui.metadataPrimaryInfoLine
import org.zenconverter.app.ui.theme.ZenAnimations

@Composable
internal fun MetadataSecurityScreen(
    texts: UiText,
    state: MetadataToolState,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onClean: () -> Unit,
    onRestore: (String) -> Unit,
    onBack: () -> Unit,

    modifier: Modifier = Modifier
) {
    SettingsPage(texts.metadataSecurityTitle, texts.settingsBack, onBack, modifier = modifier) {
        // Introductory Guidance Card
        item {
            InsetGroupCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcon(
                                icon = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = texts.metadataSecurityTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = texts.metadataSecurityNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = texts.metadataBackupNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Pickers Section
        item {
            SettingsGroupHeader(title = texts.target)
            AdaptiveActions {
                Button(
                    onClick = onPickImage,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    AppIcon(
                        icon = Icons.Rounded.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(texts.pickMetadataImage)
                }
                OutlinedButton(
                    onClick = onPickVideo,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, settingsHairlineColor()),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = settingsCardColor(),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    AppIcon(
                        icon = Icons.Rounded.Videocam,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(texts.pickMetadataVideo)
                }
            }
        }

        // Inspection Results Workbench
        item {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    fadeIn(animationSpec = tween(ZenAnimations.ContentFadeDuration)) togetherWith
                        fadeOut(animationSpec = tween(ZenAnimations.ContentFadeOutDuration)) using
                        SizeTransform(clip = false)
                },
                label = "MetadataWorkbenchState"
            ) { targetState ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (targetState) {
                        MetadataToolState.Empty -> {
                            InsetGroupCard {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = texts.metadataEmpty,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        MetadataToolState.Loading -> {
                            InsetGroupCard {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text(
                                        text = texts.processing,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        is MetadataToolState.Error -> {
                            InsetGroupCard {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    StatusLine(
                                        text = texts.metadataMessage(targetState.message),
                                        isError = true
                                    )
                                }
                            }
                        }
                        is MetadataToolState.Ready -> {
                            MetadataInspectionWorkbenchCard(
                                texts = texts,
                                state = targetState,
                                onClean = onClean,
                                onRestore = onRestore,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataInspectionWorkbenchCard(
    texts: UiText,
    state: MetadataToolState.Ready,
    onClean: () -> Unit,
    onRestore: (String) -> Unit,
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

    InsetGroupCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // File basics
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
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = metadataPrimaryInfoLine(inspection, texts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SmallTag(texts.metadataKindLabel(inspection.kind))
            }

            // Tags row
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallTag(texts.metadataSupportLabel(inspection))
                if (inspection.kind == MetadataTargetKind.Image) {
                    SmallTag(texts.yesNoLabel(inspection.hasGps, texts.metadataGps))
                    if (inspection.editable) {
                        SmallTag(texts.metadataBackupCountLabel(inspection.backups.size))
                    }
                }
            }

            // Key-Value Attributes
            MetadataCompactRows(
                rows = metadataCompactRows(inspection, texts),
            )

            // Warning / Status Message
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

            // Action Buttons
            AdaptiveActions {
                OutlinedButton(
                    onClick = { showDetails = true },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, settingsHairlineColor()),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(texts.metadataDetails)
                }

                if (inspection.kind == MetadataTargetKind.Image) {
                    Button(
                        onClick = onClean,
                        enabled = inspection.editable &&
                            inspection.hasRemovableMetadata &&
                            inspection.canWrite &&
                            !state.busy,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (state.busy) texts.processing else texts.metadataCleanAndBackup,
                        )
                    }
                }
            }

            if (inspection.kind == MetadataTargetKind.Image && inspection.backups.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        if (inspection.backups.size == 1) {
                            onRestore(inspection.backups.first().id)
                        } else {
                            showRestoreChoices = true
                        }
                    },
                    enabled = inspection.editable &&
                        inspection.canWrite &&
                        !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(texts.metadataRestore)
                }
            }
        }
    }

    if (showDetails) {
        MetadataDetailsDialog(
            texts = texts,
            inspection = inspection,
            onDismiss = { showDetails = false },
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
private fun MetadataCompactRows(
    rows: List<Pair<String, String>>,
) {
    val rowBg = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.38f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
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
    onDismiss: () -> Unit,
) {
    ZenPromptFrame(onDismissRequest = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(
                icon = Icons.Rounded.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = texts.metadataDetails,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MetadataCompactRows(
                rows = metadataDetailRows(inspection, texts),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(
                icon = Icons.Rounded.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = texts.metadataRestoreTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
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
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        0.5.dp,
                        if (index == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (index == 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                        else MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = texts.metadataBackupLabel(backup, recommended = index == 0),
                                                modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(texts.cancel)
        }
    }
}
