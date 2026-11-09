package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
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
 * Dispatch 2026-11-06-01, Amendment 1, J5, the defect (RECORD -2183): "in RouteCardRecenterCoversNoCardAfterSunsetTest the moved card's
 * top lands off-screen". The same screen: 640 x 360, font 2.0, just after sunset, the S22's insets, Re-center showing, so the card is
 * narrowed (D38) and its evening pieces wrap in a narrow right part. Wherever the card is, moved up or on the bottom edge, its top is
 * never above the compass strip's bottom (J3 step 3: "If it still can't fit with the card moved up", RECORD -955 holds). Read unclipped,
 * since the clipped bounds stop at the screen's top and hid this.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
class NavigationCardUnderStripTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule, credit = S22_MAP_CREDIT, clock = ROUTE_CARD_AFTER_SUNSET_CLOCK)

    private fun assertUnderStrip(stage: String) {
        val card = rig.bounds(NAVIGATION_HUD_TAG)
        val strip = rig.bounds(BAR_STRIP)
        val screen = rig.screen()
        println("J5 defect $stage: navigation card ${card.top.value}..${card.bottom.value}, strip bottom ${strip.bottom.value}, screen ${screen.bottom.value}, evening line ${rig.count(NAVIGATION_HUD_SUNDOWN_LINE_TAG)}")
        assertTrue("$stage: the card's top ${card.top} is under the compass strip's bottom ${strip.bottom}", card.top >= strip.bottom - 0.5.dp)
        assertTrue("$stage: the card's bottom ${card.bottom} is on the screen (${screen.bottom})", card.bottom <= screen.bottom + 0.5.dp)
    }

    @Test
    fun `J5 - after sunset at font 2,0 with the S22's insets and Re-center showing, the card never passes the compass strip`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        rig.setScreen(navigating = true)
        assertUnderStrip("no insets")
        rig.applyInsets(left = 25.dp, right = 48.dp)
        assertUnderStrip("the S22's insets")
        rig.leaveNavigationView()
        assertUnderStrip("Re-center showing")
    }
}
