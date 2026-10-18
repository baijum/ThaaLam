package com.baijum.thaalam.data

import kotlinx.serialization.Serializable

/** Summary of a tap-accuracy run, stored with the practice session. */
@Serializable
data class TapAccuracySummary(
    val tapCount: Int,
    val meanOffsetMs: Double,
    val meanAbsoluteOffsetMs: Double,
    val standardDeviationMs: Double,
    val earlyCount: Int,
    val lateCount: Int,
    val onTimeCount: Int,
    val toleranceMs: Double = 25.0,
    /** 21 buckets from -100 ms to +100 ms in 10 ms steps. */
    val histogram: List<Int> = emptyList(),
    /** "Tight", "Steady", "Loose" or "Wide". */
    val rating: String = "",
    /** "Centred", "Rushing" or "Dragging". */
    val tendency: String = "",
) {
    val onTimePercent: Double
        get() = if (tapCount == 0) 0.0 else onTimeCount * 100.0 / tapCount
}

/**
 * One completed transport run.
 *
 * The log deliberately stores a snapshot rather than a reference to live
 * settings: editing a preset later must not rewrite history. Duration comes
 * from a monotonic clock on the platform; epoch timestamps are only for
 * grouping sessions into local days and weeks.
 */
@Serializable
data class PracticeSession(
    val id: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val durationMs: Long,
    /** Stored base tempo, before kalam. */
    val startBpm: Double,
    val endBpm: Double,
    /** Effective akshara rate heard by the player, including kalam. */
    val effectiveStartBpm: Double = startBpm,
    val effectiveEndBpm: Double = endBpm,
    val meterLabel: String = "",
    val groupingLabel: String = "",
    val talaId: String? = null,
    val talaName: String? = null,
    val presetName: String? = null,
    val layerCount: Int = 0,
    val countInBars: Int = 0,
    val gapEnabled: Boolean = false,
    val gapPattern: List<Boolean> = emptyList(),
    val rampEnabled: Boolean = false,
    val muteProbability: Double = 0.0,
    val kalam: Int = 1,
    val kalai: Int = 1,
    val tap: TapAccuracySummary? = null,
) {
    val minutes: Double get() = durationMs / 60_000.0

    /** True when the session used any automated practice mode. */
    val usesPracticeMode: Boolean
        get() = gapEnabled || rampEnabled || muteProbability > 0.0

    /** "Misra Chapu" or "4/4 · 4" for the session list. */
    val contextLabel: String
        get() = talaName ?: buildString {
            append(meterLabel.ifBlank { "Free meter" })
            if (groupingLabel.isNotBlank()) {
                append(" · ")
                append(groupingLabel)
            }
        }

    /** "Gap", "Ramp", "Mute" or "Straight" for the session list. */
    val modeLabel: String
        get() = buildList {
            if (gapEnabled) add("Gap")
            if (rampEnabled) add("Ramp")
            if (muteProbability > 0.0) add("Mute ${(muteProbability * 100).toInt()}%")
            if (kalam > 1) add("${kalam}x")
            if (kalai > 1) add("${kalai}-kalai")
        }.joinToString(" · ").ifBlank { "Straight" }

    fun normalized(): PracticeSession {
        val safeStart = startedAtEpochMs.coerceAtLeast(0L)
        val safeDuration = durationMs.coerceIn(0L, MAX_DURATION_MS)
        val safeEnd = maxOf(
            safeStart,
            if (endedAtEpochMs > 0L) endedAtEpochMs else safeStart + safeDuration,
        )
        return copy(
            startedAtEpochMs = safeStart,
            endedAtEpochMs = safeEnd,
            durationMs = safeDuration,
            startBpm = startBpm.coerceIn(10.0, 1_600.0),
            endBpm = endBpm.coerceIn(10.0, 1_600.0),
            effectiveStartBpm = effectiveStartBpm.coerceIn(10.0, 1_600.0),
            effectiveEndBpm = effectiveEndBpm.coerceIn(10.0, 1_600.0),
            layerCount = layerCount.coerceIn(0, LayerSettings.MAX_LAYERS),
            countInBars = countInBars.coerceIn(0, 4),
            muteProbability = muteProbability.coerceIn(0.0, 1.0),
            kalam = kalam.takeIf { it in TalaPlayback.KALAM_OPTIONS } ?: 1,
            kalai = kalai.coerceIn(1, TalaPlayback.MAX_KALAI),
            tap = tap?.normalized(),
        )
    }

    companion object {
        const val MAX_DURATION_MS: Long = 12L * 60L * 60L * 1_000L
    }
}

@Serializable
data class PracticeGoals(
    val dailyMinutes: Int = 20,
    val weeklyMinutes: Int = 120,
) {
    fun normalized(): PracticeGoals {
        val daily = dailyMinutes.coerceIn(MIN_DAILY, MAX_DAILY)
        return copy(
            dailyMinutes = daily,
            weeklyMinutes = weeklyMinutes.coerceIn(daily, MAX_WEEKLY),
        )
    }

    companion object {
        const val MIN_DAILY = 5
        const val MAX_DAILY = 240
        const val MAX_WEEKLY = 1_680
    }
}

/** Everything persisted for the local practice log. */
@Serializable
data class PracticeData(
    val sessions: List<PracticeSession> = emptyList(),
    val goals: PracticeGoals = PracticeGoals(),
) {
    /** Newest first, de-duplicated, bounded and validated. */
    fun normalized(): PracticeData = copy(
        sessions = sessions
            .map { it.normalized() }
            .sortedByDescending { it.startedAtEpochMs }
            .distinctBy { it.id }
            .take(MAX_SESSIONS),
        goals = goals.normalized(),
    )

    fun withSession(session: PracticeSession): PracticeData = copy(
        sessions = (listOf(session.normalized()) + sessions)
            .distinctBy { it.id }
            .take(MAX_SESSIONS),
    ).normalized()

    fun withGoals(goals: PracticeGoals): PracticeData = copy(goals = goals).normalized()

    fun cleared(): PracticeData = copy(sessions = emptyList()).normalized()

    companion object {
        const val MAX_SESSIONS = 500
    }
}

private fun TapAccuracySummary.normalized(): TapAccuracySummary = copy(
    tapCount = tapCount.coerceAtLeast(0),
    meanOffsetMs = meanOffsetMs.coerceIn(-250.0, 250.0),
    meanAbsoluteOffsetMs = meanAbsoluteOffsetMs.coerceIn(0.0, 250.0),
    standardDeviationMs = standardDeviationMs.coerceIn(0.0, 250.0),
    earlyCount = earlyCount.coerceAtLeast(0),
    lateCount = lateCount.coerceAtLeast(0),
    onTimeCount = onTimeCount.coerceAtLeast(0),
    toleranceMs = toleranceMs.coerceIn(5.0, 100.0),
    histogram = histogram.take(21).map { it.coerceAtLeast(0) },
)
