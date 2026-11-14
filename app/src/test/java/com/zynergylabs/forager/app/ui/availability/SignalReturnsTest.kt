package com.zynergylabs.forager.app.ui.availability

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [signalReturns], headless (dispatch 2026-11-11-01, C4 "At once (Recommended)" and D1: "a signal that flaps must not produce a
 * burst of calls"). The times each return is told at are read from the test scheduler's virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignalReturnsTest {

    private val interval = SIGNAL_RETURN_MIN_INTERVAL_MS

    @Test
    fun `a connection there from the start is not a return`() = runTest {
        val signal = MutableSharedFlow<Boolean>(replay = 1)
        val told = mutableListOf<Long>()
        backgroundScope.launch { signalReturns(signal, interval).collect { told += testScheduler.currentTime } }
        signal.emit(true)
        advanceTimeBy(5 * interval)
        assertEquals("a connection there from the start was told as a return", emptyList<Long>(), told)
    }

    @Test
    fun `a return after a loss is told at once`() = runTest {
        val signal = MutableSharedFlow<Boolean>(replay = 1)
        val told = mutableListOf<Long>()
        backgroundScope.launch { signalReturns(signal, interval).collect { told += testScheduler.currentTime } }
        signal.emit(false)
        advanceTimeBy(1_000)
        signal.emit(true)
        runCurrent()
        assertEquals("the return was not told at once", listOf(1_000L), told)
    }

    @Test
    fun `a signal that flaps is told once at once and once more when the interval ends`() = runTest {
        val signal = MutableSharedFlow<Boolean>(replay = 1)
        val told = mutableListOf<Long>()
        backgroundScope.launch { signalReturns(signal, interval).collect { told += testScheduler.currentTime } }
        signal.emit(false)
        runCurrent()
        // Twenty returns, 100 ms apart, over four seconds.
        repeat(20) {
            advanceTimeBy(100)
            signal.emit(true)
            runCurrent()
            advanceTimeBy(100)
            signal.emit(false)
            runCurrent()
        }
        advanceTimeBy(10 * interval)
        assertEquals("twenty returns in four seconds were told more than twice", listOf(100L, 100L + interval), told)
    }

    @Test
    fun `returns further apart than the interval are each told at once`() = runTest {
        val signal = MutableSharedFlow<Boolean>(replay = 1)
        val told = mutableListOf<Long>()
        backgroundScope.launch { signalReturns(signal, interval).collect { told += testScheduler.currentTime } }
        signal.emit(false)
        runCurrent()
        for (at in listOf(1_000L, 1_000L + 2 * interval, 1_000L + 4 * interval)) {
            advanceTimeBy(at - testScheduler.currentTime - 500)
            signal.emit(false)
            advanceTimeBy(500)
            signal.emit(true)
            runCurrent()
        }
        assertEquals("a return was held back or lost", listOf(1_000L, 1_000L + 2 * interval, 1_000L + 4 * interval), told)
    }
}
