package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.TrailRoute
import com.zynergylabs.forager.app.domain.routing.TrailRouteResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

/**
 * The drop-off fixture (`src/test/resources/routing/dropoff/README.md`) on the real vendored BRouter, with nothing
 * stubbed (way-to dispatch 2026-10-28, PR 1). These pin what the fixture is for before anything is built on it: where
 * BRouter alone ends a route to each case's pin, which is the trap each case sets, so a later change to the drawing
 * cannot quietly stop a case from testing anything. They hold before and after the street drop-off: the drop-off
 * changes what the app routes to, not what the engine does with a pin.
 */
class DropOffFixtureTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val logged = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> synchronized(logged) { logged += message } }
    private val router by lazy { BRouterTrailRouter(RoutingTestData.profileDir(tmp), errorLog) }
    private val data: File by lazy { DropOffTestData.segments(tmp) }

    private fun route(to: String): TrailRoute = runBlocking {
        val result = router.route(DropOffTestData.point("walker"), DropOffTestData.point(to), data)
        assertTrue("a route to $to, not $result (log: $logged)", result is TrailRouteResult.Found)
        (result as TrailRouteResult.Found).route
    }

    private fun assertEndsAt(pin: String, end: String) {
        val last = route(pin).points.last()
        val off = GeoDistance.metersBetween(last, DropOffTestData.point(end))
        assertEquals("the route to $pin ends $off m from $end", 0.0, off, 1.0)
    }

    private fun meters(a: String, b: String) = GeoDistance.metersBetween(DropOffTestData.point(a), DropOffTestData.point(b))

    @Test
    fun `the fixture files are the ones its README records`() {
        assertEquals("a332b322768cdfddf9dcc69714e762f362d1d2258a9bf1d68b2fc72b2978d8f7", DropOffTestData.sha256("segments/W75_N40.rd5"))
        assertEquals("90b523741b016304bf255112317739ad38b9619c2f35689113c123942e511eba", DropOffTestData.sha256(DropOffTestData.TILE))
    }

    @Test
    fun `case A, the owner's case - the engine ends at the dead end behind the house, nearer than the street in front`() {
        assertEndsAt("pin_a", "engine_a")
        assertEquals(65.0, meters("pin_a", "engine_a"), 1.0)
        assertEquals(77.6, meters("pin_a", "dropoff_a"), 1.0)
    }

    @Test
    fun `case A - the driveway is closed to the profile, so a pin on it ends where it meets the street`() {
        assertEndsAt("on_driveway_a", "dropoff_a")
    }

    @Test
    fun `case B, fence - the engine ends on the lane beyond the fence`() {
        assertEndsAt("pin_b", "engine_b")
        assertTrue(meters("pin_b", "engine_b") < meters("pin_b", "dropoff_b"))
    }

    @Test
    fun `case C, stream - the engine ends on the street beyond the stream`() {
        assertEndsAt("pin_c", "engine_c")
        assertTrue(meters("pin_c", "engine_c") < meters("pin_c", "dropoff_c"))
    }

    @Test
    fun `cases D and D2, trail pins - the engine ends on the trail`() {
        assertEndsAt("pin_d", "engine_d")
        assertEndsAt("pin_d2", "engine_d2")
    }
}

/** The drop-off fixture's files and named points (`src/test/resources/routing/dropoff/`). */
internal object DropOffTestData {
    const val SQUARE = "W75_N40.rd5"
    const val TILE = "15/9875/12393.mvt"

    fun segments(tmp: TemporaryFolder): File = tmp.newFolder("dropoff-segments").also { dir -> resource("segments/$SQUARE").copyTo(File(dir, SQUARE)) }

    /** A named point from `points.json`, which `make-dropoff-fixture.py` writes from the same drawing as the data. */
    fun point(name: String): LatLng {
        val p = checkNotNull(points[name]) { "no point $name in points.json" }.jsonObject
        return LatLng(lat = p["lat"]!!.jsonPrimitive.double, lng = p["lng"]!!.jsonPrimitive.double)
    }

    fun sha256(path: String): String = MessageDigest.getInstance("SHA-256").digest(resource(path).readBytes()).joinToString("") { "%02x".format(it) }

    private val points by lazy { Json.parseToJsonElement(resource("points.json").readText()).jsonObject }

    fun resource(path: String): File =
        File(checkNotNull(javaClass.classLoader?.getResource("routing/dropoff/$path")) { "routing/dropoff/$path missing" }.toURI())
}
