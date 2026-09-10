package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [pathHome] on hand-built tracks — return-estimate dispatch, Item 1. Every expected distance is
 * worked by hand, never from the function: the points move due north, where the haversine
 * collapses to `R × Δlat` exactly (IUGG mean radius 6 371 008.8 m, so one degree of latitude is
 * π × 6 371 008.8 / 180 = 111 195.080 m, and 0.001° is 111.19508 m). The tolerance is a hundredth
 * of a metre against those literals.
 */
class PathHomeTest {

    @Test
    fun `a straight track's path home is the sum of its legs, with no hop from a walker standing on the last point`() {
        // Four points at 0.001° steps: three legs of 111.19508 m.
        val track = track(lats = listOf(45.000, 45.001, 45.002, 45.003))

        val path = pathHome(track, current = LatLng(45.003, LNG), origin = null)!!

        assertEquals(333.58524, path.trackMeters, 0.01)
        assertEquals(0.0, path.hopMeters, 0.001)
        assertEquals(0.0, path.hopCountedMeters, 0.001)
        assertNull(path.originLegMeters)
        assertEquals(333.58524, path.totalMeters, 0.01)
        assertEquals(4, path.pointCount)
        assertFalse(path.hopIsFar)
    }

    /**
     * Two owner rulings at once. **Self-join (2026-09-07):** after a double-back over their own
     * points the walker stands exactly where they stood before, so the track is joined there at
     * 0 m and the way home is the ground still ahead — one leg, 111.195 m. Until the join dispatch
     * this test asserted 555.975 m, five legs: the plain sum of the legs, which counted the two
     * legs just walked back over and could never decrease during a recording (the monotonicity
     * defect; see the test below). **Most-recent-point:** the walker is still mapped onto the last
     * stored point, never projected onto a segment — the join is between stored points, at ε.
     */
    @Test
    fun `a doubled-back track is joined at the points the walker re-occupied and reads the ground still ahead`() {
        // North three legs, back south two: the walker is on the second point, which is also the last.
        val track = track(lats = listOf(45.000, 45.001, 45.002, 45.003, 45.002, 45.001))

        val path = pathHome(track, current = LatLng(45.001, LNG), origin = null)!!

        assertEquals(111.19508, path.trackMeters, 0.01)
        assertEquals(111.19508, path.totalMeters, 0.01)
        assertEquals(1, path.joinsOnRoute)
    }

    /**
     * The join's boundary at this level, both sides. Out three legs north, then back south down a
     * parallel leg: offset 0.0002° of longitude (15.72 m at 45° N, above ε) the legs are not
     * joined and the path is the full retrace — 5 × 111.19508 + the 15.7238 m crossing =
     * 571.699 m; offset 0.0001° (7.8625 m, within ε) the walker's last point joins the outbound
     * point beside it and the path is that join plus one leg, 119.058 m. One degree of longitude
     * at latitude φ is 111 195.08 × cos φ: 78 626.2 m at 45.001°, 78 625.5 m at 45.003°.
     */
    @Test
    fun `parallel legs 15 m apart are not joined - 8 m apart they are`() {
        val outbound = listOf(45.000, 45.001, 45.002, 45.003).map { it to LNG }

        val apart = trackAt(outbound + listOf(45.003, 45.002, 45.001).map { it to LNG + 0.0002 })
        val pathApart = pathHome(apart, current = LatLng(45.001, LNG + 0.0002), origin = null)!!
        assertEquals(571.699, pathApart.trackMeters, 0.01)
        assertEquals(0, pathApart.joinsOnRoute)

        val near = trackAt(outbound + listOf(45.003, 45.002, 45.001).map { it to LNG + 0.0001 })
        val pathNear = pathHome(near, current = LatLng(45.001, LNG + 0.0001), origin = null)!!
        assertEquals(119.058, pathNear.trackMeters, 0.01)
        assertEquals(1, pathNear.joinsOnRoute)
    }

