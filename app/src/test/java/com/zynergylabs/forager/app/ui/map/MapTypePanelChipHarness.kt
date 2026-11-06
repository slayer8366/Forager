package com.zynergylabs.forager.app.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.availability.MapLayersTestScreen
import com.zynergylabs.forager.app.ui.availability.mapLayersViewModel
import com.zynergylabs.forager.app.ui.log.CartographyEntryReportScreen
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.pow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Dispatch 2026-11-04-07 (RECORD -2089, -2093, -2094): the map type panel opened through its two real entry points, the
 * Maps tab's icon bar and a journal entry's map, inside `MainActivity`'s own root, [ForagerThemeWithNoSignal], and the
 * chips read from the drawn pixels. Re-typed by hand from 22ae67021's `MapTypePanelSelectedChipTest` (removed by that
 * commit's revert), with two changes:
 * - the root carries the no-signal and downloaded-area locals, so the grey "chosen, but offline" chip can be driven
 *   through either entry point, as `MapTypePanelOfflineChipTest` drives it on the panel alone;
 * - the stand-in map is a real `clickable`, as `LayersRecordingMapSlot` is, so the journal entry's map goes fullscreen by
 *   a touch on it, not by calling the screen's tap callback by hand (RECORD -2092, K4).
 *
 * The claim these tests make is colour, not touch routing, so Layers and a chip are each opened by one real touch at
 * their centre; the routing claims, with touches across a control's bounds, belong to `MapTypePanelTest`.
 */
internal class MapTypePanelChipHarness(private val rule: ComposeContentTestRule) {

    /** What the root's [LocalNoSignal] reads. False (signal) until a test sets it. */
    val noSignal = MutableStateFlow(false)

