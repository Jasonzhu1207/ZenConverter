package org.zenconverter.app.ui.studio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import org.zenconverter.app.system.CrashReporter
import org.zenconverter.app.system.LocalTransferServer
import org.zenconverter.app.system.MediaShrinker
import org.zenconverter.app.system.StorageCleaner
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.formatBytes
import org.zenconverter.app.ui.settings.InsetGroupCard
import org.zenconverter.app.ui.settings.SettingsPage
import org.zenconverter.app.ui.settings.SettingsRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BugReport
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
    var compressingUri by remember { mutableStateOf<String?>(null) }
    var savedBytes by remember { mutableStateOf(0L) }
    var status by remember { mutableStateOf<String?>(null) }
    val cleaner = remember { StorageCleaner(context) }
    val pickFolder = rememberFolderPicker { treeUri ->
        scanning = true
        status = null
        savedBytes = 0L
        scope.launch {
            result = withContext(Dispatchers.IO) {
                runCatching { cleaner.scan(treeUri) }.getOrNull()
            }
            scanning = false
        }
    }

    fun compressVideo(file: StorageCleaner.LargeFile) {
        compressingUri = file.uri.toString()
        status = null
        scope.launch {
            val temp = File(context.cacheDir, "zen-cleaner-${System.currentTimeMillis()}.mp4")
            val shrink = withContext(Dispatchers.IO) {
                runCatching {
                    MediaShrinker.shrinkVideo(context, file.uri, temp, file.sizeBytes)
                }.getOrNull()
            }
            val label = if (shrink != null && shrink.savedBytes > 0L) {
                StudioOutput.save(
                    context,
                    shrink.output,
                    "zenconverter-compressed-${System.currentTimeMillis()}.mp4",
                    "video/mp4",
                    StudioOutput.Collection.Movies
                )
                savedBytes += shrink.savedBytes
                texts.cleanerSaved(formatBytes(shrink.savedBytes, texts))
            } else {
                texts.studioFailed()
            }
            temp.delete()
            status = label
            compressingUri = null
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
                        if (savedBytes > 0L) {
                            Text(
                                texts.cleanerSaved(formatBytes(savedBytes, texts)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        current.largeFiles.take(10).forEach { file ->
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "${file.displayName} · ${formatBytes(file.sizeBytes, texts)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (file.mimeType?.startsWith("video/") == true) {
                                    val busy = compressingUri == file.uri.toString()
                                    OutlinedButton(
                                        enabled = compressingUri == null,
                                        onClick = { compressVideo(file) }
                                    ) { Text(if (busy) texts.cleanerScanning() else texts.cleanerCompress()) }
                                }
                            }
                        }
                        status?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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

@Composable
internal fun CrashReportScreen(texts: UiText, onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(CrashReporter.isEnabled(context)) }
    var reportCount by remember { mutableStateOf(CrashReporter.reports(context).size) }
    var status by remember { mutableStateOf<String?>(null) }
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    SettingsPage(texts.crashReportingTitle(), texts.studioBack(), onBack) {
        item("crash-body") {
            InsetGroupCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        texts.crashReportingDesc(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SettingsRow(
                        badgeIcon = Icons.Rounded.BugReport,
                        title = texts.crashReportingEnable(),
                        trailingContent = {
                            androidx.compose.material3.Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    enabled = it
                                    CrashReporter.setEnabled(context, it)
                                }
                            )
                        },
                        showDivider = false
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = reportCount > 0,
                            onClick = {
                                val latest = CrashReporter.reports(context).firstOrNull() ?: return@OutlinedButton
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", latest
                                )
                                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                shareLauncher.launch(android.content.Intent.createChooser(share, texts.crashReportingShare()))
                            }
                        ) { Text(texts.crashReportingShare()) }
                        OutlinedButton(
                            enabled = reportCount > 0,
                            onClick = {
                                CrashReporter.deleteAll(context)
                                reportCount = 0
                                status = texts.crashReportingNone()
                            }
                        ) { Text(texts.crashReportingDelete()) }
                    }
                    Text(
                        if (reportCount == 0) texts.crashReportingNone() else texts.crashReportingCount(reportCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
