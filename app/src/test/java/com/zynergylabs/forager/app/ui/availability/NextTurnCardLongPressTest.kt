package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.nextturn.NextTurn
import com.zynergylabs.forager.app.domain.nextturn.TurnBand
import com.zynergylabs.forager.app.domain.nextturn.TurnSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * CLAUDE.md, "Surfaces intercept touches": real long-presses at screen coordinates on the map around the navigation card reach the
 * map, with the next turn beside the needle (384 dp, normal text) and on its own line (384 dp, font 1.3, the card one line taller), in
 * portrait and in landscape (823 x 384, where the card is on the bottom edge and the rows are above it). Three rows beside the card, across its width; a point inside any clickable control is skipped,
 * and the number sampled is asserted, so an empty sample cannot pass (dispatch 2026-11-06-01, J2, build item 4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NextTurnCardLongPressTest : NextTurnCardRig() {

    private val turn = NextTurn.Turn(TurnBand.SHARP, TurnSide.RIGHT, metersAway = 60.0, sameSidePathsBefore = 0)

    private fun longPressesAroundTheCardReachTheMap(where: String, expected: NextTurnPlace) {
        show(turn)
        val c = card(where)
        assertEquals(c.describe(), expected, c.place)
        val controls = composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().map {
            with(composeRule.density) { DpRect(it.boundsInRoot.left.toDp(), it.boundsInRoot.top.toDp(), it.boundsInRoot.right.toDp(), it.boundsInRoot.bottom.toDp()) }
        }
        // In landscape the card sits on the screen's bottom edge (RECORD -953), so the map is above it there, not under it.
        val rootBottom = with(composeRule.density) { composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom.toDp() }
        val onBottomEdge = c.hud.bottom >= rootBottom - 1.dp
        var sampled = 0
        for (row in listOf(4.dp, 12.dp, 24.dp)) {
            val y: Dp = if (onBottomEdge) c.hud.top - row else c.hud.bottom + row
            for (i in 0 until SAMPLES) {
                val x = c.hud.left + 8.dp + (c.hud.right - c.hud.left - 16.dp) * (i.toFloat() / (SAMPLES - 1))
                if (controls.any { x >= it.left && x <= it.right && y >= it.top && y <= it.bottom }) continue
                val before = mapLongPresses
                val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
                composeRule.onRoot().performTouchInput { longClick(p) }
                composeRule.waitForIdle()
                assertEquals("$where: a long-press at ($x, $y), $row ${if (onBottomEdge) "above" else "under"} the card, reached the map; ${c.describe()}", before + 1, mapLongPresses)
                sampled++
            }
        }
        assertTrue("$where: sampled $sampled points", sampled >= SAMPLES)
    }

    @Test
    @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `portrait 384, normal text - beside the needle - long-presses under the card reach the map`() =
        longPressesAroundTheCardReachTheMap("384/1.0", NextTurnPlace.BESIDE_NEEDLE)

    @Test
    @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 1.3f)
    fun `portrait 384, font 1,3 - its own line - long-presses under the taller card reach the map`() =
        longPressesAroundTheCardReachTheMap("384/1.3", NextTurnPlace.OWN_LINE)

    @Test
    @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `landscape 823 x 384, normal text - long-presses under the card reach the map`() =
        longPressesAroundTheCardReachTheMap("823x384/1.0", LANDSCAPE_823_AT_1_0)

    @Test
    @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
    fun `landscape 823 x 384, font 2,0 - J3 - the next turn in the right part - long-presses above the card reach the map`() =
        longPressesAroundTheCardReachTheMap("823x384/2.0", NextTurnPlace.RIGHT_PART)

    private companion object {
        const val SAMPLES = 9
    }
}
