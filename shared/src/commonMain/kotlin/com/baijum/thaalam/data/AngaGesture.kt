package com.baijum.thaalam.data

import kotlinx.serialization.Serializable

/**
 * The hand gesture used for one akshara of a tala.
 *
 * Carnatic talas are counted with claps, finger counts and waves; Hindustani
 * vibhags are marked with tali (clap) and khali (wave). ThaaLam uses the gesture
 * to pick a distinct click timbre, so a laghu does not sound like a flat run of
 * identical pulses. [NONE] means the akshara carries no gesture (for example the
 * inner aksharas of a Hindustani vibhag, which are filled by the theka).
 */
@Serializable
enum class AngaGesture(val displayName: String, val shortLabel: String, val symbol: String) {
    NONE("None", "", ""),
    CLAP("Clap", "Clap", "●"),
    FINGER("Finger count", "Fing", "○"),
    WAVE("Wave", "Wave", "◐"),
    TALI("Tali", "Tali", "X"),
    KHALI("Khali", "Khali", "0");

    /** True for the open-hand khali gesture, which is rendered softer. */
    val isOpen: Boolean get() = this == KHALI || this == WAVE

    companion object {
        val audible: List<AngaGesture> = entries.filter { it != NONE }
    }
}
