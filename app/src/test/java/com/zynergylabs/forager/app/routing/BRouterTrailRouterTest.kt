package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteEnd
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import com.zynergylabs.forager.app.domain.routing.TrailRouter
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToLong

/**
 * The trail router through its interface, on the route cutter's Harriman fixture (dispatch 2026-10-23-02). Every
 * route here runs the real vendored BRouter; nothing is stubbed. The fixture and the command-line router's own
 * results are in `src/test/resources/routing/` (README there).
 */
class BRouterTrailRouterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logged = mutableListOf<Throwable>()
    private val errorLog = ErrorLog { _, _, error -> synchronized(logged) { logged += error } }

    private fun profileDir(): File = tmp.newFolder("profiles2").also { dir ->
        for (name in RoutingProfile.PINNED_BLOBS.keys) File(ASSETS, name).copyTo(File(dir, name))
    }

    private fun segments(): File = tmp.newFolder("segments").also { dir -> resource("segments/W75_N40.rd5").copyTo(File(dir, SQUARE)) }

    private fun router(): TrailRouter = BRouterTrailRouter(profileDir(), errorLog)

    private fun route(start: LatLng, end: LatLng, data: File = segments(), router: TrailRouter = router()) =
        runBlocking { router.route(start, end, data) }

    // ---- A route, and BRouter's own command-line router on the same data ----

    @Test
    fun `a route across the degree line is the command-line router's route, point for point and to the metre`() {
        assertSameAsCommandLine("inside-across-the-degree-line", ACROSS_FROM, ACROSS_TO)
    }

    @Test
    fun `a longer route is the command-line router's route too`() {
        assertSameAsCommandLine("inside-long", LatLng(41.2772811, -74.0422743), LatLng(41.292043, -73.996678))
    }

    private fun assertSameAsCommandLine(name: String, start: LatLng, end: LatLng) {
        val result = route(start, end)
        assertTrue("$name: found, not $result", result is TrailRouteResult.Found)
        val route = (result as TrailRouteResult.Found).route

        val feature = Json.parseToJsonElement(resource("brouter-cli/$name.geojson").readText())
            .jsonObject.getValue("features").jsonArray[0].jsonObject
        val expectedPoints = feature.getValue("geometry").jsonObject.getValue("coordinates").jsonArray.map { pair ->
            val (lon, lat) = pair.jsonArray.map { it.jsonPrimitive.double }
            micro(lat) to micro(lon)
        }
        val expectedLength = feature.getValue("properties").jsonObject.getValue("track-length").jsonPrimitive.content.toInt()

        assertTrue("$name: the command-line route has points", expectedPoints.size > 100)
        assertEquals("$name: every point, in micro-degrees", expectedPoints, route.points.map { micro(it.lat) to micro(it.lng) })
        assertEquals("$name: length in metres", expectedLength, route.lengthMetres)
        assertTrue("nothing logged on a found route: $logged", logged.isEmpty())
    }

    // ---- An end outside the data (RECORD -1546: never moved to a far trail) ----

    @Test
    fun `an end with no trail near it fails as outside the data, where BRouter's default would move it to a trail`() {
        assertEquals(
            TrailRouteResult.Failed(TrailRouteFailure.EndOutsideData(RouteEnd.END)),
            route(ACROSS_FROM, NO_TRAIL_NEAR),
        )
    }

    @Test
    fun `a start with no trail near it fails as outside the data, naming the start`() {
        assertEquals(
            TrailRouteResult.Failed(TrailRouteFailure.EndOutsideData(RouteEnd.START)),
            route(NO_TRAIL_NEAR, ACROSS_FROM),
        )
    }

    @Test
    fun `an end in a five-degree square the stored data has no file for fails as outside the data`() {
        assertEquals(
            // S-412R-1 (dispatch 2026-10-26-11): the mark is part of equality now, so the expected value names it.
            TrailRouteResult.Failed(TrailRouteFailure.EndOutsideData(RouteEnd.END, squareHasNoFile = true)),
            route(ACROSS_FROM, LatLng(46.0, -74.0)), // W75_N45
        )
    }

    // ---- No path ----

    @Test
    fun `two ends on trails that no path joins fail as no path`() {
        val result = route(ACROSS_FROM, ISLAND)
        assertEquals(
            TrailRouteResult.Failed(TrailRouteFailure.NoPath("target island detected for section 0")),
            result,
        )
    }

    // ---- Data missing ----

    @Test
    fun `no data folder fails as data missing`() {
        val result = route(ACROSS_FROM, ACROSS_TO, data = File(tmp.root, "never-downloaded"))
        assertTrue("$result", (result as TrailRouteResult.Failed).failure is TrailRouteFailure.DataMissing)
    }

    @Test
    fun `a data folder with no rd5 file fails as data missing`() {
        val empty = tmp.newFolder("empty").also { File(it, "notes.txt").writeText("not routing data") }
        val result = route(ACROSS_FROM, ACROSS_TO, data = empty)
        assertTrue("$result", (result as TrailRouteResult.Failed).failure is TrailRouteFailure.DataMissing)
    }

    // ---- Data corrupt ----

    @Test
    fun `a file cut short fails as corrupt`() {
        assertCorrupt("cut short", "file of size 100000 too short") { it.copyOf(100_000) }
    }

    @Test
    fun `a file cut short inside its header fails as corrupt, where BRouter alone returns an empty success`() {
        assertCorrupt("cut inside the header", "EOFException") { it.copyOf(150) }
    }

    @Test
    fun `a changed byte in the header fails as corrupt`() {
        assertCorrupt("header byte", "top index checksum error") { bytes -> bytes.copyOf().also { it[10] = (it[10].toInt() xor 0xff).toByte() } }
    }

    @Test
    fun `damaged tile data behind a good header fails as corrupt, from the engine's own read`() {
        assertCorrupt("tile data", "sub index checksum error") { bytes ->
            bytes.copyOf().also { for (i in 2_000 until it.size - 200) it[i] = (it[i].toInt() xor 0x5a).toByte() }
        }
    }

    private fun assertCorrupt(what: String, expectedInDetail: String, damage: (ByteArray) -> ByteArray) {
        val data = tmp.newFolder("damaged").also { File(it, SQUARE).writeBytes(damage(resource("segments/W75_N40.rd5").readBytes())) }
        val result = route(ACROSS_FROM, ACROSS_TO, data = data)
        val failure = (result as? TrailRouteResult.Failed)?.failure
        assertTrue("$what: corrupt, not $result", failure is TrailRouteFailure.DataCorrupt)
        assertTrue("$what: ${(failure as TrailRouteFailure.DataCorrupt).detail}", failure.detail.contains(expectedInDetail))
    }

    // ---- Off the caller's thread ----

    @Test
    fun `routing waits for the router's dispatcher rather than running on the caller's`() = runTest {
        val routerDispatcher = StandardTestDispatcher(testScheduler)
        val router = BRouterTrailRouter(profileDir(), errorLog, dispatcher = routerDispatcher)
        val data = segments()
        val pending = async(kotlinx.coroutines.Dispatchers.Unconfined) { router.route(ACROSS_FROM, ACROSS_TO, data) }
        assertFalse("nothing routed before the router's dispatcher runs", pending.isCompleted)
        testScheduler.advanceUntilIdle()
        assertTrue(pending.await() is TrailRouteResult.Found)
    }

    // Since the cancel fix (#382 M1) this proves the engine is *built* on the router's dispatcher, where a missing profile
    // fails; the search itself runs on the `brouter-engine` thread (BRouterTrailRouter's class doc, "Threading"). L-389-4.
    @Test
    fun `the engine runs on the router's own thread, and an engine that cannot start is a logged failure`() {
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "trail-router-test") }
        try {
            val router = BRouterTrailRouter(File(tmp.root, "no-profile-here"), { _, _, error ->
                // Without the " @coroutine#N" the coroutines debug agent adds to a thread's name under test.
                val thread = Thread.currentThread().name.substringBefore(" @coroutine")
                synchronized(logged) { logged += IllegalStateException(thread, error) }
            }, dispatcher = executor.asCoroutineDispatcher())
            val result = route(ACROSS_FROM, ACROSS_TO, router = router)
            assertTrue("$result", (result as TrailRouteResult.Failed).failure is TrailRouteFailure.EngineFailed)
            assertEquals("logged once", 1, logged.size)
            assertEquals("the engine ran on the router's thread", "trail-router-test", logged.single().message)
            assertNotEquals("not the caller's", Thread.currentThread().name.substringBefore(" @coroutine"), logged.single().message)
        } finally {
            executor.shutdown()
        }
    }

    private fun resource(path: String): File =
        File(checkNotNull(javaClass.classLoader?.getResource("routing/$path")) { "routing/$path missing" }.toURI())

    private fun micro(degrees: Double): Long = (degrees * 1_000_000).roundToLong()

    private companion object {
        val ASSETS = File("src/main/assets/${RoutingProfile.ASSET_DIR}")
        const val SQUARE = "W75_N40.rd5"

        // Route ends from server/pmtiles-worker/test/fixtures/rd5/routes.json: OpenStreetMap trail nodes.
        val ACROSS_FROM = LatLng(41.2742564, -73.9978932)
        val ACROSS_TO = LatLng(41.3036912, -74.0159457)

        // Inside the fixture's square and its one-degree tile, north of every way it holds: BRouter reports
        // "to-position not mapped" with dynamic range off, and with it on routes to a trail moved out to.
        val NO_TRAIL_NEAR = LatLng(41.40, -74.05)

        // Near a way the network from ACROSS_FROM does not reach: "target island detected" on this fixture.
        val ISLAND = LatLng(41.23, -74.03)
    }
}
