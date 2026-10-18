package com.baijum.thaalam.domain

import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.Meter
import com.baijum.thaalam.data.MetronomePreset
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.SoundSet
import com.baijum.thaalam.data.Subdivision
import kotlin.test.Test
import kotlin.test.assertEquals
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
    }
}
