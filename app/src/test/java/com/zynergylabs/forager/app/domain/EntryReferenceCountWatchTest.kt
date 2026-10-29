package com.zynergylabs.forager.app.domain

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [watchEntryReferenceCounts] is what every screen's live counts go through, so its rules are held here once.
 *
 * Amendment 1 of dispatch 2026-10-28-08: "a failed read logs and keeps the last good counts". Amendment 2 (RECORD -1784,
 * Sorrel's N4; the owner: "Retry by itself (Recommended)"): a failed read is logged and counting starts again on its own,
 * after a bounded backoff, so the counts come back true without a restart; cancellation still ends it.
 *
 * **Why every test goes through [watching].** A watch that retries a source that keeps failing never completes on its own:
 * it waits, tries, fails and waits again. Left running in this test's scope, `runTest`'s closing idle-advance would spin
 * through those waits in virtual time forever (CLAUDE.md, "A stalled test run in this repo is an unstopped poll loop, not a
 * slow test"). [watching] cancels the watch in a `finally`, so even an assertion that throws stops it, and each test moves
 * time only by a bounded [advanceTimeBy], never `advanceUntilIdle`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryReferenceCountWatchTest {

    private val written = mutableListOf<Map<String, Int>>()
    private val logged = mutableListOf<String>()
    private val startedAt = mutableListOf<Long>()
    private val log = ErrorLog { tag, message, error -> logged += "$tag: $message (${error.message})" }

    /** Starts the watch in this test's scope, runs [body], and cancels the watch whatever [body] does. */
    private suspend fun TestScope.watching(counts: Flow<Map<String, Int>>, body: suspend (Job) -> Unit) {
        val job = watchEntryReferenceCounts(this, counts, log, "waypoint") { written += it }
        try {
            body(job)
        } finally {
            job.cancel()
        }
    }

    /** A source whose [n]th collection (from 1) does what [attempt] says, recording the virtual time each collection starts. */
    private fun TestScope.source(attempt: suspend FlowCollector<Map<String, Int>>.(n: Int) -> Unit): Flow<Map<String, Int>> {
        var n = 0
        return flow {
            n++
            startedAt += testScheduler.currentTime
            attempt(n)
        }
    }

    @Test
    fun `a failed read is logged and tried again, and the counts it then reads reach the screen`() = runTest {
        val counts = source { n ->
            when (n) {
                1 -> {
                    emit(mapOf("wp-1" to 1))
                    throw IOException("disk read failed")
                }
                2 -> throw IOException("database locked")
                else -> {
                    emit(mapOf("wp-1" to 2))
                    awaitCancellation()
                }
            }
        }

        watching(counts) {
            advanceTimeBy(60_000)
            runCurrent()

            assertEquals(
                "a failed read is tried again, and the counts read after it reach the screen",
                listOf(mapOf("wp-1" to 1), mapOf("wp-1" to 2)),
                written,
            )
            assertEquals("tried at once, then 1 s after the first failure, then 2 s after the second", listOf(0L, 1_000L, 3_000L), startedAt)
            assertEquals(
                "each failure is logged, naming what it was counting and when it tries again",
                listOf(
                    "EntryReferenceCounts: Couldn't count journal entries keeping each waypoint; trying again in 1s. (disk read failed)",
                    "EntryReferenceCounts: Couldn't count journal entries keeping each waypoint; trying again in 2s. (database locked)",
                ),
                logged,
            )
        }
    }

    @Test
    fun `a read that keeps failing waits twice as long each time, never more than 30 s, and logs every failure`() = runTest {
        val counts = source { throw IOException("disk read failed") }

        watching(counts) {
            advanceTimeBy(100_000)
            runCurrent()

            assertEquals(
                "waits of 1, 2, 4, 8 and 16 s, then 30 s each time after",
                listOf(0L, 1_000L, 3_000L, 7_000L, 15_000L, 31_000L, 61_000L, 91_000L),
                startedAt,
            )
            assertEquals("every failure is logged", 8, logged.size)
            assertEquals(
                "EntryReferenceCounts: Couldn't count journal entries keeping each waypoint; trying again in 30s. (disk read failed)",
                logged.last(),
            )
            assertTrue("a source that never reads leaves nothing written", written.isEmpty())
        }
    }

    @Test
    fun `a read that succeeds between failures starts its waits again from 1 s`() = runTest {
        val counts = source { n ->
            emit(mapOf("wp-1" to n))
            if (n < 3) throw IOException("disk read failed") else awaitCancellation()
        }

        watching(counts) {
            advanceTimeBy(60_000)
            runCurrent()

            assertEquals("every read's counts reach the screen", listOf(mapOf("wp-1" to 1), mapOf("wp-1" to 2), mapOf("wp-1" to 3)), written)
            assertEquals("a good read in between means the next failure waits 1 s again, not 2 s", listOf(0L, 1_000L, 2_000L), startedAt)
        }
    }

    @Test
    fun `cancelling the watch ends its retries`() = runTest {
        val counts = source { throw IOException("disk read failed") }

        watching(counts) { job ->
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals("positive control: before the cancel it was retrying (tries at 0, 1 and 3 s)", 3, startedAt.size)

            job.cancel()
            advanceTimeBy(600_000)
            runCurrent()

            assertTrue("the watch is over", job.isCompleted)
            assertEquals("no try after the cancel", 3, startedAt.size)
            assertEquals("and nothing more logged", 3, logged.size)
        }
    }
}
