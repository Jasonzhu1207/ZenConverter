package org.zenconverter.app.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoEncoderSelectionTest {

    @Test
    fun prefersHardwareWhenTheBinaryReportsIt() {
        val choice = VideoEncoderSelection.select(
            codec = VideoEncoderSelection.Codec.H264,
            preference = VideoEncoderSelection.Preference.HardwareWhenAvailable
        ) { encoder -> encoder == VideoEncoderSelection.HARDWARE_H264 }

        assertEquals(VideoEncoderSelection.HARDWARE_H264, choice.codec)
        assertTrue(choice.usesTargetBitrate)
        assertTrue(choice.isHardware)
    }

    @Test
    fun fallsBackToSoftwareWhenHardwareIsAbsent() {
        // The shipped AAR has no MediaCodec encoders, which is the real case.
        val choice = VideoEncoderSelection.select(
            codec = VideoEncoderSelection.Codec.H264,
            preference = VideoEncoderSelection.Preference.HardwareWhenAvailable
        ) { false }

        assertEquals(VideoEncoderSelection.SOFTWARE_H264, choice.codec)
        assertFalse(choice.usesTargetBitrate)
        assertFalse(choice.isHardware)
    }

    @Test
    fun unknownAvailabilityDoesNotSelectHardware() {
        val choice = VideoEncoderSelection.select(
            codec = VideoEncoderSelection.Codec.H264,
            preference = VideoEncoderSelection.Preference.HardwareWhenAvailable
        ) { null }

        assertEquals(VideoEncoderSelection.SOFTWARE_H264, choice.codec)
        assertFalse(choice.isHardware)
    }

    @Test
    fun softwarePreferenceNeverSelectsHardware() {
        val choice = VideoEncoderSelection.select(
            codec = VideoEncoderSelection.Codec.H264,
            preference = VideoEncoderSelection.Preference.Software
        ) { true }

        assertEquals(VideoEncoderSelection.SOFTWARE_H264, choice.codec)
        assertFalse(choice.isHardware)
    }

    @Test
    fun hevcUsesThePlatformHevcEncoder() {
        val choice = VideoEncoderSelection.select(
            codec = VideoEncoderSelection.Codec.H265,
            preference = VideoEncoderSelection.Preference.HardwareWhenAvailable
        ) { encoder -> encoder == VideoEncoderSelection.HARDWARE_H265 }

        assertEquals(VideoEncoderSelection.HARDWARE_H265, choice.codec)
        assertTrue(choice.isHardware)
    }

    @Test
    fun vp9AndVp8HaveNoHardwareEncoder() {
        assertNull(VideoEncoderSelection.hardwareCodecFor(VideoEncoderSelection.Codec.Vp9))
        assertNull(VideoEncoderSelection.hardwareCodecFor(VideoEncoderSelection.Codec.Vp8))

        val choice = VideoEncoderSelection.select(
            codec = VideoEncoderSelection.Codec.Vp9,
            preference = VideoEncoderSelection.Preference.HardwareWhenAvailable
        ) { true }

        assertEquals("libvpx-vp9", choice.codec)
        assertFalse(choice.isHardware)
    }

    @Test
    fun fallbackBitrateScalesWithResolution() {
        val sd = VideoEncoderSelection.fallbackBitrateFor(
            width = 640,
            height = 480,
            frameRate = 30f,
            compressionMode = VideoCompressionMode.Standard
        )
        val fullHd = VideoEncoderSelection.fallbackBitrateFor(
            width = 1920,
            height = 1080,
            frameRate = 30f,
            compressionMode = VideoCompressionMode.Standard
        )
        assertTrue("full HD budget must exceed SD", fullHd > sd)
    }

    @Test
    fun fallbackBitrateShrinksForSmallFileMode() {
        val standard = VideoEncoderSelection.fallbackBitrateFor(
            width = 1920,
            height = 1080,
            frameRate = 30f,
            compressionMode = VideoCompressionMode.Standard
        )
        val small = VideoEncoderSelection.fallbackBitrateFor(
            width = 1920,
            height = 1080,
            frameRate = 30f,
            compressionMode = VideoCompressionMode.SmallFile
        )
        assertTrue("small-file mode must target fewer bits", small < standard)
    }

    @Test
    fun fallbackBitrateStaysWithinClampBounds() {
        val tiny = VideoEncoderSelection.fallbackBitrateFor(
            width = 16,
            height = 16,
            frameRate = 1f,
            compressionMode = VideoCompressionMode.SmallFile
        )
        assertTrue(tiny >= 500_000)

        val huge = VideoEncoderSelection.fallbackBitrateFor(
            width = 7680,
            height = 4320,
            frameRate = 120f,
            compressionMode = VideoCompressionMode.VisualLossless
        )
        assertTrue(huge <= 40_000_000)
    }

    @Test
    fun missingDimensionsStillProduceAPlausibleBitrate() {
        val bitrate = VideoEncoderSelection.fallbackBitrateFor(
            width = null,
            height = null,
            frameRate = null,
            compressionMode = VideoCompressionMode.Standard
        )
        assertTrue(bitrate >= 500_000)
    }
}
