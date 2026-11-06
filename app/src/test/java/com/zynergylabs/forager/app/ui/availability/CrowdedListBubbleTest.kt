package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.zynergylabs.forager.app.domain.CrowdedIconsMode
import com.zynergylabs.forager.app.domain.CrowdedIconsPreferenceRepository
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.log.RECORD_DETAILS_SHEET_TAG
import com.zynergylabs.forager.app.ui.map.BUBBLE_TAIL_LENGTH
import com.zynergylabs.forager.app.ui.map.CROWDED_BUBBLE_MAX_CARD_HEIGHT
import com.zynergylabs.forager.app.ui.map.CROWDED_LIST_CLOSE_TAG
import com.zynergylabs.forager.app.ui.map.CROWDED_LIST_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_CLOSE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DETAILS_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.crowdedListRowTag
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.TAP_BOX_DP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.hypot
import kotlin.math.max

/**
 * Dispatch 2026-11-04-02 (RECORD -2039, -2040): Settings > Map > "Crowded icons" = List, and a tapped stack's list is a floating
 * bubble, not a card across the bottom. On the real [AvailabilityScreen] with the real ViewModel reading the stored mode, at a
 * fixed clock ([ROUTE_CARD_NOON_CLOCK], D43's lesson), every touch a real one at screen coordinates:
 *
 *  - A1: the bubble's tip is on the stack (its card within the tail's length of it), it follows the stack when the user pans
 *    (S2: a pan keeps it), and it keeps clear of the icon cluster (S6);
 *  - S1: its card is no taller than [CROWDED_BUBBLE_MAX_CARD_HEIGHT] and inside the map;
 *  - a 10-record stack's rows scroll inside it;
 *  - A2 with S3: a row opens that record's bubble in the same place; Back returns to the list; the record bubble's X, a tap on
 *    the map, or one of its actions closes both;
 *  - X and a tap on the map close the list.
 *
 * Each window runs it, navigating (the screenshots' case) in portrait and landscape at fonts 1.0 and 2.0, and in portrait not
 * navigating. The map is [CrowdedStackStubMap]: the real tap handler and crowded-stack zoom over a scene; a pan there moves the
 * scene and lets the host's own [com.zynergylabs.forager.app.ui.map.fanout.CrowdedStackHost.followList] place the bubble, with
 * the scene's projection standing in for MapLibre's (the MapLibre call site is device-only).
 */
