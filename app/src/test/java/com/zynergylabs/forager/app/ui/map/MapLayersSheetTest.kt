package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The Layers sheet at the map chrome's standing opacity (owner, over a screenshot of the sheet: "Can
 * this panel be given 80% opacity like the rest of the map chrome?"). The colours are read from the
 * composed sheet's semantics; the expected ones from the theme the sheet was composed in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapLayersSheetTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    // C1: was `surfaceContainerLow`; the sheet is now the navigation bar's colour, `surfaceContainer`.
    private var surfaceContainerLow = Color.Unspecified
    private var onSurface = Color.Unspecified

    private fun setSheet() {
        composeRule.setContent {
            ForagerTheme(darkTheme = true) {
                // C1 (dispatch 2026-09-28-210), the owner: "Make sure any other pop up or bubble, or the tool panel, is the same color as the app navigation bar also please".
                surfaceContainerLow = MaterialTheme.colorScheme.surfaceContainer
                onSurface = MaterialTheme.colorScheme.onSurface
                MapLayersSheet(
                    mapMode = MapMode.TRAIL,
                    onMapModeSelected = {},
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

    @Test
    fun `the sheet's container is its default container role at the map chrome's standing opacity`() {
        setSheet()

        val container = composeRule.onNodeWithTag(MAP_LAYERS_SHEET_TAG).fetchSemanticsNode().config[MapLayersSheetContainerColor]

        assertEquals("container alpha", MAP_CHROME_OVER_MAP_ALPHA, container.alpha, 0.001f)
        assertEquals("container colour, alpha aside", surfaceContainerLow.copy(alpha = 1f), container.copy(alpha = 1f))
    }

    @Test
    fun `the sheet's content colour stays the default container role's own, opaque`() {
        setSheet()

        val content = composeRule.onNode(SemanticsMatcher.keyIsDefined(MapLayersSheetContentColor))
            .fetchSemanticsNode().config[MapLayersSheetContentColor]

        assertEquals("content colour", onSurface, content)
    }
}
