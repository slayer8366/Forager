package com.zynergylabs.forager.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The navigation display's turn words and distance kinds (dispatch 2026-09-28-677, data part B), headless. Literals only;
 * each band edge is tested on both sides, and each side with a bearing whose mirror would land in the other, so a swapped
 * side or an off-by-one edge changes an answer.
 */
class NavigationWordsTest {

    @Test
    fun `the owner's two examples read as the owner wrote them`() {
        // 350° clockwise is 10° to the left; 45° clockwise is 45° to the right.
        assertEquals("slight left 10°", turnText(350f))
        assertEquals("right 45°", turnText(45f))
    }

    @Test
    fun `under 180 clockwise is to the right and over 180 to the left, never a number past 180`() {
        assertEquals(Turn(TurnBand.TURN, TurnSide.RIGHT, 90), turnFor(90f))
        assertEquals(Turn(TurnBand.TURN, TurnSide.LEFT, 90), turnFor(270f))
        assertEquals("left 90°", turnText(270f))
        assertEquals("sharp left 135°", turnText(225f))
        assertEquals("sharp right 169°", turnText(169f))
    }

    @Test
    fun `each band starts at its own constant and ends one degree before the next`() {
        assertEquals(TurnBand.AHEAD, turnFor(9f).band)
        assertEquals(TurnBand.SLIGHT, turnFor(10f).band)
        assertEquals(TurnBand.SLIGHT, turnFor(44f).band)
        assertEquals(TurnBand.TURN, turnFor(45f).band)
        assertEquals(TurnBand.TURN, turnFor(134f).band)
        assertEquals(TurnBand.SHARP, turnFor(135f).band)
        assertEquals(TurnBand.SHARP, turnFor(169f).band)
        assertEquals(TurnBand.BEHIND, turnFor(170f).band)
        assertEquals(TurnBand.BEHIND, turnFor(180f).band)
        // The same edges on the left.
        assertEquals(TurnBand.AHEAD, turnFor(351f).band)
        assertEquals(TurnBand.SLIGHT, turnFor(350f).band)
        assertEquals(TurnBand.SLIGHT, turnFor(316f).band)
        assertEquals(TurnBand.TURN, turnFor(315f).band)
        assertEquals(TurnBand.SHARP, turnFor(225f).band)
        assertEquals(TurnBand.BEHIND, turnFor(190f).band)
    }

    @Test
    fun `the band is chosen on the rounded degrees, so the words and the number shown agree`() {
        // 9.6 rounds to 10, so it is Slight, as "· 10°" says; 9.4 rounds to 9 and is Ahead.
        assertEquals("slight right 10°", turnText(9.6f))
        assertEquals("ahead 9°", turnText(9.4f))
        assertEquals("slight left 10°", turnText(350.4f))
    }

    @Test
    fun `ahead and behind carry no side`() {
        assertEquals("ahead 0°", turnText(0f))
        assertEquals("ahead 4°", turnText(356f))
        assertEquals("behind 180°", turnText(180f))
        assertEquals("behind 175°", turnText(185f))
        assertEquals(null, turnFor(4f).side)
        assertEquals(null, turnFor(185f).side)
    }

    @Test
    fun `out-of-range input wraps rather than reading past 180`() {
        assertEquals("slight left 10°", turnText(-10f))
        assertEquals("right 45°", turnText(405f))
        assertEquals("ahead 0°", turnText(360f))
    }

    @Test
    fun `the distance kinds' words`() {
        assertEquals("by trail", DistanceKind.BY_TRAIL.words)
        assertEquals("straight", DistanceKind.STRAIGHT.words)
    }
}
