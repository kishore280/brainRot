package com.reeltracker.model

/** What the phone tells the owner's site: "scrolling" while in Reels, then "stopped" with the total. */
data class ScrollReport(
    val scrolling: Boolean,
    val reels: Int,
    val started: Long,   // wall clock, ms
    val ended: Long?,    // wall clock, ms; set only when stopped
)

/**
 * Turns detector decisions into reports for the site. Pure: the caller passes the wall clock.
 * One Reels session is one report stream: a "scrolling" report on enter, the same with the running
 * count on each heartbeat ([current]), and one "stopped" report on exit.
 */
class ScrollSession {
    private var started: Long? = null
    private var reels = 0

    fun onDecision(d: Decision, now: Long): ScrollReport? = when (d) {
        Decision.EnterReels -> {
            started = now
            reels = 0
            current()
        }

        is Decision.ReelConfirmed -> {
            if (started != null) reels++
            null
        }

        Decision.ExitReels -> started?.let { start ->
            started = null
            ScrollReport(scrolling = false, reels = reels, started = start, ended = now)
        }

        Decision.Ignore -> null
    }

    /** The heartbeat while in Reels; null outside. */
    fun current(): ScrollReport? = started?.let { ScrollReport(scrolling = true, reels = reels, started = it, ended = null) }
}
