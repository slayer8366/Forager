package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import com.zynergylabs.forager.app.domain.BackByShown
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
import com.zynergylabs.forager.app.domain.SundownLine
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.NavigationSideInsets
import com.zynergylabs.forager.app.ui.map.navigationViewPaddingPx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
 * Dispatch 2026-11-06-01, Amendment 1, J8 (RECORD -2183). Sorrel's review: in landscape at font 2.0 with Try again and both evening
 * lines, the navigation card has no room to move up (J5: NoRoom), stays on the bottom edge and covers the walker's dot. The owner:
 * "No, fix it", then "Map moves dot up (Recommended)" ("The map keeps your position just above the card's top; the card stays
 * whole"); "(a) Landscape only".
 *
 * The camera is MapLibre's and cannot run under Robolectric, so the map is a stub. What this asserts, on the real Maps tab: the
 * padding the navigation view is asked for (`NavigationViewRequest.sideInsetsPx`, as the camera gets it through
 * [navigationViewPaddingPx]), and where that puts the walker's dot: the centre of the padded area, `top + (H - top - bottom) / 2`
 * below the map's top. Where the card covers the dot the dot's centre sits [WALKER_DOT_CLEARANCE] above the card's top; where the card
 * already clears it the view asks for nothing (D29's test holds the same with no card at default font). Whether MapLibre then moves
 * the camera so is device-only.
 */
abstract class NavigationDotAboveCard {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val minute = 60_000L
    private val morning = 1_791_050_400_000L

    /** A made-up test point, as NextTurnCardRig's P; the start an offset from it. */
    private val here = LatLng(45.3736, -121.6959)
    private val origin = Waypoint(
        id = "origin", lat = here.lat + 380.0 / 111_195.0, lng = here.lng, altitude = null, name = "Start", note = "",
        createdAtEpochMillis = morning, trackId = "t1", designation = WaypointDesignation.ORIGIN,
    )
    private val east = LatLng(here.lat, here.lng + 0.001)

    private val utc = TimeZone.getTimeZone("UTC")
    private val clock = object : SundownClock {
        override fun full(epochMillis: Long): String = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
        override fun short(epochMillis: Long): String = SimpleDateFormat("h:mm", Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
    }
    private val sunset = morning + (4 * 60 + 58) * minute
    private val eveningLine = SundownLine.BeforeSunset(
        nowEpochMillis = sunset - 48 * minute,
        sunsetAtEpochMillis = sunset,
        civilDuskAtEpochMillis = sunset + 28 * minute,
        startBackAtEpochMillis = sunset - 10 * minute,
    )
    private val backByShown = BackByShown(trackId = "t1", backByAtEpochMillis = morning + 40 * minute, nowEpochMillis = morning)

    private var renderMode: MapRenderMode? = null
    private val slot: MapSlot = { _, _, mode, _, _, _, _, _, modifier ->
        renderMode = mode
        Box(modifier.testTag(LAYOUT_FIXES_MAP_TAG).pointerInput(Unit) { detectTapGestures() })
    }

    private var route by mutableStateOf<ReturnRoute>(ReturnRoute.Ahead(east, 390.0))
    private var line by mutableStateOf<SundownLine?>(null)
    private var backBy by mutableStateOf<BackByShown?>(null)

    /** The L away from the card, as HudLandscapeT11Fixes' tallest case: with it below the card RECORD -770 takes the lines off. */
    private fun setScreen() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        composeRule.setContent {
            val state = AvailabilityUiState(
                region = Region(lat = here.lat, lng = here.lng, radiusKm = 15),
                liveFix = LocationFix.Update(lat = here.lat, lng = here.lng, altitude = 3_000.0, accuracyMeters = 12.5f, timestampEpochMillis = morning, provider = FixProvider.GPS),
                unitSystem = UnitSystem.IMPERIAL,
                mapIconClusterPlacement = MapIconClusterPlacement.DEFAULT,
            ).withFixHeight()
            CompositionLocalProvider(LocalSundownClock provides clock) {
                AvailabilityScreen(
                    uiState = state,
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
                    mapSlot = slot,
                    navigation = AvailabilityScreenNavigation(isReturning = true, onToggleReturning = {}, navigationTarget = origin, returnRoute = route, onRetryRoute = {}),
                    recording = AvailabilityScreenRecording(isRecording = true, onToggleRecording = {}, recordingSundownLine = line, recordingBackBy = backBy),
                    mapControls = AvailabilityScreenMapControls(onLocateMe = {}, onMapIconClusterPlacementChanged = {}),
                    compassProvider = object : CompassProvider {
                        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(281f, HeadingUncertainty.Estimated(2f), 0L))
                    },
                    computeTrueHeading = ComputeTrueHeadingUseCase(object : DeclinationProvider {
                        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
                    }),
                    currentTime = CurrentTimeProvider { morning },
                )
            }
        }
        settle()
    }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(3_000L)
        composeRule.waitForIdle()
    }

    private fun nodes(tag: String): List<SemanticsNode> = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()

    private fun tag(tag: String): DpRect = with(composeRule.density) {
        val b = nodes(tag).single().boundsInRoot
        DpRect(b.left.toDp(), b.top.toDp(), b.right.toDp(), b.bottom.toDp())
    }

    private fun insets(): NavigationSideInsets =
        renderMode?.navigationView?.sideInsetsPx ?: throw AssertionError("navigating, so the map was asked for its navigation view")

    /** Where the camera puts the walker's dot for [side]: the centre of the padded area, read off the padding the camera is sent. */
    private fun dotFor(side: NavigationSideInsets): Pair<Dp, Dp> = with(composeRule.density) {
        val map = tag(LAYOUT_FIXES_MAP_TAG)
        val widthPx = (map.right - map.left).toPx()
        val heightPx = (map.bottom - map.top).toPx()
        val (left, top, right, bottom) = navigationViewPaddingPx(heightPx.toInt(), side).toList()
        val x = map.left + (left + (widthPx - left - right) / 2).toFloat().toDp()
        val y = map.top + (top + (heightPx - top - bottom) / 2).toFloat().toDp()
        x to y
    }

    /** Whether [card] reaches into the dot's square (its clearance each way), by more than [slack]. */
    private fun covers(card: DpRect, dot: Pair<Dp, Dp>, slack: Dp = 0.dp): Boolean {
        val (x, y) = dot
        val r = WALKER_DOT_CLEARANCE - slack
        return card.left < x + r && card.right > x - r && card.top < y + r && card.bottom > y - r
    }

    /**
     * J8: where the card on the bottom edge would cover the dot as the view stood before (no bottom padding), the dot's centre is its
     * clearance above the card's top; where it would not, the view asks for nothing more. Returns whether the card covered it.
     */
    private fun assertDotClear(where: String): Boolean {
        val card = tag(NAVIGATION_HUD_TAG)
        val side = insets()
        val before = dotFor(NavigationSideInsets.NONE)
        val now = dotFor(side)
        val covered = covers(card, before)
        println("J8 $where: card $card, side $side, dot before ${before.second.value} (x ${before.first.value}), now ${now.second.value}, covered before $covered, Try again ${nodes(NAVIGATION_HUD_RETRY_TAG).size}, evening ${nodes(NAVIGATION_HUD_SUNDOWN_LINE_TAG).size}")
        assertTrue("J8 $where: the card $card covers none of the walker's dot at y ${now.second}, x ${now.first}", !covers(card, now, slack = 0.5.dp))
        if (covered) {
            assertEquals("J8 $where: the dot's centre sits its clearance above the card's top ${card.top}, within half a dp", (card.top - WALKER_DOT_CLEARANCE).value, now.second.value, 0.5f)
            assertEquals("J8 $where: only up and down: the dot stays where it was across", before.first.value, now.first.value, 0.5f)
        } else {
            assertEquals("J8 $where: the card already clears the dot, so the view is unchanged", NavigationSideInsets.NONE, side)
        }
        return covered
    }

    private fun setWithheldEvening() {
        route = ReturnRoute.Unavailable(canRetry = true)
        line = eveningLine
        backBy = backByShown
    }

    @Test @Config(fontScale = 2.0f)
    fun `J8 at font 2,0 with Try again and both evening lines the dot sits just above the card, and comes back as the card shrinks`() {
        setWithheldEvening()
        setScreen()
        assertTrue("precondition: Try again is up", nodes(NAVIGATION_HUD_RETRY_TAG).isNotEmpty())
        assertTrue("positive control: Sorrel's case, the card on the bottom edge covers the dot as the view stood", assertDotClear("Try again and the evening lines"))
        val raised = insets()
        // The card shrinks: the route comes back and the evening ends.
        composeRule.runOnIdle { route = ReturnRoute.Ahead(east, 390.0); line = null; backBy = null }
        settle()
        assertTrue("precondition: Try again has gone", nodes(NAVIGATION_HUD_RETRY_TAG).isEmpty())
        assertDotClear("the card shrunk")
        assertTrue("J8: the shrunk card lets the dot back down (bottom padding ${raised.bottomPadding()} then ${insets().bottomPadding()})", insets().bottomPadding() < raised.bottomPadding())
    }

    @Test
    fun `J8 at font 1,0 with Try again and both evening lines the card clears the dot and the view is unchanged`() {
        setWithheldEvening()
        setScreen()
        assertTrue("precondition: Try again is up", nodes(NAVIGATION_HUD_RETRY_TAG).isNotEmpty())
        assertTrue("precondition: the card already clears the dot here", !assertDotClear("font 1.0, Try again and the evening lines"))
    }

    @Test
    fun `J8 at font 1,0 on the way home the card clears the dot and the view is unchanged`() {
        setScreen()
        assertTrue("precondition: the card already clears the dot here", !assertDotClear("font 1.0, on the way home"))
    }

    private fun NavigationSideInsets.bottomPadding(): Double = navigationViewPaddingPx(1000, this)[3]
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationDotAboveCardAt780Test : NavigationDotAboveCard()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationDotAboveCardAt823Test : NavigationDotAboveCard()
