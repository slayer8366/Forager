package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.ui.map.MAP_LAYERS_SHEET_TAG
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.zynergylabs.forager.app.domain.model.RecordPoint
import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * [CartographyEntryReportScreen]'s own fullscreen behavior — fullscreen-maps dispatch, Part 1. See
 * that composable's own doc comment, "Fullscreen," for the one-call-site/`Modifier`-only-resize
 * design this exercises indirectly (through [MapSlot]'s own captured arguments, the same technique
 * [CartographyEntryReportScreenMapTest] already establishes — [SightingsMap] itself can't be
 * composed under Robolectric).
 *
 * Entering fullscreen is driven by invoking the fake [MapSlot]'s own captured `onTap` directly,
 * not a real tap on the map surface: the fake renders nothing interactive of its own (it only
 * records what it was called with, the same as every other test in this package's own map-driven
 * suite), so there is no real click target for `performClick()` to reach — the same reason those
 * other tests never drive `onLongPress`/`onTap` either. Every button *inside* the fullscreen
 * chrome ([MapIconBar]/[MapBarIconButton]) is a plain, real Compose composable once shown, and is
 * driven with ordinary `performClick()` like anything else in this codebase.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CartographyEntryReportScreenFullscreenTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val baseEntry = CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)
        .copy(
            isDraft = false,
            waypointDecisions = listOf(WaypointDecision(waypointId = "w1", name = "Trailhead", lat = 45.5, lng = -122.5, kept = true)),
        )

    private val mapDataWithWaypoint = CartographyEntryMapData(
        trackPolylines = emptyList(),
        findMarkers = emptyList(),
        waypointMarkers = listOf(RecordPoint("w1", LatLng(45.5, -122.5))),
        photoMarkers = emptyList(),
        offlineRegionCircles = emptyList(),
    )

    private fun offlineRegionSummary(): OfflineRegionSummary = OfflineRegionSummary(
        id = 1L,
        name = "Ridge Region",
        region = Region(lat = 45.5, lng = -122.5, radiusKm = 10),
        minZoom = com.zynergylabs.forager.app.domain.OfflineMapRepository.MIN_ZOOM,
        maxZoom = com.zynergylabs.forager.app.domain.OfflineMapRepository.MAX_ZOOM,
        tileCount = 100,
        sizeBytes = 1_000L,
        createdAtEpochMillis = 0L,
    )

    private var capturedRegion: Region? = null
    private var capturedRenderMode: MapRenderMode? = null
    private var capturedFocusOverride: LatLng? = null
    private var capturedOnTap: (() -> Unit)? = null

    private val capturingMapSlot: MapSlot = { region, _, renderMode, focusOverride, _, onTap, _, _, _ ->
        capturedRegion = region
        capturedRenderMode = renderMode
        capturedFocusOverride = focusOverride
        capturedOnTap = onTap
    }

    private fun setScreen(getCurrentLocation: suspend () -> LocationResult = { LocationResult.LocationUnavailable }) {
        composeRule.setContent {
            CartographyEntryReportScreen(
                entry = baseEntry,
                galleryPhotos = emptyList(),
                distanceUnit = DistanceUnit.MILES,
                mapSlot = capturingMapSlot,
                night = false,
                getMapData = { _, _ -> mapDataWithWaypoint },
                getCoveringOfflineRegion = { _, _ -> offlineRegionSummary() },
                getCurrentLocation = getCurrentLocation,
                onEdit = {},
                onDeleteEntry = {},
                onBack = {},
            )
        }
        composeRule.waitForIdle()
    }

    private fun enterFullscreen() {
        assertNotNull("the map section must have resolved before tapping it", capturedOnTap)
        capturedOnTap?.invoke()
        composeRule.waitForIdle()
    }

    @Test
    fun `tapping the preview enters fullscreen, showing the icon bar`() {
        setScreen()
        composeRule.onNodeWithContentDescription("Exit fullscreen").assertDoesNotExist()

        enterFullscreen()

        composeRule.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
    }

    @Test
    fun `the Return row exits fullscreen`() {
        setScreen()
        enterFullscreen()
        composeRule.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Exit fullscreen").performClick()

        composeRule.onNodeWithContentDescription("Exit fullscreen").assertDoesNotExist()
    }

    // Map layers L0b (planner's ruling on F3): this used to check that the entry map's basemap
    // popover opened anchored to the layers row rather than the fullscreen row (fullscreen-fixes
    // dispatch, Item 2). The popover is gone; the layers row now opens the Layers sheet, a modal
    // bottom sheet with no anchor. What is checked instead, with a real touch, is that this row
    // opens it, with its map types.
    @Test
    fun `a real touch on the layers row opens the Layers sheet with its map types`() {
        setScreen()
        enterFullscreen()

        composeRule.onNode(hasContentDescription("Layers: Street map. Choose the map type and overlays."))
            .performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MAP_LAYERS_SHEET_TAG).assertIsDisplayed()
        listOf("Street", "Topographical").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        // Satellite was removed by dispatch 2026-09-28-708; the entry map's sheet offers it nowhere.
        composeRule.onAllNodesWithText("Satellite").assertCountEquals(0)
    }

    @Test
    fun `the recenter button pans the camera once via focusOverride, without enabling tracking`() {
        setScreen(getCurrentLocation = { LocationResult.Success(lat = 46.1, lng = -121.9) })
        enterFullscreen()
        assertEquals(null, capturedFocusOverride)
        assertEquals(false, capturedRenderMode?.trackLiveLocation)

        composeRule.onNodeWithContentDescription("Center on my location").performClick()
        composeRule.waitForIdle()

        assertEquals(LatLng(46.1, -121.9), capturedFocusOverride)
        // Still never tracking — a locate-me tap must not flip this to true.
        assertEquals(false, capturedRenderMode?.trackLiveLocation)
    }

    // Reversed from "disabled while offline" to "hidden while offline" — fullscreen-fixes dispatch,
    // Item 3, a deliberate owner reversal of this exact behavior (see MapChrome.kt's own
    // mapModePickerEnabled doc comment). This test's premise ("the picker stays present, described as
    // disabled") is now false by design, matching the standard the fullscreen-maps dispatch's own
    // back-navigation test change set: reported here, not silently absorbed.
    @Test
    fun `the basemap picker is hidden while the offline toggle is on`() {
        setScreen()
        enterFullscreen()
        composeRule.onNodeWithContentDescription("Offline maps off").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Layers: Street map. Choose the map type and overlays."))
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Offline maps off").performClick()

        composeRule.onNode(hasContentDescription("Layers: Street map. Choose the map type and overlays."))
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Offline maps use one fixed style.").assertDoesNotExist()
    }

    @Test
    fun `the offline toggle agrees between the inline row and the fullscreen chrome`() {
        setScreen()
        composeRule.onNodeWithTag(OFFLINE_TOGGLE_TEST_TAG).performClick() // inline, before fullscreen

        enterFullscreen()

        composeRule.onNodeWithContentDescription("Offline maps on").assertIsDisplayed()
        assertEquals(true, capturedRenderMode?.useOfflineTiles)
    }

    @Test
    fun `entering and exiting fullscreen preserves the map's own region`() {
        setScreen()
        val regionBefore = capturedRegion

        enterFullscreen()
        assertEquals(regionBefore, capturedRegion)

        composeRule.onNodeWithContentDescription("Exit fullscreen").performClick()
        assertEquals(regionBefore, capturedRegion)
    }

    /** The confirmed state leak this dispatch fixes — see [CartographyEntryReportScreen]'s own doc comment, "Fullscreen." */
    @Test
    // Was "…defaults to Topographical…"; the default became Street (dispatch 2026-10-18-04, Amendment 2; RECORD -1297).
    fun `the entry map's own basemap defaults to the default map, Street, independent of any injected value`() {
        setScreen()

        assertEquals(MapMode.DEFAULT.basemap, capturedRenderMode?.basemap)
        assertEquals(Basemap.OSM_STANDARD, capturedRenderMode?.basemap)
    }
}
