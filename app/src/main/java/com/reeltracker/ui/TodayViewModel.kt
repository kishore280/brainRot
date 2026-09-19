package com.reeltracker.ui

import android.app.Application
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reeltracker.data.ReelGraph
import com.reeltracker.model.DayStats
import com.reeltracker.service.ReelServiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TodayViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ReelGraph.repository(app)

    val stats: StateFlow<DayStats> =
        repo.todayStats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayStats.EMPTY)

    val lastEventAt: StateFlow<Long?> =
        repo.lastEventAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    /** Writes the SignalRecorder ring buffer to Download/. Doubles as a replay-test fixture. */
    fun exportCapture() {
        viewModelScope.launch {
            _exportMessage.value = try {
                "Saved to ${withContext(Dispatchers.IO) { writeCapture() }}"
            } catch (e: Exception) {
                "Export failed: ${e.message}"
            }
        }
    }

    private fun writeCapture(): String {
        val app = getApplication<Application>()
        val ig = try {
            app.packageManager.getPackageInfo("com.instagram.android", 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val json = ReelServiceState.recorder.exportJson(
            mapOf(
                "igVersion" to ig,
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "sdk" to Build.VERSION.SDK_INT.toString(),
                "exportedAt" to System.currentTimeMillis().toString(),
            )
        )
        val name = "reel-capture-${System.currentTimeMillis()}.json"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = app.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("could not create file")
        resolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) }
        return "Download/$name"
    }
}
