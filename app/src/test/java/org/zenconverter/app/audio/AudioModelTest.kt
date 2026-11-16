package org.zenconverter.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformAnalyzerTest {
    @Test
    fun peaksCaptureSilenceAndFullScale() {
        val samples = floatArrayOf(0f, 0f, 1f, -1f, 0.5f, -0.5f, 0f, 0f)
        val peaks = WaveformAnalyzer.peaksFromSamples(samples, channelCount = 1, sampleRateHz = 8, bucketCount = 64)
        assertEquals(64, peaks.bucketCount)
        assertEquals(8L, peaks.sampleCount)
        assertEquals(1.0, peaks.peakAmplitude.toDouble(), 1e-6)
    }

    @Test
    fun silentBufferIsReportedSilent() {
        val peaks = WaveformAnalyzer.peaksFromSamples(FloatArray(2048), channelCount = 2, sampleRateHz = 44_100)
        assertTrue(peaks.isSilent())
    }

    @Test
    fun bucketCountIsClamped() {
        val low = WaveformAnalyzer.peaksFromSamples(FloatArray(16), 1, 8, bucketCount = 1)
        val high = WaveformAnalyzer.peaksFromSamples(FloatArray(16), 1, 8, bucketCount = 1_000_000)
        assertEquals(WaveformAnalyzer.MIN_BUCKETS, low.bucketCount)
        assertEquals(WaveformAnalyzer.MAX_BUCKETS, high.bucketCount)
    }

    @Test
    fun pcm16ConvertsToNormalizedFloats() {
        val pcm = byteArrayOf(0x00, 0x40, 0x00, 0xC0.toByte()) // 16384, -16384
        val samples = WaveformAnalyzer.samplesFromPcm16(pcm, channelCount = 1)
        assertEquals(2, samples.size)
        assertEquals(0.5, samples[0].toDouble(), 1e-4)
        assertEquals(-0.5, samples[1].toDouble(), 1e-4)
    }

    @Test
    fun durationFollowsSampleRate() {
        val peaks = WaveformAnalyzer.peaksFromSamples(FloatArray(44_100), 1, 44_100)
        assertEquals(1.0, peaks.durationSeconds, 1e-9)
    }

    @Test
    fun emptyBufferHasNoAmplitude() {
        val peaks = WaveformAnalyzer.peaksFromSamples(FloatArray(0), 1, 44_100)
        assertEquals(0f, peaks.peakAmplitude)
        assertEquals(0L, peaks.sampleCount)
    }
}

class AudioAnalysisTest {
    @Test
    fun detectsTempoOfAPeriodicPulse() {
        val sampleRate = 8_000
        val samples = FloatArray(sampleRate * 4)
        val period = sampleRate / 2 // two beats per second
        var index = 0
        while (index < samples.size) {
            samples[index] = 0.9f
            index += period
        }
        val bpm = AudioAnalysis.estimateBpm(samples, sampleRate)
        assertTrue("expected a detected tempo, got $bpm", bpm != null)
        assertEquals(120.0, bpm!!, 12.0)
    }

    @Test
    fun silentInputYieldsNoTempo() {
        assertNull(AudioAnalysis.estimateBpm(FloatArray(8_000), 8_000))
    }

    @Test
    fun analyzeReturnsAKeyForTonalInput() {
        val sampleRate = 44_100
        val samples = FloatArray(sampleRate) { index ->
            (0.6 * kotlin.math.sin(2.0 * Math.PI * 440.0 * index / sampleRate)).toFloat()
        }
        val result = AudioAnalysis.analyze(samples, sampleRate, 1)
        assertEquals("A", result.key?.substringBefore(' '))
    }
}

class TranscriptEditingTest {
    @Test
    fun deletingWordsDropsTheirTime() {
        val words = listOf(
            TranscribedWord("one", 0.0, 1.0),
            TranscribedWord("two", 1.0, 2.0),
            TranscribedWord("three", 2.0, 3.0)
        )
        val plan = TranscriptEditing.plan(listOf(TranscriptSegment(0.0, 3.0, "one two three", words)), setOf(1))
        assertEquals(1.0, plan.removedSeconds, 1e-9)
        assertEquals("one three", plan.remainingText)
        assertEquals(2, plan.keepSegments.size)
    }

    @Test
    fun adjacentKeepSegmentsAreMerged() {
        val words = listOf(
            TranscribedWord("a", 0.0, 1.0),
            TranscribedWord("b", 1.0, 2.0)
        )
        val plan = TranscriptEditing.plan(listOf(TranscriptSegment(0.0, 2.0, "a b", words)), emptySet())
        assertEquals(1, plan.keepSegments.size)
        assertEquals(0.0, plan.keepSegments.first().startSeconds, 1e-9)
        assertEquals(2.0, plan.keepSegments.first().endSeconds, 1e-9)
    }

    @Test
    fun deletingEverythingLeavesNoSegments() {
        val words = listOf(TranscribedWord("a", 0.0, 1.0))
        val plan = TranscriptEditing.plan(listOf(TranscriptSegment(0.0, 1.0, "a", words)), setOf(0))
        assertTrue(plan.keepSegments.isEmpty())
        assertEquals("", plan.remainingText)
    }
}
