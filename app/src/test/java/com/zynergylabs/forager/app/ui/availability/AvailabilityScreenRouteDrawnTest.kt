package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
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
import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.MapSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

/**
 * Dispatch 2026-09-28-497 (plan task T7), through the real [AvailabilityScreen]: while returning the
 * map is handed the way back; once within max(2 x accuracy, 15 m) of the start the map is handed the
 * start's place for its ring and no longer the start's pin, and the HUD reads "Arrived"; the Return
 * control reads "Stop navigating" while navigating. The map is a stand-in that records what it was
 * handed and counts long-presses: what MapLibre draws is device-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvailabilityScreenRouteDrawnTest {

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
    private val far = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 3.79f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val atStart = LocationFix.Update(lat = start.lat + 10.0 / 111_195.08, lng = start.lng, altitude = 50.0, accuracyMeters = 3.79f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val line = RouteLine(
        atReturn = listOf(LatLng(45.52, -122.68), LatLng(45.525, -122.68), LatLng(45.53, -122.68)),
        ahead = listOf(LatLng(45.525, -122.68), LatLng(45.53, -122.68)),
        aheadIsCurrent = true,
    )

    private var fix by mutableStateOf(far)
    private var returning by mutableStateOf(true)
    private var content: MapOverlayContent? = null
    private var longPresses = 0

    private val map: MapSlot = { _, overlay, _, _, _, _, _, _, modifier ->
        content = overlay
        Box(modifier.testTag(MAP_TAG).pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }) })
    }

    private fun setScreen() {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                recording = AvailabilityScreenRecording(
                    isRecording = true,
                ),
                records = AvailabilityScreenRecords(
                    waypoints = listOf(start),
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = returning,
                    navigationTarget = start,
                    returnRoute = ReturnRoute.Ahead(LatLng(45.525, -122.68), 1_000.0),
                    routeLine = line,
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

    private fun returnControlLabel(): String? =
        // The merged node: the button as accessibility presents it, its icon's description included.
        composeRule.onNodeWithTag(RETURN_CONTROL_TAG).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

    @Test
    fun `returning, the map is handed the way back, the start's pin, and no ring`() {
        setScreen()
        val route = content!!.route!!
        assertEquals(line, route.line)
        assertNull(route.arrivedAt)
        assertTrue("the start's pin is drawn", content!!.waypoints.any { it.id == start.id })
    }

    @Test
    fun `within 15 m of the start the map is handed the ring's place instead of the start's pin, and the HUD reads Arrived`() {
        fix = atStart
        setScreen()
        val route = content!!.route!!
        assertEquals(LatLng(start.lat, start.lng), route.arrivedAt)
        assertTrue("the start's pin is left out", content!!.waypoints.none { it.id == start.id })
        assertEquals(ARRIVED_TEXT, composeRule.onNodeWithTag(NAVIGATION_HUD_DISTANCE_TAG).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text })
    }

    @Test
    fun `the Return control reads Stop navigating while navigating, and its own label otherwise, and ending removes the line`() {
        setScreen()
        assertEquals("Stop navigating", returnControlLabel())
        composeRule.runOnIdle { returning = false }
        composeRule.waitForIdle()
        assertTrue("its own label again: ${returnControlLabel()}", returnControlLabel() != "Stop navigating")
        assertNull("not navigating: no way back drawn", content!!.route)
    }

    /** CLAUDE.md, the Surface pitfall: with the way back handed to the map, real long-presses below the HUD reach the map. */
    @Test
    fun `with the way back drawn, real long-presses on the map below the HUD reach the map`() {
        setScreen()
        val hud = composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).getUnclippedBoundsInRoot()
        val controls = composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().map {
            with(composeRule.density) { DpRect(it.boundsInRoot.left.toDp(), it.boundsInRoot.top.toDp(), it.boundsInRoot.right.toDp(), it.boundsInRoot.bottom.toDp()) }
        }
        var sampled = 0
        for (row in listOf(6.dp, 20.dp)) {
            val y = hud.bottom + row
            for (i in 0 until SAMPLES) {
                val x = hud.left + 8.dp + (hud.right - hud.left - 16.dp) * (i.toFloat() / (SAMPLES - 1))
                if (controls.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) continue
                val before = longPresses
                val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
                composeRule.onRoot().performTouchInput { longClick(p) }
                composeRule.waitForIdle()
                assertEquals("a long-press at ($x, $y) must reach the map", before + 1, longPresses)
                sampled++
            }
        }
        assertTrue("at least $MIN_SAMPLED points sampled, not $sampled", sampled >= MIN_SAMPLED)
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val MAP_TAG = "route-drawn-map"
        const val RETURN_CONTROL_TAG = "control-pill-return-to-vehicle"
        const val SAMPLES = 8
        const val MIN_SAMPLED = 8
    }
}
