package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
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

private fun hostActivityRule() = object : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }
}

private val BEFORE_SEARCH_SIGHTING = Sighting(
    observationId = 501L,
    taxonId = 47347L,
    scientificName = "Cantharellus formosus",
    commonName = "Golden chanterelle",
    lat = 45.52,
    lng = -122.62,
    observedOn = LocalDate.of(2026, 9, 1),
    photoUrl = null,
)

/**
 * Saved planned trips reach the compact Maps tab's map whether or not a search has run (owner, 2026-09-28,
 * "Option A for the fix"; dispatch `2026-09-28-97`), and sightings still wait for one. Through the real
 * `AvailabilityScreen` and `AvailabilityViewModel`, reading the [MapOverlayContent][com.zynergylabs.forager.app.ui.map.MapOverlayContent]
 * the screen hands the map slot, and drawing the trip glyph in a stub map only when the screen handed it the trip
 * (`BubbleMapSlot`). At `a0a54f9` (and at this dispatch's base) the compact tab passed `emptyList()` while
 * `uiState.region` was null, which is what a real S22 showed: two saved trips, no flag, until a search ran.
 */
abstract class PlannedTripsBeforeSearchContract(private val glyphX: Dp, private val glyphY: Dp) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.PLANNED_TRIPS, BUBBLE_TRIP.id, glyphX, glyphY, BUBBLE_TRIP.location)))
    private val store = OneCellStore()

    @Test
    fun `saved trips reach the map and their glyph is drawn with no search run`() {
        val viewModel = mapLayersViewModel(store = store, plannedTrips = listOf(BUBBLE_TRIP))
        composeRule.setContent { MapLayersTestScreen(viewModel = viewModel, mapSlot = map.slot, store = store) }
        composeRule.waitForIdle()

        // The precondition that makes the case this one: no search has run, and the trip is loaded.
        assertNull("no search has run", viewModel.uiState.value.region)
        assertEquals("the trip is saved and loaded", listOf(BUBBLE_TRIP), viewModel.uiState.value.plannedTrips)

        assertEquals("the map slot is handed the saved trip", listOf(BUBBLE_TRIP), map.content!!.plannedTrips)
        composeRule.onNodeWithTag(glyphTag(BUBBLE_TRIP.id)).assertIsDisplayed()
    }

    @Test
    fun `sightings still wait for a search, and are handed over once one has run`() {
        val viewModel = mapLayersViewModel(store = store)
        var searched by mutableStateOf(false)
        composeRule.setContent {
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = store,
                // A state the ViewModel cannot reach: sightings held while no region is set.
                uiStateTransform = { state ->
                    state.copy(
                        region = if (searched) BUBBLE_REGION.region else null,
                        sightings = listOf(BEFORE_SEARCH_SIGHTING),
                    )
                },
            )
        }
        composeRule.waitForIdle()
        assertEquals("with no region the map is handed no sightings", emptyList<Sighting>(), map.content!!.sightings)

        composeRule.runOnIdle { searched = true }
        composeRule.waitForIdle()
        assertEquals("with a region set the same sighting is handed over (the control)", listOf(BEFORE_SEARCH_SIGHTING), map.content!!.sightings)
    }
}

/** The S22 Ultra's portrait window. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenPlannedTripsBeforeSearchPortraitTest : PlannedTripsBeforeSearchContract(60.dp, 500.dp)

/** The S22 Ultra's short landscape window (the dispatch's `w823dp-h384dp-land`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class AvailabilityScreenPlannedTripsBeforeSearchLandscapeTest : PlannedTripsBeforeSearchContract(420.dp, 200.dp)
