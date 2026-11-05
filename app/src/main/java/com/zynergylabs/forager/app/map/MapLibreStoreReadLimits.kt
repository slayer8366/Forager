package com.zynergylabs.forager.app.map

/**
 * This app's own operating limits on a read of MapLibre's offline store that SQLite refuses while MapLibre is mid-save
 * (dispatch 2026-11-05-01, Amendment 1 B1: the planner's call P4 on the Phase 1 report, RECORD -2121). They are stated
 * limits, not measurements: MapLibre logs no commit, so how long one lasts on a phone is not known (the Phase 1 report,
 * "Could not determine"). [retryingRefusals] applies them.
 */
internal object MapLibreStoreReadLimits {
    /**
     * The waits between attempts, in order: 50, 100, 200 and 400 ms, 750 ms in all.
     * - **Why more than one retry:** a commit is short, but commits come in runs while a map view starts. MapLibre writes
     *   on every tile and resource it reads from its store (`offline_database.cpp` at android-v13.5.0), and session 14's
     *   three refusals came 0.23 to 1.12 s after a style load. One immediate retry would likely land in the same run.
     * - **Why doubling from 50 ms:** the early attempts catch a gap between two commits quickly, and the later ones reach
     *   past a longer run.
     * - **Why 750 ms in all:** a read that never clears gives up in under 0.8 s, inside the owner's "within under a second"
     *   (RECORD -2122). The bound is per read: Navigate reads its two ends in turn (`WayToEnds.kt`), so a store that never
     *   clears costs it about 1.5 s before both ends go by trail, logged, as before.
     */
    val WAITS_MILLIS: List<Long> = listOf(50L, 100L, 200L, 400L)

    /** Attempts at one read, the first included: one more than the waits between them, so 5. */
    val ATTEMPTS: Int = WAITS_MILLIS.size + 1
}
