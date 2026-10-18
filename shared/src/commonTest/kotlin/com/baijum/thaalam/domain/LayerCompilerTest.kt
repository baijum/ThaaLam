package com.baijum.thaalam.domain

import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.LayerSettings
import com.baijum.thaalam.data.Meter
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.SoundSet
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LayerCompilerTest {

    private fun layer(
        pulses: Int,
        cycleBeats: Double,
        swingPercent: Double = 50.0,
        enabled: Boolean = true,
    ) = LayerSettings(
        id = "layer-$pulses-$cycleBeats",
        name = "Test layer",
        enabled = enabled,
        pulses = pulses,
        cycleBeats = cycleBeats,
        accentPattern = LayerSettings.defaultAccentPattern(pulses),
        soundSet = SoundSet.WOODBLOCK,
        gain = 0.7f,
        swingPercent = swingPercent,
    )

    private fun assertOffset(expected: Double, actual: Double) {
        assertTrue(abs(expected - actual) < 1e-9, "expected $expected, got $actual")
    }

    private fun assertOffsetApprox(expected: Double, actual: Double) {
        assertTrue(abs(expected - actual) < 1e-3, "expected $expected, got $actual")
    }

    @Test
    fun `default settings compile to one primary voice`() {
        val voices = LayerCompiler.compile(MetronomeSettings())
        assertEquals(1, voices.size)
        val primary = voices.single()
        assertTrue(primary.isPrimary)
        assertEquals(4, primary.stepCount)
        assertEquals(listOf(0.0, 1.0, 2.0, 3.0), primary.steps.map { it.offsetBeats })
    }

    @Test
    fun `sixteenth layer is evenly spaced across the bar`() {
        val settings = MetronomeSettings(
            meter = Meter(4, 4, listOf(4)),
            layers = listOf(layer(pulses = 16, cycleBeats = 4.0)),
        )
        val compiled = LayerCompiler.compile(settings)
        assertEquals(2, compiled.size)
        val voice = compiled[1]
        assertEquals(16, voice.stepCount)
        voice.steps.forEachIndexed { index, step ->
            assertOffset(index * 0.25, step.offsetBeats)
        }
        assertOffset(0.25, voice.gaps.first())
        assertOffset(0.25, voice.gaps.last())
    }

    @Test
    fun `polyrhythm three over two uses a two beat cycle`() {
        val voice = LayerCompiler.compileLayer(layer(pulses = 3, cycleBeats = 2.0))
        assertEquals(3, voice.stepCount)
        voice.steps.forEachIndexed { index, step ->
            assertOffset(index * 2.0 / 3.0, step.offsetBeats)
        }
        voice.gaps.forEach { assertOffset(2.0 / 3.0, it) }
    }

    @Test
    fun `polyrhythm five over four interlocks on the right offsets`() {
        val voice = LayerCompiler.compileLayer(layer(pulses = 5, cycleBeats = 4.0))
        assertEquals(5, voice.stepCount)
        voice.steps.forEachIndexed { index, step ->
            assertOffset(index * 0.8, step.offsetBeats)
        }
    }

    @Test
    fun `polymeter keeps its own cycle length`() {
        val voice = LayerCompiler.compileLayer(layer(pulses = 3, cycleBeats = 3.0))
        assertEquals(3.0, voice.cycleBeats)
        assertEquals(listOf(0.0, 1.0, 2.0), voice.steps.map { it.offsetBeats })
        assertEquals(listOf(1.0, 1.0, 1.0), voice.gaps)
    }

    @Test
    fun `swing delays odd layer pulses`() {
        val voice = LayerCompiler.compileLayer(
            layer(pulses = 8, cycleBeats = 4.0, swingPercent = 66.7),
        )
        val expected = listOf(0.0, 2.0 / 3.0, 1.0, 1.0 + 2.0 / 3.0, 2.0, 2.0 + 2.0 / 3.0, 3.0, 3.0 + 2.0 / 3.0)
        voice.steps.forEachIndexed { index, step ->
            assertOffsetApprox(expected[index], step.offsetBeats)
        }
    }

    @Test
    fun `grid key ignores accents gain sound and offset`() {
        val base = layer(pulses = 5, cycleBeats = 4.0)
        val edited = base.copy(
            accentPattern = base.accentPattern.map { BeatKind.MUTE },
            gain = 0.1f,
            offsetMs = 20.0,
            soundSet = SoundSet.BELL,
        )
        assertEquals(LayerCompiler.layerGridKey(base), LayerCompiler.layerGridKey(edited))
        assertNotEquals(
            LayerCompiler.layerGridKey(base),
            LayerCompiler.layerGridKey(base.copy(pulses = 7)),
        )
        assertNotEquals(
            LayerCompiler.layerGridKey(base),
            LayerCompiler.layerGridKey(base.copy(cycleBeats = 3.0)),
        )
    }

    @Test
    fun `pattern key includes enabled layers only`() {
        val base = MetronomeSettings()
        val withLayer = base.copy(layers = listOf(layer(3, 2.0)))
        assertNotEquals(LayerCompiler.patternKey(base), LayerCompiler.patternKey(withLayer))
        val disabled = withLayer.copy(layers = withLayer.layers.map { it.copy(enabled = false) })
        assertEquals(LayerCompiler.patternKey(base), LayerCompiler.patternKey(disabled))
    }

    @Test
    fun `normalization limits and repairs layers`() {
        val layers = (1..5).map {
            LayerSettings(
                id = "same",
                name = "  ",
                pulses = 0,
                cycleBeats = 0.0,
                accentPattern = emptyList(),
                gain = 2f,
                offsetMs = 100.0,
                swingPercent = 100.0,
            )
        }
        val settings = MetronomeSettings(layers = layers).normalized()
        assertEquals(3, settings.layers.size)
        assertEquals(3, settings.layers.map { it.id }.toSet().size)
        settings.layers.forEach { normalized ->
            assertEquals(1, normalized.pulses)
            assertEquals(0.25, normalized.cycleBeats)
            assertEquals(1f, normalized.gain)
            assertEquals(50.0, normalized.offsetMs)
            assertEquals(75.0, normalized.swingPercent)
            assertEquals(1, normalized.accentPattern.size)
            assertTrue(normalized.name.isNotBlank())
        }
    }

    @Test
    fun `disabled layers compile only when requested`() {
        val settings = MetronomeSettings(layers = listOf(layer(3, 2.0, enabled = false)))
        assertEquals(1, LayerCompiler.compile(settings).size)
        assertEquals(2, LayerCompiler.compile(settings, includeDisabled = true).size)
    }

    @Test
    fun `polyrhythm and polymeter classification uses the primary bar`() {
        assertTrue(LayerCompiler.isPolyrhythm(layer(5, 4.0), primaryBeats = 4))
        assertFalse(LayerCompiler.isPolymeter(layer(5, 4.0), primaryBeats = 4))
        assertTrue(LayerCompiler.isPolymeter(layer(3, 3.0), primaryBeats = 4))
        assertFalse(LayerCompiler.isPolyrhythm(layer(3, 3.0), primaryBeats = 4))
    }
}
