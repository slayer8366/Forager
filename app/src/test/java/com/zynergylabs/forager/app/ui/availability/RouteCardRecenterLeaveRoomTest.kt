package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * D32 (dispatch 2026-10-30-08, RECORD -1987; the owner: "Both cards narrow (Recommended)" and "Always (Recommended)"), replacing D31.
 * On the S22 at 640 x 360 dp, font 2.0, navigating in landscape with 3 stops at peek, the gap between the L and the cards was about
 * 25 dp (s8 b3-3p-rc.xml: the L's foot ends at 484 px, both cards start at 576 px), narrower than Re-center's 48 dp icon, so it took
 * D10's rise to a 22 px sliver under the search strip (docs/audits/2026-11-03-s22-session-8.md on tools/s22-combined, B3).
 * RouteCardRecenterShortLandscapeFont20Test passed at the same size because Robolectric reports no window insets: there the gap is
 * 101 dp. This class gives the window the S22's insets, 25 dp on the left and 48 dp on the right.
 *
 * Both cards narrow from the L's side by just enough for the icon and an 8 dp gap either side, keeping their right edges and edge to
 * edge (C21); Re-center shows its icon alone at the bottom beside the L, never risen, at every stop count, peek and expanded, and
 * five real touches across it reach it.
 *
 * D38 (RECORD -2006, the owner: "Keep it narrow (Recommended)") changed this class's last claim: with the card closed the navigation
 * card used to be back at the strip's width (D32), and now it stays narrowed, so Re-center keeps its spot beside the L (D34). The
 * natural width is read from the strip, which the navigation card sits at the width of, since the navigation card no longer shows it.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardRecenterLeaveRoomTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule, credit = S22_MAP_CREDIT)

    /**
     * Dispatch 2026-11-06-04, Amendment 2 (RECORD -2186), the owner: "Ring replaces the pill" (the option read: "In landscape
     * navigation the pill doesn't show; the ringed locate icon is the re-center button. Portrait keeps the pill as now"). This case
     * read Re-center's icon beside the L in the room D32 leaves; Re-center no longer shows in landscape navigation, so it now reads
     * that the pill is absent and that real touches across the locate row, which stands in for it, bring the navigation view back,
     * at every stop count, peek and expanded. D32's and D38's claims on the two cards are kept exactly as they were (both narrowed
     * from the L's side by the icon's room, their right edges the strip's, the navigation card still narrowed with the card closed):
     * Amendment 2 did not rule on them, and whether that room should stay now that nothing shows in it is the owner's question.
     */
    @Test
    fun `with the S22's insets at 640 x 360 and font 2_0, both cards narrow from the L's side and the locate row stands in for Re-center`() {
        // The S22 was at user_rotation 1 (s8 logs/b3-set.txt), the cutout on the left.
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        rig.setScreen(navigating = true)
        rig.applyInsets(left = 25.dp, right = 48.dp)
        rig.leaveNavigationView()
        val navigationAlone = rig.bounds(NAVIGATION_HUD_TAG)
        val strip = rig.bounds(BAR_STRIP)
        val l = rig.bounds(MAP_ICON_CLUSTER_TAG)
        println("D32 no card: L ${l.left.value}..${l.right.value} x ${l.top.value}..${l.bottom.value}, strip ${strip.left.value}..${strip.right.value}, navigation card ${navigationAlone.left.value}..${navigationAlone.right.value} x ${navigationAlone.top.value}..${navigationAlone.bottom.value}, natural gap ${(strip.left - l.right).value} (the S22: 24.5)")
        // The case is reached: at the strip's width the gap is narrower than the icon and a gap either side.
        val needed = 8.dp + RETURN_TO_ROUTE_PILL_HEIGHT + 8.dp
        assertTrue("precondition: the gap beside the L at the strip's width, ${strip.left - l.right}, is narrower than $needed", strip.left - l.right < needed)
        val cardsLeft = l.right + needed
        // D38: with no card the navigation card is already narrowed, its right edge the strip's.
        assertEquals("D38, no card: the navigation card is narrowed from the L's side", cardsLeft.value, navigationAlone.left.value, 0.5f)
        assertEquals("D38, no card: the navigation card keeps the strip's right edge", strip.right.value, navigationAlone.right.value, 0.5f)
        assertEquals("Amendment 2, no card: Re-center does not show", 0, rig.count(RETURN_TO_ROUTE_TAG))
        listOf(1, 3, 6).forEach { n ->
            listOf(false, true).forEach { expanded ->
                val where = "$n stops, ${if (expanded) "expanded" else "peek"}"
                rig.plotTo(n)
                rig.setExpanded(expanded)
                val card = rig.bounds(ROUTE_CARD_TAG)
                val navigation = rig.bounds(NAVIGATION_HUD_TAG)
                val locate = rig.described(LOCATE_ROW)
                println("D32 $where: locate ${locate.left.value}..${locate.right.value} x ${locate.top.value}..${locate.bottom.value}, card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, navigation card ${navigation.left.value}..${navigation.right.value} x ${navigation.top.value}..${navigation.bottom.value}")
                assertEquals("$where: the route card's left leaves just the room for the icon beside the L", cardsLeft.value, card.left.value, 0.5f)
                assertEquals("$where: the navigation card narrows with it, from the L's side", cardsLeft.value, navigation.left.value, 0.5f)
                assertEquals("$where: the route card keeps its right edge", strip.right.value, card.right.value, 0.5f)
                assertEquals("$where: the navigation card keeps its right edge", strip.right.value, navigation.right.value, 0.5f)
                rig.assertApart("$where: the locate row and the route card", locate, card)
                rig.assertApart("$where: the locate row and the navigation card", locate, navigation)
                rig.assertApart("$where: the locate row and the search bar", locate, rig.bounds(SEARCH_ENTRY_BAR_TAG))
                composeRule.assertLocateRowReCentres(
                    where = where,
                    restoreId = { rig.map.renderMode!!.navigationView!!.restoreRequestId },
                    settle = rig::settle,
                    before = { at -> assertEquals("$at: Re-center does not show", 0, rig.count(RETURN_TO_ROUTE_TAG)) },
                    after = { rig.leaveNavigationView() },
                )
            }
        }
        rig.setExpanded(false)
        rig.closeCard()
        // D38 (replacing D32's "back at its width"): the card closed, the navigation card stays narrowed.
        rig.assertSame("the card closed, the navigation card stays narrowed", navigationAlone, rig.bounds(NAVIGATION_HUD_TAG))
        assertEquals("the card closed, Re-center still does not show", 0, rig.count(RETURN_TO_ROUTE_TAG))
    }
}
