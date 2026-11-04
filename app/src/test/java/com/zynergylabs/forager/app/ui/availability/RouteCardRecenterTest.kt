package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.MAP_CREDIT_CAPTION_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
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
 * Dispatch 2026-10-30-08, Amendment 17 (RECORD -1937), the owner: "Stop it rising (Recommended)". On the S22, expanded while navigating
 * in landscape, Re-center rose with the card and landed on the search bar (docs/audits/2026-11-01-s22-card-beside-bar.md on
 * tools/s22-combined), though the card was in the other column. Re-center rises for the card only where the two share a column. The
 * real Maps tab, navigating and moved away from the navigation view.
 *
 * Amendment 17, part 2 ruled (RECORD -1943): where the two share a column, Re-center rises for the card; the portrait classes here.
 * D17 (RECORD -1958) replaces that rise in landscape while the card sits on the navigation card: Re-center goes beside the L, at the
 * bottom ([assertBesideTheL]). D27 (RECORD -1976): where its word does not fit there, its icon alone ([assertIconOnlyBesideTheL]).
 */
abstract class RouteCardRecenterTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    internal val rig = RouteCardBarRig(composeRule, credit = S22_MAP_CREDIT)

    protected fun recenter(): DpRect = rig.bounds(RETURN_TO_ROUTE_TAG)

    /**
     * D32 (RECORD -1987), everywhere else: where the icon fits beside the L at the strip's width, neither card narrows. Both keep the
     * navigation card's bounds from before the route card showed, [navigationAlone], on the left and the right.
     */
    private fun assertCardsUnnarrowed(where: String, navigationAlone: DpRect) {
        val card = rig.bounds(ROUTE_CARD_TAG)
        val navigation = rig.bounds(NAVIGATION_HUD_TAG)
        assertEquals("$where: the route card's left, not narrowed (D32)", navigationAlone.left.value, card.left.value, 0.5f)
        assertEquals("$where: the route card's right, not narrowed (D32)", navigationAlone.right.value, card.right.value, 0.5f)
        assertEquals("$where: the navigation card's left, not narrowed (D32)", navigationAlone.left.value, navigation.left.value, 0.5f)
        assertEquals("$where: the navigation card's right, not narrowed (D32)", navigationAlone.right.value, navigation.right.value, 0.5f)
    }

    private fun assertOtherColumn(where: String, pill: DpRect, card: DpRect) {
        assertTrue("$where: precondition: the card ${card.left}..${card.right} is in the other column from Re-center ${pill.left}..${pill.right}", pill.right <= card.left || card.right <= pill.left)
    }

    /** In the other column, through every state of the card, Re-center stays where it was and off the search bar. */
    protected fun assertStaysPut(window: String, stops: List<Int> = listOf(1, 3, 6)) {
        rig.setScreen(navigating = true)
        rig.leaveNavigationView()
        val before = recenter()
        val search = rig.bounds(SEARCH_ENTRY_BAR_TAG)
        stops.forEach { n ->
            listOf(false, true).forEach { expanded ->
                val where = "$window, $n stops, ${if (expanded) "expanded" else "peek"}"
                rig.plotTo(n)
                rig.setExpanded(expanded)
                val card = rig.bounds(ROUTE_CARD_TAG)
                println("A17 $where: Re-center ${before.left.value}..${before.right.value} x ${before.top.value}..${before.bottom.value}, now top ${recenter().top.value}, card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, search bottom ${search.bottom.value}")
                assertOtherColumn(where, before, card)
                rig.assertSame("$where: Re-center", before, recenter())
                rig.assertApart("$where: Re-center and the search bar", recenter(), rig.bounds(SEARCH_ENTRY_BAR_TAG))
            }
        }
    }

    /**
     * D27 (RECORD -1976, the owner: "Icon only where room is short (Recommended)", whose option read "Where the word doesn't fit beside
     * the icon bar, Re-center shows just its target icon, small enough to sit beside the bar at the bottom like everywhere else. It never
     * rises onto the search bar."): where Re-center with its word does not fit the gap between the L and the card on the navigation card,
     * it shows its icon alone, a 48 dp target whose content description is still "Re-center", beside the L at the bottom, never risen.
     * Through every stop count, peek and expanded, it stays in one place, clear of the card, the L, the record pill, the credit and the
     * search bar, and five real touches across it reach it.
     *
     * D34 (RECORD -2005) changed the no-card ends of this: with no card, Re-center's word at bottom centre would sit on the navigation
     * card, so it is already its icon beside the L, and is again when the card closes. The precondition that read Re-center's word width
     * from its no-card place (the gap beside the L narrower than the word) has no word to read there any more and is removed; the icon
     * alone at every count is still asserted.
     */
    protected fun assertIconOnlyBesideTheL(window: String, stops: List<Int> = listOf(1, 3, 6)) {
        rig.setScreen(navigating = true)
        rig.leaveNavigationView()
        val noCard = recenter()
        val navigationAlone = rig.bounds(NAVIGATION_HUD_TAG)
        val creditAtRest = rig.bounds(MAP_CREDIT_CAPTION_TAG)
        val fontScale = composeRule.density.fontScale
        composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).assert(hasText(RETURN_TO_ROUTE_TEXT).not(), { "$window font $fontScale: D34: with no card Re-center's word would cover the navigation card, so it shows no word" })
        rig.assertApart("$window font $fontScale: D34, with no card, Re-center and the navigation card", noCard, navigationAlone)
        val bottom = minOf(noCard.bottom.value, (creditAtRest.top - 8.dp).value)
        var first: DpRect? = null
        stops.forEach { n ->
            listOf(false, true).forEach { expanded ->
                val where = "$window font $fontScale, $n stops, ${if (expanded) "expanded" else "peek"}"
                rig.plotTo(n)
                rig.setExpanded(expanded)
                listOf(0.5f to 0.5f, -1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
                    val card = rig.bounds(ROUTE_CARD_TAG)
                    val pill = recenter()
                    val l = rig.bounds(MAP_ICON_CLUSTER_TAG)
                    val credit = rig.bounds(MAP_CREDIT_CAPTION_TAG)
                    val recordPill = rig.bounds(BAR_PILL)
                    println("D27 $where ($sx, $sy): Re-center ${pill.left.value}..${pill.right.value} x ${pill.top.value}..${pill.bottom.value}, card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, L ${l.left.value}..${l.right.value} x ${l.top.value}..${l.bottom.value}, record pill ${recordPill.left.value}..${recordPill.right.value} x ${recordPill.top.value}..${recordPill.bottom.value}, credit ${credit.left.value}..${credit.right.value} x ${credit.top.value}..${credit.bottom.value}")
                    assertCardsUnnarrowed(where, navigationAlone)
                    assertEquals("$where: Re-center shows its icon alone, a 48 dp target: its width", 48f, (pill.right - pill.left).value, 0.5f)
                    assertEquals("$where: Re-center shows its icon alone, a 48 dp target: its height", 48f, (pill.bottom - pill.top).value, 0.5f)
                    composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).assert(hasText(RETURN_TO_ROUTE_TEXT).not(), { "$where: Re-center shows no word" })
                    composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).assertContentDescriptionEquals(RETURN_TO_ROUTE_TEXT)
                    assertEquals("$where: Re-center stays at the bottom, never risen: its bottom (no card ${noCard.bottom}, credit top ${creditAtRest.top})", bottom, pill.bottom.value, 0.5f)
                    assertTrue("$where: Re-center's left ${pill.left} is next to the L's right ${l.right}", pill.left >= l.right && pill.left <= l.right + 16.dp)
                    rig.assertSame("$where: Re-center stays in one place", first ?: pill.also { first = it }, pill)
                    rig.assertApart("$where: Re-center and the card", pill, card)
                    rig.assertApart("$where: Re-center and the L", pill, l)
                    rig.assertApart("$where: Re-center and the record pill", pill, recordPill)
                    rig.assertApart("$where: Re-center and the credit", pill, credit)
                    rig.assertApart("$where: Re-center and the search bar", pill, rig.bounds(SEARCH_ENTRY_BAR_TAG))
                    val restore = rig.map.renderMode!!.navigationView!!.restoreRequestId
                    composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).performTouchInput {
                        click(Offset(center.x + sx * (width / 2 - 12.dp.toPx()), center.y + sy * (height / 2 - 6.dp.toPx())))
                    }
                    rig.settle()
                    assertEquals("$where: a touch at ($sx, $sy) on Re-center", restore + 1, rig.map.renderMode!!.navigationView!!.restoreRequestId)
                    rig.leaveNavigationView()
                }
            }
        }
        rig.setExpanded(false)
        rig.closeCard()
        rig.assertSame("$window: the card closed, Re-center", noCard, recenter())
        composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).assert(hasText(RETURN_TO_ROUTE_TEXT).not(), { "$window: D34, the card closed, Re-center is still its icon beside the L" })
    }

    /**
     * D17 (RECORD -1958, the owner: "Into the open map, at the bottom (Recommended)"): while the route card sits on the navigation card,
     * Re-center moves sideways out of the card's column into the open map beside it, at the bottom next to the record pill. It keeps
     * the height it has with no card, beside the L, and does not move as the card grows or expands. At every stop count it is clear of
     * the card, the L, the pill, the credit and the search bar, and five real touches across it reach it, each time shown again over the
     * card already up. Back where it was when the card closes. Replaces D10's rise for this case only.
     */
    protected fun assertBesideTheL(window: String, stops: List<Int> = listOf(1, 3, 6)) {
        rig.setScreen(navigating = true)
        rig.leaveNavigationView()
        val noCard = recenter()
        val navigationAlone = rig.bounds(NAVIGATION_HUD_TAG)
        val creditAtRest = rig.bounds(MAP_CREDIT_CAPTION_TAG)
        rig.plotTo(1)
        val beside = recenter()
        val l = rig.bounds(MAP_ICON_CLUSTER_TAG)
        val fontScale = composeRule.density.fontScale
        println("D17 $window font $fontScale: no card ${noCard.left.value}..${noCard.right.value} x ${noCard.top.value}..${noCard.bottom.value}, beside ${beside.left.value}..${beside.right.value} x ${beside.top.value}..${beside.bottom.value}, L ${l.left.value}..${l.right.value} x ${l.top.value}..${l.bottom.value}, credit at rest ${creditAtRest.left.value}..${creditAtRest.right.value} x ${creditAtRest.top.value}..${creditAtRest.bottom.value}")
        // At the bottom: where it sits with no card, or, where the credit is taller than its clearance (font 2.0), a gap above the credit.
        val bottom = minOf(noCard.bottom.value, (creditAtRest.top - 8.dp).value)
        assertEquals("$window: Re-center stays at the bottom, clear of the credit: its bottom (no card ${noCard.bottom}, credit top ${creditAtRest.top})", bottom, beside.bottom.value, 0.5f)
        assertTrue("$window: Re-center's left ${beside.left} is next to the L's right ${l.right}", beside.left >= l.right && beside.left <= l.right + 16.dp)
        stops.forEach { n ->
            listOf(false, true).forEach { expanded ->
                val where = "$window font $fontScale, $n stops, ${if (expanded) "expanded" else "peek"}"
                rig.plotTo(n)
                rig.setExpanded(expanded)
                listOf(0.5f to 0.5f, -1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
                    val card = rig.bounds(ROUTE_CARD_TAG)
                    val pill = recenter()
                    val credit = rig.bounds(MAP_CREDIT_CAPTION_TAG)
                    println("D17 $where ($sx, $sy): Re-center ${pill.left.value}..${pill.right.value} x ${pill.top.value}..${pill.bottom.value}, card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, credit ${credit.left.value}..${credit.right.value} x ${credit.top.value}..${credit.bottom.value}")
                    rig.assertSame("$where: Re-center", beside, pill)
                    assertCardsUnnarrowed(where, navigationAlone)
                    // D27 (RECORD -1976): where it fits, Re-center keeps its word beside the L.
                    composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).assert(hasText(RETURN_TO_ROUTE_TEXT), { "$where: Re-center has its word" })
                    rig.assertApart("$where: Re-center and the card", pill, card)
                    rig.assertApart("$where: Re-center and the L", pill, rig.bounds(MAP_ICON_CLUSTER_TAG))
                    rig.assertApart("$where: Re-center and the record pill", pill, rig.bounds(BAR_PILL))
                    rig.assertApart("$where: Re-center and the credit", pill, credit)
                    rig.assertApart("$where: Re-center and the search bar", pill, rig.bounds(SEARCH_ENTRY_BAR_TAG))
                    val restore = rig.map.renderMode!!.navigationView!!.restoreRequestId
                    composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).performTouchInput {
                        val inX = 12.dp.toPx()
                        val inY = 6.dp.toPx()
                        click(Offset(center.x + sx * (width / 2 - inX), center.y + sy * (height / 2 - inY)))
                    }
                    rig.settle()
                    assertEquals("$where: a touch at ($sx, $sy) on Re-center", restore + 1, rig.map.renderMode!!.navigationView!!.restoreRequestId)
                    // Shown again over the card already up, as the user gets there by panning.
                    rig.leaveNavigationView()
                }
            }
        }
        rig.setExpanded(false)
        rig.closeCard()
        rig.assertSame("$window: the card closed, Re-center", noCard, recenter())
    }
}

