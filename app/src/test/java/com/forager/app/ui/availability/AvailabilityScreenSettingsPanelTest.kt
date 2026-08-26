package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.model.ForagingAreas
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.ui.map.Basemap
import com.forager.app.ui.map.MapSlot
import java.time.LocalDate
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

/**
 * Three Phase 2 pieces of [AvailabilityScreen], measured headlessly:
 *
 * 1. The quick-fire mode toggle over the map's own top-right corner, and that tapping it actually
 *    changes which [Basemap] the map slot receives — not just that an icon is somewhere on screen.
 * 2. Settings' "Choose Maps Service" section.
 * 3. The "Offline Maps" submenu: reachable regardless of the selected [com.forager.app.ui.map.MapService]
 *    (offline downloads always target USGS internally, so nothing about reaching the submenu depends
 *    on the live service selection — see `com.forager.app.domain.OfflineMapRepository`'s doc comment),
 *    its navigation (entry row in → back arrow out), and picking a region by panning its
 *    [com.forager.app.ui.map.CentrePinLocationPicker] map and confirming with OK, instead of typing
 *    coordinates.
 *
 * The map is stubbed, same reasoning as [AvailabilityScreenLayoutTest]: composing the real one
 * starts osmdroid. [CapturingMapSlot] backs *both* the main Map tab's map and the Offline Maps
 * submenu's picker map — both are mounted through the same [AvailabilityScreen.mapSlot] parameter,
 * and both can be composed at once (the main tab stays mounted behind the drawer while a submenu is
 * open), so the stub tells them apart by content: the picker always gets empty sightings/areas/
 * planned-trips, the main tab's ([SEARCHED_STATE]) doesn't.
 *
 * Not covered here, and not verifiable headlessly: the icon's and picker map's actual pixel
 * position/legibility over real map tiles — see README's "Not yet verified".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenSettingsPanelTest {

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

    private var capturedBasemap: Basemap? = null
    private var capturedOfflinePickerBasemap: Basemap? = null

    /** See this class's doc comment for why the two map instances are told apart by content. */
    private val CapturingMapSlot: MapSlot = { _, content, renderMode, _, _, _, onCameraIdle, modifier ->
        if (content.sightings.isEmpty() && content.areas.isEmpty() && content.plannedTrips.isEmpty()) {
            capturedOfflinePickerBasemap = renderMode.basemap
            Column(modifier.testTag(OFFLINE_PICKER_MAP_TAG)) {
                Button(onClick = { onCameraIdle(PICKED_LOCATION) }) { Text("Simulate pan to test location") }
            }
        } else {
            capturedBasemap = renderMode.basemap
            Box(modifier.testTag(MAP_SLOT_TAG))
        }
    }

    private fun setScreen() {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = SEARCHED_STATE,
                onUseCurrentLocation = {},
                onManualLatChanged = {},
                onManualLngChanged = {},
                onSearchManualCoordinates = {},
                onRadiusChanged = {},
                onMonthSelected = {},
                onMapTabSelected = {},
                onSeasonalTabSelected = {},
                onToggleForagingAreas = {},
                onCategorySelected = {},
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
                onReopenTaxonSuggestions = {},
                onPlaceTripPin = { _, _, _ -> },
                onDeletePlannedTrip = {},
                onRecentSearchSelected = {},
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                mapSlot = CapturingMapSlot,
            )
        }
    }

    /**
     * Unlike [setScreen], wires the offline-map callbacks to real local state so panning and
     * confirming the picker map (see [CapturingMapSlot]) actually round-trips into
     * [AvailabilityUiState] the same way [AvailabilityViewModel]'s real
     * `onOfflineMapLatChanged`/`onOfflineMapLngChanged` do — needed for the "picking a region sets
     * it" test, which otherwise has nothing to observe.
     */
    private fun setScreenWithOfflineMapsState(initial: AvailabilityUiState = SEARCHED_STATE) {
        composeRule.setContent {
            var current by remember { mutableStateOf(initial) }
            AvailabilityScreen(
                uiState = current,
                onUseCurrentLocation = {},
                onManualLatChanged = {},
                onManualLngChanged = {},
                onSearchManualCoordinates = {},
                onRadiusChanged = {},
                onMonthSelected = {},
                onMapTabSelected = {},
                onSeasonalTabSelected = {},
                onToggleForagingAreas = {},
                onCategorySelected = {},
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
                onReopenTaxonSuggestions = {},
                onPlaceTripPin = { _, _, _ -> },
                onDeletePlannedTrip = {},
                onRecentSearchSelected = {},
                onOfflineMapLatChanged = { text -> current = current.copy(offlineMapLatText = text) },
                onOfflineMapLngChanged = { text -> current = current.copy(offlineMapLngText = text) },
                onOfflineMapRadiusChanged = { radius -> current = current.copy(offlineMapRadiusKm = radius) },
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                mapSlot = CapturingMapSlot,
            )
        }
    }

    private fun openSettings() {
        composeRule.onNodeWithText("Settings").performClick()
    }

    private fun openOfflineMaps() {
        openSettings()
        composeRule.onNodeWithText("Offline Maps").performClick()
    }

    @Test
    fun `the map opens on the OpenTopoMap basemap, OpenStreetMap topo mode`() {
        setScreen()

        // Basemap.DEFAULT resolves through MapService.DEFAULT (OpenStreetMap) — see MapService's
        // doc comment for why this changed from PR #13's USGS Topo default.
        assertEquals(Basemap.OPEN_TOPO_MAP, capturedBasemap)
    }

    @Test
    fun `the quick-fire icon renders over the map's own top-right corner`() {
        setScreen()

        val iconBounds = composeRule
            .onNodeWithContentDescription("Showing topo mode. Switch to regular mode.")
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val mapBounds = composeRule.onNodeWithTag(MAP_SLOT_TAG).getUnclippedBoundsInRoot()

        // Over the map, not beside it: the icon's horizontal center must fall within the map's own
        // width, and it sits in the map's top half and right half — top-right, not merely "on top of".
        val iconCenterX = iconBounds.left + iconBounds.width / 2
        assertTrue(
            "expected the icon's horizontal center ($iconCenterX) to fall inside the map's bounds " +
                "(${mapBounds.left}..${mapBounds.right})",
            iconCenterX in mapBounds.left..mapBounds.right,
        )
        assertTrue(iconBounds.top < mapBounds.top + mapBounds.height / 2)
        assertTrue(iconCenterX > mapBounds.left + mapBounds.width / 2)
    }

    @Test
    fun `tapping the quick-fire icon toggles the basemap the map slot receives`() {
        setScreen()
        assertEquals(Basemap.OPEN_TOPO_MAP, capturedBasemap)

        composeRule.onNodeWithContentDescription("Showing topo mode. Switch to regular mode.").performClick()
        composeRule.waitForIdle()

        assertEquals(Basemap.OSM_STANDARD, capturedBasemap)
        composeRule.onNodeWithContentDescription("Showing regular mode. Switch to topo mode.").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Showing regular mode. Switch to topo mode.").performClick()
        composeRule.waitForIdle()

        assertNotEquals(Basemap.OSM_STANDARD, capturedBasemap)
        assertEquals(Basemap.OPEN_TOPO_MAP, capturedBasemap)
    }

    @Test
    fun `switching MapService in Settings preserves the current topo or regular mode`() {
        setScreen()
        // Switch to regular mode under OpenStreetMap first.
        composeRule.onNodeWithContentDescription("Showing topo mode. Switch to regular mode.").performClick()
        composeRule.waitForIdle()
        assertEquals(Basemap.OSM_STANDARD, capturedBasemap)

        openSettings()
        composeRule.onNodeWithText("USGS").performClick()
        composeRule.waitForIdle()

        // Settings is its own bottom-nav tab now (not an overlay on the map), so the Maps tab's map
        // slot is unmounted while here and won't observe the new service until it's recomposed —
        // switch back to it, same as a real user would, before reading what it was handed.
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.waitForIdle()

        // "if a map has two modes, toggle the two": switching service must not reset the mode, so
        // OpenStreetMap's regular mode carries over to USGS's regular mode (Imagery), not Topo.
        assertEquals(Basemap.USGS_IMAGERY_TOPO, capturedBasemap)
    }

    @Test
    fun `Settings shows Choose Maps Service with both options, and an Offline Maps entry row`() {
        setScreen()
        openSettings()

        composeRule.onNodeWithText("Choose Maps Service").assertIsDisplayed()
        composeRule.onNodeWithText("OpenStreetMap").assertIsDisplayed()
        composeRule.onNodeWithText("USGS").assertIsDisplayed()
        composeRule.onNodeWithText("Offline Maps").assertIsDisplayed()
    }

    /**
     * The behavior this project's owner explicitly asked for: offline downloads always use USGS
     * internally, so reaching the submenu never depends on which service is selected for live
     * browsing. OpenStreetMap is the default (untouched) service for this test.
     */
    @Test
    fun `Offline Maps is reachable under the default OpenStreetMap service, with no gating message`() {
        setScreen()
        openOfflineMaps()

        composeRule.onNodeWithTag(OFFLINE_PICKER_MAP_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithText(
            "Offline downloads are only available for the USGS map service",
            substring = true,
        ).assertCountEquals(0)
    }

    @Test
    fun `the Offline Maps submenu always resolves the picker map to USGS Topo`() {
        setScreen()
        openOfflineMaps()
        // A synchronizing node query (as every other test in this file that reads capture state
        // right after a click already has, via assertIsDisplayed/assertIsNotEnabled) is what
        // actually forces recomposition to settle before a plain var read; performClick() alone
        // doesn't guarantee it here.
        composeRule.onNodeWithTag(OFFLINE_PICKER_MAP_TAG).assertIsDisplayed()

        assertEquals(Basemap.USGS_TOPO, capturedOfflinePickerBasemap)
    }

    @Test
    fun `the Offline Maps entry row navigates into the submenu, and its back arrow returns to Settings`() {
        setScreen()
        openSettings()

        composeRule.onNodeWithText("Offline Maps").performClick()
        composeRule.onNodeWithTag(OFFLINE_PICKER_MAP_TAG).assertIsDisplayed()
        // Settings' own content — the map-service picker — is no longer on screen once inside the submenu.
        composeRule.onAllNodesWithText("Choose Maps Service").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Back to Settings").performClick()

        composeRule.onNodeWithText("Choose Maps Service").assertIsDisplayed()
        composeRule.onAllNodesWithTag(OFFLINE_PICKER_MAP_TAG).assertCountEquals(0)
    }

    @Test
    fun `Download Maps is disabled with no region picked, and no regions or delete buttons show with nothing downloaded`() {
        setScreen()
        openOfflineMaps()

        composeRule.onNodeWithText("Download Maps").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        // Delete is per-region now (OfflineRegionRow), not a standalone always-present button — with
        // nothing downloaded there is no row to show one on.
        composeRule.onNodeWithText("No regions downloaded yet.").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Delete").assertCountEquals(0)
    }

    @Test
    fun `panning the picker map and confirming with OK sets the region and enables Download Maps`() {
        setScreenWithOfflineMapsState()
        openOfflineMaps()
        composeRule.onNodeWithText("Download Maps").assertIsNotEnabled()

        composeRule.onNodeWithText("Simulate pan to test location").performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            "Download region: ${"%.4f".format(PICKED_LOCATION.lat)}, ${"%.4f".format(PICKED_LOCATION.lng)}",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Download Maps").assertIsEnabled()
    }

    @Test
    fun `the Offline Maps submenu states it covers the continental US with vector map data`() {
        setScreen()
        openOfflineMaps()

        composeRule.onAllNodesWithText("continental United States", substring = true).assertCountEquals(1)
    }

    /**
     * The offline-readiness text a downloaded region shows — the z0-14 local-archive vs. z15
     * live-fetched-at-download-time distinction `docs/plans/forager-navigator-plan.md`'s Phase 1c
     * item asks this submenu to surface, per the project owner's own call to extend this existing
     * panel rather than build a separate live-position readiness check (deferred).
     *
     * This text moved with Workstream B from the picker's own transient download status (main's
     * single-region `OfflineMapStatusContent`) to each region's own row in `OfflineRegionsSection` —
     * see that composable's doc comment for why: [OfflineMapStatus.Succeeded] is a bare marker with
     * no region data left to attach the text to, once a region can no longer replace whatever was
     * downloaded before it.
     */
    @Test
    fun `a downloaded region states it is ready to zoom 15, with the z14-archive vs z15-live distinction`() {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = SEARCHED_STATE.copy(
                    offlineRegions = listOf(
                        OfflineRegionSummary(
                            id = 1L,
                            name = "Chanterelle Ridge",
                            region = REGION,
                            minZoom = OfflineMapRepository.MIN_ZOOM,
                            maxZoom = OfflineMapRepository.MAX_ZOOM,
                            tileCount = 4200,
                            sizeBytes = 18_500_000L,
                            createdAtEpochMillis = 0L,
                        ),
                    ),
                ),
                onUseCurrentLocation = {},
                onManualLatChanged = {},
                onManualLngChanged = {},
                onSearchManualCoordinates = {},
                onRadiusChanged = {},
                onMonthSelected = {},
                onMapTabSelected = {},
                onSeasonalTabSelected = {},
                onToggleForagingAreas = {},
                onCategorySelected = {},
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
                onReopenTaxonSuggestions = {},
                onPlaceTripPin = { _, _, _ -> },
                onDeletePlannedTrip = {},
                onRecentSearchSelected = {},
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                mapSlot = CapturingMapSlot,
            )
        }
        openOfflineMaps()

        composeRule.onAllNodesWithText("Ready to zoom 15", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("zoom 10–14 from the archive", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("zoom 15 detail fetched live from Protomaps", substring = true).assertCountEquals(1)
    }
}

private const val MAP_SLOT_TAG = "settings-panel-test-map-slot"
private const val OFFLINE_PICKER_MAP_TAG = "settings-panel-test-offline-picker-map-slot"

private val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)
private val PICKED_LOCATION = LatLng(lat = 44.5, lng = -121.5)

private fun sighting(index: Int) = Sighting(
    observationId = index.toLong(),
    taxonId = 100L + index,
    scientificName = "Species $index",
    commonName = "Species $index",
    lat = REGION.lat + index * 0.001,
    lng = REGION.lng + index * 0.001,
    observedOn = LocalDate.of(2025, 8, 1),
    photoUrl = null,
)

private val SEARCHED_STATE = AvailabilityUiState(
    region = REGION,
    sightings = List(4) { sighting(it) },
    foragingAreas = ForagingAreas.None(ForagingAreas.Reason.TOO_FEW_OBSERVATIONS, observationsConsidered = 4),
    showForagingAreas = false,
)
