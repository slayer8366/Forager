package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.MapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.MapChromeEdges
import com.zynergylabs.forager.app.ui.map.MapChromeEdgesDrawn
import com.zynergylabs.forager.app.ui.map.MapChromeOutlineSides
import com.zynergylabs.forager.app.ui.map.MapChromeShape
import com.zynergylabs.forager.app.ui.theme.DarkColors
import com.zynergylabs.forager.app.ui.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowDisplay
import org.robolectric.annotation.Config

/*
 * Dispatch 2026-10-17-09 (RECORD -1224, -1225; the owner: "The updated UI still needs to be applied to the Journal, the
 * Seasonal, and the List tab, as they all still carry the old UI"). On List, Seasonal and Journal the search line is the Maps
 * tab's rounded, outlined pill and the bottom bar (the rail in landscape) has the Maps tab's rounded corners and outline, all
 * solid, since no map is behind them (owner: "In the places that aren't covering a map, they can stay solid"). The Maps tab's
 * own values are asserted unchanged.
 *
 * Driven through the real screen (`AvailabilityScreen`, the app's theme), reaching each tab by its own navigation item, and read
 * from the drawing parameters each piece carries beside its test tag: the shape it is clipped or filled to (`MapChromeShape`),
 * the outline it draws (`MapChromeEdges`, set from the same arguments `mapChromeEdges` draws with), and its fill
 * (`MapChromeContainerColor`). Robolectric reports zero insets (CLAUDE.md, "Known pitfalls"), so where the pieces sit against the
 * real system bars is the device check's, not this file's.
 *
 * Landscape (RECORD -1227, "Move to Maps' spot"; "The layout resizing between tabs looks like a UI glitch"; then RECORD -1238,
 * "Yes, at the left (Recommended)", after a centred build passed over nothing but suited no floating keyboard): the pill on
 * the three tabs is the Maps pill's size, its start just clear of the navigation bar, which with no cut-out on the rail's side
 * is the Maps pill's own place. So every piece keeps identical bounds across the switch here, in portrait and in landscape.
 */

private typealias ShapeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

private val R = MAP_CARD_CORNER_RADIUS
private val ALL_SIDES = MapChromeOutlineSides(left = true, top = true, right = true, bottom = true)

/** The Maps tab's pill, and what the three tabs' pill is to match: all four corners rounded, outlined all round. */
private val PILL_EDGES = MapChromeEdgesDrawn(R, R, R, R, ALL_SIDES)

/** The Maps tab's bottom bar, and the three tabs': the top two corners rounded; outlined on its top and sides, not its bottom. */
private val BAR_SHAPE: Shape = RoundedCornerShape(topStart = R, topEnd = R)
private val BAR_EDGES = MapChromeEdgesDrawn(R, R, 0.dp, 0.dp, MapChromeOutlineSides(left = true, top = true, right = true, bottom = false))

/** The Maps tab's rail, by its port edge: the two corners facing away from the port edge rounded and outlined there. */
private val RAIL_EDGES_PORT_LEFT = MapChromeEdgesDrawn(0.dp, R, R, 0.dp, MapChromeOutlineSides(left = false, top = true, right = true, bottom = true))
private val RAIL_EDGES_PORT_RIGHT = MapChromeEdgesDrawn(R, 0.dp, 0.dp, R, MapChromeOutlineSides(left = true, top = true, right = false, bottom = true))

private fun ShapeRule.oneNode(tag: String): SemanticsNode {
    val nodes = onAllNodes(hasTestTag(tag) and SemanticsMatcher.keyIsDefined(MapChromeContainerColor), useUnmergedTree = true).fetchSemanticsNodes()
    assertEquals("one node tagged $tag", 1, nodes.size)
    return nodes.single()
}

private fun SemanticsNode.shape(): Shape? = config.getOrElseNullable(MapChromeShape) { null }
private fun SemanticsNode.edges(): MapChromeEdgesDrawn? = config.getOrElseNullable(MapChromeEdges) { null }
private fun SemanticsNode.fill(): Color = config[MapChromeContainerColor]

