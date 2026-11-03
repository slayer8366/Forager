package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.route.PlotRouteUseCase
import com.zynergylabs.forager.app.domain.route.RouteLeg
import com.zynergylabs.forager.app.domain.route.legElevation
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routing.TrailEngine
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.ui.route.routeElevationText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * One plotted leg on the real vendored BRouter and the Harriman square with heights (`segments-heighted`, from #447), through
 * [PlotRouteUseCase] (dispatch 2026-10-30-08, Amendment 8, RECORD -1896). Nothing is stubbed but the list of downloaded
 * regions.
 *
 * The leg is `inside-short`, between two OpenStreetMap trail nodes, so its joins are a few metres at most. Its expected
 * heights are BRouter v1.7.10's own command-line router's (`brouter-cli/heighted/inside-short.geojson`). The expected climb
 * and descent were banked from those 167 heights by a separate script with the same 4 m rule, outside this app: first
 * 261.75 m, last 281.5 m, so a net +19.75 m; 168.75 m up and 146.0 m down.
 */
class PlotRouteHeightedOnBRouterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> synchronized(logged) { logged += message } }

    private val router by lazy { BRouterTrailRouter(RoutingTestData.profileDir(tmp), errorLog) }
    private val engines = object : TrailEngineSource {
        override suspend fun engine(): Result<TrailEngine> = Result.success(TrailEngine(router, BRouterTrailSnapper(router, TrailSnapMethod.SNAP)))
    }

    private val from = LatLng(41.2741743, -74.0013283)
    private val to = LatLng(41.2788313, -74.0189684)

    private fun commandLineHeights(): List<Double?> =
        Json.parseToJsonElement(RoutingTestData.resource("brouter-cli/heighted/inside-short.geojson").readText()).jsonObject
            .getValue("features").jsonArray[0].jsonObject.getValue("geometry").jsonObject.getValue("coordinates").jsonArray
            .map { point -> point.jsonArray.getOrNull(2)?.jsonPrimitive?.double }

    @Test
    fun `a leg on the heighted square carries the command-line router's heights, and its climb is banked from them`() {
        val data = RoutingTestData.heightedSegments(tmp)
        val region = Ground.region(id = 7, centre = from, radiusKm = 20, routeData = RegionRouteData.Stored("run", "t", 10L, data))
        val leg = runBlocking { PlotRouteUseCase(FakeRegions(region), engines, errorLog).leg(from, to) }

        assertTrue("along trails, not $leg (log: $logged)", leg is RouteLeg.Trail)
        val trail = leg as RouteLeg.Trail
        val expected = commandLineHeights()
        assertEquals("the command-line route's points", 167, expected.size)
        assertEquals("the leg's heights are the command-line router's, point for point", expected, trail.trailHeightsMetres)

        val elevation = requireNotNull(legElevation(trail)) { "an elevation for a leg with every height" }
        assertEquals("net: the last height minus the first", 19.75, elevation.netMeters, 1e-9)
        assertEquals("climb, banked through 4 m", 168.75, elevation.climbMeters, 1e-9)
        assertEquals("descent, banked through 4 m", 146.0, elevation.descentMeters, 1e-9)
        assertEquals("Elevation: +20 m (↑ 169 ↓ 146)", routeElevationText(elevation, UnitSystem.METRIC))
        assertEquals("Elevation: +65 ft (↑ 554 ↓ 479)", routeElevationText(elevation, UnitSystem.IMPERIAL))
        assertEquals("nothing fell back: $logged", emptyList<String>(), logged.toList())
    }
}
