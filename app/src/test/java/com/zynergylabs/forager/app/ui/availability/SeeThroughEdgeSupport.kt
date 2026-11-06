package com.zynergylabs.forager.app.ui.availability

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_DARK
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_LIGHT
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import com.zynergylabs.forager.app.ui.track.RecordingNotice
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Dispatch 2026-11-05-06, Phase 2 (RECORD -2154, the owner: "Thin outline (Recommended)"; -2155, -2156): what the see-through map
 * windows' edges are read against. Shared by SeeThroughEdgePixelsTest's portrait and landscape classes.
 */

internal typealias EdgeShadeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/**
 * The bare map every edge is read over: the grey beside the bubble in the owner's crop, (181, 180, 176), as Phase 1 measured it
 * (prompts/preserved/2026-11-05-06-phase1-report.md, section 1). A mid grey, not the screen's own background, so a shadow under a
 * window darkens something the fill lets through by a measurable amount in both themes: over a near-black background a shadow
 * changes almost nothing, and a check there could pass with the band still drawn.
 */
internal val EDGE_SHADE_MAP_GREY = Color(0xFFB5B4B0)

/** The scrim the add tile and the map type panel lay over the map (AddActionTile.kt, MapTypePanel.kt: black at 0.32). */
internal const val EDGE_SHADE_SCRIM_ALPHA = 0.32f

/** [top] at [alpha] over [under], per channel. */
internal fun edgeOver(top: Color, alpha: Float, under: Color) = Color(
    red = top.red * alpha + under.red * (1f - alpha),
    green = top.green * alpha + under.green * (1f - alpha),
    blue = top.blue * alpha + under.blue * (1f - alpha),
)

/** A window's fill, its colour role at the map chrome's 0.8, over [under]. */
internal fun edgeFill(role: Color, under: Color) = edgeOver(role.copy(alpha = 1f), MAP_CHROME_OVER_MAP_ALPHA, under)

/** The map-chrome hairline (MapChromeOutline.kt: Bark at 0.4 by day, white at 0.4 at night) over a window's [fill]. */
internal fun edgeLine(dark: Boolean, fill: Color): Color {
    val line = if (dark) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT
    return edgeOver(line.copy(alpha = 1f), line.alpha, fill)
}

internal fun edgeHex(c: Color): String = "%06X".format(c.toArgb() and 0xFFFFFF)

internal fun edgeDiff(a: Color, b: Color): Float = max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue)))

internal enum class EdgeSide { LEFT, TOP, RIGHT, BOTTOM }

/**
 * One edge of a window: its fill from [fromDp] to [toDp] dp in, at [along] of the way along that edge (0 the start, 1 the end,
 * left to right or top to bottom), and at its very edge (1.5 px in, the middle of a 1 dp line at 3 px per dp) the hairline where
 * [line], or the fill where not (a side on the screen's edge, a side another piece draws, or one the rulings leave open).
 * [fromDp] to [toDp] stay clear of the window's content, in its padding.
 */
internal data class EdgeSpec(val side: EdgeSide, val along: Float, val fromDp: Int, val toDp: Int, val line: Boolean)

/** One pixel read, against what it should be. */
internal data class EdgeReading(val where: String, val read: Color, val expected: Color, val tolerance: Float) {
    val ok: Boolean get() = edgeDiff(read, expected) <= tolerance
    override fun toString() = "$where ${edgeHex(read)} (want ${edgeHex(expected)})"
}

/** Fill samples within 2/255 of the composite, as MapChromeColourPixelsTest holds its fills; the hairline within 3/255. */
internal const val EDGE_FILL_TOLERANCE = 2f / 255f
internal const val EDGE_LINE_TOLERANCE = 3f / 255f

/**
 * Reads [specs] on the window at [bounds] in [pixels] (3 px per dp here: every class runs at xxhdpi). Each sample is held to [role]
 * at 0.8 over what lies under that pixel, [under], and the hairline over that.
 */
