package com.baijum.thaalam.domain

import com.baijum.thaalam.data.LayerSettings
import com.baijum.thaalam.data.MetronomeSettings
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimingSimulatorTest {

    private fun layer(
        pulses: Int,
        cycleBeats: Double,
        offsetMs: Double = 0.0,
    ) = LayerSettings(
        id = "test-layer",
        name = "Test layer",
        pulses = pulses,
        cycleBeats = cycleBeats,
        accentPattern = LayerSettings.defaultAccentPattern(pulses),
        gain = 1f,
        offsetMs = offsetMs,
    )

    @Test
    fun `straight quarter notes land on exact frame multiples`() {
        val settings = MetronomeSettings(
            bpm = 120.0,
            countInBars = 0,
        )
        val ticks = TimingSimulator.run(settings, totalFrames = 96_000)
        assertEquals(listOf(0L, 24_000L, 48_000L, 72_000L), ticks.map { it.frame })
        assertTrue(ticks.all { it.voiceId == VoicePattern.PRIMARY_ID })
    }

    @Test
    fun `five over four layer interleaves with the primary voice`() {
        val settings = MetronomeSettings(
            bpm = 120.0,
            countInBars = 0,
            layers = listOf(layer(pulses = 5, cycleBeats = 4.0)),
        )
        val ticks = TimingSimulator.run(settings, totalFrames = 96_000)
        val layerTicks = ticks.filter { it.voiceId == "test-layer" }
        assertEquals(listOf(0L, 19_200L, 38_400L, 57_600L, 76_800L), layerTicks.map { it.frame })
        val primaryFrames = ticks.filter { it.voiceId == VoicePattern.PRIMARY_ID }.map { it.frame }
        assertEquals(listOf(0L, 24_000L, 48_000L, 72_000L), primaryFrames)
    }


    @Test
    fun `per-step offset shifts only that layer pulse`() {
        val settings = MetronomeSettings(
            bpm = 120.0,
            countInBars = 0,
            layers = listOf(
                layer(pulses = 5, cycleBeats = 4.0).copy(
                    stepOffsetsMs = listOf(0.0, 10.0, 0.0, 0.0, 0.0),
                ),
            ),
        )
        val layerFrames = TimingSimulator.run(settings, totalFrames = 96_000)
            .filter { it.voiceId == "test-layer" }
            .map { it.frame }
        assertEquals(listOf(0L, 19_680L, 38_400L, 57_600L, 76_800L), layerFrames)
    }

    @Test
    fun `layer offset delays every layer tick`() {
        val settings = MetronomeSettings(
            bpm = 120.0,
            countInBars = 0,
            layers = listOf(layer(pulses = 5, cycleBeats = 4.0, offsetMs = 10.0)),
        )
        val firstLayer = TimingSimulator.run(settings, totalFrames = 96_000)
            .first { it.voiceId == "test-layer" }
        assertEquals(480L, firstLayer.frame)
    }

    @Test
    fun `layers are silent during count-in but keep their phase`() {
        val settings = MetronomeSettings(
            bpm = 120.0,
            countInBars = 1,
            layers = listOf(layer(pulses = 5, cycleBeats = 4.0)),
        )
        val ticks = TimingSimulator.run(settings, totalFrames = 192_001)
        val countIn = ticks.filter { it.isCountIn }
        assertFalse(countIn.any { it.voiceId == "test-layer" && it.audible })
        val firstMusicalLayer = ticks.first {
            !it.isCountIn && it.voiceId == "test-layer" && it.audible
        }
        assertEquals(96_000L, firstMusicalLayer.frame)
    }

    @Test
    fun `gap bars mute every voice while the clock advances`() {
        val settings = MetronomeSettings(
            bpm = 120.0,
            countInBars = 0,
            gapPlayBars = 1,
            gapSilentBars = 1,
            layers = listOf(layer(pulses = 5, cycleBeats = 4.0)),
        )
        val ticks = TimingSimulator.run(settings, totalFrames = 192_001)
        val secondBar = ticks.filter { it.bar == 2L }
        assertTrue(secondBar.isNotEmpty())
        assertFalse(secondBar.any { it.audible })
    }

    @Test
    fun `stepped ramp changes the next bar tempo`() {
        val settings = MetronomeSettings(
            bpm = 100.0,
            countInBars = 0,
            rampEnabled = true,
            rampTargetBpm = 120.0,
            rampStepBpm = 20.0,
            rampEveryBars = 1,
        )
        val ticks = TimingSimulator.run(settings, totalFrames = 240_001)
        val barOne = ticks.filter { it.bar == 1L }
        val barTwo = ticks.filter { it.bar == 2L }
        assertTrue(barOne.all { it.bpm == 100.0 })
        assertTrue(barTwo.isNotEmpty())
        assertTrue(barTwo.all { it.bpm == 120.0 })
    }

    @Test
    fun `five over four stays within one sample over thirty minutes`() {
        val settings = MetronomeSettings(
            bpm = 123.4,
            countInBars = 0,
            layers = listOf(layer(pulses = 5, cycleBeats = 4.0)),
        )
        val ticks = TimingSimulator.run(settings, totalFrames = 30L * 60L * 48_000L)
        val primary = ticks.filter { it.voiceId == VoicePattern.PRIMARY_ID }.map { it.frame }
        val layerTicks = ticks.filter { it.voiceId == "test-layer" }.map { it.frame }
        val primaryInterval = 60.0 / 123.4 * 48_000.0
        val layerInterval = primaryInterval * 4.0 / 5.0
        primary.zipWithNext { a, b ->
            assertTrue(abs((b - a) - primaryInterval) <= 1.0, "primary drift at $a-$b")
        }
        layerTicks.zipWithNext { a, b ->
            assertTrue(abs((b - a) - layerInterval) <= 1.0, "layer drift at $a-$b")
        }
    }
}
