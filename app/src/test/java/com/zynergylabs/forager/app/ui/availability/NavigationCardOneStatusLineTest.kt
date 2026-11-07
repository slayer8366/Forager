package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
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
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
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

/**
 * Dispatch 2026-11-05-10, Amendment 1, H10 (RECORD -2181), the owner: "The more urgent one (Recommended)", the option reading "Where
 * status words take their own line (large text), only one shows: "No fix 6 min" ahead of "No compass". The card stays one line taller at
 * most, and the route card keeps its buttons. At normal text both show as now." The planner's reading: under H8, one status line at
 * most, by the existing priority. Fix-age warnings come ahead of the compass notice, which comes ahead of other words.
 *
 * The card itself ([NavigationInfo]), as the screen composes it, in the app's own theme: the way home by trail, the start 50 m ahead
 * ("Approaching" with a fresh fix), with the map's navigation view turned north-up ([NavigationFacing.NORTH_UP], "No compass"). A
 * fix 6 min old is lost ("No fix for 6 min", "No fix 6 min" short). At font 2.0 in a 360 or 320 dp card the status words are under
 * the parts (H8, the positive control); at font 1.0 in a 384 dp card they are in the right part, and both lines show, as before H10.
 * The point is the made-up P. Was (H3, the planner's reading of Q6): with other status words at once, both show, the notice on its own
 * line under them, under the parts too.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardOneStatusLineTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val t = 1_791_050_400_000L
    private val here = LatLng(45.3736, -121.6959)
    private val start = Waypoint(
        id = "origin", lat = here.lat + 380.0 / 111_195.0, lng = here.lng, altitude = null, name = "Start", note = "",
        createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN,
    )
    private val fix = LocationFix.Update(lat = here.lat + 330.0 / 111_195.0, lng = here.lng, altitude = 120.0, accuracyMeters = 3.79f, timestampEpochMillis = t, provider = FixProvider.GPS)

    private fun compose(width: Dp, ageMillis: Long) = composeRule.setContent {
        ForagerTheme(darkTheme = false) {
            Box(Modifier.width(width).testTag(NAVIGATION_HUD_TAG)) {
                NavigationInfo(
                    heading = remember { mutableStateOf<TrueHeadingReading>(TrueHeadingReading.Unreliable) },
                    liveFix = fix,
                    target = start,
                    distanceUnit = DistanceUnit.MILES,
                    currentTime = CurrentTimeProvider { t + ageMillis },
                    showDecimalDegrees = false,
                    onExit = {},
                    route = ReturnRoute.Ahead(LatLng(here.lat, here.lng + 0.001), 390.0),
                    facing = NavigationFacing.NORTH_UP,
                )
            }
        }
    }

    private fun node(tag: String): SemanticsNode? =
        composeRule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(NAVIGATION_HUD_TAG)), useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()

    private fun text(n: SemanticsNode?) = n?.config?.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

    /** What the status space shows, the status words and the notice in turn, read only where a line is drawn (not the empty, zero-height node). */
    private fun lines(): List<String> = listOfNotNull(node(NAVIGATION_HUD_STATUS_TAG), node("navigation-hud-compass-notice"))
        .filter { it.boundsInRoot.height > 0f }
        .mapNotNull { text(it)?.takeIf(String::isNotEmpty) }

    private fun assertLines(where: String, width: Dp, ageMillis: Long, below: Boolean, expected: Array<List<String>>) {
        compose(width, ageMillis)
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        val problems = mutableListOf<String>()
        val divider = node(NAVIGATION_HUD_DIVIDER_TAG)
        val first = listOfNotNull(node(NAVIGATION_HUD_STATUS_TAG), node("navigation-hud-compass-notice")).filter { it.boundsInRoot.height > 0f }.minByOrNull { it.boundsInRoot.top }
        // Positive control: H8 puts the status words under the parts here (below), or leaves them in the right part (not below).
        if (divider == null || first == null) {
            problems += "positive control: no divider or no status line drawn (divider ${divider?.boundsInRoot}, line ${first?.boundsInRoot})"
        } else if ((first.boundsInRoot.top >= divider.boundsInRoot.bottom - 1f) != below) {
            problems += "positive control: the status space is ${if (below) "not " else ""}under the parts (line ${first.boundsInRoot}, divider ${divider.boundsInRoot})"
        }
        val shown = lines()
        if (shown !in expected) problems += "the status space shows $shown, not ${expected.joinToString(" or ")}"
        println("ONESTATUSLINE|$where|$shown|divider ${divider?.boundsInRoot}|first ${first?.boundsInRoot}")
        assertTrue(problems.joinToString("\n") { "$where: $it" }, problems.isEmpty())
    }

    @Test @Config(fontScale = 2.0f)
    fun `360 dp, font 2,0, a lost fix and no compass - only the fix's age shows, under the parts`() =
        assertLines("360/2.0 lost", 360.dp, 6 * 60_000L, below = true, expected = arrayOf(listOf("No fix for 6 min"), listOf("No fix 6 min")))

    @Test @Config(fontScale = 2.0f)
    fun `320 dp, font 2,0, a lost fix and no compass - only the fix's age shows, under the parts`() =
        assertLines("320/2.0 lost", 320.dp, 6 * 60_000L, below = true, expected = arrayOf(listOf("No fix for 6 min"), listOf("No fix 6 min")))

    @Test @Config(fontScale = 2.0f)
    fun `360 dp, font 2,0, Approaching and no compass - only No compass shows, under the parts`() =
        assertLines("360/2.0 approaching", 360.dp, 1_000L, below = true, expected = arrayOf(listOf("No compass")))

    /** At normal text both show as now: a control that H10 leaves alone, so it passes before and after H10 by design. */
    @Test
    fun `384 dp, font 1,0, Approaching and no compass - both show in the right part, as before`() =
        assertLines("384/1.0 approaching", 384.dp, 1_000L, below = false, expected = arrayOf(listOf("Approaching", "No compass")))
}
