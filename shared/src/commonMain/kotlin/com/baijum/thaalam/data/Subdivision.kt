package com.baijum.thaalam.data

/**
 * Rhythmic subdivision of one pulse (one denominator unit of the meter).
 *
 * [ticksPerBeat] is the number of clicks inside a single pulse. For a 4/4 bar
 * an [EIGHTH] subdivision produces two clicks per pulse (eight notes), a
 * [TRIPLET] three, and so on. Quintuplets, sextuplets, septuplets and nonuplets
 * are first-class citizens rather than afterthoughts.
 */
enum class Subdivision(val ticksPerBeat: Int, val displayName: String, val shortLabel: String) {
    QUARTER(1, "Quarter", "1/4"),
    EIGHTH(2, "Eighth", "1/8"),
    TRIPLET(3, "Triplet", "1/8T"),
    SIXTEENTH(4, "Sixteenth", "1/16"),
    QUINTUPLET(5, "Quintuplet", "1/16Q"),
    SEXTUPLET(6, "Sextuplet", "1/16S"),
    SEPTUPLET(7, "Septuplet", "1/16P"),
    THIRTY_SECOND(8, "Thirty-second", "1/32"),
    NONUPLET(9, "Nonuplet", "1/16N");

    /** True when this subdivision divides the pulse into an even number of parts. */
    val isEvenlyDivisible: Boolean get() = ticksPerBeat % 2 == 0

    companion object {
        val maxTicks: Int = entries.maxOf { it.ticksPerBeat }

        fun fromTicks(ticks: Int): Subdivision =
            entries.firstOrNull { it.ticksPerBeat == ticks.coerceIn(1, maxTicks) } ?: QUARTER

        /** The Indian classical *nadai* / *gathi* values that map directly onto subdivisions. */
        val nadaiValues: List<Subdivision> = listOf(TRIPLET, SIXTEENTH, QUINTUPLET, SEPTUPLET, NONUPLET)
    }
}
