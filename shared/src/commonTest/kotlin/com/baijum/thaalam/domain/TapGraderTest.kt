package com.baijum.thaalam.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TapGraderTest {

    @Test
    fun `exact taps produce a tight centred summary`() {
        val grader = TapGrader()
        listOf(1_000.0, 1_500.0, 2_000.0).forEach { target ->
            grader.addTarget(target)
            assertEquals(0.0, grader.addTap(target))
        }
        val summary = assertNotNull(grader.summary())
        assertEquals(3, summary.tapCount)
        assertEquals(0.0, summary.meanOffsetMs)
        assertEquals(0.0, summary.meanAbsoluteOffsetMs)
        assertEquals(3, summary.onTimeCount)
        assertEquals("Tight", summary.rating)
        assertEquals("Centred", summary.tendency)
        assertEquals(3, summary.histogram.sum())
    }

    @Test
    fun `early and late taps are classified against the tolerance`() {
        val grader = TapGrader(toleranceMs = 10.0)
        grader.addTarget(1_000.0)
        grader.addTap(970.0) // 30 ms early
        grader.addTarget(2_000.0)
        grader.addTap(2_040.0) // 40 ms late
        grader.addTarget(3_000.0)
        grader.addTap(3_005.0) // within tolerance

        val summary = assertNotNull(grader.summary())
        assertEquals(1, summary.earlyCount)
        assertEquals(1, summary.lateCount)
        assertEquals(1, summary.onTimeCount)
        assertEquals(5.0, summary.meanOffsetMs, 0.001)
        assertEquals(25.0, summary.meanAbsoluteOffsetMs, 0.001)
        assertEquals("Steady", summary.rating)
        assertEquals("Centred", summary.tendency)
    }

    @Test
    fun `a tap far from any target is ignored`() {
        val grader = TapGrader(maxOffsetMs = 100.0)
        grader.addTarget(1_000.0)
        assertNull(grader.addTap(1_500.0))
        assertEquals(0, grader.tapCount())
        assertNull(grader.lastOffsetMs)
    }

    @Test
    fun `taps match the nearest unused target`() {
        val grader = TapGrader()
        grader.addTarget(1_000.0)
        grader.addTarget(1_500.0)
        assertEquals(-20.0, grader.addTap(1_480.0))
        assertEquals(20.0, grader.addTap(1_020.0))
        assertEquals(2, grader.tapCount())
    }

    @Test
    fun `summary buckets cover the full range and clamp outliers`() {
        val grader = TapGrader(maxOffsetMs = 300.0)
        grader.addTarget(1_000.0)
        grader.addTap(800.0) // -200 ms, clamped to the first bucket
        grader.addTarget(2_000.0)
        grader.addTap(2_200.0) // +200 ms, clamped to the last bucket
        val summary = assertNotNull(grader.summary())
        assertEquals(2, summary.histogram.sum())
        assertEquals(1, summary.histogram.first())
        assertEquals(1, summary.histogram.last())
    }

    @Test
    fun `reset clears targets offsets and the last reading`() {
        val grader = TapGrader()
        grader.addTarget(1_000.0)
        grader.addTap(1_000.0)
        grader.reset()
        assertEquals(0, grader.tapCount())
        assertNull(grader.summary())
        assertNull(grader.lastOffsetMs)
        assertNull(grader.addTap(2_000.0))
    }
}
