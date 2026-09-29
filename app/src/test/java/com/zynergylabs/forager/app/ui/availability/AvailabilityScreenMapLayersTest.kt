package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.domain.MapLayerPreferences
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MAP_LAYERS_SHEET_TAG
import com.zynergylabs.forager.app.ui.map.LEGEND_MAX_HEIGHT
import com.zynergylabs.forager.app.ui.map.MAP_LEGEND_CHIP_TAG
import com.zynergylabs.forager.app.ui.map.mapLayerOpacityTag
import com.zynergylabs.forager.app.ui.map.mapLayerReorderTag
import com.zynergylabs.forager.app.ui.map.mapLayerSwitchTag
import com.zynergylabs.forager.app.ui.map.layers.ForecastCellsShown
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_NO_FORECAST_HERE
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_REFERENCE_CLASS
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.activeLayerCredits
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

private typealias MapLayersRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

private val CHANTERELLES_LABEL = "Test forecast: chanterelles (synthetic data)"
private val CHICKEN_LABEL = "Test forecast: chicken of the woods (synthetic data)"

/** The Maps tab's overlay switches, in the sheet's order (the dispatch, B1): seven, and J8's "Journal entries" last. */
private val MAPS_TAB_OVERLAY_LABELS = listOf("Finds", "Photos", "Waypoints", "Planned trips", "Recording trail", "Tracks", "Offline maps", "Journal entries")

private val DATES = mapOf(
    MapLayerIds.FORECAST_CHANTERELLES to ForecastCellsShown(week = MAP_LAYERS_TEST_WEEK, weatherThrough = LocalDate.of(2026, 9, 26)),
    MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS to ForecastCellsShown(week = MAP_LAYERS_TEST_WEEK, weatherThrough = LocalDate.of(2026, 9, 25)),
)

private fun hostActivityRule() = object : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }
}

private fun MapLayersRule.centreOf(node: SemanticsNodeInteraction): Offset {
    val bounds = node.getUnclippedBoundsInRoot()
    return with(density) { Offset(((bounds.left + bounds.right) / 2).toPx(), ((bounds.top + bounds.bottom) / 2).toPx()) }
}

/** A real touch at [atDp] on the app's own window (the sheet, when open, is a window of its own). */
private fun MapLayersRule.touchMapAt(xDp: Dp, yDp: Dp) {
    val at = with(density) { Offset(xDp.toPx(), yDp.toPx()) }
    onRoot().performTouchInput { click(at) }
    waitForIdle()
}

/** The sheet's own window, found by the sheet inside it, for a tap on its scrim. */
private fun MapLayersRule.sheetWindow() = onNode(isRoot() and hasAnyDescendant(hasTestTag(MAP_LAYERS_SHEET_TAG)))

private fun MapLayersRule.closeSheetByTappingOutside() {
    sheetWindow().performTouchInput { click(Offset(centerX, 4f)) }
    waitForIdle()
}

private fun SemanticsNodeInteraction.touch() = performTouchInput { click() }

private fun MapLayersRule.customAction(tag: String, label: String) {
    val action = onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
    runOnIdle { action.action() }
    waitForIdle()
}

