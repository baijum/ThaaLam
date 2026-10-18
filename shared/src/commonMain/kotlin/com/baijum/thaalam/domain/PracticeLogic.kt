package com.baijum.thaalam.domain

import com.baijum.thaalam.data.MetronomeSettings

/**
 * Deterministic practice gating: gap bars, stochastic muting and tempo ramps.
 *
 * The audio engines call these helpers at bar/tick boundaries. Keeping the
 * decisions pure makes the practice modes reproducible and unit-testable
 * without an audio device.
 */
object PracticeLogic {

    /**
     * Whether a normal (post count-in) bar should be silent.
     *
     * [barIndex] is 1-based: bars 1..[playBars] click, the next [silentBars] are
     * silent, then the cycle repeats.
     */
    fun isGapSilentBar(barIndex: Int, playBars: Int, silentBars: Int): Boolean {
        if (playBars <= 0 || silentBars <= 0 || barIndex <= 0) return false
        val cycle = playBars + silentBars
        val positionInCycle = (barIndex - 1) % cycle
        return positionInCycle >= playBars
    }

    /**
     * Stochastic mute decision for one tick. [random] is expected in [0, 1).
     * The first beat of the bar can be protected so the pulse never disappears
     * completely at high mute percentages.
     */
    fun shouldMuteTick(
        random: Double,
        probability: Double,
        isFirstBeatOfBar: Boolean,
        protectFirstBeat: Boolean,
        isGapSilent: Boolean,
    ): Boolean {
        if (isGapSilent) return true
        if (isFirstBeatOfBar && protectFirstBeat) return false
        return probability > 0.0 && random < probability
    }

    /** True when a tempo-ramp adjustment lands on this bar (1-based). */
    fun shouldStepRamp(barIndex: Int, everyBars: Int): Boolean =
        barIndex > 0 && everyBars > 0 && barIndex % everyBars == 0

    /** Effective BPM for a bar, including count-in and stepped ramp handling. */
    fun bpmForBar(settings: MetronomeSettings, barIndex: Int, isCountIn: Boolean): Double {
        if (isCountIn) return TempoMath.clampBpm(settings.bpm)
        if (!settings.rampEnabled) return TempoMath.clampBpm(settings.bpm)
        val steps = (barIndex - 1) / settings.rampEveryBars.coerceAtLeast(1)
        var bpm = TempoMath.clampBpm(settings.bpm)
        repeat(steps) {
            bpm = TempoMath.rampStep(bpm, settings.rampTargetBpm, settings.rampStepBpm)
        }
        return bpm
    }

    /**
     * Lightweight deterministic pseudo-random generator suitable for the audio
     * thread (no allocations, no global RNG contention).
     */
    class Lcg(seed: Long = 0x5DEECE66DL) {
        private var state: Long = seed

        /** Next value in [0, 1). */
        fun nextDouble(): Double {
            state = state * 25214903917L + 11L
            val bits = (state ushr 16) and 0x7FFFFFFFL
            return bits / 0x80000000.toDouble()
        }

        fun reset(seed: Long = 0x5DEECE66DL) {
            state = seed
        }
    }
}
