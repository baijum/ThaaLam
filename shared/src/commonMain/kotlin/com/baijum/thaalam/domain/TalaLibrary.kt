package com.baijum.thaalam.domain

import com.baijum.thaalam.data.Anga
import com.baijum.thaalam.data.AngaType
import com.baijum.thaalam.data.Tala
import com.baijum.thaalam.data.Tradition

/**
 * The built-in tala catalogue.
 *
 * Carnatic: all 35 Suladi Sapta Talas (7 talas × 5 jathis) plus the common
 * Chapu talas. Hindustani: Teental, Jhaptal, Rupak, Ektaal, Dadra, Keherwa,
 * Deepchandi and Tilwada. The catalogue is plain data, so the tala browser and
 * the metronome share exactly the same cycle definitions.
 */
object TalaLibrary {

    private data class Jathi(val value: Int, val name: String)

    private val jathis = listOf(
        Jathi(3, "Tisra"),
        Jathi(4, "Chaturasra"),
        Jathi(5, "Khanda"),
        Jathi(7, "Misra"),
        Jathi(9, "Sankeerna"),
    )

    private data class SaptaTala(
        val id: String,
        val name: String,
        val shape: List<AngaType>,
    )

    /** The seven Suladi talas, each expressed as a sequence of anga types. */
    private val saptaTalas = listOf(
        SaptaTala("dhruva", "Dhruva", listOf(AngaType.LAGHU, AngaType.DHRUTAM, AngaType.LAGHU, AngaType.DHRUTAM)),
        SaptaTala("matya", "Matya", listOf(AngaType.LAGHU, AngaType.DHRUTAM, AngaType.LAGHU)),
        SaptaTala("rupaka", "Rupaka", listOf(AngaType.DHRUTAM, AngaType.LAGHU)),
        SaptaTala("jhampa", "Jhampa", listOf(AngaType.LAGHU, AngaType.ANUDHRUTAM, AngaType.DHRUTAM)),
        SaptaTala("triputa", "Triputa", listOf(AngaType.LAGHU, AngaType.DHRUTAM, AngaType.DHRUTAM)),
        SaptaTala("ata", "Ata", listOf(AngaType.LAGHU, AngaType.LAGHU, AngaType.DHRUTAM, AngaType.DHRUTAM)),
        SaptaTala("eka", "Eka", listOf(AngaType.LAGHU)),
    )

    private fun angasFor(shape: List<AngaType>, laghuLength: Int): List<Anga> = shape.map { type ->
        when (type) {
            AngaType.LAGHU -> Anga(type, laghuLength)
            AngaType.DHRUTAM -> Anga(type, 2)
            AngaType.ANUDHRUTAM -> Anga(type, 1)
            else -> Anga(type, 1)
        }
    }

    private fun suladiTalas(): List<Tala> = saptaTalas.flatMap { tala ->
        jathis.map { jathi ->
            val angas = angasFor(tala.shape, jathi.value)
            val length = angas.sumOf { it.length }
            Tala(
                id = "carnatic.${tala.id}.${jathi.value}",
                name = "${tala.name} (${jathi.name})",
                tradition = Tradition.CARNATIC,
                angas = angas,
                jathi = jathi.value,
                details = "${jathi.name} jathi · $length aksharas · ${angas.joinToString(" ") { it.type.symbol + it.length }}",
            )
        }
    }

    private fun chapuTala(id: String, name: String, lengths: List<Int>, details: String): Tala =
        Tala(
            id = "carnatic.chapu.$id",
            name = name,
            tradition = Tradition.CARNATIC,
            angas = lengths.map { Anga(AngaType.CHAPU, it) },
            details = details,
        )

    private fun vibhagTala(
        id: String,
        name: String,
        lengths: List<Int>,
        markers: List<String>,
        bols: List<String> = emptyList(),
        details: String,
    ): Tala = Tala(
        id = "hindustani.$id",
        name = name,
        tradition = Tradition.HINDUSTANI,
        angas = lengths.zip(markers) { length, marker -> Anga(AngaType.VIBHAG, length, marker) },
        bols = bols,
        details = details,
    )

    private val carnaticChapu: List<Tala> = listOf(
        chapuTala(
            id = "misra",
            name = "Misra Chapu",
            lengths = listOf(3, 4),
            details = "7 aksharas · clapped as 3 + 4; the workhorse of Carnatic repertoire",
        ),
        chapuTala(
            id = "khanda",
            name = "Khanda Chapu",
            lengths = listOf(2, 3),
            details = "5 aksharas · clapped as 2 + 3",
        ),
        chapuTala(
            id = "tisra",
            name = "Tisra Chapu",
            lengths = listOf(3),
            details = "3 aksharas · a single three-akshara cycle",
        ),
    )

