package com.reeltracker.model

/** A stretch of continuous Reels scrolling, derived from event timestamps at read time. */
data class Sitting(
    val start: Long,
    val end: Long,
    val reels: Int,
) {
    val durationMs: Long get() = end - start
}

data class DayStats(
    val count: Int,
    val hourly: List<Int>,        // 24 buckets, local time
    val sittings: List<Sitting>,  // chronological
    val lastAt: Long?,
) {
    val scrollingMs: Long get() = sittings.sumOf { it.durationMs }

    /** Mean seconds spent per reel, or null when there's nothing to average. */
    val secondsPerReel: Long?
        get() {
            // Each sitting's span covers (reels - 1) gaps; the first reel of a sitting has no known start.
            val gaps = sittings.sumOf { it.reels - 1 }
            return if (gaps <= 0) null else scrollingMs / gaps / 1000
        }

    companion object {
        /** Gap after which a new sitting starts. Reels are rarely watched for longer than this. */
        const val SITTING_GAP_MS = 3 * 60_000L

        val EMPTY = DayStats(0, List(24) { 0 }, emptyList(), null)

        /**
         * @param times event wall-clock times, ascending
         * @param hourOf maps a wall-clock time to its local hour (0..23); injected to keep this pure
         */
        fun from(times: List<Long>, hourOf: (Long) -> Int): DayStats {
            if (times.isEmpty()) return EMPTY
            val hourly = IntArray(24)
            val sittings = ArrayList<Sitting>()
            var start = times[0]
            var prev = times[0]
            var n = 0
            for (t in times) {
                hourly[hourOf(t)]++
                if (t - prev > SITTING_GAP_MS) {
                    sittings += Sitting(start, prev, n)
                    start = t
                    n = 0
                }
                n++
                prev = t
            }
            sittings += Sitting(start, prev, n)
            return DayStats(times.size, hourly.toList(), sittings, times.last())
        }
    }
}
