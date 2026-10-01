package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.map.JOURNAL_ENTRIES_CHIP_TAG
import com.zynergylabs.forager.app.ui.map.MAP_LEGEND_CHIP_TAG
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.fanout.FanOutTestScene
import com.zynergylabs.forager.app.ui.map.fanout.MapTapHandler
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutBackHandler
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutHost
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutState
import com.zynergylabs.forager.app.ui.map.fanout.RecordingSinks
import com.zynergylabs.forager.app.ui.map.fanout.memberPositionDp
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import com.zynergylabs.forager.app.ui.map.rememberMapFanSpace
import com.zynergylabs.forager.app.ui.map.trackMapFanSpace
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
 * Stacks tapped next to the screen's edges and controls fan out on screen and clear of them (continuation
 * `-208`; the owner's "2 A"), through the real [AvailabilityScreen]: the real icon cluster, legend chip
 * and Journal chip, laid out at the size under test, measured by the screen's own composables and read
 * by the same [rememberMapFanSpace] `SightingsMap` uses. The map is a stub that takes real pointer input
 * and hands each tap to the real [MapTapHandler] over a fake probe. Every touch is a real coordinate
 * touch on the app's window, and the checks compare against the controls' own unclipped bounds, not the
 * registry the map read.
 *
 * What it does not reach: the real `MapView` (a fan drawn by MapLibre), the device's system-bar insets
 * (Robolectric reports zero), and any control not registered: the compass strip, the search bar, the
 * bottom navigation and the rail are not keep-outs, so a stack near them can fan under them.
 */
abstract class MarkerFanOutPlacementScreenTests(private val rotation: Int) {

    private val gapDp = 12f

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val fan = MarkerFanOutState()
    private val sinks = RecordingSinks()
    private var scene: FanOutTestScene? = null

