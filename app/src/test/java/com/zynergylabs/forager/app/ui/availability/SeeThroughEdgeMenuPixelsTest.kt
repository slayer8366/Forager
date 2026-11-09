package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_ENTRY_COUNT_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_ENTRY_LIST_TAG
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerDark
import com.zynergylabs.forager.app.ui.theme.SurfaceContainerLight
import java.time.LocalDate
import kotlin.math.roundToInt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-06, Amendment 1, C2 (RECORD -2154, the owner: "Thin outline (Recommended)", whose option read "No shadow. A
 * crisp 1 dp line at the edge, the same hairline you chose for the icon bar, bottom bar and header (-966/-967). The window reads an
 * even 80% right to its edge, and the map shows through evenly"; A1 names Material's menus too): the list a map bubble opens from
 * its "In N journal entries" line (MapFeatureBubble.kt, KeptInEntries). It is a Material menu over the map at the map chrome's
 * 0.8, so it has no shadow and carries the map-chrome hairline, through MapChromeMenuEdge.kt as the other menus over the map do.
 *
 * A menu opens in a popup window of its own, so the screen's root capture SeeThroughEdgePixelsTest reads does not show it. Here
 * the whole screen is read instead, every window as it is composited (Robolectric's UiAutomation screenshot, which draws each
 * window with the hardware renderer, shadows included, at its place on the screen). The list lies over the bubble and the map, so
 * each pixel of it is held to its fill at 0.8 over what that same pixel showed before the list opened.
 *
 * Before C2 the list kept Material's own 3 dp shadow (MenuDefaults.ShadowElevation) and no line: its edges read the fill, not the
 * hairline, and the shadow showed through the fill as a band inside them (Phase 1 report, section 2).
 */
abstract class SeeThroughEdgeMenuPixelsTests(private val dark: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    /** Four saved entries shown on the map, each keeping the bubble's waypoint: past three, its bubble names them in one line. */
    private val keeping: List<CartographyEntry> = (0 until 4).map { k ->
        CartographyEntry.draft(id = "entry-$k", date = LocalDate.of(2026, 9, 12).minusDays(k.toLong()), updatedAtEpochMillis = 1_000L).copy(
            isDraft = false,
            waypointDecisions = listOf(WaypointDecision(BUBBLE_WAYPOINT.id, BUBBLE_WAYPOINT.name, BUBBLE_WAYPOINT.lat, BUBBLE_WAYPOINT.lng, kept = true)),
            shownOnMap = true,
        )
    }

    private val rig = EdgeShadeRig(composeRule, dark, keeping)

    /** The list's colour role, the menu container (`journalMenuContainerColor`: the theme's surfaceContainer at 0.8). */
    private val role get() = if (dark) SurfaceContainerDark else SurfaceContainerLight

    /** The whole screen, every window as composited, in screen pixels; saved for the artifact under [name]. */
    private fun screen(name: String): PixelMap {
        composeRule.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: throw AssertionError("no screenshot of the screen: this class needs Robolectric's native graphics")
        val image = bitmap.asImageBitmap()
        saveEdgeCapture("$name-${if (dark) "night" else "day"}", image)
        return image.toPixelMap()
    }

    /**
     * The list's items sit 12 dp in at the sides (MenuDefaults.DropdownMenuItemContentPadding) and its column 8 dp in at the top and
     * bottom, so each edge is read 2 to 6 dp in, at its middle.
     */
    @Test
    fun `a bubble's list of keeping entries over the map is the 0_8 composite from 2 dp inside all four edges, with the hairline at each`() {
        rig.setScreen()
        rig.touchTag(glyphTag(BUBBLE_WAYPOINT.id))
        composeRule.onNodeWithTag(MAP_BUBBLE_ENTRY_COUNT_TAG).assertTextEquals("In 4 journal entries")
        val before = screen("entry-list-before")
        val refX = (before.width * 0.01f).roundToInt()
        val refY = (before.height * 0.75f).roundToInt()
        check(edgeDiff(before[refX, refY], EDGE_SHADE_MAP_GREY) <= EDGE_FILL_TOLERANCE) {
            "the reference point ($refX, $refY) px is not bare map: read ${edgeHex(before[refX, refY])}, want ${edgeHex(EDGE_SHADE_MAP_GREY)}"
        }

        rig.touchTag(MAP_BUBBLE_ENTRY_COUNT_TAG)
        val list = composeRule.onNodeWithTag(MAP_BUBBLE_ENTRY_LIST_TAG, useUnmergedTree = true).fetchSemanticsNode()
        val pixels = screen("entry-list")
        check(edgeDiff(pixels[refX, refY], before[refX, refY]) <= EDGE_FILL_TOLERANCE) {
            "the bare map moved when the list opened: at ($refX, $refY) read ${edgeHex(pixels[refX, refY])}, before ${edgeHex(before[refX, refY])}"
        }

        val d = rig.density
        val at = list.positionOnScreen
        val bounds = DpRect((at.x / d).dp, (at.y / d).dp, ((at.x + list.size.width) / d).dp, ((at.y + list.size.height) / d).dp)
        println("EDGESHADE|entry-list|on screen $bounds")
        assertEdges(
            "the bubble's list of keeping entries",
            readEdges(
                pixels, d, bounds, dark, role, { x, y -> before[x, y] },
                listOf(
                    EdgeSpec(EdgeSide.LEFT, 0.5f, 2, 6, line = true),
                    EdgeSpec(EdgeSide.TOP, 0.5f, 2, 6, line = true),
                    EdgeSpec(EdgeSide.RIGHT, 0.5f, 2, 6, line = true),
                    EdgeSpec(EdgeSide.BOTTOM, 0.5f, 2, 6, line = true),
                ),
            ),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeeThroughEdgeMenuPixelsLightTest : SeeThroughEdgeMenuPixelsTests(dark = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeeThroughEdgeMenuPixelsDarkTest : SeeThroughEdgeMenuPixelsTests(dark = true)
