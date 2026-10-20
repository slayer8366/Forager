package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-10-12-08 (RECORD -946, -950, -951). The owner, verbatim: "So when the navigation HUD slides down, it needs to
 * stay the same size. The compass strip stays exactly where it is in both modes, but the navigation HUD with the design slides
 * down"; and, with a screenshot of today's app, "Keep it exactly like this size. Exactly as we have it today". In a short
 * landscape window, at both rotations: the compass strip's bounds and the search bar's are identical navigating and not; the bar
 * meets the strip edge to edge (RECORD -934) in both; navigating, the navigation card's left and right edges are the strip's; and
 * long-presses beside the card and above it reach the map. RECORD -953 moved the card from under the strip to the bottom edge
 * ("Just flipped upside down"); before it, the card's top was the strip's bottom.
 *
 * Was (dispatch 2026-10-12-06, RECORD -936, -940): navigating, the bar's inner edge was the card's, the card beside the bar at the
 * top of the map in the strip's place, and the window's short side wide: `assertEquals(card.left, bar.right)`,
 * `assertEquals(card.top, bar.top)`, `assertEquals(shortSide, card.width)`.
 */
abstract class LandscapeCardMeetsBarTests {
    val composeRule = createComposeRule()
    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 3_000.0, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private var longPresses = 0
    private var rotationSeen: Int? = null
    private var returning by androidx.compose.runtime.mutableStateOf(false)
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
        Box(modifier.testTag("landscape-card-map").pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }) })
    }

    private fun setScreen(rotation: Int) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                recording = AvailabilityScreenRecording(
                    isRecording = true,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = returning,
                    navigationTarget = start,
                    returnRoute = ReturnRoute.Ahead(LatLng(45.525, -122.68), 900.0),
                ),
                compassProvider = FixedCompass(315f),
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
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("the screen must actually see the rotation this test is about", rotation, rotationSeen)
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private val STRIP = "compass-elevation-strip"

    private fun longPressAt(x: Dp, y: Dp) {
        val at = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { down(at); advanceEventTime(700); up() }
        composeRule.waitForIdle()
    }

    private fun inAnyControl(x: Dp, y: Dp): Boolean = composeRule.onAllNodes(androidx.compose.ui.test.hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
        .any { n -> with(composeRule.density) { x.toPx() >= n.boundsInRoot.left && x.toPx() <= n.boundsInRoot.right && y.toPx() >= n.boundsInRoot.top && y.toPx() <= n.boundsInRoot.bottom } }

    private fun DpRect.same(other: DpRect): Boolean =
        listOf(left - other.left, top - other.top, right - other.right, bottom - other.bottom).all { kotlin.math.abs(it.value) <= 0.5f }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun assertStripStaysAndCardHangs(rotation: Int) {
        setScreen(rotation)
        // RECORD -956 (the owner: "One should look exactly line the other"): the bar on the left at both rotations. Was `rotation == Surface.ROTATION_90`,
        // so ROTATION_270 asserted "the bar's left edge is the strip's right edge".
        val barOnLeft = true
        val stripBefore = bounds(STRIP)
        val barBefore = bounds(SEARCH_ENTRY_BAR_TAG)
        assertEquals("positive control: not navigating, no card", 0, composeRule.onAllNodesWithTag(NAVIGATION_HUD_TAG).fetchSemanticsNodes().size)

        composeRule.runOnIdle { returning = true }
        settle()
        val strip = bounds(STRIP)
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val card = bounds(NAVIGATION_HUD_TAG)
        println("MEASURED rotation $rotation: strip before $stripBefore, navigating $strip; bar before $barBefore, navigating $bar; card $card")
        assertTrue("the strip stays exactly where it is, the same size: $stripBefore, navigating $strip", strip.same(stripBefore))
        assertTrue("and so does the search bar: $barBefore, navigating $bar", bar.same(barBefore))
        if (barOnLeft) {
            assertEquals("the bar's right edge is the strip's left edge", strip.left.value, bar.right.value, 0.5f)
        } else {
            assertEquals("the bar's left edge is the strip's right edge", strip.right.value, bar.left.value, 0.5f)
        }
        assertEquals("the card's left edge is the strip's: card $card, strip $strip", strip.left.value, card.left.value, 0.5f)
        assertEquals("the card's right edge is the strip's: card $card, strip $strip", strip.right.value, card.right.value, 0.5f)
        // RECORD -953 ("have the card peak from the botton of the screen ... Just flipped upside down"): the card sits on the
        // window's bottom edge in the strip's column. Was (-951): `assertEquals(strip.bottom, card.top)`, hung from the strip.
        val window = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertEquals("the card sits on the bottom edge: card $card, window $window", window.bottom.value, card.bottom.value, 0.5f)
        assertTrue("and the map shows between the strip and the card: strip $strip, card $card", card.top > strip.bottom + 8.dp)

        // Beside the card, on the search bar's side, nearer the card than the cluster on the bar's outer side, and between the strip
        // and the card in the strip's column: the map's.
        val x = if (barOnLeft) card.left - 12.dp else card.right + 12.dp
        val midX = (card.left + card.right) / 2
        var sampled = 0
        for ((px, y) in listOf(x to card.top + 12.dp, x to (card.top + card.bottom) / 2, midX to (strip.bottom + card.top) / 2, midX to card.top - 4.dp)) {
            if (inAnyControl(px, y)) continue
            val before = longPresses
            longPressAt(px, y)
            assertEquals("a long-press at (${px.value}, ${y.value}) beside or above the card reached the map", before + 1, longPresses)
            sampled++
        }
        assertTrue("sampled beside and above the card: $sampled", sampled >= 2)

        composeRule.runOnIdle { returning = false }
        settle()
        assertTrue("navigation over, the strip is still where it was: ${bounds(STRIP)}", bounds(STRIP).same(stripBefore))
        assertEquals("and the card has gone", 0, composeRule.onAllNodesWithTag(NAVIGATION_HUD_TAG).fetchSemanticsNodes().size)
    }

    @Test fun `at ROTATION_90 the strip stays put and the same size navigating or not, and the card sits on the bottom edge at its exact width`() = assertStripStaysAndCardHangs(Surface.ROTATION_90)

    @Test fun `at ROTATION_270 the strip stays put and the same size navigating or not, and the card sits on the bottom edge at its exact width`() = assertStripStaysAndCardHangs(Surface.ROTATION_270)

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeCardMeetsBar780Test : LandscapeCardMeetsBarTests()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeCardMeetsBar823Test : LandscapeCardMeetsBarTests()

/** A wider landscape window (dispatch 2026-10-12-06's case for the old capped bar), kept as a third width for the same claims. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w915dp-h412dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeCardMeetsBar915Test : LandscapeCardMeetsBarTests()
