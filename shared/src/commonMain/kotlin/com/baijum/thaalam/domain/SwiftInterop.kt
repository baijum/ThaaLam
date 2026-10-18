package com.baijum.thaalam.domain

import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.LayerSettings
import com.baijum.thaalam.data.Meter
import com.baijum.thaalam.data.MetronomePreset
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.SoundSet
import com.baijum.thaalam.data.Subdivision
import com.baijum.thaalam.data.Tala
import com.baijum.thaalam.data.TalaPlayback
import kotlin.math.roundToInt

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

    // ---------------------------------------------------------------------
    // Layers
    // ---------------------------------------------------------------------

    fun setLayers(
        settings: MetronomeSettings,
        layers: List<LayerSettings>,
    ): MetronomeSettings = settings.copy(layers = layers).normalized()

    fun addLayer(
        settings: MetronomeSettings,
        layer: LayerSettings,
    ): MetronomeSettings {
        if (settings.layers.size >= LayerSettings.MAX_LAYERS) return settings
        return settings.copy(layers = settings.layers + layer).normalized()
    }

    fun updateLayer(
        settings: MetronomeSettings,
        layer: LayerSettings,
    ): MetronomeSettings {
        if (settings.layers.none { it.id == layer.id }) return settings
        return settings.copy(
            layers = settings.layers.map { if (it.id == layer.id) layer else it },
        ).normalized()
    }

    fun removeLayer(settings: MetronomeSettings, id: String): MetronomeSettings =
        settings.copy(layers = settings.layers.filterNot { it.id == id }).normalized()

    fun clearLayers(settings: MetronomeSettings): MetronomeSettings =
        settings.copy(layers = emptyList()).normalized()

    fun setLayerEnabled(
        settings: MetronomeSettings,
        id: String,
        enabled: Boolean,
    ): MetronomeSettings = mutateLayer(settings, id) { it.copy(enabled = enabled) }

    fun setLayerSolo(
        settings: MetronomeSettings,
        id: String,
        solo: Boolean,
    ): MetronomeSettings = mutateLayer(settings, id) { it.copy(solo = solo) }

    fun setLayerStepOffset(
        settings: MetronomeSettings,
        id: String,
        index: Int,
        offsetMs: Double,
    ): MetronomeSettings = mutateLayer(settings, id) {
        LayerMutations.setStepOffset(it, index, offsetMs)
    }

    fun cycleLayerStepOffset(
        settings: MetronomeSettings,
        id: String,
        index: Int,
    ): MetronomeSettings = mutateLayer(settings, id) {
        LayerMutations.cycleStepOffset(it, index)
    }

    fun setLayerGain(
        settings: MetronomeSettings,
        id: String,
        gain: Double,
    ): MetronomeSettings = mutateLayer(settings, id) { it.copy(gain = gain.toFloat()) }

    fun setLayerOffset(
        settings: MetronomeSettings,
        id: String,
        offsetMs: Double,
    ): MetronomeSettings = mutateLayer(settings, id) { it.copy(offsetMs = offsetMs) }

    fun setLayerSoundSet(
        settings: MetronomeSettings,
        id: String,
        soundSet: SoundSet,
    ): MetronomeSettings = mutateLayer(settings, id) { it.copy(soundSet = soundSet) }

    fun cycleLayerAccent(
        settings: MetronomeSettings,
        id: String,
        index: Int,
    ): MetronomeSettings = mutateLayer(settings, id) { layer ->
        val pattern = layer.accentPattern.toMutableList()
        if (index in pattern.indices) {
            pattern[index] = MetronomeStateLogic.cycleBeatType(pattern[index])
        }
        layer.copy(accentPattern = pattern)
    }

    private fun mutateLayer(
        settings: MetronomeSettings,
        id: String,
        transform: (LayerSettings) -> LayerSettings,
    ): MetronomeSettings {
        val index = settings.layers.indexOfFirst { it.id == id }
        if (index < 0) return settings
        val layers = settings.layers.toMutableList()
        layers[index] = transform(layers[index])
        return settings.copy(layers = layers).normalized()
    }

    // ---------------------------------------------------------------------
    // Tala playback (kalam, kalai, eduppu)
    // ---------------------------------------------------------------------

    fun setTalaPlayback(
        settings: MetronomeSettings,
        kalam: Int,
        kalai: Int,
        eduppuOffsetAksharas: Double,
    ): MetronomeSettings = TalaApplication.withPlayback(
        settings,
        TalaPlayback(
            kalam = kalam,
            kalai = kalai,
            eduppuOffsetAksharas = eduppuOffsetAksharas,
        ),
    )

    fun setKalam(settings: MetronomeSettings, kalam: Int): MetronomeSettings =
        TalaApplication.withKalam(settings, kalam)

    fun setKalai(settings: MetronomeSettings, kalai: Int): MetronomeSettings =
        TalaApplication.withKalai(settings, kalai)

    fun setEduppu(
        settings: MetronomeSettings,
        eduppuOffsetAksharas: Double,
    ): MetronomeSettings = TalaApplication.withEduppu(settings, eduppuOffsetAksharas)

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

