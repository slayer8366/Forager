package com.zynergylabs.forager.app.map

import com.zynergylabs.forager.app.domain.NetworkSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The blocking side of the network signal that a held tile waits on (dispatch 2026-10-23-06 Amendment 1). Real threads
 * and real time, because what is under test is a thread being woken; the timeouts are wide so a slow CI machine cannot
 * turn a pass into a fail.
 */
class SignalNetworkWaitTest {

    private val readings = MutableSharedFlow<Boolean>(replay = 1)
    private val signal = object : NetworkSignal {
        override val validated = readings
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `before any reading the network is not taken as validated`() {
        val wait = SignalNetworkWait(signal, scope)
        assertFalse(wait.isValidated())
    }

    @Test
    fun `a waiting thread is woken by the network validating, long before its timeout`() {
        val wait = SignalNetworkWait(signal, scope)
        runBlocking { readings.emit(false) }
        val emitter = Thread {
            Thread.sleep(300)
            runBlocking { readings.emit(true) }
        }
        val start = System.nanoTime()
        emitter.start()

        wait.awaitValidated(20_000)

        val waitedMs = (System.nanoTime() - start) / 1_000_000
        emitter.join()
        assertTrue("woken after $waitedMs ms of a 20 s timeout", waitedMs < 10_000)
        assertTrue(wait.isValidated())
    }

    @Test
    fun `with no validated network the wait ends at its timeout`() {
        val wait = SignalNetworkWait(signal, scope)
        runBlocking { readings.emit(false) }
        val start = System.nanoTime()

        wait.awaitValidated(500)

        val waitedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue("returned after $waitedMs ms of a 500 ms timeout", waitedMs >= 400)
        assertFalse(wait.isValidated())
    }

    @Test
    fun `a lost network is read as not validated`() {
        val wait = SignalNetworkWait(signal, scope)
        runBlocking { readings.emit(true) }
        awaitReading { wait.isValidated() }
        runBlocking { readings.emit(false) }
        awaitReading { !wait.isValidated() }
    }

    private fun awaitReading(condition: () -> Boolean) {
        val end = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            assertTrue("the reading never arrived", System.nanoTime() < end)
            Thread.sleep(10)
        }
    }
}
