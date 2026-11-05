package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CrowdedIconsMode
import com.zynergylabs.forager.app.domain.CrowdedIconsPreferenceRepository
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Test-only stores around the real "Crowded icons" repository, for AvailabilityScreenCrowdedIconsSettingTest (dispatch
 * 2026-11-05-03). Neither changes what a read or a write does to the file; each changes only when a write is made, or
 * what the test can see of it.
 */

/**
 * A store whose writes reach [delegate] late, at points the test controls (dispatch 2026-11-05-03).
 *
 * Every write is held when it is made. Each read through this store first reads [delegate], then lets the oldest held
 * write through and runs it to completion before returning. So a read returns the value from before that write, and the
 * write is on disk by the time the read returns.
 *
 * That is the interleaving CI showed on some runs (the Phase 1 report, section 3): the restart's wait read the first
 * pick's List, the second pick's write landed just after that read, and the third was still queued when the restart
 * cancelled the store. Here it happens on every run.
 *
 * A write let through runs on [Dispatchers.IO], not in its caller's context. DataStore runs a write's transform in the
 * caller's context (DataStore 1.2.1, DataStoreImpl.kt:351). The test reads from a `runBlocking` on the main thread, and
 * a write that needed the main looper there would never finish. A write held when the store is released is never made,
 * as a write still queued in a cancelled DataStore is not.
 *
 * Every write is held, the first too. Phase 1's plan let the first go straight through. That write would then run in the
 * ViewModel's main-thread context, and a read that let the second through while the first was still queued would block
 * the main thread behind it. Holding every write gives one release per read, whoever reads and however fast the
 * machine is.
 */
internal class LateCrowdedIconsStore(private val delegate: CrowdedIconsPreferenceRepository) : CrowdedIconsPreferenceRepository {
    private val held = ConcurrentLinkedQueue<Pair<CrowdedIconsMode, CompletableDeferred<Result<Unit>>>>()

    override suspend fun getCrowdedIconsMode(unknown: (String) -> Unit): Result<CrowdedIconsMode?> {
        val read = delegate.getCrowdedIconsMode(unknown)
        held.poll()?.let { (mode, written) -> written.complete(withContext(Dispatchers.IO) { delegate.setCrowdedIconsMode(mode) }) }
        return read
    }

    override suspend fun setCrowdedIconsMode(mode: CrowdedIconsMode): Result<Unit> {
        val written = CompletableDeferred<Result<Unit>>()
        held += mode to written
        return written.await()
    }
}
