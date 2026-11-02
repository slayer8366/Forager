package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
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
 * bottom ([assertBesideTheL]).
 */
abstract class RouteCardRecenterTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    internal val rig = RouteCardBarRig(composeRule, credit = S22_MAP_CREDIT)

    protected fun recenter(): DpRect = rig.bounds(RETURN_TO_ROUTE_TAG)

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
        rig.plotTo(1)
        val beside = recenter()
        val l = rig.bounds(MAP_ICON_CLUSTER_TAG)
        val fontScale = composeRule.density.fontScale
        println("D17 $window font $fontScale: no card ${noCard.left.value}..${noCard.right.value} x ${noCard.top.value}..${noCard.bottom.value}, beside ${beside.left.value}..${beside.right.value} x ${beside.top.value}..${beside.bottom.value}, L ${l.left.value}..${l.right.value} x ${l.top.value}..${l.bottom.value}")
        assertEquals("$window: Re-center stays at the bottom, as with no card: its bottom", noCard.bottom.value, beside.bottom.value, 0.5f)
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

/** The shortest landscape window at font 2.0: the least open map beside the card that this suite has. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardRecenterShortLandscapeFont20Test : RouteCardRecenterTests() {

    @Test
    fun `in the shortest landscape window at font 2_0, Re-center sits beside the L, clear of the card, peek and expanded`() = assertBesideTheL("640")
}
