package com.reeltracker.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.reeltracker.data.ReelGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home-screen widget: today's reel count and the brain at its current rot stage.
 *
 * Three things redraw it: Android's periodic update (the midnight rollover when nothing is
 * watched), the accessibility service on every counted reel, and the app when it opens.
 */
class ReelWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                val count = ReelGraph.repository(context).todayCountNow()
                render(context, manager, ids, count)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Pushes [count] to every placed widget. Cheap when none are placed. */
        fun update(context: Context, count: Int) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ReelWidgetProvider::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids, count)
        }

        /** Reads today's count and redraws, for callers that do not have it to hand. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            scope.launch { update(app, ReelGraph.repository(app).todayCountNow()) }
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray, count: Int) {
            val stage = BrainRot.stage(count)
            val views = RemoteViews(context.packageName, R.layout.widget_reels).apply {
                setImageViewResource(R.id.widget_brain, BrainRot.sprite(stage))
                setTextViewText(R.id.widget_count, count.toString())
                setTextViewText(
                    R.id.widget_caption,
                    "${context.getString(R.string.widget_reels_today)} · ${BrainRot.label(stage)}",
                )
                setContentDescription(R.id.widget_root, "$count reels today, ${BrainRot.label(stage)}")
                openAppOnTap(context)?.let { setOnClickPendingIntent(R.id.widget_root, it) }
            }
            manager.updateAppWidget(ids, views)
        }

        private fun openAppOnTap(context: Context): PendingIntent? {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
            launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            return PendingIntent.getActivity(
                context, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