/**
 * A 915 x 412 landscape phone, navigating: the card on the right, resting on the navigation card (the L is fixed on the left while
 * navigating, RECORD -955). Here Re-center was already in the other column and stayed put (D10); D17 (RECORD -1958) puts it beside the L
 * whenever the card sits on the navigation card, so it is in the same place on every phone, and a wider navigation card cannot reach it.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w915dp-h412dp-land-xxhdpi")
class RouteCardRecenterLandscapeTest : RouteCardRecenterTests() {

    @Test
    fun `navigating in landscape with the card on the navigation card, Re-center sits beside the L, at peek and expanded`() = assertBesideTheL("915")
}

/** A tablet in landscape, navigating: the card on the navigation card in the column away from the L (C21, RECORD -1955); D17 as above. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-land-xhdpi")
class RouteCardRecenterTabletLandscapeTest : RouteCardRecenterTests() {

    @Test
    fun `on a tablet in landscape with the card on the navigation card, Re-center sits beside the L`() = assertBesideTheL("tablet landscape", listOf(3))
}

/** A tablet in portrait: the card at its one width at the left edge, Re-center at the bottom centre. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w824dp-h1318dp-xhdpi")
class RouteCardRecenterTabletTest : RouteCardRecenterTests() {

    @Test
    fun `on a tablet in portrait, Re-center stays put for a card beside it`() = assertStaysPut("tablet", listOf(3))
}

/** The S22's portrait window: they share the column, so Re-center keeps clear of the card, and comes back when it closes. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardRecenterSharedColumnTest : RouteCardRecenterTests() {

    @Test
    fun `sharing the card's column, Re-center stays clear of it, peek and expanded, and goes back when it closes`() {
        rig.setScreen(navigating = true)
        rig.leaveNavigationView()
        val before = recenter()
        rig.plotTo(3)
        listOf(false, true).forEach { expanded ->
            rig.setExpanded(expanded)
            val card = rig.bounds(ROUTE_CARD_TAG)
            val pill = recenter()
            println("A17 shared, expanded $expanded: Re-center ${pill.left.value}..${pill.right.value} x ${pill.top.value}..${pill.bottom.value}, card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}")
            assertTrue("precondition: they share a column: ${pill.left}..${pill.right} and ${card.left}..${card.right}", pill.left < card.right && card.left < pill.right)
            assertTrue("expanded $expanded: Re-center's bottom ${pill.bottom} is above the card's top ${card.top}", pill.bottom <= card.top + 0.5.dp)
        }
        rig.setExpanded(false)
        rig.closeCard()
        rig.assertSame("the card closed", before, recenter())
    }
}

/**
 * The S22's landscape window under Robolectric, no real insets: the card on the navigation card (about 400..743 dp) shared Re-center's
 * column (about 317..426 dp), and D10's rise took Re-center under the search bar at 3 and 6 stops expanded (D15, r90-3e). D17 (RECORD
 * -1958): beside the L instead, at the bottom.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardRecenterS22LandscapeTest : RouteCardRecenterTests() {

    @Test
    fun `at the S22's landscape size, Re-center sits beside the L, clear of the card, peek and expanded`() = assertBesideTheL("823")
}

/** D17 at the largest font scale tested (C23: 2.0), where the navigation card is narrowest against the L and Re-center widest. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardRecenterS22LandscapeFont20Test : RouteCardRecenterTests() {

    @Test
    fun `at the S22's landscape size and font 2_0, Re-center sits beside the L, clear of the card, peek and expanded`() = assertBesideTheL("823")
}

/** The shortest landscape window: the card on the navigation card took the middle, where Re-center sat. D17 as above. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi")
class RouteCardRecenterShortLandscapeTest : RouteCardRecenterTests() {

    @Test
    fun `in the shortest landscape window, Re-center sits beside the L, clear of the card, peek and expanded`() = assertBesideTheL("640")
}

/**
 * The shortest landscape window at font 2.0: the gap beside the L is 101 dp and Re-center with its word 160.7 dp (D17's stop, 1ea496ab5).
 * D20 (RECORD -1960) made it rise above the card here, which on the S22 put it on the search bar collapsed and under the top strip
 * expanded (D21, RECORD -1976). D27 (RECORD -1976) replaces that: it shows its icon alone beside the L at the bottom. Decided by
 * measurement (its word's width against the gap), not by this window or font.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardRecenterShortLandscapeFont20Test : RouteCardRecenterTests() {

    @Test
    fun `in the shortest landscape window at font 2_0, Re-center too wide with its word shows its icon alone beside the L, peek and expanded`() = assertIconOnlyBesideTheL("640")
}
