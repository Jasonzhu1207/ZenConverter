package org.zenconverter.app.model

import android.graphics.Bitmap
import android.util.Log

/**
 * JNI bridge to NCNN and Vulkan GPU acceleration routines.
 */
object NcnnNative {
    private const val TAG = "NcnnNative"

    private val isLoaded: Boolean by lazy {
        try {
            System.loadLibrary("ncnn")
            System.loadLibrary("zen_ncnn")
            val gpuCount = nativeInit()
            Log.i(TAG, "NCNN native library loaded successfully. Available Vulkan GPUs: $gpuCount")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load NCNN native libraries: ${t.message}", t)
            false
        }
    }

    fun ensureLoaded(): Boolean = isLoaded

    fun getGpuCount(): Int {
        if (!ensureLoaded()) return 0
        return nativeGetGpuCount()
    }

    fun getGpuDeviceName(deviceIndex: Int): String {
        if (!ensureLoaded()) return "CPU"
        return nativeGetGpuDeviceName(deviceIndex)
    }

    fun interface ProgressCallback {
        fun onProgress(progress: Float)
    }

    fun interface CancelCheck {
        fun isCancelled(): Boolean
    }

    external fun nativeInit(): Int
    external fun nativeDestroy()
    external fun nativeGetGpuCount(): Int
    external fun nativeGetGpuDeviceName(deviceIndex: Int): String
    external fun nativeRealEsrganUpscale(
        srcBitmap: Bitmap,
        dstBitmap: Bitmap,
        paramPath: String,
        binPath: String,
        scale: Int,
        tileSize: Int,
        tilePad: Int,
        gpuIndex: Int,
        progressCallback: ProgressCallback?,
        cancelCheck: CancelCheck?
    ): Int
}
