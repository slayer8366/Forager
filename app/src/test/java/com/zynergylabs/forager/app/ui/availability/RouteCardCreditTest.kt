package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/** The credit the S22 showed in D12 (`d12/ui/r90-3p.xml`), 1638 px wide there at 3.75 px per dp. */
internal const val S22_MAP_CREDIT = "Protomaps © OpenStreetMap · Trails: USDA Forest Service, CC BY 4.0 · BLM"

/**
 * Dispatch 2026-10-30-08, D13 (RECORD -1955, the planner's call under D9): the map's credit strip keeps clear of the route card wherever
 * the card is. D12 saw it drawn over the card's button row on the S22 (Undo by 9 px, Save by 46 px), because the credit rose only for a
 * card in the other bottom corner (the L on the right). Now it rises where the card would cover it at rest, and only there.
 *
 * The real Maps tab in a landscape window, recording, with the S22's credit drawn where the map draws it (`RouteMapSlot`). Each case
 * asserts the drawn credit's bounds against the card's, so a credit that rises for nothing, or stays down under the card, fails.
 */
abstract class RouteCardCreditTests(private val rotation: Int) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule, credit = S22_MAP_CREDIT)

    private fun overlaps(a: DpRect, b: DpRect): Boolean = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    /** The credit and the card at [stops] stops, peek and expanded: never overlapping. Returns whether the credit rose, at each. */
    private fun walk(where: String, stops: List<Int> = listOf(1, 3, 6)): List<Boolean> {
        val rest = rig.bounds(MAP_CREDIT_CAPTION_TAG)
        return stops.flatMap { n ->
            listOf(false, true).map { expanded ->
                rig.plotTo(n)
                rig.setExpanded(expanded)
                val at = "$where, $n stops, ${if (expanded) "expanded" else "peek"}"
                val card = rig.bounds(ROUTE_CARD_TAG)
                val credit = rig.bounds(MAP_CREDIT_CAPTION_TAG)
                println("D13 font ${composeRule.density.fontScale} rot $rotation $at: card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, credit ${credit.left.value}..${credit.right.value} x ${credit.top.value}..${credit.bottom.value} (rest top ${rest.top.value})")
                assertTrue("$at: the credit $credit is clear of the card $card", !overlaps(credit, card))
                credit.top < rest.top - 0.5.dp
            }
        }
    }

    /** Not navigating, the L on the left: the card is in the navigation card's spot, on the rail's side. */
    protected fun notNavigatingLLeft(): List<Boolean> {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        rig.setScreen()
        return walk("not navigating, the L on the left")
    }

    /** Navigating: the card rests on the navigation card (C21). */
    protected fun navigating(): List<Boolean> {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        rig.setScreen(navigating = true)
        return walk("navigating")
    }

    /** C20, the L on the rail's side: the card in the other bottom corner, over the credit's start, which rises as before. */
    protected fun lOnTheRight(): List<Boolean> {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        rig.setScreen()
        rig.dragBarAcross(rightward = true)
        return walk("not navigating, the L on the right")
    }

    @Test
    fun `navigating, the card on the navigation card is clear of the credit, which stays down`() {
        assertEquals("the credit never rises while the card rests on the navigation card", emptyList<Boolean>(), navigating().filter { it })
    }

    @Test
    fun `the L on the right, the credit rises clear of the card in its corner`() {
        assertTrue("the credit rose for the card over it", lOnTheRight().all { it })
    }
}

/** Font 1.0 on the S22's window: with the L on the left the card ends short of the credit's end, so the credit stays down. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.0f)
class RouteCardCreditTest : RouteCardCreditTests(Surface.ROTATION_90) {
    @Test
    fun `not navigating, the L on the left, the credit is clear of the card`() {
        notNavigatingLLeft()
    }
}

/**
 * Font 1.3: the credit is wider and the card too, so with the L on the left the card in the rail's corner covers the credit's end.
 * Under the old rule (the L's side) it stayed down under the card; it rises now.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.3f)
class RouteCardCreditFont13Test : RouteCardCreditTests(Surface.ROTATION_90) {
    @Test
    fun `not navigating, the L on the left, the card over the credit's end lifts it clear`() {
        assertTrue("the credit rose for the card over its end", notNavigatingLLeft().all { it })
    }
}

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardCreditFont20Test : RouteCardCreditTests(Surface.ROTATION_270) {
    @Test
    fun `not navigating, the L on the left, the credit is clear of the card`() {
        notNavigatingLLeft()
    }
}
