package com.baijum.thaalam.data

import kotlinx.serialization.Serializable

/**
 * A musical meter expressed as a written time signature plus an optional
 * additive grouping.
 *
 * [beatsPerBar] counts pulses of [beatUnit] (the numerator of the written time
 * signature), so 6/8 has six pulses that default to a 3+3 grouping. Keeping the
 * numerator as the pulse count makes odd and additive meters (7/8 as 2+2+3,
 * 9/8 as 2+3+2+2, ...) as easy to express as plain 4/4.
 */
@Serializable
data class Meter(
    val beatsPerBar: Int = 4,
    val beatUnit: Int = 4,
    val grouping: List<Int> = defaultGrouping(beatsPerBar, beatUnit),
) {
    val label: String get() = "$beatsPerBar/$beatUnit"

    /** Additive grouping written as "2 + 2 + 3". */
    val groupingLabel: String get() = grouping.joinToString(" + ")

    /** True for the conventional compound meters (6/8, 9/8, 12/8, ...). */
    val isCompound: Boolean
        get() = beatUnit == 8 && beatsPerBar >= 6 && beatsPerBar % 3 == 0

    /** Number of groups when the bar is read additively. */
    val groupCount: Int get() = grouping.size

    fun withGrouping(newGrouping: List<Int>): Meter =
        normalized(beatsPerBar, beatUnit, newGrouping)

    /** Returns a structurally valid meter with every value inside its supported range. */
    fun normalized(): Meter = normalized(beatsPerBar, beatUnit, grouping)

    companion object {
        const val MIN_BEATS = 1
        const val MAX_BEATS = 32
        val beatUnits: List<Int> = listOf(2, 4, 8, 16)

        /** Common meters offered as one-tap presets. */
        val presets: List<Meter> = listOf(
            Meter(2, 4, listOf(2)),
            Meter(3, 4, listOf(3)),
            Meter(4, 4, listOf(4)),
            Meter(5, 4, listOf(2, 3)),
            Meter(6, 4, listOf(3, 3)),
            Meter(7, 4, listOf(2, 2, 3)),
            Meter(5, 8, listOf(2, 3)),
            Meter(6, 8, listOf(3, 3)),
            Meter(7, 8, listOf(2, 2, 3)),
            Meter(8, 8, listOf(3, 3, 2)),
            Meter(9, 8, listOf(3, 3, 3)),
            Meter(11, 8, listOf(3, 3, 3, 2)),
            Meter(12, 8, listOf(3, 3, 3, 3)),
        )

        /**
         * Musically sensible default grouping for a written time signature.
         *
         * Compound eighths group in threes, common additive odd meters use their
         * idiomatic grouping, and everything else groups as one block.
         */
        fun defaultGrouping(beatsPerBar: Int, beatUnit: Int): List<Int> {
            val beats = beatsPerBar.coerceIn(MIN_BEATS, MAX_BEATS)
            return when {
                beatUnit == 8 && beats >= 6 && beats % 3 == 0 -> List(beats / 3) { 3 }
                beats == 5 && beatUnit == 8 -> listOf(2, 3)
                beats == 5 && beatUnit == 4 -> listOf(2, 3)
                beats == 7 && beatUnit == 8 -> listOf(2, 2, 3)
                beats == 7 && beatUnit == 4 -> listOf(2, 2, 3)
                beats == 8 && beatUnit == 8 -> listOf(3, 3, 2)
                beats == 11 && beatUnit == 8 -> listOf(3, 3, 3, 2)
                beats >= 6 && beatUnit == 4 && beats % 3 == 0 -> List(beats / 3) { 3 }
                else -> listOf(beats)
            }
        }

        /** Builds a normalized meter, repairing an invalid grouping if necessary. */
        fun normalized(beatsPerBar: Int, beatUnit: Int, grouping: List<Int>?): Meter {
            val beats = beatsPerBar.coerceIn(MIN_BEATS, MAX_BEATS)
            val unit = beatUnit.takeIf { it in beatUnits } ?: 4
            val candidate = grouping
                ?.filter { it > 0 }
                ?.takeIf { it.isNotEmpty() && it.sum() == beats }
                ?: defaultGrouping(beats, unit)
            return Meter(beats, unit, candidate)
        }

        /** Parses a "6/8" style label, returning null when it is not a usable meter. */
        fun parse(label: String): Meter? {
            val parts = label.split('/')
            if (parts.size != 2) return null
            val beats = parts[0].trim().toIntOrNull() ?: return null
            val unit = parts[1].trim().toIntOrNull() ?: return null
            if (beats !in MIN_BEATS..MAX_BEATS || unit !in beatUnits) return null
            return normalized(beats, unit, null)
        }
    }
}
