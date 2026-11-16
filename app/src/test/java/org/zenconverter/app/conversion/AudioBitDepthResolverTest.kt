package org.zenconverter.app.conversion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioBitDepthResolverTest {

    @Test
    fun autoLeavesFormatUntouched() {
        assertNull(AudioBitDepthResolver.sampleFormat(AudioBitDepth.Auto))
        assertNull(AudioBitDepthResolver.flacBitsPerRawSample(AudioBitDepth.Auto))
    }

    @Test
    fun wavPicksTheMatchingPcmEncoder() {
        assertEquals("pcm_s16le", AudioBitDepthResolver.wavEncoder(AudioBitDepth.Bit16))
        assertEquals("pcm_s24le", AudioBitDepthResolver.wavEncoder(AudioBitDepth.Bit24))
        assertEquals("pcm_s32le", AudioBitDepthResolver.wavEncoder(AudioBitDepth.Bit32))
        assertEquals("pcm_s16le", AudioBitDepthResolver.wavEncoder(AudioBitDepth.Auto))
    }

    @Test
    fun flacClampsThirtyTwoBitToTwentyFour() {
        assertEquals(16, AudioBitDepthResolver.flacBitsPerRawSample(AudioBitDepth.Bit16))
        assertEquals(24, AudioBitDepthResolver.flacBitsPerRawSample(AudioBitDepth.Bit24))
        assertEquals(24, AudioBitDepthResolver.flacBitsPerRawSample(AudioBitDepth.Bit32))
    }

    @Test
    fun sampleFormatMapsIntegerDepths() {
        assertEquals("s16", AudioBitDepthResolver.sampleFormat(AudioBitDepth.Bit16))
        assertEquals("s32", AudioBitDepthResolver.sampleFormat(AudioBitDepth.Bit24))
        assertEquals("s32", AudioBitDepthResolver.sampleFormat(AudioBitDepth.Bit32))
    }
}
