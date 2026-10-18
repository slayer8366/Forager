package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.FindDecision
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.track.RecordingNotice
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The map-chrome follow-ups' last three items (dispatch `2026-09-28-104`, planner message `2026-09-29-12`): item 8 (the
 * entry map starts on the Maps tab's basemap, and follows Night Maps), item 2 (the search notice below the compass
 * strip, the cluster below the notice) and item 6 (the snackbar above the bottom navigation, clear of the rail).
 *
 * Every test drives the real [AvailabilityScreen] through [MapChromeTestScreen]. Robolectric reports zero window
 * insets, so where the system navigation bar or the cut-out decides a position the item is also a device item.
 */
private val RESUMED_DAY = LocalDate.of(2026, 9, 27)

private val RESUMED_ENTRY = CartographyEntry.draft(id = "entry-resumed", date = RESUMED_DAY, updatedAtEpochMillis = 1_000L).copy(
    isDraft = false,
    text = "A wet morning on the ridge.",
    findDecisions = listOf(FindDecision("find-1", RESUMED_DAY, "Golden chanterelle", hasPhotos = false, kept = true)),
)

private val RESUMED_ENTRY_MAP = CartographyEntryMapData(
    trackPolylines = emptyList(),
    findMarkers = listOf(RecordPoint("find-1", LatLng(45.51, -122.61))),
    waypointMarkers = emptyList(),
    photoMarkers = emptyList(),
    offlineRegionCircles = emptyList(),
)

/** A map stand-in that records every [MapRenderMode] it is handed and takes pointer input as the real map does. */
internal class RecordingRenderModes {
    val modes = mutableListOf<MapRenderMode>()

    val slot: MapSlot = { _, _, renderMode, _, _, onTap, _, _, modifier ->
        androidx.compose.runtime.SideEffect { modes += renderMode }
        Box(
            modifier
                .testTag("map-slot")
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTap() },
        )
    }

    /** The entry map's mode: the only map here that does not track the live location. */
    fun entryMode(): MapRenderMode? = modes.lastOrNull { !it.trackLiveLocation }

    /** The Maps tab's own map. */
    fun mapsTabMode(): MapRenderMode? = modes.lastOrNull { it.trackLiveLocation }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapChromeEntryMapTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val recorder = RecordingRenderModes()
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()
    private val map = BubbleMapSlot(emptyList())

    private fun setScreen(nightMaps: Boolean = false) {
        state.ui = state.ui.copy(nightModeMaps = nightMaps, nightModeMapsLoaded = true)
        state.cartography = CartographyUiState(entries = listOf(RESUMED_ENTRY), editingEntry = RESUMED_ENTRY)
        state.entryMapData = RESUMED_ENTRY_MAP
        composeRule.setContent {
            // The recorder is the map; BubbleMapSlot is only there to satisfy the shared screen's signature.
            MapChromeTestScreen(state, map, roles, mapSlotOverride = recorder.slot)
        }
        composeRule.waitForIdle()
    }

    private fun layersRow(): SemanticsNodeInteraction = composeRule.onNode(hasContentDescription("Layers:", substring = true))

    /**
     * Picks [label] in the Layers sheet. [closeWithBack] presses the Activity's Back afterwards; the entry map's fullscreen
     * caller passes `false`, because there that Back does not reach the sheet's dialog window: it reaches the screen's own
     * `BackHandler`, which leaves fullscreen (`CartographyEntryReportScreen.kt:354`), and the test does that on purpose below.
     */
    private fun pickMapType(label: String, closeWithBack: Boolean = true) {
        layersRow().performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(label).performTouchInput { click() }
        composeRule.waitForIdle()
        if (closeWithBack) {
            composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitForIdle()
        }
    }

    private fun openJournal() {
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("the entry report's map is showing", 1, composeRule.onAllNodesWithTag(com.zynergylabs.forager.app.ui.log.CARTOGRAPHY_MAP_TEST_TAG).fetchSemanticsNodes().size)
    }

    @Test
    // Was "…set to Street starts on Street"; flipped to Topographical when Street became the opening map (dispatch
    // 2026-10-18-04, Amendment 2; RECORD -1297), so the pick still moves the Maps tab off what it opens on.
    fun `an entry map opened after the Maps tab is set to Topographical starts on Topographical`() {
        setScreen()
        pickMapType("Topographical")
        openJournal()

        assertEquals(Basemap.OPEN_TOPO_MAP, recorder.entryMode()?.basemap)
    }

    @Test
    // Was "…starts on Topographical", asserting Basemap.OPEN_TOPO_MAP; the default became Street (Amendment 2, RECORD -1297).
    fun `an entry map opened with the Maps tab on its default starts on Street`() {
        setScreen()
        openJournal()

        assertEquals(Basemap.OSM_STANDARD, recorder.entryMode()?.basemap)
    }

    @Test
    // Was "…leaves the Maps tab on Street", picking Street first and Topographical on the entry map. Flipped when Street
    // became the opening map (Amendment 2, RECORD -1297), so both picks still move off what each map shows.
    fun `changing the basemap on the entry map leaves the Maps tab on Topographical`() {
        setScreen()
        pickMapType("Topographical")
        openJournal()
        // Fullscreen (a tap on the preview) brings the map's own Layers control.
        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.CARTOGRAPHY_MAP_TEST_TAG).performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        // The entry map starts on the Maps tab's Topographical, so Street is the other choice it can take.
        pickMapType("Street", closeWithBack = false)
        assertEquals("the entry map took Street", Basemap.OSM_STANDARD, recorder.entryMode()?.basemap)

        // Back leaves fullscreen, which unmounts the sheet with it.
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        assertEquals("the Maps tab is still on Topographical", Basemap.OPEN_TOPO_MAP, recorder.mapsTabMode()?.basemap)
    }

    /**
     * The device check saw the entry map in daylight at night. This pins what the code does: with Night Maps on and
     * loaded, the entry map is handed `night = true`. It passes at base, so the plumbing is not the cause (see the report).
     */
    @Test
    fun `with Night Maps on the entry map is handed night`() {
        setScreen(nightMaps = true)
        openJournal()

        assertTrue("night reached the entry map's render mode", recorder.entryMode()?.night == true)
    }
}

