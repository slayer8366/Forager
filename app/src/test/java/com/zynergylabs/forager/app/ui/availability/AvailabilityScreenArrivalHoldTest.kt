package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.MapSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RECORD -844, -845: once a return ends on arrival, the navigation display holds "Arrived", with the
 * start ringed, then closes to the plain map. The real [AvailabilityScreen], given what `MainActivity`
 * gives it from the recording ViewModel: `isReturning` false (the return has ended) and `isArrivalHeld`
 * while the ViewModel's hold runs (its timing is `TrackRecordingSwipeAwayTest`'s, on a virtual clock).
 * `MainActivity`'s line joining the two is not exercised by a test; no test in this repository
 * composes `MainActivity`.
 *
 * The fix is a drive-off: 1.1 km from the start. Read live, it would be a distance; held, it is
 * "Arrived". The map is a stand-in that records what it was handed; what MapLibre draws is device-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvailabilityScreenArrivalHoldTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val t = 1_700_000_000_000L
    private val start = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private val drivingAway = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 3.79f, timestampEpochMillis = t, provider = FixProvider.GPS)

    private var held by mutableStateOf(true)
    private var content: MapOverlayContent? = null

    private val map: MapSlot = { _, overlay, _, _, _, _, _, _, modifier ->
        content = overlay
        Box(modifier.testTag(MAP_TAG))
    }

    private fun setScreen() {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = drivingAway),
                recording = AvailabilityScreenRecording(
                    isRecording = true,
                ),
                records = AvailabilityScreenRecords(
                    waypoints = listOf(start),
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = false,
                    isArrivalHeld = held,
                    navigationTarget = start,
                    // What MainActivity passes once the route tick has stopped and cleared its route.
                    returnRoute = ReturnRoute.Pending,
                    routeLine = null,
                ),
                compassProvider = FixedCompass(0f),
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
    }

    private fun textOfTag(tag: String): String =
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private fun returnControlLabel(): String? =
        composeRule.onNodeWithTag(RETURN_CONTROL_TAG).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    @Test
    fun `while held, the display reads Arrived and the start is ringed, though the fix is driving away`() {
        setScreen()

        assertEquals(ARRIVED_TEXT, textOfTag(NAVIGATION_HUD_DISTANCE_TAG))
        assertEquals("", textOfTag(NAVIGATION_HUD_STATUS_TAG))
        assertEquals("the ring at the start", LatLng(start.lat, start.lng), content!!.route!!.arrivedAt)
        assertEquals("the control is the display's exit while it shows", "Stop navigating", returnControlLabel())
    }

    @Test
    fun `when the hold ends, the display closes to the plain map`() {
        setScreen()
        composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).assertExists()

        composeRule.runOnIdle { held = false }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).assertDoesNotExist()
        assertNull("not navigating: nothing drawn for a return", content!!.route)
        assertEquals("the Return control's own label again", true, returnControlLabel() != "Stop navigating")
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val MAP_TAG = "arrival-hold-map"
        const val RETURN_CONTROL_TAG = "control-pill-return-to-vehicle"
    }
}
