package com.zynergylabs.forager.app.ui.map

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.availability.MapLayersTestScreen
import com.zynergylabs.forager.app.ui.availability.hostActivity
import com.zynergylabs.forager.app.ui.availability.mapLayersViewModel
import com.zynergylabs.forager.app.ui.log.CartographyEntryReportScreen
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil

/**
 * Dispatch 2026-11-06-04, Amendment 1 (RECORD -2186). The owner, to "The journal map's locate button is one row of the
 * shared icon bar, which already has a thin outline as a whole. What do you want?": "(b) Ring it on both maps" (the option
 * read: "A 1 dp Bark/cream ring around the locate row on the journal map and the Maps tab, so both bars stay the same").
 *
 * The ring is read from the drawn pixels of the locate row ("Center on my location") of the real icon bar on both maps:
 * the Maps tab, and the journal entry's map in fullscreen (the only place that map draws the bar), inside `MainActivity`'s
 * own root ([ForagerThemeWithNoSignal]) by day and with the app's Night Mode on. The colours are written out as the ruled
 * hex (Re-center's outline, #505: `onSurface`, Bark by day, Cream at night), not read from the code under test, so a ring
 * drawn in any other colour, or none, fails. Each side of the ring is read inwards from the row's own edge along its
 * middle row (left, right) and centre column (top, bottom), in the first 11 dp only: the icon's glyph starts at about
 * 13 dp, and by day it is Bark too. The ring is a circle the size of the bar's round badges ([MAP_ICON_BAR_FILL_DIAMETER],
 * 36 dp, inset 6 dp), so where it crosses those lines anti-aliasing may soften its outermost pixel: at most ceil(1 dp)
 * wide. The bar's own outline, Bark or white at 40%, composites to a different colour and cannot be mistaken for it.
 * The reset-orientation row beside it is read the same way and must carry no ring.
 */
