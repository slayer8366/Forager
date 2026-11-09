package com.zynergylabs.forager.app.domain.nextturn

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The turn rule on measured directions (dispatch 2026-11-06-01, Amendment 1): B1, R(30°, 30°); B3, forks; C1, the bands;
 * C2, the fork's side. Bearings are given straight, so each case's angles are exact. The route comes in heading north
 * (0°) in every case.
 */
class TurnRuleTest {

    private fun judge(out: Double, vararg others: Double) = judgeJunction(inBearing = 0.0, outBearing = out, others = others.map { OtherWay(it) })

    private fun judgeWays(out: Double, vararg others: OtherWay) = judgeJunction(inBearing = 0.0, outBearing = out, others = others.toList())

    // B1: "It is a turn when another way there is straighter than the route's way, or when the route bends at least 30°
    // and is the straightest by less than 30°."

    @Test
    fun `B1 - turning left at a crossroads, where the way straight on is straighter, is a turn`() {
        val j = judge(270.0, 0.0, 90.0)
        assertEquals(JunctionClass.TURN, j.junctionClass)
        assertEquals(TurnSide.LEFT, j.side)
        assertEquals(-90.0, j.routeTurnDegrees!!, 1e-9)
        assertEquals("the way straight on is 90 degrees straighter", -90.0, j.marginDegrees!!, 1e-9)
    }

    @Test
    fun `B1 - a slight bend at a junction where another way is straighter is a turn, however small`() {
        // The route bends 20 degrees left; another way leaves 5 degrees right: under S, but another way is straighter.
        val j = judge(340.0, 5.0, 120.0)
        assertEquals(JunctionClass.TURN, j.junctionClass)
        assertEquals(TurnSide.LEFT, j.side)
        assertEquals(-15.0, j.marginDegrees!!, 1e-9)
    }

    @Test
    fun `B1 - at a T approached on its stem the route is the straightest by nothing, and it is a turn`() {
        val j = judge(90.0, 270.0)
        assertEquals(JunctionClass.TURN, j.junctionClass)
        assertEquals(TurnSide.RIGHT, j.side)
        assertEquals(0.0, j.marginDegrees!!, 1e-9)
    }

    @Test
    fun `B1 - a wide Y the route takes by a margin under 30 degrees, bending 40, is a turn`() {
        val j = judge(40.0, 300.0)
        assertEquals(JunctionClass.TURN, j.junctionClass)
        assertEquals(TurnSide.RIGHT, j.side)
        assertEquals(20.0, j.marginDegrees!!, 1e-9)
    }

    @Test
    fun `B1 - winding through a junction, bending under 30 degrees and the straightest, is no turn`() {
        // The other way is 15 degrees further round than the route, inside M, so only S keeps this from being a turn.
        val j = judge(25.0, 320.0)
        assertEquals(JunctionClass.STRAIGHT_ON, j.junctionClass)
        assertEquals(null, j.side)
    }

    @Test
    fun `B1 - bending 40 degrees and the straightest by 30 or more is no turn`() {
        assertEquals(JunctionClass.STRAIGHT_ON, judge(40.0, 290.0).junctionClass) // margin 30: "by less than 30" fails
        assertEquals(JunctionClass.STRAIGHT_ON, judge(40.0, 280.0).junctionClass) // margin 40
    }

    @Test
    fun `B1 - the bend's threshold is at least 30 degrees`() {
        // The other way is 10 degrees further round than the route, so the margin is under 30 either side of S.
        assertEquals(JunctionClass.TURN, judge(30.0, 320.0).junctionClass)
        assertEquals(JunctionClass.STRAIGHT_ON, judge(29.5, 320.5).junctionClass)
    }

    @Test
    fun `B1 - carrying straight on through a crossroads is no turn, and counts a path either side`() {
        val j = judge(0.0, 270.0, 90.0)
        assertEquals(JunctionClass.STRAIGHT_ON, j.junctionClass)
        assertEquals(1, j.leftPaths)
        assertEquals(1, j.rightPaths)
    }

    @Test
    fun `B1 - the way the route came in on is never one of the others, so a way back the same way changes nothing`() {
        // A way leaving almost straight back, 170 degrees round: it is a path to the right, never straighter.
        val j = judge(0.0, 170.0)
        assertEquals(JunctionClass.STRAIGHT_ON, j.junctionClass)
        assertEquals(0, j.leftPaths)
        assertEquals(1, j.rightPaths)
    }

