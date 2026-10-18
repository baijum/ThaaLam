package com.baijum.thaalam.data

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * How a tala is rendered, independently of its base tempo.
 *
 * - [kalam] is the speed of the akshara stream: 1x, 2x or 4x. The base BPM is
 *   preserved, so switching kalam never loses the tempo the user typed.
 * - [kalai] is the number of internal pulses per akshara: 1-kalai clicks once
 *   per akshara, 2-kalai clicks twice. It multiplies the nadai/subdivision.
 * - [eduppuOffsetAksharas] is where the transport starts inside the cycle.
 *   `0.0` is sam; negative values start before sam (atita) and positive values
 *   start after sam (anagata). The count-in is aligned so that its last click
 *   lands immediately before this point.
 *
 * The value is additive JSON with defaults, so a v0.2 settings blob (which has
 * no `talaPlayback` field) loads as plain sam / 1x / 1-kalai.
 */
@Serializable
data class TalaPlayback(
    val kalam: Int = 1,
    val kalai: Int = 1,
    val eduppuOffsetAksharas: Double = 0.0,
) {
    /** Base-BPM multiplier applied by [MetronomeSettings.effectiveBpm]. */
    val kalamFactor: Double get() = kalam.toDouble()

    val isDefault: Boolean
        get() = kalam == 1 && kalai == 1 && eduppuOffsetAksharas == 0.0

    fun normalized(): TalaPlayback = copy(
        kalam = kalam.takeIf { it in KALAM_OPTIONS } ?: 1,
        kalai = kalai.coerceIn(1, MAX_KALAI),
        eduppuOffsetAksharas = snapEduppu(eduppuOffsetAksharas),
    )

    /** "1x", "2x", "4x". */
    val kalamLabel: String get() = "${kalam}x"

    /** "1-kalai", "2-kalai". */
    val kalaiLabel: String get() = "$kalai-kalai"

    /** Human label for the current eduppu, e.g. "Sam" or "½ before sam". */
    val eduppuLabel: String get() = eduppuLabel(eduppuOffsetAksharas)

    /** Short state description used by accessibility labels. */
    val summary: String
        get() = buildString {
            append(kalamLabel)
            append(" · ")
            append(kalaiLabel)
            if (eduppuOffsetAksharas != 0.0) {
                append(" · ")
                append(eduppuLabel)
            }
        }

    companion object {
        const val MAX_KALAI: Int = 2
        const val MIN_EDUPPU_AKSHARAS: Double = -8.0
        const val MAX_EDUPPU_AKSHARAS: Double = 8.0
        const val EDUPPU_STEP_AKSHARAS: Double = 0.25

        val KALAM_OPTIONS: List<Int> = listOf(1, 2, 4)
        val KALAI_OPTIONS: List<Int> = listOf(1, 2)

        /** Common eduppu positions offered as one-tap choices. */
        val EDUPPU_OPTIONS: List<Double> = listOf(0.0, -0.5, -1.0, 0.5, 1.0)

        /** Rounds to a musically readable quarter-akshara and clamps the range. */
        fun snapEduppu(value: Double): Double {
            val clamped = value.coerceIn(MIN_EDUPPU_AKSHARAS, MAX_EDUPPU_AKSHARAS)
            val snapped = (clamped / EDUPPU_STEP_AKSHARAS).roundToInt() * EDUPPU_STEP_AKSHARAS
            // Avoid -0.0 in labels and equality checks.
            return if (snapped == 0.0) 0.0 else snapped
        }

        /** "Sam", "½ before sam", "1 after sam", … */
        fun eduppuLabel(value: Double): String {
            val snapped = snapEduppu(value)
            if (snapped == 0.0) return "Sam"
            val amount = formatAksharas(kotlin.math.abs(snapped))
            return if (snapped < 0.0) "$amount before sam" else "$amount after sam"
        }

        private fun formatAksharas(value: Double): String = when (value) {
            0.25 -> "¼"
            0.5 -> "½"
            0.75 -> "¾"
            1.0 -> "1"
            else -> {
                val rounded = (value * 100.0).roundToInt() / 100.0
                if (rounded == rounded.toInt().toDouble()) {
                    rounded.toInt().toString()
                } else {
                    rounded.toString()
                }
            }
        }
    }
}
