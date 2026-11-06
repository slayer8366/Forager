package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertNotEquals
import com.zynergylabs.forager.app.ui.theme.LightColors
import com.zynergylabs.forager.app.ui.theme.DarkColors
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.log.RecordsSubTab
import com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_TAG
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_TAG
import com.zynergylabs.forager.app.ui.map.MAP_ICON_CLUSTER_CHILD_ALPHA
import com.zynergylabs.forager.app.ui.map.MAP_ICON_CLUSTER_CONTAINER_ALPHA
import com.zynergylabs.forager.app.ui.map.MapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/*
 * C1 (dispatch 2026-09-28-210): every piece of map chrome takes the navigation bar's colour. The owner, verbatim: "The map
 * chrome isn't aligned. The search panel and map icon bar are the wrong color. Have them be the same color as the bottom app
 * navigation bar. Make sure any other pop up or bubble, or the tool panel, is the same color as the app navigation bar also
 * please". And: "Opacity for the icon bar is fine as is."
 *
 * Each surface is reached through the real screen (`AvailabilityScreen`, in the app's own theme, dark and light), by the
 * entry point a person uses, and read through the `MapChromeContainerColor` semantics it carries beside its tag. The
 * expectation is the navigation bar's container colour (`MaterialTheme.colorScheme.surfaceContainer`, read in the same
 * composition) **aside from alpha**, and the alpha each surface already had: 0.8 (`MAP_CHROME_OVER_MAP_ALPHA`) for a single
 * layer, 0.6 for the cluster's container and 0.5 for the layers on it. No alpha assertion is changed by this file.
 *
 * `MapChromeOverMapTest` covers the sheets, dialogs, menus, drawer, snackbar and centre-pin row; this file covers the rest.
 */

private typealias ColourRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/** The one container colour set on the node tagged [tag]. */
private fun ColourRule.containerOn(tag: String): Color {
    val nodes = onAllNodes(hasTestTag(tag) and SemanticsMatcher.keyIsDefined(MapChromeContainerColor), useUnmergedTree = true).fetchSemanticsNodes()
    assertEquals("one node tagged $tag carries MapChromeContainerColor", 1, nodes.size)
    return nodes.single().config[MapChromeContainerColor]
}

/** [tag]'s container is [token], aside from alpha, at [alpha]. */
private fun ColourRule.assertToken(tag: String, token: Color, alpha: Float) {
    val container = containerOn(tag)
    assertEquals("$tag: container alpha", alpha, container.alpha, 0.002f)
    assertEquals("$tag: container colour aside from alpha, the navigation bar's", token.copy(alpha = 1f), container.copy(alpha = 1f))
}

private fun ColourRule.touchCentreOfTag(tag: String) {
    onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click(center) }
    waitForIdle()
}

private fun chromeColourGlyphs(x: Dp, firstY: Dp, step: Dp) = listOf(
    StubGlyph(MapLayerIds.WAYPOINTS, BUBBLE_WAYPOINT.id, x, firstY, LatLng(BUBBLE_WAYPOINT.lat, BUBBLE_WAYPOINT.lng)),
    StubGlyph(MapLayerIds.KEPT_TRACKS, BUBBLE_TRACK.id, x, firstY + step, LatLng(45.505, -122.6)),
)

// ---------------------------------------------------------------------------------------------------
// The compact Maps tab in portrait and in a short landscape window (the landscape L), each in dark and light.
// ---------------------------------------------------------------------------------------------------

