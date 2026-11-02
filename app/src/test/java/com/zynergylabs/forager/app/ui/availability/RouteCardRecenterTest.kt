package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
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
 * Amendment 17, part 2 ruled (RECORD -1943, the owner: "Fix the S22 case, then check (Recommended)"): where the two share a column
 * in landscape (the 823 x 384 window here, with no real insets, and 640 x 360), whether Re-center may then sit on the search bar is
 * unruled and unproven on a phone. Those classes assert only that Re-center clears the card, and print where it is against the
 * search bar; they do not assert either way about the search bar.
 */
abstract class RouteCardRecenterTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    internal val rig = RouteCardBarRig(composeRule)

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
     * Where the two share a column in landscape: Re-center clears the card at peek and expanded (Amendment 17: "stays clear of the card
     * where they share a column"). Its place against the search bar is printed for the record and not asserted (RECORD -1943).
     */
    protected fun assertClearsSharedColumn(window: String) {
        rig.setScreen(navigating = true)
        rig.leaveNavigationView()
        rig.plotTo(3)
        listOf(false, true).forEach { expanded ->
            rig.setExpanded(expanded)
            val card = rig.bounds(ROUTE_CARD_TAG)
            val pill = recenter()
            val search = rig.bounds(SEARCH_ENTRY_BAR_TAG)
            val onSearch = !(pill.right <= search.left || search.right <= pill.left || pill.bottom <= search.top || search.bottom <= pill.top)
            println("A17 $window shared, expanded $expanded: Re-center ${pill.left.value}..${pill.right.value} x ${pill.top.value}..${pill.bottom.value}, card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, search ${search.left.value}..${search.right.value} x ${search.top.value}..${search.bottom.value}, Re-center meets the search bar: $onSearch (recorded, not asserted)")
            assertTrue("$window, expanded $expanded: precondition: they share a column: ${pill.left}..${pill.right} and ${card.left}..${card.right}", pill.left < card.right && card.left < pill.right)
            rig.assertApart("$window, expanded $expanded: Re-center and the card", pill, card)
        }
    }

    /**
     * Re-center shown with the card already up sits as it does with no card, or, where it shares the card's column, clears the card
     * (D10's rule, which C18 moved the landscape card into: the card now touches the navigation card, where Re-center sits); and five
     * real touches across it reach it.
     */
    protected fun assertShownWithCardUp(window: String) {
        rig.setScreen(navigating = true)
        rig.leaveNavigationView()
        val before = recenter()
        // Back to the navigation view as the user gets there, through Re-center, so the card comes up with no Re-center showing.
        composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).performTouchInput { click(center) }
        rig.settle()
        assertEquals("precondition: Re-center went with the view restored", 0, rig.count(RETURN_TO_ROUTE_TAG))
        rig.plotTo(3)
        rig.setExpanded(true)
        listOf(0.5f to 0.5f, -1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
            rig.leaveNavigationView()
            val card = rig.bounds(ROUTE_CARD_TAG)
            if (before.left < card.right && card.left < before.right) {
                rig.assertApart("$window: shown over a card already up, sharing its column, Re-center and the card", recenter(), card)
            } else {
                rig.assertSame("$window: shown over a card already up", before, recenter())
            }
            val restore = rig.map.renderMode!!.navigationView!!.restoreRequestId
            composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).performTouchInput {
                val inX = 12.dp.toPx()
                val inY = 6.dp.toPx()
                click(Offset(center.x + sx * (width / 2 - inX), center.y + sy * (height / 2 - inY)))
            }
            rig.settle()
            assertEquals("$window: a touch at ($sx, $sy) on Re-center", restore + 1, rig.map.renderMode!!.navigationView!!.restoreRequestId)
        }
    }
}

/**
 * A 915 x 412 landscape phone: the card on the right, resting on the navigation card (the L is fixed on the left while navigating,
 * RECORD -955), and Re-center in the other column, 20 dp apart, so it stays put. C18 had put the card touching the navigation card, in
 * Re-center's column, and this class asserted Re-center cleared it (D14); C21 (RECORD -1955) puts the card back on the navigation card,
 * and this class back to what it asserted before C18 (8a6126f9d).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w915dp-h412dp-land-xxhdpi")
class RouteCardRecenterLandscapeTest : RouteCardRecenterTests() {

    @Test
    fun `navigating in landscape, Re-center stays put for the card in the other column, at peek and expanded`() = assertStaysPut("landscape")

    @Test
    fun `shown with the card already up, Re-center sits where it does with no card, and takes real touches`() = assertShownWithCardUp("landscape")
}

/**
 * A tablet in landscape, navigating: the card on the navigation card in the column away from the L (C21, RECORD -1955), Re-center in
 * the middle of the map, so it stays put. Under C18 the card reached Re-center's column and this class asserted Re-center cleared it
 * (D14); back to what it asserted before C18 (8a6126f9d).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-land-xhdpi")
class RouteCardRecenterTabletLandscapeTest : RouteCardRecenterTests() {

    @Test
    fun `on a tablet in landscape, Re-center stays put for the card in the other column`() = assertStaysPut("tablet landscape", listOf(3))
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
 * The S22's landscape window under Robolectric, no real insets: the card (about 400..743 dp) and Re-center (about 317..426 dp) share a
 * column, so Re-center rises and clears the card. Where that leaves it against the search bar is printed, not asserted (RECORD -1943).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardRecenterS22LandscapeTest : RouteCardRecenterTests() {

    @Test
    fun `at the S22's landscape size here they share a column, and Re-center clears the card, peek and expanded`() = assertClearsSharedColumn("823")
}

/** A short phone: the room for the card takes the middle, so the two share a column here. Re-center clears the card; the search bar is printed only. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi")
class RouteCardRecenterShortLandscapeTest : RouteCardRecenterTests() {

    @Test
    fun `in the shortest landscape window they share a column, and Re-center clears the card, peek and expanded`() = assertClearsSharedColumn("640")
}
