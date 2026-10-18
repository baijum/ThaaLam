package com.baijum.thaalam.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TapTempoTest {

    @Test
    fun `steady taps produce the expected tempo`() {
        val tapTempo = TapTempo()
        tapTempo.tap(0)
        tapTempo.tap(500)
        val result = tapTempo.tap(1000)
        assertNotNull(result.bpm)
        assertTrue(abs(result.bpm - 120.0) < 1e-6)
        assertEquals(3, result.tapCount)
        assertTrue(result.stability != null && result.stability > 0.99)
    }

    @Test
    fun `first tap has no tempo`() {
        val result = TapTempo().tap(100)
        assertNull(result.bpm)
        assertEquals(1, result.tapCount)
    }

    @Test
    fun `a long pause starts a new session`() {
        val tapTempo = TapTempo(resetGapMs = 2000)
        tapTempo.tap(0)
        tapTempo.tap(500)
        val result = tapTempo.tap(10_000)
        assertTrue(result.didReset)
        assertEquals(1, result.tapCount)
        assertNull(result.bpm)
    }

    @Test
    fun `tempo is clamped to the supported range`() {
        val tapTempo = TapTempo()
        tapTempo.tap(0)
        tapTempo.tap(10) // 6000 BPM raw
        assertTrue(tapTempo.tap(20).bpm!! <= 400.0)
    }

    @Test
    fun `only the most recent taps are averaged`() {
        val tapTempo = TapTempo(maxTaps = 4)
        tapTempo.tap(0)
        tapTempo.tap(1000)
        tapTempo.tap(1500)
        tapTempo.tap(2000)
        val result = tapTempo.tap(2500)
        // The 1000 ms first interval has rolled out; the rest are 500 ms.
        val bpm = result.bpm
        assertNotNull(bpm)
        assertTrue(abs(bpm - 120.0) < 1e-6)
    }

    @Test
    fun `out of order taps are ignored`() {
        val tapTempo = TapTempo()
        tapTempo.tap(1000)
        tapTempo.tap(500)
        assertEquals(1, tapTempo.tapCount)
    }
}
