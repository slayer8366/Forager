package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.AvailabilityEntry
import com.zynergylabs.forager.app.domain.model.AvailabilityForecast
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Motion Part 1, item 3 (dispatch 2026-09-28-652; scout L1; the owner, RECORD -651: "Yes, round them all", with touch areas
 * unchanged). The species card's press is now drawn in its rounded shape, and its tap must still cover the whole card, corners
 * included: real touches 2 dp in from each of the card's four corners, where the rounded highlight no longer reaches, each still
 * opens the species on the map.
 *
 * This passes before the change as well, by design: it pins what the change must not move. The way it would fail is a clip put
 * before the card's tap (the obvious way to round a highlight), which would take the corners away from it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class SpeciesCardCornerTouchTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val stubMap: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }

    private val region = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

    private val listState = AvailabilityUiState(
        region = region,
        forecast = AvailabilityForecast(
            region = region,
            month = 8,
            filter = TaxonFilter.FUNGI,
            entries = listOf(
                AvailabilityEntry(
                    species = SpeciesObservationCount(
                        taxonId = 48473L,
                        scientificName = "Ganoderma applanatum",
                        commonName = "artist's bracket",
                        rank = "species",
                        observationCount = 14,
                        photoUrl = null,
                        wikipediaUrl = null,
                    ),
                    relativeLikelihood = 1.0f,
                ),
            ),
        ),
        selectedMonth = LocalDate.now().monthValue,
    )

    private fun card(): DpRect = with(composeRule.density) {
        composeRule.onNodeWithTag("species-row").fetchSemanticsNode().boundsInRoot.let { DpRect(it.left.toDp(), it.top.toDp(), it.right.toDp(), it.bottom.toDp()) }
    }

    @Test
    fun `a real touch 2 dp in from each corner of a species card still opens it on the map`() {
        composeRule.setContent { ForagerTheme { LayoutFixesScreen(uiState = listState, mapSlot = stubMap) } }
        composeRule.waitForIdle()
        val corners = listOf(
            "top left" to { c: DpRect -> c.left + 2.dp to c.top + 2.dp },
            "top right" to { c: DpRect -> c.right - 2.dp to c.top + 2.dp },
            "bottom left" to { c: DpRect -> c.left + 2.dp to c.bottom - 2.dp },
            "bottom right" to { c: DpRect -> c.right - 2.dp to c.bottom - 2.dp },
        )
        corners.forEach { (name, point) ->
            composeRule.onNodeWithText("List").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("List").assertIsSelected()
            val (x, y) = point(card())
            composeRule.touchAt(x, y)
            composeRule.waitForIdle()
            try {
                composeRule.onNodeWithText("Maps").assertIsSelected()
            } catch (e: AssertionError) {
                throw AssertionError("the touch 2 dp in from the card's $name corner (${x.value}, ${y.value}) did not open it on the map", e)
            }
        }
    }
}
