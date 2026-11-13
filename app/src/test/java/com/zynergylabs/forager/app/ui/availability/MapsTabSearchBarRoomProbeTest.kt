package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-11-01, Amendment R step 1 (a probe, not for merge): the room in the Maps tab's search bar for "Search
 * failed." and a "Try again" button, against today's resting line and Clear. Printed, lines starting "R ".
 */
abstract class BarRoomProbe(private val where: String) {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = RealSearchScreenRig(composeRule)

    private fun layoutOf(text: String): TextLayoutResult {
        val out = mutableListOf<TextLayoutResult>()
        composeRule.onNode(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(out)
        return out.single()
    }

    private fun b(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    @Test
    fun `room in the bar`() {
        rig.setScreen()
        val before = activeSearchSummary(rig.viewModel.uiState.value, rig.viewModel.uiState.value.distanceUnit)
        rig.searchCoordinates("45.3736", "-121.6959")
        val summary = activeSearchSummary(rig.viewModel.uiState.value, rig.viewModel.uiState.value.distanceUnit)
        val field = layoutOf(summary)
        val clear = layoutOf("Clear")
        fun widthIn(l: TextLayoutResult, s: String) =
            TextMeasurer(l.layoutInput.fontFamilyResolver, l.layoutInput.density, l.layoutInput.layoutDirection).measure(s, l.layoutInput.style, maxLines = 1, softWrap = false).size.width
        println(
            "R $where: before a search the line reads <$before>; after <$summary>. bar ${b(SEARCH_ENTRY_BAR_TAG)}, field ${b(ACTIVE_SEARCH_SUMMARY_TAG)}, Clear ${b(SEARCH_BAR_CLEAR_TAG)}; " +
                "resting line width ${widthIn(field, summary)} px, ellipsized ${field.isLineEllipsized(0)}, field style ${field.layoutInput.style.fontSize}; " +
                "\"Search failed.\" ${widthIn(field, "Search failed.")} px; \"Trying again…\" ${widthIn(field, "Trying again…")} px; " +
                "\"Search failed. Check your connection.\" ${widthIn(field, "Search failed. Check your connection.")} px; " +
                "in Clear's style: \"Try again\" ${widthIn(clear, "Try again")} px, \"Clear\" ${widthIn(clear, "Clear")} px; density ${field.layoutInput.density.density}",
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BarRoomPortrait1Test : BarRoomProbe("portrait font 1")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BarRoomPortrait2Test : BarRoomProbe("portrait font 2")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BarRoomLandscape1Test : BarRoomProbe("short landscape font 1")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BarRoomLandscape2Test : BarRoomProbe("short landscape font 2")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-land", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BarRoomTablet2Test : BarRoomProbe("landscape tablet font 2")
