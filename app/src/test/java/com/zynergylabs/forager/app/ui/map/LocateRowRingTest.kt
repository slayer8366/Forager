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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-06-04, Amendment 1 (RECORD -2186) ringed the locate row on both maps ("(b) Ring it on both maps");
 * Amendment 2 replaces that build. The owner, seeing it on the S22: "Yeah, no ring. Only have the ring in landscape when
 * needing to re-center the map camera during navigation [...] every other situation it doesn't have a ring." So these
 * cases now read that the ring is absent where Amendment 1 drew it: the Maps tab not navigating, and the journal entry's
 * map in fullscreen (the only place that map draws the bar), by day and with the app's Night Mode on. The landscape
 * navigation ring, and its absence in the other navigating cases, are `LocateRingReCenterTest`'s.
 *
 * The ring is read from the drawn pixels of the locate row ("Center on my location") as before: each side inwards from
 * the row's own edge along its middle row and centre column, in the first 11 dp only (the glyph starts at about 13 dp),
 * for the ruled hex written out (`onSurface`, Bark #3B2E24 by day, Cream #EDE3D0 at night). No side may carry it.
 *
 * Amendment 4 (RECORD -2186) gave the ring the map chrome's own translucent outline (the owner, to the ring: "Yes, same
 * outline"), so "no ring" no longer looks for one colour: on each side the ring's place ([ringBand], 6 to 7 dp in, a pixel
 * either side) must read as the row's own fill just inside it ([ringUnder], 9 dp in) and nothing else, so a ring of any colour
 * fails it. Until Amendment 4 these looked only for the solid `onSurface` above.
 */
abstract class LocateRowRingTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private class Look(val name: String, val dark: Boolean)

    private val day = Look("day", dark = false)

    private val night = Look("night", dark = true)

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

    private fun assertNoRing(where: String, look: Look) {
        val bounds = composeRule.onNodeWithContentDescription(LOCATE).getUnclippedBoundsInRoot()
        assertEquals("$where: the locate row's width is unchanged, the 48 dp target", 48f, (bounds.right - bounds.left).value, 0.5f)
        assertEquals("$where: the locate row's height is unchanged, the 48 dp target", 48f, (bounds.bottom - bounds.top).value, 0.5f)
        val band = ringBand(::px)
        for ((side, edge) in sides(LOCATE)) {
            val under = edge[ringUnder(::px)]
            val off = firstNotUnder(edge, under, band.first, band.last)
            assertNull("$where, ${look.name}, locate row, $side side: no ring, the ring's place ($band px in) is the fill ${under.hex()} alone; ${off?.let { "$it px in reads ${edge[it].hex()}" }}; the edge reads ${edge.map { it.hex() }}", off)
        }
    }

    protected fun assertNoRingOnTheMapsTab(dark: Boolean) {
        val look = if (dark) night else day
        setMapsTab(look)
        assertNoRing("Maps tab, not navigating", look)
    }

    protected fun assertNoRingOnTheJournalMap(dark: Boolean) {
        val look = if (dark) night else day
        setJournalMap(look)
        assertNoRing("journal map", look)
    }

    /**
     * CLAUDE.md: a semantic click proves wiring, not routing, and a finger is not a point. Real touches at five points across
     * the journal map's locate row (centre, and 8% in from each side), each asking for the location once. A guard on the row's
     * touch area and what lies over it: it passes the same before and after either amendment, by design.
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
        val ACROSS = listOf(0.5f to 0.5f, 0.08f to 0.5f, 0.92f to 0.5f, 0.5f to 0.08f, 0.5f to 0.92f)
    }
}

/** The S22's portrait window. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class LocateRowRingTest : LocateRowRingTests() {

    @Test
    fun `by day, the Maps tab's locate row has no ring when not navigating`() = assertNoRingOnTheMapsTab(dark = false)

    @Test
    fun `at night, the Maps tab's locate row has no ring when not navigating`() = assertNoRingOnTheMapsTab(dark = true)

    @Test
    fun `by day, the journal map's locate row has no ring`() = assertNoRingOnTheJournalMap(dark = false)

    @Test
    fun `at night, the journal map's locate row has no ring`() = assertNoRingOnTheJournalMap(dark = true)

    @Test
    fun `real touches across the journal map's locate row still reach it`() = assertTouchesReachTheJournalLocate()
}
