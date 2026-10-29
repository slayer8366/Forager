package com.zynergylabs.forager.app.domain.dropoff

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteWay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The walk out along closed ways to a public street (way-to dispatch 2026-10-28, PR 3), on small invented networks.
 * Positions are metres east and north of an origin at 40° N, as `at(x, y)` gives them.
 */
class AccessWayMouthsTest {

    private fun at(x: Double, y: Double) = LatLng(lat = 40.0 + y / 111_195.0, lng = -100.0 + x / (111_195.0 * Math.cos(Math.toRadians(40.0))))
    private fun way(vararg xy: Pair<Double, Double>, tags: Map<String, String>) = RouteWay(xy.map { at(it.first, it.second) }, tags)

    private val street = mapOf("highway" to "residential")
    private val driveway = mapOf("highway" to "service", "service" to "driveway", "access" to "private")
    private val privateTrack = mapOf("highway" to "track", "access" to "private")
    private val house = listOf(listOf(at(-8.0, 67.0), at(8.0, 67.0), at(8.0, 83.0), at(-8.0, 83.0), at(-8.0, 67.0)))

    @Test
    fun `a driveway from the house to the street gives the street end, its length along`() {
        val ways = listOf(way(-100.0 to 0.0, 20.0 to 0.0, tags = street), way(20.0 to 0.0, 100.0 to 0.0, tags = street), way(20.0 to 0.0, 20.0 to 60.0, 8.0 to 70.0, tags = driveway))
        val mouth = AccessWayMouths.find(house, ways).single()
        assertEquals(0.0, com.zynergylabs.forager.app.domain.GeoDistance.metersBetween(mouth.at, at(20.0, 0.0)), 0.01)
        assertEquals(75.6, mouth.walkMeters, 0.1)
    }

    @Test
    fun `the distance is to the house outline - a driveway ending 20 m from the wall counts, 30 m does not`() {
        val near = listOf(way(30.0 to 0.0, 30.0 to 75.0, 28.0 to 75.0, tags = driveway), way(0.0 to 0.0, 30.0 to 0.0, tags = street))
        assertEquals(1, AccessWayMouths.find(house, near).size) // ends 20 m east of the wall at x = 8
        val far = listOf(way(40.0 to 0.0, 40.0 to 75.0, 38.0 to 75.0, tags = driveway), way(0.0 to 0.0, 40.0 to 0.0, tags = street))
        assertTrue(AccessWayMouths.find(house, far).isEmpty()) // 30 m
    }

    @Test
    fun `closed ways in a chain are followed to the street, up to 300 m`() {
        val chain = listOf(
            way(0.0 to 70.0, 0.0 to 300.0, tags = driveway),
            way(0.0 to 300.0, 0.0 to 330.0, tags = privateTrack),
            way(-50.0 to 330.0, 0.0 to 330.0, tags = street),
        )
        assertEquals(260.0, AccessWayMouths.find(house, chain).single().walkMeters, 0.5)
        val tooLong = chain.toMutableList().also { it[2] = way(-50.0 to 400.0, 0.0 to 400.0, tags = street); it += way(0.0 to 330.0, 0.0 to 400.0, tags = privateTrack) }
        assertTrue(AccessWayMouths.find(house, tooLong).isEmpty()) // 333 m
    }

    @Test
    fun `two driveways give two mouths, nearest first, and the walk never goes on along another driveway past a street`() {
        val ways = listOf(
            way(5.0 to 70.0, 5.0 to 0.0, tags = driveway), // 70 m to Front
            way(-5.0 to 83.0, -5.0 to 140.0, tags = driveway), // 57 m to Back
            way(-50.0 to 0.0, 50.0 to 0.0, 5.0 to 0.0, tags = street),
            way(-50.0 to 140.0, -5.0 to 140.0, tags = street),
            way(-5.0 to 140.0, -5.0 to 200.0, tags = driveway), // beyond Back: never walked
            way(-50.0 to 200.0, -5.0 to 200.0, tags = street),
        )
        assertEquals(listOf(57.0, 70.0), AccessWayMouths.find(house, ways).map { Math.round(it.walkMeters).toDouble() })
    }

    @Test
    fun `a closed network that never meets a public street gives nothing`() {
        assertTrue(AccessWayMouths.find(house, listOf(way(0.0 to 70.0, 0.0 to 0.0, tags = driveway), way(0.0 to 0.0, 50.0 to 0.0, tags = privateTrack))).isEmpty())
    }

    @Test
    fun `what counts as closed and as a public street`() {
        assertTrue(AccessWayMouths.isClosed(mapOf("highway" to "service", "service" to "driveway")))
        assertTrue(AccessWayMouths.isClosed(mapOf("highway" to "residential", "access" to "no")))
        assertTrue(AccessWayMouths.isClosed(mapOf("highway" to "path", "foot" to "private")))
        assertFalse(AccessWayMouths.isClosed(mapOf("highway" to "service", "service" to "alley")))
        assertTrue(AccessWayMouths.isPublicStreet(mapOf("highway" to "secondary")))
        assertFalse(AccessWayMouths.isPublicStreet(mapOf("highway" to "path")))
        assertFalse(AccessWayMouths.isPublicStreet(mapOf("highway" to "residential", "access" to "private")))
    }
}
