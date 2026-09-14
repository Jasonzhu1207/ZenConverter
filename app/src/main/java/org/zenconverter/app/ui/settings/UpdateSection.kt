package org.zenconverter.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.zenconverter.app.R
import org.zenconverter.app.ui.StatusLine
import org.zenconverter.app.ui.UiText
import org.zenconverter.app.ui.openExternalLink
import org.zenconverter.app.updates.UpdateChannel
import org.zenconverter.app.updates.UpdateCheckResult

@Composable
internal fun UpdateSection(texts: UiText, state: UpdateStateHolder) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsChoiceGroup(
            options = UpdateChannel.entries,
            selectedOption = state.channel,
            onOptionSelected = state::selectChannel,
            labelProvider = texts::updateChannelLabel,
            enabled = !state.busy
        )
        OutlinedButton(onClick = state::check, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.checking) texts.checkingUpdates else texts.checkUpdates)
        }
        if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (val result = state.checkState) {
            null -> Unit
            is UpdateCheckResult.Available -> StatusLine(texts.updateAvailableMessage(result.release))
            is UpdateCheckResult.UpToDate -> StatusLine(texts.currentIsLatest(result.latest.channel))
            is UpdateCheckResult.Failed -> StatusLine(texts.updateFailureMessage(result.reason, result.detail), isError = true)
        }
        state.notice?.let { StatusLine(it.resolve(context)) }
        val release = when (val result = state.checkState) {
            is UpdateCheckResult.Available -> result.release
            is UpdateCheckResult.UpToDate -> result.latest
            else -> null
        }
        release?.let {
            Text(texts.releaseDetail(it), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.ui_settings_update_source), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { openExternalLink(context, it.releasePageUrl, texts.linkUnavailable) }) {
                Text(stringResource(R.string.ui_settings_release_notes))
            }
        }
        if (state.checkState is UpdateCheckResult.Available && release != null) {
            when (val download = state.downloadState) {
                UpdateDownloadState.Idle -> {
                    Button(onClick = state::download, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(texts.appDownload)
                    }
                }
                is UpdateDownloadState.Downloading -> {
                    val fraction = download.progress.fraction
                    if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text(texts.downloadProgressMessage(download.progress), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = state::cancelDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(texts.cancelDownload)
                    }
                }
                is UpdateDownloadState.Completed -> {
                    StatusLine(texts.downloadComplete)
                    Button(onClick = state::install, modifier = Modifier.fillMaxWidth()) { Text(texts.openDownloadedApk) }
                }
                is UpdateDownloadState.Failed -> {
                    StatusLine(texts.downloadFailureMessage(download.message), isError = true)
                    Button(onClick = state::download, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(texts.appDownload)
                    }
                }
            }
            OutlinedButton(
                onClick = { openExternalLink(context, release.downloadUrl, texts.linkUnavailable) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(texts.browserDownload) }
        }
    }
}
