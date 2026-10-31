package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.TrailRoute
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import com.zynergylabs.forager.app.domain.routing.TrailRouter
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import com.zynergylabs.forager.app.domain.wayto.FakeEngines
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The trail data's heights reach a plotted leg (dispatch 2026-10-30-08, Amendment 8, RECORD -1896): [PlotRouteUseCase.leg]
 * keeps the engine's `TrailRoute.heightsMetres` on the leg's trail part, point for point. The joins from each stop to the
 * trail carry none, so the line has two more points than heights.
 */
class PlotRouteHeightsTest {

    private val errorLog = ErrorLog { _, _, _ -> }

    /** One route, whatever is asked: three points along [Ground]'s east-west line, with [heights]. */
    private class HeightedRouter(private val heights: List<Double?>) : TrailRouter {
        val points = listOf(at(0.0, 0.0), at(250.0, 0.0), at(500.0, 0.0))
        override suspend fun route(start: LatLng, end: LatLng, routeData: File): TrailRouteResult =
            TrailRouteResult.Found(TrailRoute(points, 500, heights))
    }

    private fun leg(router: TrailRouter): RouteLeg =
        runBlocking { PlotRouteUseCase(FakeRegions(Ground.region()), FakeEngines(router), errorLog).leg(at(0.0, 40.0), at(500.0, -60.0)) }

    @Test
    fun `a leg keeps the engine's heights on its trail part, and its joins carry none`() {
        val router = HeightedRouter(listOf(100.0, 110.0, 104.0))
        val trail = leg(router) as RouteLeg.Trail

        assertEquals("the trail part is the engine's route", router.points, trail.line.subList(trail.trailFrom, trail.trailTo + 1))
        assertEquals("its heights, point for point", listOf(100.0, 110.0, 104.0), trail.trailHeightsMetres)
        assertEquals("the joins carry no heights: two more points than heights", trail.trailHeightsMetres.size + 2, trail.line.size)
        assertEquals("the leg's metres still count the joins", 40.0 + 500.0 + 60.0, trail.meters, 0.5)
        assertEquals(GeoDistance.metersBetween(trail.line.first(), at(0.0, 40.0)), 0.0, 0.01)
    }

    @Test
    fun `a route with only some heights keeps them as they are, and the leg shows no elevation`() {
        val trail = leg(HeightedRouter(listOf(100.0, null, 120.0))) as RouteLeg.Trail

        assertEquals(listOf(100.0, null, 120.0), trail.trailHeightsMetres)
        assertNull(legElevation(trail))
    }
}
