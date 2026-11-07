package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.SundownLine
import com.zynergylabs.forager.app.domain.WaypointNavigation
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.domain.nextturn.NextTurn
import com.zynergylabs.forager.app.domain.nextturn.NextTurnArrow
import com.zynergylabs.forager.app.domain.nextturn.NextTurnBlank
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.Shadows

/**
 * Dispatch 2026-11-06-01, Amendment 1, J2 (RECORD -2183): the navigation card's next turn, read by bounds through the real
 * [AvailabilityScreen] in the app's own theme, as `NavigationCardKindLineScreen` reads #498's top line (its own type, not the default
 * the older screen tests use). The way home along a route ("1280 ft", "by trail") carries the next turn as the route tick hands it to
 * the card ([ReturnRoute.Ahead.nextTurn]); the way to a waypoint with no route is the straight line. The evening line is the screen's
 * own sundown line ([Evening]), its times in UTC. The map is a box that counts real long-presses (CLAUDE.md, the `Surface` pitfall).
 *
 * The point is P, a made-up test point; the targets are offsets from it. Robolectric reports no insets (CLAUDE.md, "Known pitfalls"),
 * which the card's widths do not depend on; the S22 steps cover the phone.
 */
abstract class NextTurnCardRig {

    protected val composeRule: ComposeContentTestRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    /** 2026-10-03T18:00:00Z, mid-morning at P: no sundown window open. */
    private val t = 1_791_050_400_000L

    /** The evening line in the right part, through the screen's own sundown line; the widest the fit probe read was "Dark since 10:23 PM". */
    protected enum class Evening(val line: ((Long) -> SundownLine)?) {
        NONE(null),
        BEFORE_SUNSET({ t -> SundownLine.BeforeSunset(t, t + 148 * 60_000L, t + 180 * 60_000L, null) }),
        AFTER_SUNSET({ t -> SundownLine.AfterSunset(t, t - 10 * 60_000L, t + 21 * 60_000L) }),
        DARK_SINCE({ t -> SundownLine.DarkSince(t - 37 * 60_000L) }),
        DARK_SINCE_LONG({ t -> SundownLine.DarkSince(t - (19 * 60 + 37) * 60_000L) }),
    }

    private val here = LatLng(45.3736, -121.6959)
    private val metresPerDegreeLat = 111_195.0
    private val start = Waypoint(
        id = "origin", lat = here.lat + 380.0 / metresPerDegreeLat, lng = here.lng, altitude = null, name = "Start", note = "",
        createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN,
    )
    private val waypoint = Waypoint(id = "wp", lat = here.lat + 381.0 / metresPerDegreeLat, lng = here.lng, altitude = null, name = "Point", note = "", createdAtEpochMillis = t)
    private val east = LatLng(here.lat, here.lng + 0.001)

