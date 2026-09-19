package com.reeltracker.data

import com.reeltracker.model.DayStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The only component that writes. Everyone else collects Flows; nobody caches a number.
 * Days are computed at read time, so midnight is not an event.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReelRepository internal constructor(private val dao: ReelDao) {

    val todayCount: Flow<Int> = today().flatMapLatest { dao.countSince(it) }

    val todayStats: Flow<DayStats> = today().flatMapLatest { start ->
        val zone = ZoneId.systemDefault()
        dao.timesSince(start).map { times ->
            DayStats.from(times) { Instant.ofEpochMilli(it).atZone(zone).hour }
        }
    }

    /** One read of today's count, for callers that cannot collect (the home-screen widget). */
    suspend fun todayCountNow(): Int = todayCount.first()

    /** Surfaces silent service death: if this is stale while you know you scrolled, detection broke. */
    val lastEventAt: Flow<Long?> = dao.lastEventAt()

    suspend fun openSession(): Long = dao.insertSession(Session(startedAt = now(), endedAt = null))

    suspend fun closeSession(id: Long) = dao.closeSession(id, now())

    suspend fun closeOpenSessions() = dao.closeOpenSessions(now())

    suspend fun record(sessionId: Long, key: Int, strategyId: String) {
        dao.insertEvent(ReelEvent(sessionId = sessionId, key = key, wallClock = now(), strategyId = strategyId))
    }

    private fun now() = System.currentTimeMillis()

    /** Emits the start of today, then again at each local midnight. */
    private fun today(): Flow<Long> = flow {
        while (true) {
            val zone = ZoneId.systemDefault()
            val date = LocalDate.now(zone)
            emit(date.atStartOfDay(zone).toInstant().toEpochMilli())
            val next = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            delay((next - System.currentTimeMillis()).coerceAtLeast(0) + 1_000)
        }
    }.distinctUntilChanged()
}
