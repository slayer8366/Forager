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
abstract class RouteCardOnNavigationCardFontTests(
    private val rotation: Int,
    /** A fixed clock (pre-main's red, run 41728736456: the class read the real one, and the sundown line made it hour-dependent). */
    private val clock: com.zynergylabs.forager.app.domain.CurrentTimeProvider = ROUTE_CARD_NOON_CLOCK,
    /** Whether the navigation card carries its sundown line at [clock] in this window: the precondition the clock sets up. */
    private val sundownLineShown: Boolean = false,
) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule, clock = clock)

    private fun layoutOf(matcher: SemanticsMatcher): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /**
     * Whole: nothing ellipsised or overflowing, every character laid out, and every line break between words. Returns the lines. A
     * text that wraps its content, a button's label or the total, is checked by its ink fitting [inside] (the button's bounds, or its
     * own) instead of the layout's overflow flag, which Robolectric reports for every such text here, a portrait card's button labels at
     * font 1.0 included (a probe, deleted after; its figures are in the C23 report).
     */
    private fun assertWholeWords(where: String, matcher: SemanticsMatcher, inside: androidx.compose.ui.unit.DpRect? = null): Int {
        val layout = layoutOf(matcher)
        val text = layout.layoutInput.text.text
        (0 until layout.lineCount).forEach { assertFalse("$where: line $it of \"$text\" is cut", layout.isLineEllipsized(it)) }
        if (inside == null) {
            assertFalse("$where: \"$text\" overflows its box", layout.hasVisualOverflow)
        } else {
            val ink = with(composeRule.density) { (0 until layout.lineCount).maxOf { layout.getLineRight(it) - layout.getLineLeft(it) }.toDp() }
            assertTrue("$where: \"$text\" ink $ink fits its button ${inside.right - inside.left}", ink <= inside.right - inside.left)
        }
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
        println("C23 font ${composeRule.density.fontScale} rot $rotation: navigation card ${hud.left.value}..${hud.right.value} x ${hud.top.value}..${hud.bottom.value}, sundown line shown ${rig.count(NAVIGATION_HUD_SUNDOWN_LINE_TAG)}")
        assertEquals("precondition: the navigation card's sundown line at this clock", if (sundownLineShown) 1 else 0, rig.count(NAVIGATION_HUD_SUNDOWN_LINE_TAG))
        var wrapped = 0
        var peekTop = 0.dp
        var peekCard: androidx.compose.ui.unit.DpRect? = null
        var peekLeftElevationOut = false
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
                // D42 (RECORD -2021): the navigation card's evening lines may step aside for this card, so its top is read now.
                val hudNow = rig.bounds(NAVIGATION_HUD_TAG)
                assertTrue("$where: Save's bottom ${save.bottom} shows at or above the navigation card's top ${hudNow.top}", save.bottom <= hudNow.top + 0.5.dp)
                // D37 (RECORD -2006, the owner: "Cover the top bar (Recommended)"): where the peek card already fills its cap (its top a
                // gap below the strip's bottom) and has rows to show (two stops or more), the expanded card rises over the strip. At noon
                // its lines and rows fill the raised cap, so its top is a gap below the screen's top. In the evening that exact top, and
                // whether the rows show, wait on the owner (the navigation card's sundown line takes the room, s10); only the rise is
                // asserted. At one stop there are no rows, so expanded is the same as peek (the planner, 2026-11-03). Elsewhere, as
                // before, the card's top is below the strip.
                if (!expanded) {
                    peekTop = card.top
                    peekCard = card
                    // D44 (RECORD -2027): a peek whose lines do not fit leaves its elevation line out, and is then shorter than its cap;
                    // its lines still fill the cap, which is what D37 reads. The rig's legs carry heights, so a routed peek has one.
                    peekLeftElevationOut = stops >= 2 && rig.count(com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_ELEVATION_TAG) == 0
                }
                val peekFillsCap = kotlin.math.abs((peekTop - (strip.bottom + 8.dp)).value) < 0.5f || peekLeftElevationOut
                if (expanded && stops == 1) {
                    rig.assertSame("$where: at one stop, with no rows, expanded is the same as peek", peekCard!!, card)
                } else if (expanded && peekFillsCap && !sundownLineShown) {
                    assertEquals("$where: D37, the peek filled its cap, so expanded the card covers the strip, to 8 dp from the top", 8f, card.top.value, 0.5f)
                } else if (expanded && peekFillsCap) {
                    assertTrue("$where: D37, the peek filled its cap, so expanded the card rises over the strip: top ${card.top}, strip's bottom ${strip.bottom}", card.top < strip.bottom && card.top >= 8.dp - 0.5.dp)
                    println("C23 PENDING (owner) $where: the evening's expanded top ${card.top.value} and the rows' room are not ruled")
                } else {
                    assertTrue("$where: the top ${card.top} is below the strip's bottom ${strip.bottom}", card.top >= strip.bottom)
                }
                if (stops == 1) {
                    wrapped += assertWholeWords("$where, the hint", hasTestTag(ROUTE_CARD_COUNT_TAG)) - 2
                } else {
                    wrapped += assertWholeWords("$where, the count", hasTestTag(ROUTE_CARD_ROUTED_COUNT_TAG)) - 1
                    assertWholeWords("$where, the total", hasTestTag(ROUTE_CARD_ROUTED_TOTAL_TAG), rig.bounds(ROUTE_CARD_ROUTED_TOTAL_TAG))
                }
                assertEquals("$where: Undo on one line", 1, assertWholeWords("$where, Undo", button(ROUTE_CARD_UNDO_TAG, "Undo"), rig.bounds(ROUTE_CARD_UNDO_TAG)))
                assertEquals("$where: Cancel on one line", 1, assertWholeWords("$where, Cancel", button(ROUTE_CARD_CANCEL_TAG, "Cancel"), rig.bounds(ROUTE_CARD_CANCEL_TAG)))
                assertEquals("$where: Save on one line", 1, assertWholeWords("$where, Save", button(ROUTE_CARD_SAVE_TAG, "Save"), rig.bounds(ROUTE_CARD_SAVE_TAG)))
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

/**
 * Pre-main's push run 41728736456 (671b669bf) and tools/s22-combined's 41734379417 failed RouteCardOnNavigationCardFont20Test at
 * "1 stops, expanded: D37 ... expected 8.0 but was 16.666666", the navigation card at 186..384 dp where the green runs had 266..384.
 * The class read the real clock: both red runs ran inside the 150 minutes before the rig's sunset (at the rig's place then, [a point near the owner's home; removed 2026-11-06], about 00:53 UTC
 * on 2026-11-04), when the navigation card carries its sundown line; the green ones ran at 18:36 to 19:36 UTC. Every class here now
 * runs at a fixed clock, noon ([ROUTE_CARD_NOON_CLOCK]) by default, and each window also at [ROUTE_CARD_EVENING_CLOCK].
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardOnNavigationCardFont20EveningTest : RouteCardOnNavigationCardFontTests(Surface.ROTATION_270, ROUTE_CARD_EVENING_CLOCK, sundownLineShown = true)

/** Font 1.3 on the S22's window, in the evening. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.3f)
class RouteCardOnNavigationCardFont13EveningTest : RouteCardOnNavigationCardFontTests(Surface.ROTATION_90, ROUTE_CARD_EVENING_CLOCK, sundownLineShown = true)

/**
 * Font 2.0 in the short landscape window, in the evening. The sundown line does not show here: the evening lines leave the navigation
 * card where they would take its top over Reset north or the walker's dot (RECORD -955), as run 41728736456 showed (its navigation
 * card 242..360 dp at 22:28 UTC).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardOnNavigationCardShortFont20EveningTest : RouteCardOnNavigationCardFontTests(Surface.ROTATION_90, ROUTE_CARD_EVENING_CLOCK, sundownLineShown = false)

/**
 * 2026-11-03 11:56:09 PST (19:56:09 UTC): 4 h 54 min before sunset at the rig's place (00:50:57 UTC by the app's own sunset), so no
 * sundown line. Moved from 12:00 PST with the rig's place (dispatch 2026-11-05-11), keeping to the second the offset it had there.
 */
internal val ROUTE_CARD_NOON_CLOCK = com.zynergylabs.forager.app.domain.CurrentTimeProvider { java.time.Instant.parse("2026-11-03T19:56:09Z").toEpochMilli() }

/**
 * Font 2.0 in the short landscape window, just after sunset: pre-main's push run 41747193319 failed ShortFont20Test at 00:57 UTC, its
 * navigation card 194..360 dp: after sunset the line is short enough to fit here, which before sunset it is not.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardOnNavigationCardShortFont20AfterSunsetTest : RouteCardOnNavigationCardFontTests(Surface.ROTATION_90, ROUTE_CARD_AFTER_SUNSET_CLOCK, sundownLineShown = true)

/**
 * 2026-11-04 00:53:09 UTC: just after sunset at the rig's place, 28 min before dark. Moved from 00:57 UTC, pre-main's run 41747193319's
 * hour, with the rig's place (dispatch 2026-11-05-11), keeping to the second the offset from sunset it had there.
 */
internal val ROUTE_CARD_AFTER_SUNSET_CLOCK = com.zynergylabs.forager.app.domain.CurrentTimeProvider { java.time.Instant.parse("2026-11-04T00:53:09Z").toEpochMilli() }

/**
 * 2026-11-03 22:41:09 UTC: 2 h 9 min before sunset at the rig's place, so the sundown line shows where it fits. Moved from 22:45 UTC with
 * the rig's place (dispatch 2026-11-05-11), keeping to the second the offset it had there.
 */
internal val ROUTE_CARD_EVENING_CLOCK = com.zynergylabs.forager.app.domain.CurrentTimeProvider { java.time.Instant.parse("2026-11-03T22:41:09Z").toEpochMilli() }
