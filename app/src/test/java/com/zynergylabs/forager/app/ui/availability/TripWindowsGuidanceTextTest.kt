package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every text the Trip Windows card's guidance section ever showed, as it read on screen: RECORD -727 removed the last
 * two (the fungi and plants blocks), dispatch 2026-09-28-695 the two before them. Headings and one phrase from each
 * paragraph, so a block that came back under a new heading would still be caught by its body.
 */
private val GUIDANCE_TEXTS = listOf(
    "Rain and fungi: the general pattern",
    "one to three weeks after sustained rain",
    "is often quoted as broadly typical for temperate fleshy fungi",
    "Forager has not measured any relationship between these conditions",
    "Rain and plants: no pattern to offer",
    "no weather-based pattern for plants",
    "The rainfall and soil measurements are still shown, without an interpretation",
    "No weather guidance for this selection",
    "No species-specific data is available for",
)

private val FLY_AGARIC = TaxonSearchResult(
    taxonId = 48715,
    scientificName = "Amanita muscaria",
    commonName = "Fly Agaric",
    rank = "species",
    iconicTaxonName = "Fungi",
    photoUrl = null,
)

private val RAMPS = TaxonSearchResult(
    taxonId = 54713,
    scientificName = "Allium tricoccum",
    commonName = "Ramps",
    rank = "species",
    iconicTaxonName = "Plantae",
    photoUrl = null,
)

/** A species in a group Forager never wrote weather guidance for. */
private val LADYBIRD = TaxonSearchResult(
    taxonId = 48484,
    scientificName = "Harmonia axyridis",
    commonName = "Asian Lady Beetle",
    rank = "species",
    iconicTaxonName = "Insecta",
    photoUrl = null,
)

/**
 * RECORD -727, the owner: "remove the block of text here labeled Rain and Fungi: the general pattern", then "Remove
 * both" for the plants block. The Trip Windows card shows no guidance text for fungi, plants or an unknown group,
 * through the real [AvailabilityScreen] over the real [AvailabilityViewModel] (`mapLayersViewModel`,
 * `MapLayersTestScreen`), read in the Tools drawer's Trip Planner section a user opens to see it.
 *
 * The selection is made by calling [AvailabilityViewModel.onTaxonSearchResultSelected], the callback a suggestion
 * row's tap calls (the shared fixture's species search returns no rows to tap); the starting selection is the Fungi
 * category. Before -727, fungi and plants each showed a block here (this class, as it stood, asserted the fungi one).
 *
 * The trip-window weather fetch fails in this fixture, so the card shows its error line. That line is what each test
 * holds as proof the card itself rendered, so an absent guidance block is the card's choice and not a card that never
 * composed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class TripWindowsGuidanceTextTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private fun setScreenWithARegionSearched(): AvailabilityViewModel {
        val viewModel = mapLayersViewModel()
        composeRule.setContent {
            MapLayersTestScreen(viewModel = viewModel, mapSlot = LayersRecordingMapSlot().slot, store = AbsentForecastCellStore)
        }
        composeRule.waitForIdle()
        // The same reference coordinates AvailabilityScreenConditionsMonthTest types; not anyone's real place.
        composeRule.runOnIdle {
            viewModel.onManualLatChanged("45.326")
            viewModel.onManualLngChanged("-122.634")
            viewModel.searchManualCoordinates()
        }
        composeRule.waitForIdle()
        return viewModel
    }

    private fun pickByName(viewModel: AvailabilityViewModel, result: TaxonSearchResult) {
        composeRule.runOnIdle { viewModel.onTaxonSearchResultSelected(result) }
        composeRule.waitForIdle()
    }

    private fun openTripPlanner() {
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.onNodeWithText("Trip Planner").performClick()
        composeRule.waitForIdle()
    }

    /** The card rendered: its title and its (failed) measurements line. */
    private fun assertTripWindowsCardShown() {
        composeRule.onNodeWithText("Trip Windows").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Couldn't load trip-window weather.").performScrollTo().assertIsDisplayed()
    }

    private fun assertNoGuidanceText() {
        GUIDANCE_TEXTS.forEach { text -> composeRule.onAllNodesWithText(text, substring = true).assertCountEquals(0) }
    }

    @Test
    fun `the fungi category shows no guidance text`() {
        val viewModel = setScreenWithARegionSearched()
        assertEquals("the precondition: the default selection", TaxonFilter.FUNGI, viewModel.uiState.value.taxonFilter)
        assertEquals("the precondition: its group is fungi", "Fungi", viewModel.uiState.value.foragingSelection.iconicTaxonName)

        openTripPlanner()

        assertTripWindowsCardShown()
        assertNoGuidanceText()
    }

    @Test
    fun `a fungus species shows no guidance text`() {
        val viewModel = setScreenWithARegionSearched()
        pickByName(viewModel, FLY_AGARIC)
        assertEquals("the precondition: its group is fungi", "Fungi", viewModel.uiState.value.foragingSelection.iconicTaxonName)

        openTripPlanner()

        assertTripWindowsCardShown()
        assertNoGuidanceText()
    }

    @Test
    fun `a plant species shows no guidance text`() {
        val viewModel = setScreenWithARegionSearched()
        pickByName(viewModel, RAMPS)
        assertEquals("the precondition: its group is plants", "Plantae", viewModel.uiState.value.foragingSelection.iconicTaxonName)

        openTripPlanner()

        assertTripWindowsCardShown()
        assertNoGuidanceText()
    }

    @Test
    fun `a species in an unknown group shows no guidance text`() {
        val viewModel = setScreenWithARegionSearched()
        pickByName(viewModel, LADYBIRD)
        assertEquals(
            "the precondition: the species is the selection",
            TaxonFilter.SpecificTaxon(taxonId = 48484, label = "Asian Lady Beetle"),
            viewModel.uiState.value.taxonFilter,
        )

        openTripPlanner()

        assertTripWindowsCardShown()
        assertNoGuidanceText()
    }
}
