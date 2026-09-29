package com.reeltracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.reeltracker.model.ScrollReport

/**
 * The silent "brain rotting" notification, built the way Pano Scrobbler builds its now-playing one
 * (github.com/kawaiiDango/pano-scrobbler, PanoNotifications.android.kt):
 * - an IMPORTANCE_LOW channel: no sound, no vibration, no pop-up, still in the shade;
 * - setShowWhen(false), setColor, a white-silhouette small icon, and one id updated in place;
 * - a "✓" line once it is over, as Pano shows "✓ track" after a scrobble.
 * Ongoing while in Reels; after, a summary you can swipe away, gone by itself after an hour.
 */
internal class BrainNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    /** The last stage's picture: decoded again only when the stage changes, not on every reel. */
    private var lastPicture: Pair<Int, Bitmap>? = null
    private val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
        PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
    }

    init {
        // Creating an existing channel again changes nothing, so this is safe on every start.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.noti_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.noti_channel_description)
                setShowBadge(false)
            }
        )
    }

    /** [today] is today's count, for the brain's stage. */
    fun show(r: ScrollReport, today: Int) {
        if (!r.scrolling && r.reels == 0) return manager.cancel(ID)
        val stage = BrainRot.stage(today)
        val title = if (r.scrolling) {
            context.resources.getQuantityString(R.plurals.noti_scrolling, r.reels, r.reels)
        } else {
            context.resources.getQuantityString(R.plurals.noti_stopped, r.reels, r.reels, minutes(r))
        }
        val text = context.getString(R.string.noti_today, BrainRot.label(stage), today)
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_noti_brain)
            .setLargeIcon(picture(stage))
            .setColor(COLOR)
            .setShowWhen(false)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOngoing(r.scrolling)
            .setOnlyAlertOnce(true)
            .apply { if (!r.scrolling) setAutoCancel(true).setTimeoutAfter(SUMMARY_MS) }
            .build()
        manager.notify(ID, n)
    }

    fun cancel() = manager.cancel(ID)

    private fun picture(stage: Int): Bitmap =
        lastPicture?.takeIf { it.first == stage }?.second
            ?: BitmapFactory.decodeResource(context.resources, BrainRot.sprite(stage)).also { lastPicture = stage to it }

    private fun minutes(r: ScrollReport): Int = (((r.ended ?: r.started) - r.started) / 60_000).toInt().coerceAtLeast(1)

    private companion object {
        const val CHANNEL = "brain_rotting"
        const val ID = 1
        const val COLOR = 0xFFE5483C.toInt() // the app's accent
        const val SUMMARY_MS = 60 * 60_000L
    }
}
