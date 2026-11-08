package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
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
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-06-01, Amendment 1, J7 (RECORD -2183): "Prove it can't loop (a settling test across frames)", at the edge.
 *
 * [NextTurnRightPartSettlesTest] runs the real screen at 823 x 384, but there the card with its evening line misses the room by more
 * than a line, so it would settle whatever the next turn's place did to the height the room is decided on. This one puts the room's
 * edge where a loop would live: the card alone (landscape, font 2.0, its evening line never fitting under the limit given), and a
 * host that decides "off for room" the way [navigationCardLift] does, from the height the card reports with its evening line, against
 * a limit set half a line below the height measured with the next turn on its own line. If the height read changed with the next
 * turn's place (a line shorter in the right part), the right part would read as room, the own line as none, and the next turn would
 * swap places every frame or two. The card, its height reported and the next turn's place are read every frame for three seconds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NextTurnRoomDecisionSettlesTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 50.0, accuracyMeters = 8f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.53, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    /** The model host's limit on the card's height with its evening line; above it, off for room. Infinite: always room. */
    private var limitPx by mutableFloatStateOf(Float.POSITIVE_INFINITY)
    private var reportedPx by mutableFloatStateOf(Float.NaN)

    @After
    fun restore() {
        composeRule.mainClock.autoAdvance = true
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
                    sundownLine = SUNDOWN,
                    // RECORD -955's limit far below the screen: the line never fits on the card, as where it can't even moved up.
                    eveningLineTopLimitInRootPx = 100_000f,
                    // As the host: off for room where the height with the evening line passes the limit (NavigationCardLift.NoRoom).
                    eveningOffForRoom = !reportedPx.isNaN() && reportedPx > limitPx,
                    onHeightWithEvening = { height, _ -> reportedPx = height },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private data class Frame(val card: DpRect, val nextTurn: DpRect, val reportedPx: Float)

    private fun frame() = Frame(
        composeRule.onNodeWithTag(NAVIGATION_HUD_TAG).getUnclippedBoundsInRoot(),
        composeRule.onNodeWithTag(NEXT_TURN_SPACE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot(),
        reportedPx,
    )

    private fun inRightPart(f: Frame): Boolean =
        f.nextTurn.left >= composeRule.onNodeWithTag(NAVIGATION_HUD_DIVIDER_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot().right - 0.5.dp

    @Test
    fun `J7 - with the room's edge half a line below the card's height with its evening line, the next turn takes the right part and stays`() {
        show()
        val own = frame()
        println("J7 edge, room: $own")
        assertTrue("precondition: with room, the next turn has its own line (J3 step 3): $own", !inRightPart(own))
        assertTrue("precondition: the card reports its height with the evening line: $own", !own.reportedPx.isNaN())

        // The limit half a line below that height: off for room now, and a line shorter would read as room.
        val halfLinePx = with(composeRule.density) { 16.dp.toPx() }
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { limitPx = own.reportedPx - halfLinePx; Snapshot.sendApplyNotifications() }
        val frames = (0 until FRAMES).map {
            composeRule.mainClock.advanceTimeByFrame()
            frame()
        }
        val tail = frames.drop(SETTLE_FRAMES)
        val first = tail.first()
        val moved = tail.withIndex().filter { it.value != first }
        val swaps = frames.zipWithNext().count { (a, b) -> inRightPart(a) != inRightPart(b) }
        println("J7 edge, off for room (limit ${own.reportedPx - halfLinePx}): settled $first; frames that differ after $SETTLE_FRAMES: ${moved.size}; place swaps over $FRAMES frames: $swaps; reported ${frames.map { it.reportedPx }.distinct()}")
        assertTrue("J7: off for room, the next turn is in the right part: $first", inRightPart(first))
        assertTrue("J7: the next turn changes place once, never back and forth: $swaps swaps", swaps <= 1)
        assertTrue("J7: nothing moves once settled (no loop); first $first, then ${moved.take(3)}", moved.isEmpty())
        assertEquals("J7: the height the room is decided on is the same with the next turn in the right part as on its own line", own.reportedPx, first.reportedPx, 0.5f)
    }

    private companion object {
        const val SUNDOWN = "Sunset 4:52 PM"

        /** Three seconds of frames, longer than any of the card's motion. */
        const val FRAMES = 180

        /** The first second, for the one move from the own line to the right part to run. */
        const val SETTLE_FRAMES = 60
    }
}
