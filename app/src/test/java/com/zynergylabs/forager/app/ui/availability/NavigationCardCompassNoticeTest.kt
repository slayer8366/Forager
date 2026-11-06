package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
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
 * Dispatch 2026-11-05-10, Amendment 1, H3 (RECORD -2181): the compass notices. The owner, to "When the compass struggles, the card's
 * heading spot shows "Compass calibrating…" or "Compass unavailable · north up" (the map turns north-up). With the heading off the
 * card, where do these go?": "In the status space (Recommended)", the option reading "They show where "Approaching" goes, under the
 * evening line. The compass strip stays exactly as it is outside navigation." With other status words at once, the planner's reading
 * of Q6: both show, the notice on its own line under them. Was (dispatch 2026-09-28-430, ruling E): the notice was the card's heading
 * label, beside the north arrow.
 *
 * The card itself ([NavigationInfo]), as the screen composes it, in the app's own theme, 384 dp wide: the way home by trail 50 m
 * from the start ("Approaching"), with the map's navigation view retrying a stuck compass ([NavigationFacing.CALIBRATING]) and then
 * north-up ([NavigationFacing.NORTH_UP]). The facing is the map's, decided by the navigation view, so it is set here as the screen
 * hands it over. The point is the made-up P.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardCompassNoticeTest {

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
    private var facing by mutableStateOf(NavigationFacing.FACING_UP)

    private fun compose() = composeRule.setContent {
        ForagerTheme(darkTheme = false) {
            Box(Modifier.width(384.dp).testTag(NAVIGATION_HUD_TAG)) {
                NavigationInfo(
                    heading = mutableStateOf<TrueHeadingReading>(TrueHeadingReading.Unreliable),
                    liveFix = fix,
                    target = start,
                    distanceUnit = DistanceUnit.MILES,
                    currentTime = CurrentTimeProvider { t + 1_000L },
                    showDecimalDegrees = false,
                    onExit = {},
                    route = ReturnRoute.Ahead(LatLng(here.lat, here.lng + 0.001), 390.0),
                    facing = facing,
                )
            }
        }
    }

    private fun node(tag: String): SemanticsNode? =
        composeRule.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(NAVIGATION_HUD_TAG)), useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()

    private fun text(n: SemanticsNode?) = n?.config?.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }

    private fun assertNotice(f: NavigationFacing, words: String) {
        compose()
        composeRule.runOnIdle { facing = f }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        val problems = mutableListOf<String>()
        val status = node(NAVIGATION_HUD_STATUS_TAG)
        val divider = node(NAVIGATION_HUD_DIVIDER_TAG)
        // The tag's own string, so this test compiles before H3 (tests first): NAVIGATION_HUD_COMPASS_NOTICE_TAG.
        val notice = node("navigation-hud-compass-notice")
        if (text(status) != "Approaching") problems += "positive control: the status words read <${text(status)}>, not <Approaching>"
        if (text(notice) != words) problems += "the card's status space shows the notice <${text(notice)}>, not <$words>"
        if (notice != null && status != null && notice.boundsInRoot.top < status.boundsInRoot.bottom - 1f) problems += "the notice ${notice.boundsInRoot} is not on its own line under the status words ${status.boundsInRoot}"
        if (notice != null && divider != null && notice.boundsInRoot.left < divider.boundsInRoot.right) problems += "the notice ${notice.boundsInRoot} is not in the right part (the divider ${divider.boundsInRoot})"
        // H1: the card draws no heading, the notice included.
        if (node(NAVIGATION_HUD_HEADING_TAG) != null) problems += "the card still has a heading <${text(node(NAVIGATION_HUD_HEADING_TAG))}>"
        println("COMPASSNOTICE|$f|notice <${text(notice)}> ${notice?.boundsInRoot}|status <${text(status)}> ${status?.boundsInRoot}")
        assertTrue(problems.joinToString("\n") { "$f: $it" }, problems.isEmpty())
    }

    @Test
    fun `H3, calibrating - Compass calibrating is in the status space, under Approaching`() = assertNotice(NavigationFacing.CALIBRATING, "Compass calibrating…")

    @Test
    fun `H3, north-up - Compass unavailable north up is in the status space, under Approaching`() = assertNotice(NavigationFacing.NORTH_UP, "Compass unavailable · north up")
}
