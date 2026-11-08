package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OUTLINE_WIDTH
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_DARK
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_LIGHT
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import com.zynergylabs.forager.app.ui.route.RoutePlottingControls
import com.zynergylabs.forager.app.ui.route.RoutePlottingViewModel
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-11-05-10, Amendment 1, E21 (RECORD -2181). On the S22, E20's line down the navigation card's end, where the side bar's
 * rounded bottom corner curves away, ran about 1 dp nearer the map than the side bar's line above it: a visible step. The owner:
 * "Option 1. It has never been any other way. This must be from context drift." (the option: "Where the navigation card meets the side
 * bar, one shared line, no 1 dp step").
 *
 * The claim, read from rendered pixels of the real [AvailabilityScreen] over a flat grey stand-in map, in landscape with the side bar
 * shown, at both rotations, with and without a saved route's card resting on the navigation card, by day and at night, at two sizes:
 * **below where the side bar's corner starts to curve, the card's end line lies in exactly the columns the side bar's line lies in just
 * above it, as wide, and in the outline's colour.**
 *
 * How a row's line is found: in a window across the card's end, a pixel is the line's when it lies on the outline's side (darker by day,
 * lighter at night) of the card's fill read 2 dp inside the card, by more than half the contrast the outline makes over that fill (the
 * half-gap rule of `NavigationCardOutlineEveryEdgeTests`), and is not the flat colour read 2 dp outside the card (within [EXACT]). The
 * outside colour is excluded rather than read for the outline's side, because at night the outline over the card's fill comes within
 * 0.02 of the stand-in map's grey (red run 42440723930: E20's line read 0.522 at night, the map 0.502), so a pixel lighter than the map
 * would have missed E20's line and reported no line at all, not where it was. The side bar's line is read on the six rows from 6 dp to
 * 1 dp above where its corner starts to curve (outside: the side bar's fill). The card's end line is read on every row below that whose
 * pixels from just past the side bar's line out to 2 dp beyond the card's edge are all bare map, so neither the side bar nor its curving
 * line is in the window (outside: the map). At least one such row must be found.
 */
abstract class NavigationCardEndLineInLineTests(private val dark: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val t = ROUTE_CARD_NOON_CLOCK.nowEpochMillis()
    // The made-up point P (dispatch 2026-11-05-10's rules), and the start a short way from it.
    private val fix = LocationFix.Update(lat = P_LAT, lng = P_LNG, altitude = 104.9, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = P_LAT - 0.0065, lng = P_LNG + 0.01, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private val bareMap = Color(0xFF808080)
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.background(bareMap)) }
    private val outline = if (dark) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT
    private val theme = if (dark) "night" else "day"
    private lateinit var image: PixelMap

    private val plotting = RoutePlottingViewModel(
        routeLeg = { a, b -> straight(a, b) },
        saveRoute = { _, _, _ -> Result.failure(IllegalStateException("not saved in this test")) },
        onSaved = {},
        errorLog = { _, _, _ -> },
    )

    /** RECORD -956: the side bar is on the window's right at both rotations; the insets are the S22's, mirrored at rotation 270. */
    protected fun check(rotation: Int, routeCard: Boolean) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        setScreen()
        val rotation90 = rotation == Surface.ROTATION_90
        applyInsets(left = if (rotation90) 25.dp else 48.dp, right = if (rotation90) 48.dp else 25.dp, navigationOnRight = rotation90)
        assertEquals("the side bar shows", 1, count(COMPACT_NAVIGATION_RAIL_TAG))
        if (routeCard) {
            composeRule.runOnUiThread { plotting.onOpenSavedRoute(ROUTE_TRIP, fromTripPlanner = false) }
            settle()
            assertEquals("the route card shows", 1, count(ROUTE_CARD_TAG))
        }
        val card = bounds(NAVIGATION_HUD_TAG)
        val rail = bounds(COMPACT_NAVIGATION_RAIL_TAG)
        val name = "${if (rotation90) "rot90" else "rot270"}-${if (routeCard) "route-card" else "no-route-card"}-$theme"
        println("MEASURED E21 $name: card $card, side bar $rail")
        assertEquals("$name: the card's end meets the side bar (card $card, side bar $rail)", rail.left.value, card.right.value, 0.01f)
        image = composeRule.onRoot().captureToImage().toPixelMap()

        val edge = px(card.right)
        val strokePx = px(MAP_CHROME_OUTLINE_WIDTH)
        val window = (edge - px(4.dp))..(edge + strokePx)
        val curveStart = px(rail.bottom - MAP_CARD_CORNER_RADIUS)

        // The side bar's line, just above where its corner curves.
        val railRows = (curveStart - px(6.dp))..(curveStart - px(1.dp))
        val railColumns = railRows.map { y -> lineColumns(y, window, edge) }.distinct()
        println("MEASURED E21 $name: the side bar's line, rows $railRows, columns $railColumns (the card's edge at x $edge)")
        assertEquals("$name: the side bar's line lies in the same columns on every row just above its corner: $railColumns", 1, railColumns.size)
        val railLine = railColumns.single()
        assertEquals("$name: the side bar's line is a stroke ($strokePx px) wide: $railLine", strokePx, railLine.size)

        // The card's end line, below, on every row where the side bar and its curving line are clear of the window.
        val clear = (curveStart until px(card.bottom)).filter { y -> ((edge + strokePx)..(edge + px(2.dp))).all { x -> isMap(x, y) } }
        assertTrue("$name: no row below the side bar's corner start has bare map beside the card's end (the spot was not reached)", clear.isNotEmpty())
        val steps = mutableListOf<String>()
        val offColour = mutableListOf<String>()
        val lineOverMap = lineOver(bareMap)
        for (y in clear) {
            val columns = lineColumns(y, window, edge)
            if (columns != railLine) steps += "row $y: the card's end line in columns $columns (${columns.firstOrNull()?.minus(railLine.first())?.let { "%+d px".format(it) } ?: "none"}), pixels ${window.joinToString { rgb(image[it, y]) }}"
            columns.filter { x -> !near(image[x, y], lineOverMap) }.forEach { x -> offColour += "($x, $y) ${rgb(image[x, y])}, the outline over the map ${rgb(lineOverMap)}" }
        }
        println("MEASURED E21 $name: ${clear.size} rows read below the corner start (y ${clear.first()} to ${clear.last()}), off the side bar's line ${steps.size}, off the outline's colour ${offColour.size}")
        steps.take(4).forEach { println("STEP E21 $name: $it") }
        assertTrue("$name: the card's end line steps off the side bar's line (columns $railLine) on ${steps.size} of ${clear.size} rows; first: ${steps.firstOrNull()}", steps.isEmpty())
        assertTrue("$name: ${offColour.size} pixels of the card's end line are not the outline's colour over the map; first: ${offColour.firstOrNull()}", offColour.isEmpty())
    }

    /** The x columns of row [y] within [window] that are the outline's (see the class doc). */
    private fun lineColumns(y: Int, window: IntRange, edge: Int): List<Int> {
        val inside = image[edge - px(2.dp), y]
        val outside = image[edge + px(2.dp), y]
        return window.filter { x -> onLineSide(image[x, y], inside) && !exact(image[x, y], outside) }
    }

    private fun onLineSide(p: Color, ref: Color): Boolean {
        val gap = lum(lineOver(ref)) - lum(ref)
        return (lum(p) - lum(ref)) * sign(gap) > abs(gap) / 2f
    }

    private fun lineOver(fill: Color): Color {
        val a = outline.alpha
        return Color(red = outline.red * a + fill.red * (1f - a), green = outline.green * a + fill.green * (1f - a), blue = outline.blue * a + fill.blue * (1f - a))
    }

    private fun exact(p: Color, q: Color) = abs(p.red - q.red) < EXACT && abs(p.green - q.green) < EXACT && abs(p.blue - q.blue) < EXACT
    private fun near(p: Color, q: Color) = abs(p.red - q.red) < COLOUR_MATCH && abs(p.green - q.green) < COLOUR_MATCH && abs(p.blue - q.blue) < COLOUR_MATCH
    private fun isMap(x: Int, y: Int) = x in 0 until image.width && y in 0 until image.height && near(image[x, y], bareMap)
    private fun lum(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    private fun rgb(c: Color) = "(%.3f, %.3f, %.3f)".format(c.red, c.green, c.blue)

    private fun setScreen() {
        composeRule.setContent {
            val state by plotting.uiState.collectAsState()
            ForagerTheme(darkTheme = dark) {
                AvailabilityScreen(
                    uiState = AvailabilityUiState(liveFix = fix, plannedTrips = listOf(ROUTE_TRIP)).withFixHeight(),
                    recording = AvailabilityScreenRecording(isRecording = true),
                    navigation = AvailabilityScreenNavigation(
                        isReturning = true,
                        navigationTarget = start,
                        returnRoute = ReturnRoute.Ahead(LatLng(start.lat, start.lng), 245.0),
                    ),
                    compassProvider = FixedCompass(134f),
                    computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                    currentTime = ROUTE_CARD_NOON_CLOCK,
                    mapSlot = map,
                    mapControls = AvailabilityScreenMapControls(routePlotting = RoutePlottingControls(state, plotting)),
                    searchInputs = AvailabilityScreenSearchInputs(
                        onUseCurrentLocation = {}, onManualLatChanged = {}, onManualLngChanged = {}, onSearchManualCoordinates = {},
                        onRadiusChanged = {}, onMonthSelected = {}, onMapTabSelected = {}, onSeasonalTabSelected = {},
                        onTaxonSearchQueryChanged = {}, onTaxonSearchResultSelected = {}, onDismissTaxonSuggestions = {},
                        onReopenTaxonSuggestions = {}, onPlaceTripPin = { _, _, _ -> }, onDeletePlannedTrip = {}, onRecentSearchSelected = {},
                    ),
                    offlineMaps = AvailabilityScreenOfflineMaps(
                        onOfflineMapLatChanged = {}, onOfflineMapLngChanged = {}, onOfflineMapRadiusChanged = {}, onOfflineMapNameChanged = {},
                        onOfflineMapsOpened = {}, onDownloadOfflineMaps = {}, onDeleteOfflineRegion = {},
                    ),
                    settings = AvailabilityScreenSettings(onNightModeMapsChanged = {}, onThemeModeChanged = {}),
                )
            }
        }
        settle()
    }

    private fun applyInsets(left: Dp, right: Dp, navigationOnRight: Boolean) {
        val l = with(composeRule.density) { left.roundToPx() }
        val r = with(composeRule.density) { right.roundToPx() }
        val (cutout, navigation) = if (navigationOnRight) {
            androidx.core.graphics.Insets.of(l, 0, 0, 0) to androidx.core.graphics.Insets.of(0, 0, r, 0)
        } else {
            androidx.core.graphics.Insets.of(0, 0, r, 0) to androidx.core.graphics.Insets.of(l, 0, 0, 0)
        }
        val insets = androidx.core.view.WindowInsetsCompat.Builder()
            .setInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout(), cutout)
            .setInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars(), navigation)
            .build()
        composeRule.runOnUiThread {
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(composeRule.activity.window.decorView.findViewById(android.R.id.content), insets)
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
    }

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size
    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
    private fun px(d: Dp): Int = (d.value * composeRule.density.density).roundToInt()

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    @Test fun `rotation 90, route card on the navigation card`() = check(Surface.ROTATION_90, routeCard = true)

    @Test fun `rotation 90, no route card`() = check(Surface.ROTATION_90, routeCard = false)

    @Test fun `rotation 270, route card on the navigation card`() = check(Surface.ROTATION_270, routeCard = true)

    @Test fun `rotation 270, no route card`() = check(Surface.ROTATION_270, routeCard = false)

    private companion object {
        /** The made-up point P, from dispatch 2026-11-05-10's rules. */
        const val P_LAT = 45.3736
        const val P_LNG = -121.6959

        /** How close two colours are read as the same, per channel. */
        const val COLOUR_MATCH = 0.03f

        /** Two pixels of one flat fill: within 2 of 255 per channel. */
        const val EXACT = 0.008f
    }
}

/** The owner's S26 in landscape at its default display size, about 891 x 411 dp (inferred, not read from the phone), as E19/E20's check. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w891dp-h411dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardEndLineInLineS26DayTest : NavigationCardEndLineInLineTests(dark = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w891dp-h411dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardEndLineInLineS26NightTest : NavigationCardEndLineInLineTests(dark = true)

/** D32's case, as E19/E20's check: 640 x 360 dp at font 2.0, where both cards narrow for Re-center's room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardEndLineInLineNarrowDayTest : NavigationCardEndLineInLineTests(dark = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardEndLineInLineNarrowNightTest : NavigationCardEndLineInLineTests(dark = true)
