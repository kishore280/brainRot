package com.reeltracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayStatsTest {

    private val min = 60_000L
    private val hourOf = { t: Long -> ((t / (60 * min)) % 24).toInt() }

    @Test
    fun `empty day`() {
        val s = DayStats.from(emptyList(), hourOf)
        assertEquals(0, s.count)
        assertEquals(0L, s.scrollingMs)
        assertNull(s.secondsPerReel)
    }

    @Test
    fun `gap longer than threshold splits sittings`() {
        val times = listOf(0L, 10_000, 20_000, 20_000 + 4 * min, 20_000 + 4 * min + 30_000)
        val s = DayStats.from(times, hourOf)
        assertEquals(2, s.sittings.size)
        assertEquals(Sitting(0, 20_000, 3), s.sittings[0])
        assertEquals(2, s.sittings[1].reels)
        assertEquals(50_000L, s.scrollingMs)
        assertEquals(50L / 3, s.secondsPerReel) // 50 s over 3 gaps
    }

    @Test
    fun `hourly buckets`() {
        val times = listOf(0L, 1, 61 * min, 23 * 60 * min)
        val s = DayStats.from(times, hourOf)
        assertEquals(2, s.hourly[0])
        assertEquals(1, s.hourly[1])
        assertEquals(1, s.hourly[23])
        assertEquals(23 * 60 * min, s.lastAt)
    }
}
