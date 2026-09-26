package com.zynergylabs.forager.app.photo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CaptureCountdown] headless, under virtual time: no Compose, no Android, no dialog. The
 * dialog's own tests (`InAppCameraTimerTest`) drive the same class through the shutter.
 *
 * **Every test that starts a countdown ends it in a `finally`** (CLAUDE.md, the unstopped poll
 * loop): a countdown here is finite, so a forgotten one would not spin `runTest` forever the way
 * `TrackRecordingViewModel`'s loop does, but a failed assertion part-way through must still not
 * leave a job running into the next assertion's idea of time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureCountdownTest {

    /**
     * A scope on the test's virtual time that a test can cancel the way a removed dialog cancels
     * its own. A child of `backgroundScope`, not of the test's own job: an open child `Job` under
     * the test's job never completes, and `runTest` fails on it after a minute of real time.
     */
    private fun TestScope.childScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))

    private fun TestScope.fromZero(countdown: CaptureCountdown, seconds: Int, body: () -> Unit) {
        try {
            countdown.start(seconds) { body() }
            runCurrent()
        } catch (t: Throwable) {
            countdown.cancel()
            throw t
        }
    }

    @Test
    fun `at 3 s, nothing at 2_9 s and exactly one capture at 3 s`() = runTest {
        val countdown = CaptureCountdown(childScope())
        var captures = 0
        try {
            fromZero(countdown, 3) { captures += 1 }
            advanceTimeBy(2_900); runCurrent()
            assertEquals("nothing at 2.9 s", 0, captures)
            advanceTimeBy(100); runCurrent()
            assertEquals("exactly one at 3 s", 1, captures)
            advanceTimeBy(60_000); runCurrent()
            assertEquals("and never again", 1, captures)
        } finally {
            countdown.cancel()
        }
    }

    @Test
    fun `at 10 s, nothing at 9_9 s and exactly one capture at 10 s`() = runTest {
        val countdown = CaptureCountdown(childScope())
        var captures = 0
        try {
            fromZero(countdown, 10) { captures += 1 }
            advanceTimeBy(9_900); runCurrent()
            assertEquals("nothing at 9.9 s", 0, captures)
            advanceTimeBy(100); runCurrent()
            assertEquals("exactly one at 10 s", 1, captures)
            advanceTimeBy(60_000); runCurrent()
            assertEquals("and never again", 1, captures)
        } finally {
            countdown.cancel()
        }
    }

    @Test
    fun `the remaining whole seconds count down 3, 2, 1 and clear at zero`() = runTest {
        val countdown = CaptureCountdown(childScope())
        try {
            assertNull("nothing shown before a start", countdown.remainingSeconds.value)
            fromZero(countdown, 3) {}
            assertEquals(3, countdown.remainingSeconds.value)
            assertTrue(countdown.isRunning)
            advanceTimeBy(1_000); runCurrent()
            assertEquals(2, countdown.remainingSeconds.value)
            advanceTimeBy(1_000); runCurrent()
            assertEquals(1, countdown.remainingSeconds.value)
            advanceTimeBy(1_000); runCurrent()
            assertNull("cleared at zero", countdown.remainingSeconds.value)
            assertFalse("and finished", countdown.isRunning)
        } finally {
            countdown.cancel()
        }
    }

    @Test
    fun `cancel stops the countdown with no capture, ever, and clears the display`() = runTest {
        val countdown = CaptureCountdown(childScope())
        var captures = 0
        try {
            fromZero(countdown, 3) { captures += 1 }
            advanceTimeBy(1_500); runCurrent()
            assertEquals("precondition: counting", 2, countdown.remainingSeconds.value)

            countdown.cancel()
            runCurrent()

            assertNull("the display clears", countdown.remainingSeconds.value)
            assertFalse(countdown.isRunning)
            advanceTimeBy(60_000); runCurrent()
            assertEquals("no capture, ever", 0, captures)
        } finally {
            countdown.cancel()
        }
    }

    /** The dialog's case: Back removes the dialog, and its `rememberCoroutineScope` is cancelled under the countdown. */
    @Test
    fun `cancelling the scope it was given stops it with no capture`() = runTest {
        val scope = childScope()
        val countdown = CaptureCountdown(scope)
        var captures = 0
        try {
            fromZero(countdown, 3) { captures += 1 }
            advanceTimeBy(1_000); runCurrent()

            scope.cancel()
            advanceTimeBy(60_000); runCurrent()

            assertEquals(0, captures)
            assertFalse(countdown.isRunning)
        } finally {
            countdown.cancel()
        }
    }

    @Test
    fun `the timer cycles Off, 3 s, 10 s and back to Off`() {
        assertEquals(TimerMode.ThreeSeconds, TimerMode.Off.next())
        assertEquals(TimerMode.TenSeconds, TimerMode.ThreeSeconds.next())
        assertEquals(TimerMode.Off, TimerMode.TenSeconds.next())
        assertEquals(listOf(0, 3, 10), TimerMode.entries.map { it.seconds })
    }
}
