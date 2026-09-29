package com.zynergylabs.forager.app.data.backup

import kotlin.math.cos
import kotlin.math.PI
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule a restore uses to say two offline regions are the same one (owner 3.1): the same name, the same radius, and
 * centres within 1 m. Plain JVM: it is arithmetic, and the boundary is the thing to pin down.
 */
class RegionMatchTest {

    private val metresPerDegree = 6_371_000.0 * PI / 180.0

    private fun match(
        a: Triple<Double, Double, Int> = Triple(45.5, -122.5, 5),
        b: Triple<Double, Double, Int>,
        nameB: String = "Cedar Creek",
    ) = sameOfflineRegion("Cedar Creek", a.first, a.second, a.third, nameB, b.first, b.second, b.third)

    @Test
    fun `the very same region matches itself`() = assertTrue(match(b = Triple(45.5, -122.5, 5)))

    @Test
    fun `a centre half a metre north matches, and two metres north does not`() {
        assertTrue(match(b = Triple(45.5 + 0.5 / metresPerDegree, -122.5, 5)))
        assertFalse(match(b = Triple(45.5 + 2.0 / metresPerDegree, -122.5, 5)))
    }

    @Test
    fun `east-west distance is measured on the ground, not in degrees, at a high latitude`() {
        val lat = 60.0
        val perDegreeLng = metresPerDegree * cos(lat * PI / 180.0)
        val a = Triple(lat, 10.0, 5)
        assertTrue("0.5 m east", match(a = a, b = Triple(lat, 10.0 + 0.5 / perDegreeLng, 5)))
        assertFalse("2 m east", match(a = a, b = Triple(lat, 10.0 + 2.0 / perDegreeLng, 5)))
    }

    @Test
    fun `two centres either side of the antimeridian a few centimetres apart match`() {
        assertTrue(match(a = Triple(0.0, 179.9999999, 5), b = Triple(0.0, -179.9999999, 5)))
    }

    @Test
    fun `a different name or a different radius never matches, however close the centres`() {
        assertFalse(match(b = Triple(45.5, -122.5, 5), nameB = "Cedar Creek Two"))
        assertFalse(match(b = Triple(45.5, -122.5, 6)))
    }
}
