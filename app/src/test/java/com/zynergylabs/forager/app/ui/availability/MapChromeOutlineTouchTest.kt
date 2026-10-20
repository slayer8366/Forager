package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.MapSlot
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
 * Dispatch 2026-10-13-02 (RECORD -966, -967): with the outline drawn, long-presses at screen coordinates in the rounded corner
 * gaps of every outlined piece still reach the map. The outline is drawn only (`mapChromeEdges` measures nothing and takes no
 * touch), so this is the regression guard the dispatch asked for, not evidence the outline is there: the pixel tests
 * (`MapChromeOutlinePixelsTest`) are that. Several points per corner (a finger is not a point); a point within reach of a
 * control (its bounds, or the 6 dp a 36 dp control's touch target extends) is skipped and counted, since a finger there goes to
 * that control on a device too. Every reached point is counted and the count asserted, so an empty sample cannot pass.
 */
abstract class MapChromeOutlineTouchTests(private val rotation: Int?) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 104.9, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.5135, lng = -122.67, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private var longPresses = 0

    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
        Box(modifier.testTag(MAP_TAG).pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }) })
    }

    private fun setScreen(navigating: Boolean) {
        if (rotation != null) Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                recording = AvailabilityScreenRecording(
                    isRecording = navigating,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = navigating,
                    navigationTarget = if (navigating) start else null,
                    returnRoute = ReturnRoute.Ahead(LatLng(45.5135, -122.67), 245.0),
                ),
                compassProvider = FixedCompass(134f),
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
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /**
     * Each control's reach (its bounds and 6 dp round them), with what names it, so a skipped point says which control it was by.
     * Controls inside [clippedBy] are left out: that piece clips its content to its rounded shape, touch area included (the pill
     * is a `Surface`; the bottom bar clips, RECORD -937), so its own controls cannot take a touch in its corner gaps. CI run
     * 38511944362 at 36e79dd9 counted them and skipped every bottom-bar corner point by its List and Tools items (0 of 6), where
     * `RoundedMapCardTouchTest` long-presses the same corners on main, unskipped, and they reach the map.
     */
    private fun controlsReach(clippedBy: String?): List<Pair<String, DpRect>> = composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
        .filterNot { it.config.getOrNull(SemanticsProperties.TestTag) == MAP_TAG }
        .filterNot { n -> clippedBy != null && generateSequence(n.parent) { it.parent }.any { it.config.getOrNull(SemanticsProperties.TestTag) == clippedBy } }
        // Off the window entirely: the closed drawer's header ("Close search options") sits left of the screen, and run
        // 38512455317 skipped the pill's top-left points by its 6 dp margin. A control off screen takes no touch.
        .filter { n -> n.boundsInRoot.right > 0f && n.boundsInRoot.bottom > 0f && n.boundsInRoot.left < windowPx().first && n.boundsInRoot.top < windowPx().second }
        .map { n ->
            val name = n.config.getOrNull(SemanticsProperties.TestTag)
                ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                ?: n.config.getOrNull(SemanticsProperties.Text)?.joinToString()
                ?: "node ${n.id}"
            with(composeRule.density) {
                name to DpRect(n.boundsInRoot.left.toDp() - 6.dp, n.boundsInRoot.top.toDp() - 6.dp, n.boundsInRoot.right.toDp() + 6.dp, n.boundsInRoot.bottom.toDp() + 6.dp)
            }
        }

    private fun windowPx(): Pair<Float, Float> = composeRule.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().size.let { it.width.toFloat() to it.height.toFloat() }

    private fun longPressAt(x: Dp, y: Dp) {
        val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput {
            down(p)
            advanceEventTime(700)
            up()
        }
        composeRule.waitForIdle()
    }

    /** Points in a rounded corner's gap: (2, 2) and (4, 1) and (1, 4) dp in from the corner, toward the piece. */
    private fun gap(cornerX: Dp, cornerY: Dp, inX: Int, inY: Int): List<Pair<Dp, Dp>> =
        listOf(2 to 2, 4 to 1, 1 to 4).map { (dx, dy) -> cornerX + (dx * inX).dp to cornerY + (dy * inY).dp }

    private fun assertGapsReachMap(what: String, points: List<Pair<Dp, Dp>>, atLeast: Int, clippedBy: String? = null) {
        val reach = controlsReach(clippedBy)
        var reached = 0
        val skipped = mutableListOf<String>()
        for ((x, y) in points) {
            val by = reach.firstOrNull { (_, it) -> x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }
            if (by != null) {
                skipped += "(${x.value}, ${y.value}) by ${by.first}"
                continue
            }
            val before = longPresses
            longPressAt(x, y)
            assertEquals("$what: a long-press at (${x.value}, ${y.value}) in a rounded corner's gap reached the map", before + 1, longPresses)
            reached++
        }
        println("MEASURED rotation $rotation, $what: reached $reached of ${points.size}, skipped beside a control $skipped")
        assertTrue("$what: at least $atLeast corner points reached the map, not $reached", reached >= atLeast)
    }

    protected fun portraitCorners() {
        setScreen(navigating = false)
        val pill = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds(STRIP_TAG)
        val bar = bounds(COMPACT_BOTTOM_NAV_TAG)
        // CI runs 38507319758 and 38511944362 skipped 5 of these 6 points by the pill's own controls (Close search options,
        // active-search-summary), which the pill clips to its shape: now left out of the reach (clippedBy).
        assertGapsReachMap("the pill's top corners", gap(pill.left, pill.top, 1, 1) + gap(pill.right, pill.top, -1, 1), atLeast = 6, clippedBy = SEARCH_ENTRY_BAR_TAG)
        assertGapsReachMap("the strip's bottom corners", gap(strip.left, strip.bottom, 1, -1) + gap(strip.right, strip.bottom, -1, -1), atLeast = 3)
        assertGapsReachMap("the bottom bar's top corners", gap(bar.left, bar.top, 1, 1) + gap(bar.right, bar.top, -1, 1), atLeast = 6, clippedBy = COMPACT_BOTTOM_NAV_TAG)
    }

    protected fun portraitNavigatingCardCorners() {
        setScreen(navigating = true)
        val strip = bounds(STRIP_TAG)
        assertGapsReachMap("the strip's grown card's bottom corners", gap(strip.left, strip.bottom, 1, -1) + gap(strip.right, strip.bottom, -1, -1), atLeast = 3)
    }

    protected fun landscapeCorners() {
        setScreen(navigating = false)
        val pill = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds(STRIP_TAG)
        val rail = bounds(COMPACT_NAVIGATION_RAIL_TAG)
        assertGapsReachMap("the pill's outer corners", gap(pill.left, pill.top, 1, 1) + gap(pill.left, pill.bottom, 1, -1), atLeast = 4)
        // The strip's rail-side corners sit inside its three-dot button's reach, so those points go to the button and are skipped.
        assertGapsReachMap(
            "the strip's and the rail's corners",
            gap(strip.right, strip.bottom, -1, -1) + gap(rail.left, rail.top, 1, 1) + gap(rail.left, rail.bottom, 1, -1),
            atLeast = 3,
        )
    }

    protected fun landscapeCardCorners() {
        setScreen(navigating = true)
        val card = bounds(NAVIGATION_HUD_TAG)
        assertGapsReachMap("the navigation card's top corners", gap(card.left, card.top, 1, 1) + gap(card.right, card.top, -1, 1), atLeast = 3)
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
        const val MAP_TAG = "map-chrome-outline-touch-map"
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapChromeOutlinePortraitTouchTest : MapChromeOutlineTouchTests(rotation = null) {
    @Test fun `long-presses in the pill's, the strip's and the bottom bar's rounded corner gaps reach the map`() = portraitCorners()

    @Test fun `navigating, long-presses in the strip's grown card's rounded corner gaps reach the map`() = portraitNavigatingCardCorners()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapChromeOutlineLandscapeTouchTest : MapChromeOutlineTouchTests(rotation = Surface.ROTATION_90) {
    @Test fun `long-presses in the pill's, the strip's and the rail's rounded corner gaps reach the map`() = landscapeCorners()

    @Test fun `navigating, long-presses in the navigation card's rounded top corner gaps reach the map`() = landscapeCardCorners()
}
