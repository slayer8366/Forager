package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import androidx.compose.runtime.snapshots.Snapshot
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.After
import androidx.compose.ui.semantics.getOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Motion Part 2, Amendment 1 (RECORD -672), item 2 (scout N6): the navigation display's sundown line fades and grows like the
 * strip's. The display is caught growing and shrinking between its two heights; under reduced motion (the transition scale alone
 * at 0, so Compose's own animations still run) it takes the line's height at once.
 *
 * Nothing here has been run: written with the code, before the build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
// Dispatch 2026-10-12-06: native graphics, so text is measured at its real width. The card now places a short evening line in
// its right column and a long one on its own line under the columns (RECORD -934), decided by measured widths; under the legacy
// graphics this class ran on, every text measured a few dp wide, so the long line fitted the right column and the display did
// not grow (diagnosed on CI run 38223100221: "0.7 mi" measured 2.3 dp). The claim, and SUNDOWN, are unchanged.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HudSundownLineGrowTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 8f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    private var line by mutableStateOf<String?>(null)
    private var backBy by mutableStateOf<String?>(null)

    @After
    fun restore() {
        composeRule.mainClock.autoAdvance = true
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    }

    private fun show() {
        composeRule.setContent {
            ForagerTheme {
                val heading = remember { mutableStateOf<TrueHeadingReading>(TrueHeadingReading.Available(80f)) }
                NavigationHud(
                    heading = heading,
                    liveFix = fix,
                    target = start,
                    distanceUnit = DistanceUnit.MILES,
                    currentTime = CurrentTimeProvider { t + 1_000L },
                    showDecimalDegrees = false,
                    onExit = {},
                    modifier = Modifier.fillMaxWidth(),
                    sundownLine = line,
                    backByLine = backBy,
                )
            }
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun hudHeight(): Dp = composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).getUnclippedBoundsInRoot().let { it.bottom - it.top }

    private fun changeLine(next: String?) {
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { line = next; Snapshot.sendApplyNotifications() }
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
    }

    /**
     * Each frame's height for [frames] frames after [changeLine]. Dispatch 2026-10-12-06: the card's X and three-dot column now
     * stand beside the lines, so the card is as tall as the taller of the two, and the first frames of the line's growth can be
     * hidden under the X column's height. The claim is unchanged (the display grows and shrinks over frames, not at once); it is
     * read across the frames rather than at the third alone, which was the only frame sampled before.
     */
    private fun heightsOverFrames(frames: Int): List<Dp> = (1..frames).map {
        composeRule.mainClock.advanceTimeByFrame()
        hudHeight()
    }

    @Test
    fun `the display grows to hold the line when it opens, and shrinks when it closes`() {
        show()
        val short = hudHeight()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { line = SUNDOWN; backBy = BACK_BY; Snapshot.sendApplyNotifications() }
        val growingFrames = heightsOverFrames(20)
        assertTrue("the line is drawn", composeRule.onAllNodesWithTag(NAVIGATION_HUD_SUNDOWN_LINE_TAG).fetchSemanticsNodes().isNotEmpty())
        settle()
        val tall = hudHeight()
        println("MEASURED sundown grow: short $short, tall $tall, frames $growingFrames")
        assertTrue("taller with the line: $short to $tall", tall > short + 4.dp)
        assertTrue("caught growing: some frame between $short and $tall in $growingFrames", growingFrames.any { it > short + 0.5.dp && it < tall - 0.5.dp })

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { line = null; backBy = null; Snapshot.sendApplyNotifications() }
        val shrinkingFrames = heightsOverFrames(20)
        assertTrue("caught shrinking: some frame between $short and $tall in $shrinkingFrames", shrinkingFrames.any { it > short + 0.5.dp && it < tall - 0.5.dp })
        settle()
        assertEquals("back to its height without the line", short.value, hudHeight().value, 0.5f)
    }

    @Test
    fun `under reduced motion the display takes the line's height at once`() {
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
        show()
        changeLine(SUNDOWN)
        val first = hudHeight()
        settle()
        assertEquals("no grow under reduced motion", hudHeight().value, first.value, 0.5f)
    }

    private companion object {
        // Dispatch 2026-10-12-08 (RECORD -945, -950): the evening stack sits beside the navigation info's left part (the distance,
        // the turn line, the kind), about 70 dp, so a two-piece line (32 dp) no longer makes the card taller, and the claim (it
        // grows over frames when the lines open) is read with RECORD -941's own example and Back by, five pieces, about 80 dp.
        // Was "Sunset 7:42 PM · start back by 7:12 PM" alone.
        const val SUNDOWN = "Sunset 12:41 AM · in 2 h 28 min · dark 1:13 · start back by 12:31"
        const val BACK_BY = "Back by 1:30 AM"
    }
}