internal fun readEdges(
    pixels: PixelMap,
    density: Float,
    bounds: DpRect,
    dark: Boolean,
    role: Color,
    under: (x: Int, y: Int) -> Color,
    specs: List<EdgeSpec>,
): List<EdgeReading> {
    val l = (bounds.left.value * density).roundToInt()
    val t = (bounds.top.value * density).roundToInt()
    val r = (bounds.right.value * density).roundToInt()
    val b = (bounds.bottom.value * density).roundToInt()
    fun px(dp: Int) = (dp * density).roundToInt()
    return specs.flatMap { spec ->
        val alongX = l + ((r - l) * spec.along).toInt().coerceIn(0, r - l - 1)
        val alongY = t + ((b - t) * spec.along).toInt().coerceIn(0, b - t - 1)
        val name = spec.side.name.lowercase()
        val fills = (spec.fromDp..spec.toDp).map { k ->
            val (x, y) = when (spec.side) {
                EdgeSide.LEFT -> l + px(k) to alongY
                EdgeSide.RIGHT -> r - 1 - px(k) to alongY
                EdgeSide.TOP -> alongX to t + px(k)
                EdgeSide.BOTTOM -> alongX to b - 1 - px(k)
            }
            EdgeReading("$name ${k}dp in", pixels[x, y], edgeFill(role, under(x, y)), EDGE_FILL_TOLERANCE)
        }
        val (ex, ey) = when (spec.side) {
            EdgeSide.LEFT -> l + 1 to alongY
            EdgeSide.RIGHT -> r - 2 to alongY
            EdgeSide.TOP -> alongX to t + 1
            EdgeSide.BOTTOM -> alongX to b - 2
        }
        val fill = edgeFill(role, under(ex, ey))
        val edge = if (spec.line) {
            EdgeReading("$name edge, the hairline", pixels[ex, ey], edgeLine(dark, fill), EDGE_LINE_TOLERANCE)
        } else {
            EdgeReading("$name edge, no line", pixels[ex, ey], fill, EDGE_FILL_TOLERANCE)
        }
        fills + edge
    }
}

/** Every reading printed for the run's log, and a failure naming each one off its mark. */
internal fun assertEdges(what: String, readings: List<EdgeReading>) {
    println("EDGESHADE|$what|" + readings.joinToString(" ; "))
    val off = readings.filterNot { it.ok }
    if (off.isNotEmpty()) throw AssertionError("$what reads its 0.8 fill from 2 dp in and its hairline at its edge; off: " + off.joinToString(", "))
}

/** The capture, kept beside the JUnit XML in the run's artifact for the device check to compare against. Not asserted on. */
internal fun saveEdgeCapture(name: String, image: ImageBitmap) {
    val dir = File("build/test-results/testDebugUnitTest/edge-shade").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
}

/** Where navigating returns to: a track's start, as RouteCardBarRig's origin (a two-place point near MadeUpPlace). */
internal val EDGE_SHADE_ORIGIN = Waypoint(id = "origin", lat = 45.38, lng = -121.69, altitude = null, name = "Start", note = "", createdAtEpochMillis = 1_700_000_000_000L, trackId = "t1")

private val EDGE_SHADE_FIX = LocationFix.Update(
    lat = 45.37, lng = -121.69, altitude = 50.0, accuracyMeters = 8f, timestampEpochMillis = 1_700_000_000_000L, provider = FixProvider.GPS,
)

/**
 * The real screen in the app's own theme over a grey map, recording, with one waypoint glyph to open a bubble from. The return
 * button toggles navigating to [EDGE_SHADE_ORIGIN], as the ViewModel's toggle would, so the navigation card is reached by a real
 * touch on the real control. The clock is noon (D43's lesson), so the card carries no evening lines. [entries] are the Journal's
 * saved entries the screen is handed, as MainActivity hands it the day entries; none by default, so every other read is unchanged.
 */