abstract class LocateRowRingTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private class Look(val name: String, val dark: Boolean, val ring: Color)

    /** The day `onSurface`, Bark. */
    private val day = Look("day", dark = false, ring = Color(0xFF3B2E24))

    /** The night `onSurface`, Cream. */
    private val night = Look("night", dark = true, ring = Color(0xFFEDE3D0))

    private val entry = CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)
        .copy(
            isDraft = false,
            waypointDecisions = listOf(WaypointDecision(waypointId = "w1", name = "Trailhead", lat = 45.5, lng = -122.5, kept = true)),
        )

    private val entryMapData = CartographyEntryMapData(
        trackPolylines = emptyList(),
        findMarkers = emptyList(),
        waypointMarkers = listOf(RecordPoint("w1", LatLng(45.5, -122.5))),
        photoMarkers = emptyList(),
        offlineRegionCircles = emptyList(),
    )

    private var entryMapTap: (() -> Unit)? = null
    private val entryMapSlot: MapSlot = { _, _, _, _, _, onTap, _, _, _ -> entryMapTap = onTap }
    private var locationAsks = 0

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun px(dp: Float): Int = with(composeRule.density) { dp.dp.roundToPx() }

    private fun ringWidthPx(): Int = with(composeRule.density) { ceil(1.dp.toPx()).toInt() }

    private fun setMapsTab(look: Look) {
        val viewModel = mapLayersViewModel()
        val tabMap: MapSlot = { _, _, _, _, _, _, _, _, _ -> }
        composeRule.setContent {
            ForagerThemeWithNoSignal(darkTheme = look.dark, noSignal = MutableStateFlow(false), offlineRegions = emptyList()) {
                MapLayersTestScreen(viewModel = viewModel, mapSlot = tabMap, store = AbsentForecastCellStore)
            }
        }
        settle()
    }

    /** The journal entry's map, opened full screen by a tap on it, as a user opens it. */
    private fun setJournalMap(look: Look) {
        composeRule.setContent {
            ForagerThemeWithNoSignal(darkTheme = look.dark, noSignal = MutableStateFlow(false), offlineRegions = emptyList()) {
                CartographyEntryReportScreen(
                    entry = entry,
                    galleryPhotos = emptyList(),
                    distanceUnit = DistanceUnit.MILES,
                    mapSlot = entryMapSlot,
                    night = look.dark,
                    getMapData = { _, _ -> entryMapData },
                    getCoveringOfflineRegion = { _, _ -> null },
                    getCurrentLocation = { locationAsks++; LocationResult.LocationUnavailable },
                    onEdit = {},
                    onDeleteEntry = {},
                    onBack = {},
                )
            }
        }
        settle()
        val tap = entryMapTap ?: throw AssertionError("the entry's map resolved, so it can be tapped full screen")
        composeRule.runOnUiThread { tap() }
        settle()
    }

    /** The four sides, each read inwards from the row's own edge over its first 11 dp. */
    private fun sides(row: String): List<Pair<String, List<Color>>> {
        val pixels = composeRule.onNodeWithContentDescription(row).captureToImage().toPixelMap()
        val n = px(11f)
        val across = (0 until pixels.width).map { pixels[it, pixels.height / 2] }
        val down = (0 until pixels.height).map { pixels[pixels.width / 2, it] }
        return listOf(
            "left" to across.take(n),
            "right" to across.takeLast(n).reversed(),
            "top" to down.take(n),
            "bottom" to down.takeLast(n).reversed(),
        )
    }

    private fun assertRing(where: String, look: Look) {
        val bounds = composeRule.onNodeWithContentDescription(LOCATE).getUnclippedBoundsInRoot()
        assertEquals("$where: the locate row's width is unchanged, the 48 dp target", 48f, (bounds.right - bounds.left).value, 0.5f)
        assertEquals("$where: the locate row's height is unchanged, the 48 dp target", 48f, (bounds.bottom - bounds.top).value, 0.5f)
        val width = ringWidthPx()
        for ((side, edge) in sides(LOCATE)) {
            val run = runOf(edge, look.ring)
            assertNotNull("$where, ${look.name}, locate row, $side side: a ${look.ring.hex()} ring is drawn; the edge reads ${edge.map { it.hex() }}", run)
            run!!
            val drawn = run.last - run.first + 1
            println("MEASURED $where, ${look.name}, locate row, $side side: ${look.ring.hex()} for $drawn px from ${run.first} px in (6 dp = ${px(6f)} px, ceil(1 dp) = $width px); the edge reads ${edge.map { it.hex() }}")
            assertTrue("$where, ${look.name}, $side side: the ring is at most ceil(1 dp) = $width px wide, drew $drawn", drawn <= width)
            assertTrue("$where, ${look.name}, $side side: the ring sits on the badge circle, 6 dp (${px(6f)} px) in, found ${run.first} px in", run.first in (px(6f) - 1)..(px(7f)))
        }
        for ((side, edge) in sides(RESET)) {
            assertNull("$where, ${look.name}, reset-orientation row, $side side: no ring there; the edge reads ${edge.map { it.hex() }}", runOf(edge, look.ring))
        }
    }

    protected fun assertRingOnTheMapsTab(dark: Boolean) {
        val look = if (dark) night else day
        setMapsTab(look)
        assertRing("Maps tab", look)
    }

    protected fun assertRingOnTheJournalMap(dark: Boolean) {
        val look = if (dark) night else day
        setJournalMap(look)
        assertRing("journal map", look)
    }

    /**
     * CLAUDE.md: a semantic click proves wiring, not routing, and a finger is not a point. Real touches at five points across
     * the journal map's locate row, inside and outside the ring, each asking for the location once. A guard that the ring
     * changes neither the row's touch area nor what lies over it: it passes the same before and after the ring, by design.
     * The Maps tab's locate row has its own coordinate touches, unchanged (`AvailabilityScreenMapIconStackTest`).
     */
    protected fun assertTouchesReachTheJournalLocate() {
        setJournalMap(day)
        ACROSS.forEach { (fx, fy) ->
            val before = locationAsks
            composeRule.onNodeWithContentDescription(LOCATE).performTouchInput { click(Offset(width * fx, height * fy)) }
            settle()
            assertEquals("journal map: a real touch at ($fx, $fy) on the locate row asks for the location", before + 1, locationAsks)
        }
    }

    private companion object {
        const val LOCATE = "Center on my location"
        const val RESET = "Reset orientation to north"
        val ACROSS = listOf(0.5f to 0.5f, 0.08f to 0.5f, 0.92f to 0.5f, 0.5f to 0.08f, 0.5f to 0.92f)
    }
}

/** The S22's portrait window. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class LocateRowRingTest : LocateRowRingTests() {

    @Test
    fun `by day, the Maps tab's locate row has a 1 dp Bark ring`() = assertRingOnTheMapsTab(dark = false)

    @Test
    fun `at night, the Maps tab's locate row has a 1 dp pale ring`() = assertRingOnTheMapsTab(dark = true)

    @Test
    fun `by day, the journal map's locate row has a 1 dp Bark ring`() = assertRingOnTheJournalMap(dark = false)

    @Test
    fun `at night, the journal map's locate row has a 1 dp pale ring`() = assertRingOnTheJournalMap(dark = true)

    @Test
    fun `real touches across the journal map's locate row still reach it`() = assertTouchesReachTheJournalLocate()
}
