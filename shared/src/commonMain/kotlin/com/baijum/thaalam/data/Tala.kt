package com.baijum.thaalam.data

import kotlinx.serialization.Serializable

/** Rhythmic tradition a cycle belongs to. */
@Serializable
enum class Tradition(val displayName: String) {
    CARNATIC("Carnatic"),
    HINDUSTANI("Hindustani");

    companion object {
        val all: List<Tradition> = entries
    }
}

/** The building blocks of a tala cycle. */
@Serializable
enum class AngaType(val displayName: String, val symbol: String) {
    /** Carnatic laghu: one clap followed by (jathi - 1) finger counts. */
    LAGHU("Laghu", "I"),
    /** Carnatic dhrutam: one clap and one wave (always 2 aksharas). */
    DHRUTAM("Dhrutam", "O"),
    /** Carnatic anudhrutam: a single clap (always 1 akshara). */
    ANUDHRUTAM("Anudhrutam", "U"),
    /** A Chapu tala limb, grouped by claps rather than the Suladi anga system. */
    CHAPU("Chapu", "|"),
    /** Hindustani vibhag: a section of a cycle marked by sam, tali or khali. */
    VIBHAG("Vibhag", "|");

    val isCarnatic: Boolean get() = this != VIBHAG
}

/**
 * One limb (anga / vibhag) of a tala cycle.
 *
 * [marker] carries the Hindustani section marker: "X" for sam, "2", "3", "4"
 * for tali and "0" for khali. Carnatic angas leave it null and rely on [type].
 */
@Serializable
data class Anga(
    val type: AngaType,
    val length: Int,
    val marker: String? = null,
) {
    init {
        require(length > 0) { "An anga must contain at least one akshara" }
    }

    val label: String
        get() = when (type) {
            AngaType.LAGHU -> "Laghu $length"
            AngaType.DHRUTAM -> "Dhrutam"
            AngaType.ANUDHRUTAM -> "Anudhrutam"
            AngaType.CHAPU -> "Chapu $length"
            AngaType.VIBHAG -> "Vibhag $length"
        }
}

/**
 * A complete rhythmic cycle: the Carnatic Suladi Sapta Talas and Chapu talas,
 * or the Hindustani Teental, Jhaptal, Rupak and friends.
 *
 * [aksharas] is the cycle length in counted pulses. Each anga starts on an
 * accented pulse; Hindustani khali sections are rendered as [BeatKind.GHOST]
 * so the open-hand wave is audible but softer — exactly how it feels under a
 * tabla theka.
 */
@Serializable
data class Tala(
    val id: String,
    val name: String,
    val tradition: Tradition,
    val angas: List<Anga>,
    /** Jathi for Carnatic talas (3, 4, 5, 7 or 9). */
    val jathi: Int? = null,
    /** Optional theka syllables, one per akshara. */
    val bols: List<String> = emptyList(),
    val details: String = "",
) {
    init {
        require(angas.isNotEmpty()) { "A tala needs at least one anga" }
    }

    /** Total length of the cycle in aksharas. */
    val aksharas: Int get() = angas.sumOf { it.length }

    /** Additive grouping of the cycle, e.g. [4, 4, 4, 4] for Teental. */
    val grouping: List<Int> get() = angas.map { it.length }

    /** Zero-based akshara index where each anga begins. */
    val angaStarts: List<Int>
        get() {
            var index = 0
            return angas.map { anga ->
                val start = index
                index += anga.length
                start
            }
        }

    /** Accent map for the whole cycle. */
    val beatKinds: List<BeatKind>
        get() {
            val kinds = MutableList(aksharas) { BeatKind.NORMAL }
            angas.forEachIndexed { angaIndex, anga ->
                val start = angaStarts[angaIndex]
                val open = anga.marker == "0"
                kinds[start] = if (open) BeatKind.GHOST else BeatKind.ACCENT
            }
            return kinds
        }

    /** Marker shown above each anga start (Carnatic symbols or Hindustani X/2/3/0). */
    val markers: List<String?>
        get() = angas.map { anga ->
            anga.marker ?: when (anga.type) {
                AngaType.LAGHU -> "I"
                AngaType.DHRUTAM -> "O"
                AngaType.ANUDHRUTAM -> "U"
                AngaType.CHAPU -> "|"
                AngaType.VIBHAG -> "|"
            }
        }

    /** The theka syllable at an akshara, or null when the tala has no bols. */
    fun bolAt(index: Int): String? = bols.getOrNull(index)

    /** Section marker at an anga start ("X", "2", "0", "I"…), else null. */
    fun markerAt(index: Int): String? {
        val angaIndex = angaStarts.indexOf(index)
        return if (angaIndex >= 0) markers.getOrNull(angaIndex) else null
    }

    /** Best short label for an akshara: section marker, theka bol, or beat number. */
    fun labelAt(index: Int): String = markerAt(index) ?: bolAt(index) ?: (index + 1).toString()

    /** "4 + 4 + 4 + 4" */
    val groupingLabel: String get() = grouping.joinToString(" + ")

    /** "I O I O" style display for Carnatic, "X 2 0 3" for Hindustani. */
    val angaLabel: String
        get() = angas.joinToString(" ") { anga ->
            anga.marker ?: when (anga.type) {
                AngaType.LAGHU -> "I${anga.length}"
                AngaType.DHRUTAM -> "O"
                AngaType.ANUDHRUTAM -> "U"
                AngaType.CHAPU -> "|${anga.length}"
                AngaType.VIBHAG -> "|${anga.length}"
            }
        }
}
