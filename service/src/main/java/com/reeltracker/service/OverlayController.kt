package com.reeltracker.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The rotting-brain counter, floating over Reels while Reels is open.
 *
 * TYPE_ACCESSIBILITY_OVERLAY needs no SYSTEM_ALERT_WINDOW permission. The window is exactly the
 * size of the brain widget and FLAG_NOT_TOUCH_MODAL passes every touch outside it straight to
 * Instagram, so swiping reels is unaffected; only a touch that lands on the brain is ours. Those
 * drag it, and wherever it is dropped is remembered for next time.
 */
internal class OverlayController(
    private val service: AccessibilityService,
    private val count: Flow<Int>,
    private val scope: CoroutineScope,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val prefs = service.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var view: BrainCounterView? = null
    private var params: WindowManager.LayoutParams? = null
    private var job: Job? = null

    fun show() {
        if (view != null) return
        val v = BrainCounterView(service)
        val lp = WindowManager.LayoutParams(
            dp(BrainCounterView.W),
            dp(BrainCounterView.H),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val (sx, sy) = savedPosition()
            x = sx
            y = sy
        }
        try {
            wm.addView(v, lp)
        } catch (_: RuntimeException) {
            return // window token gone (service unbinding); nothing to show
        }
        view = v
        params = lp
        v.setOnTouchListener(DragListener(v, lp))
        job = scope.launch { count.distinctUntilChanged().collect { v.setCount(it) } }
    }

    fun hide() {
        job?.cancel()
        job = null
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        params = null
    }

    // ------------------------------------------------------------------ position

    /** Last dropped position, clamped to this screen; top-right under Reels' top bar by default. */
    private fun savedPosition(): Pair<Int, Int> {
        val (screenW, screenH) = screen()
        val w = dp(BrainCounterView.W)
        val h = dp(BrainCounterView.H)
        val x = prefs.getInt(KEY_X, screenW - w - dp(8))
        val y = prefs.getInt(KEY_Y, dp(96))
        return x.coerceIn(0, (screenW - w).coerceAtLeast(0)) to y.coerceIn(0, (screenH - h).coerceAtLeast(0))
    }

    private fun save(x: Int, y: Int) {
        prefs.edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply()
    }

    private fun screen(): Pair<Int, Int> {
        val m = service.resources.displayMetrics
        return m.widthPixels to m.heightPixels
    }

    /**
     * Drag once the finger has moved past the touch slop; a touch that never moves is a tap.
     * On release the widget eases to the nearer side edge so it never sits over the middle of a
     * reel, and that position is saved.
     */
    private inner class DragListener(
        private val v: BrainCounterView,
        private val lp: WindowManager.LayoutParams,
    ) : android.view.View.OnTouchListener {
        private val slop = ViewConfiguration.get(service).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: android.view.View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        val (screenW, screenH) = screen()
                        lp.x = (startX + dx.roundToInt()).coerceIn(0, screenW - lp.width)
                        lp.y = (startY + dy.roundToInt()).coerceIn(0, screenH - lp.height)
                        runCatching { wm.updateViewLayout(v, lp) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                    if (dragging) snapToEdge() else if (e.actionMasked == MotionEvent.ACTION_UP) v.poke()
                    dragging = false
                }
            }
            return true
        }

        private fun snapToEdge() {
            val (screenW, _) = screen()
            val margin = dp(4)
            val target = if (lp.x + lp.width / 2 < screenW / 2) margin else screenW - lp.width - margin
            val from = lp.x
            android.animation.ValueAnimator.ofInt(from, target).apply {
                duration = 220
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener {
                    lp.x = it.animatedValue as Int
                    runCatching { wm.updateViewLayout(v, lp) }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) = save(lp.x, lp.y)
                })
                start()
            }
        }
    }

    private fun dp(v: Int) = (v * service.resources.displayMetrics.density).toInt()

    private companion object {
        const val PREFS = "overlay"
        const val KEY_X = "x"
        const val KEY_Y = "y"
    }
}
