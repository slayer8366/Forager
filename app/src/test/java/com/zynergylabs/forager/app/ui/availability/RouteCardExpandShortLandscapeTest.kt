package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_HANDLE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_LEGS_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_COUNT_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_ELEVATION_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_ROUTED_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_UNDO_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.routeCardLegTag
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
 * D37 (dispatch 2026-10-30-08, RECORD -2006; the owner: "Cover the top bar (Recommended)"), replacing D35's fix. On the S22 at
 * 640 x 360 dp, font 2.0, navigating in landscape, 3 and 6 stops, the handle did not open the card (docs/audits/2026-11-03-s22-session-9.md
 * on tools/s22-combined, B1). Not a broken handle: the flag flipped, but the card's cap (RouteCardPlacement.kt, maxHeight) was already
 * filled by the peek lines, so the rows got 0 dp (CI run 41635001490: the card 45.33..242 dp, peek and expanded alike). So, only there
 * and only while expanded, the cap rises over the top strip; the navigation card stays; collapsing brings the strip back.
 *
 * The S22's insets (25 dp cutout on the left, 48 dp navigation bar on the right) and rotation, as RouteCardRecenterLeaveRoomTest. The
 * claim is that a finger opens the card, so the handle is touched at five points across its bounds and dragged up, never clicked
 * semantically; and the claim is what shows, so leg row 0's bounds are read clipped, not unclipped: the rows are laid out at full
 * height and clipped to what shows (RouteCardReveal.kt), so an unclipped read passes on a card that shows no row.
 */
abstract class RouteCardExpandShortLandscapeTests(private val clock: com.zynergylabs.forager.app.domain.CurrentTimeProvider) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    // A fixed clock (D43): the navigation card's sundown line would change the room by the hour.
    private val rig = RouteCardBarRig(composeRule, credit = S22_MAP_CREDIT, clock = clock)

    /** Leg row 0 as drawn: clipped by the reveal and by the card. */
    private fun rowShown(): DpRect = composeRule.onNodeWithTag(routeCardLegTag(0), useUnmergedTree = true).getBoundsInRoot()

    private fun assertOpen(where: String, peek: DpRect) {
        assertEquals("$where: the card's state", true, rig.plotting.uiState.value.expanded)
        val row = rowShown()
        val card = rig.bounds(ROUTE_CARD_TAG)
        println("D37 $where: card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, row 0 shown ${row.top.value}..${row.bottom.value} (peek top ${peek.top.value})")
        // What the card's lines take, so a card at its cap can be read: each line's unclipped top and bottom.
        listOf(ROUTE_CARD_HANDLE_TAG, ROUTE_CARD_LEGS_TAG, ROUTE_CARD_ROUTED_TAG, ROUTE_CARD_ROUTED_COUNT_TAG, ROUTE_CARD_ROUTED_ELEVATION_TAG, ROUTE_CARD_UNDO_TAG, ROUTE_CARD_SAVE_TAG, NAVIGATION_HUD_TAG, BAR_STRIP).forEach { tag ->
            if (rig.count(tag) > 0) {
                val b = rig.bounds(tag)
                println("D37 $where: $tag ${b.left.value}..${b.right.value} x ${b.top.value}..${b.bottom.value}")
            }
        }
        assertTrue("$where: leg row 0 shows, height ${row.bottom - row.top}", row.bottom - row.top > 0.dp)
        assertTrue("$where: the card rose over the strip, top ${card.top} above the peek's ${peek.top}", card.top < peek.top - 0.5.dp)
        rig.assertSame("$where: the card's bottom stays on the navigation card", peek.copy(top = card.top), card)
        assertEquals("$where: the navigation card stays", 1, rig.count(NAVIGATION_HUD_TAG))
        // D40 (RECORD -2009, the owner: "Hide elevation while open"): here even the raised cap is filled by the lines (CI run
        // 41698619389: 250 dp of lines, 250 dp of cap), so the total line's elevation line is left out; the count and the buttons stay.
        assertEquals("$where: D40, the total line's elevation line is left out while open", 0, rig.count(ROUTE_CARD_ROUTED_ELEVATION_TAG))
        assertEquals("$where: D40, \"N Waypoints routed\" stays", 1, rig.count(ROUTE_CARD_ROUTED_COUNT_TAG))
        assertEquals("$where: D40, Save stays", 1, rig.count(ROUTE_CARD_SAVE_TAG))
        assertEquals("$where: D40, Undo stays", 1, rig.count(ROUTE_CARD_UNDO_TAG))
    }

    private fun assertClosed(where: String, peek: DpRect) {
        assertEquals("$where: the card's state", false, rig.plotting.uiState.value.expanded)
        rig.assertSame("$where: collapsed, the card is back at its peek, under the strip", peek, rig.bounds(ROUTE_CARD_TAG))
        // D44 (RECORD -2027, the owner: "Elevation steps aside here too"): this peek's lines (250 dp) do not fit its cap (212.67 dp), so
        // collapsed, the elevation line stays out; D40's "back on collapse" holds where the peek has the room.
        assertEquals("$where: D44, collapsed, the elevation line stays out at this peek", 0, rig.count(ROUTE_CARD_ROUTED_ELEVATION_TAG))
    }

    @Test
    fun `at 640 x 360, font 2_0, navigating, real taps and a drag up on the handle open the card's rows over the top strip`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        rig.setScreen(navigating = true)
        rig.applyInsets(left = 25.dp, right = 48.dp)
        listOf(3, 6).forEach { n ->
            rig.plotTo(n)
            assertEquals("precondition, $n stops: peek", false, rig.plotting.uiState.value.expanded)
            assertEquals("D44, $n stops: the peek's lines do not fit its cap, so its elevation line is left out", 0, rig.count(ROUTE_CARD_ROUTED_ELEVATION_TAG))
            val peek = rig.bounds(ROUTE_CARD_TAG)
            listOf(0.5f to 0.5f, 0.2f to 0.2f, 0.8f to 0.2f, 0.2f to 0.8f, 0.8f to 0.8f).forEach { (fx, fy) ->
                val where = "$n stops, a tap at ($fx, $fy) on the handle"
                composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { click(Offset(width * fx, height * fy)) }
                rig.settle()
                assertOpen(where, peek)
                composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput { click(Offset(width * fx, height * fy)) }
                rig.settle()
                assertClosed("$where, then again", peek)
            }
            composeRule.onNodeWithTag(ROUTE_CARD_HANDLE_TAG).performTouchInput {
                val from = Offset(width * 0.5f, height * 0.5f)
                down(from)
                repeat(10) { k ->
                    advanceEventTime(16)
                    moveTo(from + Offset(0f, -(k + 1) * 8.dp.toPx()))
                }
                up()
            }
            rig.settle()
            assertOpen("$n stops, a drag up from the handle", peek)
            rig.setExpanded(false)
            assertClosed("$n stops, after the drag, collapsed", peek)
        }
    }
}

/**
 * D43 (RECORD -2020): at fixed clocks. Pre-main's push run 41747193319 failed this class with the sundown line ("Sunset was …")
 * showing, which fits the navigation card here and takes the room; it had passed earlier in the day.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardExpandShortLandscapeTest : RouteCardExpandShortLandscapeTests(ROUTE_CARD_NOON_CLOCK)

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardExpandShortLandscapeEveningTest : RouteCardExpandShortLandscapeTests(ROUTE_CARD_EVENING_CLOCK)

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardExpandShortLandscapeAfterSunsetTest : RouteCardExpandShortLandscapeTests(ROUTE_CARD_AFTER_SUNSET_CLOCK)
