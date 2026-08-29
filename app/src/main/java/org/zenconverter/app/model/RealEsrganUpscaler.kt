package org.zenconverter.app.model

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * Runs Real-ESRGAN super-resolution using NCNN Vulkan GPU acceleration.
 */
object RealEsrganUpscaler {
    private const val TAG = "RealEsrganUpscaler"
    private const val SCALE = 4
    private const val DEFAULT_TILE_SIZE = 200
    private const val DEFAULT_TILE_PAD = 10

    /**
     * Up-scales [source] by 4x using the NCNN model at [paramPath] and [binPath].
     * [onTileProgress] receives 0f..1f as tiles finish; [isCancelled] is polled
     * before every tile and cancels by throwing a [CancellationException].
     */
    fun upscale(
        source: Bitmap,
        paramPath: String,
        binPath: String,
        onTileProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): Bitmap {
        if (!NcnnNative.ensureLoaded()) {
            throw IllegalStateException("NCNN native library could not be loaded on this device")
        }

        val inputW = source.width
        val inputH = source.height
        val outputW = inputW * SCALE
        val outputH = inputH * SCALE

        val inferenceBitmap = if (source.config == Bitmap.Config.ARGB_8888) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val outputBitmap = Bitmap.createBitmap(outputW, outputH, Bitmap.Config.ARGB_8888)

        try {
            val gpuCount = NcnnNative.getGpuCount()
            val gpuIndex = if (gpuCount > 0) 0 else -1

            if (gpuIndex >= 0) {
                val gpuName = NcnnNative.getGpuDeviceName(gpuIndex)
                Log.i(TAG, "Starting Real-ESRGAN 4x upscale on GPU [$gpuName] (input: ${inputW}x${inputH} -> output: ${outputW}x${outputH})")
            } else {
                Log.i(TAG, "Starting Real-ESRGAN 4x upscale on CPU (input: ${inputW}x${inputH} -> output: ${outputW}x${outputH})")
            }

            val result = NcnnNative.nativeRealEsrganUpscale(
                srcBitmap = inferenceBitmap,
                dstBitmap = outputBitmap,
                paramPath = paramPath,
                binPath = binPath,
                scale = SCALE,
                tileSize = DEFAULT_TILE_SIZE,
                tilePad = DEFAULT_TILE_PAD,
                gpuIndex = gpuIndex,
                progressCallback = { progress ->
                    onTileProgress(progress)
                },
                cancelCheck = {
                    isCancelled()
                }
            )

            when (result) {
                0 -> {
                    Log.i(TAG, "Real-ESRGAN super-resolution completed successfully!")
                    return outputBitmap
                }
                -2 -> {
                    outputBitmap.recycle()
                    throw CancellationException("Image super-resolution was cancelled")
                }
                else -> {
                    outputBitmap.recycle()
                    throw IllegalStateException("NCNN Real-ESRGAN inference failed with error code $result")
                }
            }
        } finally {
            if (inferenceBitmap !== source) {
                inferenceBitmap.recycle()
            }
        }
    }
}
