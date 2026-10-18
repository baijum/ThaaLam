package com.baijum.thaalam.domain

import com.baijum.thaalam.data.BeatKind

/**
 * Pure metronome business logic shared by Android and iOS.
 *
 * This is the descendant of `MetronomeStateLogic` from Ukulele Companion,
 * extended with grouping-aware accent patterns and ghost/mute dynamics while
 * keeping every function free of platform imports.
 */
object MetronomeStateLogic {
    /** Coerce a BPM value into the supported range. */
    fun clampBpm(value: Double): Double =
        value.coerceIn(MetronomeSettingsConstants.MIN_BPM, MetronomeSettingsConstants.MAX_BPM)

    fun clampBpm(value: Int): Int = clampBpm(value.toDouble()).toInt()

    /** Coerce a subdivision count into the supported range (1..9). */
    fun clampSubdivision(value: Int): Int = value.coerceIn(1, 9)

    /** Coerce swing into the supported 50..75% range. */
    fun clampSwing(value: Double): Double = value.coerceIn(50.0, 75.0)

    /**
     * Cycles a pulse through its four dynamic states:
     * ACCENT → NORMAL → GHOST → MUTE → ACCENT.
     */
    fun cycleBeatType(current: BeatKind): BeatKind = when (current) {
        BeatKind.ACCENT -> BeatKind.NORMAL
        BeatKind.NORMAL -> BeatKind.GHOST
        BeatKind.GHOST -> BeatKind.MUTE
        BeatKind.MUTE -> BeatKind.ACCENT
    }

    /**
     * Accent pattern where the first pulse of every group is accented.
     *
     * For 4/4 this is a single accent on beat one; for 6/8 grouped 3+3 it
     * accents pulses one and four; for an additive 7/8 (2+2+3) it accents
     * pulses one, three and five.
     */
    fun groupAccentPattern(beats: Int, grouping: List<Int>): List<BeatKind> {
        val safeBeats = beats.coerceAtLeast(1)
        val pattern = MutableList(safeBeats) { BeatKind.NORMAL }
        var index = 0
        grouping.forEach { groupLength ->
            if (index in 0 until safeBeats) pattern[index] = BeatKind.ACCENT
            index += groupLength
        }
        if (pattern.isEmpty()) pattern.add(BeatKind.ACCENT)
        return pattern
    }

    /** Default accent pattern: group starts accented, everything else normal. */
    fun defaultAccentPattern(beats: Int, grouping: List<Int> = listOf(beats)): List<BeatKind> =
        groupAccentPattern(beats, grouping)

    /**
     * Resizes and validates an accent pattern to exactly [beats] entries.
     *
     * Missing entries become normal beats. The user is free to remove every
     * accent (or mute the whole bar) — a metronome must never override an
     * intentional dynamic choice while someone is editing pulses live.
     */
    fun normalizeAccentPattern(
        beats: Int,
        pattern: List<BeatKind>,
        @Suppress("UNUSED_PARAMETER") grouping: List<Int>,
    ): List<BeatKind> {
        val safeBeats = beats.coerceAtLeast(1)
        return List(safeBeats) { index -> pattern.getOrNull(index) ?: BeatKind.NORMAL }
    }

    /** Number of audible pulses in a bar. */
    fun audibleBeatCount(pattern: List<BeatKind>): Int = pattern.count { it.isAudible }

    /** True when the pattern is exactly the conventional group-accent pattern. */
    fun isGroupAccentPattern(beats: Int, grouping: List<Int>, pattern: List<BeatKind>): Boolean =
        pattern == groupAccentPattern(beats, grouping)
}

/** Constants mirrored from the data layer without creating a package cycle. */
internal object MetronomeSettingsConstants {
    const val MIN_BPM: Double = 10.0
    const val MAX_BPM: Double = 400.0
}
