package com.reeltracker.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.reeltracker.data.ReelGraph
import com.reeltracker.data.ReelRepository
import com.reeltracker.detect.Detector
import com.reeltracker.detect.RawEvent
import com.reeltracker.detect.SignalMapper
import com.reeltracker.model.Decision
import com.reeltracker.model.UiSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Adapter. Android types live here and nowhere else: AccessibilityEvent -> UiSignal -> Detector,
 * then Decisions are acted on. No tree traversal, no text reads.
 */
class ReelAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val detector = Detector()
    private lateinit var repo: ReelRepository
    private lateinit var overlay: OverlayController
    private lateinit var site: SiteReporter

    /** The lock screen is system UI, which counts as an overlay; screen off is what ends the session. */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) =
            handle(UiSignal.ScreenOff(SystemClock.elapsedRealtime()))
    }

    /**
     * Decisions are applied strictly in order by one consumer. A Room call suspends, so launching
     * one coroutine per decision would let a record() run before the openSession() it depends on.
     */
    private val writes = Channel<Decision>(Channel.UNLIMITED)

    override fun onServiceConnected() {
        repo = ReelGraph.repository(this)
        overlay = OverlayController(this, repo.todayCount, scope)
        site = SiteReporter(this, scope)
        detector.reset()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenOff, filter)
        scope.launch(Dispatchers.IO) { applyWrites() }
        // Home-screen widget follows the count live, including the reset at midnight.
        scope.launch(Dispatchers.IO) {
            repo.todayCount.distinctUntilChanged().collect { ReelWidgetProvider.update(this@ReelAccessibilityService, it) }
        }
        ReelServiceState.connected = true
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val type = e.eventType
        val pkg = e.packageName?.toString()

        var sourceId: String? = null
        var sourceClass: String? = null
        if (SignalMapper.needsSource(type, pkg)) {
            val src = e.source ?: return // §9: no source node, no guess
            sourceId = src.viewIdResourceName
            sourceClass = src.className?.toString()
        }

        val signal = SignalMapper.map(
            RawEvent(
                type = type,
                pkg = pkg,
                cls = e.className?.toString(),
                fromIndex = e.fromIndex,
                toIndex = e.toIndex,
                itemCount = e.itemCount,
                scrollDeltaY = e.scrollDeltaY,
                scrollY = e.scrollY,
                sourceId = sourceId,
                sourceClass = sourceClass,
                descHash = e.contentDescription?.hashCode() ?: 0,
                t = SystemClock.elapsedRealtime(),
            )
        ) ?: return
        handle(signal)
    }

    private fun handle(signal: UiSignal) {
        val decisions = detector.accept(signal)
        ReelServiceState.recorder.record(signal, decisions)
        for (d in decisions) {
            when (d) {
                Decision.EnterReels -> overlay.show()
                Decision.ExitReels -> overlay.hide()
                else -> Unit
            }
            writes.trySend(d)
            site.onDecision(d)
        }
    }

    private suspend fun applyWrites() {
        repo.closeOpenSessions() // anything left open by process death
        var session: Long? = null
        for (d in writes) when (d) {
            Decision.EnterReels -> session = repo.openSession()
            Decision.ExitReels -> session = session?.let { repo.closeSession(it); null }
            is Decision.ReelConfirmed -> session?.let { repo.record(it, d.key, d.strategyId) }
            Decision.Ignore -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        ReelServiceState.connected = false
        if (::overlay.isInitialized) overlay.hide()
        if (::site.isInitialized) {
            unregisterReceiver(screenOff)
            site.close()
        }
        writes.close()
        scope.cancel()
        super.onDestroy()
    }
}
