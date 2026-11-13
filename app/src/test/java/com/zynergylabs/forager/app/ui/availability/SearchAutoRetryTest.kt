package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SearchAutoRetry], headless (dispatch 2026-11-11-01): C4 "At once (Recommended)", C7 "Notice stays, no auto retry
 * (Recommended)", E2 "Only with Forager on screen (Recommended)", E3 "3 tries, then wait for me (Recommended)", and F2 (a return
 * during a running fetch). Each call is what [RetryFailedSearchOnSignalReturn] makes; the Boolean is "run the search again now".
 */
class SearchAutoRetryTest {

    private val gate = SearchAutoRetry()

    /** A failed search showing, Forager on screen, the signal there: the state after a failure. */
    private fun failedOnScreen() = assertFalse(gate.onStateChanged(failed = true, running = false, onScreen = true, validated = true))

    @Test
    fun `a return while the search has failed runs it at once, three times, then waits`() {
        failedOnScreen()
        repeat(3) { assertTrue("return ${it + 1} did not run the search", gate.onSignalReturn(failed = true, running = false, onScreen = true)) }
        assertFalse("a fourth return ran the search", gate.onSignalReturn(failed = true, running = false, onScreen = true))
        assertEquals(3, gate.tries)
    }

    @Test
    fun `Try again starts the count again`() {
        failedOnScreen()
        repeat(3) { gate.onSignalReturn(failed = true, running = false, onScreen = true) }
        gate.onUserRetry()
        assertTrue("after Try again a return did not run the search", gate.onSignalReturn(failed = true, running = false, onScreen = true))
    }

    @Test
    fun `a success or a new search starts the count again`() {
        failedOnScreen()
        repeat(3) { gate.onSignalReturn(failed = true, running = false, onScreen = true) }
        assertFalse(gate.onStateChanged(failed = false, running = false, onScreen = true, validated = true))
        assertEquals("the count after a success", 0, gate.tries)
        failedOnScreen()
        assertTrue("after a success a return did not run the next failed search", gate.onSignalReturn(failed = true, running = false, onScreen = true))
    }

    @Test
    fun `a return with nothing failed and nothing running does nothing`() {
        assertFalse(gate.onSignalReturn(failed = false, running = false, onScreen = true))
        assertFalse("a later failure ran the search with no return after it", gate.onStateChanged(failed = true, running = false, onScreen = true, validated = true))
    }

    @Test
    fun `a return while the fetch runs is held and runs the search once if the fetch fails`() {
        assertFalse(gate.onSignalReturn(failed = false, running = true, onScreen = true))
        assertTrue("the fetch failed after a return during it, and the search did not run again", gate.onStateChanged(failed = true, running = false, onScreen = true, validated = true))
        assertFalse("the held return ran the search twice", gate.onStateChanged(failed = true, running = false, onScreen = true, validated = true))
    }

    @Test
    fun `a return while the fetch runs is dropped if the fetch succeeds`() {
        gate.onSignalReturn(failed = false, running = true, onScreen = true)
        gate.onStateChanged(failed = false, running = false, onScreen = true, validated = true)
        assertFalse("a return held over a success ran a later failure", gate.onStateChanged(failed = true, running = false, onScreen = true, validated = true))
    }

    @Test
    fun `a return while Forager is not on screen runs nothing until it comes back, then once`() {
        failedOnScreen()
        assertFalse(gate.onStateChanged(failed = true, running = false, onScreen = false, validated = false))
        assertFalse("a return in the background ran the search", gate.onSignalReturn(failed = true, running = false, onScreen = false))
        assertTrue("coming back after a return did not run the search", gate.onStateChanged(failed = true, running = false, onScreen = true, validated = true))
        assertFalse(gate.onStateChanged(failed = true, running = false, onScreen = false, validated = true))
        assertFalse("coming back a second time ran it again", gate.onStateChanged(failed = true, running = false, onScreen = true, validated = true))
    }

