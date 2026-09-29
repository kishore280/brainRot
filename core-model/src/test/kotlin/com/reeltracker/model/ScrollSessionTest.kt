package com.reeltracker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollSessionTest {

    private val s = ScrollSession()
    private fun reel(key: Int) = s.onDecision(Decision.ReelConfirmed(key, "index-v1"), 0)

    @Test
    fun `enter reports scrolling, reels add up, exit reports the total`() {
        assertEquals(ScrollReport(true, 0, 1_000, null), s.onDecision(Decision.EnterReels, 1_000))
        assertNull(reel(1))
        assertNull(reel(2))
        assertEquals(ScrollReport(true, 2, 1_000, null), s.current())
        assertEquals(ScrollReport(false, 2, 1_000, 9_000), s.onDecision(Decision.ExitReels, 9_000))
        assertNull(s.current())
    }

    @Test
    fun `a new session starts from zero`() {
        s.onDecision(Decision.EnterReels, 0); reel(1)
        s.onDecision(Decision.ExitReels, 5)
        assertEquals(ScrollReport(true, 0, 10, null), s.onDecision(Decision.EnterReels, 10))
    }

    @Test
    fun `nothing is reported outside a session`() {
        assertNull(reel(1))
        assertNull(s.onDecision(Decision.ExitReels, 1))
        assertNull(s.onDecision(Decision.Ignore, 2))
        assertNull(s.current())
    }
}