    /**
     * **The defect being fixed, as the test that would have failed before.** Out four legs north,
     * then back over the same points one at a time: the estimate must *fall* as the walker
     * returns — 444.78, 333.59, 222.39, 111.20, 0 m — and it is 0 beside the car. The plain sum
     * read 444.78, 555.98, 667.17, 778.37, 889.56 m over the same five polls: never down, and
     * 889.56 m at the origin (the report's "6.8 km standing beside the car" in miniature).
     */
    @Test
    fun `the estimate decreases at every point of the return leg and is zero at the origin`() {
        val outbound = listOf(45.000, 45.001, 45.002, 45.003, 45.004)
        val returning = listOf(45.003, 45.002, 45.001, 45.000)

        val readings = returning.indices.map { i ->
            val lats = outbound + returning.take(i + 1)
            pathHome(track(lats), current = LatLng(lats.last(), LNG), origin = null)!!.totalMeters
        }

        assertEquals(444.78032, pathHome(track(outbound), current = LatLng(45.004, LNG), origin = null)!!.totalMeters, 0.01)
        assertEquals(333.58524, readings[0], 0.01)
        assertEquals(222.39016, readings[1], 0.01)
        assertEquals(111.19508, readings[2], 0.01)
        assertEquals(0.0, readings[3], 0.001)
        for (i in 1 until readings.size) {
            assertTrue("reading $i must be below reading ${i - 1}: ${readings[i - 1]} → ${readings[i]}", readings[i] < readings[i - 1])
        }
    }

    @Test
    fun `a hop under 25 m is measured but omitted from the total`() {
        val track = track(lats = listOf(45.000, 45.001, 45.002, 45.003))

        // 0.00018° north of the last point: 20.0151 m.
        val path = pathHome(track, current = LatLng(45.00318, LNG), origin = null)!!

        assertEquals(20.0151, path.hopMeters, 0.01)
        assertEquals(0.0, path.hopCountedMeters, 0.0)
        assertEquals(333.58524, path.totalMeters, 0.01)
        assertFalse(path.hopIsFar)
        assertEquals(HopBand.NONE, path.hopBand)
    }

    @Test
    fun `a hop between 25 and 50 m is added as-is and does not degrade`() {
        val track = track(lats = listOf(45.000, 45.001, 45.002, 45.003))

        // 0.00027° north of the last point: 30.0227 m.
        val path = pathHome(track, current = LatLng(45.00327, LNG), origin = null)!!

        assertEquals(30.0227, path.hopMeters, 0.01)
        assertEquals(30.0227, path.hopCountedMeters, 0.01)
        assertEquals(363.60791, path.totalMeters, 0.01)
        assertFalse(path.hopIsFar)
    }

    @Test
    fun `a hop over 50 m is added and marks the estimate degraded`() {
        val track = track(lats = listOf(45.000, 45.001, 45.002, 45.003))

        // 0.00054° north of the last point: 60.0453 m.
        val path = pathHome(track, current = LatLng(45.00354, LNG), origin = null)!!

        assertEquals(60.0453, path.hopMeters, 0.01)
        assertEquals(60.0453, path.hopCountedMeters, 0.01)
        assertEquals(393.63058, path.totalMeters, 0.01)
        assertTrue(path.hopIsFar)
        assertEquals(HopBand.FAR, path.hopBand)
    }

    /**
     * The owner's ruling restoring the band, with the case that motivated it: a walker standing
     * near the 25 m boundary whose successive fixes land at 26, 24, 26, 24 m. With plain bands the
     * 25 m appears and vanishes on every fix — about 28 s of walking time at 0.89 m/s; with the
     * band it counts throughout. 0.000234° is 26.0197 m, 0.000216° is 24.0181 m.
     */
    @Test
    fun `a hop hovering around 25 m stays counted once entered, rather than flickering`() {
        val track = track(lats = listOf(45.000, 45.001, 45.002, 45.003))
        val hops = listOf(45.003234, 45.003216, 45.003234, 45.003216)

        var band = HopBand.NONE
        val bands = hops.map { lat ->
            val path = pathHome(track, current = LatLng(lat, LNG), origin = null, previousHopBand = band)!!
            band = path.hopBand
            path.hopBand to path.hopCountedMeters
        }

        assertEquals(listOf(HopBand.COUNTED, HopBand.COUNTED, HopBand.COUNTED, HopBand.COUNTED), bands.map { it.first })
        assertEquals(26.0197, bands[0].second, 0.01)
        assertEquals(24.0181, bands[1].second, 0.01)
    }

