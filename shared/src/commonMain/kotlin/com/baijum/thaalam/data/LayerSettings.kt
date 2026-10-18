package com.baijum.thaalam.data

import com.baijum.thaalam.domain.MetronomeStateLogic
import kotlinx.serialization.Serializable

/**
 * One additional rhythmic voice layered over the primary metronome pulse.
 *
 * A layer is defined by [pulses] evenly spaced events across [cycleBeats]
 * primary beats. That single definition covers the three v0.2 use cases:
 *
 * - **Subdivision mixer**: `pulses = beatsPerBar * ticksPerBeat` and
 *   `cycleBeats = beatsPerBar` adds an eighth, sixteenth or tuplet voice.
 * - **Polyrhythm**: `pulses = M` and `cycleBeats = N` spreads M pulses across
 *   the same N-beat cycle as the primary voice.
 * - **Polymeter**: a different `cycleBeats` gives the layer its own bar length;
 *   the cycles realign after the least common multiple.
 *
 * The primary voice stays in [MetronomeSettings.meter], [MetronomeSettings.subdivision]
 * and [MetronomeSettings.accentPattern]. Layers are additive and optional, so an
 * old settings blob with no layers decodes to the original single-voice model.
 */
@Serializable
data class LayerSettings(
    val id: String,
    val name: String = "",
    val enabled: Boolean = true,
    /** Solos this layer: other layers stay silent, the primary pulse continues. */
    val solo: Boolean = false,
    val pulses: Int = 2,
    /** Cycle length in primary beats. Must be positive. */
    val cycleBeats: Double = 4.0,
    /** One dynamic level per pulse, resized during normalization. */
    val accentPattern: List<BeatKind> = defaultAccentPattern(2),
    val soundSet: SoundSet = SoundSet.CLICK,
    /** Relative gain, 0..1. The primary voice is always full scale. */
    val gain: Float = 0.7f,
    /** Positive values delay the layer; negative values push it ahead. */
    val offsetMs: Double = 0.0,
    /** Per-pulse timing offsets in milliseconds, one entry per pulse. */
    val stepOffsetsMs: List<Double> = emptyList(),
    /** 50 = straight, 75 = dotted feel. Applied to even pulse counts only. */
    val swingPercent: Double = 50.0,
) {
    /** Short, human-readable grid description used by both platforms. */
    val gridLabel: String
        get() {
            val beatLabel = if (cycleBeats == cycleBeats.toInt().toDouble()) {
                cycleBeats.toInt().toString()
            } else {
                ((cycleBeats * 100.0).toInt() / 100.0).toString()
            }
            return "$pulses in $beatLabel"
        }

    /** A structurally valid layer. [index] only supplies fallback names and ids. */
    fun normalized(index: Int = 0): LayerSettings {
        val safePulses = pulses.coerceIn(MIN_PULSES, MAX_PULSES)
        val safeId = id.trim().ifEmpty { "layer-${index + 1}" }
        val safeName = name.trim().ifEmpty { "Layer ${index + 1}" }
        return copy(
            id = safeId,
            name = safeName,
            pulses = safePulses,
            cycleBeats = cycleBeats.coerceIn(MIN_CYCLE_BEATS, MAX_CYCLE_BEATS),
            accentPattern = MetronomeStateLogic.normalizeAccentPattern(
                beats = safePulses,
                pattern = accentPattern,
                grouping = emptyList(),
            ),
            gain = gain.coerceIn(0f, 1f),
            offsetMs = offsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS),
            stepOffsetsMs = List(safePulses) { index ->
                stepOffsetsMs.getOrElse(index) { 0.0 }
                    .coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)
            },
            swingPercent = swingPercent.coerceIn(MIN_SWING, MAX_SWING),
        )
    }

    companion object {
        const val MAX_LAYERS: Int = 3
        const val MIN_PULSES: Int = 1
        const val MAX_PULSES: Int = 64
        const val MIN_CYCLE_BEATS: Double = 0.25
        const val MAX_CYCLE_BEATS: Double = 32.0
        const val MIN_OFFSET_MS: Double = -50.0
        const val MAX_OFFSET_MS: Double = 50.0
        const val MIN_SWING: Double = 50.0
        const val MAX_SWING: Double = 75.0

        /** Values cycled by the per-pulse timing editor. */
        val stepOffsetCycleMs: List<Double> = listOf(-20.0, -10.0, 0.0, 10.0, 20.0)

        /** Accent the start of the layer; keep the rest of the grid even. */
        fun defaultAccentPattern(pulses: Int): List<BeatKind> {
            val safe = pulses.coerceIn(MIN_PULSES, MAX_PULSES)
            return List(safe) { index ->
                if (index == 0) BeatKind.ACCENT else BeatKind.NORMAL
            }
        }

        /**
         * Normalizes and limits a layer list: at most [MAX_LAYERS], stable
         * non-empty ids, no duplicates, every value in range.
         */
        fun normalizedList(layers: List<LayerSettings>): List<LayerSettings> {
            val seen = mutableSetOf<String>()
            return layers.take(MAX_LAYERS).mapIndexed { index, layer ->
                val base = layer.normalized(index)
                var candidate = base.id
                var suffix = 2
                while (!seen.add(candidate)) {
                    candidate = "${base.id}-$suffix"
                    suffix++
                }
                base.copy(id = candidate)
            }
        }
    }
}
