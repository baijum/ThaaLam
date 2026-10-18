package com.baijum.thaalam.domain

import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.MetronomeSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MetronomeStateLogicTest {

    @Test
    fun `beat kind cycles through four states`() {
        assertEquals(BeatKind.NORMAL, MetronomeStateLogic.cycleBeatType(BeatKind.ACCENT))
        assertEquals(BeatKind.GHOST, MetronomeStateLogic.cycleBeatType(BeatKind.NORMAL))
        assertEquals(BeatKind.MUTE, MetronomeStateLogic.cycleBeatType(BeatKind.GHOST))
        assertEquals(BeatKind.ACCENT, MetronomeStateLogic.cycleBeatType(BeatKind.MUTE))
    }

    @Test
    fun `bpm is clamped`() {
        assertEquals(10.0, MetronomeStateLogic.clampBpm(1.0))
        assertEquals(400.0, MetronomeStateLogic.clampBpm(999.0))
        assertEquals(120, MetronomeStateLogic.clampBpm(120))
    }

    @Test
    fun `group accents mark every group start`() {
        val pattern = MetronomeStateLogic.groupAccentPattern(8, listOf(3, 3, 2))
        assertEquals(
            listOf(
                BeatKind.ACCENT,
                BeatKind.NORMAL,
                BeatKind.NORMAL,
                BeatKind.ACCENT,
                BeatKind.NORMAL,
                BeatKind.NORMAL,
                BeatKind.ACCENT,
                BeatKind.NORMAL,
            ),
            pattern,
        )
    }

    @Test
    fun `pattern resize fills missing entries without overriding choices`() {
        val resized = MetronomeStateLogic.normalizeAccentPattern(
            beats = 6,
            pattern = listOf(BeatKind.NORMAL, BeatKind.MUTE),
            grouping = listOf(3, 3),
        )
        assertEquals(6, resized.size)
        assertEquals(BeatKind.NORMAL, resized[0])
        assertEquals(BeatKind.MUTE, resized[1])
        assertEquals(BeatKind.NORMAL, resized[5])
    }

    @Test
    fun `every pulse may be muted deliberately`() {
        val pattern = MetronomeStateLogic.normalizeAccentPattern(
            beats = 4,
            pattern = List(4) { BeatKind.MUTE },
            grouping = listOf(4),
        )
        assertEquals(List(4) { BeatKind.MUTE }, pattern)
    }

    @Test
    fun `settings normalize meter pattern and ranges`() {
        val settings = MetronomeSettings(
            bpm = 500.0,
            meter = com.baijum.thaalam.data.Meter(6, 8, listOf(5)),
            accentPattern = listOf(BeatKind.ACCENT),
            swingPercent = 99.0,
            volume = -1f,
            countInBars = 99,
            gapPlayBars = 999,
            rampEveryBars = 0,
        ).normalized()

        assertEquals(400.0, settings.bpm)
        assertEquals(listOf(3, 3), settings.meter.grouping)
        assertEquals(6, settings.accentPattern.size)
        assertEquals(75.0, settings.swingPercent)
        assertEquals(0f, settings.volume)
        assertEquals(4, settings.countInBars)
        assertEquals(64, settings.gapPlayBars)
        assertEquals(1, settings.rampEveryBars)
    }

    @Test
    fun `group accent helper sets a valid pattern`() {
        val settings = MetronomeSettings(
            meter = com.baijum.thaalam.data.Meter(7, 8, listOf(2, 2, 3)),
        ).withGroupAccents()
        assertEquals(listOf(0, 2, 4), settings.accentPattern.indices.filter { settings.accentPattern[it] == BeatKind.ACCENT })
    }

    @Test
    fun `audible count ignores muted beats`() {
        assertEquals(
            3,
            MetronomeStateLogic.audibleBeatCount(
                listOf(BeatKind.ACCENT, BeatKind.MUTE, BeatKind.NORMAL, BeatKind.GHOST),
            ),
        )
    }

    @Test
    fun `tempo markings cover the range`() {
        assertEquals("Largo", TempoMath.tempoMarking(50.0))
        assertEquals("Andante", TempoMath.tempoMarking(90.0))
        assertEquals("Allegro", TempoMath.tempoMarking(130.0))
        assertTrue(TempoMath.tempoMarking(220.0).isNotEmpty())
    }

    @Test
    fun `frame math is sample accurate`() {
        assertEquals(24_000.0, TempoMath.framesPerBeat(120.0, 48_000))
        assertEquals(12_000.0, TempoMath.framesPerTick(120.0, 2, 48_000))
        assertEquals(0.5, TempoMath.secondsPerBeat(120.0))
        assertEquals(2.0, TempoMath.barsDurationSeconds(1, 120.0, 4))
    }
}
