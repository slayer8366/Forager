package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
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
 * Dispatch 2026-11-11-01, Amendments R, S and T at font scale 2.0. RC4: "no word broken, \"Try again\" wholly tappable at font 2.0".
 * T, TC1 (the owner: "Shorter word when tight (Recommended)"; "Button only when nothing fits (Recommended)"): the bar's line is the
 * first of "Search failed.", "Failed." that fits whole beside "Try again", else no words; TC2 (the planner's reading): "Trying
 * again…", "Trying…", else none, while a try runs. Each window states the rung it expects ([expected], [expectedRetrying]; ""
 * for no words), so a change of rung is seen. The line shown, if any, is drawn whole (its drawn width against its measured
 * width: isLineEllipsized alone could not fail, run 43380452581). "Try again" is wholly in the bar and every real touch across it
 * runs the search. Real text measurement (native graphics).
 *
 * Was (Amendments R and S): "Search failed." in every window, which at 360 dp failed (`drawn 423.0 px of 471 px`, run 43381537358),
 * the stop the owner's TB1 answers.
 */
abstract class MapsTabSearchBarLargeFontTests(private val expected: String, private val expectedRetrying: String) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun shownLines(candidates: List<String>) = candidates.filter { composeRule.onAllNodesWithText(it, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    /** The bar's line among [candidates]: the one shown, checked whole; "" when none shows. */
    private fun assertBarLine(where: String, candidates: List<String>, want: String) {
        val shown = shownLines(candidates)
        assertEquals("$where: the bar's line", listOfNotNull(want.takeIf { it.isNotEmpty() }), shown)
        // UB4: the ladder's room comes from copied paddings (SEARCH_BAR_FIELD_SIDE_PADDING). The field's own laid-out text room is the
        // text field's constraint; the rung chosen must fit it and the next longer one must not, so a drifted constant fails here.
        val field = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(field)
        val input = field.single().layoutInput
        val room = input.constraints.maxWidth
        val box = composeRule.onNodeWithTag(SEARCH_BAR_FIELD_ROOM_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        val ladderRoom = box - 2 * with(composeRule.density) { SEARCH_BAR_FIELD_SIDE_PADDING.toPx() }
        assertEquals("$where: the ladder's room ($box px less SEARCH_BAR_FIELD_SIDE_PADDING each side) is not the field's laid-out text room", room.toFloat(), ladderRoom, 1.5f)
        val measurer = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
        fun widthOf(text: String) = measurer.measure(text, input.style, maxLines = 1, softWrap = false).size.width
        val index = if (want.isEmpty()) candidates.size else candidates.indexOf(want)
        if (want.isNotEmpty()) assertTrue("$where: \"$want\" (${widthOf(want)} px) does not fit the field's laid-out room $room px", widthOf(want) <= room)
        if (index > 0) {
            val longer = candidates[index - 1]
            assertTrue("$where: \"$longer\" (${widthOf(longer)} px) fits the field's laid-out room $room px, so the ladder chose too short", widthOf(longer) > room)
        }
        if (want.isEmpty()) return
        val node = composeRule.onNode(hasText(want), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        val whole = TextMeasurer(layout.layoutInput.fontFamilyResolver, layout.layoutInput.density, layout.layoutInput.layoutDirection)
            .measure(want, layout.layoutInput.style, maxLines = 1, softWrap = false).size.width
        val drawn = node.boundsInRoot.width
        assertTrue("$where: the bar's line \"$want\" is cut short: drawn $drawn px of $whole px", drawn >= whole - 1f && layout.lineCount == 1 && !layout.isLineEllipsized(0))
    }

    @Test
    fun `at font 2 the bar's failure line is whole and Try again is wholly in the bar and runs at every touch`() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        assertTrue("positive control: the font is at scale 2", composeRule.activity.resources.configuration.fontScale >= 2f)
        assertBarLine("failed", listOf(SEARCH_BAR_FAILED_TEXT, SEARCH_BAR_FAILED_SHORT_TEXT), expected)
        val summary = activeSearchSummary(rig.viewModel.uiState.value, rig.viewModel.uiState.value.distanceUnit)
        assertTrue("the resting line shows beside the failure", composeRule.onAllNodesWithText(summary, useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val tryAgain = bounds(SEARCH_FAILED_TRY_AGAIN_TAG)
        assertTrue("at font 2 Try again $tryAgain is not wholly in the bar $bar", tryAgain.left >= bar.left - 1f && tryAgain.right <= bar.right + 1f && tryAgain.top >= bar.top - 1f && tryAgain.bottom <= bar.bottom + 1f)
        assertTrue("at font 2 Clear shows beside Try again", composeRule.onAllNodes(hasTestTag(SEARCH_BAR_CLEAR_TAG), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        for ((fx, fy) in listOf(0.1f to 0.2f, 0.9f to 0.2f, 0.5f to 0.5f, 0.1f to 0.8f, 0.9f to 0.8f)) {
            val before = rig.sightingsFetches.size
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true), fx, fy)
            assertEquals("at font 2 a real touch at ($fx, $fy) of Try again did not run the search", before + 1, rig.sightingsFetches.size)
        }
    }

    @Test
    fun `at font 2 while a try runs the bar's line is the retrying rung, whole`() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        val gate = CompletableDeferred<Unit>()
        rig.sightingsGate = gate
        try {
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
            assertTrue("positive control: the try is running", rig.viewModel.uiState.value.isRetryingSearch)
            assertBarLine("retrying", listOf(SEARCH_RETRYING_NOTICE_TEXT, SEARCH_RETRYING_SHORT_TEXT), expectedRetrying)
        } finally {
            rig.sightingsGate = null
            gate.complete(Unit)
            rig.settle()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapsTabSearchBarLargeFontPortraitTest : MapsTabSearchBarLargeFontTests(SEARCH_BAR_FAILED_TEXT, SEARCH_RETRYING_NOTICE_TEXT)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapsTabSearchBarLargeFontPortrait360Test : MapsTabSearchBarLargeFontTests(SEARCH_BAR_FAILED_SHORT_TEXT, SEARCH_RETRYING_SHORT_TEXT)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapsTabSearchBarLargeFontShortLandscapeTest : MapsTabSearchBarLargeFontTests("", "")
