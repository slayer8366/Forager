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
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Motion Part 2, item 4 for the compass strip's sundown line (dispatch 2026-09-28-666, scout C1; the owner, RECORD -651: "Fade
 * and grow"): when its window opens the strip grows down to hold it, rather than jumping, and shrinks back when it closes; under
 * reduced motion (the transition scale alone at 0, so Compose's own animations still run) the strip changes height at once.
 *
 * Nothing here has been run: written with the code, before the build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class StripSundownLineGrowTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    private var line by mutableStateOf<String?>(null)
    private var location by mutableStateOf<LatLng?>(LatLng(45.33, -122.64))

    @After
    fun restore() {
        composeRule.mainClock.autoAdvance = true
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    }

    private fun show() {
        composeRule.setContent {
            ForagerTheme {
                val heading = remember { mutableStateOf<TrueHeadingReading>(TrueHeadingReading.Available(80f)) }
                CompassElevationStrip(
                    heading = heading,
                    elevationMeters = 123.0,
                    unitSystem = UnitSystem.IMPERIAL,
                    location = location,
                    showDecimalDegrees = false,
                    onToggleCoordinateFormat = {},
                    modifier = Modifier.fillMaxWidth(),
                    sundownLine = line,
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

    private fun stripHeight(): Dp = composeRule.onNodeWithTag(STRIP_TAG).getUnclippedBoundsInRoot().let { it.bottom - it.top }

    private fun changeLine(next: String?) {
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { line = next; Snapshot.sendApplyNotifications() }
        repeat(3) { composeRule.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun `the strip grows to hold the line when it opens, and shrinks when it closes`() {
        show()
        val short = stripHeight()
        changeLine(SUNDOWN)
        val growing = stripHeight()
        assertTrue("the line is drawn", composeRule.onAllNodesWithTag(STRIP_SUNDOWN_LINE_TAG).fetchSemanticsNodes().isNotEmpty())
        settle()
        val tall = stripHeight()
        assertTrue("taller with the line: $short to $tall", tall > short + 4.dp)
        assertTrue("caught growing: $growing between $short and $tall", growing > short + 0.5.dp && growing < tall - 0.5.dp)

        changeLine(null)
        val shrinking = stripHeight()
        assertTrue("caught shrinking: $shrinking between $short and $tall", shrinking > short + 0.5.dp && shrinking < tall - 0.5.dp)
        settle()
        assertEquals("back to its height without the line", short.value, stripHeight().value, 0.5f)
        assertTrue("and the line is gone", composeRule.onAllNodesWithTag(STRIP_SUNDOWN_LINE_TAG).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `under reduced motion the strip takes the line's height at once`() {
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
        show()
        changeLine(SUNDOWN)
        val first = stripHeight()
        settle()
        assertEquals("no grow under reduced motion", stripHeight().value, first.value, 0.5f)
    }

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
        const val SUNDOWN = "Sunset 7:42 PM · start back by 7:12 PM"
    }

    /** Amendment 1 (RECORD -672), item 3 (scout C2): losing the fix crossfades the readout into the no-fix line. */
    @Test
    fun `losing the fix crossfades the readout into the no-fix line, and back`() {
        show()
        assertEquals(1, composeRule.onAllNodesWithTag(COMPASS_STRIP_HEADING_TAG).fetchSemanticsNodes().size)
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { location = null; Snapshot.sendApplyNotifications() }
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        assertEquals("the no-fix line is arriving", 1, composeRule.onAllNodesWithTag(COMPASS_STRIP_NO_FIX_TAG).fetchSemanticsNodes().size)
        assertEquals("the readout is still on screen, fading out", 1, composeRule.onAllNodesWithTag(COMPASS_STRIP_HEADING_TAG).fetchSemanticsNodes().size)
        settle()
        assertTrue("then the readout is gone", composeRule.onAllNodesWithTag(COMPASS_STRIP_HEADING_TAG).fetchSemanticsNodes().isEmpty())
        assertEquals(1, composeRule.onAllNodesWithTag(COMPASS_STRIP_NO_FIX_TAG).fetchSemanticsNodes().size)
    }
}
