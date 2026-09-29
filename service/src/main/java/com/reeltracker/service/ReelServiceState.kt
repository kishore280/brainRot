package com.reeltracker.service

import com.reeltracker.detect.SignalRecorder

/** The service's recent signals, in its own process (":bg"); the app asks for them with [CaptureExport]. */
internal object ReelServiceState {
    val recorder = SignalRecorder(capacity = 2000)
}
