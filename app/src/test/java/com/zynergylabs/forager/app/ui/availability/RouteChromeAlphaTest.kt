package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.MapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.MapChromeContentColor
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.route.DISCARD_ROUTE_DIALOG_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_SAVE_TAG
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_ADD_DATE_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_DATE_PICKER_TAG
import com.zynergylabs.forager.app.ui.route.SAVE_ROUTE_DIALOG_TAG
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The route card and the route's dialogs over the Maps tab's map are at the map chrome's alpha with their content opaque
 * (CLAUDE.md, "Nothing fully obstructs the map view"; dispatch 2026-10-30-08, Amendment 3). Read as MapChromeOverMapTest
 * reads every other surface: the container and content colours each surface marks through semantics
 * ([MapChromeContainerColor], [MapChromeContentColor]), against the theme's own role read in the same composition. Reached
 * through the real screen and real touches. MapChromeAlphaTest pins constants only, so this is a file of its own.
 *
 * "Discard this route?" marks its container only: no node in it carries a content colour, so its content is not read here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteChromeAlphaTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private val map = RouteMapSlot(
        listOf(
            StubGlyph(MapLayerIds.WAYPOINTS, CREEK.id, 60.dp, 300.dp, LatLng(CREEK.lat, CREEK.lng)),
            StubGlyph(MapLayerIds.WAYPOINTS, RIDGE.id, 60.dp, 360.dp, LatLng(RIDGE.lat, RIDGE.lng)),
            StubGlyph(MapLayerIds.PLANNED_TRIPS, ROUTE_TRIP.id, 60.dp, 420.dp, ROUTE_TRIP.location),
        ),
    )
    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { route, name, date -> Result.success(PlannedTrip("new", name, route.stops.first().position, date, route)) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    /** The navigation bar's container, the token every map chrome surface is drawn in, and its content colour. */
    private var token: Color = Color.Unspecified
    private var onToken: Color = Color.Unspecified

    private fun setScreen() {
        val viewModel = mapLayersViewModel(plannedTrips = listOf(ROUTE_TRIP))
        composeRule.setContent {
            ForagerTheme(darkTheme = true) {
                token = navigationBarContainerColor()
                onToken = contentColorFor(token)
                val state by plotting.uiState.collectAsState()
                MapLayersTestScreen(
                    viewModel = viewModel,
                    mapSlot = map.slot,
                    store = OneCellStore(),
                    waypoints = listOf(CREEK, RIDGE),
                    routePlotting = RoutePlottingControls(state, plotting),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun touchCentreOf(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput { click(Offset(width * 0.5f, height * 0.5f)) }
        composeRule.waitForIdle()
    }

    private fun plotCreekToRidge() {
        composeRule.runOnUiThread { plotting.onStartPlotting(CREEK.stop()) }
        composeRule.waitForIdle()
        touchCentreOf(glyphTag(RIDGE.id))
    }

    /** The one value of [key] on the node tagged [tag] (the key is set beside the tag). */
    private fun colourOn(tag: String, key: SemanticsPropertyKey<Color>): Color {
        val nodes = composeRule.onAllNodes(hasTestTag(tag) and SemanticsMatcher.keyIsDefined(key), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("one node tagged $tag carries ${key.name}", 1, nodes.size)
        return nodes.single().config[key]
    }

    /** The content colour on [tag]'s node or on the one descendant of it that carries one. */
    private fun contentColourIn(tag: String): Color {
        val nodes = composeRule.onAllNodes(
            (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))) and SemanticsMatcher.keyIsDefined(MapChromeContentColor),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertEquals("one node in $tag carries its content colour", 1, nodes.size)
        return nodes.single().config[MapChromeContentColor]
    }

    private fun assertContainerOverMap(tag: String) {
        val container = colourOn(tag, MapChromeContainerColor)
        assertEquals("$tag: container alpha", MAP_CHROME_OVER_MAP_ALPHA, container.alpha, 0.002f)
        assertEquals("$tag: container colour aside from alpha", token.copy(alpha = 1f), container.copy(alpha = 1f))
    }

    private fun assertContentOpaque(tag: String) {
        val inside = contentColourIn(tag)
        assertEquals("$tag: content alpha", 1f, inside.alpha, 0.002f)
        assertEquals("$tag: content colour", onToken, inside)
    }

    @Test
    fun `the route card while plotting is at the map chrome's alpha, its content opaque`() {
        setScreen()
        plotCreekToRidge()
        assertContainerOverMap(ROUTE_CARD_TAG)
        assertContentOpaque(ROUTE_CARD_TAG)
    }

    @Test
    fun `the route card on a saved route is at the map chrome's alpha, its content opaque`() {
        setScreen()
        touchCentreOf(glyphTag(ROUTE_TRIP.id))
        assertContainerOverMap(ROUTE_CARD_TAG)
        assertContentOpaque(ROUTE_CARD_TAG)
    }

    @Test
    fun `the save dialog over the map is at the map chrome's alpha, its content opaque`() {
        setScreen()
        plotCreekToRidge()
        touchCentreOf(ROUTE_CARD_SAVE_TAG)
        assertContainerOverMap(SAVE_ROUTE_DIALOG_TAG)
        assertContentOpaque(SAVE_ROUTE_DIALOG_TAG)
    }

    @Test
    fun `the save dialog's date picker over the map is at the map chrome's alpha`() {
        setScreen()
        plotCreekToRidge()
        touchCentreOf(ROUTE_CARD_SAVE_TAG)
        touchCentreOf(SAVE_ROUTE_ADD_DATE_TAG)
        assertContainerOverMap(SAVE_ROUTE_DATE_PICKER_TAG)
    }

    @Test
    fun `the discard prompt over the map is at the map chrome's alpha`() {
        setScreen()
        plotCreekToRidge()
        touchCentreOf(glyphTag(ROUTE_TRIP.id))
        assertContainerOverMap(DISCARD_ROUTE_DIALOG_TAG)
    }
}
