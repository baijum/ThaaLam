package com.baijum.thaalam.domain

import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.Subdivision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Guards the Swift-facing API surface: these functions are what iOS uses for
 * every mutation, so their semantics must match the Kotlin-side copy() usage.
 */
class SwiftInteropTest {

    @Test
    fun `defaults are normalised`() {
        val settings = SettingsMutations.defaults()
        assertEquals(4, settings.meter.beatsPerBar)
        assertEquals(4, settings.accentPattern.size)
        assertEquals(BeatKind.ACCENT, settings.accentPattern[0])
    }

    @Test
    fun `set meter applies grouping and group accents`() {
        val settings = SettingsMutations.setMeter(
            settings = MetronomeSettings(),
            beatsPerBar = 7,
            beatUnit = 8,
            grouping = "2+2+3",
        )
        assertEquals(listOf(2, 2, 3), settings.meter.grouping)
        assertEquals(listOf(0, 2, 4), settings.accentPattern.indices.filter {
            settings.accentPattern[it] == BeatKind.ACCENT
        })
        assertEquals(null, settings.talaId)
    }

    @Test
    fun `set meter with blank grouping uses the musical default`() {
        val settings = SettingsMutations.setMeter(
            settings = MetronomeSettings(),
            beatsPerBar = 6,
            beatUnit = 8,
            grouping = "",
        )
        assertEquals(listOf(3, 3), settings.meter.grouping)
    }

    @Test
    fun `invalid grouping falls back to the default`() {
        val settings = SettingsMutations.setMeter(
            settings = MetronomeSettings(),
            beatsPerBar = 5,
            beatUnit = 8,
            grouping = "9+9",
        )
        assertEquals(listOf(2, 3), settings.meter.grouping)
    }

    @Test
    fun `cycle accent advances through all four dynamics`() {
        var settings = MetronomeSettings()
        settings = SettingsMutations.cycleAccent(settings, 0)
        assertEquals(BeatKind.NORMAL, settings.accentPattern[0])
        settings = SettingsMutations.cycleAccent(settings, 0)
        assertEquals(BeatKind.GHOST, settings.accentPattern[0])
        settings = SettingsMutations.cycleAccent(settings, 0)
        assertEquals(BeatKind.MUTE, settings.accentPattern[0])
        settings = SettingsMutations.cycleAccent(settings, 0)
        assertEquals(BeatKind.ACCENT, settings.accentPattern[0])
    }

    @Test
    fun `out of range accent edits are ignored`() {
        val before = MetronomeSettings()
        val after = SettingsMutations.cycleAccent(before, 99)
        assertEquals(before.accentPattern, after.accentPattern)
    }

    @Test
    fun `ramp mutation clamps every value`() {
        val settings = SettingsMutations.setRamp(
            settings = MetronomeSettings(),
            enabled = true,
            targetBpm = 900.0,
            stepBpm = 99.0,
            everyBars = 0,
        )
        assertEquals(400.0, settings.rampTargetBpm)
        assertEquals(20.0, settings.rampStepBpm)
        assertEquals(1, settings.rampEveryBars)
        assertTrue(settings.rampEnabled)
    }

    @Test
    fun `pattern key tracks rhythmic changes but not accents`() {
        val base = MetronomeSettings()
        val accentOnly = base.copy(accentPattern = base.accentPattern.map { BeatKind.MUTE })
        assertEquals(
            PatternFactory.patternKey(base),
            PatternFactory.patternKey(accentOnly),
        )
        assertNotEquals(
            PatternFactory.patternKey(base),
            PatternFactory.patternKey(base.copy(subdivision = Subdivision.TRIPLET)),
        )
        assertNotEquals(
            PatternFactory.patternKey(base),
            PatternFactory.patternKey(base.copy(swingPercent = 66.7)),
        )
    }

    @Test
    fun `count-in pattern is quarter-note based`() {
        val settings = MetronomeSettings(subdivision = Subdivision.SEPTUPLET, swingPercent = 70.0)
        val countIn = PatternFactory.buildCountIn(settings)
        assertEquals(1, countIn.ticksPerBeat)
        assertEquals(50.0, countIn.swingPercent)
        assertEquals(settings.meter.beatsPerBar, countIn.steps.size)
    }

