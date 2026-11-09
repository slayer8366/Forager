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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
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

/**
 * RECORD -713 (the owner: "Move it into the status line (Recommended)"). When the figure and what it measures ("1280 ft",
 * "by trail") cannot share the first row, the kind starts the status line, capitalised ("By trail · …"), whole, below the
 * figure, and the first row stays the X's 48 dp: the status line ends inside it.
 *
 * Reworked with RECORD -714, which moved the three-dot button to the second row at 360 dp: there the kind fits beside the
 * figure again, so the case is set up at 320 dp, where it does not (measured: the figure and kind need about 97 dp, the
 * distance column has about 95). The earlier claim, the display as tall with the button as without, no longer holds
 * anywhere the button sits in the second row, which is one 36 dp line taller than the 24 dp coordinates line it shares; the
 * row-height claim is made on the first row instead. Native graphics, as the width test beside this one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w320dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationHudKindInStatusTest {

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

    private fun fixAt(metresNorth: Double, at: Long = now) =
        LocationFix.Update(lat = start.lat + metresNorth / 111_195.08, lng = start.lng, altitude = 120.0, accuracyMeters = 3.79f, timestampEpochMillis = at, provider = FixProvider.GPS)

    private class Case(
        val name: String,
        val liveFix: LocationFix.Update?,
        val target: Waypoint?,
        val route: ReturnRoute?,
        val approximateFix: LocationFix.Update? = null,
        val lastKnownFix: LocationFix.Update? = null,
        /** The phone's heading; the start is due south of every fix here, so 11 degrees puts it 169 degrees to the right. */
        val headingDegrees: Float = 10f,
    )

    private val cases = listOf(
        Case("Unable to calculate route", fixAt(1_500.0), start, ReturnRoute.Unavailable(canRetry = true)),
        Case("Straight line, a long distance", fixAt(15_000.0), start, ReturnRoute.Ahead(LatLng(start.lat, start.lng), 18_000.0)),
        Case("No origin waypoint for this track", fixAt(1_500.0), null, null),
        Case("Location services unavailable", null, start, ReturnRoute.Pending),
        Case("Approaching · last fix 4 min ago", fixAt(50.0, at = now - 4 * 60_000L - 30_000L), start, ReturnRoute.Ahead(LatLng(start.lat, start.lng), 60.0)),
        Case("Last seen 23 h ago, finding GPS…", null, start, ReturnRoute.Pending, lastKnownFix = fixAt(1_500.0, at = now - 23 * 3_600_000L)),
        Case("Approximate, finding GPS…", null, start, ReturnRoute.Pending, approximateFix = fixAt(1_500.0).copy(accuracyMeters = 60f, provider = FixProvider.NETWORK)),
        // Data part B (dispatch 2026-09-28-677, merged into back-by with RECORD -687): its longest first row, the turn in
        // words ("sharp right 169°") beside a four-digit figure in feet with "by trail", which that part fitted at 360 dp
        // before this button was in the row.
        Case("Sharp right, by trail", fixAt(380.0), start, ReturnRoute.Ahead(LatLng(start.lat, start.lng), 390.0), headingDegrees = 11f),
    )

    private val quickSettings = MapQuickSettings(
        isRecording = true,
        backBy = null,
        onBackByChoice = {},
        onClearBackBy = {},
        sundown = SundownSettings(),
        offTrackReminder = OffTrackReminderSettings(),
    )

    private var case by mutableStateOf<Case?>(null)
    private var withButton by mutableStateOf(true)

    private fun layoutOf(tag: String): TextLayoutResult? {
        val nodes = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes()
        if (nodes.isEmpty()) return null
        val results = mutableListOf<TextLayoutResult>()
        nodes.single().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /** `null` when [tag]'s text is laid out whole on its one line; else what was cut. */
    private fun clipped(tag: String): String? {
        val layout = layoutOf(tag) ?: return null
        val words = layout.layoutInput.text.text
        if (words.isEmpty()) return null
        val visibleEnd = layout.getLineEnd(0, visibleEnd = true)
        val whole = visibleEnd == words.length && !layout.isLineEllipsized(0) && layout.lineCount == 1
        return if (whole) null else "'$words' shows ${words.take(visibleEnd)}"
    }

    private fun measure(button: Boolean): Map<String, List<String>> {
        val found = mutableMapOf<String, List<String>>()
        for (c in cases) {
            composeRule.runOnIdle {
                case = c
                withButton = button
            }
            composeRule.waitForIdle()
            // The turn and the distance's kind since data part B (RECORD -687): both share the first row with the button.
            found[c.name] = listOfNotNull(
                clipped(NAVIGATION_HUD_DISTANCE_TAG),
                clipped(NAVIGATION_HUD_DISTANCE_KIND_TAG),
                clipped(NAVIGATION_HUD_TARGET_TAG),
                clipped(NAVIGATION_HUD_STATUS_TAG),
            )
        }
        return found
    }

    private fun bounds(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().single().let { n ->
        with(composeRule.density) { androidx.compose.ui.unit.DpRect(n.boundsInRoot.left.toDp(), n.boundsInRoot.top.toDp(), n.boundsInRoot.right.toDp(), n.boundsInRoot.bottom.toDp()) }
    }

    private fun textOf(tag: String): String =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().single().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }

    /**
     * Dispatch 2026-10-12-08 (RECORD -945, the owner: "Full dividers + by trail under figure (Recommended)"): the kind word is a
     * small line of its own under the turn line, and no longer leads the status line. Was (dispatch 2026-10-12-06): "at 320 dp the
     * kind leads the status line, whole, below the figure", asserting `assertEquals("By trail", ...)` and the kind leading the line
     * under the columns.
     */
    @Test
    fun `at 320 dp the kind is a small line of its own under the turn line, whole`() {
        val longest = cases.single { it.name == "Sharp right, by trail" }
        composeRule.setContent {
            val c = case ?: return@setContent
            NavigationHud(
                heading = remember(c) { mutableStateOf<TrueHeadingReading>(TrueHeadingReading.Available(c.headingDegrees)) },
                liveFix = c.liveFix,
                approximateFix = c.approximateFix,
                lastKnownFix = c.lastKnownFix,
                target = c.target,
                distanceUnit = DistanceUnit.MILES,
                currentTime = CurrentTimeProvider { now },
                showDecimalDegrees = false,
                onExit = {},
                route = c.route,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        composeRule.runOnIdle { case = longest; withButton = true }
        composeRule.waitForIdle()
        assertEquals("by trail", textOf(NAVIGATION_HUD_DISTANCE_KIND_TAG))
        assertEquals("the kind is whole", null, clipped(NAVIGATION_HUD_DISTANCE_KIND_TAG))
        assertEquals("the figure is whole", null, clipped(NAVIGATION_HUD_DISTANCE_TAG))
        val kind = bounds(NAVIGATION_HUD_DISTANCE_KIND_TAG)
        val figure = bounds(NAVIGATION_HUD_DISTANCE_TAG)
        val needle = bounds(NAVIGATION_HUD_NEEDLE_TAG)
        val exit = bounds(NAVIGATION_HUD_EXIT_TAG)
        val hud = bounds(NAVIGATION_HUD_TAG)
        assertTrue("the kind $kind is below the figure $figure", kind.top >= figure.bottom - 0.5.dp)
        assertTrue("the kind $kind is below the turn line (needle $needle)", kind.top >= needle.bottom - 0.5.dp)
        // RECORD -942: no straight-line note on the route, and the kind no longer leads the status line, so it is empty.
        assertEquals("the status line is empty: the kind is not in it", "", textOf(NAVIGATION_HUD_STATUS_TAG))
        assertTrue("the kind's line is inside the card, left of the X: $kind in $hud, X $exit", kind.bottom <= hud.bottom && kind.right <= exit.left + 0.5.dp)
    }
}
