package com.zynergylabs.forager.app.domain

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dispatch 2026-10-28-08, Amendment 1: "a failed read logs and keeps the last good counts". [watchEntryReferenceCounts]
 * is what every screen's live counts go through, so the rule is held here once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryReferenceCountWatchTest {

    @Test
    fun `a failed read is logged, and the last counts written stay`() = runTest {
        val written = mutableListOf<Map<String, Int>>()
        val logged = mutableListOf<String>()
        val counts = flow {
            emit(mapOf("wp-1" to 2))
            throw IOException("disk read failed")
        }

        watchEntryReferenceCounts(this, counts, ErrorLog { tag, message, error -> logged += "$tag: $message (${error.message})" }, "waypoint") { written += it }
        advanceUntilIdle()

        assertEquals("the counts read before the failure are the last written, and nothing replaces them", listOf(mapOf("wp-1" to 2)), written)
        assertEquals(
            "the failure is logged once, naming what it was counting",
            listOf("EntryReferenceCounts: Couldn't count journal entries keeping each waypoint; keeping the last counts. (disk read failed)"),
            logged,
        )
    }
}
