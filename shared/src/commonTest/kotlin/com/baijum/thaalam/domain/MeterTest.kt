package com.baijum.thaalam.domain

import com.baijum.thaalam.data.Meter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeterTest {

    @Test
    fun `compound eighths group in threes`() {
        assertEquals(listOf(3, 3), Meter.defaultGrouping(6, 8))
        assertEquals(listOf(3, 3, 3), Meter.defaultGrouping(9, 8))
        assertEquals(listOf(3, 3, 3, 3), Meter.defaultGrouping(12, 8))
    }

    @Test
    fun `additive odd meters use idiomatic grouping`() {
        assertEquals(listOf(2, 3), Meter.defaultGrouping(5, 8))
        assertEquals(listOf(2, 2, 3), Meter.defaultGrouping(7, 8))
        assertEquals(listOf(3, 3, 2), Meter.defaultGrouping(8, 8))
        assertEquals(listOf(3, 3, 3, 2), Meter.defaultGrouping(11, 8))
    }

    @Test
    fun `simple meters stay as one group`() {
        assertEquals(listOf(4), Meter.defaultGrouping(4, 4))
        assertEquals(listOf(3), Meter.defaultGrouping(3, 4))
        assertEquals(listOf(2), Meter.defaultGrouping(2, 4))
    }

    @Test
    fun `invalid grouping is repaired to match the bar length`() {
        val meter = Meter(4, 4, listOf(3, 3))
        assertEquals(listOf(4), meter.normalized().grouping)
    }

    @Test
    fun `grouping with zero entries is repaired`() {
        val meter = Meter(7, 8, listOf(2, 0, 2, 3))
        assertEquals(listOf(2, 2, 3), meter.normalized().grouping)
    }

    @Test
    fun `parse round trips common labels`() {
        val meter = Meter.parse("7/8")
        assertEquals(7, meter?.beatsPerBar)
        assertEquals(8, meter?.beatUnit)
        assertEquals(listOf(2, 2, 3), meter?.grouping)
        assertEquals("7/8", meter?.label)
    }

    @Test
    fun `parse rejects nonsense`() {
        assertNull(Meter.parse(""))
        assertNull(Meter.parse("4"))
        assertNull(Meter.parse("0/4"))
        assertNull(Meter.parse("4/5"))
    }

    @Test
    fun `compound detection follows the written meter`() {
        assertTrue(Meter(6, 8).isCompound)
        assertTrue(Meter(12, 8).isCompound)
        assertTrue(!Meter(4, 4).isCompound)
        assertTrue(!Meter(5, 8).isCompound)
    }
}
