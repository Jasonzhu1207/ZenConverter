package org.zenconverter.app.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageResizerTest {

    @Test
    fun originalModeKeepsSize() {
        assertEquals(4000 to 3000, ImageResizer.targetSize(4000, 3000, ImageResizeMode.Original))
    }

    @Test
    fun capsLongEdgeAndPreservesAspectRatio() {
        // 4000x3000 with a 1920 long-edge cap -> 1920x1440.
        assertEquals(1920 to 1440, ImageResizer.targetSize(4000, 3000, ImageResizeMode.Px1920))
    }

    @Test
    fun portraitImageCapsHeight() {
        assertEquals(1440 to 1920, ImageResizer.targetSize(3000, 4000, ImageResizeMode.Px1920))
    }

    @Test
    fun neverUpscales() {
        assertEquals(640 to 480, ImageResizer.targetSize(640, 480, ImageResizeMode.Px1920))
        assertFalse(ImageResizer.shrinks(640, 480, ImageResizeMode.Px1920))
    }

    @Test
    fun exactCapIsUnchanged() {
        assertEquals(1920 to 1080, ImageResizer.targetSize(1920, 1080, ImageResizeMode.Px1920))
        assertFalse(ImageResizer.shrinks(1920, 1080, ImageResizeMode.Px1920))
    }

    @Test
    fun shrinksReportsWhenSmaller() {
        assertTrue(ImageResizer.shrinks(3840, 2160, ImageResizeMode.Px1920))
    }

    @Test
    fun degenerateZeroSizeIsUntouched() {
        assertEquals(0 to 0, ImageResizer.targetSize(0, 0, ImageResizeMode.Px720))
    }

    @Test
    fun everyCapProducesAtMostTheCap() {
        for (mode in ImageResizeMode.entries) {
            val (width, height) = ImageResizer.targetSize(8000, 6000, mode)
            val cap = mode.maxLongEdgePx
            if (cap != null) {
                assertTrue("$mode exceeded cap", maxOf(width, height) <= cap)
            }
        }
    }
}
