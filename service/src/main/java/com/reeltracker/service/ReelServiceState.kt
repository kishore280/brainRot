package com.reeltracker.service

import com.reeltracker.detect.SignalRecorder

/** Shared between the service and the UI, which run in the same process. */
object ReelServiceState {
    val recorder = SignalRecorder(capacity = 2000)

    @Volatile
    var connected: Boolean = false
        internal set
}
