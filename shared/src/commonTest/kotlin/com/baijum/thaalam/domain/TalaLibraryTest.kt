package com.baijum.thaalam.domain

import com.baijum.thaalam.data.AngaGesture
import com.baijum.thaalam.data.BeatKind
import com.baijum.thaalam.data.MetronomeSettings
import com.baijum.thaalam.data.Subdivision
import com.baijum.thaalam.data.Tala
import com.baijum.thaalam.data.TalaPlayback
import com.baijum.thaalam.data.Tradition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TalaLibraryTest {

    @Test
    fun `all 35 suladi sapta talas are generated`() {
        assertEquals(35, TalaLibrary.suladiCount)
        assertEquals(35, TalaLibrary.carnatic.count { it.jathi != null })
    }

    @Test
    fun `tala ids are unique`() {
        val ids = TalaLibrary.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `chaturasra sapta talas have the canonical lengths`() {
        fun lengthOf(id: String): Int? = TalaLibrary.find("carnatic.$id.4")?.aksharas
        assertEquals(12, lengthOf("dhruva"))
        assertEquals(10, lengthOf("matya"))
        assertEquals(6, lengthOf("rupaka"))
        assertEquals(7, lengthOf("jhampa"))
        assertEquals(8, lengthOf("triputa"))
        assertEquals(12, lengthOf("ata"))
        assertEquals(4, lengthOf("eka"))
    }

    @Test
    fun `jathi scales the laghu length`() {
        assertEquals(3, TalaLibrary.find("carnatic.eka.3")?.aksharas)
        assertEquals(5, TalaLibrary.find("carnatic.eka.5")?.aksharas)
        assertEquals(7, TalaLibrary.find("carnatic.eka.7")?.aksharas)
        assertEquals(9, TalaLibrary.find("carnatic.eka.9")?.aksharas)
    }

    @Test
    fun `jhampa is laghu anudhrutam dhrutam`() {
        val tala = TalaLibrary.find("carnatic.jhampa.4")
        assertNotNull(tala)
        assertEquals(listOf(4, 1, 2), tala.grouping)
        assertEquals(7, tala.aksharas)
    }

    @Test
    fun `teental has four vibhags and canonical accents`() {
        val teental = TalaLibrary.find("hindustani.teental")
        assertNotNull(teental)
        assertEquals(16, teental.aksharas)
        assertEquals(listOf(4, 4, 4, 4), teental.grouping)
        assertEquals(listOf("X", "2", "0", "3"), teental.markers)
        assertEquals(BeatKind.ACCENT, teental.beatKinds[0])
        assertEquals(BeatKind.ACCENT, teental.beatKinds[4])
        assertEquals(BeatKind.GHOST, teental.beatKinds[8])
        assertEquals(BeatKind.ACCENT, teental.beatKinds[12])
        assertEquals(BeatKind.NORMAL, teental.beatKinds[1])
    }

    @Test
    fun `rupak starts on an open khali beat`() {
        val rupak = TalaLibrary.find("hindustani.rupak")
        assertNotNull(rupak)
        assertEquals(7, rupak.aksharas)
        assertEquals(BeatKind.GHOST, rupak.beatKinds[0])
        assertEquals(listOf("0", "2", "3"), rupak.markers)
    }

    @Test
    fun `theka bols match the cycle length where provided`() {
        TalaLibrary.hindustani.filter { it.bols.isNotEmpty() }.forEach { tala ->
            assertEquals(tala.aksharas, tala.bols.size, "bols for ${tala.name}")
        }
    }

    @Test
    fun `tilwada and jhoomra carry theka bols`() {
        val tilwada = TalaLibrary.find("hindustani.tilwada")
        assertNotNull(tilwada)
        assertEquals(16, tilwada.bols.size)
        assertEquals("Dha", tilwada.bols.first())

        val jhoomra = TalaLibrary.find("hindustani.jhoomra")
        assertNotNull(jhoomra)
        assertEquals(14, jhoomra.aksharas)
        assertEquals(listOf(3, 4, 3, 4), jhoomra.grouping)
        assertEquals(14, jhoomra.bols.size)
    }

    @Test
    fun `every anga produces one gesture per akshara`() {
        TalaLibrary.all.forEach { tala ->
            assertEquals(tala.aksharas, tala.gestures.size, "gestures for ${tala.name}")
            assertTrue(
                tala.gestures.count { it != AngaGesture.NONE } >= tala.angas.size,
                "gesture coverage for ${tala.name}",
            )
        }
    }

    @Test
    fun `applying a tala with playback keeps base bpm and stores kalam`() {
        val teental = TalaLibrary.find("hindustani.teental")!!
        val settings = TalaApplication.apply(
            tala = teental,
            base = MetronomeSettings(bpm = 90.0),
            nadai = Subdivision.QUARTER,
            playback = TalaPlayback(kalam = 2, kalai = 2, eduppuOffsetAksharas = 0.5),
        )
        assertEquals(90.0, settings.bpm)
        assertEquals(180.0, settings.effectiveBpm)
        assertEquals(2, settings.talaPlayback.kalai)
        assertEquals(0.5, settings.talaPlayback.eduppuOffsetAksharas)
        assertEquals(32, LayerCompiler.compilePrimary(settings).stepCount)
    }

    @Test
    fun `chapu talas are present`() {
        assertEquals(7, TalaLibrary.find("carnatic.chapu.misra")?.aksharas)
        assertEquals(5, TalaLibrary.find("carnatic.chapu.khanda")?.aksharas)
    }

    @Test
    fun `search is case insensitive and covers grouping`() {
        assertTrue(TalaLibrary.search("teental").any { it.id == "hindustani.teental" })
        assertTrue(TalaLibrary.search("CARNATIC").all { it.tradition == Tradition.CARNATIC })
        assertTrue(TalaLibrary.search("4+4+4+4").any { it.id == "hindustani.teental" })
        assertEquals(TalaLibrary.all.size, TalaLibrary.search("").size)
    }

    @Test
    fun `unknown tala ids return null`() {
        assertNull(TalaLibrary.find("nope"))
        assertNull(TalaLibrary.find(null))
    }

    @Test
    fun `applying a tala builds a matching metronome configuration`() {
        val teental: Tala = TalaLibrary.find("hindustani.teental")!!
        val settings = TalaApplication.apply(
            tala = teental,
            base = MetronomeSettings(bpm = 90.0),
            nadai = Subdivision.QUARTER,
        )
        assertEquals(16, settings.meter.beatsPerBar)
        assertEquals(listOf(4, 4, 4, 4), settings.meter.grouping)
        assertEquals(teental.beatKinds, settings.accentPattern)
        assertEquals("hindustani.teental", settings.talaId)
        assertEquals(90.0, settings.bpm)
    }
}
