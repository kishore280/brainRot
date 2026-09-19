package com.reeltracker.detect

import com.reeltracker.model.Decision
import com.reeltracker.model.UiSignal

/** Read-only view of the most recent signals, used once per Reels entry to pick a strategy. */
class SignalProbe(val recent: List<UiSignal>) {
    fun lastPagerScroll(): UiSignal.Scrolled? =
        recent.lastOrNull { it is UiSignal.Scrolled && ReelsContext.isPager(it.sourceId) } as UiSignal.Scrolled?
}

interface DetectionStrategy {
    /** Persisted with every event so historical data stays interpretable after strategies change. */
    val id: String

    fun supports(probe: SignalProbe): Boolean

    /** Only called with pager scrolls. Returning [Decision.ExitReels] means "I can no longer count". */
    fun accept(signal: UiSignal.Scrolled): Decision

    /** Forget everything. Called when the service (re)connects. */
    fun reset()
}
