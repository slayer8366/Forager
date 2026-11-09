package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Dispatch 2026-09-28-502, step 3: the dashed line navigating to a waypoint runs from the walker's fix to
 * the waypoint. With no fix, or one the HUD treats as lost, there is no walker to draw from; once arrived,
 * the line ends, as the return's does.
 *
 * And the owner's choice at the desk check ("Keep the last line, faded"): a line already drawn is kept,
 * faded, once the fix is lost, and comes back bright with a fresh fix; with no line drawn yet (the app
 * just opened), there is still none until the first fix.
 */
class StraightLineToTargetTest {

    private val t = 1_700_000_000_000L
    private val creek = Waypoint("wp-1", 45.326, -122.634, null, "Creek pin", "", 1_000L)
    private val oak = Waypoint("wp-2", 45.30, -122.60, null, "Big oak", "", 2_000L)

    private fun fix(metersSouth: Double, at: Long = t) =
        LocationFix.Update(lat = creek.lat - metersSouth / 111_195.08, lng = creek.lng, altitude = null, accuracyMeters = 3.79f, timestampEpochMillis = at, provider = FixProvider.GPS)

    private fun lineFrom(from: LocationFix.Update, to: Waypoint = creek) = listOf(LatLng(from.lat, from.lng), LatLng(to.lat, to.lng))

    private val sixMinutesLater = t + 6 * 60_000L

    @Test
    fun `from the walker's fix to the waypoint`() {
        val from = fix(500.0)
        assertEquals(listOf(LatLng(from.lat, from.lng), LatLng(creek.lat, creek.lng)), straightLineToTarget(from, creek, t + 1_000L))
    }

    @Test
    fun `no fix, or a lost one, draws no line`() {
        assertNull(straightLineToTarget(null, creek, t))
        assertNull("six minutes old is lost", straightLineToTarget(fix(500.0), creek, sixMinutesLater))
    }

    @Test
    fun `within 15 m the walker has arrived and the line ends`() {
        assertNull(straightLineToTarget(fix(12.0), creek, t + 1_000L))
        assertEquals(2, straightLineToTarget(fix(18.0), creek, t + 1_000L)?.size)
    }

    // ── The owner's "Keep the last line, faded" (desk check, 2026-10-04) ──

    @Test
    fun `with a current fix the line is current, from that fix`() {
        val from = fix(500.0)
        assertEquals(StraightLine(lineFrom(from), isCurrent = true), nextStraightLine(null, from, creek, t + 1_000L))
    }

    @Test
    fun `once the fix is lost, the line already drawn is kept, grey (offline)`() {
        // The owner, at the desk check after seeing it: "An offline grey color will work", then "Grey once location is
        // lost". This dispatch's own test until then expected the kept line faded blue.
        val from = fix(500.0)
        val drawn = nextStraightLine(null, from, creek, t + 1_000L)

        assertEquals(StraightLine(lineFrom(from), isCurrent = false, isOffline = true), nextStraightLine(drawn, from, creek, sixMinutesLater))
        assertEquals("no fix at all keeps it too", StraightLine(lineFrom(from), isCurrent = false, isOffline = true), nextStraightLine(drawn, null, creek, sixMinutesLater))
    }

    @Test
    fun `a fresh fix brings it back current, from the new fix`() {
        val kept = StraightLine(lineFrom(fix(500.0)), isCurrent = false)
        val fresh = fix(300.0, at = sixMinutesLater)
        // One second old: fresh, so current.
        assertEquals(StraightLine(lineFrom(fresh), isCurrent = true), nextStraightLine(kept, fresh, creek, sixMinutesLater + 1_000L))
    }

    @Test
    fun `with no line drawn yet, as when the app has just opened, there is none until the first fix`() {
        assertNull(nextStraightLine(null, null, creek, t))
        assertNull(nextStraightLine(null, fix(500.0), creek, sixMinutesLater))
    }

    @Test
    fun `arrival ends the line, kept or not`() {
        val drawn = nextStraightLine(null, fix(500.0), creek, t + 1_000L)
        assertNull(nextStraightLine(drawn, fix(10.0), creek, t + 1_000L))
    }

    @Test
    fun `a line to another waypoint is not kept for this one`() {
        val toOak = StraightLine(lineFrom(fix(500.0), oak), isCurrent = true)
        assertNull(nextStraightLine(toOak, null, creek, sixMinutesLater))
    }

    // ── The owner's "Fade with the HUD" (relayed by the planner, RECORD -506): the line fades when the HUD dims its
    // distance, from 30 s, as well as once the fix is lost. One test at each boundary. ──

    @Test
    fun `at the 30 s boundary - just before it the line is current, at it the line fades, from the same fix`() {
        val from = fix(500.0)
        assertEquals(StraightLine(lineFrom(from), isCurrent = true), nextStraightLine(null, from, creek, t + 29_999L))
        assertEquals(StraightLine(lineFrom(from), isCurrent = false), nextStraightLine(null, from, creek, t + 30_000L))
    }

    @Test
    fun `at the 5 min boundary - just before it the line is from the stale fix, faded, at it the line kept from before, faded`() {
        val before = StraightLine(lineFrom(fix(500.0)), isCurrent = true)
        val stale = fix(300.0, at = t)
        assertEquals("stale: faded blue, not grey", StraightLine(lineFrom(stale), isCurrent = false, isOffline = false), nextStraightLine(before, stale, creek, t + 5 * 60_000L - 1L))
        assertEquals(StraightLine(lineFrom(fix(500.0)), isCurrent = false, isOffline = true), nextStraightLine(before, stale, creek, t + 5 * 60_000L))
    }

    @Test
    fun `how long until the line next changes, so it can fade at that moment with no new fix to prompt it`() {
        // Dispatch -502 changed this own new test (Amendment of RECORD -506): it was "how long until a fix is lost", for the
        // one moment the line faded; with "Fade with the HUD" it fades at 30 s too, so the wait is to whichever is next.
        assertEquals("fresh, 10 s old: 20 s to stale", 20_000L, millisUntilFreshnessChanges(fix(500.0), t + 10_000L))
        assertEquals("stale, 1 min old: 4 min to lost", 4 * 60_000L, millisUntilFreshnessChanges(fix(500.0), t + 60_000L))
        assertNull("lost: nothing more to wait for", millisUntilFreshnessChanges(fix(500.0), sixMinutesLater))
        assertNull("no fix, nothing to wait for", millisUntilFreshnessChanges(null, t))
    }
}
