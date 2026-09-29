package com.reeltracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ReelEvent::class, Session::class], version = 1, exportSchema = false)
abstract class ReelDatabase : RoomDatabase() {
    abstract fun dao(): ReelDao
}

/**
 * Process-wide wiring: one repository per process. The service runs in its own process (":bg"), so
 * the app and the service each open the same file. No multi-instance invalidation, as in Pano
 * Scrobbler (it keeps the main process's caches alive): the app reads fresh data when it opens.
 */
object ReelGraph {
    @Volatile
    private var repo: ReelRepository? = null

    fun repository(context: Context): ReelRepository =
        repo ?: synchronized(this) {
            repo ?: ReelRepository(
                Room.databaseBuilder(context.applicationContext, ReelDatabase::class.java, "reels.db")
                    .build()
                    .dao()
            ).also { repo = it }
        }
}
