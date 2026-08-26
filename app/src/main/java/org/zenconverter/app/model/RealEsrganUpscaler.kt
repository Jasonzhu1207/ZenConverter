package org.zenconverter.app.model

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Process
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import android.util.Log
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Runs the RealESRGAN_x4plus ONNX model through the normal ONNX Runtime API
 * (OrtEnvironment + OrtSession). The .onnx file is loaded as a data file, never
 * as executable code: no ClassLoader, no System.load, no reflection. All ONNX
 * objects that implement AutoCloseable (OnnxTensor, OrtSession.Result,
 * OrtSession) are closed explicitly with `.use {}` because they wrap native
 * C++ pointers that the JVM garbage collector cannot reclaim; relying on GC in
 * the tile loop would leak native memory and crash with a native OOM.
 */
object RealEsrganUpscaler {
    private const val TAG = "RealEsrganUpscaler"
    private const val SCALE = 4
    private const val INPUT_NAME = "input"
    private const val TILE_SIZE = 256
    private const val TILE_PAD = 16
    private const val MIN_INTRA_OP_THREADS = 2
    private const val MAX_INTRA_OP_THREADS = 6

    /**
     * Up-scales [source] by 4x using the model at [modelPath].
     * [onTileProgress] receives 0f..1f as tiles finish; [isCancelled] is polled
     * before every tile and cancels by throwing a [CancellationException].
     */
    fun upscale(
        source: Bitmap,
        modelPath: String,
        onTileProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): Bitmap {
        if (!isOnnxRuntimeSupported()) {
            throw IllegalStateException(
                "Image engine could not run AI super-resolution on this device"
            )
        }

        val inputW = source.width
        val inputH = source.height
        val outputW = inputW * SCALE
        val outputH = inputH * SCALE

        val output = Bitmap.createBitmap(outputW, outputH, Bitmap.Config.ARGB_8888)

        val tileCols = (inputW + TILE_SIZE - 1) / TILE_SIZE
        val tileRows = (inputH + TILE_SIZE - 1) / TILE_SIZE
        val totalTiles = tileCols * tileRows

        val maxTileW = TILE_SIZE + 2 * TILE_PAD
        val maxTileH = TILE_SIZE + 2 * TILE_PAD
        val tilePixelBuffer = IntArray(maxTileW * maxTileH)
        val inputFloatBuffer = FloatArray(maxTileW * maxTileH * 3)
        val outputFloatBuffer = FloatArray(maxTileW * SCALE * maxTileH * SCALE * 3)
        val pixelBuffer = IntArray(TILE_SIZE * SCALE * TILE_SIZE * SCALE)

        val env = OrtEnvironment.getEnvironment()
        val canAttemptNnapi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !isEmulator()
        var nnapiSucceeded = false

        val tid = Process.myTid()
        val originalPriority = runCatching { Process.getThreadPriority(tid) }.getOrDefault(Process.THREAD_PRIORITY_DEFAULT)

        // Elevate current thread priority to DISPLAY (-4) to signal the Android EAS
        // (Energy Aware Scheduling) kernel governor to bias execution onto Big/Prime performance cores.
        runCatching {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
        }

        try {
            if (canAttemptNnapi) {
                try {
                    Log.i(TAG, "Attempting AI super-resolution with NNAPI (GPU/NPU) acceleration...")
                    executeTiledInference(
                        env = env,
                        modelPath = modelPath,
                        source = source,
                        output = output,
                        inputW = inputW,
                        inputH = inputH,
                        tileCols = tileCols,
                        tileRows = tileRows,
                        totalTiles = totalTiles,
                        tilePixelBuffer = tilePixelBuffer,
                        inputFloatBuffer = inputFloatBuffer,
                        outputFloatBuffer = outputFloatBuffer,
                        pixelBuffer = pixelBuffer,
                        useNnapi = true,
                        onTileProgress = onTileProgress,
                        isCancelled = isCancelled
                    )
                    nnapiSucceeded = true
                    Log.i(TAG, "Super-resolution completed successfully via NNAPI (GPU/NPU) acceleration!")
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    throw cancellation
                } catch (driverError: Throwable) {
                    Log.w(TAG, "NNAPI acceleration failed or unsupported on this device; falling back to CPU: ${driverError.message}")
                }
            }

            if (!nnapiSucceeded) {
                try {
                    Log.i(TAG, "Running AI super-resolution with multi-core CPU engine...")
                    executeTiledInference(
                        env = env,
                        modelPath = modelPath,
                        source = source,
                        output = output,
                        inputW = inputW,
                        inputH = inputH,
                        tileCols = tileCols,
                        tileRows = tileRows,
                        totalTiles = totalTiles,
                        tilePixelBuffer = tilePixelBuffer,
                        inputFloatBuffer = inputFloatBuffer,
                        outputFloatBuffer = outputFloatBuffer,
                        pixelBuffer = pixelBuffer,
                        useNnapi = false,
                        onTileProgress = onTileProgress,
                        isCancelled = isCancelled
                    )
                    Log.i(TAG, "Super-resolution completed successfully via multi-core CPU engine")
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "Image engine could not run the Real-ESRGAN model: ${e.message ?: "ONNX Runtime error"}",
                        e
                    )
                }
            }

            return output
        } finally {
            // Restore previous thread priority so subsequent background tasks don't retain high priority
            runCatching {
                Process.setThreadPriority(tid, originalPriority)
            }
        }
    }

    private fun createSessionOptions(useNnapi: Boolean): OrtSession.SessionOptions {
        val options = OrtSession.SessionOptions()
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        val threads = (availableProcessors - 1)
            .coerceIn(MIN_INTRA_OP_THREADS, MAX_INTRA_OP_THREADS)
            .coerceAtLeast(1)
        runCatching { options.setIntraOpNumThreads(threads) }
        runCatching { options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT) }
        runCatching { options.setCPUArenaAllocator(true) }
        runCatching { options.setMemoryPatternOptimization(true) }

        if (useNnapi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                options.addNnapi()
            }
        }
        return options
    }

    private fun executeTiledInference(
        env: OrtEnvironment,
        modelPath: String,
        source: Bitmap,
        output: Bitmap,
        inputW: Int,
        inputH: Int,
        tileCols: Int,
        tileRows: Int,
        totalTiles: Int,
        tilePixelBuffer: IntArray,
        inputFloatBuffer: FloatArray,
        outputFloatBuffer: FloatArray,
        pixelBuffer: IntArray,
        useNnapi: Boolean,
        onTileProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ) {
        var completedTiles = 0
        createSessionOptions(useNnapi).use { options ->
            env.createSession(modelPath, options).use { session ->
                for (row in 0 until tileRows) {
                    for (col in 0 until tileCols) {
                        if (isCancelled()) {
                            throw kotlinx.coroutines.CancellationException("Image super-resolution cancelled")
                        }

                        val tx = col * TILE_SIZE
                        val ty = row * TILE_SIZE
                        val x0 = max(0, tx - TILE_PAD)
                        val y0 = max(0, ty - TILE_PAD)
                        val x1 = min(inputW, tx + TILE_SIZE + TILE_PAD)
                        val y1 = min(inputH, ty + TILE_SIZE + TILE_PAD)
                        val tileW = x1 - x0
                        val tileH = y1 - y0

                        source.getPixels(tilePixelBuffer, 0, tileW, x0, y0, tileW, tileH)
                        bitmapRegionToNchw(tilePixelBuffer, tileW, tileH, inputFloatBuffer)
                        val shape = longArrayOf(1L, 3L, tileH.toLong(), tileW.toLong())
                        
                        val inputBuf = FloatBuffer.wrap(inputFloatBuffer, 0, tileW * tileH * 3)

                        OnnxTensor.createTensor(env, inputBuf, shape).use { inputTensor ->
                            session.run(mapOf(INPUT_NAME to inputTensor)).use { result ->
                                val outTensor = result.get(0) as OnnxTensor
                                val outW = tileW * SCALE
                                val outH = tileH * SCALE
                                outTensor.floatBuffer.get(outputFloatBuffer, 0, outW * outH * 3)
                                writeTile(
                                    output = output,
                                    outFloat = outputFloatBuffer,
                                    outW = outW,
                                    outH = outH,
                                    srcOffsetX = (tx - x0) * SCALE,
                                    srcOffsetY = (ty - y0) * SCALE,
                                    destX = tx * SCALE,
                                    destY = ty * SCALE,
                                    coreW = min(tx + TILE_SIZE, inputW) - tx,
                                    coreH = min(ty + TILE_SIZE, inputH) - ty,
                                    pixelBuffer = pixelBuffer
                                )
                            }
                        }

                        completedTiles++
                        onTileProgress(completedTiles.toFloat() / totalTiles.toFloat())
                    }
                }
            }
        }
    }

    /**
     * Extracts the region into an NCHW RGB float array in the [0,1] range.
     * Semi-transparent pixels are composited onto a white background because
     * the model outputs opaque RGB only.
     */
    private fun bitmapRegionToNchw(
        tilePixels: IntArray,
        tileW: Int,
        tileH: Int,
        outData: FloatArray
    ) {
        val planeSize = tileW * tileH
        val rOffset = 0
        val gOffset = planeSize
        val bOffset = planeSize * 2
        val inv255 = 1f / 255f

        for (idx in 0 until planeSize) {
            val color = tilePixels[idx]
            val alpha = (color ushr 24) and 0xFF
            val red = (color ushr 16) and 0xFF
            val green = (color ushr 8) and 0xFF
            val blue = color and 0xFF
            if (alpha == 255) {
                outData[rOffset + idx] = red * inv255
                outData[gOffset + idx] = green * inv255
                outData[bOffset + idx] = blue * inv255
            } else {
                outData[rOffset + idx] = ((red * alpha + 255 * (255 - alpha)) / 255) * inv255
                outData[gOffset + idx] = ((green * alpha + 255 * (255 - alpha)) / 255) * inv255
                outData[bOffset + idx] = ((blue * alpha + 255 * (255 - alpha)) / 255) * inv255
            }
        }
    }

    /**
     * Writes the model's up-scaled core tile into [output]. [outFloat] holds the
     * NCHW float output (channel-major: R plane, G plane, B plane), and
     * [srcOffsetX]/[srcOffsetY] point at the core region inside that tile while
     * [destX]/[destY] locate the core region in the full-size output bitmap.
     */
    private fun writeTile(
        output: Bitmap,
        outFloat: FloatArray,
        outW: Int,
        outH: Int,
        srcOffsetX: Int,
        srcOffsetY: Int,
        destX: Int,
        destY: Int,
        coreW: Int,
        coreH: Int,
        pixelBuffer: IntArray
    ) {
        val planeSize = outW * outH
        val gOffset = planeSize
        val bOffset = planeSize * 2
        val dstW = coreW * SCALE
        val dstH = coreH * SCALE
        var p = 0
        for (dy in 0 until dstH) {
            val srcY = srcOffsetY + dy
            val rowBase = srcY * outW + srcOffsetX
            for (dx in 0 until dstW) {
                val sx = rowBase + dx
                val r = (outFloat[sx].coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val g = (outFloat[gOffset + sx].coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                val b = (outFloat[bOffset + sx].coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
                pixelBuffer[p++] = (-0x1000000) or (r shl 16) or (g shl 8) or b
            }
        }
        output.setPixels(pixelBuffer, 0, dstW, destX, destY, dstW, dstH)
    }

    /**
     * Returns false on environments where ONNX Runtime's arm64 native library
     * is known to crash during load, so [upscale] refuses to touch it there and
     * the conversion fails with a clear message instead of a native segfault.
     *
     * ONNX Runtime 1.29.0 performs CPU topology detection inside its ELF
     * constructors (it reads `/sys/devices/system/cpu/.../cache/index[*]/shared_cpu_list`).
     * When this arm64-only build runs on an x86 emulator through the ARM-to-x86
     * translation bridge, that detection reads the host's mismatched CPU tree
     * and dereferences a null pointer before any Java exception can be thrown.
     * A SIGSEGV in `dlopen` constructors cannot be caught from Kotlin, so the
     * only safe behavior is to not load the library on those devices.
     */
    private fun isOnnxRuntimeSupported(): Boolean = !isEmulator()

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.orEmpty()
        val model = Build.MODEL.orEmpty()
        val hardware = Build.HARDWARE.orEmpty()
        val product = Build.PRODUCT.orEmpty()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val device = Build.DEVICE.orEmpty()
        return when {
            fingerprint.startsWith("generic", ignoreCase = true) -> true
            fingerprint.contains("emulator", ignoreCase = true) -> true
            model.contains("emulator", ignoreCase = true) -> true
            model.contains("android sdk", ignoreCase = true) -> true
            hardware.contains("goldfish", ignoreCase = true) -> true
            hardware.contains("ranchu", ignoreCase = true) -> true
            manufacturer.contains("genymotion", ignoreCase = true) -> true
            product.contains("sdk", ignoreCase = true) ||
                product.contains("emulator", ignoreCase = true) -> true
            brand.startsWith("generic", ignoreCase = true) &&
                device.startsWith("generic", ignoreCase = true) -> true
            else -> false
        }
    }
}
