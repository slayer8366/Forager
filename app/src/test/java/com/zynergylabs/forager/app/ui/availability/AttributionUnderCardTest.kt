package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.ATTRIBUTION_BUTTON_DP
import com.zynergylabs.forager.app.ui.map.ATTRIBUTION_CLEAR_GAP_DP
import com.zynergylabs.forager.app.ui.map.attributionEndInsetClearOf
import com.zynergylabs.forager.app.ui.map.attributionHiddenUnder
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * D28 with D30 (dispatch 2026-10-30-08, RECORD -1979, -1980). The owner: "Hide while a card covers it (Recommended)", then "Never
 * rises, hides under cards (Recommended)", whose option read "One rule everywhere: the 'i' stays in its bottom-right corner and simply
 * hides while any card covers that corner, in portrait and landscape, and comes back when it clears. This replaces 'i moves above the
 * card' in portrait too." Seen on the S22: b4-nav0 (the "i" under the navigation card's fill) and b4-6e (the route card expanded).
 *
 * The "i" is MapLibre's own view, and a real MapView cannot be built under Robolectric, so the map is a stub. What this asserts, on
 * the real Maps tab: the cards' measured bounds the map is handed (`renderMode.attributionHideUnder`), the "i"'s corner never moving
 * for a card, and the decision the map applies with them (`attributionHiddenUnder`, the function `MapAttributionInsets` calls), against
 * the "i"'s rectangle worked out here from the same margins and the cards' bounds read from their own nodes. MapLibre's own margins
 * are assumed as the S22 run record measured them (AttributionClearOfLTest). Whether MapLibre then hides the button and the logo is
 * device-only.
 */
abstract class AttributionUnderCardTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    internal val rig = RouteCardBarRig(composeRule)

    private fun px(value: androidx.compose.ui.unit.Dp): Int = with(composeRule.density) { value.roundToPx() }

    /** MapLibre's own margins: 4 dp on every side, as the S22 shows them (RECORD -2187; was a 52 dp right margin, AttributionClearOfLTest). */
    private fun defaultsPx(): IntArray = intArrayOf(px(4.dp), px(4.dp), px(4.dp), px(4.dp))

    private fun DpRect.inMapPx(): Rect {
        val slot = rig.bounds("map-slot")
        return with(composeRule.density) { Rect((left - slot.left).toPx(), (top - slot.top).toPx(), (right - slot.left).toPx(), (bottom - slot.top).toPx()) }
    }

    /** The "i"'s bottom inset, as the map is handed it. */
    protected fun iBottomInset(): Float {
        val mode = rig.map.renderMode ?: throw AssertionError("the map slot was given a render mode")
        return (mode.attributionBottomInset ?: mode.bottomInset).value
    }

    /** The "i"'s rectangle in the map's px, and whether the map hides it, both from what the map is handed. */
    private fun iAndDecision(): Pair<Rect, Boolean> {
        val mode = rig.map.renderMode ?: throw AssertionError("the map slot was given a render mode")
        val slot = rig.bounds("map-slot")
        val width = px(slot.right - slot.left)
        val height = px(slot.bottom - slot.top)
        val defaults = defaultsPx()
        val bottom = px(mode.attributionBottomInset ?: mode.bottomInset)
        val button = px(ATTRIBUTION_BUTTON_DP.dp)
        val end = attributionEndInsetClearOf(mode.attributionKeepClear, width, height, defaults, bottom, px(mode.attributionEndInset), false, button, px(ATTRIBUTION_CLEAR_GAP_DP.dp))
        val right = (width - defaults[2] - end).toFloat()
        val bottomEdge = (height - defaults[3] - bottom).toFloat()
        val rect = Rect(right - button, bottomEdge - button, right, bottomEdge)
        return rect to attributionHiddenUnder(mode.attributionHideUnder, width, height, defaults, bottom, end, false, button)
    }

    /** The cards over the map, read from their own nodes: the route card, and the navigation card where one is drawn under its own fill. */
    private fun cardsOnScreen(navigationCard: Boolean): List<Pair<String, Rect>> = buildList {
        if (rig.count(ROUTE_CARD_TAG) > 0) add("route card" to rig.bounds(ROUTE_CARD_TAG).inMapPx())
        if (navigationCard && rig.count(NAVIGATION_HUD_TAG) > 0) add("navigation card" to rig.bounds(NAVIGATION_HUD_TAG).inMapPx())
    }

    private fun near(a: Rect, b: Rect) = listOf(a.left - b.left, a.top - b.top, a.right - b.right, a.bottom - b.bottom).all { kotlin.math.abs(it) <= 1.5f }

    /**
     * The map is handed each card's own bounds, and hides the "i" exactly where one of them covers it. Returns whether it is hidden.
     * [navigationCard]: the navigation card is the landscape one, a card of its own over the map (in portrait the navigation info is in
     * the top strip, which D30 leaves out).
     */
    protected fun assertDecision(where: String, navigationCard: Boolean): Boolean {
        val handed = rig.map.renderMode!!.attributionHideUnder
        val cards = cardsOnScreen(navigationCard)
        cards.forEach { (name, rect) ->
            assertTrue("$where: the map is handed the $name's measured bounds $rect (handed $handed)", handed.any { near(it, rect) })
        }
        assertEquals("$where: the map is handed only the cards on screen ${cards.map { it.first }} (handed $handed)", cards.size, handed.size)
        val (i, hidden) = iAndDecision()
        val covering = cards.filter { it.second.overlaps(i) }.map { it.first }
        println("D28 $where: i $i, cards ${cards.map { "${it.first} ${it.second}" }}, covered by $covering, hidden $hidden")
        assertEquals("$where: the \"i\" at $i is hidden exactly while a card covers it (covered by $covering)", covering.isNotEmpty(), hidden)
        return hidden
    }
}

