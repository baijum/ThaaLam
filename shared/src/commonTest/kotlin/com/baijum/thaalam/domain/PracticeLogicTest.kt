package com.baijum.thaalam.domain

import com.baijum.thaalam.data.MetronomeSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PracticeLogicTest {

    @Test
    fun `gap trainer plays then rests in a cycle`() {
        // 3 bars on, 1 bar off.
        assertFalse(PracticeLogic.isGapSilentBar(1, 3, 1))
        assertFalse(PracticeLogic.isGapSilentBar(2, 3, 1))
        assertFalse(PracticeLogic.isGapSilentBar(3, 3, 1))
        assertTrue(PracticeLogic.isGapSilentBar(4, 3, 1))
        assertFalse(PracticeLogic.isGapSilentBar(5, 3, 1))
        assertTrue(PracticeLogic.isGapSilentBar(8, 3, 1))
    }

    @Test
    fun `gap trainer is disabled without both values`() {
        assertFalse(PracticeLogic.isGapSilentBar(4, 0, 4))
        assertFalse(PracticeLogic.isGapSilentBar(4, 3, 0))
        assertFalse(PracticeLogic.isGapSilentBar(0, 3, 1))
    }

    @Test
    fun `protected first beat is never muted`() {
        assertFalse(
            PracticeLogic.shouldMuteTick(
                random = 0.0,
                probability = 1.0,
                isFirstBeatOfBar = true,
                protectFirstBeat = true,
                isGapSilent = false,
            ),
        )
    }

    @Test
    fun `unprotected first beat can be muted`() {
        assertTrue(
            PracticeLogic.shouldMuteTick(
                random = 0.0,
                probability = 1.0,
                isFirstBeatOfBar = true,
                protectFirstBeat = false,
                isGapSilent = false,
            ),
        )
    }

    @Test
    fun `stochastic muting follows the probability`() {
        assertFalse(
            PracticeLogic.shouldMuteTick(0.9, 0.2, false, true, false),
        )
        assertTrue(
            PracticeLogic.shouldMuteTick(0.1, 0.2, false, true, false),
        )
    }

    @Test
    fun `gap bars mute every tick regardless of probability`() {
        assertTrue(
            PracticeLogic.shouldMuteTick(0.99, 0.0, true, false, isGapSilent = true),
        )
    }

    @Test
    fun `stepped ramp only adjusts on the configured bar`() {
        assertFalse(PracticeLogic.shouldStepRamp(3, 4))
        assertTrue(PracticeLogic.shouldStepRamp(4, 4))
        assertEquals(102.0, TempoMath.rampStep(100.0, 120.0, 2.0))
        assertEquals(100.0, TempoMath.rampStep(100.0, 120.0, 0.0))
        assertEquals(120.0, TempoMath.rampStep(119.0, 120.0, 2.0))
    }

    @Test
    fun `ramped bpm advances once per configured bars`() {
        val settings = MetronomeSettings(
            bpm = 100.0,
            rampEnabled = true,
            rampTargetBpm = 110.0,
            rampStepBpm = 2.0,
            rampEveryBars = 4,
        )
        assertEquals(100.0, PracticeLogic.bpmForBar(settings, 1, false))
        assertEquals(100.0, PracticeLogic.bpmForBar(settings, 4, false))
        assertEquals(102.0, PracticeLogic.bpmForBar(settings, 5, false))
        assertEquals(110.0, PracticeLogic.bpmForBar(settings, 40, false))
    }

    @Test
    fun `ramped bpm helper matches bar gating`() {
        val settings = MetronomeSettings(
            bpm = 100.0,
            rampEnabled = true,
            rampTargetBpm = 110.0,
            rampStepBpm = 2.0,
            rampEveryBars = 4,
        )
        assertEquals(100.0, TempoMath.rampedBpm(settings, barIndex = 4))
        assertEquals(102.0, TempoMath.rampedBpm(settings, barIndex = 5))
        assertEquals(102.0, PracticeLogic.bpmForBar(settings, barIndex = 5, isCountIn = false))
    }

    @Test
    fun `count-in bars keep the base tempo`() {
        val settings = MetronomeSettings(
            bpm = 90.0,
            rampEnabled = true,
            rampTargetBpm = 140.0,
            rampEveryBars = 1,
        )
        assertEquals(90.0, PracticeLogic.bpmForBar(settings, 8, isCountIn = true))
    }

    @Test
    fun `lcg stays in range and changes`() {
        val random = PracticeLogic.Lcg(seed = 42L)
        val first = random.nextDouble()
        val second = random.nextDouble()
        assertTrue(first in 0.0..1.0)
        assertTrue(second in 0.0..1.0)
        assertTrue(first != second)
    }
}
