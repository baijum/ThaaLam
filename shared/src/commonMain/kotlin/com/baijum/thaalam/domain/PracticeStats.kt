package com.baijum.thaalam.domain

import com.baijum.thaalam.data.PracticeData
import com.baijum.thaalam.data.PracticeSession
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** One day in the recent-practice strip. */
data class PracticeDay(
    /** ISO local date, for example "2026-10-18". */
    val date: String,
    val minutes: Double,
    val isToday: Boolean,
)

/** Best tempo reached for one tala or preset. */
data class TempoBest(
    val label: String,
    val bpm: Double,
    val sessions: Int,
)

/** Local, offline practice statistics shared by both platforms. */
data class PracticeStats(
    val todayMinutes: Double = 0.0,
    val weekMinutes: Double = 0.0,
    val totalMinutes: Double = 0.0,
    val sessionCount: Int = 0,
    val streakDays: Int = 0,
    val dailyGoalMinutes: Int = 20,
    val weeklyGoalMinutes: Int = 120,
    val todayProgress: Double = 0.0,
    val weekProgress: Double = 0.0,
    val recentDays: List<PracticeDay> = emptyList(),
    val bestByTala: List<TempoBest> = emptyList(),
    val bestByPreset: List<TempoBest> = emptyList(),
    val lastSession: PracticeSession? = null,
) {
    val hasSessions: Boolean get() = sessionCount > 0
}

/**
 * Computes goals, streaks and best tempos from the local log.
 *
 * Sessions are split at local midnight before minutes are attributed to a day,
 * so an overnight practice does not inflate one date. The time zone is passed in
 * explicitly so the calculation is deterministic in tests and can follow the
 * device's current zone in the app.
 */
object PracticeStatsCalculator {
    fun compute(
        data: PracticeData,
        nowEpochMs: Long,
        zoneId: String,
    ): PracticeStats {
        val normalized = data.normalized()
        val zone = runCatching { TimeZone.of(zoneId) }.getOrDefault(TimeZone.UTC)
        val today = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(zone).date
        val weekStart = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

        val minutesByDay = mutableMapOf<LocalDate, Double>()
        normalized.sessions.forEach { session ->
            addSessionMinutes(session, zone, minutesByDay)
        }

        val todayMinutes = minutesByDay[today] ?: 0.0
        val weekMinutes = minutesByDay.entries
            .filter { it.key >= weekStart && it.key <= today }
            .sumOf { it.value }
        val totalMinutes = normalized.sessions.sumOf { it.durationMs } / 60_000.0
        val goals = normalized.goals

        var streakCursor = today
        if ((minutesByDay[streakCursor] ?: 0.0) <= 0.0) {
            streakCursor = streakCursor.minus(1, DateTimeUnit.DAY)
        }
        var streak = 0
        while ((minutesByDay[streakCursor] ?: 0.0) > 0.0) {
            streak++
            streakCursor = streakCursor.minus(1, DateTimeUnit.DAY)
        }

        return PracticeStats(
            todayMinutes = todayMinutes,
            weekMinutes = weekMinutes,
            totalMinutes = totalMinutes,
            sessionCount = normalized.sessions.size,
            streakDays = streak,
            dailyGoalMinutes = goals.dailyMinutes,
            weeklyGoalMinutes = goals.weeklyMinutes,
            todayProgress = progress(todayMinutes, goals.dailyMinutes),
            weekProgress = progress(weekMinutes, goals.weeklyMinutes),
            recentDays = (DAYS_IN_STRIP - 1 downTo 0).map { offset ->
                val day = today.minus(offset, DateTimeUnit.DAY)
                PracticeDay(
                    date = day.toString(),
                    minutes = minutesByDay[day] ?: 0.0,
                    isToday = offset == 0,
                )
            },
            bestByTala = bestTempos(normalized.sessions) { it.talaName },
            bestByPreset = bestTempos(normalized.sessions) { it.presetName },
            lastSession = normalized.sessions.firstOrNull(),
        )
    }

    fun compute(data: PracticeData, nowEpochMs: Long): PracticeStats =
        compute(data, nowEpochMs, TimeZone.currentSystemDefault().id)

    private fun addSessionMinutes(
        session: PracticeSession,
        zone: TimeZone,
        minutesByDay: MutableMap<LocalDate, Double>,
    ) {
        val startEpoch = session.startedAtEpochMs
        val endEpoch = maxOf(session.endedAtEpochMs, startEpoch + session.durationMs)
        if (endEpoch <= startEpoch) return
        val start = Instant.fromEpochMilliseconds(startEpoch)
        val end = Instant.fromEpochMilliseconds(endEpoch)
        var day = start.toLocalDateTime(zone).date
        val endDay = end.toLocalDateTime(zone).date
        var guard = 0
        while (day <= endDay && guard++ < MAX_DAYS_PER_SESSION) {
            val dayStart = day.atStartOfDayIn(zone)
            val dayEnd = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            val from = if (start > dayStart) start else dayStart
            val to = if (end < dayEnd) end else dayEnd
            if (to > from) {
                val minutes = (to - from).inWholeMilliseconds / 60_000.0
                minutesByDay[day] = (minutesByDay[day] ?: 0.0) + minutes
            }
            day = day.plus(1, DateTimeUnit.DAY)
        }
    }

    private fun bestTempos(
        sessions: List<PracticeSession>,
        label: (PracticeSession) -> String?,
    ): List<TempoBest> {
        val grouped = mutableMapOf<String, MutableList<Double>>()
        sessions.forEach { session ->
            val name = label(session)?.takeIf { it.isNotBlank() } ?: return@forEach
            val bpm = maxOf(
                session.effectiveStartBpm,
                session.effectiveEndBpm,
                session.startBpm,
                session.endBpm,
            )
            grouped.getOrPut(name) { mutableListOf() }.add(bpm)
        }
        return grouped.map { (name, bpms) ->
            TempoBest(label = name, bpm = bpms.max(), sessions = bpms.size)
        }.sortedByDescending { it.bpm }.take(MAX_BEST_ENTRIES)
    }

    private fun progress(minutes: Double, goal: Int): Double =
        if (goal <= 0) 0.0 else (minutes / goal).coerceIn(0.0, 1.0)

    private const val DAYS_IN_STRIP = 7
    private const val MAX_BEST_ENTRIES = 5
    private const val MAX_DAYS_PER_SESSION = 8
}
