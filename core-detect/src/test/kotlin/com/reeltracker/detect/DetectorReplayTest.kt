package com.reeltracker.detect

import com.reeltracker.model.Decision
import com.reeltracker.model.UiSignal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Replays every capture in src/test/resources/corpus and asserts the count in its filename
 * (`...__expectN.json[l]`). Every Instagram breakage should add a capture here, so a fix for a
 * new build can't silently regress an old one.
 *
 * Understands both the M0 spike format (JSONL of raw events) and SignalRecorder exports.
 */
class DetectorReplayTest {

    @Test
    fun `every capture in the corpus replays to its expected count`() {
        val dir = File(javaClass.classLoader!!.getResource("corpus")!!.toURI())
        val files = dir.listFiles()!!.filter { it.name.contains("__expect") }.sortedBy { it.name }
        assertTrue("corpus is empty", files.isNotEmpty())

        val failures = files.mapNotNull { f ->
            val expected = Regex("""__expect(\d+)""").find(f.name)!!.groupValues[1].toInt()
            val actual = replay(load(f))
            if (actual == expected) null else "${f.name}: expected $expected, got $actual"
        }
        assertEquals(emptyList<String>(), failures)
    }

    /** Counts what the database would keep: distinct (session, key). */
    private fun replay(signals: List<UiSignal>): Int {
        val detector = Detector()
        var session = 0
        val stored = HashSet<Pair<Int, Int>>()
        for (s in signals) for (d in detector.accept(s)) when (d) {
            Decision.EnterReels -> session++
            is Decision.ReelConfirmed -> {
                assertTrue("confirmed outside Reels", detector.inReels)
                stored += session to d.key
            }
            else -> Unit
        }
        return stored.size
    }

    private fun load(f: File): List<UiSignal> =
        if (f.name.endsWith(".jsonl")) loadSpike(f) else loadRecorder(f)

    private fun loadSpike(f: File): List<UiSignal> = f.readLines().filter { it.isNotBlank() }.mapNotNull { line ->
        val o = json.parseToJsonElement(line).jsonObject
        val type = SPIKE_TYPES[o.str("type")] ?: return@mapNotNull null
        val src = o["src"] as? JsonObject
        SignalMapper.map(
            RawEvent(
                type = type,
                pkg = o.str("pkg"),
                cls = o.str("cls"),
                fromIndex = o.int("from"),
                toIndex = o.int("to"),
                itemCount = o.int("count"),
                scrollDeltaY = o.int("dy"),
                scrollY = o.int("sy"),
                sourceId = src?.str("id"),
                sourceClass = src?.str("cls"),
                descHash = src?.get("descHash")?.jsonPrimitive?.intOrNull ?: 0,
                t = o["t"]!!.jsonPrimitive.long,
            )
        )
    }

    private fun loadRecorder(f: File): List<UiSignal> =
        (json.parseToJsonElement(f.readText()).jsonObject["entries"] as JsonArray).map { e ->
            val o = e.jsonObject
            val t = o["t"]!!.jsonPrimitive.long
            when (o.str("sig")) {
                "Foreground" -> UiSignal.Foreground(o.str("pkg")!!, o.str("cls"), t)
                "WindowState" -> UiSignal.WindowState(o.str("cls"), o.int("rootDescHash"), t)
                "Selected" -> UiSignal.Selected(o.str("sourceId"), t)
                "Scrolled" -> UiSignal.Scrolled(
                    o.int("from"), o.int("to"), o.int("count"), o.int("dy"), o.int("sy"),
                    o.str("sourceId"), o.str("sourceClass"), t,
                )
                else -> error("unknown signal in ${f.name}: $o")
            }
        }

    private fun JsonObject.str(k: String): String? = this[k]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(k: String): Int = this[k]?.jsonPrimitive?.int ?: -1

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val SPIKE_TYPES = mapOf(
            "TYPE_VIEW_SELECTED" to SignalMapper.TYPE_VIEW_SELECTED,
            "TYPE_WINDOW_STATE_CHANGED" to SignalMapper.TYPE_WINDOW_STATE_CHANGED,
            "TYPE_VIEW_SCROLLED" to SignalMapper.TYPE_VIEW_SCROLLED,
        )
    }
}