    // B3: "a fork is where the route keeps the straightest way while another way leaves within 45° of it. The arrow is
    // keep-left when the branch leaves to the right, and keep-right when it leaves to the left."

    @Test
    fun `B3 - keeping the straightest way while another leaves within 45 degrees of it is a fork`() {
        assertEquals(JunctionClass.FORK, judge(5.0, 325.0).junctionClass) // 40 degrees from the route's way
        assertEquals(JunctionClass.FORK, judge(0.0, 40.0).junctionClass)
    }

    @Test
    fun `B3 - another way leaving more than 45 degrees from the route's way makes no fork`() {
        assertEquals(JunctionClass.STRAIGHT_ON, judge(5.0, 310.0).junctionClass) // 55 degrees from it
        assertEquals(JunctionClass.STRAIGHT_ON, judge(0.0, 50.0).junctionClass)
    }

    @Test
    fun `B3 - a turn stays a turn when another way also leaves within 45 degrees`() {
        val j = judge(270.0, 0.0, 240.0)
        assertEquals(JunctionClass.TURN, j.junctionClass)
        assertEquals(null, j.keep)
    }

    @Test
    fun `B3 - a fork counts no paths passed, since it is a next turn itself`() {
        val j = judge(0.0, 30.0, 270.0)
        assertEquals(JunctionClass.FORK, j.junctionClass)
        assertEquals(0, j.leftPaths)
        assertEquals(0, j.rightPaths)
    }

    // B5, the owner: "Walkable ways only (Recommended)": "Footpaths, trails, tracks and streets count. Driveways,
    // parking-lot lanes and motorway slips don't, so a driveway never makes it \"the second left\"."

    @Test
    fun `B5 - carrying straight on, a way that is not a path is not counted on its side, and is counted apart`() {
        val j = judgeWays(0.0, OtherWay(270.0), OtherWay(90.0, countsAsPath = false), OtherWay(100.0, countsAsPath = false))
        assertEquals(JunctionClass.STRAIGHT_ON, j.junctionClass)
        assertEquals(1, j.leftPaths)
        assertEquals(0, j.rightPaths)
        assertEquals(0, j.leftNotPaths)
        assertEquals(2, j.rightNotPaths)
    }

    @Test
    fun `B5 - the turn rule still counts every way, so a driveway straight on makes turning left a turn`() {
        val j = judgeWays(270.0, OtherWay(0.0, countsAsPath = false))
        assertEquals(JunctionClass.TURN, j.junctionClass)
        assertEquals(TurnSide.LEFT, j.side)
    }

    // C2: the fork's side.

    @Test
    fun `C2 - a branch leaving to the right is keep left, and to the left keep right`() {
        assertEquals(TurnSide.LEFT, judge(0.0, 30.0).keep)
        assertEquals(TurnSide.RIGHT, judge(0.0, 330.0).keep)
    }

    @Test
    fun `C2 - with branches within 45 degrees on both sides, the nearer one says which side to keep to`() {
        assertEquals(TurnSide.RIGHT, judge(0.0, 340.0, 35.0).keep)
        assertEquals(TurnSide.LEFT, judge(0.0, 325.0, 15.0).keep)
    }

    // C1: "slight below 45°, plain from 45° to 134°, sharp from 135°, with left and right. There is no U-turn arrow."

    @Test
    fun `C1 - the bands are slight below 45, plain from 45 to below 135, sharp from 135`() {
        assertEquals(TurnBand.SLIGHT, bandOf(44.9))
        assertEquals(TurnBand.PLAIN, bandOf(45.0))
        assertEquals(TurnBand.PLAIN, bandOf(134.9))
        assertEquals(TurnBand.SHARP, bandOf(135.0))
        assertEquals("no U-turn band", TurnBand.SHARP, bandOf(180.0))
        assertEquals("left as right", TurnBand.SLIGHT, bandOf(-44.9))
        assertEquals(TurnBand.SHARP, bandOf(-135.0))
    }

    @Test
    fun `C1 - a turn carries its band from the route's own turn`() {
        assertEquals(TurnBand.SLIGHT, judge(340.0, 5.0).band)
        assertEquals(TurnBand.PLAIN, judge(270.0, 0.0).band)
        assertEquals(TurnBand.SHARP, judge(210.0, 0.0, 90.0).band)
        assertEquals(TurnSide.LEFT, judge(210.0, 0.0, 90.0).side)
    }
}
