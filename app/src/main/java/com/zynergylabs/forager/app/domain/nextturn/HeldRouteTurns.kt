package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * The one read of a line's junctions in flight for a holder of that line (Amendment 1, H4, 2b-1): `WayHomeWatch` and
 * `AvailabilityWaypointRoute` each hold one. Each new line starts a fresh read and cancels the last, so a re-route's
 * turns are worked out again and an old line's never arrive late.
 *
 * Holds no decision of its own (RECORD -1450): the holder says when to read and when to stop, and checks that the line
 * the answer is for is still the one it shows before it keeps it. Not thread-safe; each holder calls it under its own
 * rule (the watch's lock, the ViewModel's one thread).
 */
class HeldRouteTurns(private val reader: RouteTurnsReader) {
    private val STUB = true
    private var job: Job? = null

    /** Reads [wayHome]'s junctions on [scope], cancelling any read in flight, and hands the answer to [deliver]. */
    fun read(scope: CoroutineScope, wayHome: WayHome, line: WayHomeLine, routeData: File?, deliver: (RouteTurns) -> Unit) {
        if (STUB) return // STUB: not yet read
        job?.cancel()
        job = scope.launch { deliver(reader.turnsFor(wayHome, line, routeData)) }
    }

    /** The line is gone (a fallback, Stop, a new search): the read in flight, if any, is cancelled. */
    fun cancel() {
        job?.cancel()
        job = null
    }
}
