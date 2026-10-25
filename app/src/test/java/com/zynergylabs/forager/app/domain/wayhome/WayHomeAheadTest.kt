package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.HopBand
import com.zynergylabs.forager.app.domain.RouteHome
import com.zynergylabs.forager.app.domain.RouteLinePartKind
import com.zynergylabs.forager.app.domain.RouteWithheldReason
import com.zynergylabs.forager.app.domain.nextRouteLine
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayhome.WayHomeLines.hairpin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The way home along trails as the HUD and map take it (dispatch 2026-10-24-10 Amendment 1, PR B): the figure is the
 * place's, the needle aims 25 m along the line, and the line ahead comes in dotted (track) and solid (trail) runs.
 */
class WayHomeAheadTest {

    private val line = WayHomeLine(hairpin)

    private fun ahead(at: com.zynergylabs.forager.app.domain.model.LatLng, generation: Long = 7): RouteHome.Ahead {
        val progress = placeOnWayHome(line, at, previous = null)
        val result = wayHomeAhead(line, progress, at, generation)
        assertTrue("ahead, not $result", result is RouteHome.Ahead)
        return result as RouteHome.Ahead
    }

    @Test
    fun `from the start of the line the ahead runs are track, trail, track, end to end`() {
        val route = ahead(at(0.0, -50.0))
        assertEquals(listOf(RouteLinePartKind.TRACK, RouteLinePartKind.TRAIL, RouteLinePartKind.TRACK), route.aheadParts.map { it.kind })
        assertEquals("the runs share their joining nodes and cover the line", route.routeAhead, route.aheadParts.flatMapIndexed { i, p -> if (i == 0) p.points else p.points.drop(1) })
        assertEquals("the trail run starts where the walker joined the trail", 0.0, GeoDistance.metersBetween(route.aheadParts[1].points.first(), at(0.0, 0.0)), 0.01)
        assertEquals(570.0, route.routeMeters, 0.5)
        assertEquals(7L, route.generation)
    }

    @Test
    fun `on the trail the ahead starts with the trail, the figure is the place's, and the needle aims 25 m on`() {
        val route = ahead(at(100.0, 0.0))
        assertEquals(RouteLinePartKind.TRAIL, route.aheadParts.first().kind)
        val place = (placeOnWayHome(line, at(100.0, 0.0), null) as WayHomeProgress.Placed).place
        assertEquals(place.remainingMeters, route.routeMeters, 0.0)
        assertEquals(place.remainingMeters, route.pathHome.totalMeters, 0.01)
        // Placed at (80, 0), 20 m behind the walker, for the distance. The needle aims from the node nearest the walker,
        // (100, 0), not back at the place: the first node at least 25 m on from there is (130, 0).
        assertEquals(0.0, GeoDistance.metersBetween(route.lookahead, at(130.0, 0.0)), 0.5)
    }

    @Test
    fun `off the line the route is withheld, as the far hop withholds it`() {
        val result = wayHomeAhead(line, placeOnWayHome(line, at(-80.0, -50.0), null), at(-80.0, -50.0), 7)
        assertEquals(RouteHome.Withheld(RouteWithheldReason.OFF_ROUTE, HopBand.FAR), result)
    }

    @Test
    fun `the way home swapping in replaces the line at Return once, and later ticks keep it`() {
        val trackRoute = ahead(at(0.0, -50.0), generation = 0).copy(route = listOf(at(0.0, -50.0), at(0.0, -100.0)), aheadParts = emptyList())
        val atReturn = nextRouteLine(null, trackRoute)!!
        assertEquals(trackRoute.route, atReturn.atReturn)

        val swapped = nextRouteLine(atReturn, ahead(at(0.0, -50.0)))!!
        assertEquals("the trail line replaces the track at Return", line.nodes, swapped.atReturn)
        assertEquals(3, swapped.aheadParts.size)

        val later = nextRouteLine(swapped, ahead(at(100.0, 0.0)))!!
        assertEquals("kept within one generation", line.nodes, later.atReturn)
        assertEquals(RouteLinePartKind.TRAIL, later.aheadParts.first().kind)

        val reRouted = nextRouteLine(later, ahead(at(100.0, 0.0), generation = 8).copy(route = listOf(at(1.0, 1.0), at(2.0, 2.0))))!!
        assertEquals("a re-route replaces it again", listOf(at(1.0, 1.0), at(2.0, 2.0)), reRouted.atReturn)
    }
}
