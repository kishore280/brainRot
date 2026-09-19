package com.reeltracker.detect

import com.reeltracker.model.UiSignal

/**
 * Flat copy of the AccessibilityEvent fields we read. The service fills it from the live event;
 * replay tests fill it from a capture. One mapping, two sources, so tests exercise the same path.
 */
data class RawEvent(
    val type: Int,
    val pkg: String?,
    val cls: String?,
    val fromIndex: Int,
    val toIndex: Int,
    val itemCount: Int,
    val scrollDeltaY: Int,
    val scrollY: Int,
    val sourceId: String?,
    val sourceClass: String?,
    val descHash: Int,
    val t: Long,
)

object SignalMapper {
    // Mirrors of android.view.accessibility.AccessibilityEvent constants.
    const val TYPE_VIEW_SELECTED = 0x4
    const val TYPE_WINDOW_STATE_CHANGED = 0x20
    const val TYPE_VIEW_SCROLLED = 0x1000

    /** True when the event needs `event.source` read to be mapped. Lets the adapter skip the IPC otherwise. */
    fun needsSource(type: Int, pkg: String?): Boolean =
        pkg == ReelsContext.PACKAGE && (type == TYPE_VIEW_SCROLLED || type == TYPE_VIEW_SELECTED)

    fun map(e: RawEvent): UiSignal? = when (e.type) {
        TYPE_WINDOW_STATE_CHANGED -> when (e.pkg) {
            null -> null
            ReelsContext.PACKAGE -> UiSignal.WindowState(e.cls, e.descHash, e.t)
            else -> UiSignal.Foreground(e.pkg, e.cls, e.t)
        }

        TYPE_VIEW_SCROLLED ->
            if (e.pkg != ReelsContext.PACKAGE) null
            else UiSignal.Scrolled(
                e.fromIndex, e.toIndex, e.itemCount, e.scrollDeltaY, e.scrollY,
                e.sourceId, e.sourceClass, e.t,
            )

        TYPE_VIEW_SELECTED ->
            if (e.pkg != ReelsContext.PACKAGE) null else UiSignal.Selected(e.sourceId, e.t)

        else -> null
    }
}