    @Test
    fun `starter presets are identical across platforms`() {
        val presets = PresetFactory.starterPresets()
        assertEquals(3, presets.size)
        assertEquals(listOf("starter-warmup", "starter-teental", "starter-odd"), presets.map { it.id })
        assertEquals("Teental", TalaLibrary.find(presets[1].settings.talaId)?.name?.substringBefore(" ")?.trim())
    }

    @Test
    fun `preset create and rename trim input`() {
        val preset = PresetFactory.create(
            id = "id-1",
            name = "  Practice  ",
            settings = MetronomeSettings(),
            createdAtEpochMs = 123L,
        )
        assertEquals("Practice", preset.name)
        assertEquals("Renamed", PresetFactory.rename(preset, "  Renamed  ").name)
        assertEquals("Practice", PresetFactory.rename(preset, "   ").name)
    }

    @Test
    fun `tala application by tick count matches subdivision overload`() {
        val tala = TalaLibrary.find("hindustani.teental")!!
        val base = MetronomeSettings(bpm = 88.0)
        val byTicks = TalaApplication.applyTala(tala, base, nadaiTicks = 3)
        val bySubdivision = TalaApplication.apply(tala, base, Subdivision.TRIPLET)
        assertEquals(bySubdivision, byTicks)
        assertEquals(3, byTicks.subdivision.ticksPerBeat)
        assertEquals(16, byTicks.meter.beatsPerBar)
    }

    @Test
    fun `layer factory maps subdivisions and polyrhythms`() {
        val base = MetronomeSettings()
        val subdivision = LayerFactory.subdivision(base, Subdivision.SIXTEENTH)
        assertEquals(16, subdivision.pulses)
        assertEquals(4.0, subdivision.cycleBeats)
        assertEquals(16, subdivision.accentPattern.size)

        val poly = LayerFactory.polyrhythm(base, pulses = 5, cycleBeats = 4.0)
        assertEquals(5, poly.pulses)
        assertEquals(4.0, poly.cycleBeats)

        val polymeter = LayerFactory.polymeter(base, beats = 5, beatUnit = 8)
        assertEquals(5, polymeter.pulses)
        assertEquals(2.5, polymeter.cycleBeats)
    }

    @Test
    fun `settings mutations add edit and remove a layer`() {
        val base = SettingsMutations.defaults()
        val layer = LayerFactory.polyrhythm(base, pulses = 3, cycleBeats = 2.0)
        var settings = SettingsMutations.addLayer(base, layer)
        assertEquals(1, settings.layers.size)

        settings = SettingsMutations.setLayerGain(settings, layer.id, 0.25)
        assertEquals(0.25f, settings.layers.single().gain)

        settings = SettingsMutations.cycleLayerAccent(settings, layer.id, 0)
        assertEquals(BeatKind.NORMAL, settings.layers.single().accentPattern[0])

        settings = SettingsMutations.removeLayer(settings, layer.id)
        assertTrue(settings.layers.isEmpty())
    }


    @Test
    fun `solo and step-offset mutations are Swift friendly`() {
        val base = SettingsMutations.defaults()
        val layer = LayerFactory.polyrhythm(base, pulses = 5, cycleBeats = 4.0)
        var settings = SettingsMutations.addLayer(base, layer)
        settings = SettingsMutations.setLayerSolo(settings, layer.id, true)
        assertTrue(settings.layers.single().solo)
        settings = SettingsMutations.cycleLayerStepOffset(settings, layer.id, 0)
        assertEquals(10.0, LayerMutations.stepOffsetAt(settings.layers.single(), 0))
        assertEquals(5, LayerMutations.stepOffsetCount(settings.layers.single()))
    }

    @Test
    fun `pattern factory exposes compiled layers and a count-in voice`() {
        val base = MetronomeSettings()
        val layered = base.copy(
            layers = listOf(
                LayerFactory.subdivision(base, Subdivision.EIGHTH),
            ),
        )
        assertEquals(1, PatternFactory.voices(base).size)
        assertEquals(2, PatternFactory.voices(layered).size)
        assertEquals(4, PatternFactory.countInVoice(layered).pulseCount)
        assertNotEquals(
            PatternFactory.patternKey(base),
            PatternFactory.patternKey(layered),
        )
    }
}
