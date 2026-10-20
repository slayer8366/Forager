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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
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
import com.zynergylabs.forager.app.ui.map.MapRenderMode
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
 * RECORD -956, the owner: "There should be nothing fixed between the two landscape modes. One should look exactly line the other";
 * "Screen left can be either charging port or front camera end, depending on which degree rotation it's in"; told the side bar
 * followed the charging port, "That was never my call and that should be fixed".
 *
 * The same screen at ROTATION_90 and ROTATION_270, and each main control's bounds compared for equality: the rail, the search bar,
 * the compass strip, the navigation card, the icon bar while navigating, the drawer's sheet, and what the map is asked for the "i"
 * and its credit strip. Robolectric reports no cut-out and no navigation bar, so this is the layout itself; where the real system
 * bar and the camera fall at each rotation is a device item.
 *
 * And the L's stored side (the planner's answer 2): a stored port side reads as the screen's right, and a stored punch-hole side as
 * its left, at both rotations, as the user saw them at ROTATION_90.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeRotationsAlikeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 12.5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private var returning by mutableStateOf(false)
    private var placementNow by mutableStateOf(MapIconClusterPlacement.DEFAULT)
    private var opening by mutableStateOf(0)
    private var contentSet = false
    private var rotationSeen: Int? = null
    private var lastRenderMode: MapRenderMode? = null

    private val map: MapSlot = { _, _, renderMode, _, _, _, _, _, modifier ->
        lastRenderMode = renderMode
        Box(modifier.testTag("rotations-alike-map"))
    }

    /**
     * The screen at [rotation]. A compose rule takes `setContent` once, so the first call sets the content and each later call opens
     * the screen afresh in it (a new key, so everything remembered, the rotation included, is read again as on a real opening).
     */
    private fun setScreen(rotation: Int, navigating: Boolean, placement: MapIconClusterPlacement = MapIconClusterPlacement.DEFAULT) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        returning = navigating
        placementNow = placement
        if (contentSet) {
            opening++
            settle(rotation)
            return
        }
        contentSet = true
        composeRule.setContent { androidx.compose.runtime.key(opening) {
            rotationSeen = LocalView.current.display?.rotation
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix, mapIconClusterPlacement = placementNow),
                recording = AvailabilityScreenRecording(
                    isRecording = true,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = returning,
                    navigationTarget = start,
                    returnRoute = ReturnRoute.Ahead(LatLng(45.525, -122.68), 900.0),
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
        } }
        settle(rotation)
    }

    private fun settle(rotation: Int) {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(3_000L)
        composeRule.waitForIdle()
        assertEquals("the screen must see the rotation this case is about", rotation, rotationSeen)
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun DpRect.same(o: DpRect) = listOf(left - o.left, top - o.top, right - o.right, bottom - o.bottom).all { kotlin.math.abs(it.value) <= 0.5f }

    /** Each main control's bounds, navigating or not, and what the map is asked for the "i", at [rotation]. */
    private fun snapshot(rotation: Int, navigating: Boolean): Map<String, Any?> {
        setScreen(rotation, navigating)
        val out = linkedMapOf<String, Any?>(
            "rail" to bounds(COMPACT_NAVIGATION_RAIL_TAG),
            "search bar" to bounds(SEARCH_ENTRY_BAR_TAG),
            "strip" to bounds("compass-elevation-strip"),
            "icon bar" to bounds(MAP_ICON_BAR_TAG),
            "i beside the credit strip" to lastRenderMode?.attributionBesideCredit,
        )
        if (navigating) out["card"] = bounds(NAVIGATION_HUD_TAG)
        return out
    }

    private fun assertAlike(navigating: Boolean) {
        val at90 = snapshot(Surface.ROTATION_90, navigating)
        val at270 = snapshot(Surface.ROTATION_270, navigating)
        println("MEASURED navigating $navigating, ROTATION_90 $at90; ROTATION_270 $at270")
        val differ = at90.keys.filter { key ->
            val a = at90[key]
            val b = at270[key]
            if (a is DpRect && b is DpRect) !a.same(b) else a != b
        }
        assertTrue("the same at both rotations; differ: ${differ.map { "$it: ${at90[it]} vs ${at270[it]}" }}", differ.isEmpty())
        val rail = at90["rail"] as DpRect
        val window = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertEquals("the rail is on the screen's right", window.right.value, rail.right.value, 0.5f)
    }

    @Test fun `not navigating, the rail, search bar, strip and icon bar are where they are at the other rotation`() = assertAlike(navigating = false)

    @Test fun `navigating, the rail, search bar, strip, card and icon bar are where they are at the other rotation`() = assertAlike(navigating = true)

    @Test
    fun `the drawer opens from the rail's side, the right, at both rotations`() {
        val sheets = listOf(Surface.ROTATION_90, Surface.ROTATION_270).map { rotation ->
            setScreen(rotation, navigating = false)
            composeRule.onNodeWithText("Tools").performClick()
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(1_000L)
            composeRule.waitForIdle()
            bounds(TOOLS_DRAWER_SHEET_TAG)
        }
        println("MEASURED drawer sheet: ROTATION_90 ${sheets[0]}, ROTATION_270 ${sheets[1]}")
        assertTrue("the same at both rotations: ${sheets[0]} vs ${sheets[1]}", sheets[0].same(sheets[1]))
        val window = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue("on the right: ${sheets[0]}", (sheets[0].left + sheets[0].right) / 2 > (window.left + window.right) / 2)
    }

    @Test
    fun `the credit strip is at the bottom left at both rotations`() {
        // The real credit caption is drawn by the real map (SightingsMap), which Robolectric cannot build; the map is asked the same
        // thing at both rotations (the "i" beside it, held in the snapshots above). Here: the bottom-left request is the same.
        val asks = listOf(Surface.ROTATION_90, Surface.ROTATION_270).map { rotation ->
            setScreen(rotation, navigating = false)
            lastRenderMode?.let { listOf(it.bottomInset, it.attributionBesideCredit, it.attributionEndInset) }
        }
        assertEquals("the map is asked the same at both rotations", asks[0], asks[1])
    }

    private fun barOnLeft(): Boolean {
        val w = composeRule.onRoot().getUnclippedBoundsInRoot()
        val b = bounds(MAP_ICON_BAR_TAG)
        return (b.left + b.right) / 2 < (w.left + w.right) / 2
    }

    @Test
    fun `a stored port side reads as the screen's right, and a stored punch-hole side as its left, at both rotations`() {
        for (rotation in listOf(Surface.ROTATION_90, Surface.ROTATION_270)) {
            setScreen(rotation, navigating = false, placement = MapIconClusterPlacement.DEFAULT.copy(landscapeOnPortSide = true))
            assertTrue("rotation $rotation: stored port side, the screen's right (${bounds(MAP_ICON_BAR_TAG)})", !barOnLeft())
            setScreen(rotation, navigating = false, placement = MapIconClusterPlacement.DEFAULT.copy(landscapeOnPortSide = false))
            assertTrue("rotation $rotation: stored punch-hole side, the screen's left (${bounds(MAP_ICON_BAR_TAG)})", barOnLeft())
        }
    }
}
