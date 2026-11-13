package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-11-01, Amendment U, UB2: in short landscape the bar is only as wide as its floor
 * ([rememberLandscapeBarFloorPx]), which is measured from the resting line. With the shortest resting line there is ("May · 1 km":
 * the shortest month, the smallest radius, no species) and the search failed, at font 1.0, the bar must still read "Search
 * failed." whole. Through the real screen and the real ViewModel; real text measurement (native graphics).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapsTabSearchBarShortLineTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

    /** The bar's line after a failed search with the shortest resting line in [unit]: [want] is that resting line. */
    private fun shortestLineFails(unit: DistanceUnit, want: String) {
        rig.setScreen()
        rig.viewModel.onDistanceUnitSelected(unit)
        rig.viewModel.onMonthSelected(5)
        rig.viewModel.onRadiusChanged(1)
        rig.settle()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        val state = rig.viewModel.uiState.value
        assertEquals("positive control: the shortest resting line", want, activeSearchSummary(state, state.distanceUnit))
        assertTrue("positive control: the font is at scale 1", composeRule.activity.resources.configuration.fontScale == 1f)
        assertTrue("positive control: the search failed", mapsSearchFailed(state))

        val bar = composeRule.onNodeWithTag(SEARCH_ENTRY_BAR_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // VA4: the measured widths in every message, so a failure says by how much.
        val field = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(field)
        val input = field.single().layoutInput
        val measurer = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
        val whole = measurer.measure(SEARCH_BAR_FAILED_TEXT, input.style, maxLines = 1, softWrap = false).size.width
        val widths = "\"$want\": bar ${bar.width} px, field's text room ${input.constraints.maxWidth} px, \"$SEARCH_BAR_FAILED_TEXT\" $whole px"
        val shown = listOf(SEARCH_BAR_FAILED_TEXT, SEARCH_BAR_FAILED_SHORT_TEXT)
            .filter { composeRule.onAllNodesWithText(it, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("UB2: the bar's line in short landscape at font 1 ($widths)", listOf(SEARCH_BAR_FAILED_TEXT), shown)
        val node = composeRule.onNode(hasText(SEARCH_BAR_FAILED_TEXT), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertTrue("UB2: \"$SEARCH_BAR_FAILED_TEXT\" is cut short: drawn ${node.boundsInRoot.width} px ($widths)", node.boundsInRoot.width >= whole - 1f && layouts.single().lineCount == 1)
        val tryAgain = composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("UB2: Try again $tryAgain is not wholly in the bar $bar ($widths)", tryAgain.left >= bar.left - 1f && tryAgain.right <= bar.right + 1f)
    }

    @Test
    fun `in short landscape at font 1 with the shortest resting line in kilometres a failed search reads Search failed whole`() =
        shortestLineFails(DistanceUnit.KILOMETERS, "May · 1 km")

    /** VA4: the app's default unit is miles, where the shortest line is narrower still ("1 mi"). */
    @Test
    fun `in short landscape at font 1 with the shortest resting line in miles a failed search reads Search failed whole`() =
        shortestLineFails(DistanceUnit.MILES, "May · 1 mi")
}
