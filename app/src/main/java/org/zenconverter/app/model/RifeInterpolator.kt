package org.zenconverter.app.model

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * Runs RIFE (Real-Time Intermediate Flow Estimation) frame interpolation
 * using NCNN Vulkan GPU acceleration.
 */
object RifeInterpolator {
    private const val TAG = "RifeInterpolator"

    /**
     * Interpolates an intermediate frame between [frame0] and [frame1] using
     * the RIFE NCNN model at [paramPath] and [binPath].
     */
    fun interpolate(
        frame0: Bitmap,
        frame1: Bitmap,
        paramPath: String,
        binPath: String,
        isCancelled: () -> Boolean = { false }
    ): Bitmap {
        if (!NcnnNative.ensureLoaded()) {
            throw IllegalStateException("NCNN native library could not be loaded on this device")
        }

        val w = frame0.width
        val h = frame0.height

        val b0 = if (frame0.config == Bitmap.Config.ARGB_8888) frame0 else frame0.copy(Bitmap.Config.ARGB_8888, false)
        val b1 = if (frame1.config == Bitmap.Config.ARGB_8888) frame1 else frame1.copy(Bitmap.Config.ARGB_8888, false)
        val outputBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        try {
            val gpuCount = NcnnNative.getGpuCount()
            val gpuIndex = if (gpuCount > 0) 0 else -1

            val result = NcnnNative.nativeRifeInterpolate(
                srcBitmap0 = b0,
                srcBitmap1 = b1,
                dstBitmap = outputBitmap,
                paramPath = paramPath,
                binPath = binPath,
                gpuIndex = gpuIndex,
                cancelCheck = { isCancelled() }
            )

            when (result) {
                0 -> return outputBitmap
                -2 -> {
                    outputBitmap.recycle()
                    throw CancellationException("Frame interpolation was cancelled")
                }
                else -> {
                    outputBitmap.recycle()
                    throw IllegalStateException("NCNN RIFE inference failed with error code $result")
                }
            }
        } finally {
            if (b0 !== frame0) b0.recycle()
            if (b1 !== frame1) b1.recycle()
        }
    }
}
