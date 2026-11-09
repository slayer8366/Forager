package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T11 fixes (dispatch 2026-10-09-02, RECORD -766; the owner's confirmation, RECORD -768): the order in which the navigation
 * display gives way, on pinned widths (one character = one pixel), so an order error is a literal failure. The screen-level
 * guards are `HudLandscapeT11FixesTest`.
 */
class NavigationHudFitTest {

    private val chars: (String) -> Int = { it.length }

    private val t = 1_791_050_400_000L
    private val here = LatLng(45.52, -122.68)
    private val start = Waypoint("origin", here.lat + 380.0 / 111_195.0, here.lng, null, "Start", "", t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private fun fix(at: Long = t, lat: Double = here.lat, accuracy: Float = 12.5f) =
        LocationFix.Update(lat = lat, lng = here.lng, altitude = 3_000.0, accuracyMeters = accuracy, timestampEpochMillis = at, provider = FixProvider.GPS)

    private fun readout(now: Long = t, route: ReturnRoute? = ReturnRoute.Ahead(LatLng(here.lat, here.lng + 0.001), 390.0), liveFix: LocationFix.Update? = fix(), target: Waypoint? = start) =
        navigationReadout(TrueHeadingReading.Available(281f), liveFix, target, DistanceUnit.MILES, now, route = route)

    // ── The short forms ──

    @Test
    fun `the short forms read as the owner confirmed them`() {
        assertEquals("169°", turnShortForm("sharp right 169°"))
        assertEquals("4°", turnShortForm("ahead 4°"))
        assertNull(turnShortForm(""))
        assertNull(turnShortForm("Target"))
        assertEquals("≤ 16 ft", distanceShortForm("within 16 ft"))
        assertNull(distanceShortForm("1280 ft"))
        assertEquals("45 s old", staleFixShortText(45_000L))
        assertEquals("No fix 6 min", lostFixShortText(6 * 60_000L))
        assertEquals("No location", NO_FIX_SHORT_TEXT)
        assertEquals("No start point", NO_ORIGIN_SHORT_TEXT)
        assertEquals("No route", ROUTE_UNAVAILABLE_SHORT_TEXT)
    }

    @Test
    fun `each status carries its shorter forms in order, and a warning has no empty one`() {
        // RECORD -942: the straight-line note shows only before the first route or with it withheld (Pending here); on the
        // route (Ahead) there is no status at all. Was: the note on the route.
        assertEquals("on the route, no note", listOf(""), readout().statusForms())
        assertEquals(listOf("≈ 1250 ft straight", "≈ 1250 ft", ""), readout(route = ReturnRoute.Pending).statusForms())
        assertEquals("with a lead, the note is whole or gone", listOf("≈ 1250 ft straight", ""), readout(route = ReturnRoute.Pending).statusForms(kindInStatus = true))
        assertEquals(listOf("Last fix 45 s ago", "45 s old"), readout(now = t + 45_000L).statusForms())
        assertEquals(listOf("No fix for 6 min", "No fix 6 min"), readout(now = t + 6 * 60_000L).statusForms())
        assertEquals(listOf(NO_FIX_MESSAGE, "No location"), readout(liveFix = null).statusForms())
        assertEquals(listOf(NO_ORIGIN_TEXT, "No start point"), readout(target = null).statusForms())
        val fiftyMetres = start.copy(lat = here.lat + 50.0 / 111_195.0)
        assertEquals(listOf("Approaching", ""), readout(target = fiftyMetres, route = null).statusForms())
        assertEquals(listOf("Approaching · last fix 45 s ago", "Last fix 45 s ago", "45 s old"), readout(target = fiftyMetres, route = null, now = t + 45_000L).statusForms())
    }

    @Test
    fun `a readout copied with a new status keeps no other status's short form`() {
        val copied = readout(liveFix = null).copy(statusText = "Last seen 2 h ago, finding GPS…")
        assertEquals(listOf("Last seen 2 h ago, finding GPS…"), copied.statusForms())
        assertNull(copied.statusShortText)
    }

    // ── The first row ──

    private fun firstRow(r: NavigationHudReadout, shared: Int) = firstRowFit(r, shared, needlePx = 2, kindGapPx = 1, turnWidth = chars, figureWidth = chars, kindWidth = chars)

    @Test
    fun `the first row gives way in order - the kind moves, the turn shrinks to the bearing, the turn goes, the figure shortens`() {
        val r = readout().copy(targetText = "sharp right 169°", distanceText = "1280 ft", distanceKindText = "by trail")
        // turn 18, figure 7, kind 8 + gap 1.
        assertEquals(FirstRowFit("sharp right 169°", "1280 ft", kindInStatus = false), firstRow(r, 18 + 7 + 1 + 8))
        assertEquals(FirstRowFit("sharp right 169°", "1280 ft", kindInStatus = true), firstRow(r, 18 + 7))
        assertEquals(FirstRowFit("169°", "1280 ft", kindInStatus = true), firstRow(r, 4 + 7))
        assertEquals("the needle's own width stays", FirstRowFit("", "1280 ft", kindInStatus = true), firstRow(r, 2 + 7))
        assertEquals("only then the figure, which has no shorter form, is cut by its ellipsis", FirstRowFit("", "1280 ft", kindInStatus = true), firstRow(r, 5))
        val within = r.copy(distanceText = "within 16 ft", distanceKindText = null)
        assertEquals(FirstRowFit("", "≤ 16 ft", kindInStatus = false), firstRow(within, 9))
    }

    @Test
    fun `the turn words also give way to a warning or a message that would not fit under the figure`() {
        // No fix: "Target" under a dimmed needle, "—" in the large slot, "Location services unavailable" or "No location".
        val r = readout(liveFix = null)
        assertEquals(11, statusMinWidth(r, chars))
        assertEquals("Target stays while the short message fits", "Target", firstRowFit(r, 6 + 11, 2, 1, chars, chars, chars, statusMinWidth(r, chars)).turnText)
        assertEquals("Target goes when it would not", "", firstRowFit(r, 6 + 10, 2, 1, chars, chars, chars, statusMinWidth(r, chars)).turnText)
        assertEquals("a droppable note asks for no width", 0, statusMinWidth(readout(), chars))
    }

    // ── The status line ──

    private fun status(r: NavigationHudReadout, kindInStatus: Boolean, max: Int) = statusLineFit(r, kindInStatus, max, leadSeparatorPx = 3, widthOf = chars)

    @Test
    fun `the straight-line note shortens, then goes, before the kind's lead`() {
        // RECORD -942: the note now shows only with no route figure (Pending), where the display has no kind; the lead is set by
        // hand here so the pure rule (the note gives way before a lead) is still pinned. Was: readout() on the route.
        val r = readout(route = ReturnRoute.Pending).copy(distanceKindText = "by trail")
        assertEquals(StatusLineFit(null, "≈ 1250 ft straight"), status(r, false, 18))
        assertEquals(StatusLineFit(null, "≈ 1250 ft"), status(r, false, 17))
        assertEquals(StatusLineFit(null, ""), status(r, false, 8))
        // "By trail" (8) + " · " (3) + note (18) = 29.
        assertEquals(StatusLineFit("By trail", "≈ 1250 ft straight"), status(r, true, 29))
        assertEquals("never 'By trail · ≈ 1250 ft'", StatusLineFit("By trail", ""), status(r, true, 28))
        assertEquals("the lead stays alone, cut if it must be", StatusLineFit("By trail", ""), status(r, true, 3))
    }

    @Test
    fun `the stale warning keeps its short form and the lead goes before it is cut`() {
        val r = readout(now = t + 45_000L)
        assertEquals(StatusLineFit("By trail", "Last fix 45 s ago"), status(r, true, 8 + 3 + 17))
        assertEquals(StatusLineFit("By trail", "45 s old"), status(r, true, 8 + 3 + 8))
        // With the lead gone the warning takes the longest form that fits on its own.
        assertEquals(StatusLineFit(null, "Last fix 45 s ago"), status(r, true, 8 + 3 + 7))
        assertEquals(StatusLineFit(null, "45 s old"), status(r, true, 10))
        assertEquals("the last resort is the ellipsis on the short form", StatusLineFit(null, "45 s old"), status(r, true, 2))
    }

    @Test
    fun `Approaching goes whole rather than being cut`() {
        val fiftyMetres = start.copy(lat = here.lat + 50.0 / 111_195.0)
        val r = readout(target = fiftyMetres, route = null)
        assertEquals(StatusLineFit(null, "Approaching"), status(r, false, 11))
        assertEquals(StatusLineFit(null, ""), status(r, false, 10))
    }

    @Test
    fun `a message is shortened, never dropped`() {
        val r = readout(liveFix = null)
        assertEquals(StatusLineFit(null, NO_FIX_MESSAGE), status(r, false, NO_FIX_MESSAGE.length))
        assertEquals(StatusLineFit(null, "No location"), status(r, false, 11))
        assertEquals(StatusLineFit(null, "No location"), status(r, false, 4))
    }

    @Test
    fun `statusTextThatFits keeps -694's answers`() {
        val fiftyMetres = start.copy(lat = here.lat + 50.0 / 111_195.0)
        val r = readout(target = fiftyMetres, route = null, now = t + 45_000L)
        assertEquals("Approaching · last fix 45 s ago", statusTextThatFits(r, 100, chars))
        assertEquals("Last fix 45 s ago", statusTextThatFits(r, 30, chars))
        assertEquals("45 s old", statusTextThatFits(r, 10, chars))
    }

    // ── Try again beside its message ──

    @Test
    fun `the route message takes the first form that fits beside Try again`() {
        val large: (String) -> Int = { it.length * 2 }
        assertEquals(RouteMessageFit(ROUTE_UNAVAILABLE_TEXT, inStatusType = false), routeMessageFit(50, large, chars))
        assertEquals(RouteMessageFit(ROUTE_UNAVAILABLE_TEXT, inStatusType = true), routeMessageFit(25, large, chars))
        assertEquals("never in the large type, which makes the display taller", RouteMessageFit("No route", inStatusType = true), routeMessageFit(16, large, chars))
        assertEquals(RouteMessageFit("No route", inStatusType = true), routeMessageFit(8, large, chars))
        assertEquals(RouteMessageFit("No route", inStatusType = true), routeMessageFit(3, large, chars))
        assertTrue(retryFitsBeside(columnPx = 30, retryPx = 20, gapPx = 2, shortMessagePx = 8))
        assertFalse(retryFitsBeside(columnPx = 29, retryPx = 20, gapPx = 2, shortMessagePx = 8))
    }

    // ── The L's slide ──

    @Test
    fun `the L slides only in landscape, while navigating, on the display's side, at either rotation`() {
        // ROTATION_90: the rail, and so the display, on the right.
        assertTrue(navigationSlideApplies(landscape = true, isNavigating = true, clusterOnLeftSide = false, displayEdge = ScreenEdge.Right))
        assertFalse(navigationSlideApplies(landscape = true, isNavigating = true, clusterOnLeftSide = true, displayEdge = ScreenEdge.Right))
        // ROTATION_270: on the left (the owner: "Follow the display", RECORD -768).
        assertTrue(navigationSlideApplies(landscape = true, isNavigating = true, clusterOnLeftSide = true, displayEdge = ScreenEdge.Left))
        assertFalse(navigationSlideApplies(landscape = true, isNavigating = true, clusterOnLeftSide = false, displayEdge = ScreenEdge.Left))
        assertFalse("not navigating", navigationSlideApplies(landscape = true, isNavigating = false, clusterOnLeftSide = false, displayEdge = ScreenEdge.Right))
        assertFalse("portrait", navigationSlideApplies(landscape = false, isNavigating = true, clusterOnLeftSide = false, displayEdge = null))
    }
}
