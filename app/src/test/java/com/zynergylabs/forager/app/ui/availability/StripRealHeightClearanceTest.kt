package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.JOURNAL_ENTRIES_CHIP_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_CLOSE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplay

/**
 * RECORD -709 (the owner: "Follow the strip's real height (Recommended)"). Back by's three-dot button (dispatch
 * 2026-09-28-645) gave the compass strip a 36 dp floor, twice the one text line everything below it used to assume, so
 * the search dropdown and the icon bar's drag limit reached up over the strip's lower half (measured on the first back-by
 * build: dropdown top 67 dp, bar top 71 dp, strip bottom 85 dp at 360 dp). Each thing placed below the strip now clears its
 * measured height. Through the real [AvailabilityScreen] ([MapLayersTestScreen]) with a stub map that takes real touches;
 * each check compares a control's own unclipped bounds with the strip's.
 *
 * Portrait: each one starts at or below the strip's bottom. Landscape: the strip sits in the other top corner from the
 * search bar and the L, so each one is checked not to overlap the strip's rectangle.
 */
abstract class StripRealHeightClearanceTests(private val rotation: Int, private val portrait: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val glyphs = mutableStateListOf<StubGlyph>()
    private val map = BubbleMapSlot(glyphs)

    private fun setScreen(withJournalChip: Boolean = false) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        var rotationSeen: Int? = null
        val viewModel = mapLayersViewModel()
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = AbsentForecastCellStore,
                waypoints = listOf(BUBBLE_WAYPOINT),
                cartographyUiState = if (withJournalChip) LAYOUT_FIXES_SHOWN_ENTRY_STATE else com.zynergylabs.forager.app.ui.log.CartographyUiState(),
            )
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("the screen must see the pinned rotation", rotation, rotationSeen)
    }

    private fun tag(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun strip(): DpRect {
        val strip = tag(STRIP_TAG)
        // The premise: the strip is the three-dot button's 36 dp, taller than one text line (about 16 dp).
        assertEquals("the strip is the button's 36 dp: ${strip.describe()}", 36f, (strip.bottom - strip.top).value, 0.5f)
        return strip
    }

    private fun assertClearOfStrip(what: String, box: DpRect, strip: DpRect) {
        if (portrait) {
            assertTrue("$what ${box.describe()} starts at or below the strip's bottom ${strip.describe()}", box.top >= strip.bottom - 0.5.dp)
        } else {
            assertFalse("$what ${box.describe()} does not overlap the strip ${strip.describe()}", box.overlapsRect(strip))
        }
    }

    @Test
    fun `the search dropdown clears the strip's real height`() {
        setScreen()
        val strip = strip()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performClick()
        composeRule.waitForIdle()
        assertClearOfStrip("the search dropdown", tag(SEARCH_DROPDOWN_TAG), strip)
    }

    @Test
    fun `the icon bar cannot be dragged over the strip's real height`() {
        setScreen()
        val strip = strip()
        val handle = tag("map-icon-bar-minimize-handle")
        composeRule.longPressDrag((handle.left + handle.right) / 2, (handle.top + handle.bottom) / 2, 0.dp, (-2000).dp)
        assertClearOfStrip("the icon cluster", tag(MAP_ICON_CLUSTER_TAG), strip)
        assertClearOfStrip("the bar's top row", composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot(), strip)
    }

    @Test
    fun `the journal chip clears the strip's real height`() {
        setScreen(withJournalChip = true)
        val strip = strip()
        assertTrue("the chip is shown", composeRule.onAllNodesWithTag(JOURNAL_ENTRIES_CHIP_TAG).fetchSemanticsNodes().isNotEmpty())
        assertClearOfStrip("the journal chip", tag(JOURNAL_ENTRIES_CHIP_TAG), strip)
    }

    /**
     * The bubble's minY. A card opens above its glyph unless that would put its top above minY, when it opens below. So the
     * glyph is placed where its card, opened above, would have its top 3 dp above the strip's bottom (the card's layout box
     * starts about 12 dp above the card itself, measured: a top 9 dp up already fell under the old one-line minY): under the old one-line
     * clearance that was allowed, and the card covered the strip's lower part; under the strip's real height it opens below
     * the glyph instead. The card's height above its glyph is measured first, on a glyph in mid map.
     */
    @Test
    fun `a bubble that would open over the strip's lower half opens clear of the strip's real height`() {
        setScreen()
        val strip = strip()
        val slot = tag("map-slot")
        val x = (slot.left + slot.right) / 2
        fun placeAndOpen(y: Dp, atX: Dp = x) {
            glyphs.clear()
            glyphs.add(StubGlyph(MapLayerIds.WAYPOINTS, "wp-1", atX - slot.left, y - slot.top, LatLng(45.326, -122.634)))
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(glyphTag("wp-1")).performTouchInput { click(center) }
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(500)
            composeRule.waitForIdle()
        }
        if (!portrait) {
            // Landscape: the window is too short for a card above a glyph in mid map, so the minY case cannot be set up;
            // a glyph just under the strip, at its centre, is checked to open clear of it. Unchanged by -709 (the strip is
            // not measured in landscape), so this is a pin, not a proof of the change.
            placeAndOpen(strip.bottom + 4.dp, (strip.left + strip.right) / 2)
            assertClearOfStrip("the bubble's card", tag(MAP_BUBBLE_TAG), strip)
            return
        }
        val midY = (slot.top + slot.bottom) / 2
        placeAndOpen(midY)
        val reference = tag(MAP_BUBBLE_TAG)
        assertTrue("mid map, the card opens above its glyph: ${reference.describe()}", reference.bottom <= midY)
        val above = midY - reference.top
        composeRule.onNodeWithTag(MAP_BUBBLE_CLOSE_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        placeAndOpen(strip.bottom - 3.dp + above)
        assertClearOfStrip("the bubble's card", tag(MAP_BUBBLE_TAG), strip)
    }

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class StripRealHeightClearance360Test : StripRealHeightClearanceTests(Surface.ROTATION_0, portrait = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi")
class StripRealHeightClearanceLandscapeTest : StripRealHeightClearanceTests(Surface.ROTATION_90, portrait = false)

