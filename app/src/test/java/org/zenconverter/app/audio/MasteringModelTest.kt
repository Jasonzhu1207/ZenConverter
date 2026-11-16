package org.zenconverter.app.audio

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MasteringModelTest {
    @Test
    fun defaultCurveHasTheSixNamedBandsInOrder() {
        val curve = EqBand.defaultCurve()
        assertEquals(MasteringBand.entries, curve.map { it.band })
        assertEquals(30.0, curve.first().hz, 0.001)
        assertEquals(9500.0, curve.last().hz, 0.001)
    }

    @Test
    fun highPassBandEmitsAHighpassFilterNotAnEqualizer() {
        val plan = MasteringPlan(bands = listOf(EqBand(MasteringBand.HighPass)))
        val filters = MasteringModel.filtersFor(plan)
        assertTrue(filters.first().startsWith("highpass=f=30"))
        assertFalse(filters.any { it.startsWith("equalizer=f=30:") })
    }

    @Test
    fun bellBandsUseTheirRealFrequencyNotTheIndex() {
        val plan = MasteringPlan(bands = EqBand.defaultCurve())
        val chain = MasteringModel.filterChain(plan)
        // The presence and air bands must carry their true centre frequency.
        assertTrue(chain.contains("equalizer=f=3200"))
        assertTrue(chain.contains("equalizer=f=9500"))
        assertFalse(chain.contains("equalizer=f=3:"))
        assertFalse(chain.contains("equalizer=f=9:"))
    }

    @Test
    fun disabledBandsAreLeftOutOfTheChain() {
        val curve = EqBand.defaultCurve().map {
            if (it.band == MasteringBand.MudCut) it.copy(enabled = false) else it
        }
        val chain = MasteringModel.filterChain(MasteringPlan(bands = curve))
        assertFalse(chain.contains("equalizer=f=320:"))
    }

    @Test
    fun chainEndsWithLoudnormSoTheCeilingIsGuaranteed() {
        val chain = MasteringModel.filterChain(MasteringPlan())
        assertTrue(chain.trim().endsWith("print_format=summary"))
        assertTrue(chain.contains("loudnorm=I=-14"))
        assertTrue(chain.contains("TP=-1"))
    }

    @Test
    fun measuredLoudnessTurnsOnTheLinearSecondPass() {
        val measured = LoudnessAnalyzer.Measurement(
            inputI = -20.5, inputTp = -2.1, inputLra = 8.3,
            inputThresh = -31.0, targetOffset = 0.2,
            samplePeakDb = -2.4, crestFactorDb = 12.0
        )
        val filter = MasteringModel.loudnormFilter(MasteringPlan(), measured)
        assertTrue(filter.contains("measured_I=-20.5"))
        assertTrue(filter.contains("linear=true"))
    }

    @Test
    fun singlePassHasNoMeasuredArguments() {
        val filter = MasteringModel.loudnormFilter(MasteringPlan(), null)
        assertFalse(filter.contains("measured_I"))
        assertTrue(filter.contains("print_format=summary"))
    }

    @Test
    fun commandShowsTheRealFiltersAndACodec() {
        val command = MasteringModel.commandFor(MasteringPlan())
        assertTrue(command.startsWith("-i in.wav -af \""))
        assertTrue(command.contains("highpass=f=30"))
        assertTrue(command.contains("-c:a pcm_s24le"))
        assertTrue(command.endsWith("out.wav"))
    }

    @Test
    fun balanceMovesEveryStemOntoTheTarget() {
        val measured = listOf(
            StemKind.Vocals to -18.2,
            StemKind.Drums to -17.4,
            StemKind.Bass to -19.0,
            StemKind.Other to -21.6
        )
        val balances = MasteringModel.balanceStems(measured, targetLufs = -18.0)
        assertEquals(4, balances.size)
        balances.forEach { assertEquals(-18.0, it.targetLufs, 0.001) }
    }

    @Test
    fun balanceTrimsAreClampedSoTheMixIsNotInverted() {
        val balances = MasteringModel.balanceStems(
            listOf(StemKind.Vocals to -40.0),
            targetLufs = -18.0,
            maxTrimDb = 6.0
        )
        assertEquals(6.0, balances.first().trimDb, 0.001)
    }

    @Test
    fun clippingReportIsFlaggedAndNotHealthy() {
        val report = MasteringModel.Report(
            integratedLufs = -14.0, truePeakDb = 0.8, samplePeakDb = 0.2,
            loudnessRangeLu = 5.0, crestFactorDb = 9.0,
            target = MasteringTarget.Streaming
        )
        assertTrue(report.clipping)
        assertEquals(MasteringModel.Verdict.Clipping, MasteringModel.verdictFor(report))
        assertFalse(report.healthy)
    }

    @Test
    fun overCompressedReportIsFlaggedEvenWithoutClipping() {
        val report = MasteringModel.Report(
            integratedLufs = -14.0, truePeakDb = -1.2, samplePeakDb = -1.4,
            loudnessRangeLu = 3.0, crestFactorDb = 4.5,
            target = MasteringTarget.Streaming
        )
        assertTrue(report.overCompressed)
        assertEquals(MasteringModel.Verdict.OverCompressed, MasteringModel.verdictFor(report))
    }

    @Test
    fun quietReportIsCalledTooQuiet() {
        val report = MasteringModel.Report(
            integratedLufs = -22.0, truePeakDb = -3.0, samplePeakDb = -3.2,
            loudnessRangeLu = 12.0, crestFactorDb = 14.0,
            target = MasteringTarget.Streaming
        )
        assertEquals(MasteringModel.Verdict.TooQuiet, MasteringModel.verdictFor(report))
    }

    @Test
    fun onTargetNonClippingReportIsHealthy() {
        val report = MasteringModel.Report(
            integratedLufs = -14.3, truePeakDb = -1.1, samplePeakDb = -1.3,
            loudnessRangeLu = 8.0, crestFactorDb = 11.0,
            target = MasteringTarget.Streaming
        )
        assertEquals(MasteringModel.Verdict.Healthy, MasteringModel.verdictFor(report))
        assertTrue(report.healthy)
    }

    @Test
    fun shapeFollowsTheCrestFactor() {
        fun report(crest: Double) = MasteringModel.Report(
            integratedLufs = -14.0, truePeakDb = -1.0, samplePeakDb = -1.2,
            loudnessRangeLu = 7.0, crestFactorDb = crest,
            target = MasteringTarget.Streaming
        )
        assertEquals(MasteringModel.WaveformShape.Thick, MasteringModel.shapeFor(report(5.0)))
        assertEquals(MasteringModel.WaveformShape.Dynamic, MasteringModel.shapeFor(report(11.0)))
        assertEquals(MasteringModel.WaveformShape.Sparse, MasteringModel.shapeFor(report(18.0)))
    }
}

