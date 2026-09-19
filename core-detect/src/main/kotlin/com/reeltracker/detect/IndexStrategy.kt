package com.reeltracker.detect

import com.reeltracker.model.Decision
import com.reeltracker.model.UiSignal

/**
 * Tier A. The pager reports the index of the page it's on; a reel counts the first time we see
 * its index settle.
 *
 * - A swipe started and abandoned never changes the index, so it never counts.
 * - A range-reporting pager (RecyclerView / ViewPager2) reports from != to mid-swipe; we wait for
 *   from == to. Instagram's ViewPager always reports from == to, so this costs nothing today.
 * - Swiping back lands on an index already seen, so it doesn't count. Seen indices outlive Reels
 *   sessions, so a session split by e.g. opening comments can't re-count old reels.
 * - A fresh feed (pull-to-refresh, app relaunch) restarts indices from 0. We start a new epoch
 *   only when the item count shrank AND the index went backwards; either alone happens in normal
 *   use (an item removed; a swipe back) and resetting then would re-count the current reel.
 *   The epoch goes into the key so a reset mid-session can't collide with earlier keys.
 */
class IndexStrategy : DetectionStrategy {
    override val id = "index-v1"

    private val seen = HashSet<Int>()
    private var epoch = 0
    private var lastIndex = -1
    private var lastItemCount = -1

    override fun supports(probe: SignalProbe): Boolean {
        val s = probe.lastPagerScroll() ?: return false
        return s.toIndex >= 0 && s.itemCount >= 0
    }

    override fun accept(signal: UiSignal.Scrolled): Decision {
        if (signal.toIndex < 0 || signal.itemCount < 0) return Decision.ExitReels
        if (signal.fromIndex >= 0 && signal.fromIndex != signal.toIndex) return Decision.Ignore

        if (signal.itemCount < lastItemCount && signal.toIndex < lastIndex) {
            epoch++
            seen.clear()
        }
        lastItemCount = signal.itemCount
        lastIndex = signal.toIndex

        if (!seen.add(signal.toIndex)) return Decision.Ignore
        return Decision.ReelConfirmed(key = epoch * EPOCH_STRIDE + signal.toIndex, strategyId = id)
    }

    override fun reset() {
        seen.clear()
        epoch = 0
        lastIndex = -1
        lastItemCount = -1
    }

    private companion object {
        const val EPOCH_STRIDE = 100_000
    }
}
