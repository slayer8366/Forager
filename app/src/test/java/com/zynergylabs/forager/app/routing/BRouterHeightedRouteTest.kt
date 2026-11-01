package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.TrailRoute
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
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
import kotlin.math.roundToLong

/**
 * The trail router on the route cutter's Harriman square **with heights** (dispatch 2026-10-30-06, Amendment 3; Sorrel's
 * N4 and N7 on #447): `segments-heighted/W75_N40.rd5`, a byte-for-byte copy of the Worker fixture from #452. The square
 * without heights keeps its own folder and its own test (`BRouterTrailRouterTest`, "every point's height is missing").
 *
 * Every route here runs the real vendored BRouter. The expected routes are what BRouter v1.7.10's own command-line router
 * returned for all eight routes in the Worker fixture's `routes.json` on the same square (`brouter-cli/heighted/`,
 * written by `make-expected.sh`; README there).
 */
class BRouterHeightedRouteTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logged = mutableListOf<Throwable>()
    private val errorLog = ErrorLog { _, _, error -> synchronized(logged) { logged += error } }

    private val router by lazy { BRouterTrailRouter(RoutingTestData.profileDir(tmp), errorLog) }
    private val data by lazy { RoutingTestData.heightedSegments(tmp) }

    private fun route(r: Route): TrailRoute {
        val result = runBlocking { router.route(r.from, r.to, data) }
        assertTrue("${r.name}: found, not $result", result is TrailRouteResult.Found)
        return (result as TrailRouteResult.Found).route
    }

    @Test
    fun `each of the eight routes is the command-line router's, point for point, to the metre, and height for height`() {
        for (r in ROUTES) {
            val route = route(r)
            val feature = Json.parseToJsonElement(RoutingTestData.resource("brouter-cli/heighted/${r.name}.geojson").readText())
                .jsonObject.getValue("features").jsonArray[0].jsonObject
            val coordinates = feature.getValue("geometry").jsonObject.getValue("coordinates").jsonArray.map { point -> point.jsonArray.map { it.jsonPrimitive.double } }
            val expectedLength = feature.getValue("properties").jsonObject.getValue("track-length").jsonPrimitive.content.toInt()

            assertTrue("${r.name}: the command-line route has points", coordinates.size > 100)
            assertEquals("${r.name}: every point, in micro-degrees", coordinates.map { micro(it[1]) to micro(it[0]) }, route.points.map { micro(it.lat) to micro(it.lng) })
            assertEquals("${r.name}: length in metres", expectedLength, route.lengthMetres)
            // BRouter's GeoJSON writes a third value only where the point has a height (FormatJson.java:109).
            assertEquals("${r.name}: every point's height, as the command-line router gives it", coordinates.map { it.getOrNull(2) }, route.heightsMetres)
        }
        assertTrue("nothing logged on found routes: $logged", logged.isEmpty())
    }

    @Test
    fun `every point of the eight routes has a height, 5_75 to 413_75 m over 3,630 points, as Aspen measured them`() {
        val heights = ROUTES.flatMap { route(it).heightsMetres }
        // Aspen's note to Rowan (prompts/aspen/2026-10-31-01): "every point of all 8 routes has one (3,630 points,
        // 5.75-413.75 m, BRouter 1.7.10 and stock hiking-mountain.brf)". Counted here from the app's own router.
        assertEquals("points over the eight routes", 3_630, heights.size)
        assertEquals("points with no height", 0, heights.count { it == null })
        assertEquals("the lowest height", 5.75, heights.filterNotNull().min(), 0.0)
        assertEquals("the highest height", 413.75, heights.filterNotNull().max(), 0.0)
    }

    @Test
    fun `no route height is below -500 m, so BRouter's no-height marker never reads as a height`() {
        // Sorrel's N7: BRouter's -12345 marker, read as quarter metres, would be -3,086.25 m. Nowhere on land is below
        // -500 m (the lowest dry ground, the Dead Sea shore, is about -430 m).
        val heights = ROUTES.flatMap { route(it).heightsMetres }.filterNotNull()
        assertTrue("the routes carry heights to check: ${heights.size}", heights.size > 3_000)
        assertEquals("heights below -500 m", emptyList<Double>(), heights.filter { it < -500.0 })
    }

    private fun micro(degrees: Double): Long = (degrees * 1_000_000).roundToLong()

    private class Route(val name: String, val from: LatLng, val to: LatLng)

    private companion object {
        // From server/pmtiles-worker/test/fixtures/rd5/routes.json: OpenStreetMap trail nodes, as make-expected.sh routes them.
        val ROUTES = listOf(
            Route("inside-across-the-degree-line", LatLng(41.2742564, -73.9978932), LatLng(41.3036912, -74.0159457)),
            Route("inside-short", LatLng(41.2741743, -74.0013283), LatLng(41.2788313, -74.0189684)),
            Route("inside-long", LatLng(41.2772811, -74.0422743), LatLng(41.292043, -73.996678)),
            Route("edge-north-a", LatLng(41.3047796, -74.0006515), LatLng(41.3053493, -74.0355387)),
            Route("edge-north-b", LatLng(41.3061883, -73.9991069), LatLng(41.3020883, -74.0438051)),
            Route("detour-west", LatLng(41.2940064, -74.0832124), LatLng(41.2535731, -74.0887379)),
            Route("outside-west-island", LatLng(41.2749253, -73.9994484), LatLng(41.2684229, -74.1114362)),
            Route("outside-west-moved", LatLng(41.2749253, -73.9994484), LatLng(41.2588594, -74.1204499)),
        )
    }
}
