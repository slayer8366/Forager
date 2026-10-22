package com.zynergylabs.forager.app.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The "no signal" rule (dispatch 2026-10-22-01 Amendment 2, RECORD -1556): switch to Trail Map after 5 s without a
 * validated network, back after 30 s with one, and a change that does not last its delay is dropped. Driven in virtual
 * time through [noSignalWithHysteresis], the function the app's [NetworkSignal] goes through.
 *
 * Every test cancels its collector inside the body, so no pending delay outlives it (CLAUDE.md: an unstopped timer
 * makes `runTest` spin).
 */
class NetworkSignalTest {

    private fun kotlinx.coroutines.test.TestScope.startCollecting(input: MutableSharedFlow<Boolean>, out: MutableList<Boolean>) =
        launch { noSignalWithHysteresis(input).collect { out += it } }

    @Test
    fun `the delays are the planner's 5 s and 30 s`() {
        assertEquals(5_000L, NO_SIGNAL_SWITCH_DELAY_MS)
        assertEquals(30_000L, SIGNAL_RETURN_SWITCH_DELAY_MS)
    }

    @Test
    fun `the first reading is taken at once, with no delay, either way`() = runTest {
        for (validated in listOf(true, false)) {
            val input = MutableSharedFlow<Boolean>(replay = 1)
            val out = mutableListOf<Boolean>()
            val job = startCollecting(input, out)
            input.emit(validated)
            runCurrent()
            assertEquals("validated=$validated", listOf(!validated), out)
            job.cancel()
        }
    }

    @Test
    fun `losing the signal switches to no signal only after 5 s`() = runTest {
        val input = MutableSharedFlow<Boolean>(replay = 1)
        val out = mutableListOf<Boolean>()
        val job = startCollecting(input, out)
        input.emit(true)
        runCurrent()
        input.emit(false)
        advanceTimeBy(NO_SIGNAL_SWITCH_DELAY_MS - 1)
        runCurrent()
        assertEquals("still the chosen map at 4.999 s", listOf(false), out)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("no signal at 5 s", listOf(false, true), out)
        job.cancel()
    }

    @Test
    fun `the signal back switches only after 30 s`() = runTest {
        val input = MutableSharedFlow<Boolean>(replay = 1)
        val out = mutableListOf<Boolean>()
        val job = startCollecting(input, out)
        input.emit(false)
        runCurrent()
        input.emit(true)
        advanceTimeBy(SIGNAL_RETURN_SWITCH_DELAY_MS - 1)
        runCurrent()
        assertEquals("still Trail Map at 29.999 s", listOf(true), out)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("the chosen map at 30 s", listOf(true, false), out)
        job.cancel()
    }

    @Test
    fun `a signal that flickers back and forth never swaps the map`() = runTest {
        val input = MutableSharedFlow<Boolean>(replay = 1)
        val out = mutableListOf<Boolean>()
        val job = startCollecting(input, out)
        input.emit(true)
        runCurrent()
        // Lost for 4 s at a time, back for 1 s: never 5 s without.
        repeat(10) {
            input.emit(false)
            advanceTimeBy(4_000)
            runCurrent()
            input.emit(true)
            advanceTimeBy(1_000)
            runCurrent()
        }
        assertEquals(listOf(false), out)

        // Once switched, back for 20 s at a time: never 30 s with.
        input.emit(false)
        advanceTimeBy(NO_SIGNAL_SWITCH_DELAY_MS)
        runCurrent()
        repeat(5) {
            input.emit(true)
            advanceTimeBy(20_000)
            runCurrent()
            input.emit(false)
            advanceTimeBy(1_000)
            runCurrent()
        }
        assertEquals(listOf(false, true), out)
        job.cancel()
    }
}