    private val slot: MapSlot = { _, _, _, _, _, onTap, _, _, modifier ->
        Box(
            modifier
                .testTag(STAND_IN_MAP_TAG)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap() },
        )
    }

    /** The Maps tab, Trail chosen (nothing stored), the app's Night Mode on when [dark]. */
    fun setMapsTab(dark: Boolean, offlineRegions: List<OfflineRegionSummary> = emptyList()) {
        val viewModel = mapLayersViewModel()
        rule.setContent {
            ForagerThemeWithNoSignal(darkTheme = dark, noSignal = noSignal, offlineRegions = offlineRegions) {
                MapLayersTestScreen(viewModel, slot, AbsentForecastCellStore)
            }
        }
        rule.waitForIdle()
    }

    /**
     * A journal entry's map, Trail chosen ([MapMode.DEFAULT]), made fullscreen by a real touch on it: the panel is
     * composed only while it is (CartographyEntryReportScreen.kt, `if (isMapFullscreen)`). The entry's `night` is the
     * map's own Night Maps style and is left off: the chips follow the app's Night Mode, [dark], not that.
     */
    fun setEntryMap(dark: Boolean, offlineRegions: List<OfflineRegionSummary> = emptyList()) {
        rule.setContent {
            ForagerThemeWithNoSignal(darkTheme = dark, noSignal = noSignal, offlineRegions = offlineRegions) {
                CartographyEntryReportScreen(
                    entry = entry,
                    galleryPhotos = emptyList(),
                    distanceUnit = DistanceUnit.MILES,
                    mapSlot = slot,
                    night = false,
                    getMapData = { _, _ -> mapData },
                    getCoveringOfflineRegion = { _, _ -> null },
                    getCurrentLocation = { LocationResult.LocationUnavailable },
                    onEdit = {},
                    onDeleteEntry = {},
                    onBack = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(STAND_IN_MAP_TAG).performTouchInput { click(center) }
        rule.waitForIdle()
    }

    /** The root's pixels just before the panel last opened: what lies under the panel, named in the contrast figures. */
    private var beforePanel: PixelMap? = null

    /** A real touch at the centre of the Layers row, and the panel is open. */
    fun openPanel() {
        beforePanel = rule.onRoot().captureToImage().toPixelMap()
        val row = rule.onNodeWithContentDescription("Layers:", substring = true).getUnclippedBoundsInRoot()
        val at = with(rule.density) { Offset(((row.left + row.right) / 2).toPx(), ((row.top + row.bottom) / 2).toPx()) }
        rule.onRoot().performTouchInput { click(at) }
        rule.waitForIdle()
        rule.onNodeWithTag(MAP_TYPE_PANEL_TAG).assertIsDisplayed()
    }

    /** A real touch at the centre of [mode]'s chip: a choice, which closes the panel (MapTypePanel.kt, `onDismiss`). */
    fun chooseByTouch(mode: MapMode) {
        rule.onNodeWithTag(mapModeChipTag(mode)).performTouchInput { click(center) }
        rule.waitForIdle()
    }

    private fun px(dp: Dp): Int = with(rule.density) { dp.roundToPx() }

    /** ceil(1 dp) in pixels: what Foundation's border draws for a 1 dp stroke at this density. */
    fun outlineWidthPx(): Int = with(rule.density) { ceil(1.dp.toPx()).toInt() }

    /** The chip node's middle row of pixels, left to right. The node is 48 dp tall and the chip 32 dp, centred. */
    fun middleRow(mode: MapMode): List<Color> {
        val pixels = rule.onNodeWithTag(mapModeChipTag(mode)).captureToImage().toPixelMap()
        return (0 until pixels.width).map { pixels[it, pixels.height / 2] }
    }

    /**
     * The drawn fill: 4 dp in from the node's left edge, half way down. Past a 1 dp outline even if the node is wider than
     * the chip (a chip narrower than 48 dp is centred in a 48 dp touch target, up to 2 dp in), and short of the label.
     */
    fun drawnFill(mode: MapMode): Color = middleRow(mode)[px(4.dp)]

    /**
     * The middle row's first [EDGE_WINDOW] from the node's left edge, and its last read inwards from the right edge: where
     * an outline is drawn, short of the label, whose padding from the chip's edge is wider than this.
     */
    fun edges(mode: MapMode): List<Pair<String, List<Color>>> {
        val row = middleRow(mode)
        val n = px(EDGE_WINDOW)
        return listOf("left edge" to row.take(n), "right edge" to row.takeLast(n).reversed())
    }

    /** Every pixel of [mode]'s label: its `Text` node's own bounds, in the unmerged tree, inside that chip. */
    fun labelPixels(mode: MapMode): List<Color> {
        val pixels = rule.onNode(hasText(mode.label) and hasAnyAncestor(hasTestTag(mapModeChipTag(mode))), useUnmergedTree = true)
            .captureToImage().toPixelMap()
        return (0 until pixels.height).flatMap { y -> (0 until pixels.width).map { x -> pixels[x, y] } }
    }

    /**
     * The open panel's drawn colour, in its top padding at its middle, and the root's pixel at the same place just before
     * the panel opened, which is what the panel's 0.8 fill lies over in this test.
     */
    fun panelAndBackground(): Pair<Color, Color> {
        val panel = rule.onNodeWithTag(MAP_TYPE_PANEL_TAG)
        val pixels = panel.captureToImage().toPixelMap()
        val x = pixels.width / 2
        val y = px(Spacing4)
        val bounds = panel.getUnclippedBoundsInRoot()
        val left = with(rule.density) { bounds.left.roundToPx() }
        val top = with(rule.density) { bounds.top.roundToPx() }
        val before = checkNotNull(beforePanel) { "the panel was never opened through openPanel()" }
        return pixels[x, y] to before[left + x, top + y]
    }

    private val entry = CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)
        .copy(isDraft = false, waypointDecisions = listOf(WaypointDecision(waypointId = "w1", name = "Trailhead", lat = 45.5, lng = -121.09375, kept = true)))

    private val mapData = CartographyEntryMapData(
        trackPolylines = emptyList(),
        findMarkers = emptyList(),
        waypointMarkers = listOf(RecordPoint("w1", LatLng(45.5, -121.09375))),
        photoMarkers = emptyList(),
        offlineRegionCircles = emptyList(),
    )

    companion object {
        const val STAND_IN_MAP_TAG = "chip-harness-map"

        /** How far in from each edge of the chip node an outline is looked for. */
        val EDGE_WINDOW = 6.dp

        /** Inside the panel's 1 dp border and its 8 dp padding (`Spacing.sm`, MapTypePanel.kt), above the chips. */
        private val Spacing4 = 4.dp

        /** One downloaded area, so no signal draws Trail Map from it in place of a chosen Topo (the grey chip). */
        fun downloadedRegion() = OfflineRegionSummary(
            id = 1, name = "Region 1", region = Region(45.5, -121.19375, 5), minZoom = 10.0, maxZoom = 15.0,
            tileCount = 100, sizeBytes = 2_000_000L, createdAtEpochMillis = 1_000L, isDownloaded = true,
        )
    }
}

/** In [edge], read from the chip's edge inwards, the run of [colour] starting at its first pixel of it; null if none. */
internal fun runOf(edge: List<Color>, colour: Color): IntRange? {
    val start = edge.indexOf(colour)
    if (start < 0) return null
    var end = start
    while (end < edge.size && edge[end] == colour) end++
    return start until end
}

/** A colour as #RRGGBB, for failure messages and the contrast figures. */
internal fun Color.hex(): String = "#%06X".format(
    ((red * 255 + 0.5f).toInt() shl 16) or ((green * 255 + 0.5f).toInt() shl 8) or (blue * 255 + 0.5f).toInt(),
)

/** WCAG 2 contrast ratio of two opaque sRGB colours. */
internal fun contrastRatio(a: Color, b: Color): Double {
    fun channel(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    fun luminance(c: Color): Double = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
    return (hi + 0.05) / (lo + 0.05)
}
