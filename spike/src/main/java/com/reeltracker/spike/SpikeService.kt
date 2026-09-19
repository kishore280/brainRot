package com.reeltracker.spike

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.Executors

/**
 * M0 spike: write every Instagram accessibility event to a JSONL file so we can answer one
 * question — does the Reels pager populate fromIndex / toIndex / itemCount?
 *
 * This breaks the production rules on purpose (tree walks, typeAllMask). It is throwaway.
 * What it must not break is the privacy rule: no text, no captions, no usernames. Strings
 * that could carry user content are logged as hash + length only.
 */
class SpikeService : AccessibilityService() {

    private val io = Executors.newSingleThreadExecutor()
    private var writer: BufferedWriter? = null
    private var lastFlush = 0L

    /** Last scroll source seen, so we snapshot the screen once each time a new scroller appears. */
    private var lastScrollSource: String? = null

    override fun onServiceConnected() {
        val dir = getExternalFilesDir(null) ?: filesDir
        val file = File(dir, "capture-${System.currentTimeMillis()}.jsonl")
        currentFile = file.absolutePath
        io.execute { writer = file.bufferedWriter() }

        emit(
            JSONObject()
                .put("type", "SPIKE_CONNECTED")
                .put("t", SystemClock.elapsedRealtime())
                .put("sdk", Build.VERSION.SDK_INT)
                .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("igVersion", instagramVersion()),
            toLogcat = true
        )
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val t = SystemClock.elapsedRealtime()
        val type = e.eventType
        val o = JSONObject()
            .put("t", t)
            .put("type", AccessibilityEvent.eventTypeToString(type))
            .put("pkg", e.packageName)
            .put("cls", e.className)
            .put("win", e.windowId)
            .put("from", e.fromIndex)
            .put("to", e.toIndex)
            .put("count", e.itemCount)
            .put("dx", e.scrollDeltaX)
            .put("dy", e.scrollDeltaY)
            .put("sx", e.scrollX)
            .put("sy", e.scrollY)
            .put("maxSy", e.maxScrollY)
            .put("cct", e.contentChangeTypes)
            .put("textItems", e.text.size)

        val src = e.source
        if (src != null) {
            o.put("src", describe(src))
            if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                // Candidate alternative to event indices: the pager's children may carry
                // CollectionItemInfo.rowIndex even when toIndex is -1.
                o.put("children", children(src, max = 6))
                o.put("parents", parents(src, depth = 6))

                val key = "${src.viewIdResourceName}|${src.className}"
                if (key != lastScrollSource) {
                    lastScrollSource = key
                    snapshot("NEW_SCROLL_SOURCE", t)
                }
            }
        }

        val loud = type == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        emit(o, toLogcat = loud)

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) snapshot("WINDOW_STATE", t)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        io.execute {
            writer?.flush()
            writer?.close()
            writer = null
        }
        io.shutdown()
        super.onDestroy()
    }

    // --- forensics -------------------------------------------------------------------------

    private fun describe(n: AccessibilityNodeInfo): JSONObject {
        val b = Rect().also(n::getBoundsInScreen)
        val o = JSONObject()
            .put("id", n.viewIdResourceName)
            .put("cls", n.className)
            .put("scrollable", n.isScrollable)
            .put("visible", n.isVisibleToUser)
            .put("childCount", n.childCount)
            .put("bounds", "${b.left},${b.top},${b.right},${b.bottom}")
        n.contentDescription?.let { o.put("descHash", it.toString().hashCode()).put("descLen", it.length) }
        n.collectionInfo?.let { o.put("collection", "${it.rowCount}x${it.columnCount}") }
        n.collectionItemInfo?.let { o.put("itemRow", it.rowIndex).put("itemCol", it.columnIndex) }
        return o
    }

    private fun children(n: AccessibilityNodeInfo, max: Int): JSONArray {
        val out = JSONArray()
        for (i in 0 until minOf(n.childCount, max)) {
            n.getChild(i)?.let { out.put(describe(it)) }
        }
        return out
    }

    private fun parents(n: AccessibilityNodeInfo, depth: Int): JSONArray {
        val out = JSONArray()
        var p = n.parent
        var d = 0
        while (p != null && d < depth) {
            out.put("${p.className}|${p.viewIdResourceName}")
            p = p.parent
            d++
        }
        return out
    }

    /** Class + view id outline of the active window. This is what ReelsContext gets built from. */
    private fun snapshot(reason: String, t: Long) {
        val root = rootInActiveWindow ?: return
        val lines = JSONArray()
        outline(root, 0, lines)
        emit(
            JSONObject().put("type", "SNAPSHOT").put("reason", reason).put("t", t).put("nodes", lines),
            toLogcat = false
        )
    }

    private fun outline(n: AccessibilityNodeInfo, depth: Int, out: JSONArray) {
        if (depth > MAX_DEPTH || out.length() >= MAX_NODES) return
        val flags = buildString {
            if (n.isScrollable) append('S')
            if (n.isVisibleToUser) append('V')
            if (n.collectionInfo != null) append('C')
        }
        out.put("$depth|${n.className}|${n.viewIdResourceName}|$flags|${n.childCount}")
        for (i in 0 until n.childCount) {
            n.getChild(i)?.let { outline(it, depth + 1, out) }
        }
    }

    // --- output ----------------------------------------------------------------------------

    private fun emit(o: JSONObject, toLogcat: Boolean) {
        val line = o.toString()
        if (toLogcat) Log.d(TAG, line)
        io.execute {
            val w = writer ?: return@execute
            w.write(line)
            w.newLine()
            val now = SystemClock.elapsedRealtime()
            if (now - lastFlush > 1_000) {
                w.flush()
                lastFlush = now
            }
        }
    }

    private fun instagramVersion(): String? = try {
        packageManager.getPackageInfo("com.instagram.android", 0).versionName
    } catch (_: Exception) {
        null
    }

    companion object {
        const val TAG = "ReelSpike"
        private const val MAX_DEPTH = 25
        private const val MAX_NODES = 400

        @Volatile
        var currentFile: String? = null
    }
}
