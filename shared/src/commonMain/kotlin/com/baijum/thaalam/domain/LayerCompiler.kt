package com.baijum.thaalam.domain

import com.baijum.thaalam.data.LayerSettings
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.Subdivision

/**
 * Compiles the primary metronome voice and its optional layers into immutable
 * voice patterns for the platform audio engines.
 *
 * This is the only place that knows how polyrhythm, polymeter and subdivision
 * mixer values turn into beat offsets. The engines walk the resulting step
 * lists and never do musical math in the render callback.
 */
object LayerCompiler {
    /**
     * The primary voice first, then every enabled layer. Disabled layers are
     * omitted from the engine graph; the UI still reads them from settings.
     */
    fun compile(
        settings: MetronomeSettings,
        includeDisabled: Boolean = false,
    ): List<VoicePattern> {
        val normalized = settings.normalized()
        val primary = compilePrimary(normalized)
        val layers = normalized.layers
            .filter { includeDisabled || it.enabled }
            .map(::compileLayer)
        return buildList {
            add(primary)
            addAll(layers)
        }
    }

    /** The normal primary grid, without layers. */
    fun compilePrimary(settings: MetronomeSettings): VoicePattern {
        val normalized = settings.normalized()
        val pattern = RhythmPattern.build(normalized)
        val steps = pattern.steps.map { step ->
            VoiceStep(
                index = step.index,
                pulse = step.beat,
                offsetBeats = step.offsetBeats,
                kind = step.kind,
                beat = step.beat,
                sub = step.sub,
                isMainBeat = step.isMainBeat,
            )
        }
        val gaps = List(pattern.stepCount) { index -> pattern.gapBeatsAfter(index) }
        return VoicePattern(
            id = VoicePattern.PRIMARY_ID,
            name = "Primary",
            isPrimary = true,
            steps = steps,
            gaps = gaps,
            cycleBeats = pattern.lengthBeats,
            pulseCount = pattern.beatsPerBar,
            accentPattern = normalized.accentPattern,
            soundSet = normalized.soundSet,
            gain = 1f,
            offsetMs = 0.0,
            swingPercent = normalized.swingPercent,
            gridKey = primaryGridKey(normalized),
        )
    }

    /** Quarter-note count-in voice using the primary accent pattern. */
    fun compileCountIn(settings: MetronomeSettings): VoicePattern {
        val normalized = settings.normalized()
        val countInSettings = normalized.copy(
            subdivision = Subdivision.QUARTER,
            swingPercent = 50.0,
        )
        val pattern = RhythmPattern.build(countInSettings)
        val steps = pattern.steps.map { step ->
            VoiceStep(
                index = step.index,
                pulse = step.beat,
                offsetBeats = step.offsetBeats,
                kind = step.kind,
                beat = step.beat,
                sub = step.sub,
                isMainBeat = step.isMainBeat,
            )
        }
        val gaps = List(pattern.stepCount) { index -> pattern.gapBeatsAfter(index) }
        return VoicePattern(
            id = VoicePattern.COUNT_IN_ID,
            name = "Count-in",
            isPrimary = true,
            steps = steps,
            gaps = gaps,
            cycleBeats = pattern.lengthBeats,
            pulseCount = pattern.beatsPerBar,
            accentPattern = normalized.accentPattern,
            soundSet = normalized.soundSet,
            gain = 1f,
            offsetMs = 0.0,
            swingPercent = 50.0,
            gridKey = countInGridKey(normalized),
        )
    }

    /**
     * Compile one layer. Pulses are spread evenly across [LayerSettings.cycleBeats];
     * swing delays odd pulses by the same ratio used by the primary voice.
     */
    fun compileLayer(layer: LayerSettings): VoicePattern {
        val safe = layer.normalized()
        val pulses = safe.pulses
        val cycle = safe.cycleBeats
        val interval = cycle / pulses
        val swingRatio = safe.swingPercent / 100.0
        val swingOffset = if (pulses % 2 == 0) {
            (swingRatio - 0.5) * 2.0 * interval
        } else {
            0.0
        }
        val offsets = List(pulses) { index ->
            index * interval + if (index % 2 == 1) swingOffset else 0.0
        }
        val steps = offsets.mapIndexed { index, offset ->
            VoiceStep(
                index = index,
                pulse = index,
                offsetBeats = offset,
                kind = safe.accentPattern.getOrElse(index) { safe.accentPattern.first() },
                beat = -1,
                sub = index,
                isMainBeat = false,
            )
        }
        val gaps = offsets.mapIndexed { index, offset ->
            val next = offsets.getOrNull(index + 1) ?: cycle
            (next - offset).takeIf { it > 0.0 } ?: interval
        }
        return VoicePattern(
            id = safe.id,
            name = safe.name,
            isPrimary = false,
            steps = steps,
            gaps = gaps,
            cycleBeats = cycle,
            pulseCount = pulses,
            accentPattern = safe.accentPattern,
            soundSet = safe.soundSet,
            gain = safe.gain,
            offsetMs = safe.offsetMs,
            swingPercent = safe.swingPercent,
            gridKey = layerGridKey(safe),
        )
    }

    /** Identity of the primary grid; excludes accents, gain and sound. */
    fun primaryGridKey(settings: MetronomeSettings): Int = listOf(
        settings.meter.beatsPerBar,
        settings.meter.beatUnit,
        settings.meter.grouping,
        settings.subdivision.ticksPerBeat,
        settings.swingPercent,
    ).hashCode()

    /** Identity of the count-in grid. */
    fun countInGridKey(settings: MetronomeSettings): Int = listOf(
        settings.meter.beatsPerBar,
        settings.meter.beatUnit,
        settings.meter.grouping,
        1,
        50.0,
    ).hashCode()

    /** Identity of a layer grid; excludes accents, gain, sound and offset. */
    fun layerGridKey(layer: LayerSettings): Int = listOf(
        layer.id,
        layer.pulses,
        layer.cycleBeats,
        layer.swingPercent,
    ).hashCode()

    /**
     * Stable identity for every grid in the snapshot. Accent, sound, gain and
     * offset changes deliberately do not change this key.
     */
    fun patternKey(settings: MetronomeSettings): Int {
        val normalized = settings.normalized()
        return buildList {
            add(primaryGridKey(normalized))
            add(countInGridKey(normalized))
            normalized.layers.filter { it.enabled }.forEach { add(layerGridKey(it)) }
        }.hashCode()
    }

    /** True when [layer] spans its own cycle rather than the primary bar. */
    fun isPolymeter(layer: LayerSettings, primaryBeats: Int): Boolean =
        layer.cycleBeats != primaryBeats.toDouble()

    /** True when the layer shares the primary cycle with a different pulse count. */
    fun isPolyrhythm(layer: LayerSettings, primaryBeats: Int): Boolean =
        layer.cycleBeats == primaryBeats.toDouble() && layer.pulses != primaryBeats
}