abstract class TabsMapChromeShapeTests(
    private val dark: Boolean,
    private val landscape: Boolean,
    /** The display's rotation in landscape (which edge the port and the punch hole are on); null in portrait. */
    private val rotation: Int? = null,
    /**
     * A system navigation bar on the screen's left, dispatched to the screen as real window insets after it is up
     * (Robolectric's own are zero, CLAUDE.md "Known pitfalls"). The S22's three-button bar sits on the left in one landscape
     * direction; the pill passed over it there (the owner, PR #252, build 1.0.3414).
     */
    private val navBarLeft: Dp = 0.dp,
) {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(emptyList())
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    private val navigationTag get() = if (landscape) COMPACT_NAVIGATION_RAIL_TAG else COMPACT_BOTTOM_NAV_TAG
    private val pillRole get() = if (dark) DarkColors.surfaceContainerHighest else LightColors.surfaceContainerLowest

    private fun setScreen() {
        if (rotation != null) Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent { MapChromeTestScreen(state, map, roles, darkTheme = dark) }
        composeRule.waitForIdle()
        if (navBarLeft > 0.dp) {
            val px = (navBarLeft.value * composeRule.activity.resources.displayMetrics.density).toInt()
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(px, 0, 0, 0))
                .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
                .build()
            composeRule.runOnUiThread {
                ViewCompat.dispatchApplyWindowInsets(composeRule.activity.window.decorView.findViewById(android.R.id.content), insets)
            }
            composeRule.waitForIdle()
            // The positive control for the injection itself: the Maps pill, which pads by the navigation bar
            // (mapControlsPadding), must actually see the inset this class is about.
            assertTrue("the injected navigation bar inset reached the screen's own Maps pill (left ${bounds(SEARCH_ENTRY_BAR_TAG).left})", bounds(SEARCH_ENTRY_BAR_TAG).left.value >= navBarLeft.value - 0.5f)
        }
    }

    /** Chooses [label] on the bottom bar or rail (a semantic click: it only gets the test to the tab). */
    private fun open(label: String) {
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(navigationTag)), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        // Journal redesign J5 (restored by RECORD -1242, "Journal back to J5"): in a landscape window the Journal hides the search
        // header until its own search icon brings it up.
        if (landscape && label == "Journal") {
            composeRule.onNodeWithTag("journal-short-search", useUnmergedTree = true).performTouchInput { click(center) }
            composeRule.waitForIdle()
        }
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertFill(what: String, node: SemanticsNode, role: Color, alpha: Float) {
        assertEquals("$what: fill alpha", alpha, node.fill().alpha, 0.002f)
        assertEquals("$what: fill colour aside from alpha", role.copy(alpha = 1f), node.fill().copy(alpha = 1f))
    }

    private fun assertMapsRailEdges(node: SemanticsNode) {
        val edges = node.edges()
        assertTrue("Maps rail: outline is one of the two port-edge forms, was $edges", edges == RAIL_EDGES_PORT_LEFT || edges == RAIL_EDGES_PORT_RIGHT)
    }

    // ── The Maps tab, unchanged ──

    @Test
    fun `on Maps the pill and the bar or rail keep their shape, outline and 0_8 fill`() {
        setScreen()
        if (!landscape) {
            val pill = composeRule.oneNode(SEARCH_ENTRY_BAR_TAG)
            assertEquals("Maps pill: shape", MapCardCorners.ALL.shape(), pill.shape())
            assertEquals("Maps pill: outline", PILL_EDGES, pill.edges())
            assertFill("Maps pill", pill, pillRole, MAP_CHROME_OVER_MAP_ALPHA)
            val bar = composeRule.oneNode(COMPACT_BOTTOM_NAV_TAG)
            assertEquals("Maps bar: shape", BAR_SHAPE, bar.shape())
            assertEquals("Maps bar: outline", BAR_EDGES, bar.edges())
            assertFill("Maps bar", bar, roles.token, MAP_CHROME_OVER_MAP_ALPHA)
        } else {
            val pill = composeRule.oneNode(SEARCH_ENTRY_BAR_TAG)
            assertFill("Maps landscape pill", pill, pillRole, MAP_CHROME_OVER_MAP_ALPHA)
            assertNotNull("Maps landscape pill: outlined", pill.edges())
            val rail = composeRule.oneNode(COMPACT_NAVIGATION_RAIL_TAG)
            assertMapsRailEdges(rail)
            assertFill("Maps rail", rail, roles.token, MAP_CHROME_OVER_MAP_ALPHA)
        }
    }

    // ── The search pill on the three tabs ──

    private fun checkPill(label: String) {
        setScreen()
        open(label)
        val pill = composeRule.oneNode(SEARCH_ENTRY_BAR_TAG)
        assertEquals("$label pill: shape, the Maps pill's", MapCardCorners.ALL.shape(), pill.shape())
        assertEquals("$label pill: outline, the Maps pill's", PILL_EDGES, pill.edges())
        assertFill("$label pill", pill, pillRole, 1f)
        assertTrue(
            "$label pill: no divider under it",
            composeRule.onAllNodes(hasTestTag(SEARCH_ENTRY_BAR_DIVIDER_TAG), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test fun `on List the search line is the Maps pill, solid, with no divider`() = checkPill("List")
    @Test fun `on Seasonal the search line is the Maps pill, solid, with no divider`() = checkPill("Seasonal")
    @Test fun `on Journal the search line is the Maps pill, solid, with no divider`() = checkPill("Journal")

    // ── The bottom bar or rail on the three tabs ──

    private fun checkNavigation(label: String) {
        setScreen()
        val mapsNav = composeRule.oneNode(navigationTag)
        val mapsShape = mapsNav.shape()
        val mapsEdges = mapsNav.edges()
        open(label)
        val nav = composeRule.oneNode(navigationTag)
        if (landscape) {
            assertEquals("$label rail: shape, the Maps rail's", mapsShape, nav.shape())
            assertEquals("$label rail: outline, the Maps rail's", mapsEdges, nav.edges())
            assertMapsRailEdges(nav)
        } else {
            assertEquals("$label bar: shape, the Maps bar's", BAR_SHAPE, nav.shape())
            assertEquals("$label bar: outline, the Maps bar's", BAR_EDGES, nav.edges())
        }
        assertFill("$label ${if (landscape) "rail" else "bar"}", nav, roles.token, 1f)
    }

    @Test fun `on List the bar or rail has the Maps shape and outline, solid`() = checkNavigation("List")
    @Test fun `on Seasonal the bar or rail has the Maps shape and outline, solid`() = checkNavigation("Seasonal")
    @Test fun `on Journal the bar or rail has the Maps shape and outline, solid`() = checkNavigation("Journal")

    // ── No jump across the tab switch ──

    private fun checkNoJump(label: String) {
        setScreen()
        val mapsNav = bounds(navigationTag)
        val mapsPill = bounds(SEARCH_ENTRY_BAR_TAG)
        open(label)
        assertBoundsEqual("$label: the ${if (landscape) "rail" else "bar"} against Maps", mapsNav, bounds(navigationTag))
        // Landscape, RECORD -1238 (the owner on build 1.0.3416: "can it move a bit to the left to accommodate floating
        // keyboards?", then "Yes, at the left (Recommended)"): the Maps pill's size, its start just clear of the navigation bar
        // (navBarLeft; Robolectric's own insets are zero). With no cut-out on the rail's side that is the Maps pill's own place,
        // so its bounds are compared whole, in portrait and landscape alike.
        val pill = bounds(SEARCH_ENTRY_BAR_TAG)
        assertBoundsEqual("$label: the pill against Maps", mapsPill, pill)
        assertTrue("$label pill: clear of the navigation bar (left ${pill.left} >= $navBarLeft)", pill.left.value >= navBarLeft.value - 0.5f)
        open("Maps")
        assertBoundsEqual("back on Maps from $label: the bar or rail", mapsNav, bounds(navigationTag))
        assertBoundsEqual("back on Maps from $label: the pill", mapsPill, bounds(SEARCH_ENTRY_BAR_TAG))
    }

    private fun assertBoundsEqual(what: String, expected: DpRect, actual: DpRect) {
        val tolerance = 0.5f
        assertEquals("$what: left", expected.left.value, actual.left.value, tolerance)
        assertEquals("$what: top", expected.top.value, actual.top.value, tolerance)
        assertEquals("$what: right", expected.right.value, actual.right.value, tolerance)
        assertEquals("$what: bottom", expected.bottom.value, actual.bottom.value, tolerance)
    }

    // ── The search panel opened from the pill ──

    /**
     * RECORD -1238 (the owner: "Yes, both (Recommended)", the planner having seen the page's text read through the panel in the
     * owner's floating-keyboard screenshot): off the map the panel is solid (owner: "In the places that aren't covering a map,
     * they can stay solid"); over the map it stays at 0.8. Opened by a real touch on the pill's field.
     */
    private fun checkPanelFill(label: String) {
        setScreen()
        if (label != "Maps") open(label)
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG, useUnmergedTree = true).performTouchInput { click(center) }
        composeRule.waitForIdle()
        val panel = composeRule.oneNode(SEARCH_DROPDOWN_TAG)
        assertFill("$label search panel", panel, roles.token, if (label == "Maps") MAP_CHROME_OVER_MAP_ALPHA else 1f)
    }

    @Test fun `on Maps the search panel stays at 0_8`() = checkPanelFill("Maps")
    @Test fun `on List the search panel is solid`() = checkPanelFill("List")
    @Test fun `on Seasonal the search panel is solid`() = checkPanelFill("Seasonal")
    @Test fun `on Journal the search panel is solid`() = checkPanelFill("Journal")

    @Test fun `Maps to List and back, the pill and the bar or rail do not move`() = checkNoJump("List")
    @Test fun `Maps to Seasonal and back, the pill and the bar or rail do not move`() = checkNoJump("Seasonal")
    @Test fun `Maps to Journal and back, the pill and the bar or rail do not move`() = checkNoJump("Journal")
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class TabsMapChromeShapePortraitDarkTest : TabsMapChromeShapeTests(dark = true, landscape = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class TabsMapChromeShapePortraitLightTest : TabsMapChromeShapeTests(dark = false, landscape = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class TabsMapChromeShapeLandscape90DarkTest : TabsMapChromeShapeTests(dark = true, landscape = true, rotation = Surface.ROTATION_90)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class TabsMapChromeShapeLandscape270DarkTest : TabsMapChromeShapeTests(dark = true, landscape = true, rotation = Surface.ROTATION_270)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class TabsMapChromeShapeLandscape90LightTest : TabsMapChromeShapeTests(dark = false, landscape = true, rotation = Surface.ROTATION_90)

/** The owner's S22 case (PR #252, build 1.0.3414): a three-button navigation bar on the left in landscape, 48 dp. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class TabsMapChromeShapeLandscapeNavBarLeftTest : TabsMapChromeShapeTests(dark = true, landscape = true, rotation = Surface.ROTATION_270, navBarLeft = 48.dp)
