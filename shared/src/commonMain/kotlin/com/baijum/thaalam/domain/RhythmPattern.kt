package com.baijum.thaalam.domain

import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.Meter
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.Subdivision

/**
 * One scheduled click inside a bar.
 *
 * [offsetBeats] is the position within the bar in beat units, already adjusted
 * for swing. It is a [Double] because swing and tuplets produce fractional
 * positions; the audio engine converts it to sample frames at the current BPM.
 */
data class RhythmStep(
    val index: Int,
    val beat: Int,
    val sub: Int,
    val offsetBeats: Double,
    val kind: BeatKind,
    val isMainBeat: Boolean,
) {
    /** True when this click is a subdivision rather than a counted pulse. */
    val isSubdivision: Boolean get() = !isMainBeat
}

/**
 * Immutable click grid for exactly one bar.
 *
 * The platform audio engines only need this flat, precomputed structure: they
 * walk it with a sample-accurate phase accumulator, which keeps beat pattern
 * logic (accents, swing, tuplets, grouping) in shared, unit-tested Kotlin while
 * keeping the real-time render path allocation-free.
 */
data class RhythmPattern(
    val steps: List<RhythmStep>,
    val beatsPerBar: Int,
    val ticksPerBeat: Int,
    val grouping: List<Int>,
    val swingPercent: Double,
) {
    val lengthBeats: Double get() = beatsPerBar.toDouble()
    val stepCount: Int get() = steps.size

    /**
     * Beat distance from [stepIndex] to the next step. The last step wraps
     * around to the start of the next bar.
     */
    fun gapBeatsAfter(stepIndex: Int): Double {
        if (steps.isEmpty()) return lengthBeats
        val current = steps[stepIndex.coerceIn(steps.indices)]
        val next = steps.getOrNull(stepIndex + 1)
        return if (next == null) {
            lengthBeats - current.offsetBeats
        } else {
            next.offsetBeats - current.offsetBeats
        }
    }

    companion object {
        /** Builds the click grid for a settings snapshot. */
        fun build(settings: MetronomeSettings): RhythmPattern {
            val meter = settings.meter.normalized()
            // Kalai multiplies the nadai/subdivision: 2-kalai clicks twice per
            // akshara even when the user picked a plain quarter pulse.
            val ticks = settings.effectiveTicksPerBeat
                .coerceIn(1, MetronomeSettings.MAX_EFFECTIVE_TICKS)
            val pattern = MetronomeStateLogic.normalizeAccentPattern(
                beats = meter.beatsPerBar,
                pattern = settings.accentPattern,
                grouping = meter.grouping,
            )
            val swingRatio = settings.swingPercent.coerceIn(50.0, 75.0) / 100.0
            val swingOffset = if (ticks % 2 == 0) {
                (swingRatio - 0.5) * 2.0 / ticks
            } else {
                0.0
            }

            val steps = ArrayList<RhythmStep>(meter.beatsPerBar * ticks)
            var index = 0
            for (beat in 0 until meter.beatsPerBar) {
                for (sub in 0 until ticks) {
                    val swung = sub % 2 == 1
                    val offset = beat + (sub.toDouble() / ticks) + if (swung) swingOffset else 0.0
                    steps += RhythmStep(
                        index = index,
                        beat = beat,
                        sub = sub,
                        offsetBeats = offset,
                        kind = if (sub == 0) pattern[beat] else BeatKind.NORMAL,
                        isMainBeat = sub == 0,
                    )
                    index++
                }
            }
            // Swing can only delay odd subdivisions; with a 50% swing the grid
            // is already sorted. Sorting defensively keeps the phase walker sane
            // for extreme values or unusual combinations.
            steps.sortBy { it.offsetBeats }
            return RhythmPattern(
                steps = steps.mapIndexed { newIndex, step -> step.copy(index = newIndex) },
                beatsPerBar = meter.beatsPerBar,
                ticksPerBeat = ticks,
                grouping = meter.grouping,
                swingPercent = settings.swingPercent.coerceIn(50.0, 75.0),
            )
        }

        /** A single-pulse pattern used by count-in bars and sound previews. */
        fun simple(meter: Meter = Meter()): RhythmPattern = build(
            MetronomeSettings(
                meter = meter,
                subdivision = Subdivision.QUARTER,
                accentPattern = MetronomeStateLogic.groupAccentPattern(
                    meter.beatsPerBar,
                    meter.grouping,
                ),
            ),
        )
    }
}
