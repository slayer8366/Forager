package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The landscape card's place against the navigation card (Sorrel's N-5, docs/reviews/2026-11-01-sorrel-pr449-2.md on sorrel-pr449-2;
 * dispatch 2026-10-30-08, Amendment 17). The padding's floor was guarded only by HUD classes that happen to lay the card out at the
 * short heights; a navigation card whose top is within the tuck of the box's bottom leaves no room to tuck into.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardPlacementTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    /** The card's bottom padding with the navigation card's top [navigationTopAboveBoxBottom] over the box's bottom. */
    private fun bottomWith(navigationTopAboveBoxBottom: Dp): Dp {
        var room: RouteCardLandscapeRoom? = null
        composeRule.setContent {
            val density = LocalDensity.current
            val cluster = remember {
                MapIconClusterState(MapIconClusterPositionState()).apply {
                    railPortEdge = ScreenEdge.Left
                    punchHoleEdge = ScreenEdge.Right
                    mapContentBoxTopInRootPx = 0f
                    mapContentBoxHeightPx = with(density) { 384.dp.toPx() }
                    navigationCardBoundsInRoot = with(density) {
                        val top = (384.dp - navigationTopAboveBoxBottom).toPx()
                        Rect(0f, top, 278.dp.toPx(), top + 100.dp.toPx())
                    }
                }
            }
            room = routeCardLandscapeRoom(cluster, stripWidthPx = with(density) { 278.dp.roundToPx() }, topLimitPx = 0f, isNavigating = true)
        }
        composeRule.waitForIdle()
        return assertNotNull("a landscape window with a measured strip has a place", room).let { room!!.bottom }
    }

    @Test
    fun `a navigation card's top within the tuck of the box's bottom leaves the padding at 0 dp`() {
        assertEquals(0f, bottomWith(10.dp).value, 0.01f)
    }

    @Test
    fun `a navigation card's top at the box's bottom leaves the padding at 0 dp`() {
        assertEquals(0f, bottomWith(0.dp).value, 0.01f)
    }

    @Test
    fun `a taller navigation card lifts the card to its top, less the tuck`() {
        assertEquals(64f, bottomWith(80.dp).value, 0.01f)
    }
}
