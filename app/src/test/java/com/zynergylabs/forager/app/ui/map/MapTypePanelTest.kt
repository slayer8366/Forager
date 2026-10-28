package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.availability.MapLayersTestScreen
import com.zynergylabs.forager.app.ui.availability.mapLayersViewModel
import com.zynergylabs.forager.app.ui.log.CartographyEntryReportScreen
import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

private fun hostActivityRule() = object : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }
}

/** A finger is not a point: several real touches across a chip's own bounds, as fractions of its width and height. */
private val CHIP_TOUCH_POINTS = listOf(0.15f to 0.5f, 0.5f to 0.5f, 0.85f to 0.5f, 0.5f to 0.25f, 0.5f to 0.75f)

/**
 * The map type panel (dispatch 2026-10-28-02, RECORD -1764; the owner: "Have the mapiconbar layers tab return to the original
 * animated state it was prior to the map layers addition.") on the Maps tab, through the real [AvailabilityScreen] and its
 * real ViewModel, portrait at the S22 Ultra's size. Every touch is a real one at screen coordinates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapTypePanelMapsTabTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivityRule()).around(composeRule)

    private var renderMode: MapRenderMode? = null
    private var mapTaps = 0
    private var mapLongPresses = 0

    private val slot: MapSlot = { _, _, mode, _, _, onTap, _, _, modifier ->
        renderMode = mode
        Box(
            modifier.testTag("map-slot").pointerInput(Unit) {
                detectTapGestures(onTap = { mapTaps++; onTap() }, onLongPress = { mapLongPresses++ })
            },
        )
    }

    private fun setScreen() {
        val viewModel = mapLayersViewModel()
        composeRule.setContent { MapLayersTestScreen(viewModel, slot, com.zynergylabs.forager.app.domain.AbsentForecastCellStore) }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun touchAt(x: Dp, y: Dp, long: Boolean = false) {
        val at = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onRoot().performTouchInput { if (long) longClick(at) else click(at) }
        composeRule.waitForIdle()
    }

    private fun touchCentre(r: DpRect) = touchAt((r.left + r.right) / 2, (r.top + r.bottom) / 2)

    private fun layersRow(): DpRect = composeRule.onNodeWithContentDescription("Layers:", substring = true).getUnclippedBoundsInRoot()

    private fun openPanel() {
        touchCentre(layersRow())
        composeRule.onNodeWithTag(MAP_TYPE_PANEL_TAG).assertIsDisplayed()
    }

    private fun panelShown() = composeRule.onAllNodesWithTag(MAP_TYPE_PANEL_TAG).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `a real touch at several points across each chip applies that map type and closes the panel`() {
        setScreen()
        // Each pass ends on Trail, so every touch is a change the map has to show.
        CHIP_TOUCH_POINTS.forEach { (fx, fy) ->
            listOf(MapMode.TOPOGRAPHIC, MapMode.OPENSTREETMAP, MapMode.TRAIL).forEach { mode ->
                openPanel()
                val chip = bounds(mapModeChipTag(mode))
                touchAt(chip.left + (chip.right - chip.left) * fx, chip.top + (chip.bottom - chip.top) * fy)
                assertEquals("$mode at ($fx, $fy) applied", mode.basemap, renderMode?.basemap)
                assertTrue("$mode at ($fx, $fy) closed the panel", !panelShown())
            }
        }
    }

    @Test
    fun `a real touch on the dimmed map closes the panel and changes nothing, and reaches no map`() {
        setScreen()
        openPanel()
        val before = renderMode?.basemap
        val tapsBefore = mapTaps
        touchAt(40.dp, 400.dp)
        assertTrue("closed", !panelShown())
        assertEquals("nothing changed", before, renderMode?.basemap)
        assertEquals("the scrim took the touch", tapsBefore, mapTaps)
    }

    @Test
    fun `Back closes the panel and changes nothing`() {
        setScreen()
        openPanel()
        val before = renderMode?.basemap
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertTrue("closed", !panelShown())
        assertEquals("nothing changed", before, renderMode?.basemap)
        composeRule.onNodeWithContentDescription("Layers:", substring = true).assertIsDisplayed()
    }

    /** Amendment 3 (RECORD -1770, N5): with fullscreen on, Back closes the panel first and fullscreen stays. */
    @Test
    fun `in fullscreen, Back closes the panel and fullscreen stays`() {
        setScreen()
        touchCentre(composeRule.onNodeWithContentDescription("Fullscreen").getUnclippedBoundsInRoot())
        composeRule.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
        openPanel()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertTrue("Back closed the panel", !panelShown())
        composeRule.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Fullscreen").assertIsDisplayed()
    }

    @Test
    fun `with the panel closed, a long-press on the map where the panel and its scrim were reaches the map`() {
        setScreen()
        // Closed before it was ever opened: nothing of the panel takes a touch (CLAUDE.md, the Surface pitfall).
        val neverOpened = mapLongPresses
        touchAt(40.dp, 400.dp, long = true)
        touchAt(192.dp, 300.dp, long = true)
        assertEquals("never opened: both long-presses reached the map", neverOpened + 2, mapLongPresses)
        openPanel()
        val panel = bounds(MAP_TYPE_PANEL_TAG)
        touchAt(40.dp, 400.dp)
        assertTrue(!panelShown())
        val before = mapLongPresses
        listOf((panel.left + panel.right) / 2 to (panel.top + panel.bottom) / 2, 40.dp to 400.dp, 192.dp to 300.dp).forEach { (x, y) ->
            touchAt(x, y, long = true)
        }
        assertEquals("all three long-presses reached the map", before + 3, mapLongPresses)
    }

    @Test
    fun `the panel's fill is the map chrome's 80 percent and its chips sit centred between its sides`() {
        setScreen()
        openPanel()
        val fill = composeRule.onNodeWithTag(MAP_TYPE_PANEL_TAG).fetchSemanticsNode().config[MapChromeContainerColor]
        assertEquals(MAP_CHROME_OVER_MAP_ALPHA, fill.alpha, 0.001f)
        val panel = bounds(MAP_TYPE_PANEL_TAG)
        val first = bounds(mapModeChipTag(MapMode.TRAIL))
        val last = bounds(mapModeChipTag(MapMode.OPENSTREETMAP))
        // The chips' laid-out boxes are 48 dp minimum touch targets, wider than a narrow drawn chip under Robolectric's fonts
        // (MapChromeChipsTest's finding, RECORD -719), so the drawn chips are held to 4 dp of centred.
        assertEquals("centred: left gap ${first.left - panel.left}, right gap ${panel.right - last.right}", (first.left - panel.left).value, (panel.right - last.right).value, 4f)
    }
}

