package com.zynergylabs.forager.app.ui.availability

import android.graphics.Bitmap
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_DARK
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_LIGHT
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Dispatch 2026-10-13-02 (RECORD -966, -967): the icon bar's outline on the map's other chrome, read from rendered pixels of the
 * real [AvailabilityScreen] over a flat grey stand-in map, day and night, portrait and landscape.
 *
 * - **Present** on each piece's sides that face the map and on its rounded corners: the pill, the compass strip (and its card
 *   grown down while navigating), the bottom bar, the rail and the landscape navigation card.
 * - **Absent** on every side that lies on the screen's own edge (RECORD -967, the owner: "2 yes"), **except the portrait top
 *   pills** (RECORD -976, the owner: "In portrait mode: Have the outlines follow the entire pill at top. The bottom bar can stay
 *   as is."): the pill all the way round, the strip on its sides and bottom, its top open under the pill and its sides clipped
 *   there; **the landscape top pills** (RECORD -988): the pill all the way round, the strip on its top, bottom and any end not
 *   joined to the pill or the rail; and **the bottom bar's sides** (RECORD -989), down to its bottom, its bottom edge still
 *   without one.
 * - **One line where two pieces touch** (the owner: "1 C one line between the two like there is now"): across each join the
 *   run of outline-coloured pixels is one line wide (2 to 4 px at xxhdpi, 1 dp being 3 px), not two (6 px), and not none.
 *
 * What counts as the outline: a pixel within 0.07 of the luminance the outline colour ([MAP_ICON_STACK_BORDER_COLOR_LIGHT] /
 * [MAP_ICON_STACK_BORDER_COLOR_DARK], its own 0.4 alpha) composites to over the piece's own fill, read at a reference point on
 * the same piece, and at least 0.12 from that fill. The fill and the cast shadows (a few percent) fall outside that band; the
 * line is about 0.26 from the fill by day and 0.3 at night. The pill's shadow stepped onto the strip does not, by day, against
 * the pill's fill: across a join a pixel also has to be clear of both fills ([isJoinLine]). Robolectric reports zero insets, so every side here that would sit beside a system bar sits on the window's edge;
 * against real system bars the sides beside them are device items.
 *
 * Each state is also written as a PNG under the build's test-results directory, which CI uploads; nothing goes into the
 * repository.
 */
abstract class MapChromeOutlinePixelsTests(private val dark: Boolean, private val rotation: Int?) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 104.9, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.5135, lng = -122.67, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private val mapColour = Color(0xFF808080)

    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
        Box(modifier.testTag(MAP_TAG).background(mapColour))
    }

    private val outline = if (dark) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT
    private val theme = if (dark) "night" else "day"
    private val orientation = if (rotation == null) "portrait" else "landscape"

    private lateinit var image: PixelMap

    private fun setScreen(navigating: Boolean, shot: String) {
        if (rotation != null) Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            ForagerTheme(darkTheme = dark) {
                AvailabilityScreen(
                    uiState = AvailabilityUiState(liveFix = fix),
                    recording = AvailabilityScreenRecording(
                        isRecording = navigating,
                    ),
                    navigation = AvailabilityScreenNavigation(
                        isReturning = navigating,
                        navigationTarget = if (navigating) start else null,
                        returnRoute = ReturnRoute.Ahead(LatLng(45.5135, -122.67), 245.0),
                    ),
                    compassProvider = FixedCompass(134f),
                    computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                    currentTime = CurrentTimeProvider { t + 1_000L },
                    mapSlot = map,
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
                    settings = AvailabilityScreenSettings(
                        onNightModeMapsChanged = {},
                        onThemeModeChanged = {},
                    ),
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        capture(shot)
    }

    private fun capture(shot: String) {
        val bitmap: ImageBitmap = composeRule.onRoot().captureToImage()
        val dir = File("build/test-results/testDebugUnitTest/map-chrome-outline-screens").apply { mkdirs() }
        val widthDp = (bitmap.width / composeRule.density.density).roundToInt()
        val file = File(dir, "$orientation-$widthDp-$shot-$theme.png")
        file.outputStream().use { bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT|${file.absolutePath}|${bitmap.width}x${bitmap.height}")
        image = bitmap.toPixelMap()
    }

    // ── Reading pixels ──

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun px(d: Dp): Int = (d.value * composeRule.density.density).roundToInt()

    /** The first pixel inside a left or top edge, and the middle of the 3 px a 1 dp line lays inside a right or bottom one. */
    private fun inStart(edge: Dp) = px(edge) + 1
    private fun inEnd(edge: Dp) = px(edge) - 2

    private fun at(x: Int, y: Int): Color = image[x, y]
    private fun at(x: Dp, y: Dp): Color = image[px(x), px(y)]

    private fun lum(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    private fun rgb(c: Color) = "(%.3f, %.3f, %.3f; lum %.3f)".format(c.red, c.green, c.blue, lum(c))

    /** The outline colour at its own alpha, over [fill]. */
    private fun lineOver(fill: Color): Color {
        val a = outline.alpha
        return Color(
            red = outline.red * a + fill.red * (1f - a),
            green = outline.green * a + fill.green * (1f - a),
            blue = outline.blue * a + fill.blue * (1f - a),
        )
    }

    private fun isLine(p: Color, fill: Color) = abs(lum(p) - lum(lineOver(fill))) < 0.07f && abs(lum(p) - lum(fill)) > 0.12f

    private fun assertLine(what: String, x: Int, y: Int, fill: Color) {
        val p = at(x, y)
        assertTrue("$what: the outline at ($x, $y) px: read ${rgb(p)}, the outline over the fill ${rgb(lineOver(fill))}, the fill ${rgb(fill)}", isLine(p, fill))
    }

    /** Within a pixel either way, for a point on a rounded corner's arc. */
    private fun assertLineNear(what: String, x: Int, y: Int, fill: Color) {
        val around = (-1..1).flatMap { dx -> (-1..1).map { dy -> at(x + dx, y + dy) } }
        assertTrue(
            "$what: the outline within a pixel of ($x, $y) px: read ${around.joinToString { rgb(it) }}, the outline over the fill ${rgb(lineOver(fill))}",
            around.any { isLine(it, fill) },
        )
    }

    private fun assertNoLine(what: String, x: Int, y: Int, fill: Color) {
        val p = at(x, y)
        assertTrue(
            "$what: no outline at ($x, $y) px, the piece's own fill: read ${rgb(p)}, the fill ${rgb(fill)}, the outline over it ${rgb(lineOver(fill))}",
            !isLine(p, fill) && abs(lum(p) - lum(fill)) < 0.05f,
        )
    }

    /**
     * A pixel on the line across a join: the outline over either piece's fill ([isLine]), and more than [JOIN_CLEAR_OF_FILLS]
     * from both fills. The second condition is from CI run 38507319758 at bac9de39, the first run of this class: by day the first
     * rows of the pill's shadow stepped onto the portrait strip read 0.678 to 0.725 (strip fill 0.832, pill fill 0.902). That is
     * within 0.07 of the outline over the pill's fill, so [isLine] alone counted them as a second line (6 px, not 3). They are
     * 0.11 to 0.15 from the strip's own fill; the line itself measured 0.235 or more from both fills at every join that run read
     * (day and night, portrait and landscape).
     */
    private fun isJoinLine(p: Color, fillA: Color, fillB: Color) =
        (isLine(p, fillA) || isLine(p, fillB)) &&
            abs(lum(p) - lum(fillA)) > JOIN_CLEAR_OF_FILLS && abs(lum(p) - lum(fillB)) > JOIN_CLEAR_OF_FILLS

    /** The outline-coloured pixels in a run across a join, read against the fill on either side ([isJoinLine]). */
    private fun lineRun(what: String, pixels: List<Pair<Int, Int>>, fillA: Color, fillB: Color): Int {
        val read = pixels.map { (x, y) -> at(x, y) }
        val count = read.count { isJoinLine(it, fillA, fillB) }
        println("MEASURED $orientation $theme $what: $count outline pixels across ${read.joinToString { "%.3f".format(lum(it)) }}")
        return count
    }

    private fun assertOneLine(what: String, pixels: List<Pair<Int, Int>>, fillA: Color, fillB: Color) {
        val count = lineRun(what, pixels, fillA, fillB)
        assertTrue(
            "$what: one line across the join, 2 to 4 px (1 dp is 3 px), not two (6) and not none: read $count across " +
                pixels.joinToString { (x, y) -> rgb(at(x, y)) },
            count in 2..4,
        )
    }

    /** On a rounded corner of radius 16 dp, the outline's 45 degree point, half a line inside the edge. */
    private val arcInset: Dp = 16.dp - (16.dp - 0.5.dp) * 0.70710677f

    // ── Portrait ──

    protected fun portraitPill() {
        setScreen(navigating = false, shot = "pill-strip-bar")
        val pill = bounds(SEARCH_ENTRY_BAR_TAG)
        val mid = (pill.top + pill.bottom) / 2
        val fill = at(pill.left + 4.dp, mid)
        assertLine("the pill's bottom", px(pill.left + 24.dp), inEnd(pill.bottom), fill)
        assertLineNear("the pill's rounded top-left corner", px(pill.left + arcInset), px(pill.top + arcInset), fill)
        assertLineNear("the pill's rounded bottom-right corner", px(pill.right - arcInset), px(pill.bottom - arcInset), fill)
        // RECORD -976 (the owner: "Have the outlines follow the entire pill at top"): its sides on the screen's edges and its top
        // carry the line too. Until -976 these three were assertNoLine (RECORD -967: no line on the screen's own edge).
        assertLine("the pill's left side, on the screen's edge", inStart(pill.left), px(mid), fill)
        assertLine("the pill's right side, on the screen's edge", inEnd(pill.right), px(mid), at(pill.right - 4.dp, mid))
        assertLine("the pill's top", px(pill.right - 30.dp), inStart(pill.top), at(pill.right - 30.dp, pill.top + 4.dp))
    }

    protected fun portraitStrip(navigating: Boolean) {
        setScreen(navigating = navigating, shot = if (navigating) "navigating" else "strip")
        val pill = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds(STRIP_TAG)
        val stripFill = at(strip.left + 4.dp, strip.top + 10.dp)
        val pillFill = at(pill.left + 4.dp, (pill.top + pill.bottom) / 2)
        assertLine("the strip's card's bottom", px(strip.left + 24.dp), inEnd(strip.bottom), stripFill)
        assertLineNear("the strip's card's rounded bottom-left corner", px(strip.left + arcInset), px(strip.bottom - arcInset), stripFill)
        // RECORD -976: the strip's sides, on the screen's edges, carry the line (until -976 these two were assertNoLine), read
        // just below the pill and low on the card, which grows down while navigating.
        val low = strip.bottom - 24.dp
        assertLine("the strip's left side, on the screen's edge", inStart(strip.left), px(strip.top + 10.dp), stripFill)
        assertLine("the strip's right side, on the screen's edge", inEnd(strip.right), px(strip.top + 10.dp), at(strip.right - 4.dp, strip.top + 10.dp))
        assertLine("the strip's left side, low on its card", inStart(strip.left), px(low), at(strip.left + 4.dp, low))
        assertLine("the strip's right side, low on its card", inEnd(strip.right), px(low), at(strip.right - 4.dp, low))
        // The tuck. The strip's sides run up into the wedges under the pill's rounded lower corners: 4 dp above the pill's
        // bottom, at the screen's edge, is the strip's fill below the pill's curve, and its side line is there.
        assertLine("the strip's left side, under the pill's rounded corner", inStart(strip.left), px(pill.bottom - 4.dp), stripFill)
        assertLine("the strip's right side, under the pill's rounded corner", inEnd(strip.right), px(pill.bottom - 4.dp), at(strip.right - 4.dp, strip.top + 10.dp))
        // Clipped at the pill: 14 dp above the pill's bottom, 1 px in from the screen's edge, lies on the pill's own corner line,
        // inside the pill. The strip is drawn over the pill (later in z-order), so a side line run on up the tuck would lay a
        // second line on it there; it reads as the pill's single line at its side, not darker (day) or lighter (night).
        val pillSide = at(inStart(pill.left), px((pill.top + pill.bottom) / 2))
        val onCorner = at(inStart(pill.left), px(pill.bottom - 14.dp))
        assertTrue(
            "the pill's corner line under the tuck is one line, the pill's: read ${rgb(onCorner)}, the pill's side line ${rgb(pillSide)}",
            abs(lum(onCorner) - lum(pillSide)) < 0.05f,
        )
        // The tuck: the strip's top lies under the pill's lower edge. One line there, the pill's; none from the strip showing
        // through the pill or laid beside it.
        val x = px(pill.right - 30.dp)
        val join = px(pill.bottom)
        assertOneLine("the pill and the strip", (join - 9..join + 9).map { x to it }, pillFill, stripFill)
    }

    protected fun portraitBottomBar() {
        setScreen(navigating = false, shot = "pill-strip-bar")
        val bar = bounds(COMPACT_BOTTOM_NAV_TAG)
        val mid = (bar.top + bar.bottom) / 2
        val fill = at(bar.left + 4.dp, mid)
        assertLine("the bottom bar's top", px(bar.left + 24.dp), inStart(bar.top), fill)
        assertLineNear("the bottom bar's rounded top-left corner", px(bar.left + arcInset), px(bar.top + arcInset), fill)
        assertLineNear("the bottom bar's rounded top-right corner", px(bar.right - arcInset), px(bar.top + arcInset), fill)
        // RECORD -989 (the owner: "give the side of the bottom navigation strip the outline too. All the way down. Just not the
        // bottom since it's supposed to meld into the android navbar"): its sides carry the line from the corners to its bottom,
        // and its bottom edge none. Until -989 the two side checks here were assertNoLine.
        val low = bar.bottom - 2.dp
        assertLine("the bottom bar's left side", inStart(bar.left), px(mid), fill)
        assertLine("the bottom bar's right side", inEnd(bar.right), px(mid), at(bar.right - 4.dp, mid))
        assertLine("the bottom bar's left side, at its bottom", inStart(bar.left), px(low), at(bar.left + 4.dp, low))
        assertLine("the bottom bar's right side, at its bottom", inEnd(bar.right), px(low), at(bar.right - 4.dp, low))
        assertNoLine("the bottom bar's bottom edge, which melds into the system bar", px(bar.left + 24.dp), inEnd(bar.bottom), at(bar.left + 24.dp, bar.bottom - 4.dp))
        assertNoLine("the bottom bar's bottom edge, right", px(bar.right - 24.dp), inEnd(bar.bottom), at(bar.right - 24.dp, bar.bottom - 4.dp))
    }

    // ── Landscape ──

    protected fun landscapePillAndStrip() {
        setScreen(navigating = false, shot = "pill-strip-rail")
        val pill = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds(STRIP_TAG)
        val rail = bounds(COMPACT_NAVIGATION_RAIL_TAG)
        println("MEASURED $orientation $theme: pill $pill, strip $strip, rail $rail")
        val pillMid = (pill.top + pill.bottom) / 2
        val pillFill = at(pill.left + 4.dp, pillMid)
        val stripFill = at(strip.left + 24.dp, strip.top + 3.dp)
        val railFill = at(rail.left + 6.dp, (strip.top + strip.bottom) / 2)
        assertEquals("the pill meets the strip", pill.right.value, strip.left.value, 0.5f)
        assertEquals("the strip meets the rail", strip.right.value, rail.left.value, 0.5f)

        assertLine("the pill's bottom", px(pill.left + 40.dp), inEnd(pill.bottom), pillFill)
        assertLineNear("the pill's rounded bottom-left corner", px(pill.left + arcInset), px(pill.bottom - arcInset), pillFill)
        // RECORD -988 (the owner: "The pill in landscape isn't fully outlined"): the pill's outer end and top, and the strip's
        // top, carry the line. Until -988 these three were assertNoLine (RECORD -967: no line on the screen's own edge).
        assertLine("the pill's outer end", inStart(pill.left), px(pillMid), pillFill)
        assertLine("the pill's top", px(pill.left + 60.dp), inStart(pill.top), at(pill.left + 60.dp, pill.top + 3.dp))
        assertLineNear("the pill's rounded top-left corner", px(pill.left + arcInset), px(pill.top + arcInset), pillFill)

        assertLine("the strip's bottom", px(strip.left + 24.dp), inEnd(strip.bottom), stripFill)
        assertLine("the strip's top", px(strip.left + 24.dp), inStart(strip.top), at(strip.left + 24.dp, strip.top + 6.dp))

        val y = px(strip.top + 3.dp)
        val join = px(strip.left)
        assertOneLine("the pill and the strip", (join - 9..join + 9).map { it to y }, pillFill, stripFill)
        val railY = px((strip.top + strip.bottom) / 2)
        val railJoin = px(rail.left)
        assertOneLine("the strip and the rail", (railJoin - 9..railJoin + 9).map { it to railY }, stripFill, railFill)
    }

    protected fun landscapeRail() {
        setScreen(navigating = false, shot = "pill-strip-rail")
        val rail = bounds(COMPACT_NAVIGATION_RAIL_TAG)
        val mid = (rail.top + rail.bottom) / 2
        val fill = at(rail.left + 6.dp, mid)
        assertLine("the rail's inner edge", inStart(rail.left), px(mid), fill)
        assertLineNear("the rail's rounded inner top corner", px(rail.left + arcInset), px(rail.top + arcInset), fill)
        assertLineNear("the rail's rounded inner bottom corner", px(rail.left + arcInset), px(rail.bottom - arcInset), fill)
        assertNoLine("the rail's outer side, on the screen's edge", inEnd(rail.right), px(mid), at(rail.right - 6.dp, mid))
        // RECORD -990 (the owner: "can landscape get the same navigation bar outline treatment?"): its top and bottom carry the
        // line out to the screen's edge, as the bottom bar's sides do (-989). Until -990 these carried none (not asserted).
        assertLine("the rail's top", px(rail.left + 40.dp), inStart(rail.top), fill)
        assertLine("the rail's top, at the screen's edge", inEnd(rail.right) - 3, inStart(rail.top), at(rail.right - 6.dp, mid))
        assertLine("the rail's bottom", px(rail.left + 40.dp), inEnd(rail.bottom), fill)
        assertLine("the rail's bottom, at the screen's edge", inEnd(rail.right) - 3, inEnd(rail.bottom), at(rail.right - 6.dp, mid))
    }

    /**
     * RECORD -990 (the owner: "there's still a slight gap in the outline in landscape mode"). Read from the owner's S26 Ultra
     * screenshot: where the strip's rounded end meets the rail, its corner arcs ended one stroke inside the strip while the rail's
     * line runs one stroke inside the rail, so between the two arcs the outline stepped out by a stroke (3 px at xxhdpi) and the
     * strip's fill showed through the step. No map pixels show there, so a check for map pixels on the outline cannot see it.
     * This one follows the outline's inner edge instead: row by row down the piece's end, from inside its fill outward, the first
     * pixel that is not the fill (more than 0.12 from it), and that edge may move by at most [MAX_STEP_PX] from one row to the
     * next. Inside the fill nothing but the outline lies between the piece and the map, so the first such pixel is the line.
     * (A first version classified line pixels by colour and tracked the innermost run; over the stand-in map's grey the arc's
     * outer half and, at night, the map itself read as line, CI run 38528961935, so it is not used.)
     */
    private fun assertEndOutlineContinuous(what: String, piece: DpRect, beyondX: Dp, pieceFill: Color, fromY: Dp, toY: Dp) {
        val x0 = px(piece.right) - px(8.dp)
        val x1 = minOf(px(beyondX) + px(4.dp), image.width - 1)
        var last: Int? = null
        val edges = mutableListOf<Int>()
        for (y in px(fromY)..px(toY)) {
            assertTrue("$what: the row starts inside the piece's fill at ($x0, $y): ${rgb(at(x0, y))}, fill ${rgb(pieceFill)}", abs(lum(at(x0, y)) - lum(pieceFill)) < 0.05f)
            val edge = (x0..x1).firstOrNull { x -> abs(lum(at(x, y)) - lum(pieceFill)) > 0.12f }
            assertTrue("$what: an outline at row $y px between x $x0 and $x1", edge != null)
            edges += edge!!
            last?.let { prev ->
                assertTrue(
                    "$what: the outline's inner edge steps by ${abs(edge - prev)} px between rows ${y - 1} and $y (from $prev to $edge); " +
                        "edges ${edges.joinToString()}",
                    abs(edge - prev) <= MAX_STEP_PX,
                )
            }
            last = edge
        }
        println("MEASURED $orientation $theme $what: inner edges ${edges.joinToString()} (piece right ${px(piece.right)}, scanned to $x1)")
    }

    /**
     * RECORD -990, the walk: around the joined top bar and the rail, every short run across the outline holds an outline pixel,
     * so no stretch shows the map, or a fill, where a line should be. The runs cross: the pill's and the strip's bottoms and tops
     * between their corners, the rail's inner edge from below its top corner to above its bottom corner, and the rail's top
     * and bottom out to the screen's edge.
     */
    private fun assertWalkFindsLine(what: String, runs: List<List<Pair<Int, Int>>>, fillA: Color, fillB: Color) {
        val broken = runs.filter { run -> run.none { (x, y) -> isLine(at(x, y), fillA) || isLine(at(x, y), fillB) } }
        println("MEASURED $orientation $theme walk $what: ${runs.size} runs, ${broken.size} without a line")
        assertTrue("$what: every run across the outline holds a line pixel; ${broken.size} of ${runs.size} do not, first at ${broken.firstOrNull()?.firstOrNull()}", broken.isEmpty())
    }

    /**
     * The runs that walk a piece's rounded right end: along each 16 dp corner, every 3 degrees, the 5 x 5 px square round the
     * point on the line's path (15.5 dp from the corner's centre, so a line drawn a stroke further out, as the shared one is,
     * still falls inside); and down the straight stretch between the corners, a run of 4 px either side of the piece's edge.
     * A first version walked rows down the piece's edge alone, which misses the line wherever the corners curve away from it
     * (CI run 38530371018: 26 of 106 rows "without a line", all in the corners, before and after the fix alike).
     */
    private fun roundedEndRuns(piece: DpRect): List<List<Pair<Int, Int>>> {
        val r = 16.dp
        val path = 15.5.dp
        val cx = px(piece.right - r)
        val corners = listOf(px(piece.top + r) to -1, px(piece.bottom - r) to 1)
        val arcs = corners.flatMap { (cy, dir) ->
            (0..90 step 3).map { deg ->
                val a = Math.toRadians(deg.toDouble())
                val x = cx + (px(path) * kotlin.math.cos(a)).roundToInt()
                val y = cy + dir * (px(path) * kotlin.math.sin(a)).roundToInt()
                (x - 2..x + 2).flatMap { xx -> (y - 2..y + 2).map { yy -> xx to yy } }
                    .filter { (xx, yy) -> xx in 0 until image.width && yy in 0 until image.height }
            }
        }
        val straight = (px(piece.top + r)..px(piece.bottom - r)).map { y ->
            (px(piece.right) - 4..px(piece.right) + 4).filter { it in 0 until image.width }.map { it to y }
        }
        return arcs + straight
    }

    protected fun landscapeOutlineUnbroken() {
        setScreen(navigating = false, shot = "pill-strip-rail")
        val pill = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds(STRIP_TAG)
        val rail = bounds(COMPACT_NAVIGATION_RAIL_TAG)
        val pillFill = at(pill.left + 40.dp, (pill.top + pill.bottom) / 2)
        val stripFill = at(strip.left + 24.dp, (strip.top + strip.bottom) / 2)
        val railFill = at(rail.left + 6.dp, (rail.top + rail.bottom) / 2)
        val r = 16.dp
        fun across(x: Int, edgeY: Int) = (edgeY - 4..edgeY + 4).filter { it in 0 until image.height }.map { x to it }
        fun down(y: Int, edgeX: Int) = (edgeX - 4..edgeX + 4).filter { it in 0 until image.width }.map { it to y }
        assertWalkFindsLine("the pill's top and bottom", (px(pill.left + r)..px(pill.right - 2.dp) step 3).flatMap { listOf(across(it, px(pill.top) + 1), across(it, px(pill.bottom) - 2)) }, pillFill, stripFill)
        assertWalkFindsLine("the strip's top and bottom", (px(strip.left + 2.dp)..px(strip.right - r) step 3).flatMap { listOf(across(it, px(strip.top) + 1), across(it, px(strip.bottom) - 2)) }, stripFill, pillFill)
        assertWalkFindsLine("the rail's inner edge", (px(rail.top + r)..px(rail.bottom - r) step 3).map { down(it, px(rail.left) + 1) }, railFill, stripFill)
        assertWalkFindsLine("the rail's top and bottom", (px(rail.left + r)..px(rail.right) - 1 step 3).flatMap { listOf(across(it, px(rail.top) + 1), across(it, px(rail.bottom) - 2)) }, railFill, railFill)
        assertWalkFindsLine("the pill's outer end", (px(pill.top + r)..px(pill.bottom - r)).map { down(it, px(pill.left) + 1) }, pillFill, pillFill)
        // The stretch the owner located the gap in ("It's on the right part of the telemetry pill"): the strip's end against the
        // rail, its full height, walked across and followed down.
        assertWalkFindsLine("the strip's end against the rail", roundedEndRuns(strip), stripFill, railFill)
        assertEndOutlineContinuous("the strip's end against the rail", strip, rail.left, stripFill, strip.top + 6.dp, strip.bottom - 6.dp)
    }

    protected fun landscapeCardEndUnbroken() {
        setScreen(navigating = true, shot = "navigating")
        val card = bounds(NAVIGATION_HUD_TAG)
        val rail = bounds(COMPACT_NAVIGATION_RAIL_TAG)
        val y = card.top + 30.dp
        val cardFill = at(card.left + 4.dp, y)
        val railFill = at(rail.left + 6.dp, y)
        assertEndOutlineContinuous("the navigation card's end against the rail", card, rail.left, cardFill, card.top + 6.dp, card.top + 40.dp)
    }

    protected fun landscapeCard() {
        setScreen(navigating = true, shot = "navigating")
        val card = bounds(NAVIGATION_HUD_TAG)
        val rail = bounds(COMPACT_NAVIGATION_RAIL_TAG)
        println("MEASURED $orientation $theme: card $card, rail $rail")
        assertEquals("the card meets the rail", card.right.value, rail.left.value, 0.5f)
        val low = card.bottom - 4.dp
        val fill = at(card.left + 4.dp, low)
        // The join is read 30 dp below the card's top, on the rail's straight inner edge. CI run 38507319758 read it at `low`,
        // 4 dp above the screen's bottom, which is inside the rail's rounded inner bottom corner: the run beside the card read
        // the map (0.502), not the rail.
        val joinY = card.top + 30.dp
        val joinFill = at(card.left + 4.dp, joinY)
        val railFill = at(rail.left + 6.dp, joinY)
        assertLine("the card's top", px(card.left + 40.dp), inStart(card.top), fill)
        assertLineNear("the card's rounded top-left corner", px(card.left + arcInset), px(card.top + arcInset), fill)
        assertLine("the card's inner side, facing the map", inStart(card.left), px(low), fill)
        assertNoLine("the card's bottom, on the screen's edge", px(card.left + 4.dp), inEnd(card.bottom), fill)
        val y = px(joinY)
        val join = px(card.right)
        assertOneLine("the card and the rail", (join - 9..join + 9).map { it to y }, joinFill, railFill)
    }

    /**
     * Fullscreen: the pill and the rail slide away, so the strip's side by the pill faces the map and the strip draws it; its
     * side by the rail is then on the window's edge (Robolectric's zero insets) and, since RECORD -988, carries the line too.
     */
    protected fun landscapeFullscreenStrip() {
        setScreen(navigating = false, shot = "pill-strip-rail")
        composeRule.onNodeWithContentDescription("Fullscreen").performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        capture("fullscreen")
        assertFalse("the pill has slid away", shown(SEARCH_ENTRY_BAR_TAG))
        assertFalse("the rail has slid away", shown(COMPACT_NAVIGATION_RAIL_TAG))
        val strip = bounds(STRIP_TAG)
        val window = composeRule.onRoot().getUnclippedBoundsInRoot()
        println("MEASURED $orientation $theme fullscreen: strip $strip, window $window")
        val mid = (strip.top + strip.bottom) / 2
        val fill = at(strip.left + 24.dp, mid)
        assertLine("the strip's side by the slid-away pill", inStart(strip.left), px(mid), fill)
        assertEquals("the strip's other side is on the window's edge", window.right.value, strip.right.value, 0.5f)
        // RECORD -988: the strip all the way round, so its far end on the window's edge carries the line too (was assertNoLine).
        assertLine("the strip's far end, on the window's edge", inEnd(strip.right), px(mid), at(strip.right - 4.dp, mid))
        // RECORD -990: the stretch the owner located the gap in, with the rail slid away: walked across and followed down.
        assertWalkFindsLine("the strip's far end in fullscreen", roundedEndRuns(strip), fill, fill)
        assertEndOutlineContinuous("the strip's far end in fullscreen", strip, strip.right, fill, strip.top + 6.dp, strip.bottom - 6.dp)
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
        const val MAP_TAG = "map-chrome-outline-map"

        /** See [isJoinLine]. */
        const val JOIN_CLEAR_OF_FILLS = 0.18f

        /** See [assertEndOutlineContinuous]: an arc near its end moves well under a pixel a row; the step it guards is 3 px. */
        const val MAX_STEP_PX = 1.5f
    }
}

abstract class MapChromeOutlinePortraitTests(dark: Boolean) : MapChromeOutlinePixelsTests(dark, rotation = null) {
    @Test fun `the pill is outlined all the way round, its sides and top included`() = portraitPill()

    @Test fun `the strip is outlined on its sides and bottom, up into the tuck but not over the pill, and the pill's edge is the one line between them`() = portraitStrip(navigating = false)

    @Test fun `navigating, the strip's card is outlined on its sides and grown bottom, and the pill's edge is still the one line`() = portraitStrip(navigating = true)

    @Test fun `the bottom bar is outlined on its top, rounded corners and sides down to its bottom, not along its bottom`() = portraitBottomBar()
}

abstract class MapChromeOutlineLandscapeTests(dark: Boolean) : MapChromeOutlinePixelsTests(dark, rotation = Surface.ROTATION_90) {
    @Test fun `the pill and the strip are outlined all the way round, with one line on each join`() = landscapePillAndStrip()

    @Test fun `the rail is outlined on its inner edge, corners, top and bottom, not its outer side`() = landscapeRail()

    @Test fun `the navigation card is outlined on its top and inner side, not its bottom, with one line against the rail`() = landscapeCard()

    @Test fun `in fullscreen the strip draws its side by the pill and its far end on the window's edge`() = landscapeFullscreenStrip()

    @Test fun `the outline round the top bar and the rail is unbroken, and the strip's end meets the rail's line without a step`() = landscapeOutlineUnbroken()

    @Test fun `navigating, the card's end meets the rail's line without a step`() = landscapeCardEndUnbroken()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapChromeOutlinePortraitDayTest : MapChromeOutlinePortraitTests(dark = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapChromeOutlinePortraitNightTest : MapChromeOutlinePortraitTests(dark = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapChromeOutlineLandscapeDayTest : MapChromeOutlineLandscapeTests(dark = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapChromeOutlineLandscapeNightTest : MapChromeOutlineLandscapeTests(dark = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapChromeOutlineLandscape780DayTest : MapChromeOutlineLandscapeTests(dark = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapChromeOutlineLandscape780NightTest : MapChromeOutlineLandscapeTests(dark = true)
