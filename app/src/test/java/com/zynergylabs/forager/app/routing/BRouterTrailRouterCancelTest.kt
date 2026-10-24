package com.zynergylabs.forager.app.routing

import btools.router.RoutingEngine
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import com.zynergylabs.forager.app.routing.RoutingTestData.ACROSS_FROM
import com.zynergylabs.forager.app.routing.RoutingTestData.ACROSS_TO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Sorrel's review of #382, M1 (dispatch 2026-10-23-09 Amendment 1, part 1): cancelling a route stops the engine.
 *
 * The fixture's routes finish in milliseconds, too fast to cancel in the middle of, so the first engine here is the real
 * `RoutingEngine`, built from the real request, whose search is replaced by a wait: it holds for up to ten seconds unless
 * it is terminated, which is what BRouter's own search loop does with the flag (`RoutingEngine.java:1975`). Every
 * engine after it is the real one, untouched.
 *
 * Once terminated, the held search keeps going for [STOPPING_MILLIS] before it returns, as a real engine does while it
 * finishes its pass and runs its own `finally` (releasing the static `ProfileCache`, `RoutingEngine.java:332-339`), and
 * sets `stopped` as its last act. So the test pins the wait for the engine to stop, not only the `terminate()` call: a
 * cancel that gave up the one-route lock before the engine stopped would return before `stopped` is set, and sooner
 * than [STOPPING_MILLIS] (Sorrel's review of #389, M-389-1; dispatch 2026-10-23-09 Amendment 3, fix 1).
 */
class BRouterTrailRouterCancelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logged = mutableListOf<Throwable>()
    private val errorLog = ErrorLog { _, _, error -> synchronized(logged) { logged += error } }

    @Test
    fun `cancelling a route terminates its engine, waits for it to stop, and the next route does not wait behind it`() = runBlocking {
        val searching = CountDownLatch(1)
        val held = AtomicReference<RoutingEngine>()
        val firstEngine = AtomicBoolean(true)
        val stopped = AtomicBoolean(false)
        val router = BRouterTrailRouter(
            RoutingTestData.profileDir(tmp),
            errorLog,
            Dispatchers.Default,
            BRouterTrailRouter.MAX_RUNNING_TIME_MILLIS,
            newEngine = { data, waypoints, context ->
                if (firstEngine.getAndSet(false)) {
                    object : RoutingEngine(null, null, data, waypoints, context, RoutingEngine.BROUTER_ENGINEMODE_ROUTING) {
                        override fun doRun(maxRunningTime: Long) {
                            held.set(this)
                            searching.countDown()
                            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(HELD_SEARCH_SECONDS)
                            while (!isTerminated && System.nanoTime() < until) Thread.sleep(5)
                            if (isTerminated) Thread.sleep(STOPPING_MILLIS)
                            stopped.set(true)
                        }
                    }
                } else {
                    RoutingEngine(null, null, data, waypoints, context, RoutingEngine.BROUTER_ENGINEMODE_ROUTING)
                }
            },
        )
        val data = RoutingTestData.segments(tmp)

        val first = launch(Dispatchers.Default) { router.route(ACROSS_FROM, ACROSS_TO, data) }
        assertTrue("the first engine's search started", searching.await(5, TimeUnit.SECONDS))
        val cancelledAt = System.nanoTime()
        first.cancelAndJoin()
        val cancelMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cancelledAt)

        assertTrue("the cancelled route's engine was terminated", held.get().isTerminated)
        assertTrue("the cancel waited for the engine to stop before it returned", stopped.get())
        assertTrue("the cancel took at least the engine's $STOPPING_MILLIS ms to stop: took $cancelMillis ms", cancelMillis >= STOPPING_MILLIS)
        assertTrue("the cancelled route ended within 2 s, not at its search's own end: took $cancelMillis ms", cancelMillis < 2_000)
        val next = withTimeout(5_000) { router.route(ACROSS_FROM, ACROSS_TO, data) }
        assertTrue("the next route is a route: $next", next is TrailRouteResult.Found)
        assertEquals("a cancel is not an engine failure, and logs nothing", emptyList<Throwable>(), logged.toList())
    }

    private companion object {
        const val HELD_SEARCH_SECONDS = 10L

        /** How long the held search keeps going after it is terminated, before it returns. */
        const val STOPPING_MILLIS = 300L
    }
}
