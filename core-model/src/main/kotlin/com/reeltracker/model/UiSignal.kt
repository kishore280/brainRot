package com.reeltracker.model

/**
 * Everything the detector is allowed to know about the outside world.
 * `t` is SystemClock.elapsedRealtime() at the time of the event.
 */
sealed interface UiSignal {
    val t: Long

    data class Foreground(
        val pkg: String,
        val cls: String?,
        override val t: Long,
    ) : UiSignal

    data class Scrolled(
        val fromIndex: Int,      // -1 when the event doesn't expose it
        val toIndex: Int,        // -1 when the event doesn't expose it
        val itemCount: Int,      // -1 when the event doesn't expose it
        val deltaY: Int,
        val scrollY: Int,
        val sourceId: String?,   // viewIdResourceName
        val sourceClass: String?,
        override val t: Long,
    ) : UiSignal

    data class WindowState(
        val cls: String?,
        val rootDescHash: Int,   // hash only — never the text itself
        override val t: Long,
    ) : UiSignal

    /**
     * TYPE_VIEW_SELECTED. Not in the original design: M0 showed that Instagram's tab switches
     * fire no window-state change, only a selection on the tab view (`clips_tab`, `feed_tab`, ...).
     */
    data class Selected(
        val sourceId: String?,
        override val t: Long,
    ) : UiSignal
}
