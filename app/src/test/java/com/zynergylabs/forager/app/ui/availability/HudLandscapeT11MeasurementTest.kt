package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.BackByShown
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeSundownCountdownUseCase
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
import com.zynergylabs.forager.app.domain.SundownLine
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.SundownCountdown
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-10-09-01 (RECORD -764, plan task T11): the navigation display (HUD) in landscape, **measured, not judged**.
 *
 * This is a measurement harness, not a guard. Its only assertions are positive controls: that each named state is the state
 * it claims to be (the HUD is up, and its large slot or status line reads what that state reads), so that a row of the
 * printed table cannot be a measurement of some other state under this one's name (CLAUDE.md, "a check that enumerates
 * states what each enumerated thing means"). Every geometric fact is printed as a `T11|` line, one per element per state,
 * and read off the JUnit XML's system-out into `docs/navigation/2026-10-09-t11-hud-landscape-check.md`. Nothing here asserts
 * that an overlap is absent: that is for the owner to rule on from the report.
 *
 * Through the real [AvailabilityScreen], with the real [AvailabilityViewModel] (`mapLayersViewModel`) for the fix (its live
 * gate, fed through a [LocationTracker]), the waypoint navigation ([AvailabilityViewModel.onNavigateToWaypoint]) and the
 * icon cluster's stored landscape side. The recording side (recording, Return, the route home, the recording's sundown line,
 * Back by) is what `TrackRecordingViewModel` hands the screen, passed as the screen's own parameters, as every landscape
 * test in this package does; that ViewModel is not in the loop (its poll loop, CLAUDE.md).
 *
 * Metric first (the fixture's stored unit), switched to feet after state 02 (data part B's longest is in feet).
 *
 * One composition per rotation, cluster side and font scale; the walk's states are stepped through it in order, as a walk
 * steps through them, and each is settled (3 s of the test clock) before it is measured. The no-fix state comes first,
 * because a fix once given to the ViewModel cannot be taken back.
 *
 * Native graphics: without it Robolectric measures text at a few dp wide. Robolectric reports no window insets (CLAUDE.md,
 * "Known pitfalls"): no status bar and no cut-out here, so every top edge is ~30 dp higher than on the S22 and the cut-out
 * band is absent. Those differences are the S22 half of the check.
 */
abstract class HudLandscapeT11Measurement(private val windowLabel: String) {

    private val composeRule: ComposeContentTestRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    // ── Fixture ──

    private val minute = 60_000L

    /** 2026-10-03T18:00:00Z, mid-morning in Oregon: no sundown window open, so no line unless a state gives one. */
    private val morning = 1_791_050_400_000L

    /** The walker: 45.52 N, 122.68 W, 3,000 m up (a four-digit altitude in feet), 12.5 m accuracy. */
    private val here = LatLng(45.52, -122.68)
    private val metresPerDegreeLat = 111_195.0

    /** The trip's start, 380 m due north: outside the 100 m approach zone ("≈ 1250 ft straight", data part B's longest). */
    private val origin = Waypoint(
        id = "origin", lat = here.lat + 380.0 / metresPerDegreeLat, lng = here.lng, altitude = null, name = "Start", note = "",
        createdAtEpochMillis = morning, trackId = "t1", designation = WaypointDesignation.ORIGIN,
    )

    /** A chosen waypoint, ~1.1 km due north of [here]. */
    private val creek = Waypoint(
        id = "wp-creek", lat = here.lat + 0.01, lng = here.lng, altitude = null, name = "Chanterelle creek", note = "",
        createdAtEpochMillis = morning,
    )

    /** The route home's lookahead, due east, so a phone facing 281° turns 169° right: "sharp right 169°". */
    private val east = LatLng(here.lat, here.lng + 0.001)

