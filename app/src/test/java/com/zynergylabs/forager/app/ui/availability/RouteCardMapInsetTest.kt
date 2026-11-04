package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * D24 (dispatch 2026-10-30-08, RECORD -1966): the credit strip is off the map, so nothing rises for the route card by the map's own
 * bottom inset any more. D13 (landscape, RECORD -1955) and D18 (portrait, -1958) raised [MapRenderMode.bottomInset] wherever the card
 * covered the credit's resting place; both are removed. The real Maps tab, recording, the card plotted to 1, 3 and 6 stops, peek and
 * expanded: the inset the map is handed is the one it had with no card, at every step. The "i" had its own rise until D30
 * (RECORD -1980) removed it; it now hides under a card instead (AttributionUnderCardTest), which this does not read.
 */
abstract class RouteCardMapInsetTests(private val rotation: Int?) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val rig = RouteCardBarRig(composeRule, credit = S22_MAP_CREDIT)

    private fun walk(where: String) {
        val rest = rig.map.renderMode?.bottomInset
        listOf(1, 3, 6).forEach { n ->
            listOf(false, true).forEach { expanded ->
                rig.plotTo(n)
                rig.setExpanded(expanded)
                assertEquals("$where, $n stops, ${if (expanded) "expanded" else "peek"}: the map's bottom inset", rest, rig.map.renderMode?.bottomInset)
            }
        }
    }

    private fun start() {
        rotation?.let { Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(it) }
        rig.setScreen()
    }

    @Test
    fun `the bar on its first side, the card leaves the map's bottom inset as it was`() {
        start()
        walk("the bar on its first side")
    }

    @Test
    fun `the bar dragged across, the card leaves the map's bottom inset as it was`() {
        start()
        rig.dragBarAcross(rightward = rotation != null)
        walk("the bar dragged across")
    }
}

/** Portrait, the S22's window: where D18 raised the inset (D15: the card on the credit by 4 dp at font 1.0). */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 1.0f)
class RouteCardMapInsetPortraitTest : RouteCardMapInsetTests(null)

/** The short landscape window: where D13 raised the inset (the card in the rail's corner over the credit). */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 1.0f)
class RouteCardMapInsetShortLandscapeTest : RouteCardMapInsetTests(Surface.ROTATION_90)
