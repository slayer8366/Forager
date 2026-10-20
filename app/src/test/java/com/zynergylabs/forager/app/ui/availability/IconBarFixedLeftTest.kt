package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
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
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_TAG
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
 * RECORD -955, the owner: "There's no friendly way to keep the icon bar on the right in landscape mode. I say we keep it on the
 * left in landscape"; "1 The screen left."; "2 yes. Only have it fixed in a) landscape and b) with navigation mode on. Those two
 * conditions must be met to have it fixed to the left. Else it can be set on the right as an option for the user".
 *
 * Through the real screen, with the saved side on the right: landscape and navigating, the bar is on the screen's left and a drag
 * toward the right does not move it there; navigation ending brings it back to the right, and nothing written holds the left; in
 * landscape not navigating, and in portrait while navigating, the saved side applies.
 */
abstract class IconBarFixedLeftTests(private val rotation: Int, private val landscape: Boolean) {
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 12.5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private var returning by mutableStateOf(false)
    private val writes = mutableListOf<MapIconClusterPlacement>()
    private var rotationSeen: Int? = null

    /** The saved side, the right: in landscape stored as the port or punch-hole side, whichever is the screen's right here. */
    private val savedRight: MapIconClusterPlacement get() = MapIconClusterPlacement.DEFAULT.copy(
        portraitOnLeft = false,
        // RECORD -956: the stored port side is the screen's right at both rotations (the rail's side). Was: the port side at
        // ROTATION_90, the punch-hole side at ROTATION_270, when the port followed the rotation.
        landscapeOnPortSide = true,
    )

    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag("icon-bar-fixed-map")) }

    private fun setScreen(navigating: Boolean) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        returning = navigating
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix, mapIconClusterPlacement = savedRight),
                recording = AvailabilityScreenRecording(
                    isRecording = true,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = returning,
                    navigationTarget = start,
                    returnRoute = ReturnRoute.Ahead(LatLng(45.525, -122.68), 900.0),
                ),
                mapControls = AvailabilityScreenMapControls(
                    onMapIconClusterPlacementChanged = { writes += it },
                ),
                compassProvider = object : CompassProvider {
                    override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(45f, HeadingUncertainty.Estimated(2f), 0L))
                },
                computeTrueHeading = ComputeTrueHeadingUseCase(object : DeclinationProvider {
                    override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
                }),
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
        settle()
        assertEquals("the screen must see the rotation this case is about", rotation, rotationSeen)
    }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(3_000L)
        composeRule.waitForIdle()
    }

    private fun bar(): DpRect = composeRule.onNodeWithTag(MAP_ICON_BAR_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun onLeft(): Boolean {
        val w = composeRule.onRoot().getUnclippedBoundsInRoot()
        val b = bar()
        return (b.left + b.right) / 2 < (w.left + w.right) / 2
    }

    /** No placement written holds the bar on the left: the saved side is still the right. */
    private fun assertNothingWroteLeft() {
        val left = writes.filter { if (landscape) it.landscapeOnPortSide != savedRight.landscapeOnPortSide else it.portraitOnLeft }
        assertTrue("no write moved the saved side to the left: $writes", left.isEmpty())
    }

    @Test
    fun `navigating, the bar is on the screen's left in landscape and on the saved side in portrait, and the saved side returns after`() {
        setScreen(navigating = false)
        assertTrue("positive control: not navigating, the saved side, the right (${bar()})", !onLeft())
        composeRule.runOnIdle { returning = true }
        settle()
        if (landscape) {
            assertTrue("landscape and navigating: the screen's left (${bar()})", onLeft())
        } else {
            assertTrue("portrait while navigating: the saved side, the right (${bar()})", !onLeft())
        }
        composeRule.runOnIdle { returning = false }
        settle()
        assertTrue("navigation over: back on the saved side, the right (${bar()})", !onLeft())
        assertNothingWroteLeft()
    }

    @Test
    fun `while fixed, a drag toward the right does not move the bar there, and writes no side`() {
        if (!landscape) return
        setScreen(navigating = true)
        assertTrue("positive control: fixed on the left (${bar()})", onLeft())
        val handle = composeRule.onNodeWithTag("map-icon-bar-minimize-handle", useUnmergedTree = true).getUnclippedBoundsInRoot()
        composeRule.longPressDrag((handle.left + handle.right) / 2, (handle.top + handle.bottom) / 2, 500.dp, 0.dp)
        settle()
        assertTrue("still on the left after the drag (${bar()})", onLeft())
        composeRule.runOnIdle { returning = false }
        settle()
        assertTrue("navigation over: the saved side, the right (${bar()})", !onLeft())
        assertNothingWroteLeft()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconBarFixedLeftLandscape90Test : IconBarFixedLeftTests(Surface.ROTATION_90, landscape = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconBarFixedLeftLandscape270Test : IconBarFixedLeftTests(Surface.ROTATION_270, landscape = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconBarFixedLeftPortraitTest : IconBarFixedLeftTests(Surface.ROTATION_0, landscape = false)
