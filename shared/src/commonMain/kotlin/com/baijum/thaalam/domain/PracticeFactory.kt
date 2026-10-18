package com.baijum.thaalam.domain

import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.PracticeData
import com.baijum.thaalam.data.PracticeGoals
import com.baijum.thaalam.data.PracticeSession
import com.baijum.thaalam.data.TapAccuracySummary

/**
 * Builds practice records from settings snapshots.
 *
 * Both platforms keep their own transport start/stop timestamps, but the
 * musical context (tala name, grouping, practice modes) is derived here so the
 * two logs cannot drift apart.
 */
object PracticeFactory {
    fun session(
        id: String,
        startedAtEpochMs: Long,
        endedAtEpochMs: Long,
        durationMs: Long,
        start: MetronomeSettings,
        end: MetronomeSettings,
        presetName: String?,
        tap: TapAccuracySummary?,
    ): PracticeSession {
        val startSettings = start.normalized()
        val endSettings = end.normalized()
        return PracticeSession(
            id = id,
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = endedAtEpochMs,
            durationMs = durationMs,
            startBpm = startSettings.bpm,
            endBpm = endSettings.bpm,
            effectiveStartBpm = startSettings.effectiveBpm,
            effectiveEndBpm = endSettings.effectiveBpm,
            meterLabel = startSettings.meter.label,
            groupingLabel = startSettings.meter.groupingLabel,
            talaId = startSettings.talaId,
            talaName = TalaLibrary.find(startSettings.talaId)?.name,
            presetName = presetName,
            layerCount = startSettings.layers.count { it.enabled },
            countInBars = startSettings.countInBars,
            gapEnabled = startSettings.isGapTraining,
            gapPattern = startSettings.gapPattern,
            rampEnabled = startSettings.rampEnabled,
            muteProbability = startSettings.muteProbability,
            kalam = startSettings.talaPlayback.kalam,
            kalai = startSettings.talaPlayback.kalai,
            tap = tap,
        ).normalized()
    }

    fun goals(dailyMinutes: Int, weeklyMinutes: Int): PracticeGoals =
        PracticeGoals(dailyMinutes, weeklyMinutes).normalized()

    fun data(sessions: List<PracticeSession>, goals: PracticeGoals): PracticeData =
        PracticeData(sessions = sessions, goals = goals).normalized()
}
