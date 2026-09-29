package com.reeltracker.service

import android.content.Context
import com.reeltracker.model.Decision
import com.reeltracker.model.ScrollReport
import com.reeltracker.model.ScrollSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The owner's site, set on the Today screen. Reporting is off until both fields are filled. */
class SiteSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("site", Context.MODE_PRIVATE)

    var url: String
        get() = prefs.getString(URL, DEFAULT_URL)!!
        set(v) = prefs.edit().putString(URL, v.trim()).apply()

    var token: String
        get() = prefs.getString(TOKEN, "")!!
        set(v) = prefs.edit().putString(TOKEN, v.trim()).apply()

    val enabled: Boolean get() = url.startsWith("https://") && token.isNotEmpty()

    private companion object {
        const val URL = "url"
        const val TOKEN = "token"
        const val DEFAULT_URL = "https://kichoow.com/api/scroll"
    }
}

/**
 * Tells the site "now scrolling Instagram" while in Reels (on enter, then every [BEAT_MS] with the
 * running count) and "stopped" with the total on exit: POST [SiteSettings.url], Bearer token, JSON.
 * Reports go out in order through one consumer, so a "stopped" never overtakes its "scrolling".
 * A lost "scrolling" is fine (the next beat replaces it); "stopped" is retried, and if it is still
 * lost the site times the session out by itself.
 */
class SiteReporter(context: Context, private val scope: CoroutineScope) {
    private val settings = SiteSettings(context)
    private val session = ScrollSession()
    private val outbox = Channel<ScrollReport>(Channel.UNLIMITED)
    private var beat: Job? = null

    init {
        scope.launch(Dispatchers.IO) { for (r in outbox) deliver(r) }
    }

    /** Call on the main thread, in decision order. */
    fun onDecision(d: Decision) {
        val report = session.onDecision(d, System.currentTimeMillis()) ?: return
        outbox.trySend(report)
        beat?.cancel()
        beat = if (report.scrolling) scope.launch {
            while (isActive) {
                delay(BEAT_MS)
                session.current()?.let { outbox.trySend(it) }
            }
        } else null
    }

    fun close() {
        beat?.cancel()
        outbox.close()
    }

    private suspend fun deliver(r: ScrollReport) {
        if (!settings.enabled) return
        val tries = if (r.scrolling) 1 else STOP_TRIES
        for (attempt in 0 until tries) {
            if (post(r)) return
            delay(RETRY_MS shl attempt)
        }
    }

    private fun post(r: ScrollReport): Boolean = runCatching {
        val body = JSONObject()
            .put("app", "instagram")
            .put("scrolling", r.scrolling)
            .put("reels", r.reels)
            .put("started", r.started)
            .put("ended", r.ended ?: JSONObject.NULL)
            .toString()
            .toByteArray()
        val c = URL(settings.url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer ${settings.token}")
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
            c.responseCode in 200..299
        } finally {
            c.disconnect()
        }
    }.getOrDefault(false)

    private companion object {
        const val BEAT_MS = 30_000L
        const val STOP_TRIES = 4
        const val RETRY_MS = 2_000L
        const val TIMEOUT_MS = 10_000
    }
}