abstract class MapChromeColourCompactTests(
    private val dark: Boolean,
    private val landscape: Boolean,
    glyphX: Dp,
    glyphY: Dp,
    glyphStep: Dp,
) {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(chromeColourGlyphs(glyphX, glyphY, glyphStep))
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    private fun setScreen() {
        composeRule.setContent { MapChromeTestScreen(state, map, roles, darkTheme = dark) }
        composeRule.waitForIdle()
    }

    private val navigationTag get() = if (landscape) COMPACT_NAVIGATION_RAIL_TAG else COMPACT_BOTTOM_NAV_TAG

    /** The cluster's bar and pill: two layers on the container in portrait (0.5), one layer at the standing 0.8 in the L. */
    private val barAlpha get() = if (landscape) MAP_CHROME_OVER_MAP_ALPHA else MAP_ICON_CLUSTER_CHILD_ALPHA

    @Test
    fun `the navigation bar or rail is the token at 0_8 over the Maps tab, and solid on another tab`() {
        setScreen()
        composeRule.assertToken(navigationTag, roles.token, MAP_CHROME_OVER_MAP_ALPHA)
        composeRule.onNodeWithText("List").performClick()
        composeRule.waitForIdle()
        composeRule.assertToken(navigationTag, roles.token, 1f)
    }

    /**
     * Dispatch 2026-10-12-08 (RECORD -945, the owner: "Lighter pill + shadow (Recommended)"; RECORD -951 for the roles): the pill
     * over the map is a shade lighter than the chrome token, the surface ramp's lightest end in each theme, still at 0.8. Was "the
     * search bar is the token at 0_8": `assertToken(SEARCH_ENTRY_BAR_TAG, roles.token, MAP_CHROME_OVER_MAP_ALPHA)`.
     */
    private val pillRole get() = if (dark) DarkColors.surfaceContainerHighest else LightColors.surfaceContainerLowest

    @Test
    fun `the search pill is a shade lighter than the token, at 0_8`() {
        setScreen()
        composeRule.assertToken(SEARCH_ENTRY_BAR_TAG, pillRole, MAP_CHROME_OVER_MAP_ALPHA)
        assertNotEquals("positive control: the pill's role is not the token", roles.token.copy(alpha = 1f), pillRole)
    }

    @Test
    fun `the search panel is the token at 0_8`() {
        setScreen()
        composeRule.touchCentreOfTag(ACTIVE_SEARCH_SUMMARY_TAG)
        composeRule.assertToken(SEARCH_DROPDOWN_TAG, roles.token, MAP_CHROME_OVER_MAP_ALPHA)
    }

    /**
     * Dispatch 2026-10-12-08 (RECORD -946, -951): in landscape the strip and the pill are the top bar, both in the pill's lighter
     * shade; in portrait the strip is the card tucked under the pill, the token. Was "the compass strip is the token at 0_8" in
     * both orientations.
     */
    @Test
    fun `the compass strip is the token at 0_8 in portrait, and the pill's lighter shade at 0_8 in landscape`() {
        setScreen()
        composeRule.assertToken("compass-elevation-strip", if (landscape) pillRole else roles.token, MAP_CHROME_OVER_MAP_ALPHA)
    }

    @Test
    fun `the navigation HUD is the token at 0_8`() {
        state.isReturning = true
        setScreen()
        composeRule.assertToken(NAVIGATION_HUD_TAG, roles.token, MAP_CHROME_OVER_MAP_ALPHA)
    }

    @Test
    fun `the icon cluster's container is the token at 0_6, where there is one`() {
        setScreen()
        if (landscape) {
            assertEquals("the landscape L draws no container", 0, composeRule.onAllNodes(hasTestTag(MAP_ICON_CLUSTER_TAG) and SemanticsMatcher.keyIsDefined(MapChromeContainerColor), useUnmergedTree = true).fetchSemanticsNodes().size)
        } else {
            composeRule.assertToken(MAP_ICON_CLUSTER_TAG, roles.token, MAP_ICON_CLUSTER_CONTAINER_ALPHA)
        }
    }

    @Test
    fun `the icon bar is the token, at its own alpha`() {
        setScreen()
        composeRule.assertToken(MAP_ICON_BAR_TAG, roles.token, barAlpha)
    }

    @Test
    fun `the record and return pill is the token, at its own alpha`() {
        setScreen()
        composeRule.assertToken("control-pill", roles.token, barAlpha)
    }

    @Test
    fun `the minimise handle's mark is the token at 0_8`() {
        setScreen()
        composeRule.assertToken("map-icon-bar-minimize-handle-mark", roles.token, MAP_CHROME_OVER_MAP_ALPHA)
    }

    @Test
    fun `the restore handle's mark is the token at 0_8`() {
        setScreen()
        composeRule.touchCentreOfTag("map-icon-bar-minimize-handle")
        composeRule.assertToken("map-icon-bar-restore-handle-mark", roles.token, MAP_CHROME_OVER_MAP_ALPHA)
    }

    @Test
    fun `a map bubble is the token at 0_8`() {
        setScreen()
        composeRule.touchCentreOfTag(glyphTag(BUBBLE_WAYPOINT.id))
        composeRule.onNodeWithTag(MAP_BUBBLE_TAG).assertIsDisplayed()
        composeRule.assertToken(MAP_BUBBLE_TAG, roles.token, MAP_CHROME_OVER_MAP_ALPHA)
    }

    @Test
    fun `the add-action tile is the token at 0_8`() {
        setScreen()
        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.assertToken(ADD_ACTION_TILE_TAG, roles.token, MAP_CHROME_OVER_MAP_ALPHA)
    }

    @Test
    fun `the search notice keeps its error colour, not the token`() {
        state.ui = state.ui.copy(errorMessage = "Something went wrong")
        setScreen()
        val notice = composeRule.containerOn(SEARCH_NOTICE_TAG)
        assertEquals("the notice's container is errorContainer, aside from alpha", roles.errorContainer.copy(alpha = 1f), notice.copy(alpha = 1f))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapChromeColourPortraitDarkTest : MapChromeColourCompactTests(dark = true, landscape = false, 60.dp, 380.dp, 120.dp)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapChromeColourPortraitLightTest : MapChromeColourCompactTests(dark = false, landscape = false, 60.dp, 380.dp, 120.dp)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class MapChromeColourShortLandscapeDarkTest : MapChromeColourCompactTests(dark = true, landscape = true, 420.dp, 150.dp, 50.dp)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class MapChromeColourShortLandscapeLightTest : MapChromeColourCompactTests(dark = false, landscape = true, 420.dp, 150.dp, 50.dp)

// ---------------------------------------------------------------------------------------------------
// The entry map's fullscreen icon bar, and "Download this area?" over the offline picker's map.
// ---------------------------------------------------------------------------------------------------

private val COLOUR_ENTRY: CartographyEntry = CartographyEntry.draft(id = "entry-1", date = BUBBLE_DAY, updatedAtEpochMillis = 1_000L).copy(
    isDraft = false,
    text = "A wet morning on the ridge.",
    waypointDecisions = listOf(WaypointDecision(BUBBLE_WAYPOINT.id, BUBBLE_WAYPOINT.name, BUBBLE_WAYPOINT.lat, BUBBLE_WAYPOINT.lng, kept = true)),
)

private val COLOUR_ENTRY_MAP = CartographyEntryMapData(
    trackPolylines = emptyList(),
    findMarkers = emptyList(),
    waypointMarkers = listOf(RecordPoint(BUBBLE_WAYPOINT.id, LatLng(BUBBLE_WAYPOINT.lat, BUBBLE_WAYPOINT.lng))),
    photoMarkers = emptyList(),
    offlineRegionCircles = emptyList(),
)

abstract class MapChromeColourEntryAndDialogTests(private val dark: Boolean) {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, BUBBLE_WAYPOINT.id, 60.dp, 60.dp, LatLng(BUBBLE_WAYPOINT.lat, BUBBLE_WAYPOINT.lng))))
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    @Test
    fun `the entry map's fullscreen icon bar is the token at 0_8`() {
        state.cartography = CartographyUiState(entries = listOf(COLOUR_ENTRY), editingEntry = COLOUR_ENTRY)
        state.entryMapData = COLOUR_ENTRY_MAP
        composeRule.setContent { MapChromeTestScreen(state, map, roles, darkTheme = dark) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("A wet morning on the ridge.").assertExists()
        // Fullscreen by a real touch on the entry's map (a plain tap enters it).
        val slot = composeRule.onNodeWithTag("map-slot").getUnclippedBoundsInRoot()
        val density = composeRule.density.density
        composeRule.onRoot().performTouchInput { click(Offset((slot.right - 20.dp).value * density, (slot.bottom - 20.dp).value * density)) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Exit fullscreen").assertExists()
        composeRule.assertToken(MAP_ICON_BAR_TAG, roles.token, MAP_CHROME_OVER_MAP_ALPHA)
    }

    /** Owner, verbatim, on dialogs: "Dialogs ... 80% over the map" (the edge-case ruling, journal-redesign.md); "Download this area?" was opaque over the picker's map. */
    @Test
    fun `Download this area is the token at 0_8 over the offline picker's map`() {
        state.ui = state.ui.copy(offlineMapLatText = "45.5", offlineMapLngText = "-122.6", offlineMapRadiusKm = 10, offlineMapRadiusTouched = true)
        composeRule.setContent { MapChromeTestScreen(state, map, roles, darkTheme = dark) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Records").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.OFFLINE_MAPS)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Download Maps").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Download this area?").assertIsDisplayed()
        composeRule.assertToken(DOWNLOAD_CONFIRM_DIALOG_TAG, roles.token, MAP_CHROME_OVER_MAP_ALPHA)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapChromeColourEntryAndDialogDarkTest : MapChromeColourEntryAndDialogTests(dark = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapChromeColourEntryAndDialogLightTest : MapChromeColourEntryAndDialogTests(dark = false)