    @Test
    fun `a counted hop leaves the band only below 20 m, and a far hop only below 45 m`() {
        val track = track(lats = listOf(45.000, 45.001, 45.002, 45.003))
        val last = 45.003

        // 20.0151 m: not entered from NONE (needs > 25), but kept from COUNTED (leaves only < 20).
        assertEquals(HopBand.NONE, pathHome(track, LatLng(last + 0.00018, LNG), null, HopBand.NONE)!!.hopBand)
        assertEquals(HopBand.COUNTED, pathHome(track, LatLng(last + 0.00018, LNG), null, HopBand.COUNTED)!!.hopBand)
        // 18.9032 m (0.00017°): below 20, so COUNTED leaves.
        assertEquals(HopBand.NONE, pathHome(track, LatLng(last + 0.00017, LNG), null, HopBand.COUNTED)!!.hopBand)

        // 47.8139 m (0.00043°): not far from NONE or COUNTED (needs > 50), but kept from FAR (leaves only < 45).
        assertEquals(HopBand.COUNTED, pathHome(track, LatLng(last + 0.00043, LNG), null, HopBand.NONE)!!.hopBand)
        assertEquals(HopBand.COUNTED, pathHome(track, LatLng(last + 0.00043, LNG), null, HopBand.COUNTED)!!.hopBand)
        assertEquals(HopBand.FAR, pathHome(track, LatLng(last + 0.00043, LNG), null, HopBand.FAR)!!.hopBand)
        // 44.4780 m (0.00040°): below 45, so FAR drops to COUNTED — and the hop is still counted.
        val leftFar = pathHome(track, LatLng(last + 0.00040, LNG), null, HopBand.FAR)!!
        assertEquals(HopBand.COUNTED, leftFar.hopBand)
        assertEquals(44.4780, leftFar.hopCountedMeters, 0.01)
        assertFalse(leftFar.hopIsFar)
        // 18.9032 m from FAR: straight to NONE, two bands in one step.
        assertEquals(HopBand.NONE, pathHome(track, LatLng(last + 0.00017, LNG), null, HopBand.FAR)!!.hopBand)
    }

    @Test
    fun `the origin's last hop is included when the track has an origin waypoint, and is zero when the origin is the first point`() {
        val track = track(lats = listOf(45.000, 45.001, 45.002, 45.003), originWaypointId = "wp-origin")
        // 0.0005° south of the first point: 55.5975 m.
        val origin = waypoint(lat = 44.9995)

        val path = pathHome(track, current = LatLng(45.003, LNG), origin = origin)!!

        assertEquals(55.5975, path.originLegMeters!!, 0.01)
        assertEquals(389.18278, path.totalMeters, 0.01)

        val sameAsFirst = pathHome(track, current = LatLng(45.003, LNG), origin = waypoint(lat = 45.000))!!
        assertEquals(0.0, sameAsFirst.originLegMeters!!, 0.0)
        assertEquals(333.58524, sameAsFirst.totalMeters, 0.01)
    }

    @Test
    fun `a track with no usable points has no path home, and a single point has a zero-length track with a measured hop`() {
        assertNull(pathHome(track(lats = emptyList()), current = LatLng(45.0, LNG), origin = null))

        val single = pathHome(track(lats = listOf(45.000)), current = LatLng(45.00027, LNG), origin = waypoint(lat = 44.9995))!!
        assertEquals(0.0, single.trackMeters, 0.0)
        assertEquals(30.0227, single.hopMeters, 0.01)
        assertEquals(55.5975, single.originLegMeters!!, 0.01)
        assertEquals(85.6202, single.totalMeters, 0.01)
        assertEquals(1, single.pointCount)
    }

    private fun track(lats: List<Double>, originWaypointId: String? = null) = trackAt(lats.map { it to LNG }, originWaypointId)

    private fun trackAt(latLngs: List<Pair<Double, Double>>, originWaypointId: String? = null) = Track(
        id = "t",
        name = null,
        startedAtEpochMillis = 0L,
        endedAtEpochMillis = null,
        points = latLngs.mapIndexed { i, (lat, lng) -> TrackPoint(lat = lat, lng = lng, altitude = null, accuracyMeters = null, timestampEpochMillis = i * 15_000L) },
        originWaypointId = originWaypointId,
    )

    private fun waypoint(lat: Double) = Waypoint(id = "wp-origin", lat = lat, lng = LNG, altitude = null, name = "Start", note = "", createdAtEpochMillis = 0L, trackId = "t")

    private companion object {
        const val LNG = -122.0
    }
}
