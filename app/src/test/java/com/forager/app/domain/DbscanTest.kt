package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [Dbscan] directly on synthetic point sets. Higher-level behaviour (aggregation,
 * ordering, empty states) is covered through the real entry point in
 * [ClusterForagingAreasUseCaseTest].
 */
class DbscanTest {

    /** 0.001° of latitude is ~111 m anywhere on Earth, which keeps these fixtures readable. */
    private fun northOf(lat: Double, meters: Double) = lat + meters / 111_194.9

    @Test
    fun `an empty input produces no clusters and no noise`() {
        val result = Dbscan.cluster(points = emptyList(), epsilonMeters = 400.0, minPoints = 4)

        assertEquals(emptyList<List<Int>>(), result.clusters)
        assertEquals(emptyList<Int>(), result.noise)
    }

    @Test
    fun `every point is reported exactly once, as either a cluster member or noise`() {
        val points = listOf(
            LatLng(45.0, -122.0),
            LatLng(northOf(45.0, 100.0), -122.0),
            LatLng(northOf(45.0, 200.0), -122.0),
            LatLng(northOf(45.0, 300.0), -122.0),
            LatLng(46.0, -122.0),
        )

        val result = Dbscan.cluster(points, epsilonMeters = 400.0, minPoints = 4)

        val accounted = result.clusters.flatten() + result.noise
        assertEquals(points.indices.toList(), accounted.sorted())
    }

    @Test
    fun `a lone point beyond the radius is noise, not absorbed into the nearby cluster`() {
        val points = listOf(
            LatLng(45.0, -122.0),
            LatLng(northOf(45.0, 100.0), -122.0),
            LatLng(northOf(45.0, 200.0), -122.0),
            LatLng(northOf(45.0, 300.0), -122.0),
            LatLng(northOf(45.0, 8_000.0), -122.0),
        )

        val result = Dbscan.cluster(points, epsilonMeters = 400.0, minPoints = 4)

        assertEquals(listOf(listOf(0, 1, 2, 3)), result.clusters)
        assertEquals(listOf(4), result.noise)
    }

    /**
     * A border point sits inside a core point's radius but has too few neighbours of its own to
     * seed a cluster. Textbook DBSCAN attaches it to the cluster that reaches it; this pins that
     * behaviour so a rewrite can't silently start dropping such points as noise.
     */
    @Test
    fun `a border point joins the cluster that reaches it`() {
        val points = listOf(
            LatLng(45.0, -122.0),
            LatLng(northOf(45.0, 50.0), -122.0),
            LatLng(northOf(45.0, 100.0), -122.0),
            LatLng(northOf(45.0, 150.0), -122.0),
            // 370 m from index 3 (within the radius) but only 2 points lie within 400 m of it,
            // so it can join a cluster without being able to seed or extend one.
            LatLng(northOf(45.0, 520.0), -122.0),
        )

        val result = Dbscan.cluster(points, epsilonMeters = 400.0, minPoints = 4)

        assertEquals(1, result.clusters.size)
        assertEquals(listOf(0, 1, 2, 3, 4), result.clusters.single().sorted())
        assertTrue(result.noise.isEmpty())
    }

    @Test
    fun `rejects a non-positive radius`() {
        val failure = runCatching { Dbscan.cluster(listOf(LatLng(45.0, -122.0)), 0.0, 4) }

        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `rejects a minPoints below one`() {
        val failure = runCatching { Dbscan.cluster(listOf(LatLng(45.0, -122.0)), 400.0, 0) }

        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
    }
}
