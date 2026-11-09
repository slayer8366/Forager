package com.zynergylabs.forager.app.ui.log

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Journal redesign J5c: the open details sheet's target is kept in `rememberSaveable` state through
 * [RecordDetailsTargetSaver], so it must come back as the same record, and a saved value it does not
 * recognise must fail loudly rather than quietly open nothing.
 */
class RecordDetailsTargetSaverTest {

    private val scope = SaverScope { true }

    private fun roundTrip(target: RecordDetailsTarget?): RecordDetailsTarget? {
        val saved = with(RecordDetailsTargetSaver) { scope.save(target) }!!
        return RecordDetailsTargetSaver.restore(saved)
    }

    @Test
    fun `every target, and no target, comes back as itself`() {
        val targets = listOf(
            RecordDetailsTarget.WaypointDetails("wp-creek"),
            // Dispatch -616: a waypoint opened from its walk keeps the walk, colons and all.
            RecordDetailsTarget.WaypointDetails("wp-creek", fromTrackId = "track:with:colons"),
            RecordDetailsTarget.TrackDetails("track:with:colons"),
            RecordDetailsTarget.OfflineRegionDetails(7L),
            null,
        )
        for (target in targets) assertEquals(target, roundTrip(target))
    }

    @Test
    fun `Back from a waypoint opened from its walk returns to the walk, and from anything else closes`() {
        assertEquals(RecordDetailsTarget.TrackDetails("t1"), RecordDetailsTarget.WaypointDetails("w1", fromTrackId = "t1").returnsTo())
        assertEquals(null, RecordDetailsTarget.WaypointDetails("w1").returnsTo())
        assertEquals(null, RecordDetailsTarget.TrackDetails("t1").returnsTo())
        assertEquals(null, RecordDetailsTarget.OfflineRegionDetails(7L).returnsTo())
    }

    @Test
    fun `a saved walk waypoint with no separator fails loudly`() {
        val error = assertThrows(IllegalStateException::class.java) { RecordDetailsTargetSaver.restore("walk-waypoint:abc") }
        assertEquals("Not a saved record-details target: 'walk-waypoint:abc'", error.message)
    }

    @Test
    fun `a saved value that names no target fails loudly`() {
        val error = assertThrows(IllegalStateException::class.java) { RecordDetailsTargetSaver.restore("find:abc") }
        assertEquals("Not a saved record-details target: 'find:abc'", error.message)
    }
}
