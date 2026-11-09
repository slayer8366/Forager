package com.zynergylabs.forager.app.domain.nextturn

import com.zynergylabs.forager.app.domain.wayhome.WayHomeLine
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLines
import com.zynergylabs.forager.app.domain.wayhome.WayHomeNote
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import com.zynergylabs.forager.app.domain.wayhome.WayHomeShown
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The way home as the next turn reads it (dispatch 2026-11-06-01, Amendment 1, J2, E2), from what `WayHomeWatch` shows: a line along
 * trails with its junctions, still being worked out, or no trail route at all (E2, the owner: "For instances without trails, when
 * there is no routing data, then keep it blank.").
 */
class NextTurnWayOfTest {

    private val line = WayHomeLine(WayHomeLines.hairpin)
    private val turns = RouteTurns.Read(emptyList(), trackAheadAfterNode = null, reading = WaysReading(0L, 1, 0))

    @Test
    fun `a line along trails is the way along trails, with its junctions or none yet`() {
        val shown = WayHomeShown("t1", 1L, WayHomeResult.AlongTrails(WayHomeLines.hairpin), line = line, turns = turns)
        assertEquals(NextTurnWay.AlongTrails(line, turns), nextTurnWayOf(shown))
        assertEquals(NextTurnWay.AlongTrails(line, null), nextTurnWayOf(shown.copy(turns = null)))
    }

    @Test
    fun `nothing shown, or the way home still being worked out, is pending`() {
        assertEquals(NextTurnWay.Pending, nextTurnWayOf(null))
        assertEquals(NextTurnWay.Pending, nextTurnWayOf(WayHomeShown("t1", 1L, result = null)))
    }

    @Test
    fun `E2 - a way home with no trail route is no trail route`() {
        assertEquals(NextTurnWay.NoTrailRoute, nextTurnWayOf(WayHomeShown("t1", 1L, WayHomeResult.TrackOnly(WayHomeNote.ROUTES_NOT_DOWNLOADED))))
    }
}
