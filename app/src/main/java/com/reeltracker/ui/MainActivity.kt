package com.reeltracker.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reeltracker.service.ReelAccessibilityService
import com.reeltracker.service.ReelWidgetProvider
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: TodayViewModel by viewModels()

    // The "brain rotting" notification needs this on Android 13+. If refused, counting still works.
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            ReelTheme {
                val context = LocalContext.current
                val stats by vm.stats.collectAsStateWithLifecycle()
                val last by vm.lastEventAt.collectAsStateWithLifecycle()
                val exportMessage by vm.exportMessage.collectAsStateWithLifecycle()
                val site by vm.site.collectAsStateWithLifecycle()
                var tracking by remember { mutableStateOf(trackingState(context)) }
                var is24h by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }
                LifecycleResumeEffect(Unit) {
                    tracking = trackingState(context)
                    is24h = DateFormat.is24HourFormat(context)
                    // Catches the widget up if the service was stopped while counts changed.
                    ReelWidgetProvider.refresh(context)
                    onPauseOrDispose { }
                }

                TodayScreen(
                    now = rememberNow(),
                    stats = stats,
                    lastEventAt = last,
                    tracking = tracking,
                    is24h = is24h,
                    exportMessage = exportMessage,
                    onOpenAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onExport = {
                        if (tracking == Tracking.On) vm.exportCapture()
                        else Toast.makeText(context, "Tracking is off, so there is nothing to export.", Toast.LENGTH_SHORT).show()
                    },
                    onAddWidget = { requestWidget(context) },
                    site = site,
                    onSaveSite = vm::saveSite,
                )
            }
        }
    }
}

/** Wall clock, ticking on each second boundary. */
@Composable
private fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000 - value % 1_000)
        }
    }
    return now
}

/**
 * Asks the launcher to place the widget; it shows its own "Add to home screen?" prompt.
 * Launchers that cannot pin (some older or third-party ones) get the manual steps instead.
 */
private fun requestWidget(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val provider = ComponentName(context, ReelWidgetProvider::class.java)
    val pinned = manager.isRequestPinAppWidgetSupported &&
        manager.requestPinAppWidget(provider, null, null)
    if (!pinned) {
        Toast.makeText(
            context,
            "Long-press your home screen, tap Widgets, and add Brainrot.",
            Toast.LENGTH_LONG,
        ).show()
    }
}

private fun trackingState(context: Context): Tracking {
    val me = ComponentName(context, ReelAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?.split(':')
        ?.any { ComponentName.unflattenFromString(it) == me } == true
    return when {
        !enabled -> Tracking.Off
        running(context, me) -> Tracking.On
        else -> Tracking.Stalled
    }
}

/**
 * Whether the system has the service bound and running. The service lives in its own process
 * (":bg"), so the app cannot read a flag from it; AccessibilityManager lists the running ones.
 */
private fun running(context: Context, me: ComponentName): Boolean =
    context.getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.let { s -> ComponentName(s.packageName, s.name) == me } }