/**
 * The entry map's panel (dispatch 2026-10-28-02): its bar is fixed at the right edge, and the panel opens beside its Layers
 * row, as `MapModePicker` did there before the Layers sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapTypePanelEntryMapTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivityRule()).around(composeRule)

    private val entry = CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)
        .copy(isDraft = false, waypointDecisions = listOf(WaypointDecision(waypointId = "w1", name = "Trailhead", lat = 45.5, lng = -122.5, kept = true)))

    private val mapData = CartographyEntryMapData(
        trackPolylines = emptyList(),
        findMarkers = emptyList(),
        waypointMarkers = listOf(RecordPoint("w1", LatLng(45.5, -122.5))),
        photoMarkers = emptyList(),
        offlineRegionCircles = emptyList(),
    )

    private var renderMode: MapRenderMode? = null
    private var onTap: (() -> Unit)? = null
    private var mapLongPresses = 0
    private val slot: MapSlot = { _, _, mode, _, _, tap, _, _, modifier ->
        renderMode = mode
        onTap = tap
        Box(modifier.pointerInput(Unit) { detectTapGestures(onTap = { tap() }, onLongPress = { mapLongPresses++ }) })
    }

    private fun setFullscreen() {
        composeRule.setContent {
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
        composeRule.waitForIdle()
        onTap?.invoke() ?: error("the map section never resolved")
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun touchAt(x: Dp, y: Dp, long: Boolean = false) {
        val at = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onRoot().performTouchInput { if (long) longClick(at) else click(at) }
        composeRule.waitForIdle()
    }

    private fun layersRow(): DpRect = composeRule.onNodeWithContentDescription("Layers:", substring = true).getUnclippedBoundsInRoot()

    private fun openPanel(): DpRect {
        val row = layersRow()
        touchAt((row.left + row.right) / 2, (row.top + row.bottom) / 2)
        composeRule.onNodeWithTag(MAP_TYPE_PANEL_TAG).assertIsDisplayed()
        return row
    }

    private fun panelShown() = composeRule.onAllNodesWithTag(MAP_TYPE_PANEL_TAG).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the Layers row opens the panel beside it, level with it, growing leftward from the bar`() {
        setFullscreen()
        val row = openPanel()
        val panel = bounds(MAP_TYPE_PANEL_TAG)
        val rowMid = (row.top + row.bottom) / 2
        val panelMid = (panel.top + panel.bottom) / 2
        assertTrue("level with the row ($panelMid vs $rowMid)", abs((panelMid - rowMid).value) <= 2f)
        assertTrue("overlaps the row ($panel, $row)", panel.left < row.right && panel.right > row.left)
        assertTrue("grows leftward ($panel, $row)", panel.left < row.left)
    }

    @Test
    fun `a real touch at several points across each chip applies it to this map and closes the panel`() {
        setFullscreen()
        CHIP_TOUCH_POINTS.forEach { (fx, fy) ->
            listOf(MapMode.TOPOGRAPHIC, MapMode.OPENSTREETMAP, MapMode.TRAIL).forEach { mode ->
                openPanel()
                val chip = bounds(mapModeChipTag(mode))
                touchAt(chip.left + (chip.right - chip.left) * fx, chip.top + (chip.bottom - chip.top) * fy)
                assertEquals("$mode at ($fx, $fy)", mode.basemap, renderMode?.basemap)
                assertTrue("$mode at ($fx, $fy) closed it", !panelShown())
            }
        }
    }

    @Test
    fun `the dimmed map and Back each close it unchanged, and closed, a long-press reaches the map`() {
        setFullscreen()
        val before = renderMode?.basemap
        openPanel()
        touchAt(40.dp, 300.dp)
        assertTrue("the scrim closed it", !panelShown())
        openPanel()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertTrue("Back closed it", !panelShown())
        composeRule.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
        assertEquals("nothing changed", before, renderMode?.basemap)

        val pressesBefore = mapLongPresses
        touchAt(40.dp, 300.dp, long = true)
        touchAt(192.dp, 420.dp, long = true)
        assertEquals("both long-presses reached the map", pressesBefore + 2, mapLongPresses)
    }
}
