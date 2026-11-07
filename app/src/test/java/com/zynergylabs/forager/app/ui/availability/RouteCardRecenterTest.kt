package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * Amendment 17, part 2 ruled (RECORD -1943): where the two share a column, Re-center rises for the card; the portrait classes here.
 * D17 (RECORD -1958) replaces that rise in landscape while the card sits on the navigation card: Re-center goes beside the L, at the
 * bottom. D27 (RECORD -1976): where its word does not fit there, its icon alone. Dispatch 2026-11-06-04 Amendment 2 (RECORD -2186)
 * replaces both in landscape navigation: the pill does not show there and the ringed locate row stands in for it
 * ([assertLocateRowStandsIn]); the portrait classes are unchanged.
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
     * Dispatch 2026-11-06-04, Amendment 2 (RECORD -2186), the owner: "Ring replaces the pill" (the option read: "In landscape
     * navigation the pill doesn't show; the ringed locate icon is the re-center button. Portrait keeps the pill as now"). This replaces
     * D17's [assertBesideTheL] and D27's icon alone beside the L, both the pill's place in landscape navigation, which no longer shows.
     * The same windows and the same walk: navigating and moved away, then the route card on the navigation card at each stop count,
     * peek and expanded, and closed again. Throughout, Re-center does not show; the locate row, which stands in for it, stays clear of
     * the card and the search bar, and five real touches across it ([assertLocateRowReCentres]) bring the navigation view back, each
     * time moved away again over the card already up. D32's claim on these windows is kept as it was: neither card narrows here.
     */
    protected fun assertLocateRowStandsIn(window: String, stops: List<Int> = listOf(1, 3, 6)) {
        rig.setScreen(navigating = true)
        rig.leaveNavigationView()
        val fontScale = composeRule.density.fontScale
        val navigationAlone = rig.bounds(NAVIGATION_HUD_TAG)
        assertEquals("$window font $fontScale, no card: Re-center does not show, the ringed locate row replaces it", 0, rig.count(RETURN_TO_ROUTE_TAG))
        stops.forEach { n ->
            listOf(false, true).forEach { expanded ->
                val where = "$window font $fontScale, $n stops, ${if (expanded) "expanded" else "peek"}"
                rig.plotTo(n)
                rig.setExpanded(expanded)
                val card = rig.bounds(ROUTE_CARD_TAG)
                val locate = rig.described(LOCATE_ROW)
                println("A2 $where: locate ${locate.left.value}..${locate.right.value} x ${locate.top.value}..${locate.bottom.value}, card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}")
                assertCardsUnnarrowed(where, navigationAlone)
                rig.assertApart("$where: the locate row and the card", locate, card)
                rig.assertApart("$where: the locate row and the search bar", locate, rig.bounds(SEARCH_ENTRY_BAR_TAG))
                composeRule.assertLocateRowReCentres(
                    where = where,
                    restoreId = { rig.map.renderMode!!.navigationView!!.restoreRequestId },
                    settle = rig::settle,
                    before = { at -> assertEquals("$at: Re-center does not show", 0, rig.count(RETURN_TO_ROUTE_TAG)) },
                    after = { at ->
                        assertEquals("$at: Re-center does not show after it", 0, rig.count(RETURN_TO_ROUTE_TAG))
                        // Moved away again over the card already up, as the user gets there by panning.
                        rig.leaveNavigationView()
                    },
                )
            }
        }
        rig.setExpanded(false)
        rig.closeCard()
        assertEquals("$window: the card closed, Re-center still does not show", 0, rig.count(RETURN_TO_ROUTE_TAG))
    }

    /**
     * Dispatch 2026-11-06-04, Amendment 3 (RECORD -2186), the owner: "(b) Pill where it fits" (the option: "A real fit test: where the
     * Re-center word fits at bottom centre without covering a card, the pill shows there and there's no ring; where it doesn't fit,
     * the ring"). Where the word at bottom centre covers neither card, whether or not the route card is open, Re-center shows there
     * with its word: not beside the L (D17's place for a card on the navigation card, which this replaces where it fits), never
     * risen, clear of both cards and the search bar, and in the same place with the card closed, at each stop count, peek and
     * expanded, and closed again. Real touches across the locate row still bring the navigation view back, as before Amendment 2.
     */
    protected fun assertPillAtBottomCentre(window: String, stops: List<Int> = listOf(1, 3, 6)) {
        rig.setScreen(navigating = true)
        rig.leaveNavigationView()
        assertEquals("$window, no card: Re-center shows, its word fitting at bottom centre", 1, rig.count(RETURN_TO_ROUTE_TAG))
        val navigationAlone = rig.bounds(NAVIGATION_HUD_TAG)
        val home = recenter()
        val l = rig.bounds(MAP_ICON_CLUSTER_TAG)
        println("A3 $window, no card: Re-center ${home.left.value}..${home.right.value} x ${home.top.value}..${home.bottom.value}, L ${l.left.value}..${l.right.value}, navigation card ${rig.bounds(NAVIGATION_HUD_TAG).left.value}..")
        assertTrue("$window: Re-center is not beside the L (within 16 dp of the L's right ${l.right}); its left is ${home.left}", home.left > l.right + 16.dp)
        assertTrue("$window: Re-center keeps its word, ${home.right - home.left} wide", home.right - home.left > 60.dp)
        stops.forEach { n ->
            listOf(false, true).forEach { expanded ->
                val where = "$window, $n stops, ${if (expanded) "expanded" else "peek"}"
                rig.plotTo(n)
                rig.setExpanded(expanded)
                rig.leaveNavigationView()
                val card = rig.bounds(ROUTE_CARD_TAG)
                println("A3 $where: Re-center ${recenter().left.value}..${recenter().right.value} x ${recenter().top.value}..${recenter().bottom.value}, card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}")
                assertEquals("$where: Re-center shows, its word fitting at bottom centre", 1, rig.count(RETURN_TO_ROUTE_TAG))
                assertCardsUnnarrowed(where, navigationAlone)
                rig.assertSame("$where: Re-center stays at bottom centre, where it is with no card", home, recenter())
                rig.assertApart("$where: Re-center and the route card", recenter(), card)
                rig.assertApart("$where: Re-center and the navigation card", recenter(), rig.bounds(NAVIGATION_HUD_TAG))
                rig.assertApart("$where: Re-center and the search bar", recenter(), rig.bounds(SEARCH_ENTRY_BAR_TAG))
                composeRule.assertLocateRowReCentres(
                    where = where,
                    restoreId = { rig.map.renderMode!!.navigationView!!.restoreRequestId },
                    settle = rig::settle,
                    before = { at -> assertEquals("$at: Re-center shows before it", 1, rig.count(RETURN_TO_ROUTE_TAG)) },
                    after = { rig.leaveNavigationView() },
                )
            }
        }
        rig.setExpanded(false)
        rig.closeCard()
        rig.leaveNavigationView()
        rig.assertSame("$window: the card closed, Re-center", home, recenter())
    }
}

