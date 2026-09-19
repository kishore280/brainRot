package com.reeltracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Store events, derive counters, never store a counter. */
@Entity(
    tableName = "reel_events",
    indices = [Index(value = ["sessionId", "key"], unique = true), Index("wallClock")],
)
data class ReelEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val key: Int,
    val wallClock: Long,        // System.currentTimeMillis()
    val strategyId: String,
)

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long?,         // null == open
)
