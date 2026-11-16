package org.zenconverter.app.conversion

/**
 * Pure geometry for the image resize option. Kept free of Android types so the
 * arithmetic can be unit tested off-device.
 */
object ImageResizer {

    /**
     * Target size for a [width] x [height] image under [mode]. Never upscales:
     * an image already within the cap is returned unchanged so a "resize" that
     * would enlarge the file is a no-op.
     */
    fun targetSize(width: Int, height: Int, mode: ImageResizeMode): Pair<Int, Int> {
        val cap = mode.maxLongEdgePx ?: return width to height
        val longEdge = maxOf(width, height)
        if (longEdge <= cap || longEdge == 0) return width to height

        val scale = cap.toDouble() / longEdge
        val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
        return scaledWidth to scaledHeight
    }

    /** Whether [mode] would actually shrink a [width] x [height] image. */
    fun shrinks(width: Int, height: Int, mode: ImageResizeMode): Boolean {
        val (targetWidth, targetHeight) = targetSize(width, height, mode)
        return targetWidth != width || targetHeight != height
    }
}
