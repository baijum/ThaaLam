package com.baijum.thaalam.domain

import kotlin.math.round

/** Result of registering one tap. */
data class TapTempoResult(
    /** Detected tempo, or null until at least two taps have been registered. */
    val bpm: Double?,
    val tapCount: Int,
    /** 1.0 = metronomic taps, 0.0 = wildly uneven. Null with fewer than three taps. */
    val stability: Double?,
    /** True when this tap started a fresh tap session. */
    val didReset: Boolean,
)

/**
 * Rolling-average tap tempo detector.
 *
 * Keeps the last [maxTaps] taps, forgets a session after [resetGapMs] of
 * silence, and reports a stability score so the UI can show whether the taps
 * are locking in. Uses the mean of the inter-tap intervals, which is far more
 * stable for a musician tapping along than a single interval.
 */
class TapTempo(
    private val maxTaps: Int = 8,
    private val resetGapMs: Long = 2500,
    private val minBpm: Double = 20.0,
    private val maxBpm: Double = 400.0,
) {
    private val taps = ArrayList<Long>(maxTaps)

    val tapCount: Int get() = taps.size

    fun reset() {
        taps.clear()
    }

    fun tap(nowMs: Long): TapTempoResult {
        var didReset = false
        if (taps.isNotEmpty() && nowMs - taps.last() > resetGapMs) {
            taps.clear()
            didReset = true
        }
        if (taps.isNotEmpty() && nowMs <= taps.last()) {
            // Ignore duplicate/out-of-order timestamps rather than producing a
            // nonsensical tempo.
            return TapTempoResult(computeBpm(), taps.size, computeStability(), false)
        }
        taps += nowMs
        while (taps.size > maxTaps) taps.removeAt(0)
        if (taps.size < 2) {
            return TapTempoResult(null, taps.size, null, didReset)
        }
        return TapTempoResult(computeBpm(), taps.size, computeStability(), didReset)
    }

    private fun computeBpm(): Double? {
        if (taps.size < 2) return null
        val intervals = taps.zipWithNext { a, b -> (b - a).toDouble() }
        val meanInterval = intervals.average()
        if (meanInterval <= 0.0) return null
        return (60_000.0 / meanInterval).coerceIn(minBpm, maxBpm)
    }

    private fun computeStability(): Double? {
        if (taps.size < 3) return null
        val intervals = taps.zipWithNext { a, b -> (b - a).toDouble() }
        val mean = intervals.average()
        if (mean <= 0.0) return null
        val variance = intervals.sumOf { (it - mean) * (it - mean) } / intervals.size
        val stdDev = kotlin.math.sqrt(variance)
        return (1.0 - stdDev / mean).coerceIn(0.0, 1.0)
    }

    /** Tempo with one decimal place, for display. */
    fun roundedBpm(bpm: Double): Double = round(bpm * 10.0) / 10.0
}
