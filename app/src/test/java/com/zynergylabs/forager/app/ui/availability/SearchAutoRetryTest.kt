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
}
