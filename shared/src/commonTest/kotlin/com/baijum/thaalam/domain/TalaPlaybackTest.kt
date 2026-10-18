package com.baijum.thaalam.domain

import com.baijum.thaalam.data.AngaGesture
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.Subdivision
import com.baijum.thaalam.data.TalaPlayback
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TalaPlaybackTest {

    private fun settings(
        talaId: String = "carnatic.eka.4",
        bpm: Double = 120.0,
        nadai: Subdivision = Subdivision.QUARTER,
        countInBars: Int = 0,
        playback: TalaPlayback = TalaPlayback(),
    ): MetronomeSettings {
        val tala = TalaLibrary.find(talaId)!!
        return TalaApplication.apply(
            tala = tala,
            base = MetronomeSettings(bpm = bpm, countInBars = countInBars),
            nadai = nadai,
            playback = playback,
        )
    }

    @Test
    fun `normalization snaps and clamps tala playback values`() {
        val normalized = TalaPlayback(
            kalam = 3,
            kalai = 9,
            eduppuOffsetAksharas = -12.3,
        ).normalized()
        assertEquals(1, normalized.kalam)
        assertEquals(2, normalized.kalai)
        assertEquals(-8.0, normalized.eduppuOffsetAksharas)

        val snapped = TalaPlayback(eduppuOffsetAksharas = 0.31).normalized()
        assertEquals(0.25, snapped.eduppuOffsetAksharas)
    }

    @Test
    fun `eduppu labels describe sam atita and anagata`() {
        assertEquals("Sam", TalaPlayback.eduppuLabel(0.0))
        assertEquals("½ before sam", TalaPlayback.eduppuLabel(-0.5))
        assertEquals("1 before sam", TalaPlayback.eduppuLabel(-1.0))
        assertEquals("½ after sam", TalaPlayback.eduppuLabel(0.5))
        assertEquals("¼ after sam", TalaPlayback.eduppuLabel(0.25))
    }

    @Test
    fun `kalam scales the effective tempo and keeps the base tempo`() {
        val base = settings(playback = TalaPlayback(kalam = 1))
        val double = settings(playback = TalaPlayback(kalam = 2))
        val quadruple = settings(playback = TalaPlayback(kalam = 4))

        assertEquals(120.0, base.bpm)
        assertEquals(120.0, base.effectiveBpm)
        assertEquals(240.0, double.effectiveBpm)
        assertEquals(480.0, quadruple.effectiveBpm)
        assertEquals(120.0, double.bpm)
        assertEquals(double.bpm, double.normalized().bpm)
    }

    @Test
    fun `kalam change does not change the grid identity`() {
        val base = settings(playback = TalaPlayback(kalam = 1))
        val faster = settings(playback = TalaPlayback(kalam = 2))
        assertEquals(
            LayerCompiler.primaryGridKey(base),
            LayerCompiler.primaryGridKey(faster),
        )
        assertEquals(LayerCompiler.patternKey(base), LayerCompiler.patternKey(faster))
    }

    @Test
    fun `kalam scales simulated tick spacing`() {
        val settings = settings(
            bpm = 120.0,
            playback = TalaPlayback(kalam = 2),
        )
        val ticks = TimingSimulator.run(settings, totalFrames = 96_001)
        assertEquals(listOf(0L, 12_000L, 24_000L, 36_000L, 48_000L), ticks.take(5).map { it.frame })
        assertTrue(ticks.all { it.bpm == 240.0 })
    }

    @Test
    fun `kalai doubles the clicks inside each akshara`() {
        val one = settings(nadai = Subdivision.QUARTER, playback = TalaPlayback(kalai = 1))
        val two = settings(nadai = Subdivision.QUARTER, playback = TalaPlayback(kalai = 2))
        val oneVoice = LayerCompiler.compilePrimary(one)
        val twoVoice = LayerCompiler.compilePrimary(two)

        assertEquals(4, oneVoice.stepCount)
        assertEquals(8, twoVoice.stepCount)
        assertEquals(2, two.effectiveTicksPerBeat)
        assertEquals(listOf(0.0, 0.5, 1.0, 1.5), twoVoice.steps.take(4).map { it.offsetBeats })
        assertNotEquals(LayerCompiler.primaryGridKey(one), LayerCompiler.primaryGridKey(two))
    }

    @Test
    fun `kalai multiplies nadai`() {
        val settings = settings(nadai = Subdivision.TRIPLET, playback = TalaPlayback(kalai = 2))
        assertEquals(6, settings.effectiveTicksPerBeat)
        assertEquals(24, LayerCompiler.compilePrimary(settings).stepCount)
    }

    @Test
    fun `eduppu rotates the cycle and preserves pulse identity`() {
        val after = settings(playback = TalaPlayback(eduppuOffsetAksharas = 0.5))
        val voice = LayerCompiler.compilePrimary(after)
        assertEquals(0.5, voice.leadInBeats)
        assertEquals(listOf(0.5, 1.5, 2.5, 3.5), voice.steps.map { it.offsetBeats })
        assertEquals(listOf(1, 2, 3, 0), voice.steps.map { it.beat })

        val before = settings(playback = TalaPlayback(eduppuOffsetAksharas = -0.5))
        val beforeVoice = LayerCompiler.compilePrimary(before)
        assertEquals(0.5, beforeVoice.leadInBeats)
        assertEquals(listOf(0, 1, 2, 3), beforeVoice.steps.map { it.beat })
    }

    @Test
    fun `eduppu rotation keeps the cycle length`() {
        val settings = settings(playback = TalaPlayback(eduppuOffsetAksharas = 0.5))
        val voice = LayerCompiler.compilePrimary(settings)
        val total = voice.steps.indices.sumOf { voice.gapBeatsAfter(it) }
        assertTrue(abs(total - 4.0) < 1e-9, "cycle length was $total")
    }

    @Test
    fun `eduppu leaves a lead-in before the first click`() {
        val settings = settings(playback = TalaPlayback(eduppuOffsetAksharas = 0.5))
        val ticks = TimingSimulator.run(settings, totalFrames = 96_001)
        assertEquals(12_000L, ticks.first().frame)
        assertEquals(1, ticks.first().beat)
        assertEquals(84_000L, ticks.first { it.beat == 0 }.frame)
    }

    @Test
    fun `count-in ends just before the eduppu`() {
        val settings = settings(
            countInBars = 1,
            playback = TalaPlayback(eduppuOffsetAksharas = 0.5),
        )
        val ticks = TimingSimulator.run(settings, totalFrames = 200_000)
        val countIn = ticks.filter { it.isCountIn }
        val musical = ticks.filterNot { it.isCountIn }

        assertEquals(12_000L, countIn.first().frame)
        assertEquals(84_000L, countIn.last().frame)
        assertEquals(0, countIn.last().beat)
        assertEquals(108_000L, musical.first().frame)
        assertTrue(musical.first().frame > countIn.last().frame)
    }

    @Test
    fun `count-in and primary transition stays exact when kalai changes the lead-in`() {
        val settings = settings(
            countInBars = 1,
            playback = TalaPlayback(kalai = 2, eduppuOffsetAksharas = 0.5),
        )
        val ticks = TimingSimulator.run(settings, totalFrames = 160_000)
        val musical = ticks.filterNot { it.isCountIn }
        // The primary grid has a click exactly at the eduppu, so the musical
        // bar starts on the boundary (96,000) even though the quarter-note
        // count-in has a half-beat lead-in of its own.
        assertEquals(96_000L, musical.first().frame)
        assertFalse(musical.first().isCountIn)
    }

    @Test
    fun `tala playback is inert without an active tala`() {
        val plain = MetronomeSettings(
            bpm = 90.0,
            talaPlayback = TalaPlayback(kalam = 4, kalai = 2, eduppuOffsetAksharas = 1.0),
        ).normalized()
        assertEquals(TalaPlayback(), plain.talaPlayback)
        assertEquals(90.0, plain.effectiveBpm)
        assertEquals(1, plain.effectiveTicksPerBeat)
    }

    @Test
    fun `clearing a tala restores the base tempo`() {
        val active = settings(playback = TalaPlayback(kalam = 2, kalai = 2))
        val cleared = TalaApplication.clear(active)
        assertEquals(null, cleared.talaId)
        assertEquals(120.0, cleared.bpm)
        assertEquals(120.0, cleared.effectiveBpm)
        assertEquals(TalaPlayback(), cleared.talaPlayback)
    }

    @Test
    fun `with kalam changes playback without touching the grid or accents`() {
        val base = settings()
        val faster = TalaApplication.withKalam(base, 2)
        assertEquals(2, faster.talaPlayback.kalam)
        assertEquals(240.0, faster.effectiveBpm)
        assertEquals(base.accentPattern, faster.accentPattern)
        assertEquals(base.meter, faster.meter)
        assertEquals(LayerCompiler.primaryGridKey(base), LayerCompiler.primaryGridKey(faster))
    }

    @Test
    fun `kalam eduppu and kalai stay on the frame grid over a long run`() {
        val settings = settings(
            bpm = 123.4,
            playback = TalaPlayback(
                kalam = 4,
                kalai = 2,
                eduppuOffsetAksharas = 0.5,
            ),
        )
        val ticks = TimingSimulator.run(settings, totalFrames = 60L * 48_000L)
        val primary = ticks.filter { it.voiceId == VoicePattern.PRIMARY_ID }
        val framePerClick = 60.0 / (123.4 * 4.0) * 48_000.0 / 2.0
        assertTrue(primary.size > 100)
        primary.zipWithNext { a, b ->
            assertTrue(
                abs((b.frame - a.frame) - framePerClick) <= 1.0,
                "frame drift at ${a.frame}-${b.frame}",
            )
        }
        // The whole run is at the kalam-scaled tempo, and the first click is
        // still half an akshara after the transport starts (the eduppu phase
        // lands on a subdivision tick with 2-kalai).
        assertTrue(primary.all { it.bpm == 493.6 })
        assertEquals(0L, primary.first().frame)
    }

    @Test
    fun `carnatic gestures follow clap and finger counts`() {
        val misraChapu = TalaLibrary.find("carnatic.chapu.misra")!!
        assertEquals(
            listOf(
                AngaGesture.CLAP,
                AngaGesture.FINGER,
                AngaGesture.FINGER,
                AngaGesture.CLAP,
                AngaGesture.FINGER,
                AngaGesture.FINGER,
                AngaGesture.FINGER,
            ),
            misraChapu.gestures,
        )
        val ekaChatushra = TalaLibrary.find("carnatic.eka.4")!!
        assertEquals(
            listOf(
                AngaGesture.CLAP,
                AngaGesture.FINGER,
                AngaGesture.FINGER,
                AngaGesture.FINGER,
            ),
            ekaChatushra.gestures,
        )
        val jhampa = TalaLibrary.find("carnatic.jhampa.4")!!
        assertEquals(AngaGesture.CLAP, jhampa.gestureAt(0))
        assertEquals(AngaGesture.FINGER, jhampa.gestureAt(1))
        assertEquals(AngaGesture.CLAP, jhampa.gestureAt(4))
        assertEquals(AngaGesture.CLAP, jhampa.gestureAt(5))
        assertEquals(AngaGesture.WAVE, jhampa.gestureAt(6))
    }

    @Test
    fun `hindustani gestures mark tali and khali only at vibhag starts`() {
        val teental = TalaLibrary.find("hindustani.teental")!!
        assertEquals(AngaGesture.TALI, teental.gestureAt(0))
        assertEquals(AngaGesture.TALI, teental.gestureAt(4))
        assertEquals(AngaGesture.KHALI, teental.gestureAt(8))
        assertEquals(AngaGesture.TALI, teental.gestureAt(12))
        assertEquals(AngaGesture.NONE, teental.gestureAt(1))
    }

    @Test
    fun `gestures reach the compiled primary voice and simulated ticks`() {
        val settings = settings(talaId = "carnatic.chapu.misra")
        val voice = LayerCompiler.compilePrimary(settings)
        assertEquals(AngaGesture.CLAP, voice.steps.first().gesture)
        assertEquals(AngaGesture.FINGER, voice.steps[1].gesture)
        val ticks = TimingSimulator.run(settings, totalFrames = 24_001)
        assertEquals(AngaGesture.CLAP, ticks.first().gesture)
        assertEquals(AngaGesture.FINGER, ticks[1].gesture)
    }
}
