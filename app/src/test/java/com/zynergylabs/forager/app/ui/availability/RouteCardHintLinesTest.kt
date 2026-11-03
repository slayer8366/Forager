package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_COUNT_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-10-30-08, C15 (RECORD -1946). The owner, from two S22 screenshots: "The cards are two sizes. Have the two sentences be
 * on separate lines and reduce it to the other card size so both can be uniform without switching". The real Maps tab at the S22's
 * size, under native graphics so the lines are the real ones: one stop's hint is laid out on exactly two lines, "1 Waypoint selected,"
 * then "tap another to route", each whole.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardHintLinesTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    private fun hintLines(): List<String> {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(ROUTE_CARD_COUNT_TAG, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.single()
        val text = layout.layoutInput.text.text
        return (0 until layout.lineCount).map { text.substring(layout.getLineStart(it), layout.getLineEnd(it, visibleEnd = true)) }
    }

    @Test
    fun `one stop's hint is two lines, the first sentence then the second`() {
        rig.setScreen()
        rig.plotTo(1)
        assertEquals("the hint's lines", listOf("1 Waypoint selected,", "tap another to route"), hintLines())
        rig.setExpanded(true)
        assertEquals("the hint's lines, expanded", listOf("1 Waypoint selected,", "tap another to route"), hintLines())
    }
}
