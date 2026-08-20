package org.zenconverter.app.model

import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
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
    private const val SCALE = 4
    private const val INPUT_NAME = "input"
    private const val TILE_SIZE = 192
    private const val TILE_PAD = 16
    private const val MAX_INTRA_OP_THREADS = 2

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
        val inputW = source.width
        val inputH = source.height
        val outputW = inputW * SCALE
        val outputH = inputH * SCALE

        val output = Bitmap.createBitmap(outputW, outputH, Bitmap.Config.ARGB_8888)

        val tileCols = (inputW + TILE_SIZE - 1) / TILE_SIZE
        val tileRows = (inputH + TILE_SIZE - 1) / TILE_SIZE
        val totalTiles = tileCols * tileRows
        var completedTiles = 0

        val maxTileW = TILE_SIZE + 2 * TILE_PAD
        val maxTileH = TILE_SIZE + 2 * TILE_PAD
        val tilePixelBuffer = IntArray(maxTileW * maxTileH)
        val inputFloatBuffer = FloatArray(maxTileW * maxTileH * 3)
        val outputFloatBuffer = FloatArray(maxTileW * SCALE * maxTileH * SCALE * 3)
        val pixelBuffer = IntArray(TILE_SIZE * SCALE * TILE_SIZE * SCALE)

        val env = OrtEnvironment.getEnvironment()
        
        try {
            OrtSession.SessionOptions().use { options ->
                val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_INTRA_OP_THREADS)
                runCatching { options.setIntraOpNumThreads(threads) }
                runCatching { options.setCPUArenaAllocator(false) }
                runCatching { options.setMemoryPatternOptimization(false) }

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
        } catch (e: OrtException) {
            throw IllegalStateException(
                "Image engine could not run the Real-ESRGAN model: ${e.message ?: "ONNX Runtime error"}",
                e
            )
        }

        return output
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

        for (yy in 0 until tileH) {
            val rowBase = yy * tileW
            for (xx in 0 until tileW) {
                val idx = rowBase + xx
                val color = tilePixels[idx]
                val alpha = Color.alpha(color)
                if (alpha == 255) {
                    outData[rOffset + idx] = Color.red(color) / 255f
                    outData[gOffset + idx] = Color.green(color) / 255f
                    outData[bOffset + idx] = Color.blue(color) / 255f
                } else {
                    outData[rOffset + idx] = compositeOnWhite(Color.red(color), alpha)
                    outData[gOffset + idx] = compositeOnWhite(Color.green(color), alpha)
                    outData[bOffset + idx] = compositeOnWhite(Color.blue(color), alpha)
                }
            }
        }
    }

    private fun compositeOnWhite(channel: Int, alpha: Int): Float {
        val blended = (channel * alpha + 255 * (255 - alpha)) / 255
        return blended / 255f
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
        val dstW = coreW * SCALE
        val dstH = coreH * SCALE
        var p = 0
        for (dy in 0 until dstH) {
            val srcY = srcOffsetY + dy
            val rowBase = srcY * outW + srcOffsetX
            for (dx in 0 until dstW) {
                val sx = rowBase + dx
                pixelBuffer[p++] = Color.rgb(
                    clampToByte(outFloat[sx]),
                    clampToByte(outFloat[planeSize + sx]),
                    clampToByte(outFloat[planeSize * 2 + sx])
                )
            }
        }
        output.setPixels(pixelBuffer, 0, dstW, destX, destY, dstW, dstH)
    }

    private fun clampToByte(value: Float): Int {
        return (value.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    }
}
