package com.reeltracker.service

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** The owner's site, set on the Today screen. Reporting is off until both fields are filled. */
data class Site(val url: String = DEFAULT_URL, val token: String = "") {
    val enabled: Boolean get() = url.startsWith("https://") && token.isNotEmpty()

    private companion object {
        const val DEFAULT_URL = "https://kichoow.com/api/scroll"
    }
}

/**
 * [Site], shared by the app (which edits it) and the service (which runs in its own process, ":bg").
 * SharedPreferences would give the service a stale copy, so this is a multi-process DataStore,
 * as Pano Scrobbler keeps its settings (PlatformStuff.android.kt, MultiProcessDataStoreFactory).
 * One instance per process, as DataStore requires.
 */
class SiteSettings private constructor(store: DataStore<Site>) : DataStore<Site> by store {
    companion object {
        @Volatile
        private var instance: SiteSettings? = null

        fun get(context: Context): SiteSettings = instance ?: synchronized(this) {
            instance ?: SiteSettings(
                MultiProcessDataStoreFactory.create(
                    serializer = SiteSerializer,
                    corruptionHandler = ReplaceFileCorruptionHandler { Site() },
                    produceFile = { File(context.applicationContext.filesDir, "site.json") },
                )
            ).also { instance = it }
        }
    }
}

private object SiteSerializer : Serializer<Site> {
    override val defaultValue = Site()

    override suspend fun readFrom(input: InputStream): Site = try {
        val o = JSONObject(input.readBytes().decodeToString())
        Site(url = o.optString("url", defaultValue.url), token = o.optString("token"))
    } catch (e: JSONException) {
        throw CorruptionException("site.json is not JSON", e)
    }

    override suspend fun writeTo(t: Site, output: OutputStream) =
        output.write(JSONObject().put("url", t.url.trim()).put("token", t.token.trim()).toString().toByteArray())
}
