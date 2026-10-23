package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routedata.RouteDataMissingReason
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [offlineRouteLine]: what a region's row says about its trail routes, the owner's step path as answered (dispatch 2026-10-23-03). */
class OfflineRouteLineTest {

    private val stored = RegionRouteData.Stored("run", "t", 10L, File("/r"))
    private val retryable = RegionRouteData.Missing(RouteDataMissingReason.NETWORK)

    private fun line(data: RegionRouteData, fetching: Boolean = false, failed: Boolean = false, noSignal: Boolean = false) =
        offlineRouteLine(data, fetching, failed, noSignal)

    @Test
    fun `A, stored routes add no line, even with squares missing, which the row does not show`() {
        assertNull(line(stored))
        assertNull(line(stored.copy(missingSquares = listOf("W130_N45"))))
    }

    @Test
    fun `B, a failed attempt says the routes didn't download and offers Try again`() {
        assertEquals(OfflineRouteLine("Map only. Trail routes didn't download.", tryAgain = true), line(retryable, failed = true))
        assertEquals(OfflineRouteLine("Map only. Trail routes didn't download.", tryAgain = true), line(RegionRouteData.NotFetched, failed = true))
    }

    @Test
    fun `no Try again before an attempt has failed`() {
        assertNull(line(retryable))
        assertNull(line(RegionRouteData.NotFetched))
    }

    @Test
    fun `C, no routes for the area, with no button`() {
        assertEquals(OfflineRouteLine("Map only. No trail routes for this area yet.", tryAgain = false), line(RegionRouteData.NoneHere))
    }

    @Test
    fun `F, a reason a retry can't fix reads as C, with no button, attempted or not`() {
        for (reason in listOf(RouteDataMissingReason.FORBIDDEN, RouteDataMissingReason.TOO_LARGE, RouteDataMissingReason.NO_APP_KEY, RouteDataMissingReason.OVER_PHONE_LIMIT, RouteDataMissingReason.BAD_REQUEST)) {
            assertEquals(reason.code, OfflineRouteLine("Map only. No trail routes for this area yet.", tryAgain = false), line(RegionRouteData.Missing(reason), failed = true))
        }
    }

    @Test
    fun `E offline, wanted routes wait for a connection, with no button`() {
        val expected = OfflineRouteLine("Map only. Trail routes download next time you're online.", tryAgain = false)
        assertEquals(expected, line(RegionRouteData.NotFetched, noSignal = true))
        assertEquals(expected, line(retryable, failed = true, noSignal = true))
        assertEquals(expected, line(RegionRouteData.Damaged("gone"), noSignal = true))
    }

    @Test
    fun `a fetch running elsewhere, as the download's own, reads as fetching`() {
        assertEquals(OfflineRouteLine("Getting trail routes", tryAgain = false), line(RegionRouteData.Fetching))
    }

    @Test
    fun `while fetching, the row says so`() {
        assertEquals(OfflineRouteLine("Getting trail routes", tryAgain = false), line(RegionRouteData.NotFetched, fetching = true))
    }
}
