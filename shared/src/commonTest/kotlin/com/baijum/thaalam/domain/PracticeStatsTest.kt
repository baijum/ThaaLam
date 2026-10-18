package com.baijum.thaalam.domain

import com.baijum.thaalam.data.PracticeData
import com.baijum.thaalam.data.PracticeGoals
import com.baijum.thaalam.data.PracticeSession
import com.baijum.thaalam.data.TapAccuracySummary
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PracticeStatsTest {

    private val zone = TimeZone.of("Asia/Kolkata")
    private val zoneId = "Asia/Kolkata"

    private fun at(date: LocalDate, hour: Int = 0, minute: Int = 0): Long =
        date.atStartOfDayIn(zone).toEpochMilliseconds() + hour * 3_600_000L + minute * 60_000L

    private fun session(
        id: String,
        startedAt: Long,
        durationMs: Long,
        bpm: Double = 100.0,
        tala: String? = null,
        preset: String? = null,
        effectiveBpm: Double = bpm,
        tap: TapAccuracySummary? = null,
    ): PracticeSession = PracticeSession(
        id = id,
        startedAtEpochMs = startedAt,
        endedAtEpochMs = startedAt + durationMs,
        durationMs = durationMs,
        startBpm = bpm,
        endBpm = bpm,
        effectiveStartBpm = effectiveBpm,
        effectiveEndBpm = effectiveBpm,
        meterLabel = "4/4",
        groupingLabel = "4",
        talaId = tala?.lowercase(),
        talaName = tala,
        presetName = preset,
        tap = tap,
    )

    @Test
    fun `today week and total minutes are computed from the local log`() {
        val today = LocalDate(2026, 10, 18)
        val yesterday = LocalDate(2026, 10, 17)
        val data = PracticeData(
            sessions = listOf(
                session("a", at(today, 9), 30 * 60_000L),
                session("b", at(yesterday, 20), 20 * 60_000L),
                session("c", at(LocalDate(2026, 10, 5), 12), 10 * 60_000L),
            ),
        )
        val stats = PracticeStatsCalculator.compute(data, at(today, 15), zoneId)
        assertEquals(30.0, stats.todayMinutes, 0.001)
        assertEquals(50.0, stats.weekMinutes, 0.001)
        assertEquals(60.0, stats.totalMinutes, 0.001)
        assertEquals(3, stats.sessionCount)
        assertEquals(2, stats.streakDays)
        assertEquals(1.0, stats.todayProgress, 0.001)
        assertEquals(0.416, stats.weekProgress, 0.001)
    }

    @Test
    fun `a session crossing midnight is split between two local days`() {
        val today = LocalDate(2026, 10, 18)
        val start = at(today, 23, 40)
        val data = PracticeData(
            sessions = listOf(session("late", start, 40 * 60_000L)),
        )
        val stats = PracticeStatsCalculator.compute(data, at(LocalDate(2026, 10, 19), 10), zoneId)
        assertEquals(20.0, stats.todayMinutes, 0.001)
        assertEquals(40.0, stats.totalMinutes, 0.001)
        assertEquals(2, stats.streakDays)
        assertEquals("2026-10-18", stats.recentDays[5].date)
        assertEquals("2026-10-19", stats.recentDays[6].date)
        assertTrue(stats.recentDays[6].isToday)
    }

    @Test
    fun `streak survives a day that has not been practised yet`() {
        val today = LocalDate(2026, 10, 18)
        val data = PracticeData(
            sessions = listOf(
                session("a", at(LocalDate(2026, 10, 16), 9), 10 * 60_000L),
                session("b", at(LocalDate(2026, 10, 17), 9), 10 * 60_000L),
            ),
        )
        val stats = PracticeStatsCalculator.compute(data, at(today, 8), zoneId)
        assertEquals(0.0, stats.todayMinutes, 0.001)
        assertEquals(2, stats.streakDays)
    }

    @Test
    fun `best tempo is tracked per tala and preset`() {
        val today = LocalDate(2026, 10, 18)
        val data = PracticeData(
            sessions = listOf(
                session("a", at(today, 9), 60_000L, bpm = 100.0, tala = "Misra Chapu", effectiveBpm = 120.0),
                session("b", at(today, 10), 60_000L, bpm = 100.0, tala = "Misra Chapu", effectiveBpm = 160.0),
                session("c", at(today, 11), 60_000L, bpm = 90.0, preset = "Warm-up", effectiveBpm = 90.0),
            ),
        )
        val stats = PracticeStatsCalculator.compute(data, at(today, 12), zoneId)
        val talaBest = stats.bestByTala.single()
        assertEquals("Misra Chapu", talaBest.label)
        assertEquals(160.0, talaBest.bpm)
        assertEquals(2, talaBest.sessions)
        assertEquals("Warm-up", stats.bestByPreset.single().label)
    }

    @Test
    fun `empty history produces zeroed stats`() {
        val stats = PracticeStatsCalculator.compute(PracticeData(), 0L, zoneId)
        assertEquals(0.0, stats.totalMinutes)
        assertEquals(0, stats.sessionCount)
        assertEquals(0, stats.streakDays)
        assertEquals(7, stats.recentDays.size)
        assertNull(stats.lastSession)
    }

    @Test
    fun `goals are normalized against each other`() {
        val goals = PracticeGoals(dailyMinutes = 999, weeklyMinutes = 1).normalized()
        assertEquals(PracticeGoals.MAX_DAILY, goals.dailyMinutes)
        assertEquals(PracticeGoals.MAX_DAILY, goals.weeklyMinutes)
    }

    @Test
    fun `practice data keeps the newest five hundred sessions`() {
        val sessions = (0 until 510).map { index ->
            session("s$index", 1_000L + index, 1_000L)
        }
        val normalized = PracticeData(sessions = sessions).normalized()
        assertEquals(PracticeData.MAX_SESSIONS, normalized.sessions.size)
        assertEquals("s509", normalized.sessions.first().id)
    }

    @Test
    fun `session normalization repairs timestamps and ranges`() {
        val session = session("bad", 1_000L, 2_000L, bpm = 99_999.0, effectiveBpm = 1.0)
            .copy(layerCount = 12, countInBars = 99, kalam = 3, kalai = 9)
            .normalized()
        assertEquals(2_000L, session.durationMs)
        assertEquals(1_600.0, session.startBpm)
        assertEquals(10.0, session.effectiveStartBpm)
        assertEquals(3, session.layerCount)
        assertEquals(4, session.countInBars)
        assertEquals(1, session.kalam)
        assertEquals(2, session.kalai)
    }

    @Test
    fun `practice json round trips and survives old or corrupt payloads`() {
        val data = PracticeData(
            sessions = listOf(
                session(
                    "a",
                    at(LocalDate(2026, 10, 18), 9),
                    20 * 60_000L,
                    tala = "Teental",
                    tap = TapAccuracySummary(
                        tapCount = 4,
                        meanOffsetMs = 5.0,
                        meanAbsoluteOffsetMs = 5.0,
                        standardDeviationMs = 2.0,
                        earlyCount = 0,
                        lateCount = 0,
                        onTimeCount = 4,
                        histogram = List(21) { 0 },
                        rating = "Tight",
                        tendency = "Centred",
                    ),
                ),
            ),
            goals = PracticeGoals(15, 90),
        )
        val decoded = PracticeCodec.decode(PracticeCodec.encode(data))
        assertEquals(1, decoded.sessions.size)
        assertEquals(15, decoded.goals.dailyMinutes)
        assertNotNull(decoded.sessions.single().tap)
        assertTrue(PracticeCodec.decode("not json").sessions.isEmpty())
        assertTrue(PracticeCodec.decode(null).sessions.isEmpty())
    }

    @Test
    fun `practice csv escapes commas and includes tap columns`() {
        val data = PracticeData(
            sessions = listOf(
                session(
                    "a",
                    at(LocalDate(2026, 10, 18), 9),
                    60_000L,
                    tala = "Misra, Chapu",
                    tap = TapAccuracySummary(
                        tapCount = 2,
                        meanOffsetMs = 10.0,
                        meanAbsoluteOffsetMs = 10.0,
                        standardDeviationMs = 1.0,
                        earlyCount = 0,
                        lateCount = 0,
                        onTimeCount = 2,
                    ),
                ),
            ),
        )
        val csv = PracticeCodec.toCsv(data)
        assertTrue(csv.startsWith("startedAtEpochMs"))
        assertTrue(csv.contains("\"Misra, Chapu\""))
        assertTrue(csv.contains(",2,10,10"))
    }
}
