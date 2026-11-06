package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
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

    @Test
    fun `with the route card closed, Re-center whose word would cover the navigation card is its icon beside the L, and real touches reach it`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        rig.setScreen(navigating = true)
        rig.applyInsets(left = 25.dp, right = 48.dp)
        rig.leaveNavigationView()
        assertEquals("precondition: no route card", 0, rig.count(ROUTE_CARD_TAG))
        val pill = rig.bounds(RETURN_TO_ROUTE_TAG)
        val l = rig.bounds(MAP_ICON_CLUSTER_TAG)
        val navigation = rig.bounds(NAVIGATION_HUD_TAG)
        val strip = rig.bounds(BAR_STRIP)
        println("D34 no card: Re-center ${pill.left.value}..${pill.right.value} x ${pill.top.value}..${pill.bottom.value}, L ${l.left.value}..${l.right.value}, navigation card ${navigation.left.value}..${navigation.right.value} x ${navigation.top.value}..${navigation.bottom.value}, strip ${strip.left.value}..${strip.right.value}")
        // The case is reached: Re-center's word, centred on the screen's bottom, would sit on the navigation card at the strip's width.
        assertTrue("precondition: the strip's left ${strip.left} is left of the screen's centre, so the word there is over the card", strip.left < rig.screen().right / 2)
        assertEquals("D38: the navigation card is narrowed from the L's side, card closed", (l.right + 8.dp + RETURN_TO_ROUTE_PILL_HEIGHT + 8.dp).value, navigation.left.value, 0.5f)
        assertEquals("D34: Re-center is its icon alone, a 48 dp target: its width", 48f, (pill.right - pill.left).value, 0.5f)
        assertEquals("D34: its height", 48f, (pill.bottom - pill.top).value, 0.5f)
        composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).assert(hasText(RETURN_TO_ROUTE_TEXT).not(), { "D34: Re-center shows no word" })
        composeRule.onNodeWithTag(RETURN_TO_ROUTE_TAG).assertContentDescriptionEquals(RETURN_TO_ROUTE_TEXT)
        assertTrue("D34: Re-center's left ${pill.left} is next to the L's right ${l.right}", pill.left >= l.right && pill.left <= l.right + 16.dp)
        rig.assertApart("Re-center and the navigation card", pill, navigation)
        rig.assertApart("Re-center and the L", pill, l)
        rig.assertApart("Re-center and the record pill", pill, rig.bounds(BAR_PILL))
        rig.assertApart("Re-center and the credit", pill, rig.bounds(MAP_CREDIT_CAPTION_TAG))
        rig.assertApart("Re-center and the search bar", pill, rig.bounds(SEARCH_ENTRY_BAR_TAG))
        touchesReach("no card", pill)
        // Opening a route card and closing it again moves neither (D38: "Nothing moves").
        rig.plotTo(3)
        rig.leaveNavigationView()
        rig.assertSame("route card open: Re-center", pill, rig.bounds(RETURN_TO_ROUTE_TAG))
        assertNavigationUnmoved("route card open: the navigation card", navigation, rig.bounds(NAVIGATION_HUD_TAG))
        rig.closeCard()
        rig.leaveNavigationView()
        rig.assertSame("route card closed again: Re-center", pill, rig.bounds(RETURN_TO_ROUTE_TAG))
        assertNavigationUnmoved("route card closed again: the navigation card", navigation, rig.bounds(NAVIGATION_HUD_TAG))
        touchesReach("closed again", pill)
    }

    private fun touchesReach(where: String, pill: DpRect) {
        listOf(0.5f to 0.5f, -1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (sx, sy) ->
            rig.assertSame("$where: Re-center before the touch at ($sx, $sy)", pill, rig.bounds(RETURN_TO_ROUTE_TAG))
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
        val screen = rig.screen()
        assertTrue("Re-center is not beside the L: its left ${pill.left}", pill.left > rig.bounds(MAP_ICON_CLUSTER_TAG).right + 16.dp)
        println("D34 wide: screen ${screen.right.value}")
    }
}