/** The S22 in landscape (823 x 384 dp at 3.75 px per dp), where b4-nav0 and b4-6e were taken. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class AttributionUnderCardLandscapeTest : AttributionUnderCardTests() {

    @Test
    fun `the i hides while the navigation card covers its corner, and comes back when navigation ends`() {
        rig.setScreen()
        val before = iBottomInset()
        assertFalse("no card: the \"i\" shows", assertDecision("no card", navigationCard = true))
        rig.navigatingNow.value = true
        rig.settle()
        assertTrue("navigating: the navigation card covers the \"i\"'s corner, as on the S22 (b4-nav0)", assertDecision("navigating", navigationCard = true))
        assertEquals("the \"i\"'s corner does not move for the navigation card", before, iBottomInset(), 0.5f)
        rig.navigatingNow.value = false
        rig.settle()
        assertFalse("navigation ended: the corner is clear, the \"i\" shows again", assertDecision("navigation ended", navigationCard = true))
    }

    @Test
    fun `navigating with the route card at 1, 3 and 6 stops, peek and expanded, the i stays in its corner and hides under the cards`() {
        rig.setScreen(navigating = true)
        val before = iBottomInset()
        listOf(1, 3, 6).forEach { n ->
            listOf(false, true).forEach { expanded ->
                val where = "navigating, $n stops, ${if (expanded) "expanded" else "peek"}"
                rig.plotTo(n)
                rig.setExpanded(expanded)
                assertEquals("$where: the \"i\" never rises for the card", before, iBottomInset(), 0.5f)
                assertTrue("$where: hidden, as on the S22 (b4-6e)", assertDecision(where, navigationCard = true))
            }
        }
    }

    @Test
    fun `not navigating, the route card in either corner hides the i only where it covers it, and the i comes back when the card closes`() {
        rig.setScreen()
        val before = iBottomInset()
        var hiddenSeen = false
        listOf(false, true).forEach { rightward ->
            rig.dragBarAcross(rightward)
            listOf(1, 3).forEach { n ->
                listOf(false, true).forEach { expanded ->
                    val where = "the L ${if (rightward) "right" else "left"}, $n stops, ${if (expanded) "expanded" else "peek"}"
                    rig.plotTo(n)
                    rig.setExpanded(expanded)
                    assertEquals("$where: the \"i\" never rises for the card", before, iBottomInset(), 0.5f)
                    hiddenSeen = assertDecision(where, navigationCard = false) || hiddenSeen
                }
            }
            rig.closeCard()
            assertFalse("the L ${if (rightward) "right" else "left"}, card closed: the \"i\" shows again", assertDecision("card closed", navigationCard = false))
        }
        assertTrue("precondition: the card covered the \"i\"'s corner in at least one case, so the rule was exercised", hiddenSeen)
    }
}

/** The S22 in portrait. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AttributionUnderCardPortraitTest : AttributionUnderCardTests() {

    @Test
    fun `with the bar on the left the i stays in its corner while the card shows, hidden exactly where the card covers it, and in place when it closes`() {
        rig.setScreen()
        rig.dragBarAcross(rightward = false)
        val before = iBottomInset()
        listOf(1, 3, 6).forEach { n ->
            listOf(false, true).forEach { expanded ->
                val where = "portrait, the bar left, $n stops, ${if (expanded) "expanded" else "peek"}"
                rig.plotTo(n)
                rig.setExpanded(expanded)
                assertEquals("$where: the \"i\" never rises for the card (D30 replaces Amendment 9's rise)", before, iBottomInset(), 0.5f)
                // Not asserted to hide: here the card rests LEGEND_ATTRIBUTION_CLEARANCE (32 dp) above the bottom inset, over the "i"'s
                // 4 + 24 dp, so it clears it (CI run 41586729551). On the S22 in fullscreen the system bar's inset puts the "i" higher
                // than the card's bottom, which is where Amendment 9 saw it over Save; Robolectric reports no insets, so that case is
                // device-only, and the decision itself is AttributionClearanceTest's.
                assertDecision(where, navigationCard = false)
            }
        }
        rig.closeCard()
        assertFalse("card closed: the \"i\" shows again", assertDecision("portrait, card closed", navigationCard = false))
        assertEquals("card closed: the \"i\" is where it was", before, iBottomInset(), 0.5f)
    }

    @Test
    fun `with the bar on the right the card is away from the corner and the i shows`() {
        rig.setScreen()
        val before = iBottomInset()
        rig.plotTo(3)
        rig.setExpanded(true)
        assertEquals("the \"i\" keeps its inset", before, iBottomInset(), 0.5f)
        assertFalse("the card is at the far side; the \"i\" shows", assertDecision("portrait, the bar right, 3 stops expanded", navigationCard = false))
    }
}
