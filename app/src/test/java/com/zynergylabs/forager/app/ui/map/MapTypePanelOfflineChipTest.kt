package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.map.TRAIL_STYLE_URL
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Dispatch 2026-10-22-06 Amendment 3 (the owner: "Have the toggle color be grey to reflect it's chosen, but offline."),
 * through the map type panel (the Layers sheet until dispatch 2026-10-28-02) as the app composes it: inside `MainActivity`'s root, [ForagerThemeWithNoSignal], with the
 * no-signal state the map reads. The steps the owner confirmed:
 *
 * ```
 * Topo chosen, signal on      → Topo in its normal selected colour
 * signal lost (map shows Trail) → Topo stays selected, filled grey
 * signal back                 → normal selected colour again
 * Trail chosen                → unchanged; Trail is not highlighted while it stands in
 * ```
 *
 * The fill is read from the drawn pixels (a point inside each chip, left of its label), in the dark and the light
 * theme, and the chip's own record of its selected fill is read alongside it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapTypePanelOfflineChipTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val noSignal = MutableStateFlow(false)
    private var offlineTiles by mutableStateOf(false)
    private val fallback = OwnTrailFallback(log = {})
    private var darkTheme by mutableStateOf(true)
    private var chosen by mutableStateOf(MapMode.TOPOGRAPHIC)

    /** The Offline maps list `MainActivity` feeds its root: one downloaded area, unless a test says otherwise. */
    private var offlineRegions by mutableStateOf(listOf(region(1, isDownloaded = true)))
    private val choices = mutableListOf<MapMode>()
    private var normalSelected = Color.Unspecified
    private var grey = Color.Unspecified
    private var onSurface = Color.Unspecified

    private fun setSheet() {
        composeRule.setContent {
            ForagerThemeWithNoSignal(darkTheme = darkTheme, noSignal = noSignal, offlineRegions = offlineRegions) {
                // Dispatch 2026-11-04-07 (RECORD -2092, K1): the normal selected fill follows the theme, to the ruled values: by day
                // Material's own pale `secondaryContainer` (A2), at night the rust red #A2612C (A3, A6). The night value is written
                // out, not read from MapModeChips, so a chip drawn any other colour fails.
                normalSelected = if (LocalForagerDarkTheme.current) Color(0xFFA2612C) else MaterialTheme.colorScheme.secondaryContainer
                grey = MaterialTheme.colorScheme.outlineVariant
                onSurface = MaterialTheme.colorScheme.onSurface
                MapTypePanel(
                    visible = true,
                    mapMode = chosen,
                    onModeSelected = { choices += it },
                    onDismiss = {},
                    modifier = Modifier.fillMaxSize(),
                    offlineTiles = offlineTiles,
                    fallback = fallback,
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** The drawn fill of [mode]'s chip: a pixel 3 dp in from its left edge, half way down, clear of its label. */
    private fun drawnFill(mode: MapMode): Color {
        val node = composeRule.onNodeWithTag(mapModeChipTag(mode))
        val pixels = node.captureToImage().toPixelMap()
        val x = with(composeRule.density) { 3.dp.roundToPx() }
        return pixels[x, pixels.height / 2]
    }

    /** The first 6 dp of [mode]'s chip's middle row, from its left edge: where an outline would be drawn, short of the label. */
    private fun leftEdge(mode: MapMode): List<Color> {
        val pixels = composeRule.onNodeWithTag(mapModeChipTag(mode)).captureToImage().toPixelMap()
        val n = with(composeRule.density) { 6.dp.roundToPx() }
        return (0 until n).map { pixels[it, pixels.height / 2] }
    }

    private fun recordedFill(mode: MapMode): Color =
        composeRule.onNodeWithTag(mapModeChipTag(mode)).fetchSemanticsNode().config[MapModeChipSelectedFill]

    private fun settle(update: () -> Unit) {
        update()
        composeRule.waitForIdle()
    }

    @Test
    fun `the chosen Topo chip turns grey while there is no signal and back when it returns, in both themes`() {
        setSheet()
        for (dark in listOf(true, false)) {
            settle { darkTheme = dark; noSignal.value = false }
            val theme = if (dark) "dark" else "light"
            composeRule.onNodeWithTag(mapModeChipTag(MapMode.TOPOGRAPHIC)).assertIsSelected()
            assertEquals("$theme, signal: Topo drawn in the normal selected fill", normalSelected, drawnFill(MapMode.TOPOGRAPHIC))

            settle { noSignal.value = true }
            composeRule.onNodeWithTag(mapModeChipTag(MapMode.TOPOGRAPHIC)).assertIsSelected()
            assertEquals("$theme, no signal: Topo drawn grey", grey, drawnFill(MapMode.TOPOGRAPHIC))
            assertEquals("$theme, no signal: Topo's selected fill", grey, recordedFill(MapMode.TOPOGRAPHIC))
            composeRule.onNodeWithTag(mapModeChipTag(MapMode.TRAIL)).assertIsNotSelected()
            assertEquals("$theme, no signal: Trail keeps the normal selected fill, unused", normalSelected, recordedFill(MapMode.TRAIL))

            settle { noSignal.value = false }
            assertEquals("$theme, signal back: Topo in the normal selected fill", normalSelected, drawnFill(MapMode.TOPOGRAPHIC))
        }
        assertEquals("the stored choice never changes", emptyList<MapMode>(), choices)
    }

    @Test
    fun `each chosen added map type's chip turns grey with no signal too`() {
        // Was "the chosen OSM chip": each added map type (dispatch 2026-11-03-05 D7; today the Street map).
        val added = ADDED_MAP_TYPES.map { it.mapMode }
        if (added.isEmpty()) return
        chosen = added.first()
        noSignal.value = true
        setSheet()
        for (mode in added) {
            settle { chosen = mode }
            composeRule.onNodeWithTag(mapModeChipTag(mode)).assertIsSelected()
            assertEquals("$mode", grey, drawnFill(mode))
        }
    }

    /** Elder's item 3 (dispatch 2026-10-23-07): grey only when Trail Map is actually drawn instead, so not with nothing downloaded. */
    @Test
    fun `with no downloaded area, no signal leaves the chosen Topo and OSM chips in the normal selected fill`() {
        // A region restored from a backup with no tiles here is not a downloaded area: nothing to draw Trail Map from.
        offlineRegions = listOf(region(1, isDownloaded = false))
        noSignal.value = true
        setSheet()
        val modes = MapMode.entries.filter { it != MapMode.TRAIL }
        for (mode in modes) {
            settle { chosen = mode }
            composeRule.onNodeWithTag(mapModeChipTag(mode)).assertIsSelected()
            assertEquals("$mode, no signal, nothing downloaded: drawn in the normal selected fill", normalSelected, drawnFill(mode))
            assertEquals("$mode, no signal, nothing downloaded: its selected fill", normalSelected, recordedFill(mode))
        }
        // A download landing while out of signal: Trail Map is now drawn instead, so the chosen chip turns grey.
        settle { offlineRegions = offlineRegions + region(2, isDownloaded = true) }
        assertEquals("${modes.last()}, a downloaded area appears: grey", grey, drawnFill(modes.last()))
    }

    private fun region(id: Long, isDownloaded: Boolean) = OfflineRegionSummary(
        id = id, name = "Region $id", region = Region(45.5, -122.6, 5), minZoom = 10.0, maxZoom = 15.0,
        tileCount = if (isDownloaded) 100 else 0, sizeBytes = if (isDownloaded) 2_000_000L else 0L, createdAtEpochMillis = 1_000L,
        isDownloaded = isDownloaded,
    )

    @Test
    fun `with offline tiles on and signal, the chosen Topo chip is grey too, and normal again when they are off`() {
        // The owner: "Same meaning everywhere: grey = chosen, but Trail is being shown instead."
        setSheet()
        for (dark in listOf(true, false)) {
            settle { darkTheme = dark; offlineTiles = true }
            val theme = if (dark) "dark" else "light"
            composeRule.onNodeWithTag(mapModeChipTag(MapMode.TOPOGRAPHIC)).assertIsSelected()
            assertEquals("$theme, offline tiles: Topo drawn grey", grey, drawnFill(MapMode.TOPOGRAPHIC))
            composeRule.onNodeWithTag(mapModeChipTag(MapMode.TRAIL)).assertIsNotSelected()
            settle { offlineTiles = false }
            assertEquals("$theme, offline tiles off: Topo in the normal selected fill", normalSelected, drawnFill(MapMode.TOPOGRAPHIC))
        }
        assertEquals("the stored choice never changes", emptyList<MapMode>(), choices)
    }

    /** Trail Map's style fails to load online, through the fallback's own entry point, as `MapListeners` calls it. */
    private fun failTrailMapStyle() = settle {
        assertEquals(true, fallback.onStyleLoadFailed(MapStyleRequest(MapStyleSource.Uri(TRAIL_STYLE_URL), useOfflineTiles = false), "Failed to load style: 404"))
    }

    @Test
    fun `once Trail Map has fallen back to OpenStreetMap, the chosen Trail chip is grey, still selected, in both themes`() {
        // The owner: "Same meaning everywhere: grey = chosen, but something else is shown." While a raster stands in (D8).
        if (trailMapStandIn == null) return
        chosen = MapMode.TRAIL
        setSheet()
        assertEquals("before the fallback", normalSelected, drawnFill(MapMode.TRAIL))
        failTrailMapStyle()
        for (dark in listOf(true, false)) {
            settle { darkTheme = dark }
            val theme = if (dark) "dark" else "light"
            composeRule.onNodeWithTag(mapModeChipTag(MapMode.TRAIL)).assertIsSelected()
            assertEquals("$theme, fallen back: Trail drawn grey", grey, drawnFill(MapMode.TRAIL))
            // Dispatch 2026-11-04-07 A4 (the owner: "No, normal chip only"; RECORD -2092, K3): the grey chip has no outline.
            assertTrue("$theme, fallen back: no onSurface outline on the grey Trail chip", leftEdge(MapMode.TRAIL).none { it == onSurface })
            MapMode.entries.filter { it != MapMode.TRAIL }.forEach { composeRule.onNodeWithTag(mapModeChipTag(it)).assertIsNotSelected() }
        }
        assertEquals("the stored choice never changes", emptyList<MapMode>(), choices)
    }

    @Test
    fun `fallen back with no signal, Trail Map is drawn from the downloads, so the Trail chip is normal`() {
        chosen = MapMode.TRAIL
        setSheet()
        failTrailMapStyle()
        settle { noSignal.value = true }
        assertEquals(normalSelected, drawnFill(MapMode.TRAIL))
    }

    @Test
    fun `the fallback leaves a chosen Topo chip normal, since Topo is still what is drawn`() {
        setSheet()
        failTrailMapStyle()
        assertEquals(normalSelected, drawnFill(MapMode.TOPOGRAPHIC))
    }

    @Test
    fun `with offline tiles on, a chosen Trail chip is unchanged`() {
        chosen = MapMode.TRAIL
        offlineTiles = true
        setSheet()
        assertEquals(normalSelected, drawnFill(MapMode.TRAIL))
    }

    @Test
    fun `a chosen Trail chip is unchanged with no signal`() {
        chosen = MapMode.TRAIL
        noSignal.value = true
        setSheet()
        composeRule.onNodeWithTag(mapModeChipTag(MapMode.TRAIL)).assertIsSelected()
        assertEquals(normalSelected, drawnFill(MapMode.TRAIL))
        assertEquals(normalSelected, recordedFill(MapMode.TRAIL))
    }

    @Test
    fun `a tap while offline is a real choice`() {
        noSignal.value = true
        setSheet()
        // A semantic click: the claim is the chip's wiring under no signal, not where a finger lands.
        composeRule.onNodeWithTag(mapModeChipTag(MapMode.TRAIL)).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(MapMode.TRAIL), choices)
    }
}