/** Factory for compiled voices that Swift can call without companion interop noise. */
object PatternFactory {
    fun build(settings: MetronomeSettings): RhythmPattern = RhythmPattern.build(settings)

    fun buildCountIn(settings: MetronomeSettings): RhythmPattern = RhythmPattern.build(
        settings.copy(subdivision = Subdivision.QUARTER, swingPercent = 50.0),
    )

    /** Primary voice followed by every enabled layer. */
    fun voices(settings: MetronomeSettings): List<VoicePattern> =
        LayerCompiler.compile(settings)

    /** Quarter-note count-in voice for the current meter and accents. */
    fun countInVoice(settings: MetronomeSettings): VoicePattern =
        LayerCompiler.compileCountIn(settings)

    /** Stable identity for every grid in the settings snapshot. */
    fun patternKey(settings: MetronomeSettings): Int =
        LayerCompiler.patternKey(settings)
}

/** Templates for new layers, shared by the Android and iOS layer editors. */
object LayerFactory {
    fun subdivision(
        settings: MetronomeSettings,
        subdivision: Subdivision,
    ): LayerSettings {
        val beats = settings.meter.normalized().beatsPerBar
        val pulses = (beats * subdivision.ticksPerBeat)
            .coerceIn(LayerSettings.MIN_PULSES, LayerSettings.MAX_PULSES)
        return LayerSettings(
            id = "layer-${subdivision.name.lowercase()}",
            name = subdivision.displayName,
            pulses = pulses,
            cycleBeats = beats.toDouble(),
            accentPattern = LayerSettings.defaultAccentPattern(pulses),
            soundSet = settings.soundSet,
            gain = 0.65f,
        ).normalized()
    }

    fun polyrhythm(
        settings: MetronomeSettings,
        pulses: Int,
        cycleBeats: Double,
    ): LayerSettings {
        val safePulses = pulses.coerceIn(LayerSettings.MIN_PULSES, LayerSettings.MAX_PULSES)
        val safeCycle = cycleBeats.coerceIn(
            LayerSettings.MIN_CYCLE_BEATS,
            LayerSettings.MAX_CYCLE_BEATS,
        )
        return LayerSettings(
            id = "layer-poly-${safePulses}-${(safeCycle * 100).roundToInt()}",
            name = "${safePulses} over ${formatBeats(safeCycle)}",
            pulses = safePulses,
            cycleBeats = safeCycle,
            accentPattern = LayerSettings.defaultAccentPattern(safePulses),
            soundSet = settings.soundSet,
            gain = 0.6f,
        ).normalized()
    }

    fun polymeter(
        settings: MetronomeSettings,
        beats: Int,
        beatUnit: Int,
    ): LayerSettings {
        val primary = settings.meter.normalized()
        val safeBeats = beats.coerceIn(1, 32)
        val safeUnit = beatUnit.takeIf { it in listOf(2, 4, 8, 16) } ?: 4
        val cycle = (safeBeats * primary.beatUnit.toDouble() / safeUnit)
            .coerceIn(LayerSettings.MIN_CYCLE_BEATS, LayerSettings.MAX_CYCLE_BEATS)
        return LayerSettings(
            id = "layer-poly-meter-$safeBeats-$safeUnit",
            name = "$safeBeats/$safeUnit cycle",
            pulses = safeBeats,
            cycleBeats = cycle,
            accentPattern = LayerSettings.defaultAccentPattern(safeBeats),
            soundSet = settings.soundSet,
            gain = 0.6f,
        ).normalized()
    }

    fun defaultPolyrhythm(settings: MetronomeSettings): LayerSettings =
        polyrhythm(settings, pulses = 3, cycleBeats = 2.0)

    private fun formatBeats(value: Double): String =
        if (value == value.toInt().toDouble()) {
            value.toInt().toString()
        } else {
            ((value * 10.0).roundToInt() / 10.0).toString()
        }
}

/** Layer-only mutations used by SwiftUI before a settings update. */
object LayerMutations {
    fun setEnabled(layer: LayerSettings, enabled: Boolean): LayerSettings =
        layer.copy(enabled = enabled).normalized()

