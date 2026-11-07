package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What TalkBack says for the next turn (dispatch 2026-11-06-01, Amendment 1, G3; J, 2b-2 step 1). G3, as the amendment sets it
 * out: "turn left/right" for a plain turn; "make a sharp left/right" for a sharp one; "slight left/right" for a slight one;
 * "keep left/right" for a fork; "keep straight" for the straight-ahead arrow. "Each is followed by the distance ("in 400
 * feet")." Counting: "with same-side paths before a turn, it counts them ("take the second left")", plain turns only.
 */
class NextTurnWordsTest {

    private fun turn(band: TurnBand, side: TurnSide, pathsBefore: Int = 0) = NextTurn.Turn(band, side, metersAway = 122.0, sameSidePathsBefore = pathsBefore)

    private val feet = "400 feet"

    @Test
    fun `G3 - a plain turn - turn left, turn right`() {
        assertEquals("turn left in 400 feet", nextTurnWords(turn(TurnBand.PLAIN, TurnSide.LEFT), feet))
        assertEquals("turn right in 400 feet", nextTurnWords(turn(TurnBand.PLAIN, TurnSide.RIGHT), feet))
    }

    @Test
    fun `G3 - a sharp turn - make a sharp left, make a sharp right`() {
        assertEquals("make a sharp left in 400 feet", nextTurnWords(turn(TurnBand.SHARP, TurnSide.LEFT), feet))
        assertEquals("make a sharp right in 400 feet", nextTurnWords(turn(TurnBand.SHARP, TurnSide.RIGHT), feet))
    }

    @Test
    fun `G3 - a slight turn - slight left, slight right, never bear`() {
        assertEquals("slight left in 400 feet", nextTurnWords(turn(TurnBand.SLIGHT, TurnSide.LEFT), feet))
        assertEquals("slight right in 400 feet", nextTurnWords(turn(TurnBand.SLIGHT, TurnSide.RIGHT), feet))
    }

    @Test
    fun `G3 - a fork - keep left, keep right`() {
        assertEquals("keep left in 400 feet", nextTurnWords(NextTurn.Fork(TurnSide.LEFT, 122.0), feet))
        assertEquals("keep right in 400 feet", nextTurnWords(NextTurn.Fork(TurnSide.RIGHT, 122.0), feet))
    }

    @Test
    fun `G3 - the straight-ahead arrow - keep straight`() {
        assertEquals("keep straight in 0.4 miles", nextTurnWords(NextTurn.NoTurnLeft, "0.4 miles"))
    }

    @Test
    fun `G3 - a blank space says nothing`() {
        for (reason in NextTurnBlank.entries) assertNull(reason.name, nextTurnWords(NextTurn.Blank(reason), feet))
    }

    @Test
    fun `G3 counting - same-side paths before a plain turn count it - take the second left, the third right`() {
        assertEquals("take the second left in 400 feet", nextTurnWords(turn(TurnBand.PLAIN, TurnSide.LEFT, pathsBefore = 1), feet))
        assertEquals("take the third right in 400 feet", nextTurnWords(turn(TurnBand.PLAIN, TurnSide.RIGHT, pathsBefore = 2), feet))
    }

    @Test
    fun `G3 counting - plain turns only - a sharp or slight turn keeps its words whatever paths come first`() {
        assertEquals("make a sharp left in 400 feet", nextTurnWords(turn(TurnBand.SHARP, TurnSide.LEFT, pathsBefore = 2), feet))
        assertEquals("slight right in 400 feet", nextTurnWords(turn(TurnBand.SLIGHT, TurnSide.RIGHT, pathsBefore = 1), feet))
    }

    @Test
    fun `counting past ten - the figure with its suffix`() {
        assertEquals("tenth", ordinalWord(10))
        assertEquals("11th", ordinalWord(11))
        assertEquals("12th", ordinalWord(12))
        assertEquals("21st", ordinalWord(21))
        assertEquals("22nd", ordinalWord(22))
        assertEquals("23rd", ordinalWord(23))
    }

    // "in 400 feet": the card's figure with its unit spelled out.

    @Test
    fun `G3 - the figure's unit is spelled out - feet, miles, meters, kilometers`() {
        assertEquals("400 feet", spokenFigure("400 ft"))
        assertEquals("0.3 miles", spokenFigure("0.3 mi"))
        assertEquals("412 meters", spokenFigure("412 m"))
        assertEquals("1.2 kilometers", spokenFigure("1.2 km"))
    }

    @Test
    fun `a figure of 1 takes the singular`() {
        assertEquals("1 foot", spokenFigure("1 ft"))
        assertEquals("1 meter", spokenFigure("1 m"))
        assertEquals("1.0 miles", spokenFigure("1.0 mi"))
    }

    @Test
    fun `every figure the card writes for a trail distance can be spoken, in both units`() {
        for (unit in DistanceUnit.entries) {
            for (meters in listOf(0.0, 0.3, 1.0, 12.0, 121.9, 401.0, 402.3, 999.4, 999.6, 1_000.0, 1_609.0, 15_000.0, 142_000.0)) {
                val figure = formatDistanceMeters(meters, unit)
                assertNotNull("$figure ($unit)", spokenFigure(figure))
            }
        }
    }

    @Test
    fun `a figure in any other form is not guessed at - the dash, within, approximately`() {
        assertNull(spokenFigure("—"))
        assertNull(spokenFigure("within 12 ft"))
        assertNull(spokenFigure("≈ 10 m"))
        assertNull(spokenFigure("Arrived"))
        assertNull(spokenFigure("400 yd"))
    }
}
