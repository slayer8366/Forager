package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
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
import com.zynergylabs.forager.app.ui.theme.DarkColors
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import com.zynergylabs.forager.app.ui.theme.LightColors
import java.time.LocalDate
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
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-10-28-02 Amendment 4 (RECORD -1771; the owner, to "Make it stand out": "Option 1, but Give it an outline."):
 * in the light theme the selected map type chip is filled `secondary` with an `onSurface` outline, where it had been the
 * pale `secondaryContainer` with none, about 1.04:1 against the panel on the S22. Since Amendment 6 (RECORD -1774) the dark
 * theme's selected chip carries its own `onSurface` outline too, with its fill unchanged.
 *
 * Both of the panel's real entry points: the Maps tab's icon bar and a journal entry's map, each opened by a real touch on
 * its Layers row, inside the app's own [ForagerTheme]. The fill and the outline are read from the drawn pixels, and the
 * chip's own record of them alongside.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapTypePanelSelectedChipTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private var onEntryMapTap: (() -> Unit)? = null

    private val slot: MapSlot = { _, _, _, _, _, tap, _, _, modifier ->
        onEntryMapTap = tap
        Box(modifier)
    }

    private fun setMapsTab(dark: Boolean) {
        val viewModel = mapLayersViewModel()
        composeRule.setContent { ForagerTheme(darkTheme = dark) { MapLayersTestScreen(viewModel, slot, AbsentForecastCellStore) } }
        composeRule.waitForIdle()
    }

    private val entry = CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)
        .copy(isDraft = false, waypointDecisions = listOf(WaypointDecision(waypointId = "w1", name = "Trailhead", lat = 45.5, lng = -122.5, kept = true)))

    private val mapData = CartographyEntryMapData(
        trackPolylines = emptyList(),
        findMarkers = emptyList(),
        waypointMarkers = listOf(RecordPoint("w1", LatLng(45.5, -122.5))),
        photoMarkers = emptyList(),
        offlineRegionCircles = emptyList(),
    )

    @Composable
    private fun EntryScreen(dark: Boolean) {
        ForagerTheme(darkTheme = dark) {
            CartographyEntryReportScreen(
                entry = entry,
                galleryPhotos = emptyList(),
                distanceUnit = DistanceUnit.MILES,
                mapSlot = slot,
                night = dark,
                getMapData = { _, _ -> mapData },
                getCoveringOfflineRegion = { _, _ -> null },
                getCurrentLocation = { LocationResult.LocationUnavailable },
                onEdit = {},
                onDeleteEntry = {},
                onBack = {},
            )
        }
    }

    /** The entry's map, opened fullscreen by a tap on it, where its icon bar and Layers row are. */
    private fun setEntryMap(dark: Boolean) {
        composeRule.setContent { EntryScreen(dark) }
        composeRule.waitForIdle()
        onEntryMapTap?.invoke() ?: error("the map section never resolved")
        composeRule.waitForIdle()
    }

    /** A real touch at the centre of the Layers row, and the panel is open. */
    private fun openPanel() {
        val row = composeRule.onNodeWithContentDescription("Layers:", substring = true).getUnclippedBoundsInRoot()
        val at = with(composeRule.density) { Offset(((row.left + row.right) / 2).toPx(), ((row.top + row.bottom) / 2).toPx()) }
        composeRule.onRoot().performTouchInput { click(at) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MAP_TYPE_PANEL_TAG).assertIsDisplayed()
    }

    /** The drawn chip's middle row of pixels. */
    private fun middleRow(mode: MapMode): List<Color> {
        val pixels = composeRule.onNodeWithTag(mapModeChipTag(mode)).captureToImage().toPixelMap()
        return (0 until pixels.width).map { pixels[it, pixels.height / 2] }
    }

    /** The drawn fill: a pixel 3 dp in from the chip's left edge, half way down, clear of its label (as `MapTypePanelOfflineChipTest`). */
    private fun drawnFill(mode: MapMode): Color = middleRow(mode)[with(composeRule.density) { 3.dp.roundToPx() }]

    private fun recorded(mode: MapMode) = composeRule.onNodeWithTag(mapModeChipTag(mode)).fetchSemanticsNode().config

    /** Light: the selected Trail chip is drawn `secondary`, and left of its fill there is an `onSurface` outline. */
    private fun assertLightSelectedChip(where: String) {
        composeRule.onNodeWithTag(mapModeChipTag(MapMode.TRAIL)).assertIsSelected()
        assertEquals("$where: the selected chip's drawn fill is secondary", LightColors.secondary, drawnFill(MapMode.TRAIL))
        assertEquals("$where: its recorded fill", LightColors.secondary, recorded(MapMode.TRAIL)[MapModeChipSelectedFill])
        assertEquals("$where: its recorded outline", LightColors.onSurface, recorded(MapMode.TRAIL)[MapModeChipSelectedOutline])
        val row = middleRow(MapMode.TRAIL)
        val firstFill = row.indexOf(LightColors.secondary)
        assertTrue("$where: the fill is drawn on the middle row", firstFill >= 0)
        // With no outline the fill starts at the node's own left edge (revert r19 showed it), so nothing is left of it.
        assertTrue("$where: an onSurface outline is drawn left of the fill", row.subList(0, firstFill).contains(LightColors.onSurface))
    }

    /**
     * Dark (Amendment 6, RECORD -1774; the owner: "Both themes. Make it uniform"): the fill unchanged, `secondaryContainer`,
     * now with the dark theme's `onSurface` outline left of it.
     */
    private fun assertDarkSelectedChip(where: String) {
        composeRule.onNodeWithTag(mapModeChipTag(MapMode.TRAIL)).assertIsSelected()
        assertEquals("$where: the dark selected chip's drawn fill", DarkColors.secondaryContainer, drawnFill(MapMode.TRAIL))
        assertEquals("$where: its recorded outline", DarkColors.onSurface, recorded(MapMode.TRAIL)[MapModeChipSelectedOutline])
        val row = middleRow(MapMode.TRAIL)
        val firstFill = row.indexOf(DarkColors.secondaryContainer)
        assertTrue("$where: the fill is drawn on the middle row", firstFill >= 0)
        assertTrue("$where: an onSurface outline is drawn left of the fill", row.subList(0, firstFill).contains(DarkColors.onSurface))
    }

    @Test
    fun `Maps tab, light theme, the selected chip is filled secondary with an onSurface outline`() {
        setMapsTab(dark = false)
        openPanel()
        assertLightSelectedChip("Maps tab")
    }

    @Test
    fun `entry map, light theme, the selected chip is filled secondary with an onSurface outline`() {
        setEntryMap(dark = false)
        openPanel()
        assertLightSelectedChip("entry map")
    }

    @Test
    fun `Maps tab, dark theme, the selected chip keeps its fill and has an onSurface outline`() {
        setMapsTab(dark = true)
        openPanel()
        assertDarkSelectedChip("Maps tab")
    }

    @Test
    fun `entry map, dark theme, the selected chip keeps its fill and has an onSurface outline`() {
        setEntryMap(dark = true)
        openPanel()
        assertDarkSelectedChip("entry map")
    }
}
