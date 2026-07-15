package org.zenconverter.app.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

object ApkInstaller {
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    fun openDownloadedApk(context: Context, file: File): ApkOpenResult {
        val appContext = context.applicationContext
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            return if (openInstallPermissionSettings(appContext)) {
                ApkOpenResult.PermissionSettingsOpened
            } else {
                ApkOpenResult.Failed
            }
        }

        val apkUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            appContext.startActivity(intent)
            ApkOpenResult.Started
        } catch (_: ActivityNotFoundException) {
            ApkOpenResult.Failed
        } catch (_: SecurityException) {
            ApkOpenResult.Failed
        }
    }

    private fun openInstallPermissionSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

enum class ApkOpenResult {
    Started,
    PermissionSettingsOpened,
    Failed
}