    private val slot: MapSlot = { _, _, renderMode, _, _, _, _, _, modifier ->
        val feed = renderMode.forecast
        LaunchedEffect(feed?.groupsByLayer) {
            feed?.onCellsShown?.invoke(LAYOUT_FIXES_DATES.filterKeys { it in feed.groupsByLayer })
        }
        val density = LocalDensity.current.density
        val space = rememberMapFanSpace()
        val probe = remember { FanOutTestScene(density).also { scene = it; it.hidden = { fan.members.map { m -> m.key }.toSet() } } }
        val handler = remember { MapTapHandler(fan, probe, { orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT) }, sinks, space) }
        MarkerFanOutHost(fan)
        MarkerFanOutBackHandler(fan)
        Box(
            modifier
                .testTag(LAYOUT_FIXES_MAP_TAG)
                .trackMapFanSpace(space)
                .pointerInput(Unit) { detectTapGestures { handler.onMapTap(LatLng(0.0, 0.0), it.x, it.y) } },
        )
    }

    private fun setScreen(shownEntry: Boolean = true) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        var rotationSeen: Int? = null
        val store = FixedForecastStore(BOTH_FORECAST_GROUPS)
        val viewModel = mapLayersViewModel(store = store)
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            MapLayersTestScreen(viewModel, slot, store, cartographyUiState = if (shownEntry) LAYOUT_FIXES_SHOWN_ENTRY_STATE else CartographyUiState())
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("the screen must see the pinned rotation", rotation, rotationSeen)
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    /** The compass strip's own text (its heading, or its no-fix line), which is inside the strip. */
    private fun stripText(): DpRect? = barBounds(COMPASS_STRIP_HEADING_TAG) ?: barBounds(COMPASS_STRIP_NO_FIX_TAG)

    /** A bar drawn over the map (the bottom navigation, the landscape rail), when this layout has it. */
    private fun barBounds(tag: String): DpRect? =
        if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()) null else bounds(tag)

    /** The part of the map no bar covers, horizontally: edge spots are taken from it, since a touch on a bar never reaches the map. */
    private fun playfieldX(): Pair<Float, Float> {
        val map = bounds(LAYOUT_FIXES_MAP_TAG)
        var left = map.left.value
        var right = map.right.value
        barBounds(COMPACT_NAVIGATION_RAIL_TAG)?.let { rail ->
            if (rail.left.value + rail.right.value < map.left.value + map.right.value) left = maxOf(left, rail.right.value) else right = minOf(right, rail.left.value)
        }
        return left to right
    }

    /**
     * A height for an edge spot at [x]: the one nearest the map's middle where a [margin] dp ring round the spot touches
     * no control and no bar, and no top chrome (the top 110 dp holds the search bar and the compass strip); `null` when
     * the edge has none (a landscape cluster can cover a whole edge).
     */
    private fun freeY(x: Float, margin: Float): Float? {
        val map = bounds(LAYOUT_FIXES_MAP_TAG)
        val boxes = listOfNotNull(
            bounds(MAP_ICON_CLUSTER_TAG), bounds(MAP_LEGEND_CHIP_TAG), bounds(JOURNAL_ENTRIES_CHIP_TAG),
            barBounds(COMPACT_BOTTOM_NAV_TAG), barBounds(COMPACT_NAVIGATION_RAIL_TAG),
        )
        val mid = (map.top.value + map.bottom.value) / 2
        val candidates = (0..80).flatMap { listOf(mid + it * 10f, mid - it * 10f) }.filter { it > map.top.value + 110f && it < map.bottom.value - 40f }
        return candidates.firstOrNull { y -> boxes.none { b -> x > b.left.value - margin && x < b.right.value + margin && y > b.top.value - margin && y < b.bottom.value + margin } }
    }

    /** A spot [gap] dp beside [box] on whichever side has room for a 60 dp ring's worth of margin, else the other. */
    private fun beside(box: DpRect): Float {
        val (left, right) = playfieldX()
        return if (box.left.value - gapDp - left >= 60f) box.left.value - gapDp else box.right.value + gapDp
    }

    private fun overlaps(a: DpRect, b: DpRect) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun DpRect.contains(x: Float, y: Float) = x > left.value && x < right.value && y > top.value && y < bottom.value

    /**
     * Puts [count] photos at ([x], [y]) dp on the app's window, touches there for real, lets the fan open,
     * and checks it: every touch square inside the map and outside the cluster, the legend and the Journal
     * chip; then a real touch at the centre and at four points across each fanned marker opens that marker's
     * own bubble.
     */
    private fun fanAt(name: String, x: Float, y: Float, count: Int) {
        val map = bounds(LAYOUT_FIXES_MAP_TAG)
        val scene = checkNotNull(scene) { "the map stub was never composed" }
        val density = composeRule.density.density
        val ids = (1..count).map { "$name-$it" }
        ids.forEach { scene.addAtScreen(MapLayerIds.PHOTOS, it, (x - map.left.value) * density, (y - map.top.value) * density) }
        sinks.events.clear()

        composeRule.touchAt(x.dp, y.dp)
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertTrue("$name: the touch at ($x, $y) opened a fan", fan.isOpen)
        assertEquals("$name: no bubble yet", emptyList<String>(), sinks.events)

        val controls = listOfNotNull(
            "cluster" to bounds(MAP_ICON_CLUSTER_TAG),
            "legend" to bounds(MAP_LEGEND_CHIP_TAG),
            barBounds(JOURNAL_ENTRIES_CHIP_TAG)?.let { "journal chip" to it },
            barBounds(COMPACT_BOTTOM_NAV_TAG)?.let { "bottom nav" to it },
            barBounds(COMPACT_NAVIGATION_RAIL_TAG)?.let { "rail" to it },
            barBounds(SEARCH_ENTRY_BAR_TAG)?.let { "search bar" to it },
            stripText()?.let { "compass strip (its text)" to it },
        )
        val half = 24f
        val placed = fan.members.map { m ->
            val at = memberPositionDp(m, fan.progress)
            m to ((at.xDp + map.left.value) to (at.yDp + map.top.value))
        }
        for ((m, at) in placed) {
            val square = DpRect((at.first - half).dp, (at.second - half).dp, (at.first + half).dp, (at.second + half).dp)
            assertTrue("$name/${m.key.featureId}: inside the map $map, square $square", square.left >= map.left && square.top >= map.top && square.right <= map.right && square.bottom <= map.bottom)
            controls.forEach { (cName, c) -> assertTrue("$name/${m.key.featureId}: clear of the $cName $c, square $square", !overlaps(square, c)) }
        }
        for ((m, at) in placed) {
            for ((dx, dy) in listOf(0f to 0f, -20f to -20f, 20f to -20f, -20f to 20f, 20f to 20f)) {
                if (!fan.isOpen) {
                    // The fan folds on a tap on one of its icons (dispatch 2026-09-28-381): let it finish, and tap the stack again.
                    composeRule.mainClock.advanceTimeBy(1_000)
                    composeRule.waitForIdle()
                    composeRule.touchAt(x.dp, y.dp)
                    composeRule.mainClock.advanceTimeBy(1_000)
                    composeRule.waitForIdle()
                    assertTrue("$name: the stack fanned again", fan.isOpen)
                }
                sinks.events.clear()
                composeRule.touchAt((at.first + dx).dp, (at.second + dy).dp)
                assertEquals("$name: a real touch $dx,$dy from ${m.key.featureId} at $at", listOf("feature:${MapLayerIds.PHOTOS}:${m.key.featureId}"), sinks.events)
                assertFalse("$name: the fan folds on that one tap", fan.isOpen)
            }
        }
        fan.fold()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        scene.markers.clear()
        assertTrue("$name: folded home", fan.members.isEmpty())
    }

    @Test
    fun `a stack tapped near the left and right edges fans on screen and clear of every control`() {
        setScreen()
        val (left, right) = playfieldX()
        var ran = 0
        listOf("left-edge" to left + 10f, "right-edge" to right - 10f).forEach { (name, x) ->
            val y = freeY(x, 24f) ?: freeY(x, 6f)
            if (y != null) {
                fanAt(name, x, y, 3)
                fanAt("$name-spiral", x, y, 10)
                ran++
            }
        }
        assertTrue("at least one edge is free of controls at this size", ran >= 1)
    }

    @Test
    fun `a stack tapped beside the icon cluster fans clear of it`() {
        setScreen()
        val c = bounds(MAP_ICON_CLUSTER_TAG)
        val midY = (c.top.value + c.bottom.value) / 2
        fanAt("beside-cluster", beside(c), midY, 4)
        val map = bounds(LAYOUT_FIXES_MAP_TAG)
        // Above it where there is room under the top chrome, else below it (short landscape).
        val y = if (c.top.value - map.top.value >= 200f) c.top.value - 12f else c.bottom.value + 12f
        fanAt("above-or-below-cluster", (c.left.value + c.right.value) / 2, y, 4)
    }

    @Test
    fun `a stack tapped beside the legend fans clear of it`() {
        setScreen()
        val l = bounds(MAP_LEGEND_CHIP_TAG)
        fanAt("above-legend", (l.left.value + l.right.value) / 2, l.top.value - 12f, 4)
        fanAt("beside-legend", beside(l), (l.top.value + l.bottom.value) / 2, 3)
    }

    @Test
    fun `a stack tapped just under the compass strip, with no chip row, fans clear of the strip`() {
        setScreen(shownEntry = false)
        val heading = checkNotNull(stripText()) { "the compass strip shows neither its heading nor its no-fix line" }
        // The strip's own bottom is not measurable from outside; a touch well under its heading is beneath it.
        fanAt("under-strip", (heading.left.value + heading.right.value) / 2, heading.bottom.value + 30f, 4)
    }

    @Test
    fun `a stack tapped beside the Journal chip fans clear of it`() {
        setScreen()
        val j = bounds(JOURNAL_ENTRIES_CHIP_TAG)
        fanAt("below-chip", (j.left.value + j.right.value) / 2, j.bottom.value + 12f, 4)
        fanAt("beside-chip", beside(j), (j.top.value + j.bottom.value) / 2, 3)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MarkerFanOutPlacementPhonePortraitTest : MarkerFanOutPlacementScreenTests(Surface.ROTATION_0)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class MarkerFanOutPlacementPhoneLandscapeTest : MarkerFanOutPlacementScreenTests(Surface.ROTATION_90)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-mdpi")
class MarkerFanOutPlacementTabletTest : MarkerFanOutPlacementScreenTests(Surface.ROTATION_0)
