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
 * The navigation display's text columns with the quick-settings button in its top row (dispatch
 * 2026-09-28-645, Amendments 2 and 3; the planner: the button left of the exit, and "check the narrowed
 * column at 360 dp with 'Unable to calculate route' and the longest status strings"). The button is the
 * three-dot one at [QUICK_SETTINGS_TAP_TARGET] since Amendment 3 (RECORD -648). On a 360 dp-wide window,
 * each state's large figure and status line are laid out whole, with the button and, for the record,
 * without it, so a clip the button did not cause is told apart from one it did.
 *
 * Native graphics, as `AvailabilityScreenSundownLineTest` reads its line: Robolectric's legacy text
 * metrics measure a character as one pixel wide, so nothing could be cut off there. The real phone's
 * font and its font-scale setting are device-only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationHudQuickSettingsWidthTest {

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
        val oneLineEllipsis = layout.lineCount == 1 && layout.isLineEllipsized(0)
        return if (whole) null else "'$words' shows ${words.take(visibleEnd)}" + if (oneLineEllipsis) ONE_LINE_ELLIPSIS else ""
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

    /**
     * Dispatch 2026-10-12-08 (RECORD -947, the owner: "Yes, only on the strip (Recommended)"; RECORD -950, -951): the three-dot
     * button is on the compass strip only, so the navigation info has no button to compare with and without. What the test held
     * stands without the comparison: at 360 dp each state's figure, kind and turn are laid out whole, and the status is whole or,
     * where it cannot be (RECORD -694, -715), one line ending in "…". Was "at 360 dp the narrowed column still lays out every large
     * figure and status line whole", asserting `causedByButton.isEmpty() && notOneLineEllipsis.isEmpty()` across the card drawn
     * with the button and without it.
     */
    @Test
    fun `at 360 dp every large figure, kind and turn is whole, and the status whole or one line ending in an ellipsis`() {
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
        val problems = mutableListOf<String>()
        var measured = 0
        for (c in cases) {
            composeRule.runOnIdle { case = c }
            composeRule.waitForIdle()
            measured++
            listOf(NAVIGATION_HUD_DISTANCE_TAG, NAVIGATION_HUD_DISTANCE_KIND_TAG, NAVIGATION_HUD_TARGET_TAG).forEach { tag ->
                clipped(tag)?.let { problems += "${c.name}: $tag $it" }
            }
            clipped(NAVIGATION_HUD_STATUS_TAG)?.takeIf { !it.endsWith(ONE_LINE_ELLIPSIS) }?.let { problems += "${c.name}: status $it" }
        }
        assertEquals("every case measured", cases.size, measured)
        assertTrue("cut where it must be whole: $problems", problems.isEmpty())
    }

    private companion object {
        const val ONE_LINE_ELLIPSIS = " [one line, ends in …]"
    }
}
