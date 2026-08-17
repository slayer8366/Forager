package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.ForagingAreas
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
 * Two Phase 2 pieces of [AvailabilityScreen], measured headlessly:
 *
 * 1. The quick-fire mode toggle over the map's own top-right corner, and that tapping it actually
 *    changes which [Basemap] the map slot receives — not just that an icon is somewhere on screen.
 * 2. Settings' "Choose Maps Service" and "Offline Maps" sections, and that "Offline Maps" is
 *    reachable only once [com.forager.app.ui.map.MapService.USGS] is selected — the structural half
 *    of decision #7 (OpenStreetMap's and OpenTopoMap's tile-usage policies forbid bulk downloading).
 *
 * The map itself is stubbed, same reasoning as [AvailabilityScreenLayoutTest]: composing the real
 * one starts osmdroid. Not covered here, and not verifiable headlessly: the icon's actual pixel
 * position over real map tiles, and anything about legibility — see README's "Not yet verified".
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

    private val CapturingMapSlot: MapSlot = { _, _, _, _, basemap, _, modifier ->
        capturedBasemap = basemap
        Box(modifier.testTag(MAP_SLOT_TAG))
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
                onToggleForagingAreas = {},
                onCategorySelected = {},
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
                onReopenTaxonSuggestions = {},
                onPlaceTripPin = { _, _, _ -> },
                onDeletePlannedTrip = {},
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineMaps = {},
                mapSlot = CapturingMapSlot,
            )
        }
    }

    private fun openSettings() {
        composeRule.onNodeWithContentDescription("Advanced search options").performClick()
        composeRule.onNodeWithText("Settings").performClick()
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

        // "if a map has two modes, toggle the two": switching service must not reset the mode, so
        // OpenStreetMap's regular mode carries over to USGS's regular mode (Imagery), not Topo.
        assertEquals(Basemap.USGS_IMAGERY_TOPO, capturedBasemap)
    }

    @Test
    fun `Settings shows Choose Maps Service with both options`() {
        setScreen()
        openSettings()

        composeRule.onNodeWithText("Choose Maps Service").assertIsDisplayed()
        composeRule.onNodeWithText("OpenStreetMap").assertIsDisplayed()
        composeRule.onNodeWithText("USGS").assertIsDisplayed()
    }

    @Test
    fun `Offline Maps is not reachable under the default OpenStreetMap service`() {
        setScreen()
        openSettings()

        composeRule.onNodeWithText("Offline Maps").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Offline downloads are only available for the USGS map service — OpenStreetMap's tile " +
                "providers don't allow bulk downloading.",
        ).assertIsDisplayed()
        // No region picker or download button reachable at all — structurally absent, not merely
        // disabled-looking, per decision #7's own "structurally incapable" requirement.
        composeRule.onAllNodesWithText("Download Maps").assertCountEquals(0)
    }

    @Test
    fun `Offline Maps becomes reachable after choosing the USGS service`() {
        setScreen()
        openSettings()

        composeRule.onNodeWithText("USGS").performClick()

        composeRule.onNodeWithText("Download Maps").assertIsDisplayed()
        // Disabled until a region is entered — the lat/lng fields both start blank.
        composeRule.onNodeWithText("Download Maps").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete Offline Maps").assertIsNotEnabled()
    }

    @Test
    fun `Offline Maps states which USGS style will be downloaded, matching the map's current mode`() {
        setScreen()
        openSettings()
        composeRule.onNodeWithText("USGS").performClick()

        composeRule.onNodeWithText(
            "Downloads USGS Topo tiles for the region below — whichever mode the map's own " +
                "quick-fire icon is currently set to.",
        ).assertIsDisplayed()
    }
}

private const val MAP_SLOT_TAG = "settings-panel-test-map-slot"

private val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

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
