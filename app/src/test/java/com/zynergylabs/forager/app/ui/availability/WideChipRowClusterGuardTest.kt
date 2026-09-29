package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.model.AvailabilityEntry
import com.zynergylabs.forager.app.domain.model.AvailabilityForecast
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.map.JOURNAL_ENTRIES_CHIP_TAG
import java.time.LocalDate
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

/**
 * The tablet's chip row never overlaps the icon cluster (Part 2 follow-ups F1 item 6; the replacement guard
 * the planner asked for when J6c removed three J6b chip-versus-Layers tests, RECORD -177). One body of
 * tests at the three tablet sizes, in native graphics, through the real [AvailabilityScreen] with a taxon
 * chip ("Showing: ...", from a real tap on View on Map) and the Journal chip ("1 journal entry on map")
 * both up. Rectangles are compared as unclipped bounds in the root, at rest and with the cluster dragged to
 * its top limit by a real long-press drag on its handle. Robolectric draws no real map and reports zero
 * system-bar insets: nothing here speaks for the device's real chrome.
 *
 * **Not a test that has been seen to fail.** The dispatch says fix the placement only if this fails; if it
 * passes at base it is a guard, and a guard that passes before and after a change is flagged as possibly
 * not covering what it claims (CLAUDE.md, Testing). The report says which happened.
 */
abstract class WideChipRowClusterGuardTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val fakeCompass = object : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(null)
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
                onNightModeMapsChanged = {},
                onThemeModeChanged = {},
                cartographyUiState = ONE_SHOWN_ENTRY,
                compassProvider = fakeCompass,
                mapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag(MAP_SLOT)) },
            )
        }
        composeRule.waitForIdle()
    }



    private fun boundsTag(tag: String) = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun overlaps(a: DpRect, b: DpRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    /** Brings the taxon chip up the way a user does: List (a tab on the narrow tablet, beside the map on the wide ones), then View on Map. */
    private fun showTaxonChip() {
        if (composeRule.onAllNodesWithText("View on Map").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithText("List").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText("View on Map").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAXON_CHIP).assertExists()
        composeRule.onNodeWithText("1 journal entry on map").assertExists()
    }

    private fun assertChipsClearOfCluster(when_: String) {
        val cluster = boundsTag(MAP_ICON_CLUSTER_TAG)
        val taxon = boundsTag(TAXON_CHIP)
        val journal = boundsTag(JOURNAL_ENTRIES_CHIP_TAG)
        assertTrue("$when_: the taxon chip $taxon does not overlap the cluster $cluster", !overlaps(taxon, cluster))
        assertTrue("$when_: the Journal chip $journal does not overlap the cluster $cluster", !overlaps(journal, cluster))
    }

    @Test
    fun `with a taxon chip and the Journal chip up, neither overlaps the cluster at rest`() {
        setScreen()
        showTaxonChip()

        assertChipsClearOfCluster("at rest")
    }

    @Test
    fun `with both chips up, neither overlaps the cluster dragged to its top limit`() {
        setScreen()
        showTaxonChip()
        val handle = composeRule.onNodeWithContentDescription("Hide map controls").getUnclippedBoundsInRoot()
        val startX = (handle.left.value + handle.right.value) / 2f
        val startY = (handle.top.value + handle.bottom.value) / 2f
        composeRule.onRoot().performTouchInput {
            down(Offset(startX * density, startY * density))
            advanceEventTime(700)
            moveBy(Offset(0f, -2000f * density), 600)
            up()
        }
        composeRule.waitForIdle()

        assertChipsClearOfCluster("dragged to the top limit")
        assertEquals("the cluster is still on the map", 1, composeRule.onAllNodesWithTag(MAP_ICON_CLUSTER_TAG).fetchSemanticsNodes().size)
    }

    private companion object {
        const val MAP_SLOT = "map-slot"
        const val TAXON_CHIP = "map-taxon-filter-chip"
        val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)
        val SPECIES = SpeciesObservationCount(taxonId = 47348, scientificName = "Cantharellus formosus", commonName = "Pacific Golden Chanterelle", rank = "species", observationCount = 42, photoUrl = null, wikipediaUrl = null)
        val MATCHING_SIGHTING = Sighting(observationId = 1, taxonId = 47348, scientificName = "Cantharellus formosus", commonName = "Pacific Golden Chanterelle", lat = 45.33, lng = -122.64, observedOn = LocalDate.of(2026, 8, 1), photoUrl = null)
        val SEARCHED_STATE = AvailabilityUiState(
            region = REGION,
            forecast = AvailabilityForecast(region = REGION, month = 8, filter = TaxonFilter.FUNGI, entries = listOf(AvailabilityEntry(species = SPECIES, relativeLikelihood = 1f))),
            sightings = listOf(MATCHING_SIGHTING),
        )
        val ONE_SHOWN_ENTRY = CartographyUiState(
            entries = listOf(CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 9, 12), updatedAtEpochMillis = 1_000L).copy(isDraft = false, text = "A walk.", shownOnMap = true)),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w824dp-h1318dp-mdpi")
class WideChipRowClusterGuardPortraitTest : WideChipRowClusterGuardTests()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w1280dp-h900dp-mdpi")
class WideChipRowClusterGuard1280Test : WideChipRowClusterGuardTests()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-mdpi")
class WideChipRowClusterGuardLandscapeTest : WideChipRowClusterGuardTests()
