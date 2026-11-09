package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.ui.map.MapSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * RECORD -729 (dispatch 2026-09-28-729), as revised by RECORD -732: in a short landscape window, not navigating, the search bar
 * and the compass strip meet at one height, split by a 1 dp line. Since -732 (the owner: "Join moves to fit the strip
 * (Recommended)") the join is the strip's inner edge, the strip as wide as its readouts need whole, not the window's centre.
 * The owner at -729, verbatim: "Landscape mode strip can extend to meet the search bar. The search bar height can change to meet the height of the strip. The two can meet at direct center, and they can
 * be split by a simple vertical line between the two"; shown as steps, "Yes, that's it (Recommended)".
 *
 * Through the real [AvailabilityScreen] at the S22's two landscape windows (780 x 360 and 823 x 384 dp), at ROTATION_90 (the
 * punch-hole, and so the bar, on the left; the rail on the right) and ROTATION_270 (the reverse). Every test first proves the
 * screen saw the rotation it is about. Insets are zero under Robolectric, so the punch-hole-side controls edge is the window's
 * edge here; against a real cut-out it is a device item. A live GPS fix, a fixed 315 degree heading and 3,000 m (9843 ft), the
 * longest readouts the earlier landscape tests use.
 */
abstract class LandscapeBarStripJoinTests {
    val composeRule = createComposeRule()
    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 3_000.0, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private var longPresses = 0
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
        Box(modifier.testTag(MAP_TAG).pointerInput(Unit) { detectTapGestures(onLongPress = { longPresses++ }) })
    }
    private var generation by mutableStateOf(0)
    private var compass: CompassProvider by mutableStateOf(FixedCompass(315f))
    private var rotationSeen: Int? = null

    private fun setScreen(rotation: Int, uiState: AvailabilityUiState = AvailabilityUiState(liveFix = fix)) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            key(generation) {
                AvailabilityScreen(
                    uiState = uiState,
                    compassProvider = compass,
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
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("the screen must actually see the rotation this test is about", rotation, rotationSeen)
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun centreOf(r: DpRect): Dp = (r.left + r.right) / 2
    private fun DpRect.d() = "[%.2f, %.2f][%.2f, %.2f]".format(left.value, top.value, right.value, bottom.value)

    // RECORD -956 (the owner: "One should look exactly line the other"): the bar is on the left and the rail on the right at both rotations, so every
    // case below takes ROTATION_90's branch. Was `rotation == Surface.ROTATION_90`, which sent ROTATION_270 to the mirrored branch
    // (the bar ending at the right edge, its left edge the strip's right). The mirrored branches stay as the record of what was
    // asserted.
    @Suppress("UNUSED_PARAMETER")
    private fun barOnLeft(rotation: Int) = true

    // ── The join: centre, height and the line ──

    private fun assertJoin(rotation: Int) {
        setScreen(rotation)
        val window = composeRule.onRoot().getUnclippedBoundsInRoot()
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds(STRIP_TAG)
        val rail = bounds(COMPACT_NAVIGATION_RAIL_TAG)
        val join = if (barOnLeft(rotation)) strip.left else strip.right
        println("MEASURED rotation $rotation: window ${window.d()}, join ${join.value}, bar ${bar.d()} (${(bar.right - bar.left).value} wide), strip ${strip.d()} (${(strip.right - strip.left).value} wide), rail ${rail.d()}")

        if (barOnLeft(rotation)) {
            assertEquals("the bar starts at the punch-hole (left) edge", window.left.value, bar.left.value, 0.5f)
            assertEquals("the bar's right edge is the strip's inner edge", join.value, bar.right.value, 0.5f)
            assertEquals("the strip ends at the rail's inner edge", rail.left.value, strip.right.value, 0.5f)
        } else {
            assertEquals("the bar ends at the punch-hole (right) edge", window.right.value, bar.right.value, 0.5f)
            assertEquals("the bar's left edge is the strip's inner edge", join.value, bar.left.value, 0.5f)
            assertEquals("the strip starts at the rail's inner edge", rail.right.value, strip.left.value, 0.5f)
        }
        assertEquals("the bar and the strip start at the same top", strip.top.value, bar.top.value, 0.5f)
        assertEquals("the bar is the strip's height", (strip.bottom - strip.top).value, (bar.bottom - bar.top).value, 0.5f)
        assertTrue("the strip keeps its 36 dp floor (${(strip.bottom - strip.top).value})", (strip.bottom - strip.top).value >= 36f - 0.5f)

        // Dispatch 2026-10-12-06 (RECORD -934, the owner confirming "in landscape, the pill and card touch edge to edge (no join
        // line)"): the 1 dp line RECORD -729 drew on the join is gone; the two pieces meet edge to edge, square on that side and
        // rounded on their outer sides. Was: the line, 1 dp wide, on the join, from the bar's top to its bottom.
        assertFalse("no line on the join", shown(LANDSCAPE_BAR_STRIP_LINE_TAG))
    }

    @Test fun `at ROTATION_90 the bar and the strip meet edge to edge at the strip's inner edge at one height, with no line`() = assertJoin(Surface.ROTATION_90)

    @Test fun `at ROTATION_270 the bar and the strip meet edge to edge at the strip's inner edge at one height, with no line`() = assertJoin(Surface.ROTATION_270)

    // ── The three-dot button, by real touches ──

    private fun assertButtonOpensMenu(rotation: Int) {
        setScreen(rotation)
        val strip = bounds(STRIP_TAG)
        val button = bounds(MAP_QUICK_SETTINGS_BUTTON_TAG)
        println("MEASURED rotation $rotation: button ${button.d()} in strip ${strip.d()}")
        assertEquals("36 dp wide: ${button.d()}", 36f, (button.right - button.left).value, 0.5f)
        assertEquals("36 dp tall: ${button.d()}", 36f, (button.bottom - button.top).value, 0.5f)
        assertTrue("at the strip's far right, inside it: ${button.d()} in ${strip.d()}", strip.right - button.right < 1.dp && button.left >= strip.left)
        val inset = 3.dp
        val points = listOf(
            centreOf(button) to (button.top + button.bottom) / 2,
            button.left + inset to button.top + inset,
            button.right - inset to button.top + inset,
            button.left + inset to button.bottom - inset,
            button.right - inset to button.bottom - inset,
        )
        for ((x, y) in points) {
            composeRule.touchAt(x, y)
            assertTrue("a real touch at (${x.value}, ${y.value}) in ${button.d()} opened the menu", shown(MAP_QUICK_SETTINGS_MENU_TAG))
            composeRule.runOnIdle { generation++ }
            composeRule.waitForIdle()
            assertFalse("a fresh screen before the next touch", shown(MAP_QUICK_SETTINGS_MENU_TAG))
        }
    }

    @Test fun `at ROTATION_90 real touches across the three-dot button open the menu`() = assertButtonOpensMenu(Surface.ROTATION_90)

    @Test fun `at ROTATION_270 real touches across the three-dot button open the menu`() = assertButtonOpensMenu(Surface.ROTATION_270)

    // ── The map's long-press reaches through around the join ──

    private fun assertLongPressesAroundJoin(rotation: Int) {
        setScreen(rotation)
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds(STRIP_TAG)
        val centre = if (barOnLeft(rotation)) strip.left else strip.right
        val clickable = composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
            .map { with(composeRule.density) { DpRect(it.boundsInRoot.left.toDp(), it.boundsInRoot.top.toDp(), it.boundsInRoot.right.toDp(), it.boundsInRoot.bottom.toDp()) } }
        // The bar is a Surface and takes every touch on itself; the strip takes touches only on its coordinates and its button.
        val notMap = clickable + bar
        val stripSide = if (barOnLeft(rotation)) 1 else -1
        val points = buildList {
            for (dx in listOf(-12, -4, 0, 4, 12)) {
                add(centre + dx.dp to bar.bottom + 4.dp)
                add(centre + dx.dp to bar.bottom + 16.dp)
            }
            // On the strip itself beside the join: its compass needle's end at ROTATION_90 (map); its button at 270 (skipped).
            for (dx in listOf(2, 6, 10)) add(centre + (stripSide * dx).dp to (strip.top + strip.bottom) / 2)
        }
        var sampled = 0
        val skipped = mutableListOf<String>()
        for ((x, y) in points) {
            if (notMap.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) {
                skipped += "(${x.value}, ${y.value})"
                continue
            }
            val before = longPresses
            val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
            composeRule.onRoot().performTouchInput { longClick(p) }
            composeRule.waitForIdle()
            assertEquals("a long-press at (${x.value}, ${y.value}) beside the join must reach the map", before + 1, longPresses)
            sampled++
        }
        println("MEASURED rotation $rotation: long-presses sampled $sampled, skipped on controls $skipped")
        assertTrue("at least 10 points below the join were sampled, not $sampled", sampled >= 10)
        if (barOnLeft(rotation)) assertTrue("at ROTATION_90 the strip's end at the join was sampled too ($sampled)", sampled >= 12)
    }

    @Test fun `at ROTATION_90 long-presses around the join reach the map`() = assertLongPressesAroundJoin(Surface.ROTATION_90)

    @Test fun `at ROTATION_270 long-presses around the join reach the map`() = assertLongPressesAroundJoin(Surface.ROTATION_270)

    // ── The readouts are whole, in both coordinate formats ──

    private fun layoutOf(tag: String): Pair<String, TextLayoutResult> {
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: ""
        return text to results.single()
    }

    private fun readoutsShown(): List<String> {
        val shownTags = listOf(COMPASS_STRIP_HEADING_TAG, COMPASS_STRIP_ELEVATION_TAG, COMPASS_STRIP_COORDINATES_TAG).filter { shown(it) }
        return shownTags.map { tag ->
            val (text, layout) = layoutOf(tag)
            assertEquals("$tag <$text> is one line", 1, layout.lineCount)
            assertFalse("$tag <$text> is whole, not ellipsised", layout.isLineEllipsized(0))
            assertEquals("$tag <$text> shows every character", text.length, layout.getLineEnd(0, visibleEnd = true))
            text
        }
    }

    private fun assertAllThree(format: String) {
        listOf(COMPASS_STRIP_HEADING_TAG, COMPASS_STRIP_ELEVATION_TAG, COMPASS_STRIP_COORDINATES_TAG).forEach {
            assertTrue("$format: $it is shown", shown(it))
        }
    }

    private fun assertReadoutsWhole(rotation: Int) {
        setScreen(rotation)
        assertAllThree("MGRS")
        val mgrs = readoutsShown()
        val coordinates = bounds(COMPASS_STRIP_COORDINATES_TAG)
        composeRule.touchAt(centreOf(coordinates), (coordinates.top + coordinates.bottom) / 2)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertAllThree("decimal")
        val decimal = readoutsShown()
        println("MEASURED rotation $rotation strip readouts: MGRS $mgrs; decimal $decimal")
        assertTrue("positive control: the touch switched the format (${mgrs.last()} to ${decimal.last()})", mgrs.last() != decimal.last())
        assertTrue("the decimal coordinates read as decimal: ${decimal.last()}", decimal.last().contains("45.52"))
    }

    @Test fun `at ROTATION_90 the strip's heading, altitude and coordinates are whole in both coordinate formats`() = assertReadoutsWhole(Surface.ROTATION_90)

    @Test fun `at ROTATION_270 the strip's heading, altitude and coordinates are whole in both coordinate formats`() = assertReadoutsWhole(Surface.ROTATION_270)

    // ── The bar's floor: its resting text and Clear stay whole ──

    private fun assertBarFloor(rotation: Int, uiState: AvailabilityUiState, withClear: Boolean) {
        setScreen(rotation, uiState)
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        assertBarTextWhole(rotation, bar)
        if (withClear) {
            val clear = bounds(SEARCH_BAR_CLEAR_TAG)
            assertTrue("Clear ${clear.d()} lies inside the bar ${bar.d()}", clear.left >= bar.left - 0.5.dp && clear.right <= bar.right + 0.5.dp)
        }
        assertAllThree("with the bar's floor")
    }

    @Test fun `at ROTATION_90 the bar's resting text is whole beside the whole strip`() = assertBarFloor(Surface.ROTATION_90, AvailabilityUiState(liveFix = fix), withClear = false)

    @Test fun `at ROTATION_270 with a search showing, the bar's summary and Clear are whole beside the whole strip`() =
        assertBarFloor(Surface.ROTATION_270, AvailabilityUiState(liveFix = fix, region = com.zynergylabs.forager.app.domain.model.Region(lat = 45.52, lng = -122.68, radiusKm = 15)), withClear = true)

    /**
     * RECORD -732: where the bar's floor and the whole strip cannot both fit, the bar keeps its floor and the strip gives way,
     * readoutsFitBeside dropping from the front; the coordinates never drop. A long species search makes the bar's summary
     * long enough that this happens in both windows (positive control: the heading is dropped).
     */
    @Test fun `at ROTATION_90 a long search keeps the bar's floor and the strip drops from the front, coordinates whole`() {
        val state = AvailabilityUiState(
            liveFix = fix,
            region = com.zynergylabs.forager.app.domain.model.Region(lat = 45.52, lng = -122.68, radiusKm = 15),
            taxonFilter = com.zynergylabs.forager.app.domain.model.TaxonFilter.SpecificTaxon(48473L, "Hericium erinaceus"),
        )
        assertBarFloorKept(Surface.ROTATION_90, state)
    }

    private fun assertBarFloorKept(rotation: Int, state: AvailabilityUiState) {
        setScreen(rotation, state)
        val bar = bounds(SEARCH_ENTRY_BAR_TAG)
        val strip = bounds(STRIP_TAG)
        val shownReadouts = readoutsShown()
        println("MEASURED rotation $rotation long search: bar ${bar.d()} (${(bar.right - bar.left).value} wide), strip ${strip.d()} (${(strip.right - strip.left).value} wide), readouts $shownReadouts")
        assertTrue("positive control: the floor bound, so the heading gave way ($shownReadouts)", !shown(COMPASS_STRIP_HEADING_TAG))
        assertTrue("the coordinates are shown", shown(COMPASS_STRIP_COORDINATES_TAG))
        assertBarTextWhole(rotation, bar)
        val clear = bounds(SEARCH_BAR_CLEAR_TAG)
        assertTrue("Clear ${clear.d()} lies inside the bar ${bar.d()}", clear.left >= bar.left - 0.5.dp && clear.right <= bar.right + 0.5.dp)
    }

    private fun assertBarTextWhole(rotation: Int, bar: DpRect) {
        val texts = composeRule.onAllNodes(
            androidx.compose.ui.test.hasAnyAncestor(androidx.compose.ui.test.hasTestTag(SEARCH_ENTRY_BAR_TAG)) and
                androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        val summary = texts.mapNotNull { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: return@mapNotNull null
            text.takeIf { it.contains(" · ") }?.let { it to node }
        }
        assertEquals("positive control: one resting text in the bar (${texts.size} texts)", 1, summary.size)
        val (text, node) = summary.single()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.single()
        println("MEASURED rotation $rotation bar ${bar.d()} (${(bar.right - bar.left).value} wide) resting text <$text>: lines ${layout.lineCount}, visible ${layout.getLineEnd(0, visibleEnd = true)} of ${text.length}")
        assertEquals("<$text> is one line", 1, layout.lineCount)
        assertEquals("<$text> shows every character", text.length, layout.getLineEnd(0, visibleEnd = true))
    }

    // ── RECORD -735: the crossed-out compass, in the room the value takes ──

    /**
     * The owner: "Same crossed-out icon (Recommended)". With no compass, then an unreliable one, the heading slot shows the
     * crossed-out compass (Icons.Filled.ExploreOff) labelled "Compass unavailable" or "Compass unreliable" for a screen reader,
     * whole, beside the altitude and the coordinates; and the join sits exactly where it does with a heading (the strip is
     * sized from the value form only). The same screen is composed afresh for each compass.
     */
    private fun assertStatusIcon(rotation: Int) {
        setScreen(rotation)
        val withHeading = bounds(STRIP_TAG)
        assertEquals("positive control: a heading value first", "315° NW", layoutOf(COMPASS_STRIP_HEADING_TAG).first)
        assertFalse("positive control: no icon with a heading", shown(COMPASS_STRIP_HEADING_ICON_TAG))
        for ((provider, label) in listOf(NoCompass to "Compass unavailable", UnreliableCompass to "Compass unreliable")) {
            composeRule.runOnIdle { compass = provider; generation++ }
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(2_000)
            composeRule.waitForIdle()
            val strip = bounds(STRIP_TAG)
            val icon = bounds(COMPASS_STRIP_HEADING_ICON_TAG)
            val description = composeRule.onNodeWithTag(COMPASS_STRIP_HEADING_ICON_TAG, useUnmergedTree = true).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("")
            val shownReadouts = readoutsShown()
            println("MEASURED rotation $rotation <$label>: icon ${icon.d()}, strip ${strip.d()}, with a heading ${withHeading.d()}, readouts $shownReadouts")
            assertEquals("the icon is labelled <$label>", label, description)
            assertFalse("<$label>: no heading words in the slot", shown(COMPASS_STRIP_HEADING_TAG))
            assertEquals("<$label>: the icon is the 14 sp line's 20 dp square", 20f, (icon.right - icon.left).value, 0.5f)
            assertTrue("<$label>: the icon ${icon.d()} lies whole inside the strip ${strip.d()}", icon.left >= strip.left && icon.right <= strip.right && icon.top >= strip.top && icon.bottom <= strip.bottom)
            assertTrue("<$label>: altitude and coordinates still show, whole ($shownReadouts)", shown(COMPASS_STRIP_ELEVATION_TAG) && shown(COMPASS_STRIP_COORDINATES_TAG))
            assertEquals("<$label>: the strip's left edge is where it is with a heading", withHeading.left.value, strip.left.value, 0.5f)
            assertEquals("<$label>: the strip's right edge is where it is with a heading", withHeading.right.value, strip.right.value, 0.5f)
            val bar = bounds(SEARCH_ENTRY_BAR_TAG)
            val join = if (barOnLeft(rotation)) strip.left else strip.right
            assertEquals("<$label>: the bar still ends at the join", join.value, (if (barOnLeft(rotation)) bar.right else bar.left).value, 0.5f)
        }
    }

    @Test fun `at ROTATION_90 with no compass or an unreliable one the crossed-out compass shows, labelled, and the join stays put`() = assertStatusIcon(Surface.ROTATION_90)

    @Test fun `at ROTATION_270 with no compass or an unreliable one the crossed-out compass shows, labelled, and the join stays put`() = assertStatusIcon(Surface.ROTATION_270)

    private object NoCompass : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(null)
    }

    /** 90 degrees of estimated uncertainty, well past CompassTrustJudge's 15 degree entry: unreliable. */
    private object UnreliableCompass : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(315f, HeadingUncertainty.Estimated(90f), 0L))
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
        const val MAP_TAG = "landscape-bar-strip-map"
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeBarStripJoin780Test : LandscapeBarStripJoinTests()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeBarStripJoin823Test : LandscapeBarStripJoinTests()