/**
 * The Layers sheet and the legend chip on the compact Maps tab, portrait (map layers L0b, B1 and B4),
 * at the S22 Ultra's portrait size: the dispatch's short landscape window is `w823dp-h384dp`. Every
 * touch is a real one at screen coordinates (CLAUDE.md, Testing), and every change goes through the
 * real [AvailabilityViewModel] to the state the map slot is handed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class AvailabilityScreenMapLayersSheetTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivityRule()).around(composeRule)

    private val map = LayersRecordingMapSlot(DATES)
    private lateinit var preferences: InMemoryLayerPreferences

    private fun setScreen(store: ForecastCellStore = AbsentForecastCellStore, stored: MapLayerPreferences = MapLayerPreferences.NONE) {
        preferences = InMemoryLayerPreferences(stored)
        val viewModel = mapLayersViewModel(layerPreferences = preferences, store = store)
        composeRule.setContent { MapLayersTestScreen(viewModel, map.slot, store) }
        composeRule.waitForIdle()
    }

    private fun openSheet() {
        composeRule.onNodeWithContentDescription(LAYERS_ROW_DESCRIPTION).touch()
        composeRule.waitForIdle()
    }

    @Test
    fun `a real touch on the Layers row opens the Layers sheet, with Map type and the Maps tab's eight overlays`() {
        setScreen()

        openSheet()

        composeRule.onNodeWithTag(MAP_LAYERS_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Layers").assertIsDisplayed()
        composeRule.onNodeWithText("Map type").assertIsDisplayed()
        composeRule.onNodeWithText("Overlays").assertIsDisplayed()
        MAPS_TAB_OVERLAY_LABELS.forEach { composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
    }

    @Test
    fun `switching Finds off in the sheet hides finds in the state the map is handed, and stores the choice`() {
        setScreen()
        assertEquals(true, map.renderMode?.layers?.stateOf(MapLayerIds.FINDS)?.visible)
        openSheet()

        composeRule.onNodeWithTag(mapLayerSwitchTag(MapLayerIds.FINDS)).touch()
        composeRule.waitForIdle()

        assertEquals(false, map.renderMode?.layers?.stateOf(MapLayerIds.FINDS)?.visible)
        assertEquals(listOf("visible ${MapLayerIds.FINDS} false"), preferences.writes)
    }

    @Test
    fun `choosing a map type in the sheet applies it and the sheet stays open`() {
        setScreen()
        openSheet()

        composeRule.onNodeWithText("Street").touch()
        composeRule.waitForIdle()

        assertEquals(Basemap.OSM_STANDARD, map.renderMode?.basemap)
        composeRule.onNodeWithTag(MAP_LAYERS_SHEET_TAG).assertIsDisplayed()
    }

    @Test
    fun `with forecast data the colour fields are listed top first with an opacity slider and a reorder handle, and Move up reorders them`() {
        setScreen(store = FixedForecastStore(BOTH_FORECAST_GROUPS))
        openSheet()

        val top = composeRule.onNodeWithText(CHANTERELLES_LABEL).performScrollTo().getUnclippedBoundsInRoot().top
        val below = composeRule.onNodeWithText(CHICKEN_LABEL).performScrollTo().getUnclippedBoundsInRoot().top
        assertTrue("chanterelles, drawn on top by default, is listed first ($top < $below)", top < below)
        val slider = composeRule.onNodeWithTag(mapLayerOpacityTag(MapLayerIds.FORECAST_CHANTERELLES)).fetchSemanticsNode().config
        assertEquals(listOf("$CHANTERELLES_LABEL opacity"), slider.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals("100 percent", slider.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(2, composeRule.onAllNodesWithText("100%").fetchSemanticsNodes().size)
        assertEquals(
            listOf("Reorder $CHICKEN_LABEL"),
            composeRule.onNodeWithTag(mapLayerReorderTag(MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription),
        )

        composeRule.customAction(mapLayerReorderTag(MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS), "Move up")

        assertEquals(listOf("order ${MapLayerIds.FORECAST_CHANTERELLES},${MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS}"), preferences.writes)
        assertEquals(listOf(MapLayerIds.FORECAST_CHANTERELLES, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS), map.renderMode?.layers?.reorderableOrder)
        val newTop = composeRule.onNodeWithText(CHICKEN_LABEL).performScrollTo().getUnclippedBoundsInRoot().top
        val newBelow = composeRule.onNodeWithText(CHANTERELLES_LABEL).performScrollTo().getUnclippedBoundsInRoot().top
        assertTrue("chicken of the woods is now listed first ($newTop < $newBelow)", newTop < newBelow)
    }

    @Test
    fun `dragging a colour field's handle down one row moves it one place down`() {
        setScreen(store = FixedForecastStore(BOTH_FORECAST_GROUPS))
        openSheet()
        val handle = composeRule.onNodeWithTag(mapLayerReorderTag(MapLayerIds.FORECAST_CHANTERELLES)).performScrollTo()
        val from = handle.getUnclippedBoundsInRoot()
        val to = composeRule.onNodeWithTag(mapLayerReorderTag(MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS)).getUnclippedBoundsInRoot()
        val rowPx = with(composeRule.density) { (to.top - from.top).toPx() }

        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, rowPx / 2), delayMillis = 50)
            moveBy(Offset(0f, rowPx / 2), delayMillis = 50)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(listOf("order ${MapLayerIds.FORECAST_CHANTERELLES},${MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS}"), preferences.writes)
    }

    @Test
    fun `a touch on the opacity slider sets that layer's opacity, and its value reads as a percent`() {
        setScreen(store = FixedForecastStore(BOTH_FORECAST_GROUPS))
        openSheet()

        composeRule.onNodeWithTag(mapLayerOpacityTag(MapLayerIds.FORECAST_CHANTERELLES)).performScrollTo()
            .performTouchInput { click(Offset(width * 0.3f, centerY)) }
        composeRule.waitForIdle()

        val opacity = map.renderMode?.layers?.stateOf(MapLayerIds.FORECAST_CHANTERELLES)?.opacity ?: error("no layer state")
        assertTrue("the slider moved the opacity well off 100% ($opacity)", opacity in 0.1f..0.45f)
        val percent = Math.round(opacity * 100)
        composeRule.onNodeWithText("$percent%").assertIsDisplayed()
        assertEquals(listOf("opacity ${MapLayerIds.FORECAST_CHANTERELLES} $opacity"), preferences.writes.takeLast(1))
    }

    @Test
    fun `with the release build's store the sheet lists no colour field, no legend shows, and no credit is added`() {
        setScreen(store = AbsentForecastCellStore)
        openSheet()

        composeRule.onAllNodesWithText(CHANTERELLES_LABEL).assertCountEqualsZero()
        composeRule.onAllNodesWithText(CHICKEN_LABEL).assertCountEqualsZero()
        composeRule.closeSheetByTappingOutside()
        composeRule.onAllNodesWithTag(MAP_LEGEND_CHIP_TAG).assertCountEqualsZero()
        assertEquals(emptyList<String>(), activeLayerCredits(MAP_LAYER_REGISTRY, map.renderMode?.layers ?: error("no layer state")))
        assertEquals("no colour field is fed", emptyMap<String, String>(), map.renderMode?.forecast?.groupsByLayer.orEmpty())
    }

    @Test
    fun `once the sheet is closed, touches where it was and around the Layers row reach the map`() {
        setScreen()
        openSheet()
        composeRule.closeSheetByTappingOutside()
        composeRule.onAllNodesWithTag(MAP_LAYERS_SHEET_TAG).assertCountEqualsZero()
        val before = map.taps

        // Where the sheet's lower half lay, clear of the nav; and left of the cluster, level with its
        // Layers row (the cluster's container is as wide as its widest child, so left of that).
        listOf(40.dp to 600.dp, 192.dp to 640.dp, 150.dp to 500.dp).forEach { (x, y) -> composeRule.touchMapAt(x, y) }
        val row = composeRule.onNodeWithContentDescription(LAYERS_ROW_DESCRIPTION).getUnclippedBoundsInRoot()
        val cluster = composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG).getUnclippedBoundsInRoot()
        composeRule.touchMapAt(cluster.left - 16.dp, (row.top + row.bottom) / 2)

        assertEquals("every one of the four touches reached the map", before + 4, map.taps)
    }

    @Test
    fun `while a colour field is visible the legend chip shows at the bottom right above the nav, collapsed to 2 layers`() {
        setScreen(store = FixedForecastStore(BOTH_FORECAST_GROUPS))

        val chip = composeRule.onNodeWithTag(MAP_LEGEND_CHIP_TAG)
        chip.assertIsDisplayed()
        composeRule.onNodeWithText("2 layers").assertIsDisplayed()
        assertEquals("Show legend", chip.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        val bounds = chip.getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val navTop = composeRule.onNodeWithText("Maps").getUnclippedBoundsInRoot().top
        assertTrue("right-aligned ($bounds in $root)", root.right - bounds.right < 24.dp)
        assertTrue("above the nav ($bounds, nav top $navTop)", bounds.bottom <= navTop)
    }

    @Test
    fun `a tap on the chip shows the legend with each ramp's 0 and 100 percent, the dates, no forecast here and the reference class, and a second tap hides it`() {
        setScreen(store = FixedForecastStore(BOTH_FORECAST_GROUPS))

        composeRule.onNodeWithTag(MAP_LEGEND_CHIP_TAG).touch()
        composeRule.waitForIdle()

        // The expanded legend is capped and scrolls (N1), so each entry is scrolled to before it is
        // checked as displayed, in the unmerged tree: the scroll sits inside the chip's one merged node.
        composeRule.onNodeWithText(CHANTERELLES_LABEL, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(CHICKEN_LABEL, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        // Counted in the unmerged tree: the chip is one clickable Surface, so the merged tree folds
        // every text inside it into the chip's own node, and a merged count sees one node however
        // many ramps it holds.
        assertEquals(2, composeRule.onAllNodesWithText("0%", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(2, composeRule.onAllNodesWithText("100%", useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Week of 2026-09-28, weather to 2026-09-26", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Week of 2026-09-28, weather to 2026-09-25", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertEquals(2, composeRule.onAllNodesWithText(LEGEND_NO_FORECAST_HERE, useUnmergedTree = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithText(LEGEND_REFERENCE_CLASS, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        assertEquals("Hide legend", composeRule.onNodeWithTag(MAP_LEGEND_CHIP_TAG).fetchSemanticsNode().config[SemanticsActions.OnClick].label)

        composeRule.onNodeWithText("2 layers").touch()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(LEGEND_REFERENCE_CLASS).assertCountEqualsZero()
    }

    @Test
    fun `touches on the map all around the legend chip reach the map`() {
        setScreen(store = FixedForecastStore(BOTH_FORECAST_GROUPS))
        val chip = composeRule.onNodeWithTag(MAP_LEGEND_CHIP_TAG).getUnclippedBoundsInRoot()
        val before = map.taps
        val midY = (chip.top + chip.bottom) / 2
        val midX = (chip.left + chip.right) / 2

        listOf(
            chip.left - 12.dp to midY,
            chip.left - 12.dp to chip.bottom - 2.dp,
            chip.left - 12.dp to chip.top - 12.dp,
            midX to chip.top - 12.dp,
            chip.right - 4.dp to chip.top - 12.dp,
        ).forEach { (x, y) -> composeRule.touchMapAt(x, y) }

        assertEquals("all five touches reached the map", before + 5, map.taps)
    }

    /**
     * The absence half passes wherever no chip exists at all, so on its own it could not fail before
     * the build; the second half (switch one on in the sheet, and the chip names it) is what fails
     * first, and the revert check on the visible-only rule is what shows the first half bites.
     */
    @Test
    fun `with no colour field visible there is no legend chip, and switching one on in the sheet shows it`() {
        setScreen(
            store = FixedForecastStore(BOTH_FORECAST_GROUPS),
            stored = MapLayerPreferences(
                visibility = mapOf(MapLayerIds.FORECAST_CHANTERELLES to false, MapLayerIds.FORECAST_CHICKEN_OF_THE_WOODS to false),
                opacity = emptyMap(),
                order = emptyList(),
            ),
        )

        composeRule.onAllNodesWithTag(MAP_LEGEND_CHIP_TAG).assertCountEqualsZero()

        openSheet()
        composeRule.onNodeWithTag(mapLayerSwitchTag(MapLayerIds.FORECAST_CHANTERELLES)).performScrollTo().touch()
        composeRule.waitForIdle()
        composeRule.closeSheetByTappingOutside()

        composeRule.onNodeWithTag(MAP_LEGEND_CHIP_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(CHANTERELLES_LABEL).assertIsDisplayed()
    }

    @Test
    fun `with the cluster dragged to the bottom on the right it stops above the chip, rises when the legend expands and returns when it collapses`() {
        setScreen(store = FixedForecastStore(BOTH_FORECAST_GROUPS))
        val handle = composeRule.centreOf(composeRule.onNodeWithTag("map-icon-bar-minimize-handle"))
        val drag = with(composeRule.density) { 2000.dp.toPx() }
        composeRule.onRoot().performTouchInput {
            down(handle)
            advanceEventTime(600)
            moveTo(handle + Offset(0f, drag))
            advanceEventTime(50)
            up()
        }
        composeRule.waitForIdle()

        fun clusterBottom() = composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG).getUnclippedBoundsInRoot().bottom
        fun chipTop() = composeRule.onNodeWithTag(MAP_LEGEND_CHIP_TAG).getUnclippedBoundsInRoot().top
        val low = clusterBottom()
        assertTrue("collapsed: the cluster stops above the chip ($low <= ${chipTop()})", low <= chipTop() + 1.dp)

        composeRule.onNodeWithTag(MAP_LEGEND_CHIP_TAG).touch()
        composeRule.waitForIdle()
        val raised = clusterBottom()
        assertTrue("expanded: the cluster rose ($raised < $low)", raised < low)
        assertTrue("expanded: and stays above the chip ($raised <= ${chipTop()})", raised <= chipTop() + 1.dp)

        composeRule.onNodeWithText("2 layers").touch()
        composeRule.waitForIdle()
        assertTrue("collapsed again: back where it was (${clusterBottom()} vs $low)", abs((clusterBottom() - low).value) <= 1f)
    }

    /**
     * N1, the owner's ruling "Cap height, scroll": the expanded legend is capped at [LEGEND_MAX_HEIGHT]
     * and its contents scroll inside it. The last entry, the reference class, starts out of view and a
     * real swipe on the chip brings it in; the cap is what makes room for the cluster in the test above.
     */
    @Test
    fun `expanded, the legend is capped in height and a real swipe on it scrolls the reference class into view`() {
        setScreen(store = FixedForecastStore(BOTH_FORECAST_GROUPS))

        composeRule.onNodeWithTag(MAP_LEGEND_CHIP_TAG).touch()
        composeRule.waitForIdle()

        val chip = composeRule.onNodeWithTag(MAP_LEGEND_CHIP_TAG)
        val height = chip.getUnclippedBoundsInRoot().let { it.bottom - it.top }
        assertTrue("capped at $LEGEND_MAX_HEIGHT ($height)", height <= LEGEND_MAX_HEIGHT + 1.dp)
        composeRule.onNodeWithText(LEGEND_REFERENCE_CLASS, useUnmergedTree = true).assertIsNotDisplayed()

        chip.performTouchInput { swipeUp(startY = bottom - 4f, endY = top + 4f, durationMillis = 400) }
        composeRule.waitForIdle()
        chip.performTouchInput { swipeUp(startY = bottom - 4f, endY = top + 4f, durationMillis = 400) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(LEGEND_REFERENCE_CLASS, useUnmergedTree = true).assertIsDisplayed()
        assertEquals("the swipes scrolled, and did not collapse the legend", "Hide legend", chip.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
    }
}

/**
 * The sheet and the legend in a short landscape window (the dispatch's `w823dp-h384dp-land`), where
 * the sheet's content scrolls and the chip must stay clear of the rail and the cluster.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class AvailabilityScreenMapLayersShortLandscapeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivityRule()).around(composeRule)

    private val map = LayersRecordingMapSlot(DATES)

    private fun setScreen(store: ForecastCellStore) {
        val viewModel = mapLayersViewModel(store = store)
        composeRule.setContent { MapLayersTestScreen(viewModel, map.slot, store) }
        composeRule.waitForIdle()
    }

    @Test
    fun `a real touch on the Layers row opens the sheet, and its content scrolls to the last overlay`() {
        setScreen(FixedForecastStore(BOTH_FORECAST_GROUPS))

        composeRule.onNodeWithContentDescription(LAYERS_ROW_DESCRIPTION).touch()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MAP_LAYERS_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(CHICKEN_LABEL).performScrollTo().assertIsDisplayed()
        // J8: "Journal entries" is the last overlay now.
        composeRule.onNodeWithText("Journal entries").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the legend chip sits at the bottom right, clear of the rail and the cluster, and touches around it reach the map`() {
        setScreen(FixedForecastStore(BOTH_FORECAST_GROUPS))
        val chip = composeRule.onNodeWithTag(MAP_LEGEND_CHIP_TAG).getUnclippedBoundsInRoot()
        val cluster = composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG).getUnclippedBoundsInRoot()
        val maps = composeRule.onNodeWithText("Maps").getUnclippedBoundsInRoot()

        assertTrue("clear of the cluster ($chip, $cluster)", chip.top >= cluster.bottom || chip.left >= cluster.right || chip.right <= cluster.left)
        assertTrue("clear of the rail's Maps entry ($chip, $maps)", chip.right <= maps.left || chip.left >= maps.right)

        val before = map.taps
        composeRule.touchMapAt(chip.left - 12.dp, (chip.top + chip.bottom) / 2)
        composeRule.touchMapAt((chip.left + chip.right) / 2, chip.top - 12.dp)
        assertEquals(before + 2, map.taps)
    }
}

/** The sheet and the legend in the wide layout (`w840dp-h1024dp`), from its own Layers control. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w840dp-h1024dp-mdpi")
class AvailabilityScreenMapLayersWideTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivityRule()).around(composeRule)

    private val map = LayersRecordingMapSlot(DATES)
    private lateinit var preferences: InMemoryLayerPreferences

    /** The wide map shows only once a region is searched, so a search is run first. */
    private fun setScreen(store: ForecastCellStore = AbsentForecastCellStore) {
        preferences = InMemoryLayerPreferences()
        val viewModel = mapLayersViewModel(layerPreferences = preferences, store = store)
        viewModel.onManualLatChanged("45.5")
        viewModel.onManualLngChanged("-122.6")
        viewModel.searchManualCoordinates()
        composeRule.setContent { MapLayersTestScreen(viewModel, map.slot, store) }
        composeRule.waitForIdle()
    }

    @Test
    fun `a real touch on the wide Layers control opens the sheet`() {
        setScreen()

        composeRule.onNodeWithContentDescription(LAYERS_ROW_DESCRIPTION).touch()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MAP_LAYERS_SHEET_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Map type").assertIsDisplayed()
        composeRule.onNodeWithText("Recording trail").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `switching Tracks off in the wide sheet hides kept tracks in the state the map is handed`() {
        setScreen()
        composeRule.onNodeWithContentDescription(LAYERS_ROW_DESCRIPTION).touch()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(mapLayerSwitchTag(MapLayerIds.KEPT_TRACKS)).performScrollTo().touch()
        composeRule.waitForIdle()

        assertEquals(false, map.renderMode?.layers?.stateOf(MapLayerIds.KEPT_TRACKS)?.visible)
        assertEquals(listOf("visible ${MapLayerIds.KEPT_TRACKS} false"), preferences.writes)
    }

    @Test
    fun `the legend chip is at the map's bottom-end corner with the icon cluster kept above it, and touches around it reach the map`() {
        // J6c (the owner's item 5): the separate "+" this chip used to stack above is a row of the icon cluster now, and
        // the chip is placed as on the phone (the bottom-end corner, above the attribution), with the cluster's clamp
        // keeping the cluster above it (this test read "the chip sits above the add button").
        setScreen(FixedForecastStore(BOTH_FORECAST_GROUPS))
        val chip = composeRule.onNodeWithTag(MAP_LEGEND_CHIP_TAG).getUnclippedBoundsInRoot()
        val cluster = composeRule.onNodeWithTag(MAP_ICON_CLUSTER_TAG).getUnclippedBoundsInRoot()
        val slot = composeRule.onNodeWithTag("map-slot").getUnclippedBoundsInRoot()

        assertTrue("the cluster stops above the legend chip ($cluster, $chip)", cluster.bottom <= chip.top)
        assertTrue("the chip is at the pane's bottom-end corner ($chip, $slot)", abs((chip.right - (slot.right - 8.dp)).value) <= 1f && slot.bottom - chip.bottom < 64.dp)

        val before = map.taps
        composeRule.touchMapAt(chip.left - 12.dp, (chip.top + chip.bottom) / 2)
        composeRule.touchMapAt((chip.left + chip.right) / 2, chip.top - 12.dp)
        composeRule.touchMapAt(chip.left - 12.dp, chip.top - 12.dp)
        assertEquals(before + 3, map.taps)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEqualsZero() {
    assertEquals(0, fetchSemanticsNodes().size)
}
