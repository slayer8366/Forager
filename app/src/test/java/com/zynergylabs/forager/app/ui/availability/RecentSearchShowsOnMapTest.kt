package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.zynergylabs.forager.app.domain.model.AvailabilityForecast
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RECORD -723, the owner: tapping a recent search, "Dropdown closes, nothing new". Through the real screen and the
 * real ViewModel ([RealSearchScreenRig]), a real touch on a recent search's row must hand the map that search's
 * observations. RECORD -725, the owner: "For recent search, just use the same month and radius as they did before":
 * the search runs with its own saved month and radius, not the current ones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RecentSearchShowsOnMapTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

    private val regionA = Region(45.326, -122.634, 8)
    private val regionB = Region(44.0521, -123.0868, 8)

    /** Opens the dropdown, opens Recent searches, and touches the row whose coordinates read [coordinates]. */
    private fun touchRecentRow(coordinates: String) {
        rig.tapBar()
        check(rig.dropdownShown()) { "the bar's tap did not open the dropdown" }
        composeRule.onNodeWithText("Recent searches").performScrollTo()
        rig.touch(composeRule.onNodeWithText("Recent searches"))
        composeRule.onNodeWithText(coordinates, substring = true).performScrollTo()
        composeRule.waitForIdle()
        rig.touch(composeRule.onNodeWithText(coordinates, substring = true))
    }

    @Test
    fun `a real touch on an earlier recent search hands the map that search's observations`() {
        rig.setScreen()
        rig.searchCoordinates("45.326", "-122.634")
        assertEquals("positive control: search A is on the map", listOf(RealSearchScreenRig.sightingAt(regionA)), rig.mapSightings)
        rig.searchCoordinates("44.0521", "-123.0868")
        assertEquals("positive control: search B is on the map", listOf(RealSearchScreenRig.sightingAt(regionB)), rig.mapSightings)

        touchRecentRow("45.3260, -122.6340")

        assertFalse("the row's touch closed the dropdown", rig.dropdownShown())
        assertEquals("the ViewModel's region is the recent search's", regionA, rig.viewModel.uiState.value.region)
        assertEquals("the map is handed search A's observations", listOf(RealSearchScreenRig.sightingAt(regionA)), rig.mapSightings)
    }

    @Test
    fun `a real touch on the recent search already showing keeps its observations on the map`() {
        rig.setScreen()
        rig.searchCoordinates("45.326", "-122.634")
        assertEquals("positive control: search A is on the map", listOf(RealSearchScreenRig.sightingAt(regionA)), rig.mapSightings)

        touchRecentRow("45.3260, -122.6340")

        assertFalse("the row's touch closed the dropdown", rig.dropdownShown())
        assertEquals("the map is handed search A's observations", listOf(RealSearchScreenRig.sightingAt(regionA)), rig.mapSightings)
    }

    @Test
    fun `a recent search saved at another month and radius runs with its own month and radius`() {
        val savedMonth = LocalDate.now().monthValue % 12 + 1 // next month: never the current one, the default
        val savedRegion = Region(43.5, -121.25, 25) // 25 km: never the default 8
        runBlocking {
            rig.searchCache.save(AvailabilityForecast(savedRegion, savedMonth, TaxonFilter.FUNGI, emptyList()), null)
        }
        rig.setScreen()

        touchRecentRow("43.5000, -121.2500")

        val state = rig.viewModel.uiState.value
        assertEquals("the month is the search's own", savedMonth, state.selectedMonth)
        assertEquals("the radius is the search's own", 25, state.radiusKm)
        assertEquals("the ranked list ran with its own month and radius", Triple(savedRegion, savedMonth, TaxonFilter.FUNGI), rig.availabilityFetches.last())
        assertEquals("the map's sightings were fetched with its own month and radius", Triple(savedRegion, savedMonth, TaxonFilter.FUNGI), rig.sightingsFetches.last())
        assertEquals("the map is handed its observations", listOf(RealSearchScreenRig.sightingAt(savedRegion)), rig.mapSightings)
    }
}
