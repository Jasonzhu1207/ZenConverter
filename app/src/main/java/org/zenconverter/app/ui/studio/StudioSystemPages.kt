package org.zenconverter.app.ui.studio

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.zenconverter.app.audio.StudioOutput
import org.zenconverter.app.studio.StudioStore
import org.zenconverter.app.system.BatteryThermalGuard
import org.zenconverter.app.system.LocalTransferServer
import org.zenconverter.app.system.StorageCleaner
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.settings.InsetGroupCard
import org.zenconverter.app.ui.settings.SettingsPage
import org.zenconverter.app.ui.settings.SettingsRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatterySaver
import java.io.File

@Composable
internal fun WifiTransferScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf<String?>(null) }
    val server = remember {
        LocalTransferServer(context) {
            StudioStore.history.mapNotNull { entry ->
                entry.outputUri?.let { uri ->
                    val file = uriToLocalFile(context, Uri.parse(uri))
                    if (file != null && file.isFile) {
                        LocalTransferServer.SharedFile(file.name, file)
                    } else {
                        null
                    }
                }
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(server) {
        onDispose { server.stop() }
    }

    SettingsPage(texts.wifiTitle(), texts.studioBack(), onBack) {
        item("wifi-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.wifiDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (running && address != null) {
                        Text(
                            texts.wifiAddress(address!!),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            texts.wifiRunning(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            texts.wifiNoFiles(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !running,
                            onClick = {
                                scope.launch {
                                    val status = server.start()
                                    running = status.running
                                    address = status.address
                                }
                            }
                        ) { Text(texts.wifiStart()) }
                        OutlinedButton(
                            enabled = running,
                            onClick = {
                                server.stop()
                                running = false
                                address = null
                            }
                        ) { Text(texts.wifiStop()) }
                    }
                }
            }
        }
    }
}

private fun uriToLocalFile(context: android.content.Context, uri: Uri): File? {
    val path = uri.path ?: return null
    val direct = File(path)
    if (direct.isFile) return direct
    return null
}

@Composable
internal fun StorageCleanerScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<StorageCleaner.ScanResult?>(null) }
    var scanning by remember { mutableStateOf(false) }
    val cleaner = remember { StorageCleaner(context) }
    val pickFolder = rememberFolderPicker { treeUri ->
        scanning = true
        scope.launch {
            result = withContext(Dispatchers.IO) {
                runCatching { cleaner.scan(treeUri) }.getOrNull()
            }
            scanning = false
        }
    }

    SettingsPage(texts.cleanerTitle(), texts.studioBack(), onBack) {
        item("cleaner-scan") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !scanning, onClick = pickFolder) {
                        Text(if (scanning) texts.cleanerScanning() else texts.cleanerScan())
                    }
                }
            }
        }
        item("cleaner-result") {
            InsetGroupCard {
                val current = result
                if (current == null) {
                    StudioNote(if (scanning) texts.cleanerScanning() else texts.cleanerEmpty())
                } else {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            texts.cleanerFound(current.largeFiles.size, "${current.totalBytes / (1024 * 1024)} MB"),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            texts.cleanerDuplicates(current.duplicates.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        current.largeFiles.take(10).forEach { file ->
                            Text(
                                "${file.displayName} · ${file.sizeBytes / (1024 * 1024)} MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GuardScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    StudioStore.ensureLoaded(context)
    val enabled = StudioStore.guardEnabled.value
    val threshold = StudioStore.guardBatteryThreshold.value
    val guard = remember(threshold) { BatteryThermalGuard(context, threshold) }
    val pausedReason = when (guard.shouldPause()) {
        BatteryThermalGuard.PauseReason.Battery -> texts.guardPausedBattery()
        BatteryThermalGuard.PauseReason.Thermal -> texts.guardPausedThermal()
        null -> null
    }

    SettingsPage(texts.guardTitle(), texts.studioBack(), onBack) {
        item("guard-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.guardDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SettingsRow(
                        badgeIcon = Icons.Rounded.BatterySaver,
                        title = texts.studioGuardEnabled(),
                        subtitle = texts.guardBatteryThreshold(threshold),
                        trailingContent = {
                            androidx.compose.material3.Switch(
                                checked = enabled,
                                onCheckedChange = { StudioStore.setGuardEnabled(context, it) }
                            )
                        },
                        showDivider = false
                    )
                    StudioFieldLabel(texts.guardBatteryThreshold(threshold))
                    Slider(
                        value = threshold.toFloat(),
                        onValueChange = { StudioStore.setGuardBatteryThreshold(context, it.toInt()) },
                        valueRange = 5f..50f,
                        steps = 8
                    )
                    pausedReason?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
internal fun AccessibilityScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    StudioStore.ensureLoaded(context)
    val largeText = StudioStore.largeText.value
    val audioDescriptions = StudioStore.audioDescriptions.value

    SettingsPage(texts.a11ySettingsTitle(), texts.studioBack(), onBack) {
        item("a11y-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth()) {
                    SettingsRow(
                        badgeIcon = Icons.Rounded.Accessibility,
                        title = texts.a11yLargeText(),
                        subtitle = texts.a11yLargeTextDesc(),
                        trailingContent = {
                            androidx.compose.material3.Switch(
                                checked = largeText,
                                onCheckedChange = { StudioStore.setLargeText(context, it) }
                            )
                        }
                    )
                    SettingsRow(
                        badgeIcon = Icons.Rounded.Accessibility,
                        title = texts.a11yAudioDescriptions(),
                        subtitle = texts.a11yAudioDescriptionsDesc(),
                        trailingContent = {
                            androidx.compose.material3.Switch(
                                checked = audioDescriptions,
                                onCheckedChange = { StudioStore.setAudioDescriptions(context, it) }
                            )
                        },
                        showDivider = false
                    )
                }
            }
        }
    }
}
