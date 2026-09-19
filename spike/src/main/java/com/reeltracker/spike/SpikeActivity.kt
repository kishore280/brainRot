package com.reeltracker.spike

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class SpikeActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { textSize = 16f }
        val open = Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad)
            addView(status)
            addView(open)
        })
    }

    override fun onResume() {
        super.onResume()
        val me = ComponentName(this, SpikeService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')
            ?.any { it.equals(me, ignoreCase = true) } == true
        status.text = buildString {
            appendLine(if (enabled) "Service: ENABLED" else "Service: disabled")
            appendLine()
            appendLine("Capture file:")
            appendLine(SpikeService.currentFile ?: "(none yet — enable the service)")
            appendLine()
            appendLine("Procedure: open Instagram → Reels. Swipe forward exactly 10 times, pausing ~2 s on each. Then swipe back 2. Then one fast flick. Then leave Instagram.")
        }
    }
}