internal class EdgeShadeRig(
    private val composeRule: EdgeShadeRule,
    private val dark: Boolean,
    private val entries: List<CartographyEntry> = emptyList(),
) {
    val map = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, BUBBLE_WAYPOINT.id, 60.dp, 380.dp, LatLng(BUBBLE_WAYPOINT.lat, BUBBLE_WAYPOINT.lng))))
    private val greyMap: MapSlot = { region, content, renderMode, focus, onLongPress, onTap, a, b, modifier ->
        map.slot(region, content, renderMode, focus, onLongPress, onTap, a, b, modifier.background(EDGE_SHADE_MAP_GREY))
    }
    var navigating by mutableStateOf(false)
    var warning by mutableStateOf<RecordingNotice?>(null)

    fun setScreen() {
        composeRule.setContent { Screen() }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Screen() {
        ForagerTheme(darkTheme = dark) {
            AvailabilityScreen(
                uiState = if (navigating) AvailabilityUiState(liveFix = EDGE_SHADE_FIX) else AvailabilityUiState(),
                searchInputs = AvailabilityScreenSearchInputs(
                    onUseCurrentLocation = {},
                    onManualLatChanged = {},
                    onManualLngChanged = {},
                    onSearchManualCoordinates = {},
                    onRadiusChanged = {},
                    onMonthSelected = {},
                    onMapTabSelected = {},
                    onSeasonalTabSelected = {},
                    onTaxonSearchQueryChanged = {},
                    onTaxonSearchResultSelected = {},
                    onDismissTaxonSuggestions = {},
                    onReopenTaxonSuggestions = {},
                    onPlaceTripPin = { _, _, _ -> },
                    onDeletePlannedTrip = {},
                    onRecentSearchSelected = {},
                ),
                offlineMaps = AvailabilityScreenOfflineMaps(
                    onOfflineMapLatChanged = {},
                    onOfflineMapLngChanged = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                ),
                settings = AvailabilityScreenSettings(onNightModeMapsChanged = {}, onThemeModeChanged = {}),
                mapSlot = greyMap,
                records = AvailabilityScreenRecords(waypoints = listOf(BUBBLE_WAYPOINT)),
                journalEntries = AvailabilityScreenJournalEntries(cartographyUiState = CartographyUiState(entries = entries)),
                navigation = if (navigating) {
                    AvailabilityScreenNavigation(isReturning = true, onToggleReturning = { navigating = !navigating }, navigationTarget = EDGE_SHADE_ORIGIN)
                } else {
                    AvailabilityScreenNavigation(onToggleReturning = { navigating = !navigating })
                },
                recording = AvailabilityScreenRecording(isRecording = true, tripStartWarning = warning),
                currentTime = ROUTE_CARD_NOON_CLOCK,
            )
        }
    }

    fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    fun touchTag(tag: String) {
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    fun touchDescribed(description: String) {
        composeRule.onNodeWithContentDescription(description).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    fun touchDescribedStarting(prefix: String) {
        composeRule.onNode(hasContentDescription(prefix, substring = true)).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    fun touchText(text: String) {
        composeRule.onNodeWithText(text).performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    /** The screen as drawn, saved for the artifact under [name]. */
    fun capture(name: String): PixelMap {
        val image = composeRule.onRoot().captureToImage()
        saveEdgeCapture("${name}-${if (dark) "night" else "day"}", image)
        return image.toPixelMap()
    }

    /**
     * The bare map at ([xFraction], [yFraction]) of the screen, checked to be [expected] (the grey, or the grey under the scrim), so
     * a reference that landed on a window or a shadow fails here, by name, rather than skewing every composite.
     */
    fun reference(pixels: PixelMap, xFraction: Float, yFraction: Float, expected: Color): Color {
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val d = composeRule.density.density
        val x = ((root.left + (root.right - root.left) * xFraction).value * d).roundToInt()
        val y = ((root.top + (root.bottom - root.top) * yFraction).value * d).roundToInt()
        val read = pixels[x, y]
        if (edgeDiff(read, expected) > EDGE_FILL_TOLERANCE) {
            throw AssertionError("the reference point ($x, $y) px is not bare map: read ${edgeHex(read)}, want ${edgeHex(expected)}")
        }
        return read
    }

    val density: Float get() = composeRule.density.density
}
