package com.baijum.thaalam.domain

import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.Meter
import com.baijum.thaalam.data.MetronomePreset
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.SoundSet
import com.baijum.thaalam.data.Subdivision

/**
 * Swift-friendly mutation helpers.
 *
 * Kotlin data-class `copy()` requires every argument in Swift, which is
 * impractical from SwiftUI. These small, intention-revealing functions keep the
 * iOS layer as concise as the Android one while preserving a single source of
 * validation and normalisation in Kotlin.
 */
object SettingsMutations {
    fun defaults(): MetronomeSettings = MetronomeSettings().normalized()

    fun setBpm(settings: MetronomeSettings, bpm: Double): MetronomeSettings =
        settings.copy(bpm = bpm).normalized()

    fun setVolume(settings: MetronomeSettings, volume: Double): MetronomeSettings =
        settings.copy(volume = volume.toFloat()).normalized()

    fun setMeter(
        settings: MetronomeSettings,
        beatsPerBar: Int,
        beatUnit: Int,
        grouping: String,
    ): MetronomeSettings {
        val parsed = parseGrouping(grouping)
        val meter = Meter.normalized(beatsPerBar, beatUnit, parsed)
        return settings.copy(
            meter = meter,
            accentPattern = MetronomeStateLogic.groupAccentPattern(meter.beatsPerBar, meter.grouping),
            talaId = null,
        ).normalized()
    }

    fun setSubdivisionTicks(settings: MetronomeSettings, ticks: Int): MetronomeSettings =
        settings.copy(subdivision = Subdivision.fromTicks(ticks)).normalized()

    fun setSwing(settings: MetronomeSettings, percent: Double): MetronomeSettings =
        settings.copy(swingPercent = percent).normalized()

    fun cycleAccent(settings: MetronomeSettings, index: Int): MetronomeSettings {
        val pattern = settings.accentPattern.toMutableList()
        if (index in pattern.indices) {
            pattern[index] = MetronomeStateLogic.cycleBeatType(pattern[index])
        }
        return settings.copy(accentPattern = pattern).normalized()
    }

    fun setGroupAccents(settings: MetronomeSettings): MetronomeSettings =
        settings.withGroupAccents().normalized()

    fun setSoundSet(settings: MetronomeSettings, soundSet: SoundSet): MetronomeSettings =
        settings.copy(soundSet = soundSet).normalized()

    fun setCountInBars(settings: MetronomeSettings, bars: Int): MetronomeSettings =
        settings.copy(countInBars = bars).normalized()

    fun setGapTrainer(settings: MetronomeSettings, playBars: Int, silentBars: Int): MetronomeSettings =
        settings.copy(gapPlayBars = playBars, gapSilentBars = silentBars).normalized()

    fun setMuteProbability(settings: MetronomeSettings, probability: Double): MetronomeSettings =
        settings.copy(muteProbability = probability).normalized()

    fun setProtectFirstBeat(settings: MetronomeSettings, protect: Boolean): MetronomeSettings =
        settings.copy(protectFirstBeat = protect).normalized()

    fun setRamp(
        settings: MetronomeSettings,
        enabled: Boolean,
        targetBpm: Double,
        stepBpm: Double,
        everyBars: Int,
    ): MetronomeSettings = settings.copy(
        rampEnabled = enabled,
        rampTargetBpm = targetBpm,
        rampStepBpm = stepBpm,
        rampEveryBars = everyBars,
    ).normalized()

    fun reset(): MetronomeSettings = MetronomeSettings().normalized()

    private fun parseGrouping(text: String): List<Int>? {
        if (text.isBlank()) return null
        val values = text.split('+', ',', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.toIntOrNull() ?: return null }
        if (values.isEmpty() || values.any { it <= 0 }) return null
        return values
    }
}

/** Factory for [RhythmPattern] that Swift can call without companion interop noise. */
object PatternFactory {
    fun build(settings: MetronomeSettings): RhythmPattern = RhythmPattern.build(settings)

    fun buildCountIn(settings: MetronomeSettings): RhythmPattern = RhythmPattern.build(
        settings.copy(subdivision = Subdivision.QUARTER, swingPercent = 50.0),
    )

    /** Stable identity for the pattern-affecting part of a settings snapshot. */
    fun patternKey(settings: MetronomeSettings): Int = listOf(
        settings.meter.beatsPerBar,
        settings.meter.beatUnit,
        settings.meter.grouping,
        settings.subdivision.ticksPerBeat,
        settings.swingPercent,
    ).hashCode()
}

/** Factory for presets and the starter library shared by both platforms. */
object PresetFactory {
    fun create(
        id: String,
        name: String,
        settings: MetronomeSettings,
        createdAtEpochMs: Long,
    ): MetronomePreset = MetronomePreset(
        id = id,
        name = name.trim().ifEmpty { "Untitled" },
        settings = settings.normalized(),
        createdAtEpochMs = createdAtEpochMs,
    )

    fun rename(preset: MetronomePreset, name: String): MetronomePreset {
        val trimmed = name.trim()
        return if (trimmed.isEmpty()) preset else preset.copy(name = trimmed)
    }

    fun starterPresets(): List<MetronomePreset> = listOf(
        MetronomePreset(
            id = "starter-warmup",
            name = "Daily warm-up",
            settings = MetronomeSettings(
                bpm = 72.0,
                meter = Meter(4, 4, listOf(4)),
                subdivision = Subdivision.EIGHTH,
                countInBars = 1,
            ).normalized(),
        ),
        MetronomePreset(
            id = "starter-teental",
            name = "Teental · 120",
            settings = TalaApplication.apply(
                tala = TalaLibrary.find("hindustani.teental")!!,
                base = MetronomeSettings(bpm = 120.0, countInBars = 1),
            ),
        ),
        MetronomePreset(
            id = "starter-odd",
            name = "Odd meter · 7/8",
            settings = MetronomeSettings(
                bpm = 104.0,
                meter = Meter(7, 8, listOf(2, 2, 3)),
                subdivision = Subdivision.EIGHTH,
                accentPattern = MetronomeStateLogic.groupAccentPattern(7, listOf(2, 2, 3)),
                countInBars = 1,
            ).normalized(),
        ),
    )
}

/** Tap-tempo factory so Swift does not need every Kotlin default argument. */
object TapTempoFactory {
    fun create(): TapTempo = TapTempo()
}
