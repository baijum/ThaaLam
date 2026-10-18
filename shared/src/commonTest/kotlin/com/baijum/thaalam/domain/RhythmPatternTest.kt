package com.baijum.thaalam.domain

import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.Meter
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.Subdivision
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RhythmPatternTest {

    private fun settings(
        meter: Meter = Meter(4, 4, listOf(4)),
        subdivision: Subdivision = Subdivision.QUARTER,
        pattern: List<BeatKind> = listOf(
            BeatKind.ACCENT,
            BeatKind.NORMAL,
            BeatKind.NORMAL,
            BeatKind.NORMAL,
        ),
        swing: Double = 50.0,
    ) = MetronomeSettings(
        meter = meter,
        subdivision = subdivision,
        accentPattern = pattern,
        swingPercent = swing,
    )

    @Test
    fun `quarter notes in 4-4 produce four steps`() {
        val pattern = RhythmPattern.build(settings())
        assertEquals(4, pattern.steps.size)
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0), pattern.steps.map { it.offsetBeats })
        assertEquals(BeatKind.ACCENT, pattern.steps[0].kind)
        assertEquals(BeatKind.NORMAL, pattern.steps[1].kind)
        assertTrue(pattern.steps.all { it.isMainBeat })
    }

    @Test
    fun `eighth notes double the click grid`() {
        val pattern = RhythmPattern.build(settings(subdivision = Subdivision.EIGHTH))
        assertEquals(8, pattern.steps.size)
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3), pattern.steps.map { it.beat })
        assertEquals(listOf(0, 1, 0, 1, 0, 1, 0, 1), pattern.steps.map { it.sub })
    }

    @Test
    fun `triplets divide each pulse into three`() {
        val pattern = RhythmPattern.build(settings(subdivision = Subdivision.TRIPLET))
        assertEquals(12, pattern.steps.size)
        pattern.steps.take(3).forEachIndexed { index, step ->
            assertTrue(abs(step.offsetBeats - index / 3.0) < 1e-9)
        }
    }

    @Test
    fun `swing delays the offbeat eighth`() {
        val straight = RhythmPattern.build(settings(subdivision = Subdivision.EIGHTH))
        val swung = RhythmPattern.build(
            settings(subdivision = Subdivision.EIGHTH, swing = 66.6667),
        )
        assertTrue(abs(swung.steps[1].offsetBeats - 2.0 / 3.0) < 1e-3)
        assertTrue(swung.steps[1].offsetBeats > straight.steps[1].offsetBeats)
        // Beat positions themselves are unchanged.
        assertEquals(swung.steps[0].offsetBeats, straight.steps[0].offsetBeats)
        assertEquals(swung.steps[2].offsetBeats, straight.steps[2].offsetBeats)
    }

    @Test
    fun `grouping accents the start of each group`() {
        val pattern = RhythmPattern.build(
            settings(
                meter = Meter(7, 8, listOf(2, 2, 3)),
                subdivision = Subdivision.EIGHTH,
                pattern = MetronomeStateLogic.groupAccentPattern(7, listOf(2, 2, 3)),
            ),
        )
        val mainBeats = pattern.steps.filter { it.isMainBeat }
        assertEquals(7, mainBeats.size)
        assertEquals(listOf(0, 2, 4), mainBeats.filter { it.kind == BeatKind.ACCENT }.map { it.beat })
    }

    @Test
    fun `gaps between steps add up to one bar`() {
        val pattern = RhythmPattern.build(
            settings(subdivision = Subdivision.EIGHTH, swing = 62.5),
        )
        val total = pattern.steps.indices.sumOf { pattern.gapBeatsAfter(it) }
        assertTrue(abs(total - 4.0) < 1e-9)
    }

    @Test
    fun `muted pulses still occupy the grid`() {
        val pattern = RhythmPattern.build(
            settings(
                pattern = listOf(BeatKind.ACCENT, BeatKind.MUTE, BeatKind.NORMAL, BeatKind.GHOST),
            ),
        )
        assertEquals(4, pattern.steps.size)
        assertEquals(BeatKind.MUTE, pattern.steps[1].kind)
        assertEquals(BeatKind.GHOST, pattern.steps[3].kind)
    }

    @Test
    fun `septuplets are supported`() {
        val pattern = RhythmPattern.build(settings(subdivision = Subdivision.SEPTUPLET))
        assertEquals(28, pattern.steps.size)
        assertEquals(7, pattern.ticksPerBeat)
    }
}
