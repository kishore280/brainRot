package com.reeltracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ReelEvent::class, Session::class], version = 1, exportSchema = false)
abstract class ReelDatabase : RoomDatabase() {
    abstract fun dao(): ReelDao
}

/** Process-wide wiring. The service and the UI run in one process and share one repository. */
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
