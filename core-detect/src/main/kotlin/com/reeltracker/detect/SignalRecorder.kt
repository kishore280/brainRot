package com.reeltracker.detect

import com.reeltracker.model.Decision
import com.reeltracker.model.UiSignal

/**
 * Ring buffer of the last [capacity] (signal, decisions) pairs. Its export is both the diagnostic
 * when Instagram changes and a replay fixture for DetectorReplayTest.
 *
 * Contains event metadata and hashes only: never caption text, usernames or message content.
 */
class SignalRecorder(private val capacity: Int = 2000) {

    private class Entry(val signal: UiSignal, val decisions: List<Decision>)

    private val buffer = arrayOfNulls<Entry>(capacity)
    private var next = 0
    private var size = 0

    @Synchronized
    fun record(signal: UiSignal, decisions: List<Decision>) {
        buffer[next] = Entry(signal, decisions)
        next = (next + 1) % capacity
        if (size < capacity) size++
    }

    @Synchronized
    fun exportJson(meta: Map<String, String?> = emptyMap()): String = buildString {
        append("{\"format\":\"reel-capture-v1\"")
        for ((k, v) in meta) append(",\"").append(esc(k)).append("\":").append(str(v))
        append(",\"entries\":[\n")
        val start = (next - size + capacity) % capacity
        for (i in 0 until size) {
            val e = buffer[(start + i) % capacity]!!
            if (i > 0) append(",\n")
            append('{')
            signal(e.signal)
            append(",\"decisions\":[")
            e.decisions.forEachIndexed { j, d -> if (j > 0) append(','); append(str(describe(d))) }
            append("]}")
        }
        append("\n]}\n")
    }

    private fun StringBuilder.signal(s: UiSignal) {
        append("\"t\":").append(s.t)
        when (s) {
            is UiSignal.Foreground -> append(",\"sig\":\"Foreground\",\"pkg\":").append(str(s.pkg))
                .append(",\"cls\":").append(str(s.cls))

            is UiSignal.WindowState -> append(",\"sig\":\"WindowState\",\"cls\":").append(str(s.cls))
                .append(",\"rootDescHash\":").append(s.rootDescHash)

            is UiSignal.Selected -> append(",\"sig\":\"Selected\",\"sourceId\":").append(str(s.sourceId))

            is UiSignal.Scrolled -> append(",\"sig\":\"Scrolled\"")
                .append(",\"from\":").append(s.fromIndex)
                .append(",\"to\":").append(s.toIndex)
                .append(",\"count\":").append(s.itemCount)
                .append(",\"dy\":").append(s.deltaY)
                .append(",\"sy\":").append(s.scrollY)
                .append(",\"sourceId\":").append(str(s.sourceId))
                .append(",\"sourceClass\":").append(str(s.sourceClass))
        }
    }

    private fun describe(d: Decision): String = when (d) {
        is Decision.ReelConfirmed -> "ReelConfirmed:${d.key}:${d.strategyId}"
        else -> d.toString()
    }

    private fun str(v: String?): String = if (v == null) "null" else "\"${esc(v)}\""

    private fun esc(v: String): String = buildString(v.length) {
        for (c in v) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }
}
