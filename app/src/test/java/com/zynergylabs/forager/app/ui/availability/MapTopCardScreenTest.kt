package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.MapSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-10-12-08 (RECORD -945 to -951): the compass strip and the navigation info on the real screen, with real touches
 * at screen coordinates, several across each target (CLAUDE.md: a semantic click asserts wiring, not routing; a finger is not a
 * point). Each sample is counted and the count asserted, so an empty sample cannot pass.
 */
abstract class MapTopCardScreenTests(private val rotation: Int?) {
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 12.5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    var returning by mutableStateOf(true)
    var route by mutableStateOf<ReturnRoute>(ReturnRoute.Unavailable(canRetry = true))
    private var generation by mutableIntStateOf(0)
    var exits = 0
    var retries = 0
    var longPresses = 0
    private var rotationSeen: Int? = null

    /** The render mode the screen last handed the map (RECORD -954: whether the "i" goes beside the credit strip). */
    var lastRenderMode: com.zynergylabs.forager.app.ui.map.MapRenderMode? = null

    private val map: MapSlot = { _, _, renderMode, _, _, _, _, _, modifier ->
        lastRenderMode = renderMode
        Box(modifier.testTag(TOP_CARD_MAP_TAG).pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }) })
    }

    /**
     * RECORD -954 (the owner: "Have the map i next to the open street maps logo"; "Just landscape"): the Maps tab asks the map to
     * put the "i" beside the credit strip in landscape, and not in portrait. The placement itself is `AttributionBesideCreditTest`'s;
     * MapLibre's control cannot be drawn or touched under Robolectric.
     */
    @Test
    fun `the map is asked to put the i beside the credit strip in landscape only`() {
        setScreen()
        assertEquals("beside the credit strip: landscape ${rotation != null}", rotation != null, lastRenderMode?.attributionBesideCredit)
    }

    fun setScreen() {
        if (rotation != null) Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            key(generation) {
                AvailabilityScreen(
                    uiState = AvailabilityUiState(liveFix = fix),
                    recording = AvailabilityScreenRecording(
                        isRecording = true,
                    ),
                    navigation = AvailabilityScreenNavigation(
                        isReturning = returning,
                        onToggleReturning = { exits++ },
                        navigationTarget = start,
                        returnRoute = route,
                        onRetryRoute = { retries++ },
                    ),
                    compassProvider = FixedCompass(45f),
                    computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                    currentTime = CurrentTimeProvider { t + 1_000L },
                    mapSlot = map,
                    searchInputs = AvailabilityScreenSearchInputs(
                        onUseCurrentLocation = {},
                        onManualLatChanged = {},
                        onManualLngChanged = {},
                        onSearchManualCoordinates = {},
                        onRadiusChanged = {},
                        onMonthSelected = {},
                        onMapTabSelected = {},
                        onSeasonalTabSelected = {},
                        onTaxonSearchQueryChanged = {},
                        onTaxonSearchResultSelected = {},
                        onDismissTaxonSuggestions = {},
                        onReopenTaxonSuggestions = {},
                        onPlaceTripPin = { _, _, _ -> },
                        onDeletePlannedTrip = {},
                        onRecentSearchSelected = {},
                    ),
                    offlineMaps = AvailabilityScreenOfflineMaps(
                        onOfflineMapLatChanged = {},
                        onOfflineMapLngChanged = {},
                        onOfflineMapRadiusChanged = {},
                        onOfflineMapNameChanged = {},
                        onOfflineMapsOpened = {},
                        onDownloadOfflineMaps = {},
                        onDeleteOfflineRegion = {},
                    ),
                    settings = AvailabilityScreenSettings(
                        onNightModeMapsChanged = {},
                        onThemeModeChanged = {},
                    ),
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        if (rotation != null) assertEquals("the screen must see the rotation this case is about", rotation, rotationSeen)
    }

    fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    fun bounds(tag: String): DpRect = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first().let { n ->
        with(composeRule.density) { DpRect(n.boundsInRoot.left.toDp(), n.boundsInRoot.top.toDp(), n.boundsInRoot.right.toDp(), n.boundsInRoot.bottom.toDp()) }
    }

    fun textOf(tag: String): String =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first().config[SemanticsProperties.Text].joinToString { it.text }

    private fun at(x: Dp, y: Dp) = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }

    fun touchAt(x: Dp, y: Dp) {
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { down(at(x, y)); up() }
        composeRule.waitForIdle()
    }

    fun longPressAt(x: Dp, y: Dp) {
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { down(at(x, y)); advanceEventTime(700); up() }
        composeRule.waitForIdle()
    }

    fun refresh() {
        composeRule.runOnIdle { generation++ }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    /** The centre and four points a third of the way to each edge: inside a round button's circle as well as a square one's. */
    fun across(r: DpRect): List<Pair<Dp, Dp>> {
        val cx = (r.left + r.right) / 2
        val cy = (r.top + r.bottom) / 2
        val dx = (r.right - r.left) / 3
        val dy = (r.bottom - r.top) / 3
        return listOf(cx to cy, cx - dx to cy, cx + dx to cy, cx to cy - dy, cx to cy + dy)
    }

    fun controls(): List<DpRect> = composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
        .filterNot { it.config.getOrNull(SemanticsProperties.TestTag) == TOP_CARD_MAP_TAG }
        .map { with(composeRule.density) { DpRect(it.boundsInRoot.left.toDp(), it.boundsInRoot.top.toDp(), it.boundsInRoot.right.toDp(), it.boundsInRoot.bottom.toDp()) } }

    fun inAny(x: Dp, y: Dp, rects: List<DpRect>) = rects.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }

    /** RECORD -950, "Nothing repeats": the heading once, in the navigation info; height, grid reference and the three-dot button once, on the strip. */
    @Test
    fun `navigating, nothing repeats - the heading in the navigation info, height, grid reference and the button on the strip`() {
        setScreen()
        assertTrue("the strip stays", shown(TOP_CARD_STRIP_TAG))
        assertEquals("the heading reads once", 1, composeRule.onAllNodesWithText("45° NE", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("in the navigation info", "45° NE", textOf(NAVIGATION_HUD_HEADING_TAG))
        assertEquals("no heading on the strip's line", 0, composeRule.onAllNodesWithTag(COMPASS_STRIP_HEADING_TAG).fetchSemanticsNodes().size)
        assertEquals("one height", 1, composeRule.onAllNodesWithTag(COMPASS_STRIP_ELEVATION_TAG).fetchSemanticsNodes().size)
        assertEquals("one grid reference", 1, composeRule.onAllNodesWithTag(COMPASS_STRIP_COORDINATES_TAG).fetchSemanticsNodes().size)
        assertEquals("one three-dot button", 1, composeRule.onAllNodesWithTag(MAP_QUICK_SETTINGS_BUTTON_TAG).fetchSemanticsNodes().size)
        val strip = bounds(TOP_CARD_STRIP_TAG)
        val button = bounds(MAP_QUICK_SETTINGS_BUTTON_TAG)
        val info = bounds(NAVIGATION_HUD_TAG)
        assertTrue("the button $button is on the strip $strip, above the navigation info $info", button.top >= strip.top && button.bottom <= info.top + 0.5.dp)
        assertEquals("the strip's own sundown line does not show", false, shown(STRIP_SUNDOWN_LINE_TAG))
        assertEquals("nor its Back by line", false, shown(STRIP_BACK_BY_LINE_TAG))
    }

    /** RECORD -950: X ends navigation; the navigation info goes and the heading returns to the strip's line. */
    @Test
    fun `real touches across the X stop navigating, and stopping puts the heading back on the strip's line`() {
        setScreen()
        var sampled = 0
        for ((x, y) in across(bounds(NAVIGATION_HUD_EXIT_TAG))) {
            val before = exits
            touchAt(x, y)
            assertEquals("a real touch at (${x.value}, ${y.value}) on the X stopped navigating", before + 1, exits)
            sampled++
        }
        assertEquals(5, sampled)
        composeRule.runOnIdle { returning = false }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertTrue("the navigation info has gone", !shown(NAVIGATION_HUD_TAG))
        assertEquals("the heading is back on the strip's line", "45° NE", textOf(COMPASS_STRIP_HEADING_TAG))
    }

    @Test
    fun `real touches across the strip's grid reference toggle it, and across its three-dot button open the menu, while navigating`() {
        setScreen()
        var last = textOf(COMPASS_STRIP_COORDINATES_TAG)
        var sampled = 0
        for ((x, y) in across(bounds(COMPASS_STRIP_COORDINATES_TAG))) {
            touchAt(x, y)
            val now = textOf(COMPASS_STRIP_COORDINATES_TAG)
            assertNotEquals("a real touch at (${x.value}, ${y.value}) on the grid reference toggled it", last, now)
            last = now
            sampled++
        }
        // The button's bounds are read before each touch: each fresh screen (refresh) starts back on MGRS, which changes the
        // landscape strip's width, and where the strip is left-aligned (ROTATION_270) its button moves with its right end.
        for (i in 0 until 5) {
            val (x, y) = across(bounds(MAP_QUICK_SETTINGS_BUTTON_TAG))[i]
            val before = exits
            touchAt(x, y)
            assertTrue("a real touch at (${x.value}, ${y.value}) on the three-dot button opened the menu", shown(MAP_QUICK_SETTINGS_MENU_TAG))
            assertEquals("and did not stop navigating", before, exits)
            refresh()
            sampled++
        }
        assertEquals(10, sampled)
    }

    @Test
    fun `real touches across Try again retry, and it goes once a route is found`() {
        setScreen()
        var sampled = 0
        for ((x, y) in across(bounds(NAVIGATION_HUD_RETRY_TAG))) {
            val before = retries
            touchAt(x, y)
            assertEquals("a real touch at (${x.value}, ${y.value}) on Try again retried", before + 1, retries)
            sampled++
        }
        assertEquals(5, sampled)
        composeRule.runOnIdle { route = ReturnRoute.Ahead(com.zynergylabs.forager.app.domain.model.LatLng(45.52, -122.679), 1_500.0) }
        composeRule.waitForIdle()
        assertTrue("Try again goes once a route is found", !shown(NAVIGATION_HUD_RETRY_TAG))
    }

    /**
     * Touches reach the map beside and around the navigation info: at its rounded lower corners, just below it and, in landscape,
     * beside it under the search bar. The card takes no touch outside its own controls.
     */
    @Test
    fun `long-presses at the card's rounded corner gaps, just off its open edge and beside it reach the map`() {
        setScreen()
        val card = bounds(NAVIGATION_HUD_TAG)
        val taken = controls()
        // Beside the card on the search bar's side, under the bar: the bar is on the left at both rotations (RECORD -956 (the owner: "One should look exactly line the other")).
        // Was `if (rotation == Surface.ROTATION_90) card.left - 12.dp else card.right + 12.dp`, the bar on the right at 270.
        val besideX = card.left - 12.dp
        val points = if (rotation == null) {
            listOf(
                card.left + 2.dp to card.bottom - 2.dp,
                card.right - 2.dp to card.bottom - 2.dp,
                card.left + 4.dp to card.bottom - 1.dp,
                card.right - 4.dp to card.bottom - 1.dp,
            ) + listOf(0.15f, 0.5f, 0.85f).flatMap { f -> val x = card.left + (card.right - card.left) * f; listOf(x to card.bottom + 3.dp, x to card.bottom + 12.dp) }
        } else {
            // RECORD -953: the landscape card sits on the bottom edge, its top two corners rounded: its top corner gaps, just above
            // it, and beside it reach the map.
            listOf(
                card.left + 2.dp to card.top + 2.dp,
                card.right - 2.dp to card.top + 2.dp,
                card.left + 4.dp to card.top + 1.dp,
                card.right - 4.dp to card.top + 1.dp,
            ) + listOf(0.15f, 0.5f, 0.85f).flatMap { f -> val x = card.left + (card.right - card.left) * f; listOf(x to card.top - 3.dp, x to card.top - 12.dp) } +
                listOf(besideX to card.top + 12.dp, besideX to (card.top + card.bottom) / 2)
        }
        var sampled = 0
        for ((x, y) in points) {
            if (inAny(x, y, taken)) continue
            val before = longPresses
            longPressAt(x, y)
            assertEquals("a long-press at (${x.value}, ${y.value}) by the card $card reached the map", before + 1, longPresses)
            sampled++
        }
        assertTrue("sampled enough points: $sampled of ${points.size}", sampled >= 8)
    }

    /**
     * RECORD -952 (the owner: "In landscape mode: Can we have the rounded corners on the app navigation buttons (the one on the
     * right side of the display) like it is in portrait?"): the rail's two corners facing the map are cut, as the bottom bar's top
     * two are (RECORD -937), so long-presses in each corner gap reach the map; clear of the corners the rail keeps them. A point
     * within a control's reach (its bounds, or the 6 dp a 36 dp control's touch target extends: the strip's three-dot button sits
     * against the rail's top corner) is skipped and counted, since a finger there goes to that control, on a device too.
     */
    fun assertRailInnerCornerGapsReachMap() {
        returning = false
        setScreen()
        val rail = bounds(COMPACT_NAVIGATION_RAIL_TAG)
        // RECORD -956 (the owner: "One should look exactly line the other"): the rail is on the right at both rotations, its inner edge its left. Was
        // `if (rotation == Surface.ROTATION_90) rail.left else rail.right` and inward -1 at 270, the rail on the left there.
        val innerX = rail.left
        val inward = 1
        val reach = controls().map { DpRect(it.left - 6.dp, it.top - 6.dp, it.right + 6.dp, it.bottom + 6.dp) }
        val top = listOf(innerX + 2.dp * inward to rail.top + 2.dp, innerX + 4.dp * inward to rail.top + 1.dp)
        val bottom = listOf(innerX + 2.dp * inward to rail.bottom - 2.dp, innerX + 4.dp * inward to rail.bottom - 1.dp)
        var reached = 0
        val skipped = mutableListOf<Pair<Dp, Dp>>()
        for ((x, y) in top + bottom) {
            if (inAny(x, y, reach)) { skipped += x to y; continue }
            val before = longPresses
            longPressAt(x, y)
            assertEquals("a long-press at (${x.value}, ${y.value}) in the rail's rounded inner corner reached the map (rail $rail)", before + 1, longPresses)
            reached++
        }
        println("MEASURED rail corner gaps, rotation $rotation: rail $rail, reached $reached, skipped beside a control $skipped")
        assertTrue("both bottom-corner points reached the map", bottom.none { it in skipped })
        assertTrue("sampled at least the bottom corner: $reached", reached >= 2)
        // Positive control: on the rail, clear of its corners, the rail takes the long-press.
        val mid = innerX + 6.dp * inward to (rail.top + rail.bottom) / 2
        val before = longPresses
        longPressAt(mid.first, mid.second)
        assertEquals("a long-press on the rail itself at (${mid.first.value}, ${mid.second.value}) stayed the rail's", before, longPresses)
    }

    /**
     * RECORD -953: the landscape card's top two corners are rounded and its bottom is square on the edge. Pixels, since the card
     * takes no touch anywhere and a long-press cannot tell a rounded corner from a square one: just inside each top corner the
     * pixel is the map's (as 4 dp above the card), while along the card's top edge, mid-width, it is the card's fill; and a bottom
     * corner, square, is the card's.
     */
    fun assertCardTopCornersRounded() {
        setScreen()
        val card = bounds(NAVIGATION_HUD_TAG)
        val image = composeRule.onRoot().captureToImage().toPixelMap()
        fun px(x: Dp, y: Dp): Color = with(composeRule.density) { image[x.toPx().toInt(), y.toPx().toInt()] }
        fun near(a: Color, b: Color) = abs(a.red - b.red) < 0.006f && abs(a.green - b.green) < 0.006f && abs(a.blue - b.blue) < 0.006f
        val mid = (card.left + card.right) / 2
        val mapAbove = px(mid, card.top - 4.dp)
        val edge = px(mid, card.top + 2.dp)
        val topLeft = px(card.left + 1.dp, card.top + 1.dp)
        val topRight = px(card.right - 2.dp, card.top + 1.dp)
        val bottomLeft = px(card.left + 1.dp, card.bottom - 1.dp)
        println("MEASURED card corners, rotation $rotation: map above $mapAbove, top edge $edge, top-left $topLeft, top-right $topRight, bottom-left $bottomLeft")
        // Closer to the map than to the card's fill, or the reverse: a cast shadow can tint a corner gap a little either way.
        fun dist(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
        assertTrue("positive control: the card's fill differs from the map above it ($edge against $mapAbove)", !near(edge, mapAbove))
        assertTrue("the top-left corner gap shows the map ($topLeft; map $mapAbove, card $edge)", dist(topLeft, mapAbove) < dist(topLeft, edge))
        assertTrue("the top-right corner gap shows the map ($topRight; map $mapAbove, card $edge)", dist(topRight, mapAbove) < dist(topRight, edge))
        assertTrue("the bottom-left corner is square, the card's ($bottomLeft; map $mapAbove, card $edge)", dist(bottomLeft, edge) < dist(bottomLeft, mapAbove))
    }

    /**
     * RECORD -953 ("have the card peak from the botton of the screen"): in landscape the card slides up from the bottom edge when
     * navigation starts. Six frames in, the card is lower than where it settles; settled, its bottom is the window's bottom.
     */
    fun assertCardSlidesUpFromTheBottom() {
        returning = true
        setScreen()
        val settled = bounds(NAVIGATION_HUD_TAG)
        composeRule.runOnIdle { returning = false }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { returning = true; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        repeat(6) { composeRule.mainClock.advanceTimeByFrame() }
        val mid = bounds(NAVIGATION_HUD_TAG)
        composeRule.mainClock.autoAdvance = true
        println("MEASURED card slide, rotation $rotation: mid $mid, settled $settled")
        assertTrue("mid-start the card $mid is below where it settles $settled: it slides up", mid.top > settled.top + 0.5.dp)
        val window = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertEquals("settled, the card's bottom is the window's bottom", window.bottom.value, settled.bottom.value, 0.5f)
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }
}

/**
 * Portrait: one card, no second tuck (RECORD -948: "have the card just extend down rather than a dual tuck, and a line to
 * separate the heading and nav info"). The navigation info is inside the strip's card, under the thin line; the card's bottom
 * is the info's; and no shadow is cast at the line, as a second tucked card would cast (the tuck's shadow darkens the card just
 * under its upper piece by 4% or more: MapChromeColourPixelsTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapTopCardPortraitScreenTest : MapTopCardScreenTests(rotation = null) {
    @Test
    fun `the navigation info is inside the strip's card under a thin line, one card with no second tuck`() {
        setScreen()
        val strip = bounds(TOP_CARD_STRIP_TAG)
        val info = bounds(NAVIGATION_HUD_TAG)
        val line = bounds(NAVIGATION_INFO_LINE_TAG)
        val coordinates = bounds(COMPASS_STRIP_COORDINATES_TAG)
        assertTrue("the info $info is inside the strip's card $strip", info.left >= strip.left - 0.5.dp && info.right <= strip.right + 0.5.dp && info.top >= strip.top)
        assertEquals("the card's bottom is the info's", strip.bottom.value, info.bottom.value, 0.5f)
        assertTrue("the line $line is under the strip's line (grid reference $coordinates)", line.top >= coordinates.bottom - 0.5.dp)
        assertEquals("the line is 1 dp", 1f, (line.bottom - line.top).value, 0.3f)
        assertTrue("the info starts at the line $line: $info", abs((info.top - line.top).value) <= 0.5f)

        val image = composeRule.onRoot().captureToImage().toPixelMap()
        fun px(x: Dp, y: Dp): Color = with(composeRule.density) { image[x.toPx().toInt(), y.toPx().toInt()] }
        val lum = { c: Color -> 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue }
        // Just below the line, and further down, at the same x near the card's left edge (clear of text).
        val x = strip.left + 3.dp
        val justBelow = px(x, line.bottom + 1.dp)
        val further = px(x, line.bottom + 10.dp)
        println("MEASURED one card: line $line, just below ${lum(justBelow)}, further ${lum(further)}")
        assertTrue("no shadow cast at the line, as a second tuck would: ${lum(justBelow)} against ${lum(further)}", lum(justBelow) >= lum(further) * 0.98f)
    }
}

/** Landscape, both rotations: the card hangs under the strip, beside the pill (RECORD -946, -950, -951). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapTopCardLandscape90ScreenTest : MapTopCardScreenTests(rotation = Surface.ROTATION_90) {
    @Test
    fun `long-presses in the rail's rounded inner corners reach the map, and the rail keeps the rest`() = assertRailInnerCornerGapsReachMap()

    @Test
    fun `the navigation card slides up from the bottom edge when navigation starts`() = assertCardSlidesUpFromTheBottom()

    @Test
    fun `the navigation card's top corners are rounded and its bottom square on the edge`() = assertCardTopCornersRounded()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapTopCardLandscape270ScreenTest : MapTopCardScreenTests(rotation = Surface.ROTATION_270) {
    @Test
    fun `long-presses in the rail's rounded inner corners reach the map, and the rail keeps the rest`() = assertRailInnerCornerGapsReachMap()

    @Test
    fun `the navigation card slides up from the bottom edge when navigation starts`() = assertCardSlidesUpFromTheBottom()

    @Test
    fun `the navigation card's top corners are rounded and its bottom square on the edge`() = assertCardTopCornersRounded()
}

internal const val TOP_CARD_MAP_TAG = "top-card-map"
internal const val TOP_CARD_STRIP_TAG = "compass-elevation-strip"

private fun abs(f: Float) = kotlin.math.abs(f)