/**
 * A 915 x 412 landscape phone, navigating: the card on the right, resting on the navigation card (the L is fixed on the left while
 * navigating, RECORD -955). Here Re-center was already in the other column and stayed put (D10); D17 (RECORD -1958) puts it beside the L
 * whenever the card sits on the navigation card, so it is in the same place on every phone, and a wider navigation card cannot reach it.
 * Amendment 2 (RECORD -2186) put the ringed locate row in its place; Amendment 3 (the owner: "(b) Pill where it fits"): its word at
 * bottom centre covers no card here (363..472 dp, the cards from 492 dp, run 42405188355), so it shows there again.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w915dp-h412dp-land-xxhdpi")
class RouteCardRecenterLandscapeTest : RouteCardRecenterTests() {

    @Test
    fun `navigating in landscape with the card on the navigation card, Re-center keeps its word at bottom centre where it fits, at peek and expanded`() = assertPillAtBottomCentre("915")
}

/**
 * A tablet in landscape, navigating: the card on the navigation card in the column away from the L (C21, RECORD -1955). D17 put
 * Re-center beside the L here, and Amendment 2 the ringed locate row in its place. Amendment 3 (RECORD -2186, the owner: "Tablets keep
 * the pill", then "(b) Pill where it fits"): Re-center's word at bottom centre covers no card here, so the pill shows there.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-land-xhdpi")
class RouteCardRecenterTabletLandscapeTest : RouteCardRecenterTests() {

    @Test
    fun `on a tablet in landscape with the card on the navigation card, Re-center keeps its word at bottom centre where it fits`() = assertPillAtBottomCentre("tablet landscape", listOf(3))
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
    fun `at the S22's landscape size, the ringed locate row stands in for Re-center, clear of the card, peek and expanded`() = assertLocateRowStandsIn("823")
}

/** D17 at the largest font scale tested (C23: 2.0), where the navigation card is narrowest against the L and Re-center widest. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardRecenterS22LandscapeFont20Test : RouteCardRecenterTests() {

    @Test
    fun `at the S22's landscape size and font 2_0, the ringed locate row stands in for Re-center, clear of the card, peek and expanded`() = assertLocateRowStandsIn("823")
}

/** The shortest landscape window: the card on the navigation card took the middle, where Re-center sat. D17 as above. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi")
class RouteCardRecenterShortLandscapeTest : RouteCardRecenterTests() {

    @Test
    fun `in the shortest landscape window, the ringed locate row stands in for Re-center, clear of the card, peek and expanded`() = assertLocateRowStandsIn("640")
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
    fun `in the shortest landscape window at font 2_0, the ringed locate row stands in for Re-center, peek and expanded`() = assertLocateRowStandsIn("640")
}
