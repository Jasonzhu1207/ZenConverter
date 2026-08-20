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
    private const val TILE_SIZE = 512
    private const val TILE_PAD = 16
    private const val MAX_INTRA_OP_THREADS = 4

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

        val srcPixels = IntArray(inputW * inputH)
        source.getPixels(srcPixels, 0, inputW, 0, 0, inputW, inputH)

        val tileCols = (inputW + TILE_SIZE - 1) / TILE_SIZE
        val tileRows = (inputH + TILE_SIZE - 1) / TILE_SIZE
        val totalTiles = tileCols * tileRows
        var completedTiles = 0

        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_INTRA_OP_THREADS)
            runCatching { setIntraOpNumThreads(threads) }
        }

        try {
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

                        val input = bitmapRegionToNchw(srcPixels, inputW, x0, y0, tileW, tileH)
                        val shape = longArrayOf(1L, 3L, tileH.toLong(), tileW.toLong())

                        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { inputTensor ->
                            session.run(mapOf(INPUT_NAME to inputTensor)).use { result ->
                                val outTensor = result.get(0) as OnnxTensor
                                val outW = tileW * SCALE
                                val outH = tileH * SCALE
                                val outFloat = FloatArray(outW * outH * 3)
                                outTensor.floatBuffer.get(outFloat)
                                writeTile(
                                    output = output,
                                    outFloat = outFloat,
                                    outW = outW,
                                    srcOffsetX = (tx - x0) * SCALE,
                                    srcOffsetY = (ty - y0) * SCALE,
                                    destX = tx * SCALE,
                                    destY = ty * SCALE,
                                    coreW = min(tx + TILE_SIZE, inputW) - tx,
                                    coreH = min(ty + TILE_SIZE, inputH) - ty
                                )
                            }
                        }

                        completedTiles++
                        onTileProgress(completedTiles.toFloat() / totalTiles.toFloat())
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
     * Extracts the [x0, x1) x [y0, y1) region into an NCHW RGB float array in
     * the [0,1] range. Tile regions are clamped to the image bounds, so border
     * tiles are simply clipped (matching the model's own zero-padding at the
     * true image edge) and interior tiles overlap real pixels for context.
     * Semi-transparent pixels are composited onto a white background because
     * the model outputs opaque RGB only.
     */
    private fun bitmapRegionToNchw(
        srcPixels: IntArray,
        srcWidth: Int,
        x0: Int,
        y0: Int,
        tileW: Int,
        tileH: Int
    ): FloatArray {
        val planeSize = tileW * tileH
        val data = FloatArray(planeSize * 3)
        val rOffset = 0
        val gOffset = planeSize
        val bOffset = planeSize * 2

        for (yy in 0 until tileH) {
            val srcRow = (y0 + yy) * srcWidth
            val rowBase = yy * tileW
            for (xx in 0 until tileW) {
                val color = srcPixels[srcRow + x0 + xx]
                val alpha = Color.alpha(color)
                val idx = rowBase + xx
                if (alpha == 255) {
                    data[rOffset + idx] = Color.red(color) / 255f
                    data[gOffset + idx] = Color.green(color) / 255f
                    data[bOffset + idx] = Color.blue(color) / 255f
                } else {
                    data[rOffset + idx] = compositeOnWhite(Color.red(color), alpha)
                    data[gOffset + idx] = compositeOnWhite(Color.green(color), alpha)
                    data[bOffset + idx] = compositeOnWhite(Color.blue(color), alpha)
                }
            }
        }
        return data
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
        srcOffsetX: Int,
        srcOffsetY: Int,
        destX: Int,
        destY: Int,
        coreW: Int,
        coreH: Int
    ) {
        val planeSize = outFloat.size / 3
        val dstW = coreW * SCALE
        val dstH = coreH * SCALE
        val pixels = IntArray(dstW * dstH)
        var p = 0
        for (dy in 0 until dstH) {
            val srcY = srcOffsetY + dy
            val rowBase = srcY * outW + srcOffsetX
            for (dx in 0 until dstW) {
                val sx = rowBase + dx
                pixels[p++] = Color.rgb(
                    clampToByte(outFloat[sx]),
                    clampToByte(outFloat[planeSize + sx]),
                    clampToByte(outFloat[planeSize * 2 + sx])
                )
            }
        }
        output.setPixels(pixels, 0, dstW, destX, destY, dstW, dstH)
    }

    private fun clampToByte(value: Float): Int {
        return (value.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
    }
}
