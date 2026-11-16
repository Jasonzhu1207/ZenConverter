package org.zenconverter.app.system

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Opt-in crash capture that never leaves the device.
 *
 * When the user enables it, an uncaught-exception handler writes a plain-text
 * report to the app's own cache directory. Nothing is uploaded and no network
 * call is made; the settings screen offers to share the file with an app the
 * user chooses, or to delete it. This keeps the "no accounts, no telemetry"
 * promise while still letting a user hand over a report when they want help.
 */
object CrashReporter {
    private const val PREFS = "zen_crash_reports"
    private const val KEY_ENABLED = "enabled"
    private const val DIR_NAME = "crash-reports"
    private const val MAX_REPORTS = 10

    @Volatile private var installed = false

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) install(context.applicationContext)
    }

    /** Installs the handler if the preference is on; safe to call on every start. */
    fun installIfEnabled(context: Context) {
        if (isEnabled(context)) install(context.applicationContext)
    }

    private fun install(context: Context) {
        if (installed) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Check the flag at crash time so turning the setting off stops capture
            // without having to swap the handler back.
            if (isEnabled(context)) runCatching { writeReport(context, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
        installed = true
    }

    fun reports(context: Context): List<File> {
        val dir = File(context.cacheDir, DIR_NAME)
        return dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun deleteAll(context: Context) {
        File(context.cacheDir, DIR_NAME).listFiles()?.forEach { it.delete() }
    }

    private fun writeReport(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.cacheDir, DIR_NAME).apply { mkdirs() }
        // Keep the folder small: a crash loop should not fill the cache.
        dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(MAX_REPORTS - 1)?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val report = File(dir, "crash-$stamp.txt")
        report.writeText(
            buildString {
                append("ZenConverter crash report\n")
                append("Time: ").append(Date()).append('\n')
                append("Thread: ").append(thread.name).append('\n')
                append("Android: ").append(Build.VERSION.RELEASE)
                append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                append("App: ").append(appVersion(context)).append("\n\n")
                append(throwable.stackTraceToString())
            }
        )
    }

    private fun appVersion(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("unknown")
}
