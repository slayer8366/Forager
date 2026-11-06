package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.AvailabilityEntry
import com.zynergylabs.forager.app.domain.model.AvailabilityForecast
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.ui.map.JOURNAL_ENTRIES_CHIP_TAG
import com.zynergylabs.forager.app.ui.map.JOURNAL_ENTRIES_LIST_TAG
import com.zynergylabs.forager.app.ui.map.MAP_LEGEND_CHIP_TAG
import java.time.LocalDate
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * Part 1 layout fixes, the owner's rulings "1 A / 2 A / 3 D" (planner message `2026-09-28-109`), item 7:
 * in a short landscape window the chip row (the taxon chip and J8's chip together) aligns to the end of
 * the search bar away from the icon cluster's current side, and follows the cluster when it is snapped
 * to the other side. The window is `w823dp-h384dp-land`, the rotation pinned and seen by the screen, and
 * the cluster is moved by the real long-press drag of its handle. Every touch is a real coordinate touch.
 *
 * At `ROTATION_90` the punch-hole (and search bar) side is the left and the cluster's default is the
 * left; since RECORD -956 the same at `ROTATION_270` (was: both the right there). Robolectric reports no cut-out, so the S22's margins are
 * device items.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LayoutFixesChipRowLandscapeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = LayoutFixesMapSlot()

    private fun setScreen(rotation: Int, label: String = SHORT_LABEL, withTaxonChip: Boolean = true, withJournalChip: Boolean = true) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        var rotationSeen: Int? = null
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            LayoutFixesScreen(
                uiState = LAYOUT_FIXES_FIX_STATE.copy(
                    sightings = List(12) { chipRowSighting(it, label) },
                    forecast = chipRowForecast(label),
                    selectedMonth = LocalDate.now().monthValue,
                ),
                mapSlot = map.slot,
                cartographyUiState = if (withJournalChip) LAYOUT_FIXES_SHOWN_ENTRY_STATE else com.zynergylabs.forager.app.ui.log.CartographyUiState(),
            )
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("the screen must see the pinned rotation", rotation, rotationSeen)
        if (!withTaxonChip) return
        // "View on Map" from a List-tab row, the taxon chip's real entry point (as the B2 tests do).
        composeRule.onNodeWithText("List").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(label).performClick()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun tag(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun cluster(): DpRect = tag(MAP_ICON_CLUSTER_TAG)
    private fun searchBar(): DpRect = tag(SEARCH_ENTRY_BAR_TAG)

    /** The taxon chip and J8's chip together: the row's drawn extent (the row draws nothing itself). */
    private fun chipRow(): DpRect {
        val present = listOf("map-taxon-filter-chip", JOURNAL_ENTRIES_CHIP_TAG).filter { composeRule.onAllNodesWithTag(it).fetchSemanticsNodes().isNotEmpty() }.map { tag(it) }
        return DpRect(present.minOf { it.left }, present.minOf { it.top }, present.maxOf { it.right }, present.maxOf { it.bottom })
    }

    private fun snapClusterAcross(dx: Dp) {
        val handle = tag("map-icon-bar-minimize-handle")
        composeRule.longPressDrag((handle.left + handle.right) / 2, (handle.top + handle.bottom) / 2, dx, 0.dp)
    }

    /** The chip row is clear of the cluster, sits at the bar's end away from it, and the reset button takes real touches. */
    private fun assertChipRowAwayFromCluster() {
        val row = chipRow()
        val bar = searchBar()
        val cluster = cluster()
        val mapArea = tag(LAYOUT_FIXES_MAP_TAG)
        val clusterOnLeft = (cluster.left + cluster.right) / 2 < (mapArea.left + mapArea.right) / 2
        assertFalse("the chip row ${row.describe()} and the cluster ${cluster.describe()} do not intersect", row.overlapsRect(cluster))
        val gap = if (clusterOnLeft) row.left - cluster.right else cluster.left - row.right
        assertTrue("the row ${row.describe()} keeps the 8 dp gap from the cluster ${cluster.describe()} (gap $gap)", gap >= 7.5.dp)
        // Planner message 2026-09-29-05: the width cap applies only where the cluster sits under the bar's reach.
        val clusterUnderBar = cluster.right > bar.left && cluster.left < bar.right
        if (clusterUnderBar) {
            val cap = (bar.right - bar.left) - (cluster.right - cluster.left) - 16.dp
            assertTrue("the row ${row.describe()} is at most ${cap.value} dp wide, the bar's width less the cluster's, its edge inset and the gap", (row.right - row.left) <= cap + 0.5.dp)
        }
        if (clusterOnLeft) {
            assertEquals("the cluster is on the left, so the row ${row.describe()} ends at the bar's right end ${bar.describe()}", bar.right.value, row.right.value, 1f)
        } else {
            assertEquals("the cluster is on the right, so the row ${row.describe()} starts at the bar's left end ${bar.describe()}", bar.left.value, row.left.value, 1f)
        }
        assertTrue("the row ${row.describe()} lies within the bar's width ${bar.describe()}", row.left >= bar.left - 0.5.dp && row.right <= bar.right + 0.5.dp)

        val reset = composeRule.onNodeWithContentDescription("Reset orientation to north").getUnclippedBoundsInRoot()
        val before = map.content!!.resetOrientationRequestId
        listOf(0.2f to 0.2f, 0.8f to 0.2f, 0.5f to 0.5f, 0.2f to 0.8f, 0.8f to 0.8f).forEach { (fx, fy) ->
            composeRule.touchAt(reset.left + (reset.right - reset.left) * fx, reset.top + (reset.bottom - reset.top) * fy)
        }
        assertEquals("five real touches across the reset button ${reset.describe()} all reached it", before + 5, map.content!!.resetOrientationRequestId)
    }

    @Test
    fun `T7 at ROTATION_90 with the cluster on its default left side the chip row is at the bar's right end, clear of it`() {
        setScreen(Surface.ROTATION_90)
        assertChipRowAwayFromCluster()
    }

    @Test
    fun `T7 at ROTATION_90 with the cluster snapped to the right the chip row is at the bar's left end`() {
        setScreen(Surface.ROTATION_90)
        snapClusterAcross(500.dp)
        assertChipRowAwayFromCluster()
    }

    // RECORD -956 (the owner: "One should look exactly line the other"): the cluster's default at 270 is the left, as at 90, so the snap that
    // exercises "follows" goes right. Was: default right, `snapClusterAcross((-500).dp)` to the left; under -956 that snap would
    // be a no-op and the second case a copy of the first.
    @Test
    fun `T7 at ROTATION_270 with the cluster on its default left side the chip row is at the bar's right end, clear of it`() {
        setScreen(Surface.ROTATION_270)
        assertChipRowAwayFromCluster()
    }

    @Test
    fun `T7 at ROTATION_270 with the cluster snapped to the right the chip row follows to the bar's left end`() {
        setScreen(Surface.ROTATION_270)
        snapClusterAcross(500.dp)
        assertChipRowAwayFromCluster()
    }

    @Test
    fun `T7 at ROTATION_90 dragging the cluster from left to right moves the chip row from the bar's right end to its left`() {
        setScreen(Surface.ROTATION_90)
        val leftEnd = chipRow().left
        snapClusterAcross(500.dp)
        assertTrue("the row moved to the bar's left end (${chipRow().describe()}, was at $leftEnd)", chipRow().left < leftEnd)
    }

    @Test
    fun `T7 at ROTATION_90 both chips do not fit beside the cluster, so they wrap onto two lines`() {
        setScreen(Surface.ROTATION_90)
        val taxon = tag("map-taxon-filter-chip")
        val journal = tag(JOURNAL_ENTRIES_CHIP_TAG)
        assertTrue("the journal chip ${journal.describe()} is on a second line, under the taxon chip ${taxon.describe()}", journal.top >= taxon.bottom - 0.5.dp)
    }

    @Test
    fun `T7 at ROTATION_90 a long label leaves the row clear of the cluster`() {
        setScreen(Surface.ROTATION_90, LONG_LABEL)
        assertChipRowAwayFromCluster()
    }

    @Test
    fun `T7 at ROTATION_270 a long label leaves the row clear of the cluster`() {
        setScreen(Surface.ROTATION_270, LONG_LABEL)
        assertChipRowAwayFromCluster()
    }

    // RECORD -956 (the owner: "One should look exactly line the other"). Was `snapClusterAcross((-500).dp)`, "snapped left", a no-op under -956.
    @Test
    fun `T7 at ROTATION_270 with the cluster snapped right a long label follows it to the bar's left end`() {
        setScreen(Surface.ROTATION_270, LONG_LABEL)
        snapClusterAcross(500.dp)
        assertChipRowAwayFromCluster()
    }

    /** A real touch at the left of J8's chip opens its list; a real touch on the taxon chip's clear button clears the filter. */
    private fun assertEachChipTakesItsTouches() {
        val journal = tag(JOURNAL_ENTRIES_CHIP_TAG)
        composeRule.touchAt(journal.left + (journal.right - journal.left) * 0.5f, (journal.top + journal.bottom) / 2)
        assertTrue("a real touch on J8's chip ${journal.describe()} opened its list", composeRule.onAllNodesWithTag(JOURNAL_ENTRIES_LIST_TAG).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun `T7 at ROTATION_90 a real touch on J8's chip reaches it`() {
        setScreen(Surface.ROTATION_90)
        assertEachChipTakesItsTouches()
    }

    @Test fun `T7 at ROTATION_270 a real touch on J8's chip reaches it`() {
        setScreen(Surface.ROTATION_270)
        assertEachChipTakesItsTouches()
    }

    /** The window's central third (x and y from 1/3 to 2/3), as the B2 tests' S10 takes it. */
    private fun assertChipRowClearOfCentralThird() {
        val root = composeRule.onAllNodes(androidx.compose.ui.test.isRoot()).onFirst().getUnclippedBoundsInRoot()
        val third = DpRect(
            root.left + (root.right - root.left) / 3, root.top + (root.bottom - root.top) / 3,
            root.left + (root.right - root.left) * 2 / 3, root.top + (root.bottom - root.top) * 2 / 3,
        )
        val row = chipRow()
        assertFalse("the chip row ${row.describe()} does not intersect the central third ${third.describe()}", row.overlapsRect(third))
    }

    @Test fun `S10 at ROTATION_90 with a long label the chip row stays out of the central third`() {
        setScreen(Surface.ROTATION_90, LONG_LABEL)
        assertChipRowClearOfCentralThird()
    }

    @Test fun `S10 at ROTATION_270 with a long label the chip row stays out of the central third`() {
        setScreen(Surface.ROTATION_270, LONG_LABEL)
        assertChipRowClearOfCentralThird()
    }

    // Planner message 2026-09-29-07 (the owner's "Option B for the chips"): the wrapped two-chip row's DRAWN chips clear
    // the central third with the standard label as well. The row is measured from the two chips' tagged bounds, which are
    // the drawn pills (J8's is 32 dp tall inside its 48 dp touch box; see the report).
    @Test fun `S10 at ROTATION_90 with the standard label the wrapped two-chip row's drawn chips stay out of the central third`() {
        setScreen(Surface.ROTATION_90)
        assertChipRowClearOfCentralThird()
    }

    @Test fun `S10 at ROTATION_270 with the standard label the wrapped two-chip row's drawn chips stay out of the central third`() {
        setScreen(Surface.ROTATION_270)
        assertChipRowClearOfCentralThird()
    }

    /**
     * J8's chip keeps taking touches over its whole drawn pill: real touches 3 dp inside its top and bottom edges and at its
     * left and right ends each open its list. (Measured at `3bbbcfa`: a touch 6 dp OUTSIDE the pill does not reach the chip,
     * so J8's tappable area is the pill, `JournalEntriesChip.kt`'s own note; the 48 dp box around it is layout only.)
     */
    private fun assertJournalChipKeepsItsTouchBox() {
        val chip = tag(JOURNAL_ENTRIES_CHIP_TAG)
        assertEquals("J8's drawn pill is 32 dp tall", 32f, (chip.bottom - chip.top).value, 0.5f)
        val cx = (chip.left + chip.right) / 2
        val cy = (chip.top + chip.bottom) / 2
        listOf(cx to chip.top + 3.dp, cx to chip.bottom - 3.dp, chip.left + 12.dp to cy, chip.right - 12.dp to cy).forEach { (x, y) ->
            composeRule.touchAt(x, y)
            assertTrue("a real touch at (${x.value}, ${y.value}) on the pill ${chip.describe()} opened its list",
                composeRule.onAllNodesWithTag(JOURNAL_ENTRIES_LIST_TAG).fetchSemanticsNodes().isNotEmpty())
            // A touch outside the popup dismisses it before the next.
            composeRule.onAllNodes(androidx.compose.ui.test.isRoot()).onFirst().performTouchInput { down(center); up() }
            composeRule.waitForIdle()
        }
    }

    @Test fun `T7 at ROTATION_90 J8's chip keeps taking touches over its pill under the tightened spacing`() {
        setScreen(Surface.ROTATION_90)
        assertJournalChipKeepsItsTouchBox()
    }

    @Test fun `T7 at ROTATION_270 J8's chip keeps taking touches over its pill under the tightened spacing`() {
        setScreen(Surface.ROTATION_270)
        assertJournalChipKeepsItsTouchBox()
    }

    /** The taxon chip's label is one ellipsized line, and the whole row is a single line tall. */
    private fun assertLongLabelOneEllipsizedLine() {
        val node = composeRule.onNode(androidx.compose.ui.test.hasText("Showing:", substring = true), useUnmergedTree = true)
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        assertTrue("the label exposes its layout", node.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results) == true)
        val layout = results.single()
        assertEquals("the long label is on one line", 1, layout.lineCount)
        assertTrue("and that line is ellipsized", layout.isLineEllipsized(0))
    }

    /** With the taxon chip alone (no second line to add), a long label leaves the whole row one chip tall and out of the central third. */
    private fun assertTaxonChipAloneOneLine() {
        assertLongLabelOneEllipsizedLine()
        val row = chipRow()
        assertTrue("the row ${row.describe()} is one chip tall (48 dp at most)", (row.bottom - row.top) <= 48.dp)
        assertChipRowClearOfCentralThird()
    }

    @Test fun `T7 at ROTATION_90 the taxon chip alone with a long label is one ellipsized line, clear of the central third`() {
        setScreen(Surface.ROTATION_90, LONG_LABEL, withJournalChip = false)
        assertTaxonChipAloneOneLine()
    }

    @Test fun `T7 at ROTATION_270 the taxon chip alone with a long label is one ellipsized line, clear of the central third`() {
        setScreen(Surface.ROTATION_270, LONG_LABEL, withJournalChip = false)
        assertTaxonChipAloneOneLine()
    }

    @Test fun `T7 at ROTATION_90 a long label is one ellipsized line`() {
        setScreen(Surface.ROTATION_90, LONG_LABEL)
        assertLongLabelOneEllipsizedLine()
    }

    @Test fun `T7 at ROTATION_270 a long label is one ellipsized line`() {
        setScreen(Surface.ROTATION_270, LONG_LABEL)
        assertLongLabelOneEllipsizedLine()
    }

    @Test fun `T7 with the cluster on the far side the two chips have room and stay on one line`() {
        setScreen(Surface.ROTATION_90)
        snapClusterAcross(500.dp)
        val taxon = tag("map-taxon-filter-chip")
        val journal = tag(JOURNAL_ENTRIES_CHIP_TAG)
        assertTrue("the journal chip ${journal.describe()} is beside the taxon chip ${taxon.describe()}, not under it", journal.top < taxon.bottom - 0.5.dp)
    }

    @Test fun `T7 at ROTATION_90 J8's chip alone clears the cluster and the reset button takes touches`() {
        setScreen(Surface.ROTATION_90, withTaxonChip = false)
        assertChipRowAwayFromCluster()
    }

    @Test fun `T7 at ROTATION_270 J8's chip alone clears the cluster and the reset button takes touches`() {
        setScreen(Surface.ROTATION_270, withTaxonChip = false)
        assertChipRowAwayFromCluster()
    }

    @Test fun `T7 at ROTATION_90 the taxon chip alone clears the cluster and the reset button takes touches`() {
        setScreen(Surface.ROTATION_90, withJournalChip = false)
        assertChipRowAwayFromCluster()
    }

    @Test fun `T7 at ROTATION_270 the taxon chip alone clears the cluster and the reset button takes touches`() {
        setScreen(Surface.ROTATION_270, withJournalChip = false)
        assertChipRowAwayFromCluster()
    }

    private fun assertTaxonClearReachable() {
        val clear = tag("map-taxon-filter-clear")
        composeRule.touchAt((clear.left + clear.right) / 2, (clear.top + clear.bottom) / 2)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertTrue("a real touch on the taxon chip's clear button ${clear.describe()} cleared the filter", composeRule.onAllNodesWithTag("map-taxon-filter-chip").fetchSemanticsNodes().isEmpty())
    }

    @Test fun `T7 at ROTATION_90 a real touch on the taxon chip's clear button reaches it`() {
        setScreen(Surface.ROTATION_90)
        assertTaxonClearReachable()
    }

    @Test fun `T7 at ROTATION_270 a real touch on the taxon chip's clear button reaches it`() {
        setScreen(Surface.ROTATION_270)
        assertTaxonClearReachable()
    }
}

private const val SHORT_LABEL = "artist's bracket"
private const val LONG_LABEL = "Chicken of the woods, sulphur shelf, the bright orange bracket fungus of oak"

private fun chipRowSighting(index: Int, label: String) = Sighting(
    observationId = index.toLong(),
    taxonId = 48473L,
    scientificName = "Ganoderma applanatum",
    commonName = label,
    lat = LAYOUT_FIXES_REGION.lat + index * 0.001,
    lng = LAYOUT_FIXES_REGION.lng + index * 0.001,
    observedOn = LocalDate.of(2025, 8, 14),
    photoUrl = null,
)

private fun chipRowForecast(label: String) = AvailabilityForecast(
    region = LAYOUT_FIXES_REGION,
    month = 8,
    filter = TaxonFilter.FUNGI,
    entries = listOf(
        AvailabilityEntry(
            species = SpeciesObservationCount(
                taxonId = 48473L,
                scientificName = "Ganoderma applanatum",
                commonName = label,
                rank = "species",
                observationCount = 14,
                photoUrl = null,
                wikipediaUrl = null,
            ),
            relativeLikelihood = 1.0f,
        ),
    ),
)

