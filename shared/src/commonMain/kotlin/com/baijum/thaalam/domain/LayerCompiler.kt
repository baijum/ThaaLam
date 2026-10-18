package com.baijum.thaalam.domain

import com.baijum.thaalam.data.AngaGesture
import com.baijum.thaalam.data.LayerSettings
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.Subdivision
import com.baijum.thaalam.data.Tala
import com.baijum.thaalam.data.TalaPlayback

/**
 * Compiles the primary metronome voice and its optional layers into immutable
 * voice patterns for the platform audio engines.
 *
 * This is the only place that knows how polyrhythm, polymeter, subdivision
 * mixer, kalai, kalam and eduppu values turn into beat offsets. The engines walk
 * the resulting step lists and never do musical math in the render callback.
 *
 * Kalai is compiled into the step grid (twice as many clicks per akshara).
 * Kalam is deliberately *not* compiled into the grid: it only scales the BPM the
 * engine uses, so switching 1x → 2x → 4x never restarts the bar. Eduppu rotates
 * the step list and leaves a [VoicePattern.leadInBeats] before the first click.
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
        val candidates = normalized.layers.filter { includeDisabled || it.enabled }
        val anySolo = candidates.any { it.enabled && it.solo }
        val layers = candidates
            .filter { !anySolo || (it.enabled && it.solo) }
            .map(::compileLayer)
        return buildList {
            add(primary)
            addAll(layers)
        }
    }

    /**
     * Layers that should be audible for the current snapshot: enabled layers,
     * narrowed to soloed layers when at least one solo is active. The primary
     * pulse always remains the reference.
     */
    fun activeLayers(settings: MetronomeSettings): List<LayerSettings> {
        val enabled = settings.normalized().layers.filter { it.enabled }
        val anySolo = enabled.any { it.solo }
        return if (anySolo) enabled.filter { it.solo } else enabled
    }

    /** The normal primary grid, without layers. */
    fun compilePrimary(settings: MetronomeSettings): VoicePattern {
        val normalized = settings.normalized()
        val pattern = RhythmPattern.build(normalized)
        val tala = TalaLibrary.find(normalized.talaId)
        val steps = pattern.steps.map { step ->
            VoiceStep(
                index = step.index,
                pulse = step.beat,
                offsetBeats = step.offsetBeats,
                kind = step.kind,
                beat = step.beat,
                sub = step.sub,
                isMainBeat = step.isMainBeat,
                gesture = if (step.isMainBeat) tala?.gestureAt(step.beat) ?: AngaGesture.NONE
                else AngaGesture.NONE,
            )
        }
        val rotated = rotateForEduppu(
            steps = steps,
            cycleBeats = pattern.lengthBeats,
            eduppuOffsetAksharas = normalized.talaPlayback.eduppuOffsetAksharas,
        )
        return VoicePattern(
            id = VoicePattern.PRIMARY_ID,
            name = "Primary",
            isPrimary = true,
            steps = rotated,
            gaps = gapsFor(rotated, pattern.lengthBeats),
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

    /**
     * Quarter-note count-in voice using the primary accent pattern.
     *
     * The count-in ignores kalai (it should be one click per akshara) but keeps
     * the eduppu phase, so its last click lands immediately before where the
     * first musical bar starts.
     */
    fun compileCountIn(settings: MetronomeSettings): VoicePattern {
        val normalized = settings.normalized()
        val countInSettings = normalized.copy(
            subdivision = Subdivision.QUARTER,
            swingPercent = 50.0,
            talaPlayback = normalized.talaPlayback.copy(kalai = 1),
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
        val rotated = rotateForEduppu(
            steps = steps,
            cycleBeats = pattern.lengthBeats,
            eduppuOffsetAksharas = normalized.talaPlayback.eduppuOffsetAksharas,
        )
        return VoicePattern(
            id = VoicePattern.COUNT_IN_ID,
            name = "Count-in",
            isPrimary = true,
            steps = rotated,
            gaps = gapsFor(rotated, pattern.lengthBeats),
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
                offsetMs = safe.offsetMs + safe.stepOffsetsMs.getOrElse(index) { 0.0 },
            )
        }
        return VoicePattern(
            id = safe.id,
            name = safe.name,
            isPrimary = false,
            steps = steps,
            gaps = gapsFor(steps, cycle),
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

    /**
     * Rotates a cycle so playback starts at the eduppu position.
     *
     * The step positions are absolute within the cycle, so a phase of `0.5`
     * moves the pulse at 0.5 to zero and leaves a lead-in when the phase falls
     * between two clicks. Pulse/beat identities are preserved, which keeps the
     * UI and live accent resolution correct.
     */
    fun rotateForEduppu(
        steps: List<VoiceStep>,
        cycleBeats: Double,
        eduppuOffsetAksharas: Double,
    ): List<VoiceStep> {
        if (steps.isEmpty() || cycleBeats <= 0.0) return steps
        val phase = TalaPlayback.snapEduppu(eduppuOffsetAksharas)
        if (phase == 0.0) return steps
        var normalizedPhase = phase % cycleBeats
        if (normalizedPhase < 0.0) normalizedPhase += cycleBeats
        if (normalizedPhase == 0.0) return steps
        return steps
            .map { step ->
                var position = step.offsetBeats - normalizedPhase
                if (position < 0.0) position += cycleBeats
                step to position
            }
            .sortedBy { it.second }
            .mapIndexed { index, (step, position) ->
                step.copy(index = index, offsetBeats = position)
            }
    }

    /** Gaps between consecutive steps, wrapping back to the first offset. */
    private fun gapsFor(steps: List<VoiceStep>, cycleBeats: Double): List<Double> {
        if (steps.isEmpty()) return emptyList()
        return List(steps.size) { index ->
            val current = steps[index].offsetBeats
            val next = if (index + 1 < steps.size) {
                steps[index + 1].offsetBeats
            } else {
                steps[0].offsetBeats + cycleBeats
            }
            (next - current).coerceAtLeast(0.0)
        }
    }

    /** Identity of the primary grid; excludes accents, gain and sound. */
    fun primaryGridKey(settings: MetronomeSettings): Int = listOf(
        settings.meter.beatsPerBar,
        settings.meter.beatUnit,
        settings.meter.grouping,
        settings.effectiveTicksPerBeat,
        settings.talaPlayback.eduppuOffsetAksharas,
        settings.swingPercent,
    ).hashCode()

    /** Identity of the count-in grid. */
    fun countInGridKey(settings: MetronomeSettings): Int = listOf(
        settings.meter.beatsPerBar,
        settings.meter.beatUnit,
        settings.meter.grouping,
        1,
        settings.talaPlayback.eduppuOffsetAksharas,
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
     * Stable identity for every grid in the snapshot. Accent, sound, gain,
     * offset and kalam changes deliberately do not change this key.
     */
    fun patternKey(settings: MetronomeSettings): Int {
        val normalized = settings.normalized()
        return buildList {
            add(primaryGridKey(normalized))
            add(countInGridKey(normalized))
            activeLayers(normalized).forEach { add(layerGridKey(it)) }
        }.hashCode()
    }

    /** True when [layer] spans its own cycle rather than the primary bar. */
    fun isPolymeter(layer: LayerSettings, primaryBeats: Int): Boolean =
        layer.cycleBeats != primaryBeats.toDouble()

    /** True when the layer shares the primary cycle with a different pulse count. */
    fun isPolyrhythm(layer: LayerSettings, primaryBeats: Int): Boolean =
        layer.cycleBeats == primaryBeats.toDouble() && layer.pulses != primaryBeats

    /** Gesture map for a tala, or an empty list when it has no active tala. */
    fun gesturesFor(settings: MetronomeSettings): List<AngaGesture> {
        val tala: Tala = TalaLibrary.find(settings.talaId) ?: return emptyList()
        return tala.gestures
    }
}
