package com.reeltracker.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun hourLabel(h: Int, is24h: Boolean): String =
    if (is24h) "%02d:00".format(h) else "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}"

internal fun duration(ms: Long): String {
    val m = ms / 60_000
    return when {
        m < 1 -> "<1 min"
        m < 60 -> "$m min"
        else -> "${m / 60}h ${m % 60}m"
    }
}

internal class Clock(is24h: Boolean) {
    private val zone = ZoneId.systemDefault()
    private val time = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm")
    private val timeFull = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a")
    private val seconds = DateTimeFormatter.ofPattern(if (is24h) "HH:mm:ss" else "h:mm:ss")
    private val amPm = DateTimeFormatter.ofPattern("a")
    private val date = DateTimeFormatter.ofPattern("EEEE, d MMMM")
    private val shortDate = DateTimeFormatter.ofPattern("d MMM")
    private val twelve = !is24h

    private fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone)

    fun date(ms: Long): String = date.format(at(ms))
    fun clock(ms: Long): String = seconds.format(at(ms))
    fun amPm(ms: Long): String? = if (twelve) amPm.format(at(ms)) else null
    fun time(ms: Long): String = timeFull.format(at(ms))

    /** "7:42 – 7:58 PM", repeating the marker only when the range crosses noon. */
    fun range(start: Long, end: Long): String {
        val a = at(start)
        val b = at(end)
        if (!twelve) return "${time.format(a)} – ${time.format(b)}"
        return if (amPm.format(a) == amPm.format(b)) "${time.format(a)} – ${timeFull.format(b)}"
        else "${timeFull.format(a)} – ${timeFull.format(b)}"
    }

    fun ago(then: Long, now: Long): String {
        val m = (now - then) / 60_000
        return when {
            m < 1 -> "just now"
            m < 60 -> "$m min ago"
            at(then).toLocalDate() == LocalDate.now(zone) -> "${m / 60} h ago"
            else -> "${shortDate.format(at(then))}, ${timeFull.format(at(then))}"
        }
    }
}
