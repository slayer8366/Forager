package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.DpRect
import com.zynergylabs.forager.app.ui.map.MAP_LAYERS_SHEET_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Item 9 (dispatch `2026-09-28-104`, continuation `2026-09-28-142`; the owner: "have the map street/topo/satellite chips be centered between
 * the panel sides. The height position on the panel is fine as is."): the Layers sheet's map-type chip row is centred between the sheet's
 * sides, and its height is what it was. Through the real [AvailabilityScreen] and a real touch on the cluster's Layers row. The row's bounds
 * are the chips' own, first to last; the sheet's are the sheet node's.
 */
internal abstract class LayersChipsTests(private val baseChipTopDp: Float) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(emptyList())
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    private fun openSheet() {
        composeRule.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription("Layers:", substring = true)).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun chip(label: String): DpRect = composeRule.onNodeWithText(label).getUnclippedBoundsInRoot()

    /**
     * The box the Row actually lays [label]'s chip out in: its minimum touch target. A chip narrower than 48 dp is
     * measured 48 dp wide by `minimumInteractiveComponentSize` and drawn centred in that box, while its semantic bounds
     * ([chip]) are the drawn chip only. The box's width is measured (the chip's layout node, `layoutInfo.width`); its left
     * edge is the drawn chip's left less half the padding, because the minimum-size modifier centres the chip in it.
     */
    private fun laidOutBox(label: String): ClosedFloatingPointRange<Float> {
        val node = composeRule.onNodeWithText(label).fetchSemanticsNode()
        val drawn = chip(label)
        val boxWidthDp = with(composeRule.density) { node.layoutInfo.width.toDp().value }
        val left = drawn.left.value - (boxWidthDp - (drawn.right.value - drawn.left.value)) / 2f
        return left..(left + boxWidthDp)
    }

    /**
     * Two checks, because semantic bounds alone are off-centre by design (RECORD -719). Two chips of unequal width get
     * unequal minimum-touch padding: under Robolectric's near-zero-width fonts Street is 39 dp and Topographical 46 dp
     * wide, so their 48 dp boxes pad them 4.5 dp and 1 dp a side. The row the Row centres is the boxes', 362.5 to 462 dp
     * in landscape, centre 412.25 against the sheet's 412; the drawn chips span 367 to 461, centre 414. This test passed
     * on the drawn chips while the row ended in Satellite (dispatch 2026-09-28-708 removed it), whose padding happened to
     * balance Street's.
     *
     * So the row of laid-out boxes is held to 1 dp, the centring the Row is asked for, and the drawn chips' own centre to
     * 2 dp, so a real centring bug still fails here: the padding cannot move what is drawn further than that.
     */
    @Test
    fun `the map-type chips are centred between the sheet's sides`() {
        openSheet()
        val sheet = composeRule.onNodeWithTag(MAP_LAYERS_SHEET_TAG).getUnclippedBoundsInRoot()
        val sheetCentre = (sheet.left.value + sheet.right.value) / 2f
        // Three chips since dispatch 2026-10-22-01: Trail first, OSM last (was Street and Topographical).
        val firstBox = laidOutBox("Trail")
        val lastBox = laidOutBox("OSM")
        assertEquals(
            "the row of laid-out chip boxes [${firstBox.start}, ${lastBox.endInclusive}] is centred on the sheet [${sheet.left.value}, ${sheet.right.value}]",
            sheetCentre,
            (firstBox.start + lastBox.endInclusive) / 2f,
            1f,
        )
        val first = chip("Trail")
        val last = chip("OSM")
        assertEquals(
            "the drawn chips [${first.left.value}, ${last.right.value}] are centred on the sheet [${sheet.left.value}, ${sheet.right.value}]",
            sheetCentre,
            (first.left.value + last.right.value) / 2f,
            2f,
        )
    }

    @Test
    fun `guard - the map-type chips' top is where it was`() {
        openSheet()
        assertEquals("the chip row's top", baseChipTopDp, chip("Trail").top.value, 0.5f)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
// 275 dp: the chip row's top at base (`a3221b4`'s tree plus the earlier items), read from the base run, not derived.
internal class LayersChipsPortraitTest : LayersChipsTests(baseChipTopDp = 275f)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
// 142 dp, read the same way.
internal class LayersChipsLandscapeTest : LayersChipsTests(baseChipTopDp = 142f)