internal fun DpRect.overlapsBounds(other: DpRect): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom

private const val RESUMED_NOTICE = "Request failed. Check your connection and try again."

/**
 * Item 2 (the owner's "Option A"): the search notice is placed below the compass strip, and while it shows the icon
 * cluster stays below its measured bottom; when the notice clears the cluster returns to where it was.
 */
internal abstract class SearchNoticeTests {

    protected val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    protected val map = BubbleMapSlot(emptyList())
    protected val state = MapChromeScreenState()
    protected val roles = MapChromeRoles()

    protected fun setScreen() {
        composeRule.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    protected fun setNotice(message: String?) {
        composeRule.runOnUiThread { state.ui = state.ui.copy(errorMessage = message) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    protected fun tagBounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    protected fun cluster(): DpRect = tagBounds(MAP_ICON_CLUSTER_TAG)
    protected fun notice(): DpRect = tagBounds(SEARCH_NOTICE_TAG)
    protected fun strip(): DpRect = tagBounds("compass-elevation-strip")

    /** Drags the cluster's handle by ([dx], [dy]) with the real long-press drag. */
    protected fun dragCluster(dx: Dp, dy: Dp) {
        val handle = tagBounds("map-icon-bar-minimize-handle")
        composeRule.longPressDrag((handle.left + handle.right) / 2, (handle.top + handle.bottom) / 2, dx, dy)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class SearchNoticePortraitTest : SearchNoticeTests() {

    @Test
    fun `a search notice is fully below the compass strip`() {
        setScreen()
        setNotice(RESUMED_NOTICE)

        val strip = strip()
        val notice = notice()
        assertFalse("the notice ${notice.describe()} and the strip ${strip.describe()} do not intersect", notice.overlapsBounds(strip))
        assertTrue("the notice ${notice.describe()} lies below the strip ${strip.describe()}", notice.top >= strip.bottom - 0.5.dp)
    }

    @Test
    fun `a cluster dragged to its top limit is held below the notice while it shows, and returns when it clears`() {
        setScreen()
        dragCluster(0.dp, (-800).dp)
        val limit = cluster().top

        setNotice(RESUMED_NOTICE)
        val notice = notice()
        assertFalse("the cluster ${cluster().describe()} and the notice ${notice.describe()} do not intersect", cluster().overlapsBounds(notice))
        assertTrue("the cluster's top ${cluster().top.value} is at or below the notice's bottom ${notice.bottom.value}", cluster().top >= notice.bottom - 0.5.dp)

        setNotice(null)
        assertEquals("the cluster returned to its limit", limit.value, cluster().top.value, 1f)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
internal class SearchNoticeLandscapeTest : SearchNoticeTests() {

    @Test
    fun `a search notice does not intersect the compass strip or the cluster, and the cluster returns when it clears`() {
        setScreen()
        val before = cluster().top

        setNotice(RESUMED_NOTICE)
        val notice = notice()
        assertFalse("the notice ${notice.describe()} and the strip ${strip().describe()} do not intersect", notice.overlapsBounds(strip()))
        assertFalse("the notice ${notice.describe()} and the cluster ${cluster().describe()} do not intersect", notice.overlapsBounds(cluster()))

        setNotice(null)
        assertEquals("the cluster returned to where it was", before.value, cluster().top.value, 1f)
    }
}

/** Item 6 (the planner's ruling): on the Maps tab the snackbar sits above the floating bottom navigation, and clear of the rail. */
internal abstract class SnackbarPlacementTests {

    protected val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    protected val map = BubbleMapSlot(emptyList())
    protected val state = MapChromeScreenState()
    protected val roles = MapChromeRoles()

    protected fun showSnackbar(): DpRect {
        composeRule.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
        state.tripStartWarning = RecordingNotice(1, "Test warning: the trip was not started because the location is not yet known")
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
        return composeRule.onNodeWithTag(COMPACT_SNACKBAR_TAG).getUnclippedBoundsInRoot()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
internal class SnackbarPortraitTest : SnackbarPlacementTests() {

    @Test
    fun `a snackbar over the Maps tab sits above the floating bottom navigation`() {
        val snackbar = showSnackbar()
        val nav = composeRule.onNodeWithTag(COMPACT_BOTTOM_NAV_TAG).getUnclippedBoundsInRoot()

        assertFalse("the snackbar ${snackbar.describe()} and the bottom nav ${nav.describe()} do not intersect", snackbar.overlapsBounds(nav))
        assertTrue("the snackbar's bottom ${snackbar.bottom.value} is at or above the nav's top ${nav.top.value}", snackbar.bottom <= nav.top + 0.5.dp)
    }
}

/** The narrowest short landscape window the app meets: the snackbar's 600 dp cap does not keep it off the rail here. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi")
internal class SnackbarNarrowLandscapeTest : SnackbarPlacementTests() {

    @Test
    fun `a snackbar over the Maps tab does not lie over the rail`() {
        val snackbar = showSnackbar()
        val rail = composeRule.onNodeWithTag(COMPACT_NAVIGATION_RAIL_TAG).getUnclippedBoundsInRoot()

        assertTrue("the rail has a width to clear (${rail.describe()})", rail.right - rail.left > 1.dp)
        assertFalse("the snackbar ${snackbar.describe()} and the rail ${rail.describe()} do not intersect", snackbar.overlapsBounds(rail))
    }
}

/** The S22's landscape window: the centred snackbar clears the rail already, so this is a guard, not a failing test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
internal class SnackbarShortLandscapeTest : SnackbarPlacementTests() {

    @Test
    fun `guard - a snackbar over the Maps tab does not lie over the rail`() {
        val snackbar = showSnackbar()
        val rail = composeRule.onNodeWithTag(COMPACT_NAVIGATION_RAIL_TAG).getUnclippedBoundsInRoot()

        assertFalse("the snackbar ${snackbar.describe()} and the rail ${rail.describe()} do not intersect", snackbar.overlapsBounds(rail))
    }
}
