package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * D34 with D38 (dispatch 2026-10-30-08, RECORD -2005, -2006). On the S22 at 640 x 360 dp, font 2.0, navigating, with the route card
 * closed, Re-center with its word sat in the middle of the navigation card over its distance (session 9, b1-closed). The owner: "Icon
 * beside the L (Recommended)" ("Wherever Re-center with its word would cover a card, it shows its icon alone beside the L at the
 * bottom") and "Keep it narrow (Recommended)" (the navigation card stays narrowed there with or without the route card, so the icon
 * has its spot). Decided by measurement: Re-center's word at its own place, bottom centre, against the cards' bounds.
 *
 * The S22's insets and rotation, as RouteCardRecenterLeaveRoomTest, which holds the same claim with the route card open.
 */
abstract class RouteCardRecenterCoversNoCardTests(
    clock: com.zynergylabs.forager.app.domain.CurrentTimeProvider,
    /**
     * D42 (RECORD -2021): where the sundown line shows with no card it steps aside when the route card opens, so the navigation card's
     * top moves; its sides do not (D38's "Nothing moves").
     */
    private val sundownLineShown: Boolean,
) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule, credit = S22_MAP_CREDIT, clock = clock)

    private fun assertNavigationUnmoved(what: String, expected: DpRect, actual: DpRect) {
        if (sundownLineShown) {
            assertEquals("$what: its left", expected.left.value, actual.left.value, 0.5f)
            assertEquals("$what: its right", expected.right.value, actual.right.value, 0.5f)
        } else {
            rig.assertSame(what, expected, actual)
        }
    }

    /**
     * Dispatch 2026-11-06-04, Amendment 2 (RECORD -2186), the owner: "Ring replaces the pill" (the option read: "In landscape
     * navigation the pill doesn't show; the ringed locate icon is the re-center button. Portrait keeps the pill as now"). This case
     * read D34's icon beside the L; Re-center no longer shows in landscape navigation, so it now reads that the pill is absent and
     * that real touches across the locate row, which stands in for it, bring the navigation view back, with the card closed, and
     * again after it opens and closes. D38's claims on the navigation card are kept as they were: narrowed from the L's side with
     * the card closed, and unmoved by the card opening and closing ("Nothing moves").
     */
    @Test
    fun `with the route card closed, Re-center does not show and real touches across the locate row standing in for it re-centre`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        rig.setScreen(navigating = true)
        rig.applyInsets(left = 25.dp, right = 48.dp)
        rig.leaveNavigationView()
        assertEquals("precondition: no route card", 0, rig.count(ROUTE_CARD_TAG))
        val locate = rig.described(LOCATE_ROW)
        val l = rig.bounds(MAP_ICON_CLUSTER_TAG)
        val navigation = rig.bounds(NAVIGATION_HUD_TAG)
        val strip = rig.bounds(BAR_STRIP)
        println("A2 no card: locate ${locate.left.value}..${locate.right.value} x ${locate.top.value}..${locate.bottom.value}, L ${l.left.value}..${l.right.value}, navigation card ${navigation.left.value}..${navigation.right.value} x ${navigation.top.value}..${navigation.bottom.value}, strip ${strip.left.value}..${strip.right.value}")
        // The case is reached: Re-center's word, centred on the screen's bottom, would sit on the navigation card at the strip's width.
        assertTrue("precondition: the strip's left ${strip.left} is left of the screen's centre, so the word there is over the card", strip.left < rig.screen().right / 2)
        assertEquals("D38: the navigation card is narrowed from the L's side, card closed", (l.right + 8.dp + RETURN_TO_ROUTE_PILL_HEIGHT + 8.dp).value, navigation.left.value, 0.5f)
        assertEquals("Amendment 2: Re-center does not show, the ringed locate row replaces it", 0, rig.count(RETURN_TO_ROUTE_TAG))
        rig.assertApart("the locate row and the navigation card", locate, navigation)
        rig.assertApart("the locate row and the search bar", locate, rig.bounds(SEARCH_ENTRY_BAR_TAG))
        touchesReach("no card")
        // Opening a route card and closing it again moves neither (D38: "Nothing moves").
        rig.plotTo(3)
        rig.leaveNavigationView()
        assertEquals("route card open: Re-center does not show", 0, rig.count(RETURN_TO_ROUTE_TAG))
        rig.assertSame("route card open: the locate row", locate, rig.described(LOCATE_ROW))
        assertNavigationUnmoved("route card open: the navigation card", navigation, rig.bounds(NAVIGATION_HUD_TAG))
        rig.closeCard()
        rig.leaveNavigationView()
        assertEquals("route card closed again: Re-center does not show", 0, rig.count(RETURN_TO_ROUTE_TAG))
        rig.assertSame("route card closed again: the locate row", locate, rig.described(LOCATE_ROW))
        assertNavigationUnmoved("route card closed again: the navigation card", navigation, rig.bounds(NAVIGATION_HUD_TAG))
        touchesReach("closed again")
    }

    private fun touchesReach(where: String) {
        composeRule.assertLocateRowReCentres(
            where = where,
            restoreId = { rig.map.renderMode!!.navigationView!!.restoreRequestId },
            settle = rig::settle,
            before = { at -> assertEquals("$at: Re-center does not show", 0, rig.count(RETURN_TO_ROUTE_TAG)) },
            after = { rig.leaveNavigationView() },
        )
    }
}

