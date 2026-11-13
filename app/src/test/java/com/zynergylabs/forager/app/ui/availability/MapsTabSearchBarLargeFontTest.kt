package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
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
 * Dispatch 2026-11-11-01, Amendment R and S at font scale 2.0, portrait (RC4: "no word broken, \"Try again\" wholly tappable at
 * font 2.0"): "Try again" in Clear's place (SB1, the owner: "\"Try again\" takes Clear's place (Recommended)"; its option text:
 * "Fits in portrait at every font"). The bar's line is whole, not cut short; "Try again" is wholly in the bar and every real
 * touch across it runs the search. Real text measurement (native graphics).
 */
abstract class MapsTabSearchBarLargeFontTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun `at font 2 the bar's failure line is whole and Try again is wholly in the bar and runs at every touch`() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNode(hasText(SEARCH_BAR_FAILED_TEXT), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        assertTrue("positive control: the font is at scale 2", composeRule.activity.resources.configuration.fontScale >= 2f)
        // The line's own drawn width against the whole line's measured width: isLineEllipsized alone read false on the placeholder
        // even where it could not fit (seen on revert run 43380452581, where Clear and Try again both showed at font 2).
        val whole = androidx.compose.ui.text.TextMeasurer(layout.layoutInput.fontFamilyResolver, layout.layoutInput.density, layout.layoutInput.layoutDirection)
            .measure(SEARCH_BAR_FAILED_TEXT, layout.layoutInput.style, maxLines = 1, softWrap = false).size.width
        val drawn = composeRule.onNode(hasText(SEARCH_BAR_FAILED_TEXT), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
        assertTrue("at font 2 the bar's line \"$SEARCH_BAR_FAILED_TEXT\" is cut short: drawn $drawn px of $whole px", drawn >= whole - 1f && layout.lineCount == 1 && !layout.isLineEllipsized(0))
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
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapsTabSearchBarLargeFontPortraitTest : MapsTabSearchBarLargeFontTests()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapsTabSearchBarLargeFontPortrait360Test : MapsTabSearchBarLargeFontTests()
