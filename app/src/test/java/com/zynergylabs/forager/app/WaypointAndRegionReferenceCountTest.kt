package com.zynergylabs.forager.app

import com.zynergylabs.forager.app.domain.ErrorLog
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Journal redesign J4, D6 (owner ruling "Fix in J4 (Recommended)"): the waypoint and offline-region
 * reference counts `MainActivity` hands `TrackRecordingViewModel` and `AvailabilityViewModel`
 * ([waypointEntryReferenceCountOrZero], [offlineRegionEntryReferenceCountOrZero]) log through the
 * app's [ErrorLog] seam when their fallback to 0 fires, the way J3 fixed the photo count
 * ([PhotoEntryReferenceCountTest]), and stay silent when the count succeeds. These counts are what
 * the Undo snackbar's reference warning reads, so a failed one silently drops the warning.
 */
class WaypointAndRegionReferenceCountTest {

    private data class Logged(val tag: String, val message: String, val error: Throwable)

    private val logged = mutableListOf<Logged>()
    private val fakeLog = ErrorLog { tag, message, error -> logged += Logged(tag, message, error) }

    @Test
    fun `a failed waypoint count shows 0 and is logged once, with the waypoint's id and the cause`() = runBlocking {
        val cause = IOException("database closed")

        val count = waypointEntryReferenceCountOrZero("wp-7", { Result.failure(cause) }, fakeLog)

        assertEquals(0, count)
        assertEquals("one log call: $logged", 1, logged.size)
        val entry = logged.single()
        assertSame(cause, entry.error)
        assertTrue("the message names the waypoint: ${entry.message}", entry.message.contains("wp-7"))
        assertTrue("the message says what is shown instead: ${entry.message}", entry.message.contains("0"))
    }

    @Test
    fun `a successful waypoint count is returned as is and logs nothing, 0 included`() = runBlocking {
        assertEquals(3, waypointEntryReferenceCountOrZero("wp-7", { Result.success(3) }, fakeLog))
        assertEquals(0, waypointEntryReferenceCountOrZero("wp-8", { Result.success(0) }, fakeLog))
        assertEquals(emptyList<Logged>(), logged)
    }

    @Test
    fun `a failed offline-region count shows 0 and is logged once, with the region's id and the cause`() = runBlocking {
        val cause = IOException("database closed")

        val count = offlineRegionEntryReferenceCountOrZero(4242L, { Result.failure(cause) }, fakeLog)

        assertEquals(0, count)
        assertEquals("one log call: $logged", 1, logged.size)
        val entry = logged.single()
        assertSame(cause, entry.error)
        assertTrue("the message names the region: ${entry.message}", entry.message.contains("4242"))
        assertTrue("the message says what is shown instead: ${entry.message}", entry.message.contains("0"))
    }

    @Test
    fun `a successful offline-region count is returned as is and logs nothing, 0 included`() = runBlocking {
        assertEquals(5, offlineRegionEntryReferenceCountOrZero(4242L, { Result.success(5) }, fakeLog))
        assertEquals(0, offlineRegionEntryReferenceCountOrZero(4243L, { Result.success(0) }, fakeLog))
        assertEquals(emptyList<Logged>(), logged)
    }
}
