package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.remember
import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.NavigationFacing
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
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
import kotlin.math.abs

/**
 * Dispatch 2026-10-12-06, RECORD -936: the navigation card at 320, 360, 384 and 412 dp, at fonts 1.0, 1.3 and 2.0, in the
 * states that stretch it. In every one:
 * - no text in the columns, the grid reference's line or the evening lines is clipped or ends in "…" (each text's own layout:
 *   no visual overflow, no ellipsis, and its one line holds the whole string where it is one line);
 * - the turn needle and the compass arrow share a line (centres within 2 dp), the needle left of the arrow;
 * - (until dispatch 2026-10-12-08) the grid reference is drawn, whole, in its column or on its own line. Since RECORD -950 it
 *   is the compass strip's while navigating, and this test no longer looks for it in the card.
 * The status line under the columns keeps its own earlier rule (RECORD -694: its shortest form, then "…" as the last resort)
 * and is not held to "never cut" here. Each state prints a `FIT|` line naming the fit it reached, so the sample can be read
 * against the steps it claims to cover; the count of states measured is asserted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardFitAcrossWidthsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val now = 1_791_050_400_000L
    private val start = Waypoint("origin", 45.53, -122.68, null, "Start", "", now - 3_600_000L, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    private fun fixAt(metresNorth: Double, accuracy: Float = 3.79f) =
        LocationFix.Update(lat = start.lat + metresNorth / 111_195.08, lng = start.lng, altitude = 3_000.0, accuracyMeters = accuracy, timestampEpochMillis = now, provider = FixProvider.GPS)

    private class Case(
        val name: String,
        val liveFix: LocationFix.Update?,
        val route: ReturnRoute?,
        val heading: TrueHeadingReading,
        val facing: NavigationFacing = NavigationFacing.FACING_UP,
        val decimal: Boolean = false,
        val sundown: String? = null,
        val backBy: String? = null,
    )

    // The start is due south of every fix: facing 11° turns 169° right, the longest turn words; 3,000 m is 9843 ft.
    private val cases = listOf(
        Case("route 1280 ft by trail, sharp right 169°", fixAt(380.0), ReturnRoute.Ahead(LatLng(start.lat, start.lng), 390.0), TrueHeadingReading.Available(11f)),
        Case("straight ≈ 1250 ft, sharp right", fixAt(381.0), null, TrueHeadingReading.Available(11f)),
        Case("straight ≈ 800 ft, ahead", fixAt(244.0), null, TrueHeadingReading.Available(182f)),
        // Arrival covers every "within" distance on the live screen (hasArrived: 15 m or twice the accuracy), so this reads
        // "Arrived"; "≤ 16 ft" is exercised by RoundedMapCardFitTest alone.
        Case("at the target: Arrived", fixAt(3.0, accuracy = 5f), null, TrueHeadingReading.Available(182f)),
        Case("decimal coordinates, longest route", fixAt(380.0), ReturnRoute.Ahead(LatLng(start.lat, start.lng), 390.0), TrueHeadingReading.Available(11f), decimal = true),
        Case("compass unavailable, north up", fixAt(380.0), ReturnRoute.Ahead(LatLng(start.lat, start.lng), 390.0), TrueHeadingReading.NoSensor, facing = NavigationFacing.NORTH_UP),
        Case("compass unreliable", fixAt(380.0), ReturnRoute.Ahead(LatLng(start.lat, start.lng), 390.0), TrueHeadingReading.Unreliable),
        Case("route unavailable, Try again", fixAt(1_500.0), ReturnRoute.Unavailable(canRetry = true), TrueHeadingReading.Available(11f)),
        Case(
            "long evening lines", fixAt(380.0), ReturnRoute.Ahead(LatLng(start.lat, start.lng), 390.0), TrueHeadingReading.Available(11f),
            sundown = "Sunset 10:58 PM · start back by 10:48", backBy = "Back by 11:30 PM",
        ),
        Case("short evening line", fixAt(244.0), null, TrueHeadingReading.Available(182f), sundown = "Dark since 7:01 PM"),
        // RECORD -941's own example, with Back by: four pieces stacked in the right column.
        Case(
            "owner's evening example", fixAt(380.0), ReturnRoute.Ahead(LatLng(start.lat, start.lng), 390.0), TrueHeadingReading.Available(11f),
            sundown = "Sunset 12:41 AM · in 2 h 28 min · dark 1:13", backBy = "Back by 1:30 AM",
        ),
    )

    private var case by mutableStateOf<Case?>(null)


    private fun bounds(tag: String): List<DpRect> = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { n ->
        with(composeRule.density) { DpRect(n.boundsInRoot.left.toDp(), n.boundsInRoot.top.toDp(), n.boundsInRoot.right.toDp(), n.boundsInRoot.bottom.toDp()) }
    }

    private class Laid(val tag: String, val layout: TextLayoutResult, val bounds: DpRect)

    private fun layoutsUnder(tag: String): List<Laid> {
        val matcher = hasAnyAncestor(hasTestTag(NAVIGATION_HUD_TAG)).and(hasTestTag(tag))
        return composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { node ->
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val b = with(composeRule.density) { DpRect(node.boundsInRoot.left.toDp(), node.boundsInRoot.top.toDp(), node.boundsInRoot.right.toDp(), node.boundsInRoot.bottom.toDp()) }
            results.firstOrNull()?.let { Laid(tag, it, b) }
        }
    }

    /**
     * `null` when [laid] shows its whole text; else what is wrong with it. Whole: no line ends in "…", the last line's visible
     * end is the text's end, every line's drawn right edge is inside the text's own box, and that box lies inside the card's
     * text area, left of the X ([limitRight]), so a parent cannot be clipping it.
     */
    private fun cut(laid: TextLayoutResult, box: DpRect, limitRight: androidx.compose.ui.unit.Dp): String? {
        val text = laid.layoutInput.text.text
        if (text.isEmpty()) return null
        val lastLine = laid.lineCount - 1
        val lineRightPx = (0..lastLine).maxOf { laid.getLineRight(it) }
        val ellipsised = (0..lastLine).any { laid.isLineEllipsized(it) }
        val visibleEnd = laid.getLineEnd(lastLine, visibleEnd = true)
        val inBox = lineRightPx <= laid.size.width + 1f
        val inCard = box.right <= limitRight + 0.5.dp
        val whole = !ellipsised && visibleEnd >= text.trimEnd().length && inBox && inCard
        return if (whole) null else "'$text' (ellipsis $ellipsised, visible ${text.take(visibleEnd)}, line ${lineRightPx}px in ${laid.size.width}px, box $box, limit $limitRight)"
    }

    // Dispatch 2026-10-12-08 (RECORD -950): the height and the grid reference are the strip's while navigating, so the card's
    // texts are the figure, the kind, the turn, the heading and the evening pieces. Was also the elevation, its "Alt" label and
    // the grid reference (dispatch 2026-10-12-06's three columns).
    private val wholeTags = listOf(
        NAVIGATION_HUD_DISTANCE_TAG, NAVIGATION_HUD_DISTANCE_KIND_TAG, NAVIGATION_HUD_TARGET_TAG,
        NAVIGATION_HUD_HEADING_TAG, NAVIGATION_HUD_EVENING_PIECE_TAG,
    )

    private fun assertFits(where: String) {
        composeRule.setContent {
            val c = case ?: return@setContent
            NavigationHud(
                heading = remember(c) { mutableStateOf(c.heading) },
                liveFix = c.liveFix,
                target = start,
                distanceUnit = DistanceUnit.MILES,
                currentTime = CurrentTimeProvider { now },
                showDecimalDegrees = c.decimal,
                onExit = {},
                route = c.route,
                facing = c.facing,
                sundownLine = c.sundown,
                backByLine = c.backBy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val problems = mutableListOf<String>()
        var measured = 0
        // RECORD -951: the card's width in landscape is the compass strip's, as wide as its readouts need, so landscape is no
        // longer a second run at the window's width; the 320 dp run is the narrow case. Was every state run at both (RECORD -940).
        for (c in cases) {
            composeRule.runOnIdle { case = c }
            composeRule.mainClock.advanceTimeBy(2_000)
            composeRule.waitForIdle()
            val texts = wholeTags.flatMap { layoutsUnder(it) }
            val buttons = bounds(NAVIGATION_HUD_EXIT_TAG).single()
            val cuts = texts.mapNotNull { laid -> cut(laid.layout, laid.bounds, buttons.left)?.let { "${laid.tag} $it" } }
            val needle = bounds(NAVIGATION_HUD_NEEDLE_TAG).single()
            val arrow = bounds(NAVIGATION_HUD_NORTH_ARROW_TAG).single()
            val turn = layoutsUnder(NAVIGATION_HUD_TARGET_TAG).firstOrNull()?.layout?.layoutInput?.text?.text ?: "<none>"
            val figure = layoutsUnder(NAVIGATION_HUD_DISTANCE_TAG).firstOrNull()?.layout?.layoutInput?.text?.text ?: "<none>"
            val card = bounds(NAVIGATION_HUD_TAG).single()
            // The card's height for the report (dispatch 2026-10-12-08: fit and height are measured and reported, not trimmed).
            println("FIT|$where|${c.name}|figure=$figure|turn=$turn|kind=${layoutsUnder(NAVIGATION_HUD_DISTANCE_KIND_TAG).size}|height=${(card.bottom - card.top).value}|cuts=$cuts")
            if (cuts.isNotEmpty()) problems += "${c.name}: $cuts"
            // RECORD -941: every evening piece is whole (no "…", named on its own) and inside the right part: right of the divider
            // and left of the X.
            val pieces = layoutsUnder(NAVIGATION_HUD_EVENING_PIECE_TAG)
            if ((c.sundown != null || c.backBy != null) && pieces.isEmpty()) problems += "${c.name}: no evening pieces drawn"
            val lastDivider = bounds(NAVIGATION_HUD_DIVIDER_TAG).maxOf { it.right }
            pieces.forEach { laid ->
                val text = laid.layout.layoutInput.text.text
                if ((0 until laid.layout.lineCount).any { laid.layout.isLineEllipsized(it) }) problems += "${c.name}: evening piece ends in …: '$text'"
                if (text.contains(" · ")) problems += "${c.name}: evening piece not broken at its joins: '$text'"
                if (laid.bounds.left < lastDivider - 0.5.dp || laid.bounds.right > buttons.left + 0.5.dp) problems += "${c.name}: evening piece '$text' ${laid.bounds} outside the right column (from $lastDivider to ${buttons.left})"
            }
            val needleMid = (needle.top + needle.bottom) / 2
            val arrowMid = (arrow.top + arrow.bottom) / 2
            if (abs((needleMid - arrowMid).value) > 2f || needle.right > arrow.left) problems += "${c.name}: arrows apart, needle $needle, arrow $arrow"
            measured++
        }
        assertEquals("every state measured", cases.size, measured)
        assertTrue("at $where: ${problems.joinToString("; ")}", problems.isEmpty())
    }

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi") fun `320 dp, font 1,0`() = assertFits("320/1.0")
    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi", fontScale = 1.3f) fun `320 dp, font 1,3`() = assertFits("320/1.3")
    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi", fontScale = 2.0f) fun `320 dp, font 2,0`() = assertFits("320/2.0")
    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi") fun `360 dp, font 1,0`() = assertFits("360/1.0")
    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 1.3f) fun `360 dp, font 1,3`() = assertFits("360/1.3")
    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f) fun `360 dp, font 2,0`() = assertFits("360/2.0")
    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi") fun `384 dp, font 1,0`() = assertFits("384/1.0")
    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 1.3f) fun `384 dp, font 1,3`() = assertFits("384/1.3")
    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f) fun `384 dp, font 2,0`() = assertFits("384/2.0")
    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi") fun `412 dp, font 1,0`() = assertFits("412/1.0")
    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f) fun `412 dp, font 1,3`() = assertFits("412/1.3")
    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 2.0f) fun `412 dp, font 2,0`() = assertFits("412/2.0")
}
