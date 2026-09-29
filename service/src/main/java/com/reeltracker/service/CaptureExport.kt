package com.reeltracker.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

/**
 * "Export debug capture": the signals live in the service's process (":bg"), so the app asks the
 * running service with an in-app broadcast, and the service writes the file and says where.
 */
object CaptureExport {
    internal const val ACTION = "com.reeltracker.EXPORT_CAPTURE"

    /** From the app. The service answers with a toast. */
    fun request(context: Context) = context.sendBroadcast(Intent(ACTION).setPackage(context.packageName))

    /** In the service: writes the SignalRecorder ring buffer to Download/. Doubles as a replay-test fixture. */
    internal fun write(context: Context): String {
        val ig = try {
            context.packageManager.getPackageInfo("com.instagram.android", 0).versionName
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
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("could not create file")
        resolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) }
        return "Download/$name"
    }
}
