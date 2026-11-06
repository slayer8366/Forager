package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.dropoff.AccessWayMouths
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteWay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The route square's ways, closed ones included, read with BRouter's own decoder from the drop-off fixture
 * (`src/test/resources/routing/dropoff/README.md`), and the access-way mouths found from them (way-to dispatch 2026-10-28,
 * PR 3). The engine never matches a pin to driveway A (`DropOffFixtureTest`); this reads it anyway.
 */
class BRouterRouteWaysTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val ways by lazy { BRouterRouteWays(RoutingTestData.profileDir(tmp)) }
    private val data: File by lazy { DropOffTestData.segments(tmp) }
    private fun near(pin: String, radius: Double = 150.0): List<RouteWay> = runBlocking { ways.near(DropOffTestData.point(pin), radius, data).getOrThrow() }
    private fun meters(a: LatLng, b: String) = GeoDistance.metersBetween(a, DropOffTestData.point(b))

    @Test
    fun `every way of the square is read, the driveway and the trail among them, each once`() {
        val all = near("pin_a", radius = 800.0)
        assertEquals(8, all.size)
        assertEquals(mapOf("residential" to 6, "service" to 1, "path" to 1), all.groupingBy { it.tags["highway"] }.eachCount())
    }

    @Test
    fun `driveway A comes with its tags and its shape, from Front Road to house A's door`() {
        val driveway = near("pin_a").single { it.tags["service"] == "driveway" }
        assertEquals(mapOf("highway" to "service", "service" to "driveway", "access" to "private"), driveway.tags)
        assertEquals(3, driveway.points.size) // the corner at (20, 60) is a shape point, not a graph node
        val (mouth, door) = driveway.points.first() to driveway.points.last()
        val ends = listOf(meters(mouth, "dropoff_a"), meters(door, "dropoff_a")).sorted()
        assertEquals(0.0, ends[0], 1.0)
        val doorEnd = if (meters(mouth, "dropoff_a") < 1.0) door else mouth
        assertEquals(9.43, meters(doorEnd, "pin_a"), 0.5)
    }

    @Test
    fun `case A's mouth is where the driveway meets Front Road, 75_6 m along it`() {
        val mouths = AccessWayMouths.find(listOf(listOf(DropOffTestData.point("pin_a"))), near("pin_a"))
        val mouth = mouths.single()
        assertEquals(0.0, meters(mouth.at, "dropoff_a"), 1.0)
        assertEquals(75.6, mouth.walkMeters, 0.5)
        assertEquals("residential", mouth.street["highway"])
    }

    @Test
    fun `the other cases have no access way`() {
        for (pin in listOf("pin_b", "pin_c", "pin_d", "pin_d2")) {
            assertEquals(pin, emptyList<AccessWayMouths.Mouth>(), AccessWayMouths.find(listOf(listOf(DropOffTestData.point(pin))), near(pin)))
        }
    }

    @Test
    fun `a square with no file is a failure, not no ways`() {
        val elsewhere = LatLng(lat = 40.1, lng = -75.5) // W80_N40, which the fixture folder does not hold
        val result = runBlocking { ways.near(elsewhere, 100.0, data) }
        assertTrue("$result", result.exceptionOrNull()?.message == "no route square W80_N40.rd5 in dropoff-segments")
    }
}