    private val utc = TimeZone.getTimeZone("UTC")
    private val clock = object : SundownClock {
        override fun full(epochMillis: Long): String = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
        override fun short(epochMillis: Long): String = SimpleDateFormat("h:mm", Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
    }

    /** The way home along a route carrying [nextTurn], or (with [straightLine]) the way to a waypoint with no route. */
    private var nextTurnNow by mutableStateOf<NextTurn>(NextTurn.Blank(NextTurnBlank.NOT_YET_KNOWN))
    private var straightLine by mutableStateOf(false)
    private var evening by mutableStateOf(Evening.NONE)
    private val compass = MutableStateFlow<CompassReading?>(CompassReading(281f, HeadingUncertainty.Estimated(2f), 0L))
    private var composed = false

    /** Real long-presses the map took. */
    protected var mapLongPresses = 0
        private set

    private fun compose() {
        composeRule.setContent {
            CompositionLocalProvider(LocalSundownClock provides clock) {
                ForagerTheme(darkTheme = false) {
                    val straight = straightLine
                    AvailabilityScreen(
                        uiState = AvailabilityUiState(
                            liveFix = LocationFix.Update(lat = here.lat, lng = here.lng, altitude = 120.0, accuracyMeters = 3.79f, timestampEpochMillis = t, provider = FixProvider.GPS),
                            waypointNavigation = WaypointNavigation(waypoint.id, resumesReturn = false).takeIf { straight },
                        ).withFixHeight(),
                        recording = AvailabilityScreenRecording(isRecording = true, recordingSundownLine = evening.line?.invoke(t)),
                        navigation = AvailabilityScreenNavigation(
                            isReturning = !straight,
                            navigationTarget = start.takeIf { !straight },
                            returnRoute = ReturnRoute.Ahead(east, 390.0, nextTurn = nextTurnNow),
                        ),
                        records = AvailabilityScreenRecords(waypoints = listOf(waypoint), waypointsLoaded = true),
                        compassProvider = object : CompassProvider { override val heading: Flow<CompassReading?> = compass },
                        computeTrueHeading = ComputeTrueHeadingUseCase(object : DeclinationProvider {
                            override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
                        }),
                        currentTime = CurrentTimeProvider { t + 1_000L },
                        mapSlot = { _, _, _, _, _, _, _, _, modifier ->
                            Box(modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { mapLongPresses++ }) })
                        },
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
        }
        composed = true
    }

    /** Shows the way home carrying [turn] (or the straight line to a waypoint), with [e] in the right part, and settles it (3 s). */
    protected fun show(turn: NextTurn, e: Evening = Evening.NONE, straight: Boolean = false) {
        if (!composed) compose()
        composeRule.runOnIdle {
            nextTurnNow = turn
            evening = e
            straightLine = straight
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
    }

    /** The card as drawn: its bounds, the needle's square, the next turn's space, the left part, the top line's text. */
    protected class Card(
        val hud: DpRect,
        val needle: DpRect,
        /** The next turn's space; `null` where none is drawn. */
        val space: DpRect?,
        val spaceCount: Int,
        val arrow: NextTurnArrow?,
        val figure: String?,
        val spoken: String?,
        val topLine: DpRect,
        val distanceText: String,
        val dividerLeft: Dp,
    ) {
        /** The left part's right edge: the divider less its 6 dp gap. */
        val leftPartRight: Dp get() = dividerLeft - CARD_DIVIDER_GAP
        val leftPartMiddle: Dp get() = (topLine.left + leftPartRight) / 2

        /** Beside the needle: the space's middle is within the needle's row. Own line: it starts below the needle. */
        val place: NextTurnPlace?
            get() = space?.let { if ((it.top + it.bottom) / 2 < needle.bottom) NextTurnPlace.BESIDE_NEEDLE else NextTurnPlace.OWN_LINE }
    }

    private fun nodes(tag: String): List<SemanticsNode> =
        composeRule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(NAVIGATION_HUD_TAG)), useUnmergedTree = true).fetchSemanticsNodes()

    private fun rect(n: SemanticsNode) = with(composeRule.density) { DpRect(n.boundsInRoot.left.toDp(), n.boundsInRoot.top.toDp(), n.boundsInRoot.right.toDp(), n.boundsInRoot.bottom.toDp()) }

    protected fun card(where: String): Card {
        val hud = composeRule.onAllNodes(hasTestTag(NAVIGATION_HUD_TAG), useUnmergedTree = true).fetchSemanticsNodes()
        check(hud.size == 1) { "$where: expected one card, found ${hud.size}" }
        val spaces = nodes(NEXT_TURN_SPACE_TAG)
        val space = spaces.singleOrNull()
        val distance = nodes(NAVIGATION_HUD_DISTANCE_TAG).single()
        return Card(
            hud = rect(hud.single()),
            needle = rect(nodes(NAVIGATION_HUD_NEEDLE_TAG).single()),
            space = space?.let(::rect),
            spaceCount = spaces.size,
            arrow = space?.config?.getOrNull(NextTurnArrowKey),
            figure = space?.config?.getOrNull(NextTurnFigureKey),
            spoken = space?.config?.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(),
            topLine = rect(nodes(NAVIGATION_HUD_TOP_LINE_TAG).single()),
            distanceText = distance.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: "",
            dividerLeft = rect(nodes(NAVIGATION_HUD_DIVIDER_TAG).single()).left,
        )
    }

    protected fun Card.describe() = "card $hud, needle $needle, space $space ($spaceCount), arrow $arrow, figure <$figure>, " +
        "spoken <$spoken>, left part ${topLine.left}..$leftPartRight, top line <$distanceText>"
}
