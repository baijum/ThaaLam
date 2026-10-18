package com.baijum.thaalam.domain

import com.baijum.thaalam.data.TapAccuracySummary
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Grades taps against the main-beat grid emitted by the transport.
 *
 * Targets are host-clock milliseconds supplied by the platform engine, already
 * compensated for output latency. A tap is matched to the nearest unused target
 * within [maxOffsetMs]. The grader itself is platform-free so it can be tested
 * on a simulated clock.
 */
class TapGrader(
    private val toleranceMs: Double = DEFAULT_TOLERANCE_MS,
    private val maxOffsetMs: Double = DEFAULT_MAX_OFFSET_MS,
) {
    private val targets = ArrayDeque<Double>()
    private val offsets = ArrayList<Double>(64)

    /** Signed offset of the most recent graded tap: positive means late. */
    var lastOffsetMs: Double? = null
        private set

    /** Adds one expected beat time. Old unmatched targets are discarded. */
    fun addTarget(timeMs: Double) {
        targets.addLast(timeMs)
        while (targets.isNotEmpty() && targets.first() < timeMs - TARGET_RETENTION_MS) {
            targets.removeFirst()
        }
        while (targets.size > MAX_TARGETS) targets.removeFirst()
    }

    /**
     * Grades a tap at [timeMs]. Returns the signed offset (positive = late), or
     * null when no target is close enough.
     */
    fun addTap(timeMs: Double): Double? {
        if (targets.isEmpty()) return null
        var bestIndex = -1
        var bestDistance = Double.MAX_VALUE
        targets.forEachIndexed { index, target ->
            val distance = abs(target - timeMs)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }
        if (bestIndex < 0 || bestDistance > maxOffsetMs) return null
        val target = targets.removeAt(bestIndex)
        val offset = timeMs - target
        offsets += offset
        lastOffsetMs = offset
        return offset
    }

    fun tapCount(): Int = offsets.size

    fun summary(): TapAccuracySummary? {
        if (offsets.isEmpty()) return null
        val mean = offsets.average()
        val meanAbsolute = offsets.sumOf { abs(it) } / offsets.size
        val variance = offsets.sumOf { (it - mean) * (it - mean) } / offsets.size
        val early = offsets.count { it < -toleranceMs }
        val late = offsets.count { it > toleranceMs }
        val onTime = offsets.size - early - late
        val histogram = MutableList(HISTOGRAM_BUCKETS) { 0 }
        offsets.forEach { offset ->
            val clamped = offset.coerceIn(-HISTOGRAM_RANGE_MS, HISTOGRAM_RANGE_MS)
            val index = ((clamped + HISTOGRAM_RANGE_MS) / HISTOGRAM_BUCKET_MS)
                .roundToInt()
                .coerceIn(0, HISTOGRAM_BUCKETS - 1)
            histogram[index] = histogram[index] + 1
        }
        return TapAccuracySummary(
            tapCount = offsets.size,
            meanOffsetMs = mean,
            meanAbsoluteOffsetMs = meanAbsolute,
            standardDeviationMs = sqrt(variance),
            earlyCount = early,
            lateCount = late,
            onTimeCount = onTime,
            toleranceMs = toleranceMs,
            histogram = histogram,
            rating = rating(meanAbsolute),
            tendency = tendency(mean),
        )
    }

    fun reset() {
        targets.clear()
        offsets.clear()
        lastOffsetMs = null
    }

    companion object {
        const val DEFAULT_TOLERANCE_MS: Double = 25.0
        const val DEFAULT_MAX_OFFSET_MS: Double = 250.0
        private const val TARGET_RETENTION_MS = 2_000.0
        private const val MAX_TARGETS = 32
        private const val HISTOGRAM_BUCKETS = 21
        private const val HISTOGRAM_RANGE_MS = 100.0
        private const val HISTOGRAM_BUCKET_MS = 10.0

        /** "Tight", "Steady", "Loose" or "Wide". */
        fun rating(meanAbsoluteMs: Double): String = when {
            meanAbsoluteMs <= 12.0 -> "Tight"
            meanAbsoluteMs <= 25.0 -> "Steady"
            meanAbsoluteMs <= 45.0 -> "Loose"
            else -> "Wide"
        }

        /** "Rushing", "Dragging" or "Centred". */
        fun tendency(meanOffsetMs: Double): String = when {
            meanOffsetMs < -15.0 -> "Rushing"
            meanOffsetMs > 15.0 -> "Dragging"
            else -> "Centred"
        }
    }
}
