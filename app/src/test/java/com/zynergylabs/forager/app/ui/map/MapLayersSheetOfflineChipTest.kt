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
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
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
 * through the Layers sheet as the app composes it: inside `MainActivity`'s root, [ForagerThemeWithNoSignal], with the
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
class MapLayersSheetOfflineChipTest {

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
    private var darkTheme by mutableStateOf(true)
    private var chosen by mutableStateOf(MapMode.TOPOGRAPHIC)
    private val choices = mutableListOf<MapMode>()
    private var normalSelected = Color.Unspecified
    private var grey = Color.Unspecified

    private fun setSheet() {
        composeRule.setContent {
            ForagerThemeWithNoSignal(darkTheme = darkTheme, noSignal = noSignal) {
                normalSelected = MaterialTheme.colorScheme.secondaryContainer
                grey = MaterialTheme.colorScheme.outlineVariant
                MapLayersSheet(
                    mapMode = chosen,
                    onMapModeSelected = { choices += it },
                    overlays = MAPS_TAB_OVERLAYS,
                    colourFields = emptyList(),
                    state = MapLayersState.DEFAULT,
                    onVisibilityChanged = { _, _ -> },
                    onOpacityChanged = { _, _ -> },
                    onColourFieldMoved = { _, _ -> },
                    onDismiss = {},
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
    fun `the chosen OSM chip turns grey with no signal too`() {
        chosen = MapMode.OPENSTREETMAP
        noSignal.value = true
        setSheet()
        composeRule.onNodeWithTag(mapModeChipTag(MapMode.OPENSTREETMAP)).assertIsSelected()
        assertEquals(grey, drawnFill(MapMode.OPENSTREETMAP))
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