    @Test
    fun `coming back with no return while away runs nothing`() {
        failedOnScreen()
        gate.onStateChanged(failed = true, running = false, onScreen = false, validated = true)
        assertFalse("coming back with no return ran a failure with good signal (C7)", gate.onStateChanged(failed = true, running = false, onScreen = true, validated = true))
    }

    @Test
    fun `coming back after a return, with the signal gone again, runs nothing`() {
        failedOnScreen()
        gate.onStateChanged(failed = true, running = false, onScreen = false, validated = true)
        gate.onSignalReturn(failed = true, running = false, onScreen = false)
        assertFalse("coming back with no signal ran the search", gate.onStateChanged(failed = true, running = false, onScreen = true, validated = false))
    }

    // ── Amendment Q, QC1 (the owner: "3 tries, then wait for me"; "Growing: 3, then 10") ──

    /** An automatic try started by a return, then failed. */
    private fun automaticTryFails() {
        assertTrue(gate.onSignalReturn(failed = true, running = false, onScreen = true))
        gate.onStateChanged(failed = false, running = true, onScreen = true, validated = true)
        gate.onStateChanged(failed = true, running = false, onScreen = true, validated = true)
    }

    @Test
    fun `a failed automatic try is followed by timed ones, 3 s then 10 s, within the three`() {
        failedOnScreen()
        automaticTryFails()
        assertEquals("the wait after the first automatic try failed", 3_000L, gate.timedTryDelayMs())
        assertTrue("the timed try did not run", gate.onTimedTryDue(failed = true, running = false, onScreen = true, validated = true))
        gate.onStateChanged(failed = false, running = true, onScreen = true, validated = true)
        gate.onStateChanged(failed = true, running = false, onScreen = true, validated = true)
        assertEquals("the wait after the second failed", 10_000L, gate.timedTryDelayMs())
        assertTrue(gate.onTimedTryDue(failed = true, running = false, onScreen = true, validated = true))
        gate.onStateChanged(failed = false, running = true, onScreen = true, validated = true)
        gate.onStateChanged(failed = true, running = false, onScreen = true, validated = true)
        assertEquals("a wait after the third failed", null, gate.timedTryDelayMs())
        assertEquals(3, gate.tries)
    }

    @Test
    fun `a manual try that fails is not followed by a timed one`() {
        failedOnScreen()
        gate.onUserRetry()
        gate.onStateChanged(failed = false, running = true, onScreen = true, validated = true)
        gate.onStateChanged(failed = true, running = false, onScreen = true, validated = true)
        assertEquals("a timed try after a manual one failed", null, gate.timedTryDelayMs())
    }

    @Test
    fun `a timed try due with no signal does not run, and the next return does`() {
        failedOnScreen()
        automaticTryFails()
        assertTrue("a timed try ran with no signal", !gate.onTimedTryDue(failed = true, running = false, onScreen = true, validated = false))
        assertEquals("still waiting after it fell due with no signal", null, gate.timedTryDelayMs())
        assertTrue("the return after it did not run the search", gate.onSignalReturn(failed = true, running = false, onScreen = true))
    }

    @Test
    fun `a timed try due off screen waits for Forager to come back`() {
        failedOnScreen()
        automaticTryFails()
        assertTrue("a timed try ran off screen", !gate.onTimedTryDue(failed = true, running = false, onScreen = false, validated = true))
        assertEquals("the timed try was dropped off screen", 3_000L, gate.timedTryDelayMs())
    }

    @Test
    fun `Try again, a success or a return of signal drops a timed try that is waiting`() {
        failedOnScreen()
        automaticTryFails()
        gate.onUserRetry()
        assertEquals("Try again did not drop the timed try", null, gate.timedTryDelayMs())

        failedOnScreen()
        automaticTryFails()
        gate.onStateChanged(failed = false, running = false, onScreen = true, validated = true)
        assertEquals("a success did not drop the timed try", null, gate.timedTryDelayMs())

        failedOnScreen()
        automaticTryFails()
        assertTrue(gate.onSignalReturn(failed = true, running = false, onScreen = true))
        assertEquals("a return of signal running a try did not drop the timed one", null, gate.timedTryDelayMs())
    }
}