    private val hindustaniTalas: List<Tala> = listOf(
        vibhagTala(
            id = "teental",
            name = "Teental",
            lengths = listOf(4, 4, 4, 4),
            markers = listOf("X", "2", "0", "3"),
            bols = listOf(
                "Dha", "Dhin", "Dhin", "Dha",
                "Dha", "Dhin", "Dhin", "Dha",
                "Dha", "Tin", "Tin", "Ta",
                "Ta", "Dhin", "Dhin", "Dha",
            ),
            details = "16 aksharas · the most common Hindustani cycle, four vibhags of four",
        ),
        vibhagTala(
            id = "jhaptal",
            name = "Jhaptal",
            lengths = listOf(2, 3, 2, 3),
            markers = listOf("X", "2", "0", "3"),
            bols = listOf("Dhi", "Na", "Dhi", "Dhi", "Na", "Ti", "Na", "Dhi", "Dhi", "Na"),
            details = "10 aksharas · 2 + 3 + 2 + 3, a staple of khayal and thumri",
        ),
        vibhagTala(
            id = "rupak",
            name = "Rupak",
            lengths = listOf(3, 2, 2),
            markers = listOf("0", "2", "3"),
            bols = listOf("Tin", "Tin", "Na", "Dhin", "Na", "Dhin", "Na"),
            details = "7 aksharas · 3 + 2 + 2; sam falls on the open (khali) first vibhag",
        ),
        vibhagTala(
            id = "ektaal",
            name = "Ektaal",
            lengths = listOf(2, 2, 2, 2, 2, 2),
            markers = listOf("X", "2", "0", "3", "0", "4"),
            bols = listOf("Dhin", "Dhin", "Dha", "Ge", "Ti", "Ra", "Ki", "Ta", "Tu", "Na", "Ka", "Ta"),
            details = "12 aksharas · six vibhags of two, two khali sections",
        ),
        vibhagTala(
            id = "dadra",
            name = "Dadra",
            lengths = listOf(3, 3),
            markers = listOf("X", "0"),
            bols = listOf("Dha", "Dhin", "Na", "Dha", "Tin", "Na"),
            details = "6 aksharas · 3 + 3, light-classical and folk favourite",
        ),
        vibhagTala(
            id = "keherwa",
            name = "Keherwa",
            lengths = listOf(4, 4),
            markers = listOf("X", "0"),
            bols = listOf("Dha", "Ge", "Na", "Ti", "Na", "Ka", "Dhi", "Na"),
            details = "8 aksharas · 4 + 4, the everyday eight-beat cycle",
        ),
        vibhagTala(
            id = "deepchandi",
            name = "Deepchandi",
            lengths = listOf(3, 4, 3, 4),
            markers = listOf("X", "2", "0", "3"),
            details = "14 aksharas · 3 + 4 + 3 + 4, associated with thumri and hori",
        ),
        vibhagTala(
            id = "tilwada",
            name = "Tilwada",
            lengths = listOf(4, 4, 4, 4),
            markers = listOf("X", "2", "0", "3"),
            details = "16 aksharas · 4 + 4 + 4 + 4, a slower, ornate sixteen-beat cycle",
        ),
    )

    /** Every built-in cycle, Carnatic first. */
    val all: List<Tala> = suladiTalas() + carnaticChapu + hindustaniTalas

    private val byId: Map<String, Tala> = all.associateBy { it.id }

    fun find(id: String?): Tala? = id?.let { byId[it] }

    fun forTradition(tradition: Tradition): List<Tala> = all.filter { it.tradition == tradition }

    val carnatic: List<Tala> get() = forTradition(Tradition.CARNATIC)
    val hindustani: List<Tala> get() = forTradition(Tradition.HINDUSTANI)

    /** Number of Suladi Sapta Talas in the catalogue (always 35). */
    val suladiCount: Int get() = carnatic.count { it.jathi != null }

    /** Case-insensitive search across name, tradition, description and grouping. */
    fun search(query: String): List<Tala> {
        val q = query.trim()
        if (q.isEmpty()) return all
        return all.filter { tala ->
            tala.name.contains(q, ignoreCase = true) ||
                tala.tradition.displayName.contains(q, ignoreCase = true) ||
                tala.details.contains(q, ignoreCase = true) ||
                tala.bols.any { it.contains(q, ignoreCase = true) } ||
                tala.grouping.joinToString("+").contains(q.replace(" ", ""))
        }
    }
}