    private val utc = TimeZone.getTimeZone("UTC")
    private val clock = object : SundownClock {
        override fun full(epochMillis: Long): String = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
        override fun short(epochMillis: Long): String = SimpleDateFormat("h:mm", Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
    }

    /** The recording's line at its longest real form, with two-digit hours: "Sunset 10:58 PM · start back by 10:48". */
    private val sunset2258 = morning + (4 * 60 + 58) * minute
    private val recordingLine = SundownLine.BeforeSunset(
        nowEpochMillis = sunset2258 - 48 * minute,
        sunsetAtEpochMillis = sunset2258,
        civilDuskAtEpochMillis = sunset2258 + 28 * minute,
        startBackAtEpochMillis = sunset2258 - 10 * minute,
    )

    private val fixes = MutableSharedFlow<LocationFix>(replay = 1)
    private val tracker = object : LocationTracker {
        override val fixes: Flow<LocationFix> = this@HudLandscapeT11Measurement.fixes
    }

    private var recording by mutableStateOf(false)
    private var returning by mutableStateOf(false)
    private var route by mutableStateOf<ReturnRoute>(ReturnRoute.Pending)
    private var line by mutableStateOf<SundownLine?>(null)
    private var backBy by mutableStateOf<BackByShown?>(null)
    private var now by mutableLongStateOf(morning)

    private lateinit var viewModel: AvailabilityViewModel
    private var rotationSeen: Int? = null

    private fun setScreen(rotation: Int, clusterOnRailSide: Boolean) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        viewModel = mapLayersViewModel(
            locationTracker = tracker,
            clusterPlacements = InMemoryClusterPlacement(
                if (clusterOnRailSide) MapIconClusterPlacement.DEFAULT.copy(landscapeOnPortSide = true) else null,
            ),
        )
        viewModel.onEnteredForeground()
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            val state by viewModel.uiState.collectAsState()
            // A new provider each time the clock is moved, so the HUD's age ticker restarts from it at once.
            val nowAtCompose = now
            val currentTime = remember(nowAtCompose) { CurrentTimeProvider { nowAtCompose } }
            CompositionLocalProvider(LocalSundownClock provides clock) {
                AvailabilityScreen(
                    uiState = state,
                    searchInputs = AvailabilityScreenSearchInputs(
                        onUseCurrentLocation = {},
                        onManualLatChanged = {},
                        onManualLngChanged = {},
                        onSearchManualCoordinates = {},
                        onRadiusChanged = {},
                        onMonthSelected = {},
                        onMapTabSelected = {},
                        onSeasonalTabSelected = {},
                        onTaxonSearchQueryChanged = {},
                        onTaxonSearchResultSelected = {},
                        onDismissTaxonSuggestions = {},
                        onReopenTaxonSuggestions = {},
                        onPlaceTripPin = { _, _, _ -> },
                        onDeletePlannedTrip = {},
                        onRecentSearchSelected = {},
                    ),
                    offlineMaps = AvailabilityScreenOfflineMaps(
                        onOfflineMapLatChanged = {},
                        onOfflineMapLngChanged = {},
                        onOfflineMapRadiusChanged = {},
                        onOfflineMapNameChanged = {},
                        onOfflineMapsOpened = {},
                        onDownloadOfflineMaps = {},
                        onDeleteOfflineRegion = {},
                    ),
                    settings = AvailabilityScreenSettings(
                        onNightModeMapsChanged = {},
                        onThemeModeChanged = {},
                    ),
                    mapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) },
                    records = AvailabilityScreenRecords(
                        waypoints = listOf(creek),
                        waypointsLoaded = true,
                    ),
                    navigation = AvailabilityScreenNavigation(
                        isReturning = returning,
                        onToggleReturning = { returning = !returning },
                        navigationTarget = if (recording) origin else null,
                        returnRoute = route,
                        onNavigateToWaypoint = viewModel::onNavigateToWaypoint,
                        onStopWaypointNavigation = viewModel::onStopWaypointNavigation,
                    ),
                    recording = AvailabilityScreenRecording(
                        isRecording = recording,
                        recordingSundownLine = line,
                        recordingBackBy = backBy,
                    ),
                    compassProvider = object : CompassProvider {
                        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(281f, HeadingUncertainty.Estimated(2f), 0L))
                    },
                    computeTrueHeading = ComputeTrueHeadingUseCase(object : DeclinationProvider {
                        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
                    }),
                    currentTime = currentTime,
                )
            }
        }
        settle()
        assertEquals("positive control: the screen sees the rotation this case is about", rotation, rotationSeen)
    }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(3_000L)
        composeRule.waitForIdle()
    }

    private fun emitFix(lat: Double, accuracy: Float = 12.5f, at: Long = now) {
        fixes.tryEmit(LocationFix.Update(lat = lat, lng = here.lng, altitude = 3_000.0, accuracyMeters = accuracy, timestampEpochMillis = at, provider = FixProvider.GPS))
    }

    // ── Reading the screen ──

    private fun nodes(tag: String): List<SemanticsNode> =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()

    /** [tag]'s bounds in dp, the union where it is on several nodes; `null` when not composed. */
    private fun boundsOf(tag: String): DpRect? {
        val found = nodes(tag).takeIf { it.isNotEmpty() } ?: return null
        val d = composeRule.density.density
        return DpRect(
            left = (found.minOf { it.boundsInRoot.left } / d).dp,
            top = (found.minOf { it.boundsInRoot.top } / d).dp,
            right = (found.maxOf { it.boundsInRoot.right } / d).dp,
            bottom = (found.maxOf { it.boundsInRoot.bottom } / d).dp,
        )
    }

    private fun window(): DpRect {
        val root = composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().first().boundsInRoot
        val d = composeRule.density.density
        return DpRect((root.left / d).dp, (root.top / d).dp, (root.right / d).dp, (root.bottom / d).dp)
    }

    private fun textOf(tag: String): String? = nodes(tag).firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult? {
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.singleOrNull()
    }

    private fun Dp.f() = "%.1f".format(value)
    private fun DpRect?.d() = if (this == null) "absent" else "[${left.f()},${top.f()} ${right.f()},${bottom.f()}] ${(right - left).f()}x${(bottom - top).f()}"

    /** Overlap with area as "w x h"; touching edges are not overlap. */
    private fun overlap(a: DpRect?, b: DpRect?): String {
        if (a == null || b == null) return "n/a"
        val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return if (w > 0.dp && h > 0.dp) "OVERLAP ${w.f()}x${h.f()}" else "clear (gap x ${(-w).f()}, y ${(-h).f()})"
    }

    private var currentCase = ""

    private fun out(element: String, detail: String) = println("T11|$windowLabel|$currentCase|$element|$detail")

    /** Every text laid out inside [containerTag]: its text, lines, ellipsis, characters drawn, and its box. */
    private fun texts(containerTag: String) {
        val inside = composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(containerTag)) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        val d = composeRule.density.density
        for (node in inside) {
            val layout = layoutOf(node) ?: continue
            val text = layout.layoutInput.text.text
            if (text.isEmpty()) continue
            val lines = layout.lineCount
            val visible = layout.getLineEnd(lines - 1, visibleEnd = true)
            val cut = layout.isLineEllipsized(lines - 1) || visible < text.length || lines > 1
            val b = node.boundsInRoot
            val box = DpRect((b.left / d).dp, (b.top / d).dp, (b.right / d).dp, (b.bottom / d).dp)
            out("text:$containerTag", "<$text> lines=$lines ellipsised=${layout.isLineEllipsized(lines - 1)} visible=$visible/${text.length} ${if (cut) "CUT" else "whole"} box=${box.d()}")
        }
    }

    /** One state: every element's bounds, the HUD against each, and the HUD's and the rail's texts. */
    private fun measure(state: String) {
        currentCase = "$caseKey|$state"
        val w = window()
        val third = DpRect(
            left = w.left + (w.right - w.left) / 3, top = w.top + (w.bottom - w.top) / 3,
            right = w.left + (w.right - w.left) * 2 / 3, bottom = w.top + (w.bottom - w.top) * 2 / 3,
        )
        val hud = boundsOf(NAVIGATION_HUD_TAG)
        val elements = linkedMapOf(
            "searchBar" to boundsOf(SEARCH_ENTRY_BAR_TAG),
            "strip" to boundsOf(STRIP_TAG),
            "join" to boundsOf(LANDSCAPE_BAR_STRIP_LINE_TAG),
            "centralThird" to third,
            "cluster" to boundsOf(MAP_ICON_CLUSTER_TAG),
            // T11 fixes: the bar on its own, for its top against the display's bottom.
            "bar" to boundsOf(com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_TAG),
            "recordButton" to boundsOf("control-pill-record"),
            "returnButton" to boundsOf("control-pill-return-to-vehicle"),
            "rail" to boundsOf(COMPACT_NAVIGATION_RAIL_TAG),
            "returnToRoute" to boundsOf(RETURN_TO_ROUTE_TAG),
        )
        out("window", w.d())
        out("hud", hud.d())
        // Inferred, not tagged: the needle (the target compass) is the icon above the turn text, in the turn text's column.
        out("turnText(needle column)", boundsOf(NAVIGATION_HUD_TARGET_TAG).d())
        for ((name, rect) in elements) {
            out(name, "${rect.d()} vsHud=${overlap(hud, rect)}")
        }
        // Distance from the window's bottom edge, for the Record/Return pill pushed to the edge at font 2.0 (-694's log).
        elements["recordButton"]?.let { out("recordButton.bottomGap", (w.bottom - it.bottom).f()) }
        elements["returnButton"]?.let { out("returnButton.bottomGap", (w.bottom - it.bottom).f()) }
        if (hud != null) {
            out("hud.height", (hud.bottom - hud.top).f())
            out("hud.clearanceAboveCentralThird", (third.top - hud.bottom).f())
            texts(NAVIGATION_HUD_TAG)
        }
        if (elements["strip"] != null) texts(STRIP_TAG)
        texts(COMPACT_NAVIGATION_RAIL_TAG)
    }

    private fun assertHudReads(state: String, tag: String, expected: (String?) -> Boolean) {
        assertTrue("positive control, $state: the HUD is up", nodes(NAVIGATION_HUD_TAG).isNotEmpty())
        val text = textOf(tag)
        assertTrue("positive control, $state: $tag reads <$text>", expected(text))
    }

    private var caseKey = ""

    /** The walk, in order. */
    private fun walk(rotation: Int, clusterOnRailSide: Boolean) {
        setScreen(rotation, clusterOnRailSide)
        caseKey = "rot=${if (rotation == Surface.ROTATION_90) 90 else 270}|font=${composeRule.density.fontScale}|cluster=${if (clusterOnRailSide) "railSide" else "punchHoleSide"}"

        // 0. Not navigating, no fix: the strip, the bar and the join, for the heights the HUD is compared with.
        measure("00-strip-nofix")

        // 1. Recording and Return tapped, no fix yet (route pending).
        composeRule.runOnIdle { recording = true; returning = true; route = ReturnRoute.Pending }
        settle()
        // T11 fixes (RECORD -766): the no-fix message may now take its short form, "No location".
        // Dispatch 2026-10-12-08 (RECORD -951, option (a)): the no-fix message shows once, on the strip, which stays while
        // navigating; the card's status line leaves it out. Was the card's NAVIGATION_HUD_STATUS_TAG reading it.
        assertHudReads("01", COMPASS_STRIP_NO_FIX_TAG) { it == NO_FIX_MESSAGE }
        measure("01-return-nofix")

        // 2. The route home with data part B's longest lines: "sharp right 169°", "1280 ft" "by trail", "≈ 1250 ft straight", 9843 ft.
        emitFix(here.lat)
        composeRule.runOnIdle { route = ReturnRoute.Ahead(east, 390.0) }
        settle()
        // T11 fixes: the turn words may give way to the bearing alone, or go; the needle's state is read off the route figure then.
        assertHudReads("02", NAVIGATION_HUD_TARGET_TAG) { it == "sharp right 169°" || it == "169°" || (it == "" && textOf(NAVIGATION_HUD_DISTANCE_TAG) == "390 m") }
        measure("02-return-longest")

        // 2f. The same in feet, data part B's own longest ("1280 ft", "≈ 1250 ft straight"); feet from here on. The fixture's
        // stored unit is metric, so the walker's switch in Settings is made through the ViewModel's own entry point.
        composeRule.runOnIdle { viewModel.onDistanceUnitSelected(com.zynergylabs.forager.app.domain.model.DistanceUnit.MILES) }
        settle()
        assertHudReads("02f", NAVIGATION_HUD_DISTANCE_TAG) { it == "1280 ft" }
        measure("02f-return-longest-feet")

        // 3. Route unavailable, with "Try again" (the one state that adds a row).
        composeRule.runOnIdle { route = ReturnRoute.Unavailable(canRetry = true) }
        settle()
        assertHudReads("03", NAVIGATION_HUD_DISTANCE_TAG) { it == ROUTE_UNAVAILABLE_TEXT || it == ROUTE_UNAVAILABLE_SHORT_TEXT }
        measure("03-return-unavailable-retry")

        // 4. Stale fix, 45 s old.
        composeRule.runOnIdle { route = ReturnRoute.Ahead(east, 390.0); now = morning + 45_000L }
        settle()
        assertHudReads("04", NAVIGATION_HUD_STATUS_TAG) { it?.contains("45 s") == true }
        measure("04-return-stale-45s")

        // 5. The sundown line (the recording's, longest form), then with Back by in its last hour.
        composeRule.runOnIdle { now = morning; emitFix(here.lat, at = morning); line = recordingLine }
        settle()
        assertHudReads("05", NAVIGATION_HUD_SUNDOWN_LINE_TAG) { it == "Sunset 10:58 PM · start back by 10:48" }
        measure("05-return-sundown")
        composeRule.runOnIdle { backBy = BackByShown(trackId = "t1", backByAtEpochMillis = now + 40 * minute, nowEpochMillis = now) }
        settle()
        assertHudReads("06", NAVIGATION_HUD_BACK_BY_LINE_TAG) { it != null && it.startsWith("Back by") }
        measure("06-return-sundown-backby-longest")

        // 06r. T11 fixes, RECORD -770: the route withheld with "Try again" and both evening lines, the state the owner ruled on
        // ("Drop the evening line then"): with the L below the display, the line leaves where it would push the bar past its
        // Fullscreen row. Not a guard here; HudLandscapeT11FixesTest holds it.
        composeRule.runOnIdle { route = ReturnRoute.Unavailable(canRetry = true) }
        settle()
        assertHudReads("06r", NAVIGATION_HUD_DISTANCE_TAG) { it == ROUTE_UNAVAILABLE_TEXT || it == ROUTE_UNAVAILABLE_SHORT_TEXT }
        measure("06r-return-unavailable-retry-evening")
        composeRule.runOnIdle { route = ReturnRoute.Ahead(east, 390.0) }
        settle()

        // 7. Return stopped, still recording, both lines: the strip carries them (its height against portrait's is -759's).
        composeRule.runOnIdle { returning = false }
        settle()
        assertTrue("positive control, 07: the HUD is gone", nodes(NAVIGATION_HUD_TAG).isEmpty())
        measure("07-strip-recording-sundown-backby")

        // 8 to 11. Navigating to a chosen waypoint, no recording: far, approaching, approaching and stale, arrived.
        composeRule.runOnIdle { recording = false; line = null; backBy = null }
        settle()
        composeRule.runOnIdle { viewModel.onNavigateToWaypoint(creek.id) }
        settle()
        assertHudReads("08", NAVIGATION_HUD_DISTANCE_TAG) { it != null && it.any(Char::isDigit) }
        measure("08-waypoint-far")

        emitFix(creek.lat - 50.0 / metresPerDegreeLat)
        settle()
        // T11 fixes: "Approaching" goes whole where it does not fit; the approach is then read off the figure (≈ 150 ft).
        assertHudReads("09", NAVIGATION_HUD_STATUS_TAG) { it?.contains("Approaching") == true || (it == "" && textOf(NAVIGATION_HUD_DISTANCE_TAG)?.contains("150") == true) }
        measure("09-waypoint-approaching")

        composeRule.runOnIdle { now = morning + 45_000L }
        settle()
        assertHudReads("10", NAVIGATION_HUD_STATUS_TAG) { it?.contains("45 s") == true }
        measure("10-waypoint-approaching-stale-45s")

        composeRule.runOnIdle { now = morning; emitFix(creek.lat - 5.0 / metresPerDegreeLat, accuracy = 5f, at = morning) }
        settle()
        assertHudReads("11", NAVIGATION_HUD_DISTANCE_TAG) { it == ARRIVED_TEXT }
        measure("11-waypoint-arrived")

        // 12. Far again, at the evening hour where the screen's own sundown line shows ("Sunset … · in 1 h 9 min · dark …").
        val known = ComputeSundownCountdownUseCase()(morning, LatLng(here.lat, here.lng), morning, 0L) as SundownCountdown.Known
        composeRule.runOnIdle { now = known.sunsetAtEpochMillis - 70 * minute; emitFix(here.lat, at = known.sunsetAtEpochMillis - 70 * minute) }
        settle()
        assertHudReads("12", NAVIGATION_HUD_SUNDOWN_LINE_TAG) { it != null && it.startsWith("Sunset") }
        measure("12-waypoint-far-screen-sundown")
    }

    @Test fun `rotation 90, cluster punch-hole side, font 1,0`() = walk(Surface.ROTATION_90, clusterOnRailSide = false)
    @Test fun `rotation 270, cluster punch-hole side, font 1,0`() = walk(Surface.ROTATION_270, clusterOnRailSide = false)
    @Test fun `rotation 90, cluster rail side, font 1,0`() = walk(Surface.ROTATION_90, clusterOnRailSide = true)
    @Test fun `rotation 270, cluster rail side, font 1,0`() = walk(Surface.ROTATION_270, clusterOnRailSide = true)

    @Test @Config(fontScale = 1.3f) fun `rotation 90, cluster punch-hole side, font 1,3`() = walk(Surface.ROTATION_90, clusterOnRailSide = false)
    @Test @Config(fontScale = 1.3f) fun `rotation 270, cluster punch-hole side, font 1,3`() = walk(Surface.ROTATION_270, clusterOnRailSide = false)
    @Test @Config(fontScale = 1.3f) fun `rotation 90, cluster rail side, font 1,3`() = walk(Surface.ROTATION_90, clusterOnRailSide = true)
    @Test @Config(fontScale = 1.3f) fun `rotation 270, cluster rail side, font 1,3`() = walk(Surface.ROTATION_270, clusterOnRailSide = true)

    @Test @Config(fontScale = 2.0f) fun `rotation 90, cluster punch-hole side, font 2,0`() = walk(Surface.ROTATION_90, clusterOnRailSide = false)
    @Test @Config(fontScale = 2.0f) fun `rotation 270, cluster punch-hole side, font 2,0`() = walk(Surface.ROTATION_270, clusterOnRailSide = false)
    @Test @Config(fontScale = 2.0f) fun `rotation 90, cluster rail side, font 2,0`() = walk(Surface.ROTATION_90, clusterOnRailSide = true)
    @Test @Config(fontScale = 2.0f) fun `rotation 270, cluster rail side, font 2,0`() = walk(Surface.ROTATION_270, clusterOnRailSide = true)

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
    }
}

/** The S22-class window the B2 tests use. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HudLandscapeT11At823Test : HudLandscapeT11Measurement("823x384")

/** The 780 x 360 window data part B's landscape test uses. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HudLandscapeT11At780Test : HudLandscapeT11Measurement("780x360")