abstract class CrowdedListBubbleTests(
    /** Where the stack is drawn, dp of the map, in this window: clear of the strip, the navigation card and the cluster. */
    private val spotXDp: Float,
    private val spotYDp: Float,
    /** The test's pan, dp. */
    private val panDp: Offset,
    private val navigating: Boolean,
) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val map = CrowdedStackStubMap(spotXDp, spotYDp, VIEW_DP, VIEW_DP)
    private val finds = (0 until 10).map { i -> BUBBLE_FIND.copy(id = "find-$i", ownIdentification = "Chanterelle %02d".format(i)) }
    private val waypoints = (1..3).map { i -> Waypoint("wp-$i", 45.3676, -121.6919, null, "Waypoint $i", "", 1_700_000_000_000L + i) }

    private val density get() = composeRule.density.density

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.3676, lng = -121.6919, altitude = 50.0, accuracyMeters = 8f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val origin = Waypoint(id = "origin", lat = 45.3776, lng = -121.6919, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1")

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun show() {
        val viewModel = mapLayersViewModel(store = OneCellStore(), crowdedIcons = StoredListMode())
        composeRule.setContent {
            MapLayersTestScreen(
                viewModel = viewModel,
                mapSlot = map.slot,
                store = OneCellStore(),
                logUiState = MushroomLogUiState(entries = finds),
                waypoints = waypoints,
                uiStateTransform = { if (navigating) it.copy(liveFix = fix) else it },
                navigation = if (navigating) {
                    AvailabilityScreenNavigation(isReturning = true, onToggleReturning = {}, navigationTarget = origin)
                } else {
                    AvailabilityScreenNavigation()
                },
                currentTime = ROUTE_CARD_NOON_CLOCK,
            )
        }
        settle()
        assertEquals("positive control: the ViewModel read List mode", CrowdedIconsMode.LIST, viewModel.uiState.value.crowdedIconsMode)
    }

    private fun stackOf(layer: String, ids: List<String>) {
        val s = checkNotNull(map.scene)
        ids.forEach { s.add(layer, it, s.centre.lat, s.centre.lng) }
    }

    private fun tenFinds() = stackOf(MapLayerIds.FINDS, finds.map { it.id })

    private fun mapBounds(): Rect = composeRule.onNodeWithTag(CROWDED_STUB_MAP_TAG).fetchSemanticsNode().boundsInRoot

    /** Where the stack is now, in the root's pixels: its spot, moved by every pan so far. */
    private fun stackInRoot(): Offset {
        val s = checkNotNull(map.scene)
        return mapBounds().topLeft + Offset(spotXDp * density + s.panXPx, spotYDp * density + s.panYPx)
    }

    private fun tapTheStack() {
        val m = mapBounds()
        val at = stackInRoot() - m.topLeft
        composeRule.onNodeWithTag(CROWDED_STUB_MAP_TAG).performTouchInput { click(at) }
        settle()
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** How far [point] is from [rect], px: zero inside it. */
    private fun distance(point: Offset, rect: Rect): Float {
        val dx = max(max(rect.left - point.x, 0f), point.x - rect.right)
        val dy = max(max(rect.top - point.y, 0f), point.y - rect.bottom)
        return hypot(dx, dy)
    }

    /** The card's tail reaches the stack: the card is within the tail's length of it (MapBubble.kt; the tip is on the anchor). */
    private fun assertTipOnTheStack(what: String, tag: String) {
        val card = bounds(tag)
        val stack = stackInRoot()
        val reach = (BUBBLE_TAIL_LENGTH.value + 1f) * density
        assertTrue("$what: the card $card is ${distance(stack, card) / density} dp from the stack at $stack, more than the tail's ${BUBBLE_TAIL_LENGTH}", distance(stack, card) <= reach)
    }

    /** A real touch at [fraction] across [tag]'s own bounds. */
    private fun touch(tag: String, fraction: Offset = Offset(0.5f, 0.5f)) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * fraction.x, height * fraction.y)) }
        settle()
    }

    /**
     * A real tap on empty map: the first of a few places around the stack that is in the map, outside its 48 dp tap box and
     * outside every bubble shown, with its own positive controls, the last that the tap reached the map.
     */
    private fun tapEmptyMap() {
        val m = mapBounds()
        val stack = stackInRoot()
        val cards = listOf(CROWDED_LIST_TAG, MAP_BUBBLE_TAG).filter { shown(it) }.map { bounds(it) }
        val margin = 24f * density
        val candidates = listOf(Offset(0f, 60f), Offset(0f, -60f), Offset(120f, 0f), Offset(-120f, 0f), Offset(120f, 60f), Offset(-120f, 60f), Offset(0f, 120f))
            .map { stack + it * density }
        val root = candidates.firstOrNull { p ->
            p.x > m.left + margin && p.x < m.right - margin && p.y > m.top + margin && p.y < m.bottom - margin && cards.none { it.contains(p) }
        }
        checkNotNull(root) { "positive control: an empty place on the map near the stack $stack, outside $cards" }
        val inMap = root - m.topLeft
        val s = checkNotNull(map.scene)
        assertTrue("positive control: no marker within the tap box at $inMap", s.hitsInBox(inMap.x, inMap.y, TAP_BOX_DP / 2f * density, listOf(MapLayerIds.FINDS, MapLayerIds.WAYPOINTS)).isEmpty())
        val before = map.mapTaps
        composeRule.onNodeWithTag(CROWDED_STUB_MAP_TAG).performTouchInput { click(inMap) }
        settle()
        assertEquals("positive control: the tap at $root reached the map", before + 1, map.mapTaps)
    }

    @Test
    fun `the bubble's tip is on the stack, it follows a pan, and it keeps clear of the icon cluster`() {
        show()
        tenFinds()
        tapTheStack()
        composeRule.onNodeWithTag(CROWDED_LIST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("10 records at this spot").assertIsDisplayed()
        assertTipOnTheStack("opened", CROWDED_LIST_TAG)
        val cluster = bounds(MAP_ICON_CLUSTER_TAG)
        assertFalse("the bubble ${bounds(CROWDED_LIST_TAG)} is clear of the icon cluster $cluster (S6)", bounds(CROWDED_LIST_TAG).overlaps(cluster))
        val before = bounds(CROWDED_LIST_TAG)

        composeRule.runOnUiThread { map.pan(panDp.x * density, panDp.y * density) }
        settle()

        assertTrue("a pan keeps the bubble (S2)", shown(CROWDED_LIST_TAG))
        assertTipOnTheStack("after the pan", CROWDED_LIST_TAG)
        assertTrue("the bubble moved with the stack: $before, then ${bounds(CROWDED_LIST_TAG)}", bounds(CROWDED_LIST_TAG) != before)
        assertFalse("still clear of the icon cluster", bounds(CROWDED_LIST_TAG).overlaps(bounds(MAP_ICON_CLUSTER_TAG)))
    }

    @Test
    fun `the bubble is no taller than the standard bubble's cap, and inside the map`() {
        show()
        tenFinds()
        tapTheStack()
        val card = bounds(CROWDED_LIST_TAG)
        val heightDp = card.height / density
        assertTrue("positive control: a card is drawn, $heightDp dp", heightDp > 0f)
        assertTrue("the card is $heightDp dp, at most $CROWDED_BUBBLE_MAX_CARD_HEIGHT", heightDp <= CROWDED_BUBBLE_MAX_CARD_HEIGHT.value + 0.5f)
        val m = mapBounds()
        assertTrue("the card $card is inside the map $m", card.top >= m.top && card.bottom <= m.bottom && card.left >= m.left && card.right <= m.right)
    }

    @Test
    fun `a 10-record stack scrolls inside the bubble`() {
        show()
        tenFinds()
        tapTheStack()
        val last = crowdedListRowTag(FanKey(MapLayerIds.FINDS, "find-9"))
        fun lastShownInCard(): Boolean = composeRule.onAllNodesWithTag(last).fetchSemanticsNodes().firstOrNull()?.let { node ->
            bounds(CROWDED_LIST_TAG).contains(node.boundsInRoot.center)
        } ?: false
        assertFalse("positive control: the last row is not in view at first", lastShownInCard())
        val card = bounds(CROWDED_LIST_TAG)
        val taps = map.mapTaps

        repeat(5) {
            if (!lastShownInCard()) {
                composeRule.onNodeWithTag(CROWDED_LIST_TAG).performTouchInput { swipeUp(startY = height * 0.9f, endY = height * 0.3f) }
                settle()
            }
        }

        assertTrue("the last row scrolled into the bubble", lastShownInCard())
        assertTrue("the bubble stayed open", shown(CROWDED_LIST_TAG))
        assertEquals("the bubble did not move", card, bounds(CROWDED_LIST_TAG))
        assertEquals("no touch reached the map", taps, map.mapTaps)
    }

    @Test
    fun `a row opens its record's bubble in the same place, Back returns to the list, and the record bubble's X closes both`() {
        show()
        tenFinds()
        tapTheStack()
        val row = crowdedListRowTag(FanKey(MapLayerIds.FINDS, "find-0"))
        // A finger is not a point (CLAUDE.md): five places across the row, each tried, and each miss named.
        val missed = mutableListOf<String>()
        for (point in listOf(Offset(0.1f, 0.5f), Offset(0.5f, 0.5f), Offset(0.9f, 0.5f), Offset(0.5f, 0.15f), Offset(0.5f, 0.85f))) {
            touch(row, point)
            if (!shown(MAP_BUBBLE_TAG)) {
                missed += "at $point: no record bubble, the list ${if (shown(CROWDED_LIST_TAG)) "still up" else "closed"}"
                continue
            }
            composeRule.onNodeWithText("Chanterelle 00").assertIsDisplayed()
            assertFalse("the record's bubble replaced the list", shown(CROWDED_LIST_TAG))
            assertTipOnTheStack("the record's bubble", MAP_BUBBLE_TAG)
            back()
            assertFalse("Back closed the record's bubble", shown(MAP_BUBBLE_TAG))
            assertTrue("and returned to the list", shown(CROWDED_LIST_TAG))
        }
        assertTrue("${missed.size} of 5 touches on the row opened nothing:\n" + missed.joinToString("\n"), missed.isEmpty())

        touch(row)
        assertTrue("positive control: the record's bubble is up", shown(MAP_BUBBLE_TAG))
        touch(MAP_BUBBLE_CLOSE_TAG)

        assertFalse("X closed the record's bubble", shown(MAP_BUBBLE_TAG))
        assertFalse("and the list with it (S3)", shown(CROWDED_LIST_TAG))
    }

    @Test
    fun `X closes the list, and a tap on the map closes it`() {
        show()
        tenFinds()
        tapTheStack()
        assertTrue("positive control: the list is up", shown(CROWDED_LIST_TAG))
        assertTrue("the bubble has its X", shown(CROWDED_LIST_CLOSE_TAG))
        touch(CROWDED_LIST_CLOSE_TAG)
        assertFalse("X closed it", shown(CROWDED_LIST_TAG))

        tapTheStack()
        assertTrue("positive control: the list is up again", shown(CROWDED_LIST_TAG))
        tapEmptyMap()
        assertFalse("a tap on the map closed it", shown(CROWDED_LIST_TAG))
    }

    @Test
    fun `a tap on the map from a row's bubble closes both`() {
        show()
        tenFinds()
        tapTheStack()
        touch(crowdedListRowTag(FanKey(MapLayerIds.FINDS, "find-0")))
        assertTrue("positive control: the record's bubble is up", shown(MAP_BUBBLE_TAG))

        tapEmptyMap()

        assertFalse("the record's bubble closed", shown(MAP_BUBBLE_TAG))
        assertFalse("and the list with it (S3)", shown(CROWDED_LIST_TAG))
    }

    @Test
    fun `an action in a row's bubble closes both`() {
        show()
        stackOf(MapLayerIds.WAYPOINTS, waypoints.map { it.id })
        tapTheStack()
        composeRule.onNodeWithText("3 records at this spot").assertIsDisplayed()
        touch(crowdedListRowTag(FanKey(MapLayerIds.WAYPOINTS, "wp-1")))
        assertTrue("positive control: the waypoint's bubble is up", shown(MAP_BUBBLE_TAG))

        touch(MAP_BUBBLE_DETAILS_TAG)

        assertTrue("positive control: Details opened its sheet", shown(RECORD_DETAILS_SHEET_TAG))
        assertFalse("positive control: the waypoint's bubble closed", shown(MAP_BUBBLE_TAG))
        // With no bubble up, a list still held would show again here: it does not, because the action closed it (S3).
        assertFalse("the list closed with the bubble (S3)", shown(CROWDED_LIST_TAG))
    }

    /** "Crowded icons" stored as List. */
    private class StoredListMode : CrowdedIconsPreferenceRepository {
        override suspend fun getCrowdedIconsMode(unknown: (String) -> Unit): Result<CrowdedIconsMode?> = Result.success(CrowdedIconsMode.LIST)
        override suspend fun setCrowdedIconsMode(mode: CrowdedIconsMode): Result<Unit> = Result.success(Unit)
    }

    private companion object {
        /** The stub's fan room and camera view, dp: List mode never zooms or fans, so only its size matters. */
        const val VIEW_DP = 300f
    }
}

