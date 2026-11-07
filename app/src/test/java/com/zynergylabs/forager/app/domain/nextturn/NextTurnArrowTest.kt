package com.zynergylabs.forager.app.domain.nextturn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The next turn's arrow (dispatch 2026-11-06-01, Amendment 1, C1 to C3; J, 2b-2 step 1). C1, the planner's call from A2's
 * three words: "slight below 45°, plain from 45° to 134°, sharp from 135°, with left and right. There is no U-turn arrow."
 * C2: "ForkLeft/ForkRight for a fork." C3: "Straight for the straight-ahead arrow." The turn's band is the one the turn rule
 * gives it ([bandOf]), so each edge here is read through the rule's own band, not a band written by hand.
 */
class NextTurnArrowTest {

    /** A turn of [degrees] (right positive, as the turn rule signs it), its band from [bandOf]. */
    private fun turnOf(degrees: Double) =
        NextTurn.Turn(bandOf(degrees), if (degrees < 0) TurnSide.LEFT else TurnSide.RIGHT, metersAway = 120.0, sameSidePathsBefore = 0)

    @Test
    fun `C1 - just below 45 degrees is a slight arrow, each side`() {
        assertEquals(NextTurnArrow.SLIGHT_LEFT, nextTurnArrow(turnOf(-44.9)))
        assertEquals(NextTurnArrow.SLIGHT_RIGHT, nextTurnArrow(turnOf(44.9)))
    }

    @Test
    fun `C1 - from 45 degrees to 134 is a plain arrow, each side`() {
        for (d in listOf(45.0, 90.0, 134.9)) {
            assertEquals("$d left", NextTurnArrow.LEFT, nextTurnArrow(turnOf(-d)))
            assertEquals("$d right", NextTurnArrow.RIGHT, nextTurnArrow(turnOf(d)))
        }
    }

    @Test
    fun `C1 - from 135 degrees is a sharp arrow, each side, and a near U-turn is still sharp - there is no U-turn arrow`() {
        for (d in listOf(135.0, 170.0, 179.9)) {
            assertEquals("$d left", NextTurnArrow.SHARP_LEFT, nextTurnArrow(turnOf(-d)))
            assertEquals("$d right", NextTurnArrow.SHARP_RIGHT, nextTurnArrow(turnOf(d)))
        }
    }

    @Test
    fun `C2 - a fork shows the side to keep to`() {
        assertEquals(NextTurnArrow.FORK_LEFT, nextTurnArrow(NextTurn.Fork(TurnSide.LEFT, 80.0)))
        assertEquals(NextTurnArrow.FORK_RIGHT, nextTurnArrow(NextTurn.Fork(TurnSide.RIGHT, 80.0)))
    }

    @Test
    fun `C3 - no turn left shows the straight-ahead arrow`() {
        assertEquals(NextTurnArrow.STRAIGHT, nextTurnArrow(NextTurn.NoTurnLeft))
    }

    @Test
    fun `E1 to E3 - every blank shows no arrow`() {
        for (reason in NextTurnBlank.entries) assertNull(reason.name, nextTurnArrow(NextTurn.Blank(reason)))
    }
}
