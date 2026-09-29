package com.reeltracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reeltracker.data.ReelGraph
import com.reeltracker.model.DayStats
import com.reeltracker.service.CaptureExport
import com.reeltracker.service.Site
import com.reeltracker.service.SiteSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TodayViewModel(private val app: Application) : AndroidViewModel(app) {

    private val repo = ReelGraph.repository(app)

    val stats: StateFlow<DayStats> =
        repo.todayStats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayStats.EMPTY)

    val lastEventAt: StateFlow<Long?> =
        repo.lastEventAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    /** Asks the service, which holds the signals in its own process, to write them to Download/. */
    fun exportCapture() {
        CaptureExport.request(getApplication())
        _exportMessage.value = "Saving to Download/. A message shows when it is there."
    }

    private val siteSettings = SiteSettings.get(app)

    /** The site for "now scrolling"; null until read. */
    val site: StateFlow<Site?> = siteSettings.data.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveSite(url: String, token: String) {
        viewModelScope.launch { siteSettings.updateData { Site(url.trim(), token.trim()) } }
    }
}
