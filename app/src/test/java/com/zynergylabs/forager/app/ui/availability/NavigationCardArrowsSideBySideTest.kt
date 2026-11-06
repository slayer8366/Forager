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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
 * Dispatch 2026-10-12-06. The owner, RECORD -932: "Have the altitude and the north compass switch sides so both arrows can be
 * side by side"; and on 2026-10-12, relayed by the planner: "The big part is to arrange it so both arrows are side by side."
 *
 * In every state below, at 360 and 412 dp and fonts 1.0 and 1.3, the turn needle (the end of the left column's lower row) and
 * the compass arrow (the start of the middle column's lower row) are measured: their vertical centres within 2 dp, the needle
 * left of the arrow, and the space between them exactly one divider with its gap either side (6 + 1 + 6 dp, RECORD -936), a divider found
 * in that space. The states run the fit through its steps (the whole turn with "by trail", the kind gone, the turn shortened
 * to degrees, a compass status as the crossed-out icon), so a step that moved either arrow would fail here. Each state's
 * turn text is printed, so the sample can be read against the steps it claims to cover.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardArrowsSideBySideTest {

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

    private fun fixAt(metresNorth: Double, at: Long = now, accuracy: Float = 3.79f) =
        LocationFix.Update(lat = start.lat + metresNorth / 111_195.08, lng = start.lng, altitude = 2_999.0, accuracyMeters = accuracy, timestampEpochMillis = at, provider = FixProvider.GPS)

    private class Case(
        val name: String,
        val liveFix: LocationFix.Update?,
        val route: ReturnRoute?,
        val heading: TrueHeadingReading,
        val facing: NavigationFacing = NavigationFacing.FACING_UP,
        val decimal: Boolean = false,
    )

    // The start is due south of every fix: a phone facing 11° turns 169° right ("sharp right 169°"), one facing 182° turns 2°
    // left ("ahead 2°").
    private val cases = listOf(
        Case("longest: sharp right, by trail, MGRS", fixAt(380.0), ReturnRoute.Ahead(LatLng(start.lat, start.lng), 390.0), TrueHeadingReading.Available(11f)),
        Case("longest with decimal coordinates", fixAt(380.0), ReturnRoute.Ahead(LatLng(start.lat, start.lng), 390.0), TrueHeadingReading.Available(11f), decimal = true),
        Case("short turn, straight", fixAt(1_500.0), null, TrueHeadingReading.Available(182f)),
        Case("approaching, no needle", fixAt(3.0), null, TrueHeadingReading.Available(182f)),
        Case("no fix", null, ReturnRoute.Pending, TrueHeadingReading.NeedsFix),
        Case("compass unreliable", fixAt(380.0), ReturnRoute.Ahead(LatLng(start.lat, start.lng), 390.0), TrueHeadingReading.Unreliable),
        Case("no compass, north up", fixAt(380.0), ReturnRoute.Ahead(LatLng(start.lat, start.lng), 390.0), TrueHeadingReading.NoSensor, facing = NavigationFacing.NORTH_UP),
        Case("route unavailable, Try again", fixAt(1_500.0), ReturnRoute.Unavailable(canRetry = true), TrueHeadingReading.Available(11f)),
    )


    private var case by mutableStateOf<Case?>(null)

    private fun bounds(tag: String): List<DpRect> = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { n ->
        with(composeRule.density) { DpRect(n.boundsInRoot.left.toDp(), n.boundsInRoot.top.toDp(), n.boundsInRoot.right.toDp(), n.boundsInRoot.bottom.toDp()) }
    }

    private fun textOrNone(tag: String): String = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .firstOrNull()?.config?.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: "<none>"

    private fun assertArrowsSideBySide(width: String) {
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
                modifier = Modifier.fillMaxWidth(),
            )
        }
        var sampled = 0
        for (c in cases) {
            composeRule.runOnIdle { case = c }
            composeRule.mainClock.advanceTimeBy(2_000)
            composeRule.waitForIdle()
            val needle = bounds(NAVIGATION_HUD_NEEDLE_TAG).single()
            val arrow = bounds(NAVIGATION_HUD_NORTH_ARROW_TAG).single()
            val dividers = bounds(NAVIGATION_HUD_DIVIDER_TAG)
            println(
                "ARROWS|$width|${c.name}|turn=${textOrNone(NAVIGATION_HUD_TARGET_TAG)}|kind=${textOrNone(NAVIGATION_HUD_DISTANCE_KIND_TAG)}" +
                    "|needle=$needle|arrow=$arrow|dividers=$dividers",
            )
            val needleMid = (needle.top + needle.bottom) / 2
            val arrowMid = (arrow.top + arrow.bottom) / 2
            assertTrue("${c.name} at $width: the two arrows share a line (centres $needleMid and $arrowMid)", abs((needleMid - arrowMid).value) <= 2f)
            assertTrue("${c.name} at $width: the needle $needle is left of the compass arrow $arrow", needle.right <= arrow.left)
            // Dispatch 2026-10-12-08 (RECORD -950, the owner: "The arrows being side by side is a compass visual"; the drawing's
            // "ahead 1° ▲ ▲ 134° SE"): the two arrows are neighbours on one line, nothing between them but the line's own gap.
            // Was (dispatch 2026-10-12-06's three columns): one divider between them, `assertEquals(1, between.size)`, and 13 dp
            // apart, the divider and its gaps.
            val between = dividers.filter { it.left >= needle.right - 0.5.dp && it.right <= arrow.left + 0.5.dp }
            assertEquals("${c.name} at $width: no divider between the arrows, $dividers", 0, between.size)
            assertEquals("${c.name} at $width: the arrows are neighbours, the line's 4 dp gap apart", 4f, (arrow.left - needle.right).value, 1f)
            sampled++
        }
        assertEquals("every state was measured", cases.size, sampled)
    }

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `at 360 dp, font 1,0, the needle and the compass arrow sit side by side in every state`() = assertArrowsSideBySide("360/1.0")

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 1.3f)
    fun `at 360 dp, font 1,3, the needle and the compass arrow sit side by side in every state`() = assertArrowsSideBySide("360/1.3")

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun `at 412 dp, font 1,0, the needle and the compass arrow sit side by side in every state`() = assertArrowsSideBySide("412/1.0")

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f)
    fun `at 412 dp, font 1,3, the needle and the compass arrow sit side by side in every state`() = assertArrowsSideBySide("412/1.3")
}
