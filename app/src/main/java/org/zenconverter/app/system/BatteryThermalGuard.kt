package org.zenconverter.app.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.delay

/**
 * Watches battery level and thermal state so long jobs can pause instead of
 * cooking the phone. Deliberately simple: the caller owns the loop and asks the
 * guard whether it should hold off.
 */
class BatteryThermalGuard(
    private val context: Context,
    private val batteryThresholdPercent: Int = 15,
    private val thermalStatusLimit: Int = PowerManager.THERMAL_STATUS_MODERATE
) {
    fun shouldPause(): PauseReason? {
        if (isBatteryLow()) return PauseReason.Battery
        if (isTooHot()) return PauseReason.Thermal
        return null
    }

    /** Suspends until the device is cool and charged enough to continue. */
    suspend fun awaitReady(onPaused: (PauseReason) -> Unit, onResumed: () -> Unit) {
        val reason = shouldPause() ?: return
        onPaused(reason)
        while (shouldPause() != null) {
            delay(POLL_INTERVAL_MS)
        }
        onResumed()
    }

    fun isBatteryLow(): Boolean {
        val manager = context.getSystemService(BatteryManager::class.java) ?: return false
        val status = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = isCharging()
        return status in 0..batteryThresholdPercent && !charging
    }

    fun isTooHot(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.currentThermalStatus >= thermalStatusLimit
    }

    private fun isCharging(): Boolean {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    enum class PauseReason { Battery, Thermal }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