    fun setSolo(layer: LayerSettings, solo: Boolean): LayerSettings =
        layer.copy(solo = solo).normalized()

    fun setStepOffset(layer: LayerSettings, index: Int, offsetMs: Double): LayerSettings {
        val offsets = layer.stepOffsetsMs.toMutableList()
        while (offsets.size <= index) offsets.add(0.0)
        if (index >= 0) offsets[index] = offsetMs
        return layer.copy(stepOffsetsMs = offsets).normalized()
    }

    /** Primitive accessors so SwiftUI does not need to bridge List<Double>. */
    fun stepOffsetCount(layer: LayerSettings): Int = layer.stepOffsetsMs.size

    fun stepOffsetAt(layer: LayerSettings, index: Int): Double =
        layer.stepOffsetsMs.getOrElse(index) { 0.0 }

    fun cycleStepOffset(layer: LayerSettings, index: Int): LayerSettings {
        val cycle = LayerSettings.stepOffsetCycleMs
        val current = layer.stepOffsetsMs.getOrElse(index) { 0.0 }
        val currentIndex = cycle.indexOfFirst { it == current }.takeIf { it >= 0 } ?: 2
        val next = cycle[(currentIndex + 1) % cycle.size]
        return setStepOffset(layer, index, next)
    }

    fun rename(layer: LayerSettings, name: String): LayerSettings {
        val trimmed = name.trim()
        return if (trimmed.isEmpty()) layer else layer.copy(name = trimmed).normalized()
    }

    fun setPulses(layer: LayerSettings, pulses: Int): LayerSettings =
        layer.copy(pulses = pulses).normalized()

    fun setCycleBeats(layer: LayerSettings, cycleBeats: Double): LayerSettings =
        layer.copy(cycleBeats = cycleBeats).normalized()

    fun setGain(layer: LayerSettings, gain: Double): LayerSettings =
        layer.copy(gain = gain.toFloat()).normalized()

    fun setOffset(layer: LayerSettings, offsetMs: Double): LayerSettings =
        layer.copy(offsetMs = offsetMs).normalized()

    fun setSwing(layer: LayerSettings, percent: Double): LayerSettings =
        layer.copy(swingPercent = percent).normalized()

    fun setSoundSet(layer: LayerSettings, soundSet: SoundSet): LayerSettings =
        layer.copy(soundSet = soundSet).normalized()

    fun cycleAccent(layer: LayerSettings, index: Int): LayerSettings {
        val pattern = layer.accentPattern.toMutableList()
        if (index in pattern.indices) {
            pattern[index] = MetronomeStateLogic.cycleBeatType(pattern[index])
        }
        return layer.copy(accentPattern = pattern).normalized()
    }
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

/** Tala metadata and playback labels shared by the Android and iOS UI. */
object TalaFactory {
    fun applyTala(
        tala: Tala,
        base: MetronomeSettings,
        nadaiTicks: Int,
        baseBpm: Double,
        kalam: Int,
        kalai: Int,
        eduppuOffsetAksharas: Double,
    ): MetronomeSettings = TalaApplication.applyTalaWithPlayback(
        tala = tala,
        base = base,
        nadaiTicks = nadaiTicks,
        baseBpm = baseBpm,
        kalam = kalam,
        kalai = kalai,
        eduppuOffsetAksharas = eduppuOffsetAksharas,
    )

    fun clear(settings: MetronomeSettings): MetronomeSettings =
        TalaApplication.clear(settings)

    fun playback(settings: MetronomeSettings): TalaPlayback = settings.talaPlayback

    fun kalamLabel(kalam: Int): String = "${kalam}x"

    fun kalaiLabel(kalai: Int): String = "$kalai-kalai"

    fun eduppuLabel(eduppuOffsetAksharas: Double): String =
        TalaPlayback.eduppuLabel(eduppuOffsetAksharas)

    fun eduppuOptionsCsv(): String =
        TalaPlayback.EDUPPU_OPTIONS.joinToString(",")

    fun kalamOptionsCsv(): String = TalaPlayback.KALAM_OPTIONS.joinToString(",")

    fun kalaiOptionsCsv(): String = TalaPlayback.KALAI_OPTIONS.joinToString(",")

    /** Default tala playback for a fresh selection. */
    fun defaultPlayback(): TalaPlayback = TalaPlayback()

    /** Preset name for the current tala configuration, e.g. "Misra Chapu · 2x · 1-kalai". */
    fun drillName(tala: Tala, playback: TalaPlayback): String =
        "${tala.name} · ${playback.summary}"
}
