package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import org.junit.Assert.assertEquals
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
 * D41 (dispatch 2026-10-30-08, RECORD -2020, the planner's call within D37): at one stop there are no rows, so expanded is the same as
 * peek, even where the peek fills its cap. With D42 and D44 in, the windows the other classes use leave a one-stop peek room to spare
 * (the D41 revert on c95f15dd5, run 41761955660, did not fail them), so this one is short enough for a one-stop peek to fill its cap
 * with no sundown line to step aside: 640 x 320 dp at font 2.0, noon, a multi-window size.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h320dp-land-xxhdpi", fontScale = 2.0f)
class RouteCardOneStopExpandTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule, credit = S22_MAP_CREDIT)

    @Test
    fun `at one stop, with no rows, a real tap on the handle leaves the card as it is at peek`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        rig.setScreen(navigating = true)
        rig.plotTo(1)
        val strip = rig.bounds(BAR_STRIP)
        val peek = rig.bounds(ROUTE_CARD_TAG)
        println("D41 peek: card ${peek.top.value}..${peek.bottom.value}, strip bottom ${strip.bottom.value}, sundown line ${rig.count(NAVIGATION_HUD_SUNDOWN_LINE_TAG)}")
        assertEquals("precondition: the one-stop peek fills its cap, its top a gap below the strip", (strip.bottom + 8.dp).value, peek.top.value, 0.5f)
        rig.touchHandle()
        assertEquals("the handle's tap was taken", true, rig.plotting.uiState.value.expanded)
        rig.assertSame("at one stop, expanded is the same as peek", peek, rig.bounds(ROUTE_CARD_TAG))
    }
}
