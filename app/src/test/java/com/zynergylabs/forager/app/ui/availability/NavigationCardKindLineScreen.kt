package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
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
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
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
import kotlin.math.abs

/**
 * Dispatch 2026-11-05-10 (RECORD -2181): the navigation card's top line, read by bounds through the real [AvailabilityScreen] in the
 * app's own theme ([ForagerTheme], so the card is measured in the type the phone draws it in, not the default the other screen
 * tests use, which is about 4% narrower: CI run 42171184260).
 *
 * The owner: "For navigation cards have the 2.2 mi on the left half, and then move the "by trail" from the bottom to the top right
 * half, on the same line as 2.2 mi", "And do the same for landscape", "Meet at the middle (Recommended)", "In the left half",
 * "Keep the text size the same size".
 *
 * The states, each through the screen's own inputs: the way home along a route ("1280 ft", "by trail"); a waypoint with no route
 * (the straight line, "≈ 1250 ft", "straight"); arrived at the start ("Arrived", no kind); the way home before its first route (the
 * dash, no kind). The turn is "sharp right 169°" in the first two ([Facing.TURN]), the longest turn words, unless a case faces the
 * target ([Facing.AHEAD]) or has no compass ([Facing.NO_COMPASS]). Each state is settled before it is read, and every reading names
 * the state it was taken in.
 *
 * The point is P, a made-up test point; the targets are offsets from it. No evening line unless a case asks for one ([Evening]): at
 * this hour the screen's own sundown line has not opened, which each reading checks (no evening piece drawn).
 *
 * Native graphics (set by each subclass): without it Robolectric measures text at a few dp wide. Robolectric reports no insets
 * (CLAUDE.md, "Known pitfalls"), which this card's widths do not depend on; the S22 check covers the phone.
 */
abstract class NavigationCardKindLineScreen {

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

    /** The evening line in the right part, each through the screen's own sundown line, its times in UTC. */
    protected enum class Evening(val line: ((Long) -> SundownLine)?) {
        NONE(null),
        /** "Dark since 5:23 PM", the owner's screenshot. */
        DARK_SINCE({ t -> SundownLine.DarkSince(t - 37 * 60_000L) }),
        /** "Dark since 10:23 PM": a two-digit hour, one digit wider (the widest piece "Dark since" shows). */
        DARK_SINCE_LONG({ t -> SundownLine.DarkSince(t - (19 * 60 + 37) * 60_000L) }),
        /** Before sunset: "Sunset 8:28 PM · in 2 h 28 min · dark 9:00". */
        BEFORE_SUNSET({ t -> SundownLine.BeforeSunset(t, t + 148 * 60_000L, t + 180 * 60_000L, null) }),
        /** Between sunset and dusk: the sun set ten minutes ago, dark in 21 min. */
        AFTER_SUNSET({ t -> SundownLine.AfterSunset(t, t - 10 * 60_000L, t + 21 * 60_000L) }),
    }

    /** Which way the phone faces: 169° right of the aim, 1° right of it, or no compass at all. */
    protected enum class Facing { TURN, AHEAD, NO_COMPASS }

    private val here = LatLng(45.3736, -121.6959)
    private val metresPerDegreeLat = 111_195.0
    private val start = Waypoint(
        id = "origin", lat = here.lat + 380.0 / metresPerDegreeLat, lng = here.lng, altitude = null, name = "Start", note = "",
        createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN,
    )
    private val waypoint = Waypoint(id = "wp", lat = here.lat + 381.0 / metresPerDegreeLat, lng = here.lng, altitude = null, name = "Point", note = "", createdAtEpochMillis = t)

    /** The route's lookahead due east of P, so facing 281° turns 169° right to it. */
    private val east = LatLng(here.lat, here.lng + 0.001)