/**
 * D43 (RECORD -2020): at fixed clocks. Run 41758945602 failed this class on the real clock: the sundown line showed with
 * no card (navigation card 226..360 dp) and stepped aside for the route card (D42), so the navigation card's top moved.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardRecenterCoversNoCardTest : RouteCardRecenterCoversNoCardTests(ROUTE_CARD_NOON_CLOCK, sundownLineShown = false)

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardRecenterCoversNoCardAfterSunsetTest : RouteCardRecenterCoversNoCardTests(ROUTE_CARD_AFTER_SUNSET_CLOCK, sundownLineShown = true)

/**
 * D34's other side (RECORD -2005): where Re-center's word at bottom centre covers no card, nothing changes: it keeps its word there.
 * Without this, a rule that put Re-center beside the L everywhere would pass the class above. A tablet's landscape window at font 1.0:
 * the S22's own (823 x 384 dp) was tried first and is not this case under Robolectric, where the word at bottom centre (306..414 dp)
 * meets the navigation card (352..695 dp; CI run 41694249037), though on the phone it sat clear (session 9, b1-n-closed).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp-land-xhdpi", fontScale = 1.0f)
class RouteCardRecenterCoversNoCardWideTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule, credit = S22_MAP_CREDIT)

    /**
     * Dispatch 2026-11-06-04, Amendment 3 (RECORD -2186), the owner: "(b) Pill where it fits" (the option: "A real fit test: where the
     * Re-center word fits at bottom centre without covering a card, the pill shows there and there's no ring; where it doesn't fit,
     * the ring"). This is D34's other side again, as before Amendment 2: the word covers no card here, so Re-center shows with it at
     * bottom centre (the claim and name restored from pre-main), and real touches across the locate row still re-centre.
     */
    @Test
    fun `where Re-center's word covers no card it keeps its word at bottom centre`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        rig.setScreen(navigating = true)
        rig.applyInsets(left = 25.dp, right = 48.dp)
        rig.leaveNavigationView()
        val pill = rig.bounds(RETURN_TO_ROUTE_TAG)
        val navigation = rig.bounds(NAVIGATION_HUD_TAG)
        val strip = rig.bounds(BAR_STRIP)
        println("D34 wide, no card: Re-center ${pill.left.value}..${pill.right.value} x ${pill.top.value}..${pill.bottom.value}, navigation card ${navigation.left.value}..${navigation.right.value}, strip ${strip.left.value}..${strip.right.value}")
        rig.assertSame("the navigation card is at the strip's width", DpRect(strip.left, navigation.top, strip.right, navigation.bottom), navigation)
        composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).assert(hasText(RETURN_TO_ROUTE_TEXT), { "Re-center keeps its word" })
        rig.assertApart("Re-center and the navigation card", pill, navigation)
        assertTrue("Re-center is not beside the L: its left ${pill.left}", pill.left > rig.bounds(MAP_ICON_CLUSTER_TAG).right + 16.dp)
        composeRule.assertLocateRowReCentres(
            where = "wide",
            restoreId = { rig.map.renderMode!!.navigationView!!.restoreRequestId },
            settle = rig::settle,
            before = { at -> assertEquals("$at: Re-center shows before it", 1, rig.count(RETURN_TO_ROUTE_TAG)) },
            after = { rig.leaveNavigationView() },
        )
    }
}
