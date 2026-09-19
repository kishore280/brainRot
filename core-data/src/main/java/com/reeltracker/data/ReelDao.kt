package com.reeltracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelDao {

    /** Duplicate (sessionId, key) is silently dropped: dedup is a storage constraint, not logic. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: ReelEvent): Long

    @Insert
    suspend fun insertSession(session: Session): Long

    @Query("UPDATE sessions SET endedAt = :now WHERE id = :id AND endedAt IS NULL")
    suspend fun closeSession(id: Long, now: Long)

    @Query("UPDATE sessions SET endedAt = :now WHERE endedAt IS NULL")
    suspend fun closeOpenSessions(now: Long)

    @Query("SELECT COUNT(*) FROM reel_events WHERE wallClock >= :since")
    fun countSince(since: Long): Flow<Int>

    @Query("SELECT wallClock FROM reel_events WHERE wallClock >= :since ORDER BY wallClock")
    fun timesSince(since: Long): Flow<List<Long>>

    @Query("SELECT MAX(wallClock) FROM reel_events")
    fun lastEventAt(): Flow<Long?>
}