/** Portrait, not navigating, font 1.0. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class CrowdedListBubblePortraitTest : CrowdedListBubbleTests(200f, 560f, Offset(-30f, 40f), navigating = false)

/** Portrait, navigating, font 1.0. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class CrowdedListBubblePortraitNavigatingTest : CrowdedListBubbleTests(200f, 560f, Offset(-30f, 40f), navigating = true)

/** Portrait, navigating, font 2.0. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
class CrowdedListBubblePortraitNavigatingFont20Test : CrowdedListBubbleTests(200f, 560f, Offset(-30f, 40f), navigating = true)

/** Landscape, navigating, font 1.0: the S22's window. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class CrowdedListBubbleLandscapeNavigatingTest : CrowdedListBubbleTests(330f, 170f, LANDSCAPE_PAN, navigating = true)

/** Landscape, navigating, font 2.0. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
class CrowdedListBubbleLandscapeNavigatingFont20Test : CrowdedListBubbleTests(330f, 170f, LANDSCAPE_PAN, navigating = true)

/**
 * Landscape's pan: 200 dp right. In this window the bubble at the stack's first place is held by the clamp on both axes (the L
 * on the left, the room under the strip; run 41785067129 saw it unmoved by a 40 dp pan, at 124 dp from the left, its full
 * 272.67 dp tall), so the pan carries the stack far enough right that the card's ideal place is inside the box across.
 */
private val LANDSCAPE_PAN = Offset(200f, 30f)
