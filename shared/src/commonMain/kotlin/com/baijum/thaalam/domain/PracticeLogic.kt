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
     * Gap 2.0: true when [barIndex] is silent, using the custom pattern when
     * present and the legacy play/silent cycle otherwise.
     */
    fun isGapSilentBar(barIndex: Int, settings: MetronomeSettings): Boolean {
        val cycle = gapCycle(settings)
        if (cycle.isEmpty() || barIndex <= 0) return false
        val position = (barIndex - 1) % cycle.size
        return !cycle[position]
    }

    /**
     * The deterministic play/gap cycle, or an empty list when gap training is
     * off. `true` means an audible bar.
     */
    fun gapCycle(settings: MetronomeSettings): List<Boolean> {
        if (settings.gapPattern.isNotEmpty()) return settings.gapPattern
        if (settings.gapPlayBars <= 0 || settings.gapSilentBars <= 0) return emptyList()
        return List(settings.gapPlayBars) { true } + List(settings.gapSilentBars) { false }
    }

    /**
     * Click gain for a bar during gap training. Play bars normally return 1.0;
     * the last [MetronomeSettings.gapFadeBars] play bars before a gap fade down
     * linearly. Silent bars return 0.0 (the sam may still be protected by the
     * engine).
     */
    fun gapGainForBar(barIndex: Int, settings: MetronomeSettings): Float {
        val cycle = gapCycle(settings)
        if (cycle.isEmpty() || barIndex <= 0) return 1f
        val position = (barIndex - 1) % cycle.size
        if (!cycle[position]) return 0f
        val fadeBars = settings.gapFadeBars.coerceIn(0, 8)
        if (fadeBars <= 0) return 1f
        var remainingPlayBars = 0
        var offset = 1
        while (offset <= cycle.size && cycle[(position + offset) % cycle.size]) {
            remainingPlayBars++
            offset++
        }
        if (offset > cycle.size) return 1f // the pattern never actually gaps
        val barsUntilGap = remainingPlayBars + 1
        if (barsUntilGap > fadeBars) return 1f
        return barsUntilGap.toFloat() / (fadeBars + 1).toFloat()
    }

    /**
     * Validates a custom gap pattern: bounded length, no all-silent cycle, and
     * no more than [maxSilentBars] consecutive silent bars. Repairs by turning
     * the offending bar into a play bar.
     */
    fun normalizeGapPattern(pattern: List<Boolean>, maxSilentBars: Int): List<Boolean> {
        if (pattern.isEmpty()) return emptyList()
        val safeMax = maxSilentBars.coerceIn(1, 16)
        val result = pattern.take(MAX_GAP_PATTERN).toMutableList()
        var silentRun = 0
        for (index in result.indices) {
            if (result[index]) {
                silentRun = 0
            } else {
                silentRun++
                if (silentRun > safeMax) {
                    result[index] = true
                    silentRun = 0
                }
            }
        }
        if (result.none { it }) result[0] = true
        return result
    }

    /** Soft sam click used on a silent bar when protection is enabled. */
    const val GAP_SAM_GAIN: Float = 0.45f

    private const val MAX_GAP_PATTERN = 16

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

    /**
     * Effective BPM for a bar, including count-in, kalam and stepped ramp
     * handling. Kalam scales the base tempo, so the ramp works in performance
     * tempo rather than in the stored base value.
     */
    fun bpmForBar(settings: MetronomeSettings, barIndex: Int, isCountIn: Boolean): Double {
        if (isCountIn) return settings.effectiveBpm
        if (!settings.rampEnabled) return settings.effectiveBpm
        val steps = (barIndex - 1) / settings.rampEveryBars.coerceAtLeast(1)
        // Ramp the stored base tempo, then apply kalam once. This keeps the
        // ramp target meaningful (it is a base BPM) regardless of kalam.
        var base = TempoMath.clampBpm(settings.bpm)
        repeat(steps) {
            base = TempoMath.rampStep(base, settings.rampTargetBpm, settings.rampStepBpm)
        }
        return TempoMath.clampEffectiveBpm(base * settings.talaPlayback.kalamFactor)
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
