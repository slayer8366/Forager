package com.zynergylabs.forager.app.ui.availability

import android.graphics.Bitmap
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_DARK
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_LIGHT
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-11-05-10, Amendment 1, E19 (RECORD -2181; the owner, with a crop of the landscape navigation card's top end corner:
 * "This corner of the navigation card doesn't have an outline.  Can it get one too?", restated as every edge of the navigation card
 * that faces the map outlined, round the corner and down the end edge before the side bar: "Yes, that's it"). The owner's case: their
 * S26, landscape navigation with a saved route's card open on the navigation card, by day, not fullscreen.
 *
 * Read from rendered pixels of the real [AvailabilityScreen] over a flat grey stand-in map, in every placement of the navigation card:
 * landscape at both rotations (the side bar on the right at both, RECORD -956; the insets mirrored), side bar shown and fullscreen, a saved route's card resting
 * on the navigation card and not, and portrait (where the navigation card is the compass strip's card); by day and at night. Landscape
 * runs with the window insets a phone delivers (a cutout on one side, the navigation bar on the side bar's), since Robolectric
 * otherwise reports none, and with none the side bar's side in fullscreen lies on the window's edge.
 *
 * The claim, for every edge and rounded corner: **wherever bare map lies just outside it, the outline is on it.** Along each straight
 * edge, every 2 dp, the pixel 3 dp outside is read; where it is the bare map (the screen's commonest colour, within [MAP_MATCH]), one of
 * the three pixels where a 1 dp line lies inside that edge must be the outline over the card's own fill there ([isLine], as in
 * `MapChromeOutlinePixelsTests`). Round each rounded corner, every 6 degrees, the pixel 3 dp outside the arc; where it is bare map, a
 * pixel from 14 to 16 dp from the corner's centre, inside the card's shape, must be the outline. Edges whose outside is another piece
 * (the side bar, the route card, the search pill) or off the window are not read. Each placement is also written as a PNG under the
 * build's test-results directory, which CI uploads.
 *
 * **E20** (RECORD -2181; the owner's marked screenshot: the missing line is at the bottom, where the card's end meets the side bar's
 * rounded bottom corner; "Outline the card's end there"). The straight edges above are read only from 17 dp above the card's bottom,
 * and the side bar's corner curves away within its bottom 16 dp, so the reading never reached that spot (E19's 36 cases passed for
 * that reason). The end edge is now read there too: every 1 dp up the bottom [MAP_CARD_CORNER_RADIUS] of the side the side bar is on,
 * the pixel 2 dp outside; where it is bare map, one of the four pixels where a 1 dp line lies at that edge must be the outline. With the
 * side bar shown, the spot must be found (bare map read there at least once), so the reading cannot pass without reaching it. **E21**
 * (RECORD -2181, the owner: one shared line, no 1 dp step) moved that line, with the side bar shown, from inside the card's edge out to
 * where the side bar's line lies, so it is read there, over the map; in fullscreen the card's own end edge is read inside it, as before.
 * `NavigationCardEndLineInLineTests` claims the columns exactly.
 */
abstract class NavigationCardOutlineEveryEdgeTests(private val dark: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val t = ROUTE_CARD_NOON_CLOCK.nowEpochMillis()
    // The made-up point P (dispatch 2026-11-05-10's rules), and the start a short way from it.
    private val fix = LocationFix.Update(lat = P_LAT, lng = P_LNG, altitude = 104.9, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = P_LAT - 0.0065, lng = P_LNG + 0.01, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.background(Color(0xFF808080))) }
    private val outline = if (dark) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT
    private val theme = if (dark) "night" else "day"
    private lateinit var image: PixelMap

    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    /**
     * The phone turned either way. RECORD -956: the side bar is on the window's right at both rotations; what changes is where the
     * insets lie, the cutout on the left and the navigation bar on the right at [Surface.ROTATION_90], the reverse at
     * [Surface.ROTATION_270] (the 2026-09-26 capture found the 3-button bar on the port edge at both, `PortEdge.kt`).
     */
    protected fun landscape(rotation: Int, fullscreen: Boolean, routeCard: Boolean) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        setScreen()
        // The S22's insets at rotation 1 (RouteCardBarRig.applyInsets), mirrored at ROTATION_270.
        val rotation90 = rotation == Surface.ROTATION_90
        applyInsets(left = if (rotation90) 25.dp else 48.dp, right = if (rotation90) 48.dp else 25.dp, navigationOnRight = rotation90)
        if (fullscreen) {
            composeRule.onNodeWithContentDescription("Fullscreen").performTouchInput { click(center) }
            settle()
            assertEquals("fullscreen: the side bar has slid away", 0, count(COMPACT_NAVIGATION_RAIL_TAG))
        } else {
            assertEquals("the side bar shows", 1, count(COMPACT_NAVIGATION_RAIL_TAG))
        }
        if (routeCard) openRouteCard()
        val card = bounds(NAVIGATION_HUD_TAG)
        if (routeCard) {
            val route = bounds(ROUTE_CARD_TAG)
            assertTrue("the route card rests on the navigation card: route card $route, navigation card $card", route.bottom > card.top && route.top < card.top)
        }
        val name = "landscape-${if (rotation90) "rot90" else "rot270"}-${if (fullscreen) "fullscreen" else "bar-shown"}-${if (routeCard) "route-card" else "no-route-card"}"
        // E20: the side bar is on the right at both rotations (RECORD -956), so the card's end is its right edge.
        val rail = if (fullscreen) null else bounds(COMPACT_NAVIGATION_RAIL_TAG)
        println("MEASURED E20 $name $theme: card $card, side bar $rail")
        checkEveryMapFacingEdge(name, card, roundedTop = true, railCornerSpotExpected = !fullscreen)
    }

    /**
     * Dispatch 2026-11-06-01, Amendment 1, J3 step 3 and J4 (RECORD -2183, the owner: "Own line, card moves up", "Round and outline"):
     * at font 2.0 with an evening line and the next turn on its own line, the card moves up off the screen's bottom edge to clear the
     * walker's dot; then its bottom corners round and its outline goes all round, so every map-facing edge and all four corners carry it.
     */
    protected fun landscapeMovedUp(rotation: Int, fullscreen: Boolean) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        setScreen(evening = true)
        val rotation90 = rotation == Surface.ROTATION_90
        applyInsets(left = if (rotation90) 25.dp else 48.dp, right = if (rotation90) 48.dp else 25.dp, navigationOnRight = rotation90)
        if (fullscreen) {
            composeRule.onNodeWithContentDescription("Fullscreen").performTouchInput { click(center) }
            settle()
            assertEquals("fullscreen: the side bar has slid away", 0, count(COMPACT_NAVIGATION_RAIL_TAG))
        }
        val card = bounds(NAVIGATION_HUD_TAG)
        val screenBottom = composeRule.onRoot().getUnclippedBoundsInRoot().bottom
        val name = "moved-up-${if (rotation90) "rot90" else "rot270"}-${if (fullscreen) "fullscreen" else "bar-shown"}"
        println("MEASURED J4 $name $theme: card $card, the screen's bottom $screenBottom, evening pieces ${count(NAVIGATION_HUD_EVENING_PIECE_TAG)}")
        assertTrue("$name: positive control: the evening line is on the card", count(NAVIGATION_HUD_EVENING_PIECE_TAG) > 0)
        assertTrue("$name: positive control: the card $card is moved up off the screen's bottom edge ($screenBottom)", card.bottom < screenBottom - MAP_CARD_CORNER_RADIUS)
        checkEveryMapFacingEdge(name, card, roundedTop = true, roundedBottom = true)
    }

    protected fun portrait(routeCard: Boolean) {
        setScreen()
        if (routeCard) openRouteCard()
        checkEveryMapFacingEdge("portrait-${if (routeCard) "route-card" else "no-route-card"}", bounds(STRIP_TAG), roundedTop = false)
    }

    private fun setScreen(evening: Boolean = false) {
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            ForagerTheme(darkTheme = dark) {
                AvailabilityScreen(
                    uiState = AvailabilityUiState(liveFix = fix, plannedTrips = listOf(ROUTE_TRIP)).withFixHeight(),
                    // J4: an evening line ("Dark since", 37 minutes ago), and a next turn, for the card moved up.
                    recording = AvailabilityScreenRecording(isRecording = true, recordingSundownLine = if (evening) com.zynergylabs.forager.app.domain.SundownLine.DarkSince(t - 37 * 60_000L) else null),
                    navigation = AvailabilityScreenNavigation(
                        isReturning = true,
                        navigationTarget = start,
                        returnRoute = if (evening) {
                            ReturnRoute.Ahead(LatLng(start.lat, start.lng), 245.0, nextTurn = com.zynergylabs.forager.app.domain.nextturn.NextTurn.Turn(com.zynergylabs.forager.app.domain.nextturn.TurnBand.SLIGHT, com.zynergylabs.forager.app.domain.nextturn.TurnSide.LEFT, metersAway = 140.0, sameSidePathsBefore = 0))
                        } else {
                            ReturnRoute.Ahead(LatLng(start.lat, start.lng), 245.0)
                        },
                    ),
                    compassProvider = FixedCompass(134f),
                    computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                    currentTime = ROUTE_CARD_NOON_CLOCK,
                    mapSlot = map,
                    mapControls = AvailabilityScreenMapControls(routePlotting = RoutePlottingControls(state, plotting)),
                    searchInputs = AvailabilityScreenSearchInputs(
                        onUseCurrentLocation = {}, onManualLatChanged = {}, onManualLngChanged = {}, onSearchManualCoordinates = {},
                        onRadiusChanged = {}, onMonthSelected = {}, onMapTabSelected = {}, onSeasonalTabSelected = {},
                        onTaxonSearchQueryChanged = {}, onTaxonSearchResultSelected = {}, onDismissTaxonSuggestions = {},
                        onReopenTaxonSuggestions = {}, onPlaceTripPin = { _, _, _ -> }, onDeletePlannedTrip = {}, onRecentSearchSelected = {},
                    ),
                    offlineMaps = AvailabilityScreenOfflineMaps(
                        onOfflineMapLatChanged = {}, onOfflineMapLngChanged = {}, onOfflineMapRadiusChanged = {}, onOfflineMapNameChanged = {},
                        onOfflineMapsOpened = {}, onDownloadOfflineMaps = {}, onDeleteOfflineRegion = {},
                    ),
                    settings = AvailabilityScreenSettings(onNightModeMapsChanged = {}, onThemeModeChanged = {}),
                )
            }
        }
        settle()
    }

    /** A saved route's card, opened as the Trip Planner's route opens it; its opening touches are `RouteCardLayoutTest`'s. */
    private fun openRouteCard() {
        composeRule.runOnUiThread { plotting.onOpenSavedRoute(ROUTE_TRIP, fromTripPlanner = false) }
        settle()
        assertEquals("the route card shows", 1, count(ROUTE_CARD_TAG))
    }

    private fun applyInsets(left: Dp, right: Dp, navigationOnRight: Boolean) {
        val l = with(composeRule.density) { left.roundToPx() }
        val r = with(composeRule.density) { right.roundToPx() }
        val (cutout, navigation) = if (navigationOnRight) {
            androidx.core.graphics.Insets.of(l, 0, 0, 0) to androidx.core.graphics.Insets.of(0, 0, r, 0)
        } else {
            androidx.core.graphics.Insets.of(0, 0, r, 0) to androidx.core.graphics.Insets.of(l, 0, 0, 0)
        }
        val insets = androidx.core.view.WindowInsetsCompat.Builder()
            .setInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout(), cutout)
            .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), navigation)
            .build()
        composeRule.runOnUiThread {
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(composeRule.activity.window.decorView.findViewById(android.R.id.content), insets)
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
    }

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
    private fun px(d: Dp): Int = (d.value * composeRule.density.density).roundToInt()
    private fun pxF(d: Dp): Float = d.value * composeRule.density.density

    // ── Reading pixels ──

    private fun lum(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    private fun rgb(c: Color) = "(%.3f, %.3f, %.3f; lum %.3f)".format(c.red, c.green, c.blue, lum(c))
    private fun inside(x: Int, y: Int) = x in 0 until image.width && y in 0 until image.height

    private fun lineOver(fill: Color): Color {
        val a = outline.alpha
        return Color(red = outline.red * a + fill.red * (1f - a), green = outline.green * a + fill.green * (1f - a), blue = outline.blue * a + fill.blue * (1f - a))
    }

    /** As `MapChromeOutlinePixelsTests.isLine`: within 0.07 of the outline over [fill], and at least 0.12 from [fill]. */
    private fun isLine(p: Color, fill: Color) = abs(lum(p) - lum(lineOver(fill))) < 0.07f && abs(lum(p) - lum(fill)) > 0.12f

    /**
     * The bare map: the stand-in's own grey, drawn opaque with nothing over it. A first version took the screen's commonest colour,
     * which at 640 dp and font 2.0 was the cards' fill (run 42390107028), so it read fills as map; by day and at night the bare map
     * there read 0.502, the stand-in's grey.
     */
    private val bareMap = Color(0xFF808080)

    private fun capture(name: String) {
        val bitmap = composeRule.onRoot().captureToImage()
        val dir = File("build/test-results/testDebugUnitTest/navigation-card-outline-every-edge").apply { mkdirs() }
        val widthDp = (bitmap.width / composeRule.density.density).roundToInt()
        val file = File(dir, "$name-$widthDp-$theme.png")
        file.outputStream().use { bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT|${file.absolutePath}|${bitmap.width}x${bitmap.height}")
        image = bitmap.toPixelMap()
    }

    /**
     * The claim (see the class doc). [roundedTop]: the landscape card's top two corners are rounded and its bottom is square on the
     * screen's edge; the portrait strip's bottom two are rounded.
     */
    private fun checkEveryMapFacingEdge(name: String, card: DpRect, roundedTop: Boolean, railCornerSpotExpected: Boolean = false, roundedBottom: Boolean = false) {
        capture(name)
        val map = bareMap
        fun isMap(x: Int, y: Int) = inside(x, y) && abs(lum(image[x, y]) - lum(map)) < MAP_MATCH &&
            abs(image[x, y].red - map.red) < MAP_MATCH && abs(image[x, y].blue - map.blue) < MAP_MATCH
        val misses = mutableListOf<String>()
        val facing = sortedMapOf<String, Int>()
        val out = px(3.dp)
        val r = 16.dp
        val left = px(card.left)
        val top = px(card.top)
        val right = px(card.right)
        val bottom = px(card.bottom)
        val inFill = px(4.dp)

        // The card's fill beside an edge: the middle of three readings 3 dp apart along it, so its 1 px divider, met at the top edge, is
        // not taken for the fill (run 42390107028: the one top-edge miss per capture was at the divider, the line read against it).
        fun fillNear(at: Pair<Int, Int>, horizontal: Boolean): Color {
            val d = px(3.dp)
            val (x, y) = at
            val reads = listOf(-d, 0, d).map { o -> if (horizontal) x + o to y else x to y + o }.filter { (a, b) -> inside(a, b) }.map { (a, b) -> image[a, b] }
            return reads.sortedBy { lum(it) }[reads.size / 2]
        }

        // Straight edges: (where, probe outside, the three line pixels inside, the fill reference inside).
        fun edge(where: String, along: IntProgression, probe: (Int) -> Pair<Int, Int>, line: (Int) -> List<Pair<Int, Int>>, fillAt: (Int) -> Pair<Int, Int>) {
            for (a in along) {
                val (ox, oy) = probe(a)
                if (!isMap(ox, oy)) continue
                facing[where] = (facing[where] ?: 0) + 1
                val fill = fillNear(fillAt(a), horizontal = where == "top" || where == "bottom")
                val run = line(a).filter { (x, y) -> inside(x, y) }
                if (run.none { (x, y) -> isLine(image[x, y], fill) }) {
                    misses += "$where at ${run.first()}: bare map ${rgb(image[ox, oy])} at ($ox, $oy), line pixels ${run.joinToString { (x, y) -> rgb(image[x, y]) }}, fill ${rgb(fill)}, the outline over it ${rgb(lineOver(fill))}"
                }
            }
        }
        val step = px(2.dp)
        val alongX = (px(card.left + r + 1.dp)..px(card.right - r - 1.dp) step step)
        val alongY = (px(card.top + r + 1.dp)..px(card.bottom - r - 1.dp) step step)
        edge("top", alongX, { x -> x to top - out }, { x -> (1..3).map { x to top + it } }, { x -> x to top + inFill })
        edge("bottom", alongX, { x -> x to bottom + out }, { x -> (2..4).map { x to bottom - it } }, { x -> x to bottom - inFill })
        edge("left", alongY, { y -> left - out to y }, { y -> (1..3).map { left + it to y } }, { y -> left + inFill to y })
        edge("right", alongY, { y -> right + out to y }, { y -> (2..4).map { right - it to y } }, { y -> right - inFill to y })

        // E20: the end edge (right, by the side bar) over the card's bottom MAP_CARD_CORNER_RADIUS, where the side bar's rounded bottom
        // corner curves away from it. Probed 2 dp out, every 1 dp, so the narrow wedge of map between the card and that corner is met.
        // J4: not on a card moved up, whose own bottom corner is rounded there and read below as a corner (run 42413989442: in
        // fullscreen this straight-edge reading met the map outside the rounded corner's own shape).
        if (roundedTop && !roundedBottom) {
            val railCorner = RAIL_CORNER_SPOT
            val alongCorner = (px(card.bottom - MAP_CARD_CORNER_RADIUS)..(bottom - 1) step px(1.dp))
            // E21: with the side bar shown, the line lies just beyond the card's edge, in the side bar's line's columns, over the map; in
            // fullscreen the card's own end edge is drawn, inside its edge, as before.
            if (railCornerSpotExpected) {
                edge(railCorner, alongCorner, { y -> right + px(2.dp) to y }, { y -> (0..3).map { right + it to y } }, { y -> right + px(2.dp) to y })
            } else {
                edge(railCorner, alongCorner, { y -> right + px(2.dp) to y }, { y -> (1..4).map { right - it to y } }, { y -> right - inFill to y })
            }
        }

        // Rounded corners: centre, and the signs that point from it to the corner.
        val corners = if (roundedTop && roundedBottom) {
            // J4: moved up, all four.
            listOf(Triple("top-left corner", -1, -1), Triple("top-right corner", 1, -1), Triple("bottom-left corner", -1, 1), Triple("bottom-right corner", 1, 1))
        } else if (roundedTop) {
            listOf(Triple("top-left corner", -1, -1), Triple("top-right corner", 1, -1))
        } else {
            listOf(Triple("bottom-left corner", -1, 1), Triple("bottom-right corner", 1, 1))
        }
        for ((where, sx, sy) in corners) {
            val cx = if (sx < 0) pxF(card.left + r) else pxF(card.right - r)
            val cy = if (sy < 0) pxF(card.top + r) else pxF(card.bottom - r)
            for (deg in 0..90 step 6) {
                val a = Math.toRadians(deg.toDouble())
                fun at(radius: Dp) = (cx + sx * pxF(radius) * cos(a)).roundToInt() to (cy + sy * pxF(radius) * sin(a)).roundToInt()
                val (ox, oy) = at(r + 3.dp)
                if (!isMap(ox, oy)) continue
                facing[where] = (facing[where] ?: 0) + 1
                val (fx, fy) = at(8.dp)
                val fill = image[fx, fy]
                // Inside the shape (14 to 16 dp) the line over the card's fill; just outside it (16 to 17.5 dp) the line over the map, which
                // a corner beside the side bar is drawn on (RECORD -990: out to the side bar's line, a stroke beyond the card's own edge),
                // counted only on the line's side of the map by more than half the gap, so a blend of fill and map never counts (K-2a).
                val run = (0..6).map { at(14.dp + (it / 3f).dp) }.distinct().filter { (x, y) -> inside(x, y) }
                val outer = (1..4).map { at(16.dp + (it / 3f).dp) }.distinct().filter { (x, y) -> inside(x, y) }
                val gap = lum(lineOver(map)) - lum(map)
                fun overMap(p: Color) = (lum(p) - lum(map)) * kotlin.math.sign(gap) > abs(gap) / 2f
                // J4, a card moved up: its bottom corner beside the side bar is drawn out to the side bar's line too, and there the line
                // lies at the shape's own edge, over the map (run 42413989442: at 54 degrees, 0.376 by day and 0.702 at night at 15.7 dp,
                // the map 0.502), so a pixel of the line over the map within 14 to 16 dp counts there, by the same half-gap rule.
                val overMapInside = roundedBottom && sy > 0 && run.any { (x, y) -> overMap(image[x, y]) }
                if (run.none { (x, y) -> isLine(image[x, y], fill) } && outer.none { (x, y) -> overMap(image[x, y]) } && !overMapInside) {
                    misses += "$where at $deg degrees: bare map ${rgb(image[ox, oy])} at ($ox, $oy), pixels 14 to 16 dp in ${run.joinToString { (x, y) -> rgb(image[x, y]) }}, 16 to 17.3 dp ${outer.joinToString { (x, y) -> rgb(image[x, y]) }}, fill ${rgb(fill)}, the outline over it ${rgb(lineOver(fill))}"
                }
            }
        }
        println("MEASURED E19 $name $theme: card $card, bare map ${rgb(map)}, map-facing samples $facing, without the outline ${misses.size}")
        misses.forEach { println("MISS E19 $name $theme: $it") }
        assertTrue("$name: the navigation card faces the map somewhere (its samples by edge $facing)", facing.isNotEmpty())
        if (railCornerSpotExpected) {
            assertTrue(
                "$name, $theme: the spot beside the side bar's bottom corner was not reached (no bare map read there; by edge $facing)",
                (facing[RAIL_CORNER_SPOT] ?: 0) > 0,
            )
        }
        assertTrue("$name, $theme: ${misses.size} map-facing places on the navigation card without the outline (by edge, map-facing $facing); first: ${misses.firstOrNull()}", misses.isEmpty())
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"

        /** E20's reading, by name (see the class doc). */
        const val RAIL_CORNER_SPOT = "end edge beside the side bar's bottom corner"

        /** The made-up point P, from dispatch 2026-11-05-10's rules. */
        const val P_LAT = 45.3736
        const val P_LNG = -121.6959

        /** How close to the bare map a pixel is read as bare map: the cards' fills over it are 0.2 or more away, day and night. */
        const val MAP_MATCH = 0.04f
    }
}

abstract class NavigationCardOutlineEveryEdgeLandscapeTests(dark: Boolean) : NavigationCardOutlineEveryEdgeTests(dark) {
    @Test fun `rotation 90, side bar shown, route card on the navigation card - the owner's case`() = landscape(Surface.ROTATION_90, fullscreen = false, routeCard = true)

    @Test fun `rotation 90, side bar shown, no route card`() = landscape(Surface.ROTATION_90, fullscreen = false, routeCard = false)

    @Test fun `rotation 90, fullscreen, route card on the navigation card`() = landscape(Surface.ROTATION_90, fullscreen = true, routeCard = true)

    @Test fun `rotation 90, fullscreen, no route card`() = landscape(Surface.ROTATION_90, fullscreen = true, routeCard = false)

    @Test fun `rotation 270, side bar shown, route card on the navigation card`() = landscape(Surface.ROTATION_270, fullscreen = false, routeCard = true)

    @Test fun `rotation 270, side bar shown, no route card`() = landscape(Surface.ROTATION_270, fullscreen = false, routeCard = false)

    @Test fun `rotation 270, fullscreen, route card on the navigation card`() = landscape(Surface.ROTATION_270, fullscreen = true, routeCard = true)

    @Test fun `rotation 270, fullscreen, no route card`() = landscape(Surface.ROTATION_270, fullscreen = true, routeCard = false)
}

/** J3 step 3 and J4: the card moved up, at font 2.0 with an evening line (see [NavigationCardOutlineEveryEdgeTests.landscapeMovedUp]). */
abstract class NavigationCardOutlineEveryEdgeMovedUpTests(dark: Boolean) : NavigationCardOutlineEveryEdgeTests(dark) {
    @Test fun `J4 - rotation 90, side bar shown, moved up - rounded and outlined all round`() = landscapeMovedUp(Surface.ROTATION_90, fullscreen = false)

    @Test fun `J4 - rotation 90, fullscreen, moved up - rounded and outlined all round`() = landscapeMovedUp(Surface.ROTATION_90, fullscreen = true)

    @Test fun `J4 - rotation 270, side bar shown, moved up - rounded and outlined all round`() = landscapeMovedUp(Surface.ROTATION_270, fullscreen = false)

    @Test fun `J4 - rotation 270, fullscreen, moved up - rounded and outlined all round`() = landscapeMovedUp(Surface.ROTATION_270, fullscreen = true)
}

abstract class NavigationCardOutlineEveryEdgePortraitTests(dark: Boolean) : NavigationCardOutlineEveryEdgeTests(dark) {
    @Test fun `portrait, route card open`() = portrait(routeCard = true)

    @Test fun `portrait, no route card`() = portrait(routeCard = false)
}

/** The owner's S26 in landscape at its default display size, about 891 x 411 dp (inferred, not read from the phone). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w891dp-h411dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardOutlineEveryEdgeS26DayTest : NavigationCardOutlineEveryEdgeLandscapeTests(dark = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w891dp-h411dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardOutlineEveryEdgeS26NightTest : NavigationCardOutlineEveryEdgeLandscapeTests(dark = true)

/** D32's case (RouteCardRecenterLeaveRoomTest): 640 x 360 dp at font 2.0, where both cards narrow for Re-center's room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardOutlineEveryEdgeNarrowDayTest : NavigationCardOutlineEveryEdgeLandscapeTests(dark = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardOutlineEveryEdgeNarrowNightTest : NavigationCardOutlineEveryEdgeLandscapeTests(dark = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardOutlineEveryEdgePortraitDayTest : NavigationCardOutlineEveryEdgePortraitTests(dark = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardOutlineEveryEdgePortraitNightTest : NavigationCardOutlineEveryEdgePortraitTests(dark = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardOutlineEveryEdgeMovedUpDayTest : NavigationCardOutlineEveryEdgeMovedUpTests(dark = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardOutlineEveryEdgeMovedUpNightTest : NavigationCardOutlineEveryEdgeMovedUpTests(dark = true)
