package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_CANCEL_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_TOTAL_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_UNDO_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-10-30-08, C21 with C23 (RECORD -1955, -1956), at large text. C21: while navigating in landscape the card rests on the
 * navigation card at its width, edge to edge. C23, the owner: "Navigation card's width, lines wrap (Recommended)", whose option read
 * "The route card still matches the navigation card edge to edge; its longer lines wrap onto two, as they already do elsewhere at large
 * text. Whole words, nothing cut." At font 2.0 the navigation card (329 dp in Robolectric) is narrower than C19's reference (409.67 dp),
 * so the card keeps the navigation card's width and its lines wrap.
 *
 * The real Maps tab, recording and navigating, under native graphics because wrapping is measured from real text. Every line on the
 * card at peek and expanded, the buttons included, is whole: no ellipsis, no overflow, and a break only between words (D12 saw Save
 * broken as "Sav" / "e" beside the navigation card).
 */
abstract class RouteCardOnNavigationCardFontTests(private val rotation: Int) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule)

    private fun layoutOf(matcher: SemanticsMatcher): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /** Whole: nothing ellipsised or overflowing, every character laid out, and every line break between words. Returns the lines. */
    private fun assertWholeWords(where: String, matcher: SemanticsMatcher): Int {
        val layout = layoutOf(matcher)
        val text = layout.layoutInput.text.text
        (0 until layout.lineCount).forEach { assertFalse("$where: line $it of \"$text\" is cut", layout.isLineEllipsized(it)) }
        assertFalse("$where: \"$text\" overflows its box", layout.hasVisualOverflow)
        assertEquals("$where: the last line of \"$text\" ends at its end", text.length, layout.getLineEnd(layout.lineCount - 1))
        (0 until layout.lineCount - 1).forEach { line ->
            val end = layout.getLineEnd(line)
            val betweenWords = text[end - 1].isWhitespace() || text.getOrNull(end)?.isWhitespace() == true
            assertTrue("$where: \"$text\" breaks inside a word after \"${text.substring(0, end)}\"", betweenWords)
        }
        println("C23 font ${composeRule.density.fontScale} rot $rotation $where: \"$text\" on ${layout.lineCount} lines")
        return layout.lineCount
    }

    private fun button(tag: String, label: String): SemanticsMatcher = hasText(label) and hasAnyAncestor(hasTestTag(tag))

    @Test
    fun `navigating - the card matches the navigation card edge to edge, and every line on it is whole words`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        rig.setScreen(navigating = true)
        val hud = rig.bounds(NAVIGATION_HUD_TAG)
        val strip = rig.bounds("compass-elevation-strip")
        val bar = rig.bounds(MAP_ICON_CLUSTER_TAG)
        println("C23 font ${composeRule.density.fontScale} rot $rotation: navigation card ${hud.left.value}..${hud.right.value} x ${hud.top.value}..${hud.bottom.value}")
        var wrapped = 0
        listOf(1, 3, 6).forEach { stops ->
            listOf(false, true).forEach { expanded ->
                val where = "navigating, $stops stops, ${if (expanded) "expanded" else "peek"}"
                rig.plotTo(stops)
                rig.setExpanded(expanded)
                val card = rig.bounds(ROUTE_CARD_TAG)
                val save = rig.bounds(ROUTE_CARD_SAVE_TAG)
                println("C23 font ${composeRule.density.fontScale} rot $rotation $where: card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, Save ..${save.bottom.value}")
                assertEquals("$where: the navigation card's left", hud.left.value, card.left.value, 0.5f)
                assertEquals("$where: the navigation card's right", hud.right.value, card.right.value, 0.5f)
                assertTrue("$where: Save's bottom ${save.bottom} shows at or above the navigation card's top ${hud.top}", save.bottom <= hud.top + 0.5.dp)
                assertTrue("$where: the top ${card.top} is below the strip's bottom ${strip.bottom}", card.top >= strip.bottom)
                if (stops == 1) {
                    wrapped += assertWholeWords("$where, the hint", hasTestTag(ROUTE_CARD_COUNT_TAG)) - 2
                } else {
                    wrapped += assertWholeWords("$where, the count", hasTestTag(ROUTE_CARD_ROUTED_COUNT_TAG)) - 1
                    assertWholeWords("$where, the total", hasTestTag(ROUTE_CARD_ROUTED_TOTAL_TAG))
                }
                assertEquals("$where: Undo on one line", 1, assertWholeWords("$where, Undo", button(ROUTE_CARD_UNDO_TAG, "Undo")))
                assertEquals("$where: Cancel on one line", 1, assertWholeWords("$where, Cancel", button(ROUTE_CARD_CANCEL_TAG, "Cancel")))
                assertEquals("$where: Save on one line", 1, assertWholeWords("$where, Save", button(ROUTE_CARD_SAVE_TAG, "Save")))
                rig.assertBarUntouched(where, bar)
            }
        }
        println("C23 font ${composeRule.density.fontScale} rot $rotation: lines wrapped beyond their usual count, all cases: $wrapped")
    }
}

/** Font 1.3 on the S22's window: the navigation card (397.7 dp) is wider than C19's reference (309.7 dp); the card takes its width. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.3f)
class RouteCardOnNavigationCardFont13Test : RouteCardOnNavigationCardFontTests(Surface.ROTATION_90)

/** Font 2.0 on the S22's window: the navigation card (329 dp) is narrower than C19's reference (409.67 dp); C23, the lines wrap. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardOnNavigationCardFont20Test : RouteCardOnNavigationCardFontTests(Surface.ROTATION_270)

/** Font 2.0 in the short landscape window, 640 x 360. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardOnNavigationCardShortFont20Test : RouteCardOnNavigationCardFontTests(Surface.ROTATION_90)
