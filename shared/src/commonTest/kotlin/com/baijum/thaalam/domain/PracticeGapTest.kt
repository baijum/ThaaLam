package com.baijum.thaalam.domain

import com.baijum.thaalam.data.LayerSettings
import com.baijum.thaalam.data.MetronomeSettings
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PracticeGapTest {

    private fun settings(
        pattern: List<Boolean> = emptyList(),
        playBars: Int = 0,
        silentBars: Int = 0,
        fadeBars: Int = 0,
        keepSam: Boolean = true,
        layers: List<LayerSettings> = emptyList(),
    ): MetronomeSettings = MetronomeSettings(
        bpm = 120.0,
        countInBars = 0,
        gapPlayBars = playBars,
        gapSilentBars = silentBars,
        gapPattern = pattern,
        gapFadeBars = fadeBars,
        gapKeepSam = keepSam,
        layers = layers,
    ).normalized()

    @Test
    fun `legacy play and silent settings still form a cycle`() {
        val settings = settings(playBars = 2, silentBars = 1)
        assertEquals(listOf(true, true, false), PracticeLogic.gapCycle(settings))
        assertFalse(PracticeLogic.isGapSilentBar(1, settings))
        assertFalse(PracticeLogic.isGapSilentBar(2, settings))
        assertTrue(PracticeLogic.isGapSilentBar(3, settings))
        assertFalse(PracticeLogic.isGapSilentBar(4, settings))
    }

    @Test
    fun `custom pattern repeats and constrains silent runs`() {
        val settings = settings(pattern = listOf(true, false, false, false, true), keepSam = false)
        assertEquals(4, settings.gapMaxSilentBars)
        assertEquals(listOf(true, false, false, false, true), settings.gapPattern)
        assertFalse(PracticeLogic.isGapSilentBar(1, settings))
        assertTrue(PracticeLogic.isGapSilentBar(2, settings))
        assertTrue(PracticeLogic.isGapSilentBar(4, settings))
        assertFalse(PracticeLogic.isGapSilentBar(5, settings))
        assertFalse(PracticeLogic.isGapSilentBar(6, settings))
    }

    @Test
    fun `normalization repairs an overlong silent run`() {
        val repaired = PracticeLogic.normalizeGapPattern(
            pattern = listOf(true, false, false, false, false, false, true),
            maxSilentBars = 2,
        )
        assertEquals(listOf(true, false, false, true, false, false, true), repaired)
    }

    @Test
    fun `an all-silent custom pattern is repaired`() {
        val repaired = PracticeLogic.normalizeGapPattern(
            pattern = listOf(false, false, false),
            maxSilentBars = 8,
        )
        assertEquals(listOf(true, false, false), repaired)
    }

    @Test
    fun `fade reduces the last play bars before a gap`() {
        val settings = settings(pattern = listOf(true, true, false), fadeBars = 2)
        assertEquals(2.0 / 3.0, PracticeLogic.gapGainForBar(1, settings).toDouble(), 0.001)
        assertEquals(1.0 / 3.0, PracticeLogic.gapGainForBar(2, settings).toDouble(), 0.001)
        assertEquals(0.0, PracticeLogic.gapGainForBar(3, settings).toDouble(), 0.001)
        assertEquals(2.0 / 3.0, PracticeLogic.gapGainForBar(4, settings).toDouble(), 0.001)
    }

    @Test
    fun `fade is inert when the pattern never reaches a gap`() {
        val settings = settings(pattern = listOf(true, true, true), fadeBars = 3)
        assertEquals(1.0, PracticeLogic.gapGainForBar(1, settings).toDouble(), 0.001)
        assertEquals(1.0, PracticeLogic.gapGainForBar(2, settings).toDouble(), 0.001)
    }

    @Test
    fun `gap bars mute every voice while the clock advances`() {
        val settings = settings(
            pattern = listOf(true, false),
            keepSam = false,
            layers = listOf(
                LayerSettings(
                    id = "layer",
                    pulses = 5,
                    cycleBeats = 4.0,
                    accentPattern = LayerSettings.defaultAccentPattern(5),
                ),
            ),
        )
        val ticks = TimingSimulator.run(settings, totalFrames = 192_001)
        val firstBar = ticks.filter { it.bar == 1L }
        val secondBar = ticks.filter { it.bar == 2L }
        assertTrue(firstBar.isNotEmpty())
        assertTrue(firstBar.all { it.audible || !it.kind.isAudible })
        assertTrue(secondBar.isNotEmpty())
        assertTrue(secondBar.none { it.audible })
    }

    @Test
    fun `protected sam is audible on a gap bar at reduced gain`() {
        val settings = settings(pattern = listOf(true, false), keepSam = true)
        val ticks = TimingSimulator.run(settings, totalFrames = 192_001)
        val secondBar = ticks.filter { it.bar == 2L }
        val sam = secondBar.first { it.beat == 0 && it.pulse == 0 }
        assertTrue(sam.audible)
        assertEquals(PracticeLogic.GAP_SAM_GAIN.toDouble(), sam.gain, 0.001)
        assertTrue(secondBar.filter { it.beat != 0 }.none { it.audible })
    }

    @Test
    fun `fade gain reaches simulated ticks and re-entry is on a downbeat`() {
        val settings = settings(
            pattern = listOf(true, true, false),
            fadeBars = 2,
            keepSam = false,
        )
        val ticks = TimingSimulator.run(settings, totalFrames = 400_000)
        val barOne = ticks.filter { it.bar == 1L }
        val barTwo = ticks.filter { it.bar == 2L }
        val barThree = ticks.filter { it.bar == 3L }
        val barFour = ticks.filter { it.bar == 4L }
        assertEquals(2.0 / 3.0, barOne.first().gain, 0.001)
        assertEquals(1.0 / 3.0, barTwo.first().gain, 0.001)
        assertTrue(barThree.none { it.audible })
        val reentry = barFour.first { it.audible }
        assertEquals(0, reentry.beat)
        // Bar 4 starts the next cycle and is two bars before the next gap, so
        // the fade is already shaping it.
        assertEquals(2.0 / 3.0, reentry.gain, 0.001)
    }

    @Test
    fun `a thirty minute gap run does not drift`() {
        val settings = settings(pattern = listOf(true, true, false), fadeBars = 1)
        val ticks = TimingSimulator.run(settings, totalFrames = 30L * 60L * 48_000L)
        val primary = ticks.filter { it.voiceId == VoicePattern.PRIMARY_ID }
        val interval = 60.0 / 120.0 * 48_000.0
        primary.zipWithNext { a, b ->
            assertTrue(abs((b.frame - a.frame) - interval) <= 1.0, "drift at ${a.frame}-${b.frame}")
        }
    }
}
