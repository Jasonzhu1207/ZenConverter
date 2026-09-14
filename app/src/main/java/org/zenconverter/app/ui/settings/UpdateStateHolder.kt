package org.zenconverter.app.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.zenconverter.app.BuildConfig
import org.zenconverter.app.R
import org.zenconverter.app.i18n.LocalizedText
import org.zenconverter.app.i18n.localizedFailure
import org.zenconverter.app.i18n.localizedText
import org.zenconverter.app.updates.*

/** Owned by the mounted app content, never by a lazy list item or a settings destination. */
internal class UpdateStateHolder(
    context: Context,
    private val installedVersion: InstalledAppVersion,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    var channel by mutableStateOf(UpdateChannel.Stable)
        private set
    var checkState by mutableStateOf<UpdateCheckResult?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var downloadState by mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle)
        private set
    var notice by mutableStateOf<LocalizedText?>(null)
        private set
    private var downloadJob by mutableStateOf<Job?>(null)

    val busy: Boolean get() = checking || downloadJob != null

    fun selectChannel(next: UpdateChannel) {
        if (busy || next == channel) return
        channel = next
        checkState = null
        downloadState = UpdateDownloadState.Idle
        notice = null
    }

    fun check() {
        if (!BuildConfig.ENABLE_GITHUB_UPDATES || busy) return
        checking = true
        checkState = null
        downloadState = UpdateDownloadState.Idle
        notice = null
        val requestedChannel = channel
        scope.launch {
            try {
                checkState = GitHubUpdateChecker.check(requestedChannel, installedVersion)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                checkState = UpdateCheckResult.Failed(
                    UpdateFailureReason.InvalidResponse,
                    exception.message?.let(LocalizedText::ExternalDetail)
                )
            } finally {
                checking = false
            }
        }
    }

    fun download() {
        if (!BuildConfig.ENABLE_GITHUB_UPDATES || busy) return
        val release = (checkState as? UpdateCheckResult.Available)?.release ?: return
        notice = null
        downloadState = UpdateDownloadState.Downloading(DownloadProgress(0L, release.sizeBytes))
        downloadJob = scope.launch {
            try {
                val result = ApkUpdateDownloader.download(appContext, release) { progress ->
                    downloadState = UpdateDownloadState.Downloading(progress)
                }
                downloadState = UpdateDownloadState.Completed(result)
            } catch (cancelled: CancellationException) {
                downloadState = UpdateDownloadState.Idle
                notice = localizedText(R.string.ui_download_cancelled)
                throw cancelled
            } catch (exception: Exception) {
                downloadState = UpdateDownloadState.Failed(exception.localizedFailure(R.string.ui_download_failed))
            } finally {
                downloadJob = null
            }
        }
    }

    fun cancelDownload() {
        // Remain busy until the coroutine releases its file and connection.
        downloadJob?.cancel()
    }

    fun install() {
        val completed = downloadState as? UpdateDownloadState.Completed ?: return
        notice = try {
            when (ApkInstaller.openDownloadedApk(appContext, completed.update.file)) {
                ApkOpenResult.Started -> null
                ApkOpenResult.PermissionSettingsOpened -> localizedText(R.string.ui_install_permission_required)
                ApkOpenResult.Failed -> localizedText(R.string.ui_apk_open_failed)
            }
        } catch (exception: Exception) {
            exception.localizedFailure(R.string.ui_apk_open_failed)
        }
    }
}

internal sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val progress: DownloadProgress) : UpdateDownloadState
    data class Completed(val update: DownloadedUpdate) : UpdateDownloadState
    data class Failed(val message: LocalizedText) : UpdateDownloadState
}
