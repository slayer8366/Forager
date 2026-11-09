package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.zynergylabs.forager.app.ui.map.CENTRE_PIN_CONFIRM_ROW_TAG
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerDark
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerLight
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-06, Phase 2, B1, in a short landscape window, where the navigation card is a card of its own on the screen's
 * bottom edge beside the rail (RECORD -953) and the centre-pin row clears the rail. Read as SeeThroughEdgePixelsTest reads the
 * portrait windows; see that class for the claim. The side against the rail carries no line of its own: the rail draws the one line
 * there (RECORD -967, -990 for the card; -2155 for the row, "In the rail layout its port side is the rail's line").
 */
abstract class SeeThroughEdgePixelsLandscapeTests(private val dark: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val rig = EdgeShadeRig(composeRule, dark)

    private val token get() = if (dark) SurfaceContainerDark else SurfaceContainerLight

    private fun read(name: String, pixels: PixelMap, tag: String, role: Color, under: Color, specs: List<EdgeSpec>) {
        assertEdges(name, readEdges(pixels, rig.density, rig.bounds(tag), dark, role, { _, _ -> under }, specs))
    }

    /**
     * Whether the rail is on the screen's left, so a window's left side is the one against it. Read from the rail's own bounds: the
     * first run of this class (42116693802) took it from where the window sat, which is right for the narrow card beside the rail and
     * wrong for the wide centre-pin row, whose middle lies away from the rail.
     */
    private fun railOnLeft(): Boolean {
        val rail = rig.bounds(COMPACT_NAVIGATION_RAIL_TAG)
        val screen = composeRule.onRoot().getUnclippedBoundsInRoot()
        return (rail.left + rail.right) / 2 < (screen.left + screen.right) / 2
    }

    /**
     * The navigation card, reached by a real touch on the return button: its top and its side facing the map outlined, its rail side
     * left to the rail's line, its bottom on the screen's edge with none (RECORD -967; CompactMapLandscapeNavigationCard.kt).
     */
    @Test
    fun `the landscape navigation card is the 0_8 composite from 2 dp inside all four edges, outlined where the rulings say`() {
        rig.setScreen()
        rig.touchTag(BAR_RETURN)
        val pixels = rig.capture("navigation-card-landscape")
        val under = rig.reference(pixels, 0.5f, 0.62f, EDGE_SHADE_MAP_GREY)
        val railLeft = railOnLeft()
        read(
            "the landscape navigation card", pixels, NAVIGATION_HUD_TAG, token, under,
            listOf(
                EdgeSpec(EdgeSide.LEFT, 0.6f, 2, 3, line = !railLeft),
                EdgeSpec(EdgeSide.TOP, 0.5f, 2, 3, line = true),
                EdgeSpec(EdgeSide.RIGHT, 0.6f, 2, 3, line = railLeft),
                EdgeSpec(EdgeSide.BOTTOM, 0.25f, 2, 3, line = false),
            ),
        )
    }

    /** RECORD -2155: the centre-pin row beside the rail: its top and its outer side outlined, its rail side and its bottom open. */
    @Test
    fun `the centre-pin row beside the rail is the 0_8 composite from 2 dp inside all four edges, its rail side left to the rail`() {
        rig.setScreen()
        rig.touchDescribed(BAR_ADD)
        rig.touchText("Waypoint")
        val pixels = rig.capture("centre-pin-row-landscape")
        val under = rig.reference(pixels, 0.5f, 0.62f, EDGE_SHADE_MAP_GREY)
        val railLeft = railOnLeft()
        read(
            "the centre-pin row beside the rail", pixels, CENTRE_PIN_CONFIRM_ROW_TAG, token, under,
            listOf(
                EdgeSpec(EdgeSide.LEFT, 0.5f, 2, 12, line = !railLeft),
                EdgeSpec(EdgeSide.TOP, 0.5f, 2, 6, line = true),
                EdgeSpec(EdgeSide.RIGHT, 0.5f, 2, 12, line = railLeft),
                EdgeSpec(EdgeSide.BOTTOM, 0.5f, 2, 6, line = false),
            ),
        )
    }
}

/** The S22 in landscape (823 x 384 dp), as AttributionUnderCardLandscapeTest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeeThroughEdgePixelsLandscapeLightTest : SeeThroughEdgePixelsLandscapeTests(dark = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeeThroughEdgePixelsLandscapeDarkTest : SeeThroughEdgePixelsLandscapeTests(dark = true)