class LoudnessAnalyzerTest {
    private val sampleRate = 48_000

    private fun sine(frequency: Double, amplitude: Double, seconds: Double): FloatArray {
        val count = (seconds * sampleRate).toInt()
        return FloatArray(count) { index ->
            (amplitude * sin(2.0 * PI * frequency * index / sampleRate)).toFloat()
        }
    }

    @Test
    fun silenceCannotBeGated() {
        assertNull(LoudnessAnalyzer.measure(FloatArray(sampleRate), sampleRate, 1))
    }

    @Test
    fun tooShortToFormABlockReturnsNull() {
        assertNull(LoudnessAnalyzer.measure(FloatArray(100), sampleRate, 1))
    }

    @Test
    fun aFullScaleSineReadsAboutMinusThreeLufs() {
        // A 997 Hz sine at full scale is the BS.1770 reference: -3.01 LUFS.
        val measurement = LoudnessAnalyzer.measure(sine(997.0, 0.999, 5.0), sampleRate, 1)
        assertNotNull(measurement)
        assertEquals(-3.01, measurement!!.inputI, 0.6)
    }

    @Test
    fun halvingTheAmplitudeDropsLoudnessByAboutSix() {
        val loud = LoudnessAnalyzer.measure(sine(997.0, 0.8, 5.0), sampleRate, 1)!!
        val quiet = LoudnessAnalyzer.measure(sine(997.0, 0.4, 5.0), sampleRate, 1)!!
        assertEquals(6.0, loud.inputI - quiet.inputI, 0.4)
    }

    @Test
    fun truePeakCatchesOvershootAboveTheSamplePeak() {
        // A sine at 0.99 is a worst case for inter-sample overshoot.
        val measurement = LoudnessAnalyzer.measure(sine(11_000.0, 0.99, 2.0), sampleRate, 1)!!
        assertTrue(measurement.inputTp >= measurement.samplePeakDb)
    }

    @Test
    fun reportCarriesTheTargetThrough() {
        val measurement = LoudnessAnalyzer.measure(sine(997.0, 0.7, 5.0), sampleRate, 1)!!
        val report = LoudnessAnalyzer.reportFor(measurement, MasteringTarget.Club)
        assertEquals(MasteringTarget.Club, report.target)
        assertEquals(-8.0, report.target.lufs, 0.001)
    }

    @Test
    fun stereoIsMeasuredLouderThanMonoForTheSameSignal() {
        val mono = sine(997.0, 0.5, 4.0)
        val stereo = FloatArray(mono.size * 2).also { output ->
            mono.indices.forEach { index ->
                output[index * 2] = mono[index]
                output[index * 2 + 1] = mono[index]
            }
        }
        val monoResult = LoudnessAnalyzer.measure(mono, sampleRate, 1)!!
        val stereoResult = LoudnessAnalyzer.measure(stereo, sampleRate, 2)!!
        assertEquals(3.0, stereoResult.inputI - monoResult.inputI, 0.5)
    }
}

class SpectrumAnalyzerTest {
    private val sampleRate = 48_000

    @Test
    fun aLowToneLandsInTheLowBands() {
        val samples = FloatArray(sampleRate) { index ->
            (0.8 * sin(2.0 * PI * 80.0 * index / sampleRate)).toFloat()
        }
        val balance = SpectrumAnalyzer.analyze(samples, sampleRate, 1)
        val lowIndex = SpectrumAnalyzer.BAND_CENTERS_HZ.indexOfFirst { it in 60.0..120.0 }
        val highIndex = SpectrumAnalyzer.BAND_CENTERS_HZ.indexOfFirst { it > 8000.0 }
        assertTrue(balance.bandsDb[lowIndex] > balance.bandsDb[highIndex])
    }

    @Test
    fun tooLittleSignalProducesAnEmptyBalance() {
        val balance = SpectrumAnalyzer.analyze(FloatArray(64), sampleRate, 1)
        assertEquals(0, balance.problemPeakIndices.size)
        assertTrue(balance.bandsDb.all { it == 0.0 })
    }

    @Test
    fun autoEqCutsTheBandThatStandsOut() {
        val bandsDb = DoubleArray(SpectrumAnalyzer.BAND_CENTERS_HZ.size)
        val mudIndex = SpectrumAnalyzer.BAND_CENTERS_HZ.indexOfFirst { it == 250.0 }
        bandsDb[mudIndex] = 9.0
        val balance = SpectrumAnalyzer.Balance(bandsDb, DoubleArray(bandsDb.size), emptyList())
        val gains = SpectrumAnalyzer.autoEqGains(balance)
        assertTrue(gains.getValue(MasteringBand.MudCut) < 0.0)
    }
}
