package com.baijum.thaalam.domain

import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.LayerSettings
import com.baijum.thaalam.data.Meter
import com.baijum.thaalam.data.MetronomePreset
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.SoundSet
import com.baijum.thaalam.data.Subdivision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresetCodecTest {

    private val sample = MetronomeSettings(
        bpm = 132.5,
        meter = Meter(7, 8, listOf(2, 2, 3)),
        subdivision = Subdivision.TRIPLET,
        accentPattern = listOf(
            BeatKind.ACCENT,
            BeatKind.NORMAL,
            BeatKind.GHOST,
            BeatKind.NORMAL,
            BeatKind.ACCENT,
            BeatKind.MUTE,
            BeatKind.NORMAL,
        ),
        swingPercent = 62.5,
        soundSet = SoundSet.WOODBLOCK,
        countInBars = 2,
        gapPlayBars = 3,
        gapSilentBars = 1,
        rampEnabled = true,
        rampTargetBpm = 160.0,
        talaId = "hindustani.rupak",
    )

    @Test
    fun `settings round trip`() {
        val decoded = PresetCodec.decodeSettings(PresetCodec.encodeSettings(sample))
        assertEquals(sample.bpm, decoded.bpm)
        assertEquals(sample.meter, decoded.meter)
        assertEquals(sample.subdivision, decoded.subdivision)
        assertEquals(sample.accentPattern, decoded.accentPattern)
        assertEquals(sample.soundSet, decoded.soundSet)
        assertEquals(sample.gapPlayBars, decoded.gapPlayBars)
        assertEquals(sample.talaId, decoded.talaId)
    }

    @Test
    fun `corrupt settings fall back to defaults`() {
        val decoded = PresetCodec.decodeSettings("{not json")
        assertEquals(100.0, decoded.bpm)
        assertEquals(4, decoded.meter.beatsPerBar)
    }

    @Test
    fun `presets round trip`() {
        val presets = listOf(
            MetronomePreset("1", "Slow practice", sample.copy(bpm = 60.0)),
            MetronomePreset("2", "Teental", sample.copy(bpm = 120.0, talaId = "hindustani.teental")),
        )
        val decoded = PresetCodec.decodePresets(PresetCodec.encodePresets(presets))
        assertEquals(2, decoded.size)
        assertEquals("Slow practice", decoded[0].name)
        assertEquals(60.0, decoded[0].settings.bpm)
        assertEquals("hindustani.teental", decoded[1].settings.talaId)
    }

    @Test
    fun `empty or invalid preset json yields an empty list`() {
        assertTrue(PresetCodec.decodePresets(null).isEmpty())
        assertTrue(PresetCodec.decodePresets("").isEmpty())
        assertTrue(PresetCodec.decodePresets("[oops").isEmpty())
    }

    @Test
    fun `decoding normalizes out-of-range settings`() {
        val raw = """{"bpm":9999.0,"volume":4.0,"meter":{"beatsPerBar":4,"beatUnit":4,"grouping":[1]},"accentPattern":["MUTE","MUTE","MUTE","MUTE"]}"""
        val decoded = PresetCodec.decodeSettings(raw)
        assertEquals(400.0, decoded.bpm)
        assertEquals(1.0f, decoded.volume)
        assertEquals(listOf(4), decoded.meter.grouping)
        assertEquals(BeatKind.MUTE, decoded.accentPattern[0])
    }

    @Test
    fun `preset summary describes the configuration`() {
        val preset = MetronomePreset("id", "Test", sample)
        assertTrue(preset.summary.contains("7/8"))
        assertTrue(preset.summary.contains("1/8T"))
        assertTrue(preset.summary.contains("swing"))
        val layered = preset.copy(
            settings = sample.copy(
                layers = listOf(
                    LayerSettings(
                        id = "layer",
                        pulses = 5,
                        cycleBeats = 4.0,
                        accentPattern = LayerSettings.defaultAccentPattern(5),
                    ),
                ),
            ),
        )
        assertTrue(layered.summary.contains("1 layer"), layered.summary)
    }

    @Test
    fun `layers round trip through the shared json codec`() {
        val layered = sample.copy(
            layers = listOf(
                LayerSettings(
                    id = "poly",
                    name = "3 over 2",
                    pulses = 3,
                    cycleBeats = 2.0,
                    accentPattern = listOf(BeatKind.ACCENT, BeatKind.NORMAL, BeatKind.GHOST),
                    soundSet = SoundSet.BELL,
                    gain = 0.5f,
                    offsetMs = 12.0,
                    swingPercent = 60.0,
                ).normalized(),
            ),
        )
        val decoded = PresetCodec.decodeSettings(PresetCodec.encodeSettings(layered))
        assertEquals(layered.layers, decoded.layers)
    }

    @Test
    fun `settings written before layers still decode`() {
        val raw = """{"bpm":120.0,"meter":{"beatsPerBar":4,"beatUnit":4,"grouping":[4]},"accentPattern":["ACCENT","NORMAL","NORMAL","NORMAL"]}"""
        val decoded = PresetCodec.decodeSettings(raw)
        assertEquals(120.0, decoded.bpm)
        assertTrue(decoded.layers.isEmpty())
    }

    @Test
    fun `layer json written before solo and step timing still decodes`() {
        val raw = """{"bpm":120.0,"meter":{"beatsPerBar":4,"beatUnit":4,"grouping":[4]},"accentPattern":["ACCENT","NORMAL","NORMAL","NORMAL"],"layers":[{"id":"legacy","pulses":3,"cycleBeats":2.0}]}"""
        val decoded = PresetCodec.decodeSettings(raw)
        val layer = decoded.layers.single()
        assertEquals("legacy", layer.id)
        assertEquals(3, layer.pulses)
        assertFalse(layer.solo)
        assertEquals(3, layer.stepOffsetsMs.size)
        assertTrue(layer.stepOffsetsMs.all { it == 0.0 })
    }
}