    private val utc = TimeZone.getTimeZone("UTC")
    private val clock = object : SundownClock {
        override fun full(epochMillis: Long): String = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
        override fun short(epochMillis: Long): String = SimpleDateFormat("h:mm", Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
    }

    protected enum class State(val figure: String, val kind: String?, val note: WayToWaypointNote? = null) {
        TRAIL("1280 ft", "by trail"),
        STRAIGHT("≈ 1250 ft", "straight"),
        ARRIVED("Arrived", null),
        DASH("—", null),
        /** Amendment 1, F2: the straight line with no trail route, for a note that only says why, "No trail routes here". */
        STRAIGHT_NOTE_GONE("≈ 1250 ft", "straight", WayToWaypointNote.NO_TRAIL_ROUTES),
        /** F2: the straight line with a note that asks something, "Trail routes not downloaded". */
        STRAIGHT_NOTE_KEPT("≈ 1250 ft", "straight", WayToWaypointNote.ROUTES_NOT_DOWNLOADED),
        ;

        /** Navigating to the waypoint, the straight line: with no route given, or with a note's straight line ([note]). */
        val straight: Boolean get() = this == STRAIGHT || note != null
    }

    private var state by mutableStateOf(State.TRAIL)
    private var evening by mutableStateOf(Evening.NONE)
    private val compass = MutableStateFlow<CompassReading?>(CompassReading(281f, HeadingUncertainty.Estimated(2f), 0L))

    private fun fix(): LocationFix.Update {
        val north = if (state == State.ARRIVED) 377.0 else 0.0
        return LocationFix.Update(lat = here.lat + north / metresPerDegreeLat, lng = here.lng, altitude = 120.0, accuracyMeters = 3.79f, timestampEpochMillis = t, provider = FixProvider.GPS)
    }

    private var composed = false

    private fun compose() {
        composeRule.setContent {
            CompositionLocalProvider(LocalSundownClock provides clock) {
                ForagerTheme(darkTheme = false) {
                    val straight = state.straight
                    AvailabilityScreen(
                        uiState = AvailabilityUiState(
                            liveFix = fix(),
                            waypointNavigation = WaypointNavigation(waypoint.id, resumesReturn = false).takeIf { straight },
                            // F2: a note's straight line, as the way to a waypoint publishes it (AvailabilityWaypointRoute).
                            waypointRoute = state.note?.let { WaypointRouteShown(hud = ReturnRoute.Straight(it), line = null) },
                        ).withFixHeight(),
                        recording = AvailabilityScreenRecording(isRecording = true, recordingSundownLine = evening.line?.invoke(t)),
                        navigation = AvailabilityScreenNavigation(
                            isReturning = !straight,
                            navigationTarget = start.takeIf { !straight },
                            returnRoute = if (state == State.DASH) ReturnRoute.Pending else ReturnRoute.Ahead(east, 390.0),
                        ),
                        records = AvailabilityScreenRecords(waypoints = listOf(waypoint), waypointsLoaded = true),
                        compassProvider = object : CompassProvider { override val heading: Flow<CompassReading?> = compass },
                        computeTrueHeading = ComputeTrueHeadingUseCase(object : DeclinationProvider {
                            override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
                        }),
                        currentTime = CurrentTimeProvider { t + 1_000L },
                        mapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) },
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

    /** Shows [s] (with "Dark since 5:23 PM" in the right part when [withEvening]) and settles it: 3 s of the test clock. */
    protected fun show(s: State, withEvening: Boolean = false) = show(s, if (withEvening) Evening.DARK_SINCE else Evening.NONE)

    /** Shows [s] with [e] in the right part, facing [facing], and settles it: 3 s of the test clock. */
    protected fun show(s: State, e: Evening, facing: Facing = Facing.TURN) {
        if (!composed) compose()
        // The way home's lookahead is due east, the waypoint due north. Facing 281° (191° for the waypoint) the aim is 169° to the
        // right; facing 89° (359°), 1° to the right.
        val aim = if (s.straight) 0f else 90f
        val faced = when (facing) {
            Facing.TURN -> (aim + 191f) % 360f
            Facing.AHEAD -> (aim + 359f) % 360f
            Facing.NO_COMPASS -> null
        }
        composeRule.runOnIdle {
            state = s
            evening = e
        }
        // The heading is smoothed (HeadingSmoother, 0.3 a reading), so one reading only moves it part of the way: the same heading is
        // read 30 times, each a new reading, so the shown heading is the one faced (0.7^30 of the old one is left).
        repeat(if (faced == null) 1 else 30) {
            composeRule.runOnIdle { compass.value = faced?.let { CompassReading(it, HeadingUncertainty.Estimated(2f), ++readings) } }
            composeRule.mainClock.advanceTimeBy(50)
        }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
    }

    private var readings = 0L

    /** A text node in the card: its bounds, its words, its first baseline in the root, its type size, and how it was laid out. */
    protected class Laid(val bounds: DpRect, val text: String, val baseline: Dp, val fontSp: Float, val layout: TextLayoutResult?)

    protected class Card(
        val hud: DpRect,
        val divider: DpRect,
        val figure: Laid,
        /** The kind, `null` where none is drawn. Stacked, it is the column of the two words, whose text is the whole kind. */
        val kind: Laid?,
        val turn: Laid?,
        val needle: DpRect,
        val heading: Laid?,
        val status: String,
        /** The status line's node: zero height where it is empty (CardRouteAndStatusLines). */
        val statusBox: DpRect?,
        val eveningPieces: Int,
        /** The top line, the left part's whole width. */
        val topLine: DpRect,
        /** "by" and "trail" where the kind is stacked, top first; empty otherwise. */
        val kindWords: List<Laid>,
        val pieces: List<Laid>,
        /** The X's tagged node: its 40 dp icon, centred in its 48 dp touch box. */
        val exit: DpRect,
        /** The north arrow, or the crossed-out compass in its place. */
        val northArrow: DpRect?,
    ) {
        /** The turn row's left: its words, or the needle where the words are empty (an empty text reports no place). */
        val turnRowLeft: Dp get() = if (turn == null || turn.text.isEmpty()) needle.left else turn.bounds.left
        val turnRowRight: Dp get() = heading?.bounds?.right ?: northArrow?.right ?: needle.right
        /** The left part's middle: its lines' centre, since the turn row is centred in it (NavigationInfoLayoutTest). */
        val middle: Dp get() = (turnRowLeft + turnRowRight) / 2
        val turnRowTop: Dp get() = needle.top
        val turnRowBottom: Dp get() = listOfNotNull(needle.bottom, heading?.bounds?.bottom, turn?.bounds?.bottom?.takeIf { turn.text.isNotEmpty() }).max()
        /** The left part's right edge: the divider less its 6 dp gap (CARD_DIVIDER_GAP). */
        val leftPartRight: Dp get() = divider.left - 6.dp
    }

    private fun nodes(tag: String): List<SemanticsNode> =
        composeRule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(NAVIGATION_HUD_TAG)), useUnmergedTree = true).fetchSemanticsNodes()

    private fun rect(n: SemanticsNode) = with(composeRule.density) { DpRect(n.boundsInRoot.left.toDp(), n.boundsInRoot.top.toDp(), n.boundsInRoot.right.toDp(), n.boundsInRoot.bottom.toDp()) }

    private fun laid(n: SemanticsNode): Laid {
        val results = mutableListOf<TextLayoutResult>()
        n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = results.firstOrNull()
        val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: ""
        val b = rect(n)
        val baseline = with(composeRule.density) { b.top + (layout?.firstBaseline ?: Float.NaN).toDp() }
        return Laid(b, text, baseline, layout?.layoutInput?.style?.fontSize?.value ?: Float.NaN, layout)
    }

    private fun single(tag: String, where: String): SemanticsNode = nodes(tag).let { found ->
        check(found.size == 1) { "$where: expected one <$tag> in the card, found ${found.size}" }
        found.single()
    }

    protected fun card(where: String): Card {
        val hud = composeRule.onAllNodes(hasTestTag(NAVIGATION_HUD_TAG), useUnmergedTree = true).fetchSemanticsNodes().single()
        val kind = nodes(NAVIGATION_HUD_DISTANCE_KIND_TAG)
        check(kind.size <= 1) { "$where: more than one kind word drawn: ${kind.size}" }
        return Card(
            hud = rect(hud),
            divider = rect(single(NAVIGATION_HUD_DIVIDER_TAG, where)),
            figure = laid(single(NAVIGATION_HUD_DISTANCE_TAG, where)),
            kind = kind.singleOrNull()?.let(::laid),
            turn = nodes(NAVIGATION_HUD_TARGET_TAG).singleOrNull()?.let(::laid),
            needle = rect(single(NAVIGATION_HUD_NEEDLE_TAG, where)),
            heading = nodes(NAVIGATION_HUD_HEADING_TAG).singleOrNull()?.let(::laid),
            status = nodes(NAVIGATION_HUD_STATUS_TAG).singleOrNull()?.let { laid(it).text } ?: "",
            statusBox = nodes(NAVIGATION_HUD_STATUS_TAG).singleOrNull()?.let(::rect),
            eveningPieces = nodes(NAVIGATION_HUD_EVENING_PIECE_TAG).size,
            topLine = rect(single(NAVIGATION_HUD_TOP_LINE_TAG, where)),
            kindWords = nodes(NAVIGATION_HUD_DISTANCE_KIND_WORD_TAG).map(::laid).sortedBy { it.bounds.top },
            pieces = nodes(NAVIGATION_HUD_EVENING_PIECE_TAG).map(::laid),
            exit = rect(single(NAVIGATION_HUD_EXIT_TAG, where)),
            northArrow = nodes(NAVIGATION_HUD_NORTH_ARROW_TAG).singleOrNull()?.let(::rect),
        )
    }

    /** [text]'s width in pixels in [like]'s own type, measured as the card measures (one line, no wrapping). */
    protected fun widthPx(like: Laid, text: String): Int {
        val input = checkNotNull(like.layout) { "<${like.text}> reports no text layout to measure in" }.layoutInput
        return TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection)
            .measure(text, input.style, maxLines = 1, softWrap = false).size.width
    }

    protected val densityPx: Float get() = composeRule.density.density

    protected fun printed(where: String, s: State, c: Card) = println(
        "TOPLINE|$where|$s|figure=<${c.figure.text}> ${c.figure.bounds} base ${c.figure.baseline}|kind=<${c.kind?.text}> ${c.kind?.bounds} base ${c.kind?.baseline}|" +
            "words=${c.kindWords.map { "<${it.text}> ${it.bounds} base ${it.baseline}" }}|top line ${c.topLine}|" +
            "middle ${c.middle}|turn row ${c.turnRowLeft}..${c.turnRowRight}, ${c.turnRowTop}..${c.turnRowBottom}|turn <${c.turn?.text}>|heading <${c.heading?.text}>|" +
            "divider ${c.divider}|exit ${c.exit}|card height ${c.hud.bottom - c.hud.top}|status <${c.status}>|pieces ${c.pieces.map { "<${it.text}> ${it.bounds.right - it.bounds.left}" }}",
    )

    /** The word gap, centred on the middle (Spacing.xs): the figure ends this far left of it, the kind starts this far right. */
    private val halfGap = 2.dp

    /**
     * Each problem with [s]'s top line as the owner's "Meet at the middle" has it: one line, meeting at the middle; with no kind, the
     * word or mark centred on the middle (B3, B4).
     */
    protected fun meetsAtTheMiddle(where: String, s: State, withEvening: Boolean = false): List<String> {
        show(s, withEvening)
        val c = card(where)
        printed(where, s, c)
        val problems = mutableListOf<String>()
        fun near(a: Dp, b: Dp, tolerance: Float = 1f) = abs((a - b).value) <= tolerance
        // Positive controls: the state is the one named, and the right part is as the case says.
        if (c.figure.text != s.figure) problems += "positive control: the distance reads <${c.figure.text}>, not <${s.figure}>"
        if ((c.eveningPieces > 0) != withEvening) problems += "positive control: ${c.eveningPieces} evening pieces drawn, evening=$withEvening"
        if (c.status.isNotEmpty() && s != State.DASH) problems += "positive control: the status line reads <${c.status}>, expected none"
        if (s.kind == null) {
            // B3 ("Arrived is simply centered with no extras") and B4 ("Centre it like Arrived (Recommended)"): a word or mark on its
            // own is centred on the middle. Was (B1, "In the left half"): ending at the middle less the half gap, as the distance.
            val centre = (c.figure.bounds.left + c.figure.bounds.right) / 2
            if (!near(centre, c.middle)) problems += "<${c.figure.text}> ${c.figure.bounds} is not centred on the middle ${c.middle}: its centre $centre"
        } else if (!near(c.figure.bounds.right, c.middle - halfGap)) {
            // "on the left half": the distance ends at the middle.
            problems += "the distance ${c.figure.bounds} does not end at the middle ${c.middle} (less the half gap)"
        }
        if (c.figure.bounds.left < c.hud.left - 0.5.dp) problems += "the distance ${c.figure.bounds} starts outside the card ${c.hud}"
        if (s.kind == null) {
            if (c.kind != null) problems += "a kind <${c.kind.text}> is drawn where there is none"
        } else {
            val kind = c.kind
            if (kind == null) {
                problems += "no kind drawn"
            } else {
                if (kind.text != s.kind) problems += "the kind reads <${kind.text}>, not <${s.kind}>"
                // "the top right half, on the same line as 2.2 mi": one baseline, the kind starting at the middle.
                if (!near(kind.baseline, c.figure.baseline, 0.5f)) problems += "the kind's baseline ${kind.baseline} is not the distance's ${c.figure.baseline}"
                if (!near(kind.bounds.left, c.middle + halfGap)) problems += "the kind ${kind.bounds} does not start at the middle ${c.middle} (plus the half gap)"
                if (kind.bounds.right > c.leftPartRight + 0.5.dp) problems += "the kind ${kind.bounds} runs past the left part's right ${c.leftPartRight}"
                if (kind.bounds.bottom > c.turnRowTop + 0.5.dp) problems += "the kind ${kind.bounds} is not above the turn row (top ${c.turnRowTop})"
                // "Keep the text size the same size": the kind is in the turn words' type, as it was.
                if (c.turn != null && c.turn.text.isNotEmpty() && kind.fontSp != c.turn.fontSp) problems += "the kind is ${kind.fontSp} sp, the turn words ${c.turn.fontSp} sp"
            }
        }
        // Nothing in the left part below the turn row: the parts end with it (with no evening stack, the divider is the left part's height).
        if (!withEvening && c.divider.bottom > c.turnRowBottom + 1.dp) problems += "the left part ends at ${c.divider.bottom}, below the turn row's bottom ${c.turnRowBottom}"
        if (c.figure.bounds.bottom > c.turnRowTop + 0.5.dp) problems += "the distance ${c.figure.bounds} is not above the turn row (top ${c.turnRowTop})"
        return problems.map { "$where $s: $it" }
    }
}
