package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.AvailabilityEntry
import com.zynergylabs.forager.app.domain.model.AvailabilityForecast
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RECORD -691 (the owner, RECORD -651: "Let taps through at once (Recommended)"): a leaving map pop-up under 48 dp tall lets a
 * long-press through to the map. The taxon chip is 32 dp tall. Before -691, Compose's minimum touch target (48 dp) widened a small
 * node's hit area past the clip `leavingTakesNoTouches` relies on (motion Part 3's build found this on a leaving list card), so a
 * touch on, or just beside, a leaving chip could still be handed to it rather than to the map.
 *
 * **What this test does not show (found at the build, by revert check R15).** It passes with the -691 change turned off as well,
 * so it is a pin on the behaviour, not evidence for that change. Over the map, a near hit on a small leaving pop-up loses to the
 * map's own direct hit beneath it, so the gap never reaches the map; it bit only where nothing beneath takes the touch directly
 * (a list's background, the Entries card in ListMotionTest, revert check R16). The -691 change on the map pop-ups is kept as a
 * guard for consistency, with that recorded.
 *
 * Through the real [AvailabilityScreen]: "View on Map" from the List tab shows the chip, a real tap on its clear button makes it
 * leave, and real long-presses are made where it still is, with the clock stopped (state applied before frames are stepped).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class SmallLeavingChipTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private var longPresses = 0

    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
        Box(modifier.testTag(LAYOUT_FIXES_MAP_TAG).pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }) })
    }

    @After
    fun restore() {
        composeRule.mainClock.autoAdvance = true
    }

    private fun setScreen() {
        composeRule.setContent {
            ForagerTheme {
                LayoutFixesScreen(
                    uiState = LAYOUT_FIXES_FIX_STATE.copy(
                        sightings = List(4) { sighting(it) },
                        forecast = forecast(),
                        selectedMonth = LocalDate.now().monthValue,
                    ),
                    mapSlot = map,
                )
            }
        }
        settle()
        // "View on Map" from a List-tab row, the chip's real entry point.
        composeRule.onNodeWithText("List").performClick()
        settle()
        composeRule.onNodeWithText(LABEL).performClick()
        settle()
    }

    private fun settle() {
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun root() = composeRule.onAllNodes(isRoot()).onFirst()

    private fun px(p: DpOffset) = with(composeRule.density) { Offset(p.x.toPx(), p.y.toPx()) }

    private fun longPressAt(p: DpOffset) {
        root().performTouchInput { down(px(p)) }
        composeRule.mainClock.advanceTimeBy(1_000)
        root().performTouchInput { up() }
    }

    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a leaving taxon chip under 48 dp lets a long-press on it, or just beside it, reach the map`() {
        setScreen()
        assertTrue("the chip shows", shown(CHIP_TAG))
        val chip = composeRule.onNodeWithTag(CHIP_TAG).getUnclippedBoundsInRoot()
        assertTrue("the chip is under 48 dp tall: ${chip.bottom - chip.top}", chip.bottom - chip.top < 48.dp)
        val clear = composeRule.onNodeWithTag(CLEAR_TAG).getUnclippedBoundsInRoot()
        val points = listOf(
            // On the chip's label, well clear of its clear button.
            DpOffset(chip.left + 16.dp, (chip.top + chip.bottom) / 2),
            // Just below it, inside the 48 dp a small control's touch area is widened to.
            DpOffset(chip.left + 16.dp, chip.bottom + 4.dp),
        )
        for ((index, point) in points.withIndex()) {
            if (index > 0) {
                // Show it again for the next point.
                composeRule.onNodeWithText("List").performClick()
                settle()
                composeRule.onNodeWithText(LABEL).performClick()
                settle()
            }
            composeRule.mainClock.autoAdvance = false
            root().performTouchInput { click(px(DpOffset((clear.left + clear.right) / 2, (clear.top + clear.bottom) / 2))) }
            composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
            repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
            assertTrue("point $index: the chip is still on screen, leaving", shown(CHIP_TAG))
            val before = longPresses
            longPressAt(point)
            settle()
            assertEquals("point $index at $point: a long-press on the leaving chip reached the map", before + 1, longPresses)
        }
    }

    private companion object {
        const val CHIP_TAG = "map-taxon-filter-chip"
        const val CLEAR_TAG = "map-taxon-filter-clear"
        const val LABEL = "artist's bracket"

        fun sighting(index: Int) = Sighting(
            observationId = index.toLong(),
            taxonId = 48473L,
            scientificName = "Ganoderma applanatum",
            commonName = LABEL,
            lat = LAYOUT_FIXES_REGION.lat + index * 0.001,
            lng = LAYOUT_FIXES_REGION.lng + index * 0.001,
            observedOn = LocalDate.of(2025, 8, 14),
            photoUrl = null,
        )

        fun forecast() = AvailabilityForecast(
            region = LAYOUT_FIXES_REGION,
            month = 8,
            filter = TaxonFilter.FUNGI,
            entries = listOf(
                AvailabilityEntry(
                    species = SpeciesObservationCount(
                        taxonId = 48473L,
                        scientificName = "Ganoderma applanatum",
                        commonName = LABEL,
                        rank = "species",
                        observationCount = 14,
                        photoUrl = null,
                        wikipediaUrl = null,
                    ),
                    relativeLikelihood = 1.0f,
                ),
            ),
        )
    }
}
